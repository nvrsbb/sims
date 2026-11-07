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

/** Fake SMS platform only; no contacts permission, SmsManager, carrier or real provider involved. */
@org.junit.runner.RunWith(RetryRunner::class)
class DeviceNewMessageTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "new-message-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val sampleDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent))
    private val now = System.currentTimeMillis()

    @After fun clean() { smsDatabase.close(); sampleDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun show(optIn: Boolean = true) {
        if (optIn) consent.setEnabled(true)
        sms.rows = listOf(DeviceSms(1, 10, "2025550101", "Existing thread text", now - 60_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true))
        compose.setContent { MaterialTheme { FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(sampleDatabase.deviceMetadata()), wide = false) } }
    }

    private fun start(number: String) {
        compose.onNodeWithTag("device-new-message").performClick()
        compose.onNodeWithTag("new-message-number").performTextInput(number)
                compose.onNodeWithTag("new-message-continue").performClick()
    }

    private fun waitInbox() = compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }

    @Test fun theButtonIsNotCoveredByTheListAndOpensTheDialogWithATapAtItsPosition() {
        show(); waitInbox()
        compose.onNodeWithTag("device-new-message").performClick() // a real touch, so a list drawn on top of it would swallow it
        compose.onNodeWithTag("new-message-number").assertExists()
    }

    @Test fun theButtonExistsOnlyWhenSendingIsAvailable() {
        sms.gate = sms.gate.copy(sendGranted = false)
        show(); waitInbox()
        compose.onNodeWithTag("device-new-message").assertDoesNotExist()
    }

    @Test fun theButtonIsAbsentBeforeAccessIsAllowed() {
        show(optIn = false)
        compose.onNodeWithTag("device-new-message").assertDoesNotExist()
        compose.onNodeWithTag("welcome-allow").assertExists()
    }

    @Test fun invalidNumbersAreRejectedWithoutLeavingTheList() {
        show(); waitInbox()
        for (bad in listOf("SMART", "12", "abc123", "")) {
            start(bad)
            compose.onNodeWithTag("new-message-error", useUnmergedTree = true).assertExists() // supporting text merges into the field
            compose.onNodeWithText("Cancel").performClick()
        }
        compose.onNodeWithTag("device-inbox-list").assertExists()
        compose.onNodeWithTag("reply-composer").assertDoesNotExist()
    }

    @Test fun aNewNumberOpensAnEmptyConversationWithTheReviewedComposerAndSendsNothingUntilConfirmed() {
        show(); waitInbox()
        start("+63 917 555 0199")
        compose.onNodeWithTag("device-conversation-title").assertTextEquals("+639175550199")
        compose.onNodeWithTag("device-new-conversation-hint").assertExists()
        compose.onNodeWithTag("device-conversation-actions").assertDoesNotExist() // nothing to pin or archive yet
        compose.onNodeWithTag("reply-composer").performTextInput("Hello new number")
        // The shared draft reaches the UI asynchronously and the keyboard moves the controls: wait, then act on the control itself.
        compose.waitUntil(UI_WAIT_MS) { runCatching { compose.onNodeWithTag("reply-review").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("reply-review").performClick()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Send real SMS?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("To: +639175550199").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()) }
    }

    @Test fun anExistingNumberInAnyFormatOpensItsThreadInstead() {
        show(); waitInbox()
        start("+1 (202) 555-0101")
        compose.onNodeWithText("Existing thread text").assertExists()
        compose.onNodeWithTag("device-new-conversation-hint").assertDoesNotExist()
        compose.onNodeWithTag("device-conversation-title").assertTextEquals("2025550101")
    }

    @Test fun onceTheProviderHasAThreadTheNewConversationBecomesTheRealOne() {
        show(); waitInbox()
        start("2025550177")
        compose.onNodeWithTag("device-new-conversation-hint").assertExists()
        sms.rows = sms.rows + DeviceSms(9, 77, "2025550177", "Now stored by the provider", now, Telephony.Sms.MESSAGE_TYPE_SENT, 7, true)
        compose.runOnIdle { sms.events.value++ }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-message-sms-9").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-new-conversation-hint").assertDoesNotExist()
        compose.onNodeWithTag("device-conversation-actions").assertExists() // now a real conversation
    }

    @Test fun backFromAnEmptyNewConversationDiscardsItAndANewStartWorks() {
        show(); waitInbox()
        start("2025550188")
        compose.onNodeWithTag("device-conversation-back").performClick()
        compose.onNodeWithTag("device-inbox-list").assertExists()
        compose.onNodeWithTag("device-thread-thread-77").assertDoesNotExist()
        start("2025550189")
        compose.onNodeWithTag("device-conversation-title").assertTextEquals("2025550189")
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()); assertEquals(0, services.drafts.read().body.length) }
    }
}
