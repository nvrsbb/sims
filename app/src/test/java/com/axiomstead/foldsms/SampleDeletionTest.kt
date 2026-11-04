package com.axiomstead.foldsms

import org.junit.Assert.*
import org.junit.Test

class SampleDeletionTest {
    @Test fun mixedDirectionsDeleteOnlyChosenMessagesAndUpdatePreview() {
        val state = SampleDeletionState().delete(setOf(102, 103))
        val visible = state.visible(exampleThreads)
        assertEquals(listOf(101), visible.first().history.map { it.id })
        assertEquals("Are we still meeting later?", visible.first().preview)
        assertEquals(exampleThreads[1], visible[1])
        assertEquals(exampleThreads, state.undo().visible(exampleThreads))
    }

    @Test fun finalMessageLeavesConversationAndUndoRestoresExactHistory() {
        val state = SampleDeletionState().delete(setOf(101, 102, 103))
        assertEquals(5, state.visible(exampleThreads).size)
        assertTrue(state.visible(exampleThreads).first().history.isEmpty())
        assertEquals("", state.visible(exampleThreads).first().preview)
        assertEquals(exampleThreads.first().history, state.undo().visible(exampleThreads).first().history)
    }

    @Test fun undoReversesLatestChangeAndEmptyOrRepeatedDeleteKeepsUndo() {
        val first = SampleDeletionState().delete(setOf(101))
        assertEquals(first, first.delete(emptySet()))
        assertEquals(first, first.delete(setOf(101)))
        val second = first.delete(setOf(101, 201))
        assertEquals(setOf(201), second.undoIds)
        assertEquals(setOf(101), second.undo().deletedIds)
        assertEquals(second.undo(), second.undo().undo())
    }

    @Test fun conversationAndAppScopesIgnoreSearchAndCategoryAndExcludeAlreadyDeletedMessages() {
        val initial = SampleDeletionState()
        val alexIds = sampleMessageIds(initial.visible(exampleThreads), 1)
        assertEquals(setOf(101, 102, 103), alexIds)
        val afterAlex = initial.delete(alexIds)
        val remaining = afterAlex.visible(exampleThreads)
        assertEquals(1, searchConversations(remaining, "Bank", "Finance").size)
        assertEquals(8, sampleMessageIds(remaining).size)
        assertTrue(sampleMessageIds(remaining, 1).isEmpty())
        assertTrue(sampleMessageIds(remaining, 999).isEmpty())
        val allDeleted = afterAlex.delete(sampleMessageIds(remaining))
        assertTrue(allDeleted.visible(exampleThreads).all { it.messages.isEmpty() })
        assertEquals(5, allDeleted.visible(exampleThreads).size)
        assertEquals(afterAlex, allDeleted.undo().copy(undoIds = afterAlex.undoIds))
        assertEquals(afterAlex.deletedIds, allDeleted.undo().deletedIds)
    }
}
