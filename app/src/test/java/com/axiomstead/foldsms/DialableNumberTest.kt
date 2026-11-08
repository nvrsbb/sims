package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialableNumberTest {
    @Test fun keepsDigitsPlusAndDialCharactersOnly() {
        assertEquals("+639185550123", dialableNumber("+63 918 555 0123"))
        assertEquals("0285550100", dialableNumber("(02) 8555-0100"))
        assertEquals("*143#", dialableNumber("*143#"))
    }

    @Test fun emergencyAndShortNumbersAreNeverFiltered() {
        listOf("112", "911", "999", "29290").forEach { assertEquals(it, dialableNumber(it)) }
    }

    @Test fun senderNamesAndNonsenseAreNotDialable() {
        assertNull(dialableNumber("EXAMPLEBANK"))
        assertNull(dialableNumber("GLOBE2"))
        assertNull(dialableNumber("+++"))
        assertNull(dialableNumber("12+34"))
        assertNull(dialableNumber(""))
    }
}
