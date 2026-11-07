package com.axiomstead.foldsms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * SMS_DELIVER reaches only the app holding the default SMS role, so this stays dormant while Google Messages is default.
 * The app is not yet eligible for the role (the MMS, send-to and respond-via-message components are not declared).
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val app = context.applicationContext
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty().map {
            IncomingSmsPart(it.originatingAddress.orEmpty(), it.messageBody.orEmpty(), it.timestampMillis)
        }
        val subscription = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", intent.getIntExtra("subscription", -1))
        val completion = goAsync()
        scope.launch {
            try { androidIncomingHandler(app).handle(parts, subscription) }
            catch (_: Exception) { }
            finally { completion.finish() }
        }
    }
    private companion object { val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}

internal fun androidIncomingHandler(context: Context) = IncomingSmsHandler(
    isDefaultApp = { Telephony.Sms.getDefaultSmsPackage(context) == context.packageName },
    store = IncomingStore { message ->
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, message.address)
            put(Telephony.Sms.BODY, message.body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, message.timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            if (message.subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, message.subscriptionId)
        }
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) != null
    },
    notifier = IncomingNotifier { message -> showIncomingNotification(context, message) }
)

private const val CHANNEL = "incoming_sms"

private fun showIncomingNotification(context: Context, message: IncomingSms) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH))
    val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    // The lock screen shows only that a message arrived; sender and text appear once the phone is unlocked.
    val hidden = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle("New message").build()
    val notification = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle(message.address).setContentText(notificationBody(message.body))
        .setStyle(NotificationCompat.BigTextStyle().bigText(message.body.trim()))
        .setCategory(NotificationCompat.CATEGORY_MESSAGE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(hidden)
        .setContentIntent(open).setAutoCancel(true).build()
    manager.notify(message.address, 0, notification) // one slot per sender: a newer message replaces the older notice
}
