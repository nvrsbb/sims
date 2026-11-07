package com.axiomstead.foldsms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony

/**
 * Writes to the SMS/MMS provider. Android allows this only for the default SMS app; every method checks the role first and
 * reports false instead of throwing, so nothing here can break sending or reading while Google Messages is default.
 */
internal class DefaultAppProviderWrites(private val context: Context) : MessageActions {
    private val rows get() = context.getSharedPreferences("sms_stored_sends", Context.MODE_PRIVATE)
    private fun isDefault() = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    override fun canModify(): Boolean = isDefault()

    /** Stores an outgoing message as queued, before it is handed to the carrier. Best effort; never blocks the send. */
    fun recordOutgoing(operationId: String, recipient: String, parts: List<String>, subscriptionId: Int) {
        if (!isDefault()) return
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, recipient); put(Telephony.Sms.BODY, joinSmsParts(parts))
                put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, SMS_TYPE_OUTBOX)
                if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
            val row = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) ?: return
            rows.edit().putString(operationId, row.toString()).apply()
        } catch (_: Exception) { }
    }

    /** Moves the stored copy to sent or failed once the journal has a final stage. */
    fun finalizeOutgoing(operationId: String, stage: String) {
        val type = storedTypeFor(stage) ?: return
        val row = rows.getString(operationId, null)?.let(Uri::parse) ?: return
        try {
            if (isDefault()) context.contentResolver.update(row, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null)
        } catch (_: Exception) { }
        rows.edit().remove(operationId).apply()
    }

    override fun markThreadRead(threadId: Long): Boolean {
        if (!isDefault() || threadId <= 0) return false
        return try {
            val read = ContentValues().apply { put("read", 1); put("seen", 1) }
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, read, "thread_id = ? AND read = 0", arrayOf(threadId.toString()))
            context.contentResolver.update(Telephony.Mms.CONTENT_URI, read, "thread_id = ? AND read = 0", arrayOf(threadId.toString()))
            true
        } catch (_: Exception) { false }
    }

    /** Deletes every SMS and MMS in the thread. Callers must have shown a confirmation first. */
    override fun deleteThread(threadId: Long): Boolean {
        if (!isDefault() || threadId <= 0) return false
        return try {
            context.contentResolver.delete(Uri.parse("content://mms-sms/conversations/$threadId"), null, null)
            true
        } catch (_: Exception) { false }
    }
}
