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

    @After fun clean() { target.delete(); smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private val target = java.io.File(context.cacheDir, "export-test-${UUID.randomUUID()}.xml")
    private val mms = FakeMmsPlatform(consent)

    private fun openSettings(readable: Boolean) {
        consent.setEnabled(true)
        sms.gate = sms.gate.copy(readGranted = readable)
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), mms)
        compose.setContent {
            MaterialTheme {
                FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = DeviceMetadataStore(metaDatabase.deviceMetadata()), wide = false,
                    exportTarget = { android.net.Uri.fromFile(target) })
            }
        }
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

    private fun startExport(includeAttachments: Boolean) {
        compose.onNodeWithTag("settings-export").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings-export").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithTag(if (includeAttachments) "export-with-attachments" else "export-text-only").performClick()
    }

    private fun parsed() = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(target)

    @Test fun exportWritesAReadableFileWithEveryMessageAndReportsIt() {
        val now = System.currentTimeMillis()
        sms.rows = (1L..3L).map { DeviceSms(it, it, "2025550$it", "Invented message $it with & <markup>", now - it * 1_000, 1, 7, true) }
        openSettings(readable = true)
        startExport(includeAttachments = false)
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Saved 3 SMS and 0 MMS.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val doc = parsed()
        assertEquals("3", doc.documentElement.getAttribute("count"))
        assertEquals(3, doc.getElementsByTagName("sms").length)
        assertTrue((0 until 3).any { (doc.getElementsByTagName("sms").item(it) as org.w3c.dom.Element).getAttribute("body") == "Invented message 2 with & <markup>" })
        compose.onNodeWithTag("export-done").performClick()
        compose.onNodeWithTag("export-status").assertDoesNotExist()
    }

    @Test fun exportWithAttachmentsIncludesPictureDataAndCountsIt() {
        val now = System.currentTimeMillis()
        sms.rows = listOf(DeviceSms(1, 1, "2025550101", "Hello", now - 5_000, 1, 7, true))
        mms.rows = listOf(DeviceMms(9, 2, "2025550102", "Photo", now - 1_000, 1, true, listOf(
            DeviceMmsPart(41, "text/plain", "A caption", "text_0.txt"), DeviceMmsPart(42, "image/jpeg", "", "photo.jpg"))))
        mms.partBytes[42] = byteArrayOf(1, 2, 3, 4, 5)
        openSettings(readable = true)
        startExport(includeAttachments = true)
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithText("Saved 1 SMS and 1 MMS.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 attachments included", substring = true).assertExists()
        val parts = parsed().getElementsByTagName("part")
        assertEquals(2, parts.length)
        assertEquals(java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4, 5)), (parts.item(1) as org.w3c.dom.Element).getAttribute("data"))
    }

    @Test fun withoutReadAccessThereIsNothingToExport() {
        openSettings(readable = false)
        compose.onNodeWithTag("settings-done").assertExists()
        compose.onNodeWithTag("settings-export").assertDoesNotExist()
        assertTrue(true)
    }
}
