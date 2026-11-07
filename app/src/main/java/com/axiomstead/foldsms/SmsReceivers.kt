package com.axiomstead.foldsms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** SMS_RECEIVED is a notification only; the external default app owns inbox insertion and notifications. */
class DeviceSmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val enabled = SmsConsentStore(context.getSharedPreferences("sms_companion_access", Context.MODE_PRIVATE)).enabled()
        if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
            SmsReceiveEvents.events.tryEmit(Unit)
    }
}

/** Only explicit immutable app-owned PendingIntents can reach this non-exported receiver. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val uri = intent.data ?: return
        if (uri.scheme != "foldsms-sent" || uri.host != "result" || uri.pathSegments.size != 2) return
        val id = runCatching { UUID.fromString(uri.pathSegments[0]).toString() }.getOrNull() ?: return
        val part = uri.pathSegments[1].toIntOrNull()?.takeIf { it in 0 until MAX_SMS_PARTS } ?: return
        val code = resultCode
        val completion = goAsync()
        receiverScope.launch {
            try {
                val services = smsServices(context)
                services.controller.result(id, part, code)
                services.journal.attempt(id)?.stage?.let { DefaultAppProviderWrites(context).finalizeOutgoing(id, it) }
            }
            catch (_: Exception) { /* Unrecorded outcomes remain unconfirmed; no resend or private-text logging. */ }
            finally { completion.finish() }
        }
    }
    companion object {
        const val ACTION = "com.axiomstead.foldsms.SMS_SENT_RESULT"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
