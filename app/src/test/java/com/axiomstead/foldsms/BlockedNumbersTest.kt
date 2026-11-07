package com.axiomstead.foldsms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedNumbersTest {
    @Test fun onlyPlainPhoneNumbersCanBeBlocked() {
        assertTrue(isBlockable("+63 917 555 0101"))
        assertTrue(isBlockable("09175550101"))
        assertTrue(isBlockable("2256"))
        assertFalse(isBlockable("GCASH"))
        assertFalse(isBlockable(""))
        assertFalse(isBlockable("12"))
    }

    @Test fun aBlockedNumberMatchesWhateverWayItIsWritten() {
        val blocked = listOf("+639175550101", "2025550199")
        assertTrue(isBlockedIn(blocked, "0917 555 0101"))
        assertTrue(isBlockedIn(blocked, "+63 917 555 0101"))
        assertTrue(isBlockedIn(blocked, "(202) 555-0199"))
        assertFalse(isBlockedIn(blocked, "09175550102"))
        assertFalse(isBlockedIn(emptyList(), "09175550101"))
        assertFalse(isBlockedIn(blocked, ""))
    }
}
