package com.axiomstead.foldsms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectReplyMessageTest {
    @Test fun everyOutcomeHasAShortPlainNotice() {
        DirectReplyOutcome.entries.forEach { outcome ->
            val text = directReplyMessage(outcome)
            assertTrue("$outcome", text.isNotBlank() && text.length < 120)
        }
    }

    @Test fun onlyTheSentNoticeClaimsSuccess() {
        assertTrue(directReplyMessage(DirectReplyOutcome.SENT).startsWith("Reply sent"))
        DirectReplyOutcome.entries.filter { it != DirectReplyOutcome.SENT }.forEach { assertFalse("$it", directReplyMessage(it).contains("Reply sent")) }
    }

    @Test fun savedDraftNoticesPointToTheApp() {
        assertTrue(directReplyMessage(DirectReplyOutcome.SAVED_LONG).contains("Open FoldSMS"))
        assertTrue(directReplyMessage(DirectReplyOutcome.SAVED_DUPLICATE).contains("Open FoldSMS"))
        assertTrue(directReplyMessage(DirectReplyOutcome.DRAFT_WAITING).contains("Open FoldSMS"))
    }
}
