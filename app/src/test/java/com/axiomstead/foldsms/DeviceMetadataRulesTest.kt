package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceMetadataRulesTest {
    private fun thread(id: Long, date: Long) = DeviceConversation("thread-$id", id, "$id",
        listOf(DeviceMessage(DeviceMessageKind.SMS, id, "$id", "t", "", date, false, true, 1, 0, 0, false)))
    private val a = thread(1, 100); private val b = thread(2, 300); private val c = thread(3, 200)
    private fun meta(vararg rows: DeviceConversationMetadata) = rows.associateBy { it.conversationKey }
    private fun keys(list: List<DeviceConversation>) = list.map { it.key }

    @Test fun newestFirstWithPinnedOnTopThenKeyAsTieBreak() {
        assertEquals(listOf("thread-2", "thread-3", "thread-1"), keys(arrangeDeviceConversations(listOf(a, b, c), emptyMap(), false, "All")))
        assertEquals(listOf("thread-1", "thread-2", "thread-3"), keys(arrangeDeviceConversations(listOf(a, b, c), meta(DeviceConversationMetadata("thread-1", pinned = true)), false, "All")))
        val tie = listOf(thread(9, 50), thread(4, 50))
        assertEquals(listOf("thread-4", "thread-9"), keys(arrangeDeviceConversations(tie, emptyMap(), false, "All")))
    }

    @Test fun archiveViewAndInboxViewAreComplementary() {
        val m = meta(DeviceConversationMetadata("thread-2", archived = true))
        assertEquals(listOf("thread-3", "thread-1"), keys(arrangeDeviceConversations(listOf(a, b, c), m, false, "All")))
        assertEquals(listOf("thread-2"), keys(arrangeDeviceConversations(listOf(a, b, c), m, true, "All")))
    }

    @Test fun unassignedConversationsUseTheAutomaticCategoryAndFiltersApplyInsideTheCurrentView() {
        // These threads have senders that are not phone numbers, so the automatic category is Business.
        val m = meta(DeviceConversationMetadata("thread-1", category = "Personal"), DeviceConversationMetadata("thread-3", category = "Personal", archived = true))
        assertEquals(listOf("thread-1"), keys(arrangeDeviceConversations(listOf(a, b, c), m, false, "Personal")))
        assertEquals(listOf("thread-3"), keys(arrangeDeviceConversations(listOf(a, b, c), m, true, "Personal")))
        assertEquals(listOf("thread-2"), keys(arrangeDeviceConversations(listOf(a, b, c), m, false, "Business")))
        assertEquals("Business", effectiveCategory(b, null))
        assertEquals("Business", effectiveCategory(b, DeviceConversationMetadata("x", category = "OTP"))) // a value stored by an older version
        assertEquals("Personal", effectiveCategory(b, DeviceConversationMetadata("x", category = "Personal")))
    }

    @Test fun pinnedStatePersistsAcrossArchivingAndOnlyAffectsItsOwnView() {
        val m = meta(DeviceConversationMetadata("thread-1", pinned = true, archived = true), DeviceConversationMetadata("thread-3", pinned = true))
        assertEquals(listOf("thread-3", "thread-2"), keys(arrangeDeviceConversations(listOf(a, b, c), m, false, "All")))
        assertEquals(listOf("thread-1"), keys(arrangeDeviceConversations(listOf(a, b, c), m, true, "All")))
    }
}
