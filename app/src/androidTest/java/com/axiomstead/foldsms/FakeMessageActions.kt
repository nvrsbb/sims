package com.axiomstead.foldsms

/** Records what the UI asked the provider to do; nothing is read, written or deleted on the device. */
internal class FakeActions(var allowed: Boolean) : MessageActions {
    val read = mutableListOf<Long>(); val deleted = mutableListOf<Long>(); val unread = mutableListOf<Pair<DeviceMessageKind, Long>>()
    override fun canModify() = allowed
    override fun markThreadRead(threadId: Long): Boolean { read += threadId; return true }
    override fun markUnread(kind: DeviceMessageKind, messageId: Long): Boolean { unread += kind to messageId; return true }
    override fun deleteThread(threadId: Long): Boolean { deleted += threadId; return true }
}
