package com.axiomstead.foldsms

internal enum class DeviceMessageKind { SMS, MMS }

/** One message of either kind, normalized for display. Source rows keep their provider ids. */
internal data class DeviceMessage(
    val kind: DeviceMessageKind, val id: Long, val address: String, val text: String, val subject: String,
    val date: Long, val outgoing: Boolean, val read: Boolean, val subscriptionId: Int, val attachmentCount: Int,
    val imageCount: Int, val failed: Boolean, val mmsParts: List<DeviceMmsPart> = emptyList()
) {
    /** Stable key unique across SMS and MMS, whose provider ids can collide. */
    val key get() = "${kind.name.lowercase()}-$id"
    val preview: String get() = when {
        text.isNotBlank() -> text.trim()
        subject.isNotBlank() -> subject.trim()
        imageCount > 0 -> if (imageCount == 1) "Photo" else "$imageCount photos"
        attachmentCount > 0 -> if (attachmentCount == 1) "Attachment" else "$attachmentCount attachments"
        else -> "Message"
    }
}

internal data class DeviceConversation(val key: String, val threadId: Long, val address: String, val messages: List<DeviceMessage>) {
    val last get() = messages.last()
    val title get() = address.ifBlank { "Unknown sender" }
    val unreadCount get() = messages.count { !it.outgoing && !it.read }
}

private const val SMS_INBOX = 1
private const val SMS_DRAFT = 3
private const val SMS_FAILED = 5
private const val MMS_DRAFT = 3

/** Digits only for numbers so "+1 (202) 555-0101" and "12025550101" match; alphanumeric senders compare lowercase. */
internal fun conversationAddressKey(address: String): String {
    val trimmed = address.trim()
    if (trimmed.isEmpty()) return ""
    val digits = trimmed.filter { it.isDigit() }
    val numeric = trimmed.all { it.isDigit() || it in "+-() ." }
    return if (numeric && digits.isNotEmpty()) digits.takeLast(10) else trimmed.lowercase()
}

internal fun DeviceSms.toMessage() = DeviceMessage(
    DeviceMessageKind.SMS, id, address, body, "", date, type != SMS_INBOX, read, subscriptionId, 0, 0, type == SMS_FAILED
)

internal fun DeviceMms.toMessage() = DeviceMessage(
    DeviceMessageKind.MMS, id, address, text, subject, date, box != 1, read, -1, attachments.size,
    attachments.count { it.image }, false, parts
)

/**
 * Groups SMS and MMS rows into conversations by provider thread id. Rows without a usable thread id fall back to their
 * normalized address. Provider drafts are skipped. Conversations are newest first; messages inside are oldest first,
 * with a deterministic tie-break so same-timestamp messages never reorder between refreshes.
 */
internal fun groupDeviceConversations(sms: List<DeviceSms>, mms: List<DeviceMms>): List<DeviceConversation> {
    data class Entry(val threadId: Long, val message: DeviceMessage)
    val entries = sms.filter { it.type != SMS_DRAFT }.map { Entry(it.threadId, it.toMessage()) } +
        mms.filter { it.box != MMS_DRAFT }.map { Entry(it.threadId, it.toMessage()) }
    val order = compareBy<DeviceMessage>({ it.date }, { it.kind.ordinal }, { it.id })
    return entries.groupBy { entry ->
        if (entry.threadId > 0) "thread-${entry.threadId}" else "address-${conversationAddressKey(entry.message.address)}"
    }.map { (key, group) ->
        val messages = group.map { it.message }.sortedWith(order)
        val address = messages.asReversed().firstOrNull { it.address.isNotBlank() }?.address.orEmpty()
        DeviceConversation(key, group.first().threadId.coerceAtLeast(0), address, messages)
    }.sortedWith(compareByDescending<DeviceConversation> { it.last.date }.thenBy { it.key })
}

/** Case-insensitive match on title, address digits or any message text; blank query keeps everything. */
internal fun searchDeviceConversations(conversations: List<DeviceConversation>, query: String): List<DeviceConversation> {
    val needle = query.trim()
    if (needle.isEmpty()) return conversations
    val digits = needle.filter { it.isDigit() }
    return conversations.filter { conversation ->
        conversation.title.contains(needle, ignoreCase = true) ||
            (digits.length >= 3 && conversation.address.filter { it.isDigit() }.contains(digits)) ||
            conversation.messages.any { it.text.contains(needle, ignoreCase = true) || it.subject.contains(needle, ignoreCase = true) }
    }
}
