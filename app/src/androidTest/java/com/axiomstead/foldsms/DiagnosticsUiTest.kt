package com.axiomstead.foldsms

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Writes only invented entries to the app's diagnostics file on the test device and removes them afterwards. */
@org.junit.runner.RunWith(RetryRunner::class)
class DiagnosticsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "diagnostics-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()

    @Before fun before() { Diagnostics.clear(context) }
    @After fun clean() { Diagnostics.clear(context); smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun openSettings() {
        consent.setEnabled(true)
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent))
        compose.setContent { MaterialTheme { FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()), wide = false) } }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-actions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Settings").performClick()
    }

    @Test fun recordingAddsEntriesInOrderAndClearEmptiesTheFile() {
        Diagnostics.record(context, "TEST_ONE", "a=1")
        Diagnostics.record(context, "TEST_TWO", "b=2")
        val lines = Diagnostics.read(context).lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue("TEST_ONE" in lines[0] && "TEST_TWO" in lines[1])
        Diagnostics.clear(context)
        assertEquals(0, Diagnostics.entryCount(context))
    }

    @Test fun settingsHidesTroubleshootingWhenThereIsNothingToShow() {
        openSettings()
        compose.onNodeWithTag("settings-done").assertExists()
        compose.onNodeWithTag("settings-diagnostics-count").assertDoesNotExist()
    }

    @Test fun settingsOffersToShareAndClearWhenThereAreEntries() {
        Diagnostics.record(context, "TEST_ONE", "a=1")
        Diagnostics.record(context, "TEST_TWO", "b=2")
        openSettings()
        compose.onNodeWithTag("settings-diagnostics-count").assertTextContains("2 entries", substring = true)
        compose.onNodeWithTag("settings-diagnostics-share").assertExists()
        compose.onNodeWithTag("settings-diagnostics-clear").performScrollTo().performClick()
        compose.onNodeWithTag("settings-diagnostics-count").assertDoesNotExist()
        assertFalse(Diagnostics.entryCount(context) > 0)
    }
}
