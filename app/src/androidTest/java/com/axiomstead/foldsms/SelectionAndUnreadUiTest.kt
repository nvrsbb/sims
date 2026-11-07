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

/** Fake platform and fake provider actions only: nothing is changed or deleted on the device. */
@org.junit.runner.RunWith(RetryRunner::class)
class SelectionAndUnreadUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "selection-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val now = System.currentTimeMillis()

    @After fun clean() { smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun row(id: Long, thread: Long, address: String, body: String, ageMs: Long, read: Boolean) =
        DeviceSms(id, thread, address, body, now - ageMs, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, read)

    private fun show(actions: FakeActions, isDefault: Boolean = true) {
        consent.setEnabled(true)
        sms.gate = sms.gate.copy(isDefaultApp = isDefault)
        sms.rows = listOf(
            row(1, 10, "2025550101", "Read already", 3_000, read = true),
            row(2, 20, "2025550102", "Unread two", 2_000, read = false),
            row(3, 30, "2025550103", "Unread three", 1_000, read = false)
        )
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent), actions, DefaultPromptStore(samplePrefs))
        compose.setContent { MaterialTheme { FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()), wide = false) } }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun longPress(thread: Long) = compose.onNodeWithTag("device-thread-thread-$thread").performTouchInput { longClick() }

    @Test fun theMenuFilterShowsOnlyUnreadConversationsAndGoesBack() {
        show(FakeActions(false), isDefault = false)
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Show unread only").performClick()
        compose.onNodeWithTag("device-thread-thread-10").assertDoesNotExist()
        compose.onNodeWithTag("device-thread-thread-20").assertExists()
        compose.onNodeWithTag("device-thread-thread-30").assertExists()
        compose.onNodeWithText("Search unread").assertExists()
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Show all messages").performClick()
        compose.onNodeWithTag("device-thread-thread-10").assertExists()
    }

    @Test fun withoutTheRoleThereIsNoSelection() {
        show(FakeActions(false), isDefault = false)
        longPress(20)
        compose.onNodeWithTag("selection-count").assertDoesNotExist()
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Select messages").assertDoesNotExist()
    }

    @Test fun longPressSelectsSelectAllCountsAndCloseLeavesSelecting() {
        show(FakeActions(true))
        longPress(20)
        compose.onNodeWithTag("selection-count").assertTextEquals("1 selected")
        compose.onNodeWithTag("device-new-message").assertDoesNotExist()
        compose.onNodeWithTag("device-thread-thread-30").performClick() // a tap adds while selecting and does not open the conversation
        compose.onNodeWithTag("selection-count").assertTextEquals("2 selected")
        compose.onNodeWithTag("reply-composer").assertDoesNotExist()
        compose.onNodeWithTag("selection-select-all").performClick()
        compose.onNodeWithTag("selection-count").assertTextEquals("3 selected")
        compose.onNodeWithTag("selection-close").performClick()
        compose.onNodeWithTag("selection-count").assertDoesNotExist()
        compose.onNodeWithTag("device-new-message").assertExists()
    }

    @Test fun markReadAndMarkUnreadActOnTheSelectedConversations() {
        val actions = FakeActions(true)
        show(actions)
        longPress(20); compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithTag("selection-read").performClick()
        compose.waitUntil(UI_WAIT_MS) { actions.read.toSet() == setOf(10L, 20L) }
        compose.onNodeWithTag("selection-count").assertDoesNotExist() // selection ends after the action
        longPress(10)
        compose.onNodeWithTag("selection-unread").performClick()
        compose.waitUntil(UI_WAIT_MS) { actions.unread == listOf(DeviceMessageKind.SMS to 1L) }
        assertTrue(actions.deleted.isEmpty())
    }

    @Test fun deleteAsksFirstAndOnlyDeletesTheSelectedConversationsAfterConfirming() {
        val actions = FakeActions(true)
        show(actions)
        longPress(20); compose.onNodeWithTag("selection-select-all").performClick()
        compose.onNodeWithTag("selection-delete").performClick()
        compose.onNodeWithText("Delete 3 conversations?").assertExists()
        compose.onNodeWithText("Keep").performClick()
        compose.runOnIdle { assertTrue(actions.deleted.isEmpty()) }
        compose.onNodeWithTag("selection-delete").performClick()
        compose.onNodeWithTag("selection-confirm-delete").performClick()
        compose.waitUntil(UI_WAIT_MS) { actions.deleted.toSet() == setOf(10L, 20L, 30L) }
        assertEquals(3, actions.deleted.size)
    }

    @Test fun theMenuCanStartSelecting() {
        show(FakeActions(true))
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Select messages").performClick()
        compose.onNodeWithTag("selection-count").assertTextEquals("0 selected")
        compose.onNodeWithTag("selection-delete").assertIsNotEnabled()
    }
}
