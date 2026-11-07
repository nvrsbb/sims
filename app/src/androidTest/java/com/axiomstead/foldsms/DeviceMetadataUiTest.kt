package com.axiomstead.foldsms

import android.content.Context
import android.provider.Telephony
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Fabricated rows and fake platforms only. Metadata stays local: nothing here writes the provider. */
@org.junit.runner.RunWith(RetryRunner::class)
class DeviceMetadataUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "device-meta-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val sampleDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val device = DeviceMetadataStore(sampleDatabase.deviceMetadata())
    private val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent))
    private val now = System.currentTimeMillis()

    @After fun clean() { smsDatabase.close(); sampleDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun show() {
        consent.setEnabled(true)
        sms.rows = listOf(
            DeviceSms(1, 10, "2025550101", "Oldest thread", now - 3_000_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true),
            DeviceSms(2, 20, "2025550102", "Middle thread", now - 2_000_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true),
            DeviceSms(3, 30, "2025550103", "Newest thread", now - 1_000_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true)
        )
        compose.setContent { MaterialTheme { FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = device, wide = false) } }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun order(): List<String> = compose.onAllNodes(SemanticsMatcher("row") { node ->
        androidx.compose.ui.semantics.SemanticsProperties.TestTag in node.config && node.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].startsWith("device-thread-")
    }).fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }

    private fun menu(vararg path: String) { path.forEachIndexed { i, label -> if (i == 0) compose.onNodeWithTag(label).performClick() else compose.onNodeWithText(label).performClick() } }

    @Test fun pinningMovesAnOlderConversationToTheTopAndPersists() {
        show()
        assertEquals(listOf("device-thread-thread-30", "device-thread-thread-20", "device-thread-thread-10"), order())
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        menu("device-conversation-actions", "Pin conversation")
        compose.onNodeWithTag("device-conversation-back").performClick()
        compose.waitUntil(UI_WAIT_MS) { order().first() == "device-thread-thread-10" }
        compose.onNodeWithTag("device-pinned-thread-10", useUnmergedTree = true).assertExists()
        runBlocking { assertTrue(device.all.first().getValue("thread-10").pinned) }
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        menu("device-conversation-actions", "Unpin conversation")
        compose.onNodeWithTag("device-conversation-back").performClick()
        compose.waitUntil(UI_WAIT_MS) { order().first() == "device-thread-thread-30" }
    }

    @Test fun archiveHidesFromInboxShowsInArchiveAndRestoreReturnsIt() {
        show()
        compose.onNodeWithTag("device-thread-thread-20").performClick()
        menu("device-conversation-actions", "Archive conversation")
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() } // returned to the list
        compose.onNodeWithTag("device-thread-thread-20").assertDoesNotExist()
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Archived").performClick()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-thread-thread-20").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-thread-thread-30").assertDoesNotExist()
        compose.onNodeWithTag("device-thread-thread-20").performClick()
        menu("device-conversation-actions", "Restore conversation")
        compose.onNodeWithText("Archive is empty").assertExists()
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Inbox").performClick()
        compose.onNodeWithTag("device-thread-thread-20").assertExists()
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()) }
    }

    private fun archiveThread20() {
        compose.onNodeWithTag("device-thread-thread-20").performClick()
        menu("device-conversation-actions", "Archive conversation")
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-thread-thread-20").assertDoesNotExist()
    }

    @Test fun aMessageReceivedAfterArchivingBringsTheConversationBackToTheInbox() {
        show(); archiveThread20()
        sms.rows = sms.rows + DeviceSms(4, 20, "2025550102", "New after archive", now, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, false)
        sms.events.value = sms.events.value + 1
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-thread-thread-20").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-unread-thread-20", useUnmergedTree = true).assertExists()
    }

    @Test fun aMessageSentAfterArchivingDoesNotBringItBack() {
        show(); archiveThread20()
        sms.rows = sms.rows + DeviceSms(4, 20, "2025550102", "Sent after archive", now, Telephony.Sms.MESSAGE_TYPE_SENT, 7, true)
        sms.events.value = sms.events.value + 1
        compose.waitForIdle()
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Archived").performClick()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-thread-thread-20").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun categoryChoiceShowsOnTheRowAndFiltersTheInbox() {
        show()
        compose.onNodeWithTag("device-category-thread-10", useUnmergedTree = true).assertTextEquals("Personal")
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        menu("device-conversation-actions", "Change category")
        compose.onNode(hasText("Business") and hasAnyAncestor(isDialog())).performClick() // the filter chip has the same label
        compose.onNodeWithTag("device-conversation-back").performClick()
        compose.waitUntil(UI_WAIT_MS) { runBlocking { device.all.first()["thread-10"]?.category == "Business" } }
        compose.onNodeWithTag("device-category-filter-Business").performClick()
        compose.onNodeWithTag("device-thread-thread-10").assertExists()
        compose.onNodeWithTag("device-thread-thread-20").assertDoesNotExist()
        compose.onNodeWithTag("device-category-filter-Personal").performClick()
        compose.onNodeWithTag("device-thread-thread-10").assertDoesNotExist() // moved to Business by hand
        compose.onNodeWithTag("device-category-filter-All").performClick()
        compose.onNodeWithTag("device-thread-thread-20").assertExists()
    }

    @Test fun realConversationFlagsNeverTouchSampleMetadata() {
        show()
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        menu("device-conversation-actions", "Pin conversation")
        compose.waitUntil(UI_WAIT_MS) { runBlocking { device.all.first()["thread-10"]?.pinned == true } }
        runBlocking { assertTrue(sampleDatabase.metadata().getAll().isEmpty()) }
    }
}
