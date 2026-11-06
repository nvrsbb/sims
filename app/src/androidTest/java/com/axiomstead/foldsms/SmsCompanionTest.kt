package com.axiomstead.foldsms

import android.content.Context
import android.provider.Telephony
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SmsCompanionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "sms-ui-${UUID.randomUUID()}"
    private val consentPreferences = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val draftPreferences = context.getSharedPreferences("$prefix-draft", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPreferences)
    private val drafts = SmsDraftStore(draftPreferences)
    private val platform = FakeSmsPlatform(consent)
    private val database = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val services = SmsServices(platform, consent, drafts, database.sends())
    @After fun clean() { database.close(); consentPreferences.edit().clear().commit(); draftPreferences.edit().clear().commit() }
    private fun show() { compose.setContent { MaterialTheme { DeviceMessagesPane(services) } } }

    @Test fun optInCancellationDoesNotReadOrSend() {
        show()
        compose.onNodeWithText("Enable device messages").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertFalse(consent.enabled()); assertEquals(0, platform.reads); assertTrue(platform.requests.isEmpty()) }
    }

    @Test fun identicalIncomingBodiesKeepDistinctLongProviderIdsAndRefresh() {
        consent.setEnabled(true)
        val first = DeviceSms(3_000_000_001L, 9, "2025550199", "Fabricated incoming SMS", 1234, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, false)
        platform.rows = listOf(first)
        show()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("device-sms-${first.id}").fetchSemanticsNodes().size == 1 }
        val second = first.copy(id = first.id + 1)
        compose.runOnIdle { platform.rows = listOf(second, first); platform.events.value++ }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("device-sms-${second.id}").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("device-sms-${first.id}").assertExists()
        compose.onNodeWithTag("device-sms-${second.id}").performClick()
        compose.onNodeWithText("Reply").performClick()
        compose.runOnIdle { assertEquals(first.address, drafts.read().address); assertTrue(platform.requests.isEmpty()) }
    }

    @Test fun previewDeviceTabPreservesTheSelectedSampleDraft() {
        val samplePreferences = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
        val sampleDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
        val sampleDrafts = DraftStore(samplePreferences)
        try {
            compose.setContent { PreviewApp(ThemeStore(samplePreferences), sampleDrafts,
                ConversationMetadataStore(sampleDatabase.metadata()), CustomSampleConversationStore(sampleDatabase, sampleDrafts),
                wide = false, deletionStore = SampleDeletionStore(samplePreferences), smsServices = services) }
            compose.onAllNodesWithText("Alex").onFirst().performClick()
            compose.onNodeWithText("Draft message").performTextInput("Fabricated sample draft")
            compose.onNodeWithTag("preview-device-messages").performClick()
            compose.onNodeWithText("Enable device messages").assertExists()
            compose.onNodeWithTag("preview-samples").performClick()
            compose.onNodeWithText("Fabricated sample draft").assertExists()
            compose.runOnIdle { assertTrue(platform.requests.isEmpty()); assertEquals(0, platform.reads) }
        } finally { sampleDatabase.close(); samplePreferences.edit().clear().commit() }
    }

    @Test fun reviewCancellationPreservesDraftAndConfirmedSendUsesFakeTransportOnly() {
        consent.setEnabled(true)
        drafts.edit("2025550199", "Fabricated outbound SMS", 7)
        show()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("SIM 1 · Fabricated SIM").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Review SMS").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Send real SMS?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(platform.requests.isEmpty()); assertEquals("Fabricated outbound SMS", drafts.read().body) }
        compose.onNodeWithText("Review SMS").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Send 1 part(s)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Send 1 part(s)").performClick()
        compose.waitUntil(5_000) { platform.requests.size == 1 }
        runBlocking { services.controller.result(platform.requests.single().id, 0, SMS_NETWORK_SENT) }
        compose.waitUntil(5_000) { drafts.read().body.isEmpty() }
        assertEquals(1, platform.requests.size)
    }
}
