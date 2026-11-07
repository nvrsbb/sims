package com.axiomstead.foldsms

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(RetryRunner::class)
class SmsReceiverTest {
    @Test fun incomingNotificationRequiresConsentAndPermissionWithoutParsingOrWritingMessages() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = app.getSharedPreferences("sms-receiver-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        var granted = false
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.RECEIVE_SMS && granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
        val receiver = DeviceSmsReceivedReceiver()
        val notification = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        suspend fun expectEvent(expected: Boolean, action: Intent = notification) {
            val event = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(200) { SmsReceiveEvents.events.first(); true } }
            receiver.onReceive(context, action)
            assertEquals(expected, event.await() == true)
        }
        try {
            expectEvent(false)
            SmsConsentStore(preferences).setEnabled(true)
            expectEvent(false)
            granted = true
            expectEvent(false, Intent("fabricated.invalid.action"))
            expectEvent(true)
            SmsConsentStore(preferences).setEnabled(false)
            expectEvent(false)
        } finally { preferences.edit().clear().commit() }
    }
}
