package com.axiomstead.foldsms

/** Only immutable fabricated fixtures are hidden. Drafts and metadata are separate. */
internal data class SampleDeletionState(
    val deletedIds: Set<Int> = emptySet(),
    val undoIds: Set<Int> = emptySet()
) {
    fun delete(ids: Set<Int>): SampleDeletionState {
        val added = ids - deletedIds
        return if (added.isEmpty()) this else copy(deletedIds = deletedIds + added, undoIds = added)
    }

    fun undo(): SampleDeletionState = copy(deletedIds = deletedIds - undoIds, undoIds = emptySet())

    fun visible(conversations: List<SampleConversation>): List<SampleConversation> =
        conversations.map { it.copy(messages = it.messages.filterNot { message -> message.id in deletedIds }) }
}

/** Pass the complete visible dataset, never search/filter results, to define “all”. */
internal fun sampleMessageIds(conversations: List<SampleConversation>, conversationId: Int? = null): Set<Int> =
    conversations.filter { conversationId == null || it.id == conversationId }
        .flatMap { it.messages }.map { it.id }.toSet()
