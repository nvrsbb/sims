package com.axiomstead.foldsms

import org.junit.Assert.*
import org.junit.Test

class ConversationSearchTest {
    @Test fun olderMessageMatchesReturnTheCorrectThreadAndSnippet() {
        val result = searchConversations(exampleThreads, "  STILL   meeting  ", "All").single()
        assertEquals(1, result.conversation.id)
        assertEquals("Are we still meeting later?", result.snippet)
    }

    @Test fun senderAddressAndFormattedNumbersMatchWithoutIgnoringCategory() {
        assertEquals(1, searchConversations(exampleThreads, "aLeX", "Personal").single().conversation.id)
        assertEquals(2, searchConversations(exampleThreads, "(555) 0102", "Finance").single().conversation.id)
        assertTrue(searchConversations(exampleThreads, "5550102", "Personal").isEmpty())
        assertTrue(searchConversations(exampleThreads, "still meeting", "Finance").isEmpty())
        assertTrue(searchConversations(exampleThreads, "+--", "All").isEmpty())
    }

    @Test fun clearingRecoversTheListAndUnknownTextShowsNoResults() {
        assertEquals(exampleThreads.map { it.id }, searchConversations(exampleThreads, " \n ", "All").map { it.conversation.id })
        assertTrue(searchConversations(exampleThreads, "no such fabricated text", "All").isEmpty())
    }
}
