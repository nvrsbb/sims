package com.axiomstead.foldsms

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The system file picker is not opened here: the tests stop at the choice dialog. The writer itself is covered by JVM tests. */
@org.junit.runner.RunWith(RetryRunner::class)
class ExportUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "export-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()

    @After fun clean() { smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun openSettings(readable: Boolean) {
        consent.setEnabled(true)
        sms.gate = sms.gate.copy(readGranted = readable)
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent))
        compose.setContent { MaterialTheme { FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()), wide = false) } }
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-inbox-actions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-inbox-actions").performClick()
        compose.onNodeWithText("Settings").performClick()
    }

    @Test fun settingsOffersTheExportAndItAsksWhatToIncludeBeforeAnyFilePicker() {
        openSettings(readable = true)
        // The tap closes the Settings window while it is still being injected, which fails the injection; the control is
        // checked to be on screen first and then activated through its semantic click.
        compose.onNodeWithTag("settings-export").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings-export").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithTag("export-text-only").assertExists()
        compose.onNodeWithTag("export-with-attachments").assertExists()
        compose.onNodeWithText("Nothing is changed or deleted", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("export-text-only").assertDoesNotExist()
        compose.onNodeWithTag("settings-done").assertExists() // back to nothing started
    }

    @Test fun withoutReadAccessThereIsNothingToExport() {
        openSettings(readable = false)
        compose.onNodeWithTag("settings-done").assertExists()
        compose.onNodeWithTag("settings-export").assertDoesNotExist()
        assertTrue(true)
    }
}
