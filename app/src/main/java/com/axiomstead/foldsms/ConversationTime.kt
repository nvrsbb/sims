package com.axiomstead.foldsms

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** List timestamp: time today, "Nov 3" earlier this year, "Nov 3, 2025" before that. Clock, zone and locale are injected. */
internal fun formatConversationTime(epochMillis: Long, now: Instant, zone: ZoneId, locale: Locale): String {
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = now.atZone(zone)
    return when {
        moment.toLocalDate() == today.toLocalDate() -> DateTimeFormatter.ofPattern("HH:mm", locale).format(moment)
        moment.year == today.year -> DateTimeFormatter.ofPattern("MMM d", locale).format(moment)
        else -> DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(moment)
    }
}
