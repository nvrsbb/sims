package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnreadFilterTest {
    private fun message(id: Long, outgoing: Boolean, read: Boolean, date: Long = id * 1_000) =
        DeviceMessage(DeviceMessageKind.SMS, id, "A", "t$id", "", date, outgoing, read, -1, 0, 0, false)
    private fun thread(key: String, vararg messages: DeviceMessage) = DeviceConversation(key, key.hashCode().toLong().coerceAtLeast(1), key, messages.toList())

    private val readThread = thread("read", message(1, outgoing = false, read = true))
    private val unreadThread = thread("unread", message(2, outgoing = false, read = false))
    private val sentOnly = thread("sent", message(3, outgoing = true, read = true))
    private val all = listOf(readThread, unreadThread, sentOnly)

    private fun keys(unreadOnly: Boolean, keep: String? = null) =
        arrangeDeviceConversations(all, emptyMap(), showArchive = false, filter = "All", unreadOnly = unreadOnly, keepKey = keep).map { it.key }.sorted()

    @Test fun unreadOnlyKeepsJustThreadsWithUnreadMessages() {
        assertEquals(listOf("read", "sent", "unread"), keys(unreadOnly = false))
        assertEquals(listOf("unread"), keys(unreadOnly = true))
    }

    @Test fun theOpenConversationStaysListedAfterItIsRead() {
        assertEquals(listOf("read", "unread"), keys(unreadOnly = true, keep = "read"))
    }

    @Test fun unreadOnlyStillRespectsArchiveAndCategory() {
        val meta = mapOf("unread" to DeviceConversationMetadata("unread", archived = true))
        assertEquals(emptyList<String>(), arrangeDeviceConversations(all, meta, false, "All", unreadOnly = true).map { it.key })
        assertEquals(listOf("unread"), arrangeDeviceConversations(all, meta, true, "All", unreadOnly = true).map { it.key })
    }

    @Test fun markUnreadTargetsTheNewestReceivedMessage() {
        val mixed = thread("m", message(1, false, true), message(2, false, true), message(3, true, true))
        assertEquals(2L, mixed.latestIncoming?.id)
        assertNull(sentOnly.latestIncoming)
    }
}
