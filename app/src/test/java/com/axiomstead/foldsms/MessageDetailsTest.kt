package com.axiomstead.foldsms

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDetailsTest {
    @Test fun senderFollowsDirection() {
        val incoming = SampleMessage(1, "Sample", Instant.EPOCH, MessageDirection.INCOMING)
        assertEquals("Alex", incoming.detailSender("Alex"))
        assertEquals("You", incoming.copy(direction = MessageDirection.OUTGOING).detailSender("Alex"))
    }

    @Test fun fullTimestampUsesManilaAcrossUtcDateBoundary() {
        val message = SampleMessage(1, "Sample", Instant.parse("2026-11-01T18:42:15Z"), MessageDirection.INCOMING)
        assertEquals("2 Nov 2026, 02:42:15 Asia/Manila (UTC+08:00)", message.fullTimestamp)
    }
}
