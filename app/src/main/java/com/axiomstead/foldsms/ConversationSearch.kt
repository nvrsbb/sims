package com.axiomstead.foldsms

data class ConversationSearchResult(val conversation: SampleConversation, val snippet: String)

/** Blank queries show the inbox. Numeric queries ignore address punctuation only. */
fun searchConversations(
    conversations: List<SampleConversation>,
    query: String,
    category: String
): List<ConversationSearchResult> {
    val term = query.trim().replace(Regex("\\s+"), " ")
    val numeric = term.isNotEmpty() && term.all { it.isDigit() || it in "+()- ." }
    val digits = term.filter(Char::isDigit)
    fun matches(text: String) = text.replace(Regex("\\s+"), " ").contains(term, ignoreCase = true)
    return conversations.mapNotNull { conversation ->
        if (category != "All" && conversation.category != category) return@mapNotNull null
        val message = if (term.isEmpty()) null else conversation.history.lastOrNull { matches(it.body) }
        val addressMatch = matches(conversation.address) ||
            (numeric && digits.isNotEmpty() && conversation.address.filter(Char::isDigit).contains(digits))
        if (term.isEmpty() || matches(conversation.sender) || addressMatch || message != null) {
            ConversationSearchResult(conversation, message?.body ?: conversation.preview)
        } else null
    }
}
