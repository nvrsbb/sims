package com.axiomstead.foldsms

/** What the conversation composer can offer right now. Pure so every branch is unit-tested. */
internal sealed interface ReplyState {
    /** Alphanumeric sender, short code or blank address: no safe SMS recipient. */
    data object NotAPhoneNumber : ReplyState
    data object NoSendPermission : ReplyState
    data object NoActiveSim : ReplyState
    /** The one shared SMS draft already holds unsent text for someone else; it must be sent or discarded first. */
    data class OtherDraftWaiting(val address: String) : ReplyState
    data object Ready : ReplyState
}

internal fun replyState(conversationAddress: String, draft: SmsDraft, canSend: Boolean, subscriptions: List<SmsSubscription>): ReplyState {
    if (runCatching { smsAddress(conversationAddress) }.isFailure) return ReplyState.NotAPhoneNumber
    if (!canSend) return ReplyState.NoSendPermission
    if (subscriptions.isEmpty()) return ReplyState.NoActiveSim
    if (draft.body.isNotBlank() && !sameRecipient(draft.address, conversationAddress)) return ReplyState.OtherDraftWaiting(draft.address)
    return ReplyState.Ready
}

/** Same person if the numbers match after formatting; blank never matches. */
internal fun sameRecipient(a: String, b: String): Boolean {
    val left = conversationAddressKey(a)
    return left.isNotEmpty() && left == conversationAddressKey(b)
}

/** The draft's SIM if still active, else the SIM of the newest message in the thread, else the first active SIM. */
internal fun preferredSubscription(conversation: DeviceConversation, draft: SmsDraft, subscriptions: List<SmsSubscription>): SmsSubscription? =
    subscriptions.firstOrNull { it.id == draft.subscriptionId }
        ?: conversation.messages.asReversed().firstNotNullOfOrNull { message -> subscriptions.firstOrNull { it.id == message.subscriptionId && message.kind == DeviceMessageKind.SMS } }
        ?: subscriptions.firstOrNull()

/** The composer shows the shared draft's text only when it is addressed to this conversation. */
internal fun composerText(conversationAddress: String, draft: SmsDraft): String =
    if (sameRecipient(draft.address, conversationAddress)) draft.body else ""

/**
 * Whether a send needs the review dialog first. A single-part reply in a conversation that already has messages goes out
 * when the owner taps Send. A new recipient, a message that costs several parts, or a possible duplicate of an earlier
 * attempt still gets the dialog.
 */
internal fun needsSendReview(existingConversation: Boolean, partCount: Int, duplicateRisk: Boolean): Boolean =
    !existingConversation || partCount != 1 || duplicateRisk
