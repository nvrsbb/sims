package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactGroupsTest {
    private val ana1 = ContactEntry("Ana Cruz", "0917 555 0199", 11, "lk-11")
    private val ana2 = ContactEntry("Ana Cruz", "(02) 8555-0100", 11, "lk-11")
    private val ben = ContactEntry("ben reyes", "+63 918 555 0123", 12, "lk-12")
    private val noId1 = ContactEntry("Carlo", "09195550001")
    private val noId2 = ContactEntry("carlo ", "09195550002")

    @Test fun entriesOfOnePersonBecomeOneRowWithAllTheirNumbers() {
        val groups = groupContacts(listOf(ana1, ben, ana2))
        assertEquals(listOf("Ana Cruz", "ben reyes"), groups.map { it.name })
        assertEquals(listOf("0917 555 0199", "(02) 8555-0100"), groups.first().numbers)
        assertEquals("lk-11", groups.first().lookupKey)
    }

    @Test fun peopleWithoutAnIdAreJoinedByNameAndTheListIsSortedIgnoringCase() {
        val groups = groupContacts(listOf(ben, noId1, ana1, noId2))
        assertEquals(listOf("Ana Cruz", "ben reyes", "Carlo"), groups.map { it.name })
        assertEquals(2, groups.last().numbers.size)
    }

    @Test fun theSameNumberWrittenTwoWaysIsListedOnce() {
        val groups = groupContacts(listOf(ana1, ana1.copy(number = "+63 917 555 0199")))
        assertEquals(1, groups.single().numbers.size)
    }

    @Test fun theInitialSkipsSymbolsAndFallsBackToAHash() {
        assertEquals("A", ContactGroup("Ana", 1, "", emptyList()).initial)
        assertEquals("J", ContactGroup("  (Jo) ", 1, "", emptyList()).initial)
        assertEquals("#", ContactGroup("--", 1, "", emptyList()).initial)
    }

    @Test fun searchFindsByNameOrDigitsAndBlankKeepsEveryone() {
        val groups = groupContacts(listOf(ana1, ana2, ben, noId1))
        assertEquals(4 - 1, filterGroups(groups, "").size)
        assertEquals(listOf("Ana Cruz"), filterGroups(groups, "cruz").map { it.name })
        assertEquals(listOf("ben reyes"), filterGroups(groups, "918 555").map { it.name })
        assertEquals(listOf("Ana Cruz"), filterGroups(groups, "85550100").map { it.name }) // the second number of Ana
        assertTrue(filterGroups(groups, "xyz").isEmpty())
        assertTrue(filterGroups(groups, "55").isEmpty()) // two digits are not enough to search numbers
    }
}
