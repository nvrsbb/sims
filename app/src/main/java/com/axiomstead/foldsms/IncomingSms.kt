package com.axiomstead.foldsms

/** One text message as the platform delivered it, possibly one of several PDUs of the same multipart message. */
internal data class IncomingSmsPart(val address: String, val body: String, val timestamp: Long)

internal data class IncomingSms(val address: String, val body: String, val timestamp: Long, val subscriptionId: Int)

/**
 * Joins the PDUs of one delivery into messages. Parts from the same sender stay in delivery order and form one message;
 * different senders in one delivery stay separate. Blank bodies are dropped; a blank sender is kept as "Unknown".
 */
internal fun assembleIncoming(parts: List<IncomingSmsPart>, subscriptionId: Int): List<IncomingSms> =
    parts.groupBy { it.address.trim() }.mapNotNull { (address, group) ->
        val body = group.joinToString("") { it.body }
        if (body.isBlank()) null else IncomingSms(address.ifEmpty { "Unknown" }, body, group.first().timestamp, subscriptionId)
    }

/** Writes a received message to the SMS provider. Only the default SMS app may do this. Returns false if it was not stored. */
internal fun interface IncomingStore { fun store(message: IncomingSms): Boolean }

/** Shows the new-message notification for [message]. */
internal fun interface IncomingNotifier { fun notify(message: IncomingSms) }

/**
 * Receiving is the default SMS app's job. While another app holds the role, Android does not send us SMS_DELIVER, and
 * this handler refuses anyway so Google Messages never gets a duplicate stored copy or a second notification.
 * A message that could not be stored is still shown, so a storage failure never hides it from the owner.
 */
internal class IncomingSmsHandler(
    private val isDefaultApp: () -> Boolean,
    private val store: IncomingStore,
    private val notifier: IncomingNotifier
) {
    /** Returns how many messages were handled. */
    fun handle(parts: List<IncomingSmsPart>, subscriptionId: Int): Int {
        if (!isDefaultApp()) return 0
        val messages = assembleIncoming(parts, subscriptionId)
        messages.forEach { message ->
            try { store.store(message) } catch (_: Exception) { /* shown below; never log message text */ }
            try { notifier.notify(message) } catch (_: Exception) { }
        }
        return messages.size
    }
}

/** Notification text: the sender, and the message trimmed to one readable line. */
internal fun notificationBody(body: String, limit: Int = 160): String {
    val line = body.trim().replace(Regex("\\s+"), " ")
    return if (line.length <= limit) line else line.take(limit - 1).trimEnd() + "…"
}
