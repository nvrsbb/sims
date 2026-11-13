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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Fake SMS platform only: these tests never call SmsManager, a carrier or the real provider. */
@org.junit.runner.RunWith(RetryRunner::class)
class ReplyComposerTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "reply-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val mms = FakeMmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val sampleDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val drafts = SmsDraftStore(smsDraftPrefs)
    private val services = SmsServices(sms, consent, drafts, smsDatabase.sends(), mms)
    private val now = System.currentTimeMillis()

    @After fun clean() {
        smsDatabase.close(); sampleDatabase.close()
        listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() }
    }

    private fun seedAndOpen(number: String = "2025550101", thread: Long = 10) {
        consent.setEnabled(true)
        sms.rows = listOf(
            DeviceSms(1, 10, "2025550101", "Are you coming?", now - 60_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true),
            DeviceSms(2, 20, "BANK", "Balance alert", now - 50_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true)
        )
        compose.setContent {
            MaterialTheme {
                FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(sampleDatabase.deviceMetadata()), wide = false)
            }
        }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-thread-thread-$thread").performClick()
    }

    // Real touches on purpose: a semantic click works even on a control that is off screen, which once hid a layout bug.
    private fun SemanticsNodeInteraction.click() = performClick()

    private fun review(text: String) {
        compose.onNodeWithTag("reply-composer").assertIsDisplayed()
        compose.onNodeWithTag("reply-send").assertIsDisplayed()
        compose.onNodeWithTag("reply-composer").performTextInput(text)
        // The shared draft reaches the UI asynchronously; do not click while the send button is still disabled.
        compose.waitUntil(UI_WAIT_MS) { runCatching { compose.onNodeWithTag("reply-send").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("reply-send").click()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Send real SMS?").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Types the text and taps Send; whether a dialog follows depends on the message. */
    private fun typeAndSend(text: String) {
        compose.onNodeWithTag("reply-composer").assertIsDisplayed()
        compose.onNodeWithTag("reply-composer").performTextInput(text)
        compose.waitUntil(UI_WAIT_MS) { runCatching { compose.onNodeWithTag("reply-send").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("reply-send").click()
    }

    @Test fun senderThatIsNotAPhoneNumberHasNoComposer() {
        seedAndOpen(thread = 20)
        compose.onNodeWithText("You can't reply to this sender", substring = true).assertExists()
        compose.onNodeWithTag("reply-composer").assertDoesNotExist()
        compose.onNodeWithTag("reply-send").assertDoesNotExist()
    }

    @Test fun aMessageOfSeveralPartsAsksFirstShowingRecipientSimPartsAndTextAndCancelSendsNothing() {
        seedAndOpen()
        compose.onNodeWithTag("reply-send").assertIsNotEnabled() // nothing typed yet
        val long = "x".repeat(150) // 70 characters per part in the fake platform: three parts
        review(long)
        compose.onNodeWithText("To: 2025550101").assertExists()
        compose.onNodeWithText("SIM 1 · Fabricated SIM").assertExists()
        compose.onNodeWithText("3 SMS part(s).", substring = true).assertExists()
        compose.onNode(hasText(long) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNodeWithText("Cancel").click()
        compose.onNodeWithText("Send real SMS?").assertDoesNotExist()
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()); assertEquals(long, drafts.read().body) }
    }

    @Test fun aOnePartReplyInAnExistingConversationSendsOnTapWithoutADialog() {
        seedAndOpen()
        typeAndSend("Hello there")
        compose.waitUntil(UI_WAIT_MS) { sms.requests.size == 1 }
        compose.onNodeWithText("Send real SMS?").assertDoesNotExist()
        assertEquals(listOf("Hello there"), sms.requests.single().parts)
    }

    @Test fun lettersTypedInQuickSuccessionStayInOrder() {
        seedAndOpen()
        val box = compose.onNodeWithTag("reply-composer")
        listOf("te", "st", ".ak", "aja", ".h").forEach { box.performTextInput(it) } // no waiting in between, like fast typing
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("test.akaja.h").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun typingASpaceTidiesPunctuationAndSendTidiesTheLastWord() {
        seedAndOpen()
        compose.onNodeWithTag("reply-composer").performTextInput("hi,ana.ok ")
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Hi, ana. Ok ").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reply-composer").performTextInput("thanks.bye")
        compose.waitUntil(UI_WAIT_MS) { runCatching { compose.onNodeWithTag("reply-send").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("reply-send").click()
        compose.waitUntil(UI_WAIT_MS) { sms.requests.size == 1 }
        assertEquals(listOf("Hi, ana. Ok thanks. Bye"), sms.requests.single().parts)
    }

    @Test fun tappingSendTwiceQuicklySendsOnce() {
        seedAndOpen()
        compose.onNodeWithTag("reply-composer").performTextInput("Hello there")
        compose.waitUntil(UI_WAIT_MS) { runCatching { compose.onNodeWithTag("reply-send").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("reply-send").click()
        runCatching { compose.onNodeWithTag("reply-send").click() }
        compose.waitUntil(UI_WAIT_MS) { sms.requests.size >= 1 }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, sms.requests.size) }
    }

    @Test fun confirmedSendDispatchesOnceThenSentResultClearsTheDraft() {
        seedAndOpen()
        typeAndSend("Hello there")
        compose.waitUntil(UI_WAIT_MS) { sms.requests.size == 1 }
        val request = sms.requests.single()
        assertEquals("2025550101", request.address)
        assertEquals(7, request.sim)
        assertEquals(listOf("Hello there"), request.parts)
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("reply-status").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reply-status").assertTextContains("Submitted", substring = true)
        compose.onNodeWithTag("reply-send").assertIsNotEnabled() // one attempt per draft revision
        runBlocking { services.controller.result(request.id, 0, SMS_NETWORK_SENT) }
        compose.waitUntil(UI_WAIT_MS) { drafts.read().body.isEmpty() }
        compose.runOnIdle { assertEquals(1, sms.requests.size) }
        compose.onNodeWithTag("reply-status").assertDoesNotExist()
    }

    @Test fun failedResultKeepsTheDraftAndNeverResendsAutomatically() {
        seedAndOpen()
        typeAndSend("Hello there")
        compose.waitUntil(UI_WAIT_MS) { sms.requests.size == 1 }
        runBlocking { services.controller.result(sms.requests.single().id, 0, 1) }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Send failed · draft retained").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals("Hello there", drafts.read().body) }
        compose.onNodeWithTag("reply-another-attempt").click()
        compose.onNodeWithText("Review another send attempt?").assertExists()
        compose.onNodeWithText("Review with duplicate risk").click()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Send real SMS?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cancel").click()
        compose.runOnIdle { assertEquals(1, sms.requests.size) }
    }

    @Test fun anotherRecipientsDraftIsNeverOverwrittenAndCanBeDiscarded() {
        consent.setEnabled(true)
        drafts.edit(address = "2025550999", body = "Unsent for someone else", subscriptionId = 7)
        seedAndOpen()
        compose.onNodeWithText("An unsent SMS to 2025550999 is waiting", substring = true).assertExists()
        compose.onNodeWithTag("reply-composer").assertDoesNotExist()
        compose.runOnIdle { assertEquals("Unsent for someone else", drafts.read().body) }
        compose.onNodeWithTag("reply-discard-other").click()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Keep draft").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Keep draft").click()
        compose.runOnIdle { assertEquals("Unsent for someone else", drafts.read().body) }
        compose.onNodeWithTag("reply-discard-other").click()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("reply-confirm-discard").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reply-confirm-discard").click()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("reply-composer").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals("", drafts.read().body) }
    }

    @Test fun missingSendPermissionHidesTheComposerAndExplains() {
        sms.gate = sms.gate.copy(sendGranted = false)
        seedAndOpen()
        compose.onNodeWithText("Allow SMS and phone access", substring = true).assertExists()
        compose.onNodeWithTag("reply-composer").assertDoesNotExist()
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()) }
    }

    @Test fun simPickerChoosesTheSimUsedForTheSend() {
        sms.sims = listOf(SmsSubscription(7, 0, "First"), SmsSubscription(8, 1, "Second"))
        seedAndOpen()
        compose.onNodeWithTag("reply-sim-8").click()
        typeAndSend("Via the second SIM")
        compose.waitUntil(UI_WAIT_MS) { sms.requests.size == 1 }
        assertEquals(8, sms.requests.single().sim)
    }

    @Test fun draftSurvivesLeavingAndReopeningTheConversation() {
        seedAndOpen()
        compose.onNodeWithTag("reply-composer").performTextInput("Draft kept")
        compose.onNodeWithTag("device-conversation-back").performClick()
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithTag("reply-composer").assertTextContains("Draft kept")
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()) }
    }
}
