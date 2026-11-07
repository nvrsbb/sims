package com.axiomstead.foldsms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast

internal const val CODE_CLIP_LABEL = "One-time code"
internal const val CLIP_CLEAR_AFTER_MS = 60_000L

/**
 * Puts a one-time code on the clipboard, marked sensitive so Android does not show it in the clipboard preview, and
 * schedules clearing it after a minute (see [ClipboardClearReceiver]).
 */
internal fun copyCodeToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(CODE_CLIP_LABEL, code)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    clipboard.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show() // newer Android confirms by itself
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return
    val clear = PendingIntent.getBroadcast(context, 0, Intent(context, ClipboardClearReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    alarms.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + CLIP_CLEAR_AFTER_MS, clear)
}

/**
 * Clears the clipboard a minute after a code was copied. When Android lets us see what is on it, it is cleared only if it is
 * still our code; from the background Android hides that, so it is cleared anyway. A newer copy within that minute is lost.
 */
class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val label = try { clipboard.primaryClipDescription?.label } catch (_: Exception) { null }
        if (label == null || label == CODE_CLIP_LABEL) try { clipboard.clearPrimaryClip() } catch (_: Exception) { }
    }
}

/** The "Copy code" button of a notification: copies, marks the conversation read (as default app) and removes the notice. */
class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val code = intent.getStringExtra("code")?.takeIf { it.length in 4..8 && it.all(Char::isDigit) } ?: return
        val address = intent.getStringExtra("address").orEmpty()
        val tag = intent.getStringExtra("tag")
        val app = context.applicationContext
        copyCodeToClipboard(app, code)
        tag?.let { app.getSystemService(android.app.NotificationManager::class.java)?.cancel(it, 0) }
        if (address.isNotEmpty() && isDefaultSmsApp(app)) {
            val completion = goAsync()
            Thread {
                try {
                    val thread = android.provider.Telephony.Threads.getOrCreateThreadId(app, address)
                    DefaultAppProviderWrites(app).markThreadRead(thread)
                } catch (_: Exception) { } finally { completion.finish() }
            }.start()
        }
    }
    companion object { const val ACTION = "com.axiomstead.foldsms.COPY_CODE" }
}
