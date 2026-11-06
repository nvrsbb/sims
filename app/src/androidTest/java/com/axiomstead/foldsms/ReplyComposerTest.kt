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
        val sampleDrafts = DraftStore(samplePrefs)
        compose.setContent {
            MaterialTheme {
                PreviewApp(ThemeStore(samplePrefs), sampleDrafts, ConversationMetadataStore(sampleDatabase.metadata()),
                    CustomSampleConversationStore(sampleDatabase, sampleDrafts), wide = false,
                    deletionStore = SampleDeletionStore(samplePrefs), smsServices = services,
                    deviceMetadataStore = DeviceMetadataStore(sampleDatabase.deviceMetadata()))
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-thread-thread-$thread").performClick()
    }

    // Typing opens the keyboard, which moves the composer after the test last measured it. A semantic click acts on the
    // control itself instead of tapping a position the keyboard may now cover.
    private fun SemanticsNodeInteraction.click() = performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)

    private fun review(text: String) {
        compose.onNodeWithTag("reply-composer").performTextInput(text)
        // The shared draft reaches the UI asynchronously; do not click while the send button is still disabled.
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("reply-review").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("reply-review").click()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Send real SMS?").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun senderThatIsNotAPhoneNumberHasNoComposer() {
        seedAndOpen(thread = 20)
        compose.onNodeWithText("You can't reply to this sender", substring = true).assertExists()
        compose.onNodeWithTag("reply-composer").assertDoesNotExist()
        compose.onNodeWithTag("reply-review").assertDoesNotExist()
    }

    @Test fun reviewShowsRecipientSimPartsAndTextAndCancelSendsNothing() {
        seedAndOpen()
        compose.onNodeWithTag("reply-review").assertIsNotEnabled() // nothing typed yet
        review("Hello there")
        compose.onNodeWithText("To: 2025550101").assertExists()
        compose.onNodeWithText("SIM 1 · Fabricated SIM").assertExists()
        compose.onNodeWithText("1 SMS part(s).", substring = true).assertExists()
        compose.onNode(hasText("Hello there") and hasAnyAncestor(isDialog())).assertExists() // the composer holds the same text
        compose.onNodeWithText("Cancel").click()
        compose.onNodeWithText("Send real SMS?").assertDoesNotExist()
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()); assertEquals("Hello there", drafts.read().body) }
        compose.onNodeWithTag("reply-composer").assertTextContains("Hello there")
    }

    @Test fun confirmedSendDispatchesOnceThenSentResultClearsTheDraft() {
        seedAndOpen()
        review("Hello there")
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()) } // review alone never sends
        compose.onNodeWithTag("reply-confirm-send").click()
        compose.waitUntil(5_000) { sms.requests.size == 1 }
        val request = sms.requests.single()
        assertEquals("2025550101", request.address)
        assertEquals(7, request.sim)
        assertEquals(listOf("Hello there"), request.parts)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reply-status").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reply-status").assertTextContains("Submitted", substring = true)
        compose.onNodeWithTag("reply-review").assertIsNotEnabled() // one attempt per draft revision
        runBlocking { services.controller.result(request.id, 0, SMS_NETWORK_SENT) }
        compose.waitUntil(5_000) { drafts.read().body.isEmpty() }
        compose.runOnIdle { assertEquals(1, sms.requests.size) }
        compose.onNodeWithTag("reply-status").assertDoesNotExist()
    }

    @Test fun failedResultKeepsTheDraftAndNeverResendsAutomatically() {
        seedAndOpen()
        review("Hello there")
        compose.onNodeWithTag("reply-confirm-send").click()
        compose.waitUntil(5_000) { sms.requests.size == 1 }
        runBlocking { services.controller.result(sms.requests.single().id, 0, 1) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Send failed · draft retained").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals("Hello there", drafts.read().body) }
        compose.onNodeWithTag("reply-another-attempt").click()
        compose.onNodeWithText("Review another send attempt?").assertExists()
        compose.onNodeWithText("Review with duplicate risk").click()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Send real SMS?").fetchSemanticsNodes().isNotEmpty() }
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
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Keep draft").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Keep draft").click()
        compose.runOnIdle { assertEquals("Unsent for someone else", drafts.read().body) }
        compose.onNodeWithTag("reply-discard-other").click()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reply-confirm-discard").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reply-confirm-discard").click()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reply-composer").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals("", drafts.read().body) }
    }

    @Test fun missingSendPermissionHidesTheComposerAndExplains() {
        sms.gate = sms.gate.copy(sendGranted = false)
        seedAndOpen()
        compose.onNodeWithText("Sending needs SMS and Phone/SIM permissions", substring = true).assertExists()
        compose.onNodeWithTag("reply-composer").assertDoesNotExist()
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()) }
    }

    @Test fun simPickerChoosesTheSimShownInReview() {
        sms.sims = listOf(SmsSubscription(7, 0, "First"), SmsSubscription(8, 1, "Second"))
        seedAndOpen()
        compose.onNodeWithTag("reply-sim-8").click()
        review("Via the second SIM")
        compose.onNodeWithText("SIM 2 · Second").assertExists()
        compose.onNodeWithTag("reply-confirm-send").click()
        compose.waitUntil(5_000) { sms.requests.size == 1 }
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
