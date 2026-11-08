package com.axiomstead.foldsms

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Fake contacts and a captured intent launcher: the real contacts and the system editors are never opened. */
@org.junit.runner.RunWith(RetryRunner::class)
class ContactsScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "contacts-screen-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val now = System.currentTimeMillis()
    private val launched = mutableListOf<Intent>()

    @After fun clean() { smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private val contacts = listOf(
        ContactEntry("Ben Reyes", "+63 918 555 0123", 12, "lk-12"),
        ContactEntry("Ana Cruz", "0917 555 0199", 11, "lk-11"), ContactEntry("Ana Cruz", "(02) 8555-0100", 11, "lk-11")
    )

    private fun show(list: FakeContactsList = FakeContactsList(true, contacts)) {
        consent.setEnabled(true)
        sms.rows = listOf(
            DeviceSms(1, 10, "09175550199", "From Ana", now - 60_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true),
            DeviceSms(2, 20, "2025550102", "From an unsaved number", now - 50_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true),
            DeviceSms(3, 30, "EXAMPLEBANK", "From a name", now - 40_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true)
        )
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent), contacts = list)
        compose.setContent {
            MaterialTheme {
                FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()), wide = false,
                    intentLauncher = { launched += it })
            }
        }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openContacts() {
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Contacts").performClick()
    }

    @Test fun theScreenListsEachPersonOnceSortedAndSearchFilters() {
        show(); openContacts()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("contacts-row-Ana Cruz").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("contacts-row-Ben Reyes").assertExists()
        compose.onNodeWithText("0917 555 0199 +1").assertExists() // the second number is counted, not listed as a second row
        compose.onNodeWithTag("contacts-search").performTextInput("reyes")
        compose.onNodeWithTag("contacts-row-Ana Cruz").assertDoesNotExist()
        compose.onNodeWithTag("contacts-search").performTextClearance()
        compose.onNodeWithTag("contacts-search").performTextInput("zzz")
        compose.onNodeWithTag("contacts-none").assertExists()
    }

    @Test fun aPersonShowsEveryNumberAndMessageOpensTheConversation() {
        show(); openContacts()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("contacts-row-Ana Cruz").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("contacts-row-Ana Cruz").performClick()
        compose.onAllNodesWithTag("contacts-number").assertCountEquals(2)
        compose.onAllNodesWithTag("contacts-message")[0].performClick()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-conversation-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-conversation-title").assertTextEquals("Ana Cruz")
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()) } // choosing a number sends nothing
    }

    @Test fun addAndEditHandOverToTheSystemEditors() {
        show(); openContacts()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("contacts-row-Ben Reyes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("contacts-add").performClick()
        compose.runOnIdle { assertEquals(ContactsContract.Intents.Insert.ACTION, launched.last().action) }
        compose.onNodeWithTag("contacts-row-Ben Reyes").performClick()
        compose.onNodeWithTag("contacts-edit").performClick()
        compose.runOnIdle {
            assertEquals(Intent.ACTION_EDIT, launched.last().action)
            assertEquals(ContactsContract.Contacts.getLookupUri(12, "lk-12"), launched.last().data)
        }
    }

    @Test fun withoutPermissionTheScreenOffersToAllowContacts() {
        show(FakeContactsList(false, contacts)); openContacts()
        compose.onNodeWithTag("contacts-allow").assertExists()
        compose.onNodeWithTag("contacts-add").assertDoesNotExist()
    }

    @Test fun aConversationOffersAddToContactsOrEditContactOrNeither() {
        show()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Ana Cruz").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-thread-thread-20").performClick() // an unsaved number
        compose.onNodeWithTag("device-conversation-actions").performClick()
        compose.onNodeWithText("Edit contact").assertDoesNotExist()
        compose.onNodeWithText("Add to contacts").performClick()
        compose.runOnIdle {
            val intent = launched.last()
            assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.action)
            assertEquals("2025550102", intent.getStringExtra(ContactsContract.Intents.Insert.PHONE))
        }
        compose.onNodeWithTag("device-conversation-back").performClick()
        compose.onNodeWithTag("device-thread-thread-10").performClick() // Ana, saved
        compose.onNodeWithTag("device-conversation-actions").performClick()
        compose.onNodeWithText("Add to contacts").assertDoesNotExist()
        compose.onNodeWithText("Edit contact").performClick()
        compose.runOnIdle { assertEquals(ContactsContract.Contacts.getLookupUri(11, "lk-11"), launched.last().data) }
        compose.onNodeWithTag("device-conversation-back").performClick()
        compose.onNodeWithTag("device-thread-thread-30").performClick() // a name sender
        compose.onNodeWithTag("device-conversation-actions").performClick()
        compose.onNodeWithText("Add to contacts").assertDoesNotExist()
        compose.onNodeWithText("Edit contact").assertDoesNotExist()
    }

    @Test fun theIntentBuildersCarryWhatTheSystemEditorsNeed() {
        assertEquals(ContactsContract.Intents.Insert.ACTION, newContactIntent().action)
        val add = addToContactsIntent("+639175550199")
        assertEquals(Intent.ACTION_INSERT_OR_EDIT, add.action)
        assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, add.type)
        assertEquals("+639175550199", add.getStringExtra(ContactsContract.Intents.Insert.PHONE))
        assertNotNull(editContactIntent(5, "abc"))
        assertEquals(null, editContactIntent(0, "abc")); assertEquals(null, editContactIntent(5, ""))
        assertEquals(null, editIntentForNumber(contacts, "2025559999"))
        assertNotNull(editIntentForNumber(contacts, "+63 917 555 0199"))
    }
}
