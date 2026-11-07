package com.axiomstead.foldsms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/*
 * Incoming MMS while FoldSMS is the default SMS app: WAP push notification -> download through the telephony service ->
 * parse -> store in the MMS provider -> acknowledge to the carrier -> notify. Nothing here runs unless the role is held.
 * The PDU parser is covered by JVM tests on fabricated PDUs; the download, provider writes and acknowledgement need a
 * real carrier and are checked on the phone (see docs/DEFAULT_SMS.md).
 */

private const val MMS_MIME = "application/vnd.wap.mms-message"
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun mmsDir(context: Context) = File(context.cacheDir, "mms").apply { mkdirs() }
private fun fileUri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.mms", file)

@Suppress("DEPRECATION")
private fun manager(context: Context, subscription: Int): SmsManager =
    if (subscription >= 0) {
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscription)
        else SmsManager.getSmsManagerForSubscriptionId(subscription)
    } else SmsManager.getDefault()

/** Fixed, non-private text: the owner learns a picture message arrived even when it could not be read. */
private fun notifyFailure(context: Context, sender: String) = showIncomingNotification(context,
    IncomingSms(sender.ifEmpty { "Picture message" }, "A picture message arrived but could not be downloaded.", System.currentTimeMillis(), -1))

class MmsPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION || intent.type != MMS_MIME) return
        val app = context.applicationContext
        // WAP_PUSH_DELIVER reaches only the default SMS app; delivery proves the role.
        val data = intent.getByteArrayExtra("data") ?: return
        val subscription = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", intent.getIntExtra("subscription", -1))
        val completion = goAsync()
        receiverScope.launch {
            try {
                val notification = parseMmsNotification(data)
                if (notification == null) notifyFailure(app, "")
                else if (!alreadyStored(app, notification.transactionId)) startDownload(app, notification, subscription)
            } catch (_: Exception) { notifyFailure(app, "") }
            finally { completion.finish() }
        }
    }
}

private fun alreadyStored(context: Context, transactionId: String): Boolean =
    context.contentResolver.query(Telephony.Mms.CONTENT_URI, arrayOf("_id"), "tr_id = ?", arrayOf(transactionId), null)?.use { it.moveToFirst() } == true

private fun startDownload(context: Context, notification: MmsNotification, subscription: Int) {
    mmsDir(context).listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000 }?.forEach { it.delete() }
    val file = File(mmsDir(context), "${UUID.randomUUID()}.dl")
    val uri = fileUri(context, file)
    context.grantUriPermission("com.android.phone", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    val result = Intent(context, MmsDownloadedReceiver::class.java).setAction(MmsDownloadedReceiver.ACTION)
        .setData(Uri.Builder().scheme("foldsms-mms").authority("download").appendPath(file.name).build())
        .putExtra("tr", notification.transactionId).putExtra("sender", notification.sender).putExtra("sub", subscription)
    // Mutable because the telephony service adds the result details to the intent.
    val pending = PendingIntent.getBroadcast(context, 0, result, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
    try { manager(context, subscription).downloadMultimediaMessage(context, notification.contentLocation, uri, null, pending) }
    catch (failure: Exception) { file.delete(); notifyFailure(context, notification.sender) }
}

/** Receives the download result from the telephony service through an app-created PendingIntent only. */
class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val name = intent.data?.takeIf { it.scheme == "foldsms-mms" && it.host == "download" }?.lastPathSegment
            ?.takeIf { Regex("[0-9a-f-]{36}\\.dl").matches(it) } ?: return
        val app = context.applicationContext
        val ok = resultCode == Activity.RESULT_OK
        val sender = intent.getStringExtra("sender").orEmpty(); val subscription = intent.getIntExtra("sub", -1)
        val transactionId = intent.getStringExtra("tr").orEmpty()
        val completion = goAsync()
        receiverScope.launch {
            val file = File(mmsDir(app), name)
            try {
                val retrieved = if (ok) file.takeIf { it.exists() && it.length() <= MAX_PDU }?.readBytes()?.let(::parseMmsRetrieved) else null
                if (retrieved == null) notifyFailure(app, sender)
                else {
                    val stored = try { storeRetrieved(app, retrieved, subscription) } catch (_: Exception) { false }
                    acknowledge(app, retrieved.transactionId.ifEmpty { transactionId }, subscription)
                    showIncomingNotification(app, IncomingSms(retrieved.sender.ifEmpty { "Picture message" }, summary(retrieved, stored), System.currentTimeMillis(), subscription))
                }
            } catch (_: Exception) { notifyFailure(app, sender) }
            finally { file.delete(); completion.finish() }
        }
    }
    companion object { const val ACTION = "com.axiomstead.foldsms.MMS_DOWNLOADED"; private const val MAX_PDU = 10_000_000L }
}

/** Stores the message; on any failure the half-written message is removed. */
private fun storeRetrieved(context: Context, message: MmsRetrieved, subscription: Int): Boolean {
    val resolver = context.contentResolver
    val now = System.currentTimeMillis() / 1000
    val thread = Telephony.Threads.getOrCreateThreadId(context, setOf(message.sender).filter { it.isNotEmpty() }.toSet().ifEmpty { setOf("unknown") })
    val values = ContentValues().apply {
        put(Telephony.Mms.THREAD_ID, thread); put(Telephony.Mms.DATE, now); put(Telephony.Mms.DATE_SENT, message.dateSeconds.takeIf { it > 0 } ?: now)
        put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX); put(Telephony.Mms.READ, 0); put(Telephony.Mms.SEEN, 0)
        put(Telephony.Mms.SUBJECT, message.subject); put(Telephony.Mms.SUBJECT_CHARSET, 106)
        put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related"); put(Telephony.Mms.MESSAGE_TYPE, 132)
        put(Telephony.Mms.MMS_VERSION, 18); put(Telephony.Mms.MESSAGE_ID, message.messageId); put(Telephony.Mms.TRANSACTION_ID, message.transactionId)
        if (subscription >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscription)
    }
    val row = resolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return false
    return try {
        val id = ContentUris.parseId(row)
        val addresses = listOf(137 to message.sender) + message.recipients.map { 151 to it }
        addresses.filter { it.second.isNotEmpty() }.forEach { (type, address) ->
            resolver.insert(Uri.parse("content://mms/$id/addr"), ContentValues().apply {
                put("msg_id", id); put("address", address); put("type", type); put("charset", 106)
            }) ?: error("address")
        }
        message.parts.forEach { part ->
            val isText = part.mimeType == "text/plain"
            val partUri = resolver.insert(Uri.parse("content://mms/$id/part"), ContentValues().apply {
                put("mid", id); put("ct", part.mimeType); put("name", part.name); put("cl", part.name)
                if (part.contentId.isNotEmpty()) put("cid", "<${part.contentId}>")
                if (isText) { put("chset", 106); put("text", part.text) }
            }) ?: error("part")
            if (!isText) (resolver.openOutputStream(partUri) ?: error("part data")).use { it.write(part.data) }
        }
        true
    } catch (failure: Exception) {
        resolver.delete(row, null, null) // never leave a message with missing parts
        false
    }
}

/** Tells the carrier the message was retrieved so it stops re-sending the notification. Best effort. */
private fun acknowledge(context: Context, transactionId: String, subscription: Int) {
    if (transactionId.isEmpty()) return
    try {
        val file = File(mmsDir(context), "${UUID.randomUUID()}.ack").apply { writeBytes(buildMmsRetrievedAck(transactionId)) }
        val uri = fileUri(context, file)
        context.grantUriPermission("com.android.phone", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        manager(context, subscription).sendMultimediaMessage(context, uri, null, null, null)
    } catch (_: Exception) { }
}
