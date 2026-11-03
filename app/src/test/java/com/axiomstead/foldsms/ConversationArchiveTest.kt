package com.axiomstead.foldsms

import org.junit.Assert.*
import org.junit.Test

class ConversationArchiveTest {
    @Test fun archiveAndInboxAreDisjointAndRestoreRetainsWholeHistory() {
        val archived = setOf(1, 3)
        assertEquals(listOf(2, 4, 5), archiveConversations(exampleThreads, archived, false).map { it.id })
        assertEquals(listOf(1, 3), archiveConversations(exampleThreads, archived, true).map { it.id })
        assertEquals(exampleThreads, archiveConversations(exampleThreads, emptySet(), false))
        assertTrue(archiveConversations(exampleThreads, emptySet(), true).isEmpty())
        assertEquals(exampleThreads.first().messages, archiveConversations(exampleThreads, archived, true).first().messages)
    }

    @Test fun searchAndCategorySubsetsStayExcludedInArchiveAndWithPins() {
        val subset = exampleThreads.filter { it.category == "Finance" }
        assertEquals(listOf(2), orderedConversations(archiveConversations(subset, setOf(1, 2), true), setOf(1)).map { it.id })
        assertTrue(archiveConversations(subset, setOf(1, 2), false).isEmpty())
    }
}
