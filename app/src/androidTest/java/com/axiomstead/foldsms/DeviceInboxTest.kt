package com.axiomstead.foldsms

import android.content.Context
import android.provider.Telephony
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Fabricated rows and fake platforms only; nothing reads the real provider or sends. */
class DeviceInboxTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "device-inbox-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val mms = FakeMmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val sampleDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), mms)

    @After fun clean() {
        smsDatabase.close(); sampleDatabase.close()
        listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() }
    }

    private fun show() {
        val drafts = DraftStore(samplePrefs)
        compose.setContent {
            MaterialTheme {
                PreviewApp(ThemeStore(samplePrefs), drafts, ConversationMetadataStore(sampleDatabase.metadata()),
                    CustomSampleConversationStore(sampleDatabase, drafts), wide = false,
                    deletionStore = SampleDeletionStore(samplePrefs), smsServices = services)
            }
        }
    }

    private fun inbox(id: Long, thread: Long, address: String, body: String, date: Long, type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX, read: Boolean = true) =
        DeviceSms(id, thread, address, body, date, type, 7, read)

    @Test fun optedOutShowsSamplesAndNeverReadsTheProvider() {
        sms.rows = listOf(inbox(1, 1, "2025550199", "Fabricated real row", 1000))
        show()
        compose.onNodeWithText("Alex").assertExists()
        compose.onNodeWithTag("device-inbox-list").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, sms.reads); assertEquals(0, mms.reads); assertTrue(sms.requests.isEmpty()) }
    }

    @Test fun cancelingTheOptInPromptReadsNothingAndKeepsSamples() {
        show()
        compose.onNodeWithTag("inbox-actions").performClick()
        compose.onNodeWithText("Real messages").performClick()
        compose.onNodeWithText("Show your real messages?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Alex").assertExists()
        compose.runOnIdle { assertTrue(!consent.enabled()); assertEquals(0, sms.reads); assertEquals(0, mms.reads) }
    }

    @Test fun optedInShowsGroupedConversationsNewestFirstWithPreviewsAndUnreadBadges() {
        consent.setEnabled(true)
        sms.rows = listOf(
            inbox(1, 10, "2025550101", "Older from Sam", 1_000, read = false),
            inbox(2, 10, "2025550101", "Newer from Sam", 2_000, read = false),
            inbox(3, 20, "BANK", "My reply to the bank", 5_000, type = Telephony.Sms.MESSAGE_TYPE_SENT),
            inbox(4, 30, "SMART", "Promo text", 3_000)
        )
        mms.rows = listOf(DeviceMms(1, 40, "2025550188", "", 4_000, 1, true, listOf(DeviceMmsPart(9, "image/jpeg", "", "pic.jpg"))))
        show()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Alex").assertDoesNotExist()
        compose.onNodeWithText("You: My reply to the bank").assertExists()
        compose.onNodeWithText("Newer from Sam").assertExists()
        compose.onNodeWithText("Older from Sam").assertDoesNotExist() // only the latest message previews
        compose.onNodeWithText("Photo").assertExists()
        compose.onNodeWithTag("device-unread-thread-10", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("device-unread-thread-20", useUnmergedTree = true).assertDoesNotExist()
        val order = compose.onAllNodes(hasTestTag("device-inbox-list").not() and hasTestTagStartingWith("device-thread-"))
            .fetchSemanticsNodes().map { node -> testTagOf(node) }
        assertEquals(listOf("device-thread-thread-20", "device-thread-thread-40", "device-thread-thread-30", "device-thread-thread-10"), order)
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()) }
    }

    @Test fun missingReadPermissionExplainsAndOffersPermissionsWithoutReading() {
        consent.setEnabled(true)
        sms.gate = sms.gate.copy(readGranted = false)
        show()
        compose.onNodeWithText("Reading messages needs SMS read and receive permission", substring = true).assertExists()
        compose.onNodeWithTag("device-request-permissions").assertExists()
        compose.onNodeWithTag("device-inbox-list").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, sms.reads) }
    }

    @Test fun emptyProviderAndNoSearchMatchesShowDistinctMessages() {
        consent.setEnabled(true)
        show()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("No messages found", substring = true).fetchSemanticsNodes().isNotEmpty() }
        sms.rows = listOf(inbox(1, 1, "2025550101", "Lunch tomorrow", 1_000))
        compose.runOnIdle { sms.events.value++ }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-inbox-search").performTextInput("zzz")
        compose.onNodeWithText("No conversations match").assertExists()
        compose.onNodeWithTag("device-inbox-search").performTextReplacement("lunch")
        compose.onNodeWithText("Lunch tomorrow").assertExists()
    }

    @Test fun providerFailureShowsAnErrorInsteadOfCrashing() {
        consent.setEnabled(true)
        mms.failRead = true
        sms.rows = listOf(inbox(1, 1, "2025550101", "SMS still shown", 1_000))
        show()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("device-inbox-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("SMS still shown").assertExists()
    }

    @Test fun sampleDataMenuSwitchesToSamplesAndBack() {
        consent.setEnabled(true)
        sms.rows = listOf(inbox(1, 1, "2025550101", "Fabricated real row", 1_000))
        show()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Sample data").performClick()
        compose.onNodeWithText("Alex").assertExists()
        compose.onNodeWithTag("inbox-actions").performClick()
        compose.onNodeWithText("Real messages").performClick()
        compose.onNodeWithText("Fabricated real row").assertExists()
    }

    @Test fun turningOffRealMessagesReturnsToSamplesAndStopsReading() {
        consent.setEnabled(true)
        sms.rows = listOf(inbox(1, 1, "2025550101", "Fabricated real row", 1_000))
        show()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Turn off real messages").performClick()
        compose.onNodeWithText("Alex").assertExists()
        compose.runOnIdle { assertTrue(!consent.enabled()) }
        val readsAfterOff = sms.reads
        compose.runOnIdle { sms.events.value++ }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(readsAfterOff, sms.reads) }
    }

    private fun testTagOf(node: androidx.compose.ui.semantics.SemanticsNode): String? =
        if (androidx.compose.ui.semantics.SemanticsProperties.TestTag in node.config) node.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] else null

    private fun hasTestTagStartingWith(prefix: String) = SemanticsMatcher("TestTag starts with $prefix") {
        testTagOf(it)?.startsWith(prefix) == true
    }
}
