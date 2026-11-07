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
        // These threads have non-numeric two-character-or-less senders and no keywords, so the automatic category is Services.
        val m = meta(DeviceConversationMetadata("thread-1", category = "Finance"), DeviceConversationMetadata("thread-3", category = "Finance", archived = true))
        assertEquals(listOf("thread-1"), keys(arrangeDeviceConversations(listOf(a, b, c), m, false, "Finance")))
        assertEquals(listOf("thread-3"), keys(arrangeDeviceConversations(listOf(a, b, c), m, true, "Finance")))
        assertEquals(listOf("thread-2"), keys(arrangeDeviceConversations(listOf(a, b, c), m, false, "Services")))
        assertEquals("Services", effectiveCategory(b, null))
        assertEquals("OTP", effectiveCategory(b, DeviceConversationMetadata("x", category = "OTP")))
    }

    @Test fun pinnedStatePersistsAcrossArchivingAndOnlyAffectsItsOwnView() {
        val m = meta(DeviceConversationMetadata("thread-1", pinned = true, archived = true), DeviceConversationMetadata("thread-3", pinned = true))
        assertEquals(listOf("thread-3", "thread-2"), keys(arrangeDeviceConversations(listOf(a, b, c), m, false, "All")))
        assertEquals(listOf("thread-1"), keys(arrangeDeviceConversations(listOf(a, b, c), m, true, "All")))
    }
}
