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

/** Invented messages, a fake platform and a fake clipboard: nothing touches the real clipboard or provider. */
@org.junit.runner.RunWith(RetryRunner::class)
class OtpUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "otp-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val now = System.currentTimeMillis()
    private val copied = mutableListOf<String>()

    @After fun clean() { smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun row(id: Long, thread: Long, address: String, body: String, ageMs: Long, type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX, read: Boolean = false) =
        DeviceSms(id, thread, address, body, now - ageMs, type, 7, read)

    private fun show(rows: List<DeviceSms>, actions: FakeActions? = null, isDefault: Boolean = false) {
        consent.setEnabled(true)
        sms.gate = sms.gate.copy(isDefaultApp = isDefault)
        sms.rows = rows
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent), actions, DefaultPromptStore(samplePrefs))
        compose.setContent {
            MaterialTheme {
                FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()),
                    wide = false, codeCopier = { copied += it })
            }
        }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
    }

    private val otp = "Your verification code is 482913. Do not share it."

    @Test fun aRecentCodeShowsInTheLatestCodeCardAndCopyingItHidesTheCard() {
        show(listOf(row(1, 10, "EXAMPLEBANK", otp, 60_000), row(2, 20, "2025550101", "Lunch?", 30_000)))
        compose.onNodeWithTag("device-latest-code-value").assertTextEquals("482913")
        compose.onNodeWithTag("device-latest-code-copy").performClick()
        compose.runOnIdle { assertEquals(listOf("482913"), copied) }
        compose.onNodeWithTag("device-latest-code").assertDoesNotExist()
        compose.onNodeWithTag("reply-composer").assertDoesNotExist() // copying does not open the conversation
    }

    @Test fun anOldCodeHasNeitherCardNorRowButton() {
        show(listOf(row(1, 10, "EXAMPLEBANK", otp, 40 * 60_000L)))
        compose.onNodeWithTag("device-latest-code").assertDoesNotExist()
        compose.onNodeWithTag("device-copy-code-thread-10", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun aCodeWeSentIsNeverOffered() {
        show(listOf(row(1, 10, "2025550101", otp, 60_000, type = Telephony.Sms.MESSAGE_TYPE_SENT, read = true)))
        compose.onNodeWithTag("device-latest-code").assertDoesNotExist()
        compose.onNodeWithTag("device-copy-code-thread-10", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun theRowOffersCopyAndCopyingDoesNotOpenTheThread() {
        show(listOf(row(1, 10, "EXAMPLEBANK", otp, 20 * 60_000L)))
        compose.onNodeWithTag("device-latest-code").assertDoesNotExist() // older than the card's ten minutes
        compose.onNodeWithTag("device-copy-code-thread-10", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf("482913"), copied) }
        compose.onNodeWithTag("reply-composer").assertDoesNotExist()
        compose.onNodeWithTag("device-inbox-list").assertExists()
    }

    @Test fun theBubbleInAConversationHasACopyButton() {
        show(listOf(row(1, 10, "EXAMPLEBANK", otp, 3 * 60 * 60_000L)))
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithText("Copy code 482913").performClick()
        compose.runOnIdle { assertEquals(listOf("482913"), copied) }
    }

    @Test fun aCodeOlderThanADayHasNoCopyButtonInTheConversation() {
        show(listOf(row(1, 10, "EXAMPLEBANK", otp, 2 * 24 * 60 * 60_000L)))
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithText("Your verification code is 482913. Do not share it.", substring = true).assertExists()
        compose.onNodeWithText("Copy code 482913").assertDoesNotExist()
    }

    @Test fun asDefaultAppCopyingMarksTheConversationRead() {
        val actions = FakeActions(true)
        show(listOf(row(1, 10, "EXAMPLEBANK", otp, 60_000)), actions, isDefault = true)
        compose.onNodeWithTag("device-latest-code-copy").performClick()
        compose.waitUntil(UI_WAIT_MS) { actions.read.contains(10L) }
        compose.runOnIdle { assertEquals(listOf("482913"), copied) }
    }

    @Test fun withoutTheRoleCopyingChangesNothingInTheProvider() {
        val actions = FakeActions(false)
        show(listOf(row(1, 10, "EXAMPLEBANK", otp, 60_000)), actions, isDefault = false)
        compose.onNodeWithTag("device-latest-code-copy").performClick()
        compose.runOnIdle { assertEquals(listOf("482913"), copied); assertTrue(actions.read.isEmpty()) }
    }
}
