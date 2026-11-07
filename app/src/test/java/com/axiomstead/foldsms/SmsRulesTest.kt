package com.axiomstead.foldsms

import org.junit.Assert.*
import org.junit.Test

class SmsRulesTest {
    @Test fun numbersNormalizeFormattingWithoutAcceptingCommandsOrMovingThePlus() {
        assertEquals("+639171234567", smsAddress(" +63 (917) 123-4567 "))
        assertEquals("5556", smsAddress("5556"))
        listOf("12", "12+345", "++123", "123;456", "*123#", "bank", "1234567890123456", "１２３４").forEach {
            assertTrue(it, runCatching { smsAddress(it) }.isFailure)
        }
    }
    @Test fun validationKeepsMessageWhitespaceAndRejectsMissingSimEmptyAndOversizedBody() {
        val draft = SmsDraft(7, "+1 (202) 555-0199", "  Fabricated SMS\n₱ 👋  ", 11)
        assertEquals("+12025550199", validateSmsDraft(draft))
        assertEquals("  Fabricated SMS\n₱ 👋  ", draft.body)
        assertTrue(runCatching { validateSmsDraft(draft.copy(subscriptionId = -1)) }.isFailure)
        assertTrue(runCatching { validateSmsDraft(draft.copy(body = " \n ")) }.isFailure)
        assertTrue(runCatching { validateSmsDraft(draft.copy(body = "x".repeat(MAX_SMS_BODY_LENGTH + 1))) }.isFailure)
    }
    @Test fun noPrivilegeSetAllowsUnconsentedOwnDefaultOrUnsupportedDispatch() {
        val allowed = SmsAccess(true, true, true, true, true, true, true)
        assertTrue(allowed.canRead); assertTrue(allowed.canSend)
        listOf(allowed.copy(enabled = false), allowed.copy(messagingSupported = false), allowed.copy(hasDefaultApp = false)).forEach {
            assertFalse(it.canRead); assertFalse(it.canSend)
        }
        assertFalse(allowed.copy(readGranted = false).canRead)
        assertFalse(allowed.copy(receiveGranted = false).canRead)
        assertFalse(allowed.copy(sendGranted = false).canSend)
        assertFalse(allowed.copy(phoneGranted = false).canSend)
        assertTrue(allowed.copy(sendGranted = false, phoneGranted = false).canRead)
    }
    @Test fun multipartResultsNeverTurnAPendingOrMixedOutcomeIntoSent() {
        assertEquals("SUBMITTED", smsPartStatus(listOf(null, SMS_NETWORK_SENT)))
        assertEquals("SUBMITTED", smsPartStatus(listOf(2, null)))
        assertEquals("SENT", smsPartStatus(listOf(SMS_NETWORK_SENT, SMS_NETWORK_SENT)))
        assertEquals("FAILED", smsPartStatus(listOf(2, 4)))
        assertEquals("PARTIAL", smsPartStatus(listOf(SMS_NETWORK_SENT, 2)))
        assertEquals("SUBMITTED", smsPartStatus(emptyList()))
    }
}
