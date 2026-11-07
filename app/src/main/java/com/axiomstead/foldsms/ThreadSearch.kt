package com.axiomstead.foldsms

/** Keys of the messages that contain [query] in their text or subject, oldest first. Blank query matches nothing. */
internal fun threadMatches(messages: List<DeviceMessage>, query: String): List<String> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return messages.filter { it.text.contains(needle, ignoreCase = true) || it.subject.contains(needle, ignoreCase = true) }.map { it.key }
}

/** Where [query] occurs in [text], case-insensitively, without overlaps; used to highlight matches. */
internal fun highlightRanges(text: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val ranges = ArrayList<IntRange>()
    var from = 0
    while (from <= text.length - needle.length) {
        val at = text.indexOf(needle, from, ignoreCase = true)
        if (at < 0) break
        ranges += at until at + needle.length
        from = at + needle.length
    }
    return ranges
}

/**
 * The status shown under one of our own messages: "Failed" and "Sending…" always, "Sent" only for the newest message we sent
 * so the thread is not full of repeated labels. Received messages have none.
 */
internal fun outgoingStatusLabel(message: DeviceMessage, newestOutgoing: Boolean): String? = when {
    !message.outgoing -> null
    message.failed -> "Failed"
    message.pending -> "Sending…"
    newestOutgoing -> "Sent"
    else -> null
}
