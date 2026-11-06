package com.axiomstead.foldsms

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MmsCompanionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "mms-ui-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val draftPrefs = context.getSharedPreferences("$prefix-draft", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val mms = FakeMmsPlatform(consent)
    private val database = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val services = SmsServices(sms, consent, SmsDraftStore(draftPrefs), database.sends(), mms)

    @After fun clean() { database.close(); consentPrefs.edit().clear().commit(); draftPrefs.edit().clear().commit() }
    private fun show() { compose.setContent { MaterialTheme { DeviceMessagesPane(services) } } }

    @Test fun accessDeniedAndCanceledConsentDoNotReadMms() {
        show()
        compose.onNodeWithText("Enable device messages").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, mms.reads); assertTrue(sms.requests.isEmpty()) }
    }

    @Test fun deniedMmsPermissionLeavesSmsAvailable() {
        consent.setEnabled(true)
        mms.granted = false
        sms.rows = listOf(DeviceSms(30, 1, "2025550199", "Fabricated SMS", 1234, android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX, 7, false))
        show()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("device-sms-30").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("MMS display needs MMS receive access", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, mms.reads); assertTrue(sms.requests.isEmpty()) }
    }

    @Test fun mmsProviderFailureLeavesSmsAvailable() {
        consent.setEnabled(true)
        sms.rows = listOf(DeviceSms(31, 1, "2025550199", "Fabricated SMS", 1234, android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX, 7, false))
        mms.failRead = true
        show()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("device-sms-31").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("MMS provider could not be read", substring = true).assertExists()
    }

    @Test fun smsAndMmsAppearInDeviceTab() {
        consent.setEnabled(true)
        sms.rows = listOf(DeviceSms(40, 1, "2025550199", "Older fabricated SMS", 1000,
            android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX, 7, false))
        mms.rows = listOf(DeviceMms(41, 1, "2025550188", "Newer fabricated MMS", 2000, 1, false,
            listOf(DeviceMmsPart(42, "text/plain", "MMS body", ""))))
        show()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("device-mms-41").fetchSemanticsNodes().size == 1 &&
                compose.onAllNodesWithTag("device-sms-40").fetchSemanticsNodes().size == 1
        }
    }

    @Test fun mainPreviewOpensStoredMmsWithoutACompanionDialog() {
        consent.setEnabled(true)
        mms.rows = listOf(DeviceMms(51, 1, "2025550188", "Fabricated picture", 2000, 1, false,
            listOf(DeviceMmsPart(52, "text/plain", "Fabricated caption", ""))))
        val preferences = context.getSharedPreferences("$prefix-preview", Context.MODE_PRIVATE)
        val sampleDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
        val sampleDrafts = DraftStore(preferences)
        try {
            compose.setContent { PreviewApp(ThemeStore(preferences), sampleDrafts,
                ConversationMetadataStore(sampleDatabase.metadata()), CustomSampleConversationStore(sampleDatabase, sampleDrafts),
                wide = false, deletionStore = SampleDeletionStore(preferences), smsServices = services) }
            compose.runOnIdle { assertEquals(0, mms.reads) }
            compose.onNodeWithTag("preview-device-messages").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("device-mms-51").fetchSemanticsNodes().size == 1 }
            compose.onNodeWithTag("device-mms-51").performClick()
            compose.onNodeWithText("Device MMS · Google Messages remains responsible for download and notification.").assertExists()
        } finally { sampleDatabase.close(); preferences.edit().clear().commit() }
    }

    @Test fun providerIdsPreserveIdenticalMmsAndImageOpensOnlyAfterTap() {
        consent.setEnabled(true)
        val image = DeviceMmsPart(9000000000, "image/jpeg", "", "fabricated.jpg")
        val first = DeviceMms(3000000001, 2, "2025550199", "Fabricated subject", 1234, 1, false,
            listOf(DeviceMmsPart(10, "text/plain", "Fabricated MMS", ""), image))
        mms.rows = listOf(first)
        show()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("device-mms-${first.id}").fetchSemanticsNodes().size == 1 }
        compose.runOnIdle { mms.rows = listOf(first.copy(id = first.id + 1), first); mms.events.value++ }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("device-mms-${first.id + 1}").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("device-mms-${first.id}").assertExists().performClick()
        compose.runOnIdle { assertEquals(0, mms.images); assertTrue(sms.requests.isEmpty()) }
        compose.onNodeWithText("View image · fabricated.jpg").performClick()
        compose.waitUntil(5_000) { mms.images == 1 }
    }
}
