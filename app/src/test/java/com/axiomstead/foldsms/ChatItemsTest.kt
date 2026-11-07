package com.axiomstead.foldsms

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatItemsTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val now = Instant.parse("2026-11-06T04:00:00Z") // 12:00, 6 Nov 2026 in Manila
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun message(id: Long, iso: String) = DeviceMessage(DeviceMessageKind.SMS, id, "1", "m$id", "", ms(iso), false, true, 1, 0, 0, false)

    @Test fun separatorLabelsFollowTheLocalCalendar() {
        fun label(iso: String) = formatDateSeparator(ms(iso), now, zone, Locale.US)
        assertEquals("Today", label("2026-11-06T01:00:00Z"))
        assertEquals("Today", label("2026-11-05T16:00:00Z")) // 00:00 local starts the new day
        assertEquals("Yesterday", label("2026-11-05T15:59:00Z"))
        assertEquals("November 3", label("2026-11-03T05:00:00Z"))
        assertEquals("December 31, 2025", label("2025-12-31T05:00:00Z"))
    }

    @Test fun oneSeparatorPerLocalDayInOrder() {
        val items = chatItems(listOf(
            message(1, "2026-11-04T01:00:00Z"), message(2, "2026-11-04T05:00:00Z"),
            message(3, "2026-11-05T15:59:00Z"), message(4, "2026-11-05T16:01:00Z"), message(5, "2026-11-06T02:00:00Z")
        ), now, zone, Locale.US)
        assertEquals(listOf("day-2026-11-04", "sms-1", "sms-2", "day-2026-11-05", "sms-3", "day-2026-11-06", "sms-4", "sms-5"), items.map { it.key })
        // sms-3 at 23:59 local on the 5th stays under the 5th; sms-4 at 00:01 on the 6th starts the 6th.
        assertEquals(listOf("November 4", "Yesterday", "Today"), items.filterIsInstance<ChatItem.Separator>().map { it.label })
    }

    @Test fun emptyConversationHasNoItemsAndTimesUseTwentyFourHourClock() {
        assertEquals(emptyList<ChatItem>(), chatItems(emptyList(), now, zone, Locale.US))
        assertEquals("09:05", formatMessageTime(ms("2026-11-06T01:05:00Z"), zone, Locale.US))
    }
}
