package com.axiomstead.foldsms

/** SMS provider message types (Telephony.Sms.MESSAGE_TYPE_*), repeated here so the rules run in plain JVM tests. */
internal const val SMS_TYPE_SENT = 2
internal const val SMS_TYPE_OUTBOX = 4
internal const val SMS_TYPE_FAILED = 5

/**
 * The provider type a stored outgoing message should have for a send-journal stage, or null to leave it as queued.
 * A partly sent message is marked failed: the owner must see that not every part went out.
 */
internal fun storedTypeFor(stage: String): Int? = when (stage) {
    "SENT" -> SMS_TYPE_SENT
    "FAILED", "PARTIAL" -> SMS_TYPE_FAILED
    else -> null
}

/** Divided SMS parts concatenate back to the text that was reviewed and confirmed. */
internal fun joinSmsParts(parts: List<String>): String = parts.joinToString("")

/** Thread actions that only the default SMS app may perform. */
internal interface MessageActions {
    fun canModify(): Boolean
    fun markThreadRead(threadId: Long): Boolean
    /** Makes one received message unread again so its conversation shows as unread. */
    fun markUnread(kind: DeviceMessageKind, messageId: Long): Boolean
    fun deleteThread(threadId: Long): Boolean
}
