package com.axiomstead.foldsms

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal sealed interface ChatItem {
    val key: String
    data class Separator(val day: LocalDate, val label: String) : ChatItem { override val key get() = "day-$day" }
    data class Bubble(val message: DeviceMessage) : ChatItem { override val key get() = message.key }
}

/** "Today", "Yesterday", "November 3", or "November 3, 2025" for earlier years. Clock, zone and locale are injected. */
internal fun formatDateSeparator(epochMillis: Long, now: Instant, zone: ZoneId, locale: Locale): String {
    val day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when {
        day == today -> "Today"
        day == today.minusDays(1) -> "Yesterday"
        day.year == today.year -> DateTimeFormatter.ofPattern("MMMM d", locale).format(day)
        else -> DateTimeFormatter.ofPattern("MMMM d, yyyy", locale).format(day)
    }
}

/** Messages oldest first with a separator before the first message of each local day. */
internal fun chatItems(messages: List<DeviceMessage>, now: Instant, zone: ZoneId, locale: Locale): List<ChatItem> {
    val items = ArrayList<ChatItem>(messages.size + 4)
    var currentDay: LocalDate? = null
    for (message in messages) {
        val day = Instant.ofEpochMilli(message.date).atZone(zone).toLocalDate()
        if (day != currentDay) {
            items += ChatItem.Separator(day, formatDateSeparator(message.date, now, zone, locale))
            currentDay = day
        }
        items += ChatItem.Bubble(message)
    }
    return items
}

internal fun formatMessageTime(epochMillis: Long, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofPattern("HH:mm", locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))
