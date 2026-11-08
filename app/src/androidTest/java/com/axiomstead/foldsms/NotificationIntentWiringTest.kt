package com.axiomstead.foldsms

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The tap on a notification arrives as an intent with the sender's address. This starts the real activity with exactly the
 * intent the notification builds and checks that it reaches the inbox, using the app's own troubleshooting log as the witness.
 * The emulator has no messages, so the conversation is "not found"; what is checked is that the request is received and handled.
 */
class NotificationIntentWiringTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Before fun before() {
        Diagnostics.clear(context)
        listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECEIVE_MMS)
            .forEach { runCatching { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) } }
        SmsConsentStore(context.getSharedPreferences("sms_companion_access", Context.MODE_PRIVATE)).setEnabled(true)
    }

    @After fun after() {
        SmsConsentStore(context.getSharedPreferences("sms_companion_access", Context.MODE_PRIVATE)).setEnabled(false)
        Diagnostics.clear(context)
    }

    private fun waitForLog(text: String): Boolean {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) { if (text in Diagnostics.read(context)) return true; Thread.sleep(300) }
        return false
    }

    @Test fun theIntentOfANotificationReachesTheInboxAndIsHandled() {
        val intent = Intent(context, MainActivity::class.java).setAction(OPEN_CONVERSATION_ACTION)
            .setData(android.net.Uri.Builder().scheme("foldsms").authority("conversation").appendPath("+15550100").build())
            .putExtra(EXTRA_OPEN_ADDRESS, "+15550100")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        ActivityScenario.launch<MainActivity>(intent).use {
            assertTrue("the activity did not receive the address: ${Diagnostics.read(context)}", waitForLog("arrived=onCreate"))
            assertTrue("the inbox did not handle the request: ${Diagnostics.read(context)}", waitForLog("NOTIFICATION_OPEN result="))
        }
    }

    @Test fun aSecondTapWhileTheScreenIsOpenArrivesAsANewIntent() {
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            val tap = Intent(context, MainActivity::class.java).setAction(OPEN_CONVERSATION_ACTION).putExtra(EXTRA_OPEN_ADDRESS, "+15550101")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // What the system does for a tap on a notification while the screen is open: it calls onNewIntent on the running one.
            scenario.onActivity { activity ->
                MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply { isAccessible = true }.invoke(activity, tap) // protected in Android
            }
            assertTrue("no new intent arrived: ${Diagnostics.read(context)}", waitForLog("arrived=onNewIntent"))
            assertTrue("the inbox did not handle the request: ${Diagnostics.read(context)}", waitForLog("NOTIFICATION_OPEN result="))
        } finally {
            // Closing waits for the screen to be destroyed, which an emulator sometimes does not report in time; that is not what
            // this test is about, so the finish is requested and a late report is ignored.
            runCatching { scenario.onActivity { it.finish() } }
            runCatching { scenario.close() }
        }
    }
}
