package com.axiomstead.foldsms

import org.junit.Assert.*
import org.junit.Test

class NewSampleConversationTest {
    @Test fun trimsNamesAndAddressesButKeepsDraftTextExactly() {
        val draft = "  Fabricated draft\nsecond line  "
        val value = validateNewSampleConversation(" Sample Person ", " +1 (202) 555-0199 ", draft, emptyList())
        assertEquals("Sample Person", value.name)
        assertEquals("+1 (202) 555-0199", value.address)
        assertEquals("12025550199", value.normalizedAddress)
        assertEquals(draft, value.draft)
    }

    @Test fun rejectsFormattedDuplicatesIncludingFixtureAddresses() {
        val failure = runCatching { validateNewSampleConversation("Sample", "1 (202) 555-0101", "New unsent draft", listOf("+1 202-555-0101")) }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertTrue(failure.exceptionOrNull()!!.message!!.contains("already uses"))
    }

    @Test fun rejectsEmptyLongNamesAndMalformedAddresses() {
        for (name in listOf("  ", "x".repeat(81))) {
            assertTrue(runCatching { validateNewSampleConversation(name, "123", "", emptyList()) }.isFailure)
        }
        for (address in listOf("", "12", "1".repeat(21), "sample@example.test", "+12+34", "١٢٣")) {
            assertTrue(runCatching { validateNewSampleConversation("Sample", address, "", emptyList()) }.isFailure)
        }
    }

    @Test fun customIdsStaySeparateFromFixturesAndDoNotWrap() {
        assertEquals(-1, nextCustomConversationId(null))
        assertEquals(-3, nextCustomConversationId(-2))
        assertTrue(runCatching { nextCustomConversationId(Int.MIN_VALUE) }.isFailure)
        assertTrue(runCatching { nextCustomConversationId(1) }.isFailure)
    }
}
