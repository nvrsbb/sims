package com.axiomstead.foldsms

fun orderedConversations(threads: List<SampleConversation>, pinnedIds: Set<Int>): List<SampleConversation> =
    threads.sortedWith(compareByDescending<SampleConversation> { it.id in pinnedIds }
        .thenByDescending { it.history.lastOrNull()?.timestamp }
        .thenBy { it.id })
