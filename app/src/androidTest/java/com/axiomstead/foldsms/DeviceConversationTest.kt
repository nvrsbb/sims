package com.axiomstead.foldsms

import android.content.Context
import android.graphics.Bitmap
import android.provider.Telephony
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Fabricated rows and fake platforms only; nothing reads the real provider or sends. */
class DeviceConversationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "device-convo-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val mms = FakeMmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val sampleDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), mms)
    private val now = System.currentTimeMillis()

    @After fun clean() {
        smsDatabase.close(); sampleDatabase.close()
        listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() }
    }

    private fun content(wide: Boolean, onBack: (OnBackPressedDispatcher) -> Unit = {}): @androidx.compose.runtime.Composable () -> Unit = {
        onBack(LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher)
        val drafts = DraftStore(samplePrefs)
        MaterialTheme {
            PreviewApp(ThemeStore(samplePrefs), drafts, ConversationMetadataStore(sampleDatabase.metadata()),
                CustomSampleConversationStore(sampleDatabase, drafts), wide = wide,
                deletionStore = SampleDeletionStore(samplePrefs), smsServices = services)
        }
    }

    private fun row(id: Long, thread: Long, address: String, body: String, date: Long, type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX, sub: Int = 7) =
        DeviceSms(id, thread, address, body, date, type, sub, true)

    private fun seed() {
        consent.setEnabled(true)
        sms.rows = listOf(
            row(1, 10, "2025550101", "Are you coming?", now - 60_000),
            row(2, 10, "2025550101", "On my way", now - 30_000, Telephony.Sms.MESSAGE_TYPE_SENT),
            row(3, 10, "2025550101", "Did that send?", now - 10_000, Telephony.Sms.MESSAGE_TYPE_FAILED),
            row(4, 20, "BANK", "Balance alert", now - 5_000_000)
        )
    }

    private fun waitForInbox() = compose.waitUntil(5_000) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }

    @Test fun tappingARowOpensTheConversationOldestFirstWithSeparatorTimesAndFailure() {
        seed()
        compose.setContent(content(wide = false))
        waitForInbox()
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithTag("device-conversation-title").assertTextEquals("2025550101")
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("Are you coming?").assertExists()
        compose.onNodeWithText("On my way").assertExists()
        compose.onNodeWithText("Failed", substring = true).assertExists()
        val order = compose.onAllNodes(hasTestTagStartingWith("device-message-sms-")).fetchSemanticsNodes().map { testTagOf(it) }
        assertEquals(listOf("device-message-sms-1", "device-message-sms-2", "device-message-sms-3"), order)
        compose.onNodeWithTag("device-inbox-list").assertDoesNotExist() // phone width shows one pane
        compose.onNodeWithTag("preview-samples").assertDoesNotExist() // bottom bar hides inside the conversation
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()) }
    }

    @Test fun backReturnsToTheListWithSearchKept() {
        seed()
        var dispatcher: OnBackPressedDispatcher? = null
        compose.setContent(content(wide = false) { dispatcher = it })
        waitForInbox()
        compose.onNodeWithTag("device-inbox-search").performTextInput("2025550101")
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithTag("device-conversation-list").assertExists()
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        compose.onNodeWithTag("device-inbox-list").assertExists()
        compose.onNodeWithTag("device-inbox-search").assertTextContains("2025550101")
        compose.onNodeWithTag("device-thread-thread-20").assertDoesNotExist()
        compose.onNodeWithTag("preview-samples").assertExists()
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithTag("device-conversation-back").performClick()
        compose.onNodeWithTag("device-inbox-list").assertExists()
    }

    @Test fun openConversationSurvivesStateRestoration() {
        seed()
        val restoration = StateRestorationTester(compose)
        restoration.setContent(content(wide = false))
        waitForInbox()
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("device-conversation-title").assertTextEquals("2025550101")
        compose.onNodeWithText("Are you coming?").assertExists()
    }

    @Test fun wideLayoutShowsBothPanesAndSwitchesConversations() {
        seed()
        compose.setContent(content(wide = true))
        waitForInbox()
        compose.onNodeWithText("Choose a conversation").assertExists()
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithTag("device-inbox-list").assertExists()
        compose.onNodeWithText("Are you coming?").assertExists()
        compose.onNodeWithTag("device-conversation-back").assertDoesNotExist()
        compose.onNodeWithTag("device-thread-thread-20").performClick()
        compose.onNodeWithTag("device-message-sms-4").assertExists() // the bubble; the list preview shows the same text
        compose.onNodeWithTag("device-conversation-title").assertTextEquals("BANK")
    }

    @Test fun mmsShowsSubjectTextImageAndOtherAttachmentsWithoutDecodingUntilShown() {
        consent.setEnabled(true)
        mms.rows = listOf(DeviceMms(5, 30, "2025550188", "Trip", now - 1_000, 1, true, listOf(
            DeviceMmsPart(11, "text/plain", "Look at this", ""), DeviceMmsPart(12, "image/jpeg", "", "beach.jpg"),
            DeviceMmsPart(13, "audio/amr", "", "note.amr"))))
        compose.setContent(content(wide = false))
        waitForInbox()
        compose.runOnIdle { assertEquals(0, mms.images) }
        compose.onNodeWithTag("device-thread-thread-30").performClick()
        compose.onNodeWithText("Trip").assertExists()
        compose.onNodeWithText("Look at this").assertExists()
        compose.onNodeWithText("audio/amr · note.amr · open in Google Messages").assertExists()
        compose.waitUntil(5_000) { mms.images == 1 }
        compose.onNodeWithText("Image unavailable", substring = true).assertExists() // the fake returns no bitmap
    }

    @Test fun twoSimsLabelMessagesAndASingleSimDoesNot() {
        seed()
        sms.sims = listOf(SmsSubscription(7, 0, "A"), SmsSubscription(8, 1, "B"))
        sms.rows = sms.rows + row(5, 10, "2025550101", "From the second SIM", now - 1_000, sub = 8)
        compose.setContent(content(wide = false))
        waitForInbox()
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("SIM 2", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Only labels inside bubbles count: rows 1-3 are on SIM 1, row 5 on SIM 2 (a composer SIM chip may also say "SIM 1").
        compose.onAllNodes(hasText("SIM 1", substring = true) and hasAnyAncestor(hasTestTagStartingWith("device-message-"))).assertCountEquals(3)
    }

    @Test fun listScrollPositionSurvivesOpeningAConversationAndGoingBack() {
        consent.setEnabled(true)
        sms.rows = (1L..40L).map { row(it, it, "20255501%02d".format(it), "Message $it", now - it * 1_000) }
        compose.setContent(content(wide = false))
        waitForInbox()
        compose.onNodeWithTag("device-inbox-list").performScrollToIndex(25)
        val before = compose.onNodeWithTag("device-inbox-list").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before > 0f)
        compose.onNodeWithTag("device-thread-thread-26").performClick()
        compose.onNodeWithTag("device-conversation-title").assertExists()
        compose.onNodeWithTag("device-conversation-back").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        val after = compose.onNodeWithTag("device-inbox-list").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 0.5f)
    }

    @Test fun deletedConversationFallsBackToTheList() {
        seed()
        compose.setContent(content(wide = false))
        waitForInbox()
        compose.onNodeWithTag("device-thread-thread-20").performClick()
        compose.onNodeWithTag("device-conversation-title").assertTextEquals("BANK")
        sms.rows = sms.rows.filter { it.threadId != 20L }
        compose.runOnIdle { sms.events.value++ }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-thread-thread-20").assertDoesNotExist()
    }

    private fun testTagOf(node: androidx.compose.ui.semantics.SemanticsNode): String? =
        if (androidx.compose.ui.semantics.SemanticsProperties.TestTag in node.config) node.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] else null

    private fun hasTestTagStartingWith(prefix: String) = SemanticsMatcher("TestTag starts with $prefix") { testTagOf(it)?.startsWith(prefix) == true }
}
