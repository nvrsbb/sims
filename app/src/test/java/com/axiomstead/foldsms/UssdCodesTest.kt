package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UssdCodesTest {
    @Test fun acceptsOrdinaryCodesAndRefusesEverythingElse() {
        listOf("*123#", "*143*1#", "#100#", "*222*0917#").forEach { assertTrue(it, isValidUssd(it)) }
        listOf("", "123", "*123", "123#", "*12 3#", "*abc#", "tel:*123#", "*1#\n", "*".repeat(50) + "#").forEach { assertFalse(it, isValidUssd(it)) }
    }

    @Test fun codesSurviveSavingAndBadRowsAreDropped() {
        val codes = listOf(UssdCode("Load balance", "*123#"), UssdCode("Data", "*143#"))
        assertEquals(codes, decodeUssdCodes(encodeUssdCodes(codes)))
        assertEquals(listOf(UssdCode("Ok", "*1#")), decodeUssdCodes("Bad\tabc\n\t*5#\nno tab here\nOk\t*1#\nToo\tmany\t*2#"))
        assertEquals(emptyList<UssdCode>(), decodeUssdCodes(""))
    }

    @Test fun labelsCannotBreakTheStoredFormat() {
        assertEquals("AB C", cleanLabel("  A\tB\n C\r "))
        assertEquals(40, cleanLabel("x".repeat(100)).length)
    }

    @Test fun theLoadBalanceCodeIsThereByDefault() {
        assertEquals(listOf(UssdCode("Load balance", "*123#")), defaultUssdCodes)
    }
}
