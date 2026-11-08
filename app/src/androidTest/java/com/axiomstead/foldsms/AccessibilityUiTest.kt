package com.axiomstead.foldsms

import android.content.Context
import android.provider.Telephony
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Fake platform only. Checks what a screen reader and a thumb need: a name for every control, 48 dp targets, large fonts. */
@org.junit.runner.RunWith(RetryRunner::class)
class AccessibilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "a11y-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val now = System.currentTimeMillis()

    @After fun clean() { smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun show(fontScale: Float = 1f) {
        consent.setEnabled(true)
        sms.rows = listOf(
            DeviceSms(1, 10, "2025550101", "Are you coming later?", now - 60_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, false),
            DeviceSms(2, 20, "EXAMPLEBANK", "Your verification code is 482913", now - 30_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, false)
        )
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent), FakeActions(true), DefaultPromptStore(samplePrefs))
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MaterialTheme { FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()), wide = false) }
            }
        }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-list").fetchSemanticsNodes().isNotEmpty() }
    }

    /** The area that reacts to a touch is at least 48 dp each way, the size Android recommends for a thumb. */
    private fun SemanticsNodeInteraction.assertTouchAtLeast(minimumDp: Float = 48f): SemanticsNodeInteraction {
        val node = fetchSemanticsNode()
        val density = node.layoutInfo.density.density
        val bounds = node.touchBoundsInRoot
        assertTrue("touch area is ${bounds.width / density} x ${bounds.height / density} dp, below $minimumDp dp",
            bounds.width / density >= minimumDp - 0.5f && bounds.height / density >= minimumDp - 0.5f)
        return this
    }

    /** Every control a person can activate must have words a screen reader can speak. */
    private fun assertEveryClickableHasAName(screen: String) {
        val unnamed = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = false).fetchSemanticsNodes().filter { node ->
            val config = node.config
            val texts = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
            val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString("")
            texts.isBlank() && description.isBlank()
        }
        assertTrue("$screen: ${unnamed.size} clickable controls have no text or description", unnamed.isEmpty())
    }

    @Test fun everyControlOnTheMainScreensHasAName() {
        show()
        assertEveryClickableHasAName("inbox")
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        assertEveryClickableHasAName("conversation")
        compose.onNodeWithTag("device-conversation-back").performClick()
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Settings").performClick()
        assertEveryClickableHasAName("settings")
    }

    @Test fun mainTargetsAreAtLeast48dpAndTheFilterChipsKnowTheirState() {
        show()
        listOf("device-category-filter-All", "device-category-filter-Personal", "device-category-filter-Business", "device-new-message",
            "device-inbox-actions", "device-thread-thread-10", "device-thread-thread-20").forEach { tag ->
            compose.onNodeWithTag(tag).assertTouchAtLeast()
        }
        compose.onNodeWithTag("device-category-filter-All").assertIsSelected()
        compose.onNodeWithTag("device-category-filter-Personal").performClick()
        compose.onNodeWithTag("device-category-filter-Personal").assertIsSelected()
        compose.onNodeWithTag("device-category-filter-All").assertIsNotSelected()
    }

    @Test fun theReplyBoxAndItsButtonAreBigEnoughAndNamed() {
        show()
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithTag("reply-send").assertTouchAtLeast()
        compose.onNodeWithContentDescription("Send SMS").assertExists()
        compose.onNodeWithTag("device-conversation-back").assertTouchAtLeast()
        compose.onNodeWithTag("conversation-search").assertTouchAtLeast()
    }

    @Test fun aRowTellsItsUnreadCountToAScreenReader() {
        show()
        val state = compose.onNodeWithTag("device-thread-thread-10").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertTrue("row state was $state", state != null && "1 unread" in state)
    }

    @Test fun atTheLargestFontEverythingStaysReachable() {
        show(fontScale = 2f)
        listOf("device-category-filter-All", "device-category-filter-Personal", "device-category-filter-Business").forEach { tag ->
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("device-category-filter-Business").performClick()
        compose.onNodeWithTag("device-category-filter-Business").assertIsSelected()
        compose.onNodeWithTag("device-category-filter-All").performClick()
        compose.onNodeWithTag("device-new-message").assertIsDisplayed()
        compose.onNodeWithTag("device-thread-thread-10").performClick()
        compose.onNodeWithTag("reply-composer").assertIsDisplayed()
        compose.onNodeWithTag("reply-send").assertIsDisplayed()
        compose.onNodeWithTag("device-conversation-back").performClick()
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("settings-turn-off").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings-done").assertIsDisplayed()
    }
}
