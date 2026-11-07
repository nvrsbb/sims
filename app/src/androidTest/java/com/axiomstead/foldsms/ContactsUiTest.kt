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

/** Fake contacts and fake SMS platform only; the real contact list and provider are never touched. */
@org.junit.runner.RunWith(RetryRunner::class)
class ContactsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "contacts-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val now = System.currentTimeMillis()

    private class FakeContacts(var allowed: Boolean, val entries: List<ContactEntry>) : ContactsPlatform {
        override fun canRead() = allowed
        override fun all() = if (allowed) entries else emptyList()
    }

    @After fun clean() { smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private val ana = ContactEntry("Ana Cruz", "0917 555 0199")

    private fun show(contacts: FakeContacts) {
        consent.setEnabled(true)
        sms.rows = listOf(DeviceSms(1, 10, "+639175550199", "Are you coming?", now - 60_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true))
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent), contacts = contacts)
        compose.setContent { MaterialTheme { FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()), wide = false) } }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun aContactNameReplacesTheNumberInTheListAndTheConversation() {
        show(FakeContacts(true, listOf(ana)))
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Ana Cruz").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("+639175550199").assertDoesNotExist()
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithTag("device-conversation-title").assertTextEquals("Ana Cruz")
    }

    @Test fun withoutContactPermissionNumbersStayAsTheyAre() {
        show(FakeContacts(false, listOf(ana)))
        compose.onNodeWithText("+639175550199").assertExists()
        compose.onNodeWithText("Ana Cruz").assertDoesNotExist()
    }

    @Test fun searchingTheInboxByNameFindsTheThread() {
        show(FakeContacts(true, listOf(ana)))
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Ana Cruz").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-inbox-search").performTextInput("cruz")
        compose.onNodeWithTag("device-thread-thread-10").assertExists()
    }

    @Test fun newMessageFindsAContactByNameAndOpensItsConversationWithoutSendingAnything() {
        show(FakeContacts(true, listOf(ana, ContactEntry("Ben Reyes", "+63 918 555 0123"))))
        compose.onNodeWithTag("device-new-message").performClick()
        compose.onNodeWithTag("new-message-number").performTextInput("ben")
        compose.onNodeWithTag("new-message-contact-0").assertExists()
        compose.onNodeWithTag("new-message-contact-1").assertDoesNotExist()
        compose.onNodeWithTag("new-message-contact-0").performClick()
        compose.onNodeWithTag("device-conversation-title").assertTextEquals("Ben Reyes")
        compose.runOnIdle { org.junit.Assert.assertTrue(sms.requests.isEmpty()) }
    }

    @Test fun withoutPermissionTheDialogOffersToAllowContacts() {
        show(FakeContacts(false, listOf(ana)))
        compose.onNodeWithTag("device-new-message").performClick()
        compose.onNodeWithTag("new-message-allow-contacts").assertExists()
        compose.onNodeWithTag("new-message-number").performTextInput("ana")
        compose.onNodeWithTag("new-message-contact-0").assertDoesNotExist()
    }
}
