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
import org.junit.Rule
import org.junit.Test

/** Invented rows, a fake platform and tiny page sizes, so paging is exercised with a handful of messages. */
@org.junit.runner.RunWith(RetryRunner::class)
class PagingUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "paging-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val now = System.currentTimeMillis()

    @After fun clean() { smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun row(id: Long, thread: Long, body: String, ageMs: Long) =
        DeviceSms(id, thread, "20255501%02d".format(thread), body, now - ageMs, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true)

    private fun show(rows: List<DeviceSms>, pageSms: Int = 5) {
        consent.setEnabled(true)
        sms.rows = rows
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent), pages = PageSizes(pageSms, 2))
        compose.setContent { MaterialTheme { FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()), wide = false) } }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Twelve conversations with one message each; thread 12 is the newest. */
    private val twelveThreads get() = (1L..12L).map { row(it, it, "Message $it", (13 - it) * 60_000) }

    @Test fun conversationsOlderThanTheFirstPageAppearWhenTheListIsScrolled() {
        show(twelveThreads)
        compose.onNodeWithTag("device-thread-thread-12").assertExists()
        compose.waitUntil(UI_WAIT_MS) {
            runCatching { compose.onNodeWithTag("device-inbox-list").performScrollToNode(hasTestTag("device-thread-thread-1")); true }.getOrDefault(false)
        }
        compose.onNodeWithTag("device-thread-thread-1").assertExists()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-loading-older").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun anOpenedConversationIsCompleteEvenWhenMostOfItIsOlderThanThePage() {
        // Thread 10 has 30 messages; only its newest five fall in a page of five.
        val rows = (1L..30L).map { row(it, 10, "Thread ten message $it", (100 - it) * 60_000) }
        show(rows)
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.waitUntil(UI_WAIT_MS) {
            runCatching { compose.onNodeWithTag("device-conversation-list").performScrollToNode(hasTestTag("device-message-sms-1")); true }.getOrDefault(false)
        }
        compose.onNodeWithTag("device-message-sms-1").assertExists()
    }

    @Test fun searchOffersToReadOlderMessagesAndThenFindsThem() {
        // Sixty conversations: the list pages in until it is longer than the screen and then stops, so the oldest one is not read yet.
        val rows = listOf(row(1, 1, "The needle is here", 10_000_000)) + (2L..60L).map { row(it, it, "Message $it", (61 - it) * 60_000) }
        show(rows)
        compose.onNodeWithTag("device-inbox-search").performTextInput("needle")
        compose.onNodeWithText("No conversations match").assertExists()
        compose.onNodeWithTag("device-search-all").performClick()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-thread-thread-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-search-all").assertDoesNotExist()
    }

    @Test fun aSmallInboxShowsNoLoadingFooterOrSearchAllButton() {
        show(twelveThreads, pageSms = 1_000)
        compose.onNodeWithTag("device-loading-older").assertDoesNotExist()
        compose.onNodeWithTag("device-inbox-search").performTextInput("zzz")
        compose.onNodeWithText("No conversations match").assertExists()
        compose.onNodeWithTag("device-search-all").assertDoesNotExist()
    }
}
