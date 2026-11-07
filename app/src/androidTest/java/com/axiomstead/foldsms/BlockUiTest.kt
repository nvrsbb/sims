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

/** A fake block list only: the real one is never touched. */
@org.junit.runner.RunWith(RetryRunner::class)
class BlockUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "block-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val now = System.currentTimeMillis()

    private class FakeBlocker(var allowed: Boolean) : NumberBlocker {
        val list = mutableListOf<String>()
        override fun canBlock() = allowed
        override fun blocked() = if (allowed) list.toList() else emptyList()
        override fun block(address: String): Boolean { if (!allowed) return false; list += smsAddress(address); return true }
        override fun unblock(address: String): Boolean { list.removeAll { conversationAddressKey(it) == conversationAddressKey(address) }; return true }
    }

    @After fun clean() { smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun show(blocker: FakeBlocker) {
        consent.setEnabled(true)
        sms.rows = listOf(
            DeviceSms(1, 10, "2025550101", "Hello from a number", now - 60_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true),
            DeviceSms(2, 20, "PROMOBRAND", "Big sale today", now - 50_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true)
        )
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent), blocker = blocker)
        compose.setContent { MaterialTheme { FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()), wide = false) } }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openMenu(thread: Long) {
        compose.onNodeWithTag("device-thread-thread-$thread").performClick()
        compose.onNodeWithTag("device-conversation-actions").performClick()
    }

    @Test fun blockingAsksFirstThenBlocksAndTheMenuOffersUnblock() {
        val blocker = FakeBlocker(true)
        show(blocker)
        openMenu(10)
        compose.onNodeWithText("Block this number").performClick()
        compose.onNodeWithText("You will stop receiving texts and calls", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(blocker.list.isEmpty()) }
        compose.onNodeWithTag("device-conversation-actions").performClick()
        compose.onNodeWithText("Block this number").performClick()
        compose.onNodeWithTag("block-confirm").performClick()
        compose.waitUntil(UI_WAIT_MS) { blocker.list == listOf("2025550101") }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Blocked", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-conversation-actions").performClick()
        compose.onNodeWithText("Unblock this number").assertExists()
        compose.onNodeWithText("Block this number").assertDoesNotExist()
    }

    @Test fun unblockingFromTheMenuRemovesTheNumber() {
        val blocker = FakeBlocker(true).also { it.list += "2025550101" }
        show(blocker)
        openMenu(10)
        compose.onNodeWithText("Unblock this number").performClick()
        compose.waitUntil(UI_WAIT_MS) { blocker.list.isEmpty() }
    }

    @Test fun settingsListsBlockedNumbersAndUnblocksThem() {
        val blocker = FakeBlocker(true).also { it.list += "2025550101" }
        show(blocker)
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("settings-blocked-2025550101").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings-unblock-2025550101").performScrollTo().performClick()
        compose.waitUntil(UI_WAIT_MS) { blocker.list.isEmpty() }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("settings-blocked-2025550101").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun aNameSenderCannotBeBlocked() {
        show(FakeBlocker(true))
        openMenu(20)
        compose.onNodeWithText("Pin conversation").assertExists()
        compose.onNodeWithText("Block this number").assertDoesNotExist()
    }

    @Test fun withoutTheRoleThereIsNoBlockOption() {
        val blocker = FakeBlocker(false)
        show(blocker)
        openMenu(10)
        compose.onNodeWithText("Pin conversation").assertExists()
        compose.onNodeWithText("Block this number").assertDoesNotExist()
        assertEquals(0, blocker.list.size)
    }
}
