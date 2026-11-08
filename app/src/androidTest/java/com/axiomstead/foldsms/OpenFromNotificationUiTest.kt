package com.axiomstead.foldsms

import android.content.Context
import android.provider.Telephony
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** The notification asks the app to open one sender's conversation; here that request is passed in directly. */
@org.junit.runner.RunWith(RetryRunner::class)
class OpenFromNotificationUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "open-${UUID.randomUUID()}"
    private val consentPrefs = context.getSharedPreferences("$prefix-consent", Context.MODE_PRIVATE)
    private val smsDraftPrefs = context.getSharedPreferences("$prefix-sms", Context.MODE_PRIVATE)
    private val samplePrefs = context.getSharedPreferences("$prefix-sample", Context.MODE_PRIVATE)
    private val consent = SmsConsentStore(consentPrefs)
    private val sms = FakeSmsPlatform(consent)
    private val smsDatabase = Room.inMemoryDatabaseBuilder(context, SmsSendDatabase::class.java).build()
    private val metaDatabase = Room.inMemoryDatabaseBuilder(context, ConversationMetadataDatabase::class.java).build()
    private val device by lazy { DeviceMetadataStore(metaDatabase.deviceMetadata()) }
    private val now = System.currentTimeMillis()
    private var handled by mutableStateOf(false)

    @After fun clean() { smsDatabase.close(); metaDatabase.close(); listOf(consentPrefs, smsDraftPrefs, samplePrefs).forEach { it.edit().clear().commit() } }

    private fun show(address: String) {
        consent.setEnabled(true)
        sms.rows = listOf(
            DeviceSms(1, 10, "2025550101", "From the first number", now - 60_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true),
            DeviceSms(2, 20, "2025550102", "From the second number", now - 50_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true),
            DeviceSms(3, 30, "EXAMPLEBANK", "Your balance", now - 40_000, Telephony.Sms.MESSAGE_TYPE_INBOX, 7, true)
        )
        val services = SmsServices(sms, consent, SmsDraftStore(smsDraftPrefs), smsDatabase.sends(), FakeMmsPlatform(consent))
        compose.setContent {
            MaterialTheme {
                FoldSmsApp(ThemeStore(samplePrefs), smsServices = services, deviceMetadataStore = device, wide = false,
                    openAddress = address, onOpenAddressHandled = { handled = true })
            }
        }
    }

    private fun titleIs(text: String) {
        compose.waitUntil(UI_WAIT_MS) { compose.onAllNodesWithTag("device-conversation-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("device-conversation-title").assertTextEquals(text)
    }

    @Test fun aNumberOpensItsConversationEvenWrittenAnotherWay() {
        show("+1 (202) 555-0102")
        titleIs("2025550102")
    }

    @Test fun aNameSenderOpensItsConversation() {
        show("EXAMPLEBANK")
        titleIs("EXAMPLEBANK")
    }

    @Test fun anArchivedConversationIsOpenedToo() {
        runBlocking { device.setArchived("thread-20", true) }
        show("2025550102")
        titleIs("2025550102")
    }

    @Test fun aSenderThatIsNotThereLeavesTheListAndTheRequestIsDropped() {
        show("2025559999")
        compose.waitUntil(UI_WAIT_MS) { handled }
        compose.onNodeWithTag("device-inbox-list").assertExists()
        compose.onNodeWithTag("device-conversation-title").assertDoesNotExist()
    }
}
