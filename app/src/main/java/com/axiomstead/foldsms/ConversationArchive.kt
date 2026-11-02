package com.axiomstead.foldsms

fun archiveConversations(
    threads: List<SampleConversation>, archivedIds: Set<Int>, showArchive: Boolean
): List<SampleConversation> = threads.filter { (it.id in archivedIds) == showArchive }
