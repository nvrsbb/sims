package com.axiomstead.foldsms

import android.content.Context
import android.provider.Telephony
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Invented messages and a fake platform only. */
@org.junit.runner.RunWith(RetryRunner::class)
class ThreadSearchUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "thread-search-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val now = System.currentTimeMillis()

    @After fun clean() { smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun row(id: Long, body: String, type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX) =
        DeviceSms(id, 10, "2025550101", body, now - (100 - id) * 60_000, type, 7, true)

    private fun open(rows: List<DeviceSms>) {
        consent.setEnabled(true)
        sms.rows = rows
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent))
        compose.setContent { MaterialTheme { FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()), wide = false) } }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-thread-thread-10").performClick()
    }

    private fun search(text: String) {
        compose.onNodeWithTag("conversation-search").performClick()
        compose.onNodeWithTag("conversation-search-field").performTextInput(text)
    }

    @Test fun searchCountsMatchesWalksThemAndClosesCleanly() {
        open(listOf(row(1, "Lunch at noon?"), row(2, "Sure, lunch works", Telephony.Sms.MESSAGE_TYPE_SENT), row(3, "Bring the report"), row(4, "Lunch is on me")))
        search("lunch")
        compose.onNodeWithTag("conversation-search-count").assertTextEquals("3/3") // starts on the newest match
        compose.onNodeWithTag("conversation-search-newer").assertIsNotEnabled()
        compose.onNodeWithTag("conversation-search-older").performClick()
        compose.onNodeWithTag("conversation-search-count").assertTextEquals("2/3")
        compose.onNodeWithTag("conversation-search-older").performClick()
        compose.onNodeWithTag("conversation-search-count").assertTextEquals("1/3")
        compose.onNodeWithTag("conversation-search-older").assertIsNotEnabled()
        compose.onNodeWithTag("conversation-search-close").performClick()
        compose.onNodeWithTag("conversation-search-count").assertDoesNotExist()
        compose.onNodeWithTag("device-conversation-title").assertExists()
    }

    @Test fun aQueryWithoutMatchesSaysSo() {
        open(listOf(row(1, "Hello"), row(2, "World")))
        search("zzz")
        compose.onNodeWithTag("conversation-search-count").assertTextEquals("No matches")
        compose.onNodeWithTag("conversation-search-older").assertIsNotEnabled()
    }

    @Test fun anOldMatchIsScrolledIntoViewInALongConversation() {
        val rows = listOf(row(1, "The needle is in here")) + (2L..60L).map { row(it, "Filler message number $it") }
        open(rows)
        compose.onNodeWithTag("device-message-sms-1").assertDoesNotExist() // the thread opens at the bottom
        search("needle")
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-message-sms-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-message-sms-1").assertIsDisplayed()
    }

    @Test fun backClosesTheSearchBeforeLeavingTheConversation() {
        open(listOf(row(1, "Hello")))
        compose.onNodeWithTag("conversation-search").performClick()
        compose.onNodeWithTag("conversation-search-field").assertExists()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("conversation-search-field").assertDoesNotExist()
        compose.onNodeWithTag("device-conversation-title").assertExists()
    }

    @Test fun statusShowsSendingFailedAndSentOnlyOnTheNewestSentMessage() {
        open(listOf(
            row(1, "First sent", Telephony.Sms.MESSAGE_TYPE_SENT), row(2, "Reply from them"),
            row(3, "Second sent", Telephony.Sms.MESSAGE_TYPE_SENT), row(4, "Queued one", Telephony.Sms.MESSAGE_TYPE_OUTBOX),
            row(5, "Broken one", Telephony.Sms.MESSAGE_TYPE_FAILED)
        ))
        compose.onNodeWithTag("device-status-sms-5", useUnmergedTree = true).assertTextContains("Failed", substring = true)
        compose.onNodeWithTag("device-status-sms-4", useUnmergedTree = true).assertTextContains("Sending", substring = true)
        compose.onNodeWithTag("device-status-sms-1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("device-status-sms-3", useUnmergedTree = true).assertDoesNotExist() // only the newest sent message says Sent
    }

    @Test fun theNewestSentMessageSaysSent() {
        open(listOf(row(1, "Hi", Telephony.Sms.MESSAGE_TYPE_SENT), row(2, "Ok", Telephony.Sms.MESSAGE_TYPE_SENT)))
        compose.onNodeWithTag("device-status-sms-2", useUnmergedTree = true).assertTextContains("Sent", substring = true)
        compose.onNodeWithTag("device-status-sms-1", useUnmergedTree = true).assertDoesNotExist()
    }
}
