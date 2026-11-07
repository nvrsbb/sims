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

/** Fake platform and fake provider actions only: nothing is deleted, written or sent on the device. */
@org.junit.runner.RunWith(RetryRunner::class)
class DefaultRoleUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "default-role-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val promptPrefs = context.getSharedPreferences("$prefix-prompt", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val now = System.currentTimeMillis()

    private class FakeActions(var allowed: Boolean) : MessageActions {
        val read = mutableListOf<Long>(); val deleted = mutableListOf<Long>()
        override fun canModify() = allowed
        override fun markThreadRead(threadId: Long): Boolean { read += threadId; return true }
        override fun deleteThread(threadId: Long): Boolean { deleted += threadId; return true }
    }

    @After fun clean() { smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, promptPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun show(actions: FakeActions, isDefault: Boolean) {
        consent.setEnabled(true)
        sms.gate = sms.gate.copy(isDefaultApp = isDefault)
        sms.rows = listOf(DeviceSms(1, 10, "2025550101", "Are you coming?", now - 60_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, false))
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent), actions, DefaultPromptStore(promptPrefs))
        compose.setContent { MaterialTheme { FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()), wide = false) } }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun theOfferShowsUntilNotNowAndThenStaysGone() {
        show(FakeActions(false), isDefault = false)
        compose.onNodeWithTag("device-make-default").assertExists()
        compose.onNodeWithTag("device-default-not-now").performClick()
        compose.onNodeWithTag("device-make-default").assertDoesNotExist()
        compose.runOnIdle { assertTrue(DefaultPromptStore(promptPrefs).dismissed()) }
    }

    @Test fun noOfferWhenFoldSmsIsAlreadyDefault() {
        show(FakeActions(true), isDefault = true)
        compose.onNodeWithTag("device-make-default").assertDoesNotExist()
    }

    @Test fun withoutTheRoleThereIsNoDeleteAndNothingIsMarkedRead() {
        val actions = FakeActions(false)
        show(actions, isDefault = false)
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithTag("device-conversation-actions").performClick()
        compose.onNodeWithText("Delete conversation").assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.read.isEmpty() && actions.deleted.isEmpty()) }
    }

    @Test fun asDefaultOpeningMarksReadAndDeleteNeedsConfirmation() {
        val actions = FakeActions(true)
        show(actions, isDefault = true)
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.waitUntil(UI_WAIT_MS) { actions.read.contains(10L) }
        compose.onNodeWithTag("device-conversation-actions").performClick()
        compose.onNodeWithText("Delete conversation").performClick()
        compose.onNodeWithText("Delete this conversation?").assertExists()
        compose.runOnIdle { assertTrue(actions.deleted.isEmpty()) } // nothing happens before the confirmation
        compose.onNodeWithText("Keep").performClick()
        compose.runOnIdle { assertTrue(actions.deleted.isEmpty()) }
        compose.onNodeWithTag("device-conversation-actions").performClick()
        compose.onNodeWithText("Delete conversation").performClick()
        compose.onNodeWithTag("conversation-confirm-delete").performClick()
        compose.waitUntil(UI_WAIT_MS) { actions.deleted == listOf(10L) }
        assertEquals(listOf(10L), actions.deleted)
    }
}
