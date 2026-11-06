package com.axiomstead.foldsms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal data class DeviceMmsPart(val id: Long, val mimeType: String, val text: String, val name: String) {
    val image get() = mimeType.startsWith("image/")
    val attachment get() = mimeType != "text/plain" && mimeType != "application/smil"
}

internal data class DeviceMms(val id: Long, val threadId: Long, val address: String, val subject: String,
    val date: Long, val box: Int, val read: Boolean, val parts: List<DeviceMmsPart>) {
    val text get() = parts.filter { it.mimeType == "text/plain" }.joinToString("\n") { it.text }.take(10_000)
    val attachments get() = parts.filter { it.attachment }
}

internal interface MmsPlatform {
    fun canRead(): Boolean
    fun recent(): List<DeviceMms>
    fun image(partId: Long): Bitmap?
    fun changes(): Flow<Unit>
}

internal object MmsReceiveEvents {
    val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}

/** A notification only. The default app downloads, persists, and notifies about the MMS. */
class DeviceMmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION || intent.type != "application/vnd.wap.mms-message") return
        val consent = SmsConsentStore(context.getSharedPreferences("sms_companion_access", Context.MODE_PRIVATE))
        if (consent.enabled() && ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_MMS) == PackageManager.PERMISSION_GRANTED)
            MmsReceiveEvents.events.tryEmit(Unit)
    }
}

internal class AndroidMmsPlatform(private val context: Context, private val consent: SmsConsentStore) : MmsPlatform {
    override fun canRead(): Boolean = AndroidSmsPlatform(context, consent).access().canRead &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_MMS) == PackageManager.PERMISSION_GRANTED

    override fun recent(): List<DeviceMms> {
        check(canRead()) { "MMS read access is unavailable." }
        val resolver = context.contentResolver
        val columns = arrayOf("_id", "thread_id", "date", "msg_box", "read", "sub")
        return resolver.query(Telephony.Mms.CONTENT_URI, columns, null, null, "date DESC, _id DESC")?.use { cursor ->
            buildList {
                while (size < 100 && cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val addressUri = Telephony.Mms.CONTENT_URI.buildUpon().appendPath(id.toString()).appendPath("addr").build()
                    val addresses = resolver.query(addressUri, arrayOf("address", "type"), null, null, null)?.use { addr ->
                        buildList {
                            while (addr.moveToNext()) {
                                val number = addr.getString(0).orEmpty()
                                if (number.isNotBlank() && number != "insert-address-token") add(addr.getInt(1) to number)
                            }
                        }
                    }.orEmpty()
                    val box = cursor.getInt(3)
                    val address = addresses.firstOrNull { it.first == if (box == 1) 137 else 151 }?.second
                        ?: addresses.firstOrNull()?.second.orEmpty()
                    val partUri = Telephony.Mms.CONTENT_URI.buildUpon().appendPath(id.toString()).appendPath("part").build()
                    val parts = resolver.query(partUri, arrayOf("_id", "ct", "text", "name", "fn"), null, null, "seq ASC")?.use { part ->
                        buildList {
                            while (size < 30 && part.moveToNext()) {
                                val mime = part.getString(1).orEmpty().lowercase().substringBefore(';').trim()
                                val label = part.getString(3).orEmpty().ifBlank { part.getString(4).orEmpty() }
                                add(DeviceMmsPart(part.getLong(0), mime, part.getString(2).orEmpty().take(10_000), label.take(200)))
                            }
                        }
                    }.orEmpty()
                    add(DeviceMms(id, cursor.getLong(1), address, cursor.getString(5).orEmpty().take(500),
                        cursor.getLong(2).coerceIn(0, Long.MAX_VALUE / 1000) * 1000, box, cursor.getInt(4) != 0, parts))
                }
            }
        } ?: emptyList()
    }

    override fun image(partId: Long): Bitmap? {
        check(canRead()) { "MMS read access is unavailable." }
        val uri = Telephony.Mms.Part.CONTENT_URI.buildUpon().appendPath(partId.toString()).build()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    override fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        var observing = false
        if (canRead()) try {
            context.contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer)
            observing = true
        } catch (_: SecurityException) { trySend(Unit) }
        val events = launch { MmsReceiveEvents.events.collect { trySend(Unit) } }
        val stopConsent = consent.listen { trySend(Unit) }
        trySend(Unit)
        awaitClose {
            if (observing) context.contentResolver.unregisterContentObserver(observer)
            events.cancel()
            stopConsent()
        }
    }.conflate()
}
