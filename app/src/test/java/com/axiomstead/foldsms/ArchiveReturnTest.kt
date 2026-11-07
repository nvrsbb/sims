package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveReturnTest {
    private fun message(id: Long, outgoing: Boolean, date: Long) =
        DeviceMessage(DeviceMessageKind.SMS, id, "A", "t$id", "", date, outgoing, true, -1, 0, 0, false)
    private val thread = DeviceConversation("t", 1, "A", listOf(message(1, false, 1_000), message(2, true, 2_000)))

    @Test fun anUnarchivedOrUnknownConversationIsNotArchived() {
        assertFalse(isArchived(thread, null))
        assertFalse(isArchived(thread, DeviceConversationMetadata("t", archived = false, archivedAtDate = 500)))
    }

    @Test fun archivedStaysArchivedUntilSomethingIsReceivedAfterTheArchiveDate() {
        val meta = DeviceConversationMetadata("t", archived = true, archivedAtDate = 2_000)
        assertTrue(isArchived(thread, meta))
        val sentLater = thread.copy(messages = thread.messages + message(3, true, 9_000))
        assertTrue(isArchived(sentLater, meta)) // our own reply never un-archives
        val sameTime = thread.copy(messages = thread.messages + message(4, false, 2_000))
        assertTrue(isArchived(sameTime, meta)) // not strictly later
        val received = thread.copy(messages = thread.messages + message(5, false, 2_001))
        assertFalse(isArchived(received, meta))
    }

    @Test fun archivesWithoutADateStayArchivedLikeBefore() {
        val old = DeviceConversationMetadata("t", archived = true, archivedAtDate = 0)
        val received = thread.copy(messages = thread.messages + message(5, false, 99_000))
        assertTrue(isArchived(received, old))
    }

    @Test fun theInboxAndArchiveViewsFollowTheRule() {
        val received = thread.copy(messages = thread.messages + message(5, false, 5_000))
        val meta = mapOf("t" to DeviceConversationMetadata("t", archived = true, archivedAtDate = 2_000))
        assertEquals(listOf("t"), arrangeDeviceConversations(listOf(received), meta, showArchive = false, filter = "All").map { it.key })
        assertEquals(emptyList<String>(), arrangeDeviceConversations(listOf(received), meta, showArchive = true, filter = "All").map { it.key })
        assertEquals(listOf("t"), arrangeDeviceConversations(listOf(thread), meta, showArchive = true, filter = "All").map { it.key })
    }
}
