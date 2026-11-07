package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingSmsTest {
    private fun part(address: String, body: String, time: Long = 1_000) = IncomingSmsPart(address, body, time)

    private class Recorder(var storeResult: Boolean = true, var storeThrows: Boolean = false) {
        val stored = mutableListOf<IncomingSms>(); val shown = mutableListOf<IncomingSms>()
        val store = IncomingStore { if (storeThrows) throw SecurityException("not default"); stored += it; storeResult }
        val notifier = IncomingNotifier { shown += it }
    }

    @Test fun multipartPdusFromOneSenderJoinInOrderIntoOneMessage() {
        val result = assembleIncoming(listOf(part("+15550100", "Hello, ", 5), part("+15550100", "world", 6)), 3)
        assertEquals(listOf(IncomingSms("+15550100", "Hello, world", 5, 3)), result)
    }

    @Test fun differentSendersStaySeparateAndBlankBodiesAreDropped() {
        val result = assembleIncoming(listOf(part("A", "one"), part("B", "two"), part("C", "  ")), -1)
        assertEquals(listOf("A", "B"), result.map { it.address })
    }

    @Test fun aBlankSenderIsShownAsUnknown() {
        assertEquals("Unknown", assembleIncoming(listOf(part(" ", "hi")), -1).single().address)
    }

    @Test fun nothingIsStoredOrShownWhileAnotherAppIsDefault() {
        val r = Recorder()
        assertEquals(0, IncomingSmsHandler({ false }, r.store, r.notifier).handle(listOf(part("A", "hi")), 1))
        assertTrue(r.stored.isEmpty() && r.shown.isEmpty())
    }

    @Test fun asDefaultAppAMessageIsStoredAndShownOnce() {
        val r = Recorder()
        assertEquals(1, IncomingSmsHandler({ true }, r.store, r.notifier).handle(listOf(part("A", "hi")), 1))
        assertEquals(r.stored, r.shown)
        assertEquals(1, r.shown.size)
    }

    @Test fun aStorageFailureStillShowsTheMessage() {
        val r = Recorder(storeThrows = true)
        IncomingSmsHandler({ true }, r.store, r.notifier).handle(listOf(part("A", "code 123456")), 1)
        assertEquals("code 123456", r.shown.single().body)
        val r2 = Recorder(storeResult = false)
        IncomingSmsHandler({ true }, r2.store, r2.notifier).handle(listOf(part("A", "x")), 1)
        assertEquals(1, r2.shown.size)
    }

    @Test fun aFailingNotifierDoesNotStopLaterMessagesFromBeingStored() {
        val stored = mutableListOf<String>()
        val handler = IncomingSmsHandler({ true }, { stored += it.address; true }, { throw IllegalStateException() })
        handler.handle(listOf(part("A", "1"), part("B", "2")), 1)
        assertEquals(listOf("A", "B"), stored)
    }

    @Test fun notificationTextIsOneTrimmedLine() {
        assertEquals("a b c", notificationBody("  a\n b\t c \n"))
        val long = notificationBody("x".repeat(500))
        assertEquals(160, long.length); assertTrue(long.endsWith("…"))
    }
}
