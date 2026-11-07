package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Test

class PagingTest {
    private data class Row(val id: Long, val text: String)

    @Test fun extraRowsAreAddedOnceAndTheWindowKeepsItsOrderAndValues() {
        val window = listOf(Row(5, "window five"), Row(4, "window four"))
        val thread = listOf(Row(5, "stale copy"), Row(3, "older three"), Row(1, "older one"))
        val merged = mergeRows(window, listOf(thread, listOf(Row(3, "again"))), Row::id)
        assertEquals(listOf(5L, 4L, 3L, 1L), merged.map { it.id })
        assertEquals("window five", merged.first { it.id == 5L }.text) // the window's row wins
    }

    @Test fun withNoExtrasTheWindowIsReturnedAsIs() {
        val window = listOf(Row(1, "a"))
        assertEquals(window, mergeRows(window, emptyList(), Row::id))
        assertEquals(window, mergeRows(window, listOf(emptyList()), Row::id))
    }

    @Test fun pagesGrowThreefoldAndNeverOverflow() {
        assertEquals(3_000, nextPageLimit(1_000))
        assertEquals(Int.MAX_VALUE, nextPageLimit(Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, nextPageLimit(Int.MAX_VALUE / 2))
        var limit = 1_000; repeat(40) { limit = nextPageLimit(limit) }
        assertEquals(Int.MAX_VALUE, limit)
    }

    @Test fun conversationsBuiltFromAWindowPlusAThreadHaveTheWholeThread() {
        fun sms(id: Long, thread: Long, date: Long) = DeviceSms(id, thread, "2025550101", "m$id", date, 1, -1, true)
        val window = listOf(sms(30, 10, 3_000), sms(29, 10, 2_900))
        val full = (1L..30L).map { sms(it, 10, it * 100) }
        val conversation = groupDeviceConversations(mergeRows(window, listOf(full), DeviceSms::id), emptyList()).single()
        assertEquals(30, conversation.messages.size)
        assertEquals("m30", conversation.last.text)
    }
}
