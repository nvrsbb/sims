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

/** Fabricated rows and fake platforms only; nothing reads the real provider or sends. */
@org.junit.runner.RunWith(RetryRunner::class)
class DeviceInboxTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "device-inbox-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val mms = FakeMmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val sampleDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), mms)

    @After fun clean() {
        smsDatabase.close(); sampleDatabase.close()
        listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() }
    }

    private fun show() {
        compose.setContent {
            MaterialTheme {
                FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(sampleDatabase.deviceMetadata()), wide = false)
            }
        }
    }

    private fun inbox(id: Long, thread: Long, address: String, body: String, date: Long, type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX, read: Boolean = true) =
        DeviceSms(id, thread, address, body, date, type, 7, read)

    @Test fun beforeAccessIsAllowedOnlyTheWelcomeScreenShowsAndNothingIsRead() {
        sms.rows = listOf(inbox(1, 1, "2025550199", "Fabricated real row", 1000))
        show()
        compose.onNodeWithTag("welcome-allow").assertIsDisplayed()
        compose.onNodeWithTag("device-inbox-list").assertDoesNotExist()
        compose.onNodeWithTag("device-inbox-search").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, sms.reads); assertEquals(0, mms.reads); assertTrue(sms.requests.isEmpty()); assertTrue(!consent.enabled()) }
    }

    @Test fun optedInShowsGroupedConversationsNewestFirstWithPreviewsAndUnreadBadges() {
        consent.setEnabled(true)
        sms.rows = listOf(
            inbox(1, 10, "2025550101", "Older from Sam", 1_000, read = false),
            inbox(2, 10, "2025550101", "Newer from Sam", 2_000, read = false),
            inbox(3, 20, "BANK", "My reply to the bank", 5_000, type = Telephony.Sms.MESSAGE_TYPE_SENT),
            inbox(4, 30, "SMART", "Promo text", 3_000)
        )
        mms.rows = listOf(DeviceMms(1, 40, "2025550188", "", 4_000, 1, true, listOf(DeviceMmsPart(9, "image/jpeg", "", "pic.jpg"))))
        show()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("You: My reply to the bank").assertExists()
        compose.onNodeWithText("Newer from Sam").assertExists()
        compose.onNodeWithText("Older from Sam").assertDoesNotExist() // only the latest message previews
        compose.onNodeWithText("Photo").assertExists()
        compose.onNodeWithTag("device-unread-thread-10", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("device-unread-thread-20", useUnmergedTree = true).assertDoesNotExist()
        val order = compose.onAllNodes(hasTestTag("device-inbox-list").not() and hasTestTagStartingWith("device-thread-"))
            .fetchSemanticsNodes().map { node -> testTagOf(node) }
        assertEquals(listOf("device-thread-thread-20", "device-thread-thread-40", "device-thread-thread-30", "device-thread-thread-10"), order)
        compose.runOnIdle { assertTrue(sms.requests.isEmpty()) }
    }

    @Test fun missingReadPermissionExplainsAndOffersPermissionsWithoutReading() {
        consent.setEnabled(true)
        sms.gate = sms.gate.copy(readGranted = false)
        show()
        compose.onNodeWithText("Allow SMS access to see your messages", substring = true).assertExists()
        compose.onNodeWithTag("device-request-permissions").assertExists()
        compose.onNodeWithTag("device-inbox-list").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, sms.reads) }
    }

    @Test fun emptyProviderAndNoSearchMatchesShowDistinctMessages() {
        consent.setEnabled(true)
        show()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("No messages yet", substring = true).fetchSemanticsNodes().isNotEmpty() }
        sms.rows = listOf(inbox(1, 1, "2025550101", "Lunch tomorrow", 1_000))
        compose.runOnIdle { sms.events.value++ }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-inbox-search").performTextInput("zzz")
        compose.onNodeWithText("No conversations match").assertExists()
        compose.onNodeWithTag("device-inbox-search").performTextReplacement("lunch")
        compose.onNodeWithText("Lunch tomorrow").assertExists()
    }

    @Test fun providerFailureShowsAnErrorInsteadOfCrashing() {
        consent.setEnabled(true)
        mms.failRead = true
        sms.rows = listOf(inbox(1, 1, "2025550101", "SMS still shown", 1_000))
        show()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-error").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("SMS still shown").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun turningOffAccessInSettingsReturnsToWelcomeAndStopsReading() {
        consent.setEnabled(true)
        sms.rows = listOf(inbox(1, 1, "2025550101", "Fabricated real row", 1_000))
        show()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("settings-turn-off").performClick()
        compose.onNodeWithTag("welcome-allow").assertIsDisplayed()
        compose.runOnIdle { assertTrue(!consent.enabled()) }
        val readsAfterOff = sms.reads
        compose.runOnIdle { sms.events.value++ }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(readsAfterOff, sms.reads) }
    }

    @Test fun theMenuHasOnlyArchivedAndSettings() {
        consent.setEnabled(true)
        sms.rows = listOf(inbox(1, 1, "2025550101", "Fabricated real row", 1_000))
        show()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Archived").assertExists()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Request permissions").assertDoesNotExist()
        compose.onNodeWithText("Sample data").assertDoesNotExist()
    }

    @Test fun everyCategoryChipIsVisibleOnOneLineWithoutScrolling() {
        consent.setEnabled(true)
        sms.rows = listOf(inbox(1, 1, "2025550101", "Fabricated real row", 1_000))
        show()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        val tops = listOf("All", "Personal", "Finance", "Services", "Promotions", "OTP").map { name ->
            compose.onNodeWithTag("device-category-filter-$name").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.y
        }
        assertTrue("all chips share one row: $tops", tops.max() - tops.min() < 8f)
        compose.onNodeWithTag("device-category-filter-Unknown").assertDoesNotExist() // nothing is Unknown
    }

    @Test fun conversationsShowAnAutomaticCategoryThatTheFilterUses() {
        consent.setEnabled(true)
        sms.rows = listOf(
            inbox(1, 10, "2025550101", "See you tonight", 3_000),
            inbox(2, 20, "EXAMPLEBANK", "Your verification code is 482913", 2_000),
            inbox(3, 30, "PARCELCO", "Your parcel is out for delivery", 1_000)
        )
        show()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-category-thread-10", useUnmergedTree = true).assertTextEquals("Personal")
        compose.onNodeWithTag("device-category-thread-20", useUnmergedTree = true).assertTextEquals("OTP")
        compose.onNodeWithTag("device-category-thread-30", useUnmergedTree = true).assertTextEquals("Services")
        compose.onNodeWithTag("device-category-filter-OTP").performClick()
        compose.onNodeWithTag("device-thread-thread-20").assertExists()
        compose.onNodeWithTag("device-thread-thread-10").assertDoesNotExist()
    }

    @Test fun searchAndMenuShareOneRowAtTheTopAndThereIsNoAppTitle() {
        consent.setEnabled(true)
        sms.rows = listOf(inbox(1, 1, "2025550101", "Fabricated real row", 1_000))
        show()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        val search = compose.onNodeWithTag("device-inbox-search").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("device-inbox-actions").fetchSemanticsNode().boundsInRoot
        assertTrue("menu sits to the right of search on the same row", menu.left >= search.right - 1f && kotlin.math.abs(menu.center.y - search.center.y) < 40f)
        compose.onNodeWithText("FoldSMS").assertDoesNotExist()
        compose.onNodeWithText("Messages").assertDoesNotExist()
    }

    private fun testTagOf(node: androidx.compose.ui.semantics.SemanticsNode): String? =
        if (androidx.compose.ui.semantics.SemanticsProperties.TestTag in node.config) node.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] else null

    @Test fun deniedMmsPermissionKeepsSmsAvailableAndExplains() {
        consent.setEnabled(true)
        mms.granted = false
        sms.rows = listOf(inbox(30, 1, "2025550199", "Fabricated SMS", 1234))
        show()
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Allow MMS access to see pictures", substring = true).assertExists()
        compose.onNodeWithText("Fabricated SMS").assertExists()
        compose.runOnIdle { assertEquals(0, mms.reads); assertTrue(sms.requests.isEmpty()) }
    }

    private fun hasTestTagStartingWith(prefix: String) = SemanticsMatcher("TestTag starts with $prefix") {
        testTagOf(it)?.startsWith(prefix) == true
    }
}
