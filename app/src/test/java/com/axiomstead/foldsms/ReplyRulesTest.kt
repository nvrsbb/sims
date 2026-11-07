package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyRulesTest {
    private val sims = listOf(SmsSubscription(7, 0, "A"), SmsSubscription(8, 1, "B"))
    private val empty = SmsDraft()
    private fun message(sim: Int, kind: DeviceMessageKind = DeviceMessageKind.SMS) =
        DeviceMessage(kind, 1, "1", "t", "", 1, false, true, sim, 0, 0, false)
    private fun conversation(vararg messages: DeviceMessage) = DeviceConversation("thread-1", 1, "2025550101", messages.toList())

    @Test fun onlyPhoneNumbersCanBeRepliedTo() {
        for (bad in listOf("", "SMART", "BANK-ALERT", "12", "#123#", "*100#")) assertEquals(bad, ReplyState.NotAPhoneNumber, replyState(bad, empty, true, sims))
        assertEquals(ReplyState.Ready, replyState("+63 917 555 0101", empty, true, sims))
    }

    @Test fun permissionAndSimGatesComeBeforeDraftChecks() {
        assertEquals(ReplyState.NoSendPermission, replyState("2025550101", empty, false, sims))
        assertEquals(ReplyState.NoActiveSim, replyState("2025550101", empty, true, emptyList()))
    }

    @Test fun anotherRecipientsUnsentTextBlocksTheComposerButBlankOrSameRecipientDoesNot() {
        val other = SmsDraft(3, "2025550999", "Unsent for someone else", 7)
        assertEquals(ReplyState.OtherDraftWaiting("2025550999"), replyState("2025550101", other, true, sims))
        assertEquals(ReplyState.Ready, replyState("2025550101", other.copy(body = "  "), true, sims))
        // Text with no recipient is never adopted silently: the box would look empty while typing destroyed it.
        assertEquals(ReplyState.OtherDraftWaiting(""), replyState("2025550101", other.copy(address = ""), true, sims))
        assertEquals(ReplyState.Ready, replyState("2025550101", other.copy(address = "+1 (202) 555-0101"), true, sims))
    }

    @Test fun recipientsMatchAcrossFormattingButNeverWhenBlank() {
        assertTrue(sameRecipient("+1 (202) 555-0101", "2025550101"))
        assertFalse(sameRecipient("", ""))
        assertFalse(sameRecipient("2025550101", "2025550102"))
    }

    @Test fun simPreferenceFollowsDraftThenLatestSmsThenFirst() {
        val thread = conversation(message(7), message(8))
        assertEquals(7, preferredSubscription(thread, SmsDraft(subscriptionId = 7), sims)?.id)
        assertEquals(8, preferredSubscription(thread, SmsDraft(subscriptionId = 99), sims)?.id) // stale draft SIM -> latest message
        assertEquals(7, preferredSubscription(conversation(message(-1, DeviceMessageKind.MMS)), empty, sims)?.id)
        assertEquals(7, preferredSubscription(conversation(message(55)), empty, sims)?.id) // unknown SIM -> first active
        assertEquals(null, preferredSubscription(thread, empty, emptyList()))
    }

    @Test fun composerShowsDraftTextOnlyForItsOwnConversation() {
        val draft = SmsDraft(2, "+1 202 555 0101", "Hello", 7)
        assertEquals("Hello", composerText("2025550101", draft))
        assertEquals("", composerText("2025550102", draft))
        assertEquals("", composerText("2025550101", SmsDraft()))
    }

    @Test fun onlyAOnePartReplyInAnExistingConversationWithoutDuplicateRiskSkipsTheReview() {
        assertFalse(needsSendReview(existingConversation = true, partCount = 1, duplicateRisk = false))
        assertTrue(needsSendReview(existingConversation = false, partCount = 1, duplicateRisk = false)) // new recipient
        assertTrue(needsSendReview(existingConversation = true, partCount = 2, duplicateRisk = false)) // costs more
        assertTrue(needsSendReview(existingConversation = true, partCount = 1, duplicateRisk = true)) // may duplicate an earlier send
    }
}
