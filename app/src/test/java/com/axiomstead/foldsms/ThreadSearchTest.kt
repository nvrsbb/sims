package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadSearchTest {
    private fun message(id: Long, text: String, outgoing: Boolean = false, subject: String = "", failed: Boolean = false, pending: Boolean = false) =
        DeviceMessage(DeviceMessageKind.SMS, id, "A", text, subject, id * 1000, outgoing, true, -1, 0, 0, failed, pending = pending)

    @Test fun matchesAreInOrderCaseInsensitiveAndIncludeTheSubject() {
        val messages = listOf(message(1, "Lunch at noon?"), message(2, "Bring the report"), message(3, "ok", subject = "LUNCH plan"), message(4, "sure, lunch works", outgoing = true))
        assertEquals(listOf("sms-1", "sms-3", "sms-4"), threadMatches(messages, "lunch"))
        assertEquals(listOf("sms-2"), threadMatches(messages, "  report "))
    }

    @Test fun aBlankOrUnmatchedQueryMatchesNothing() {
        val messages = listOf(message(1, "Hello"))
        assertTrue(threadMatches(messages, "").isEmpty())
        assertTrue(threadMatches(messages, "   ").isEmpty())
        assertTrue(threadMatches(messages, "zzz").isEmpty())
    }

    @Test fun highlightRangesAreCaseInsensitiveAndDoNotOverlap() {
        assertEquals(listOf(0..4, 12..16), highlightRanges("Lunch is on LUNCH", "lunch"))
        assertEquals(listOf(0..1, 2..3), highlightRanges("aaaa", "aa"))
        assertEquals(listOf(6..7), highlightRanges("Hello Ab", "ab"))
        assertTrue(highlightRanges("text", "").isEmpty())
    }

    @Test fun onlyFailedSendingAndTheNewestSentMessageShowAStatus() {
        assertNull(outgoingStatusLabel(message(1, "in"), newestOutgoing = true)) // received
        assertEquals("Failed", outgoingStatusLabel(message(2, "x", outgoing = true, failed = true), newestOutgoing = false))
        assertEquals("Sending…", outgoingStatusLabel(message(3, "x", outgoing = true, pending = true), newestOutgoing = false))
        assertEquals("Sent", outgoingStatusLabel(message(4, "x", outgoing = true), newestOutgoing = true))
        assertNull(outgoingStatusLabel(message(5, "x", outgoing = true), newestOutgoing = false))
    }

    @Test fun providerTypesMapToPending() {
        fun sms(type: Int) = DeviceSms(1, 1, "A", "t", 1, type, -1, true).toMessage()
        assertTrue(sms(4).pending); assertTrue(sms(6).pending)
        assertFalse(sms(2).pending); assertFalse(sms(1).pending)
        assertTrue(sms(5).failed)
    }
}
