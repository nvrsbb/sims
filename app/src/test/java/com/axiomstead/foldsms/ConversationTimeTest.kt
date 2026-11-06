package com.axiomstead.foldsms

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationTimeTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val now = Instant.parse("2026-11-06T04:00:00Z") // 12:00 on 6 Nov 2026 in Manila
    private fun at(iso: String) = formatConversationTime(Instant.parse(iso).toEpochMilli(), now, zone, Locale.US)

    @Test fun sameLocalDayShowsTheTime() = assertEquals("09:15", at("2026-11-06T01:15:00Z"))
    @Test fun localMidnightBelongsToTheNewDay() {
        assertEquals("00:00", at("2026-11-05T16:00:00Z"))
        assertEquals("Nov 5", at("2026-11-05T15:59:00Z"))
    }
    @Test fun earlierThisYearShowsMonthAndDay() = assertEquals("Jan 2", at("2026-01-02T05:00:00Z"))
    @Test fun earlierYearsIncludeTheYear() = assertEquals("Dec 31, 2025", at("2025-12-31T05:00:00Z"))
}
