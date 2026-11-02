package com.axiomstead.foldsms

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class SampleConversationsTest {
    @Test fun historyAndPreviewUseChronologyRatherThanInputOrder() {
        val earlier = SampleMessage(1, "First", Instant.parse("2026-11-02T00:00:00Z"), MessageDirection.INCOMING)
        val later = earlier.copy(id = 2, body = "Last", timestamp = earlier.timestamp.plusSeconds(60))
        val conversation = SampleConversation(10, "Test", "Personal", listOf(later, earlier))
        assertEquals(listOf(earlier, later), conversation.history)
        assertEquals("Last", conversation.preview)
        assertEquals("08:01", conversation.time)
    }

    @Test fun equalTimestampsHaveStableMessageOrder() {
        val one = SampleMessage(1, "One", Instant.EPOCH, MessageDirection.INCOMING)
        val two = one.copy(id = 2, body = "Two")
        assertEquals(listOf(one, two), SampleConversation(10, "Test", "Personal", listOf(two, one)).history)
    }

    @Test fun sampleHistoriesStaySeparateAndKeepExistingDraftIds() {
        assertEquals(listOf(1, 2, 3, 4, 5), exampleThreads.map { it.id })
        assertTrue(exampleThreads.all { it.history.size >= 2 })
        val ids = exampleThreads.flatMap { it.history }.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals("See you this evening!", exampleThreads.first().preview)
        assertTrue(exampleThreads.first().history.any { it.direction == MessageDirection.OUTGOING })
    }
}
