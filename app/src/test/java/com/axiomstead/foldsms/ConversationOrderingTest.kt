package com.axiomstead.foldsms

import org.junit.Assert.*
import org.junit.Test

class ConversationOrderingTest {
    @Test fun pinsLeadWithoutChangingHistoryAndTiesUseStableIds() {
        val first = exampleThreads[0]
        val second = exampleThreads[1]
        val tie = first.copy(id = 9)
        val input = listOf(tie, second, first)
        assertEquals(listOf(2, 1, 9), orderedConversations(input, setOf(2)).map { it.id })
        assertEquals(listOf(1, 9, 2), orderedConversations(input, emptySet()).map { it.id })
        assertEquals(second.messages, orderedConversations(input, setOf(2)).first().messages)
    }

    @Test fun pinsDoNotReintroduceThreadsRemovedByFilters() {
        val visible = exampleThreads.filter { it.category == "Finance" && it.sender.contains("Bank") }
        assertEquals(listOf(2), orderedConversations(visible, setOf(1, 3)).map { it.id })
        assertTrue(orderedConversations(emptyList(), setOf(1)).isEmpty())
    }
}
