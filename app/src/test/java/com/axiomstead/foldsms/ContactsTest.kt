package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsTest {
    private val ana = ContactEntry("Ana Cruz", "0917 555 0199")
    private val ben = ContactEntry("Ben Reyes", "+63 918 555 0123")
    private val anaWork = ContactEntry("Ana Cruz", "(02) 8555-0100")

    @Test fun aNumberStoredInAnyFormatMatchesTheNumberInTheThread() {
        val names = contactNameMap(listOf(ana, ben))
        assertEquals("Ana Cruz", names[conversationAddressKey("+639175550199")])
        assertEquals("Ben Reyes", names[conversationAddressKey("09185550123")])
        assertEquals(null, names[conversationAddressKey("+15550100")])
    }

    @Test fun twoContactsWithTheSameNumberGiveTheFirstByName() {
        val names = contactNameMap(listOf(ContactEntry("Zed", "0917 555 0199"), ContactEntry("Abe", "+63 917 555 0199")))
        assertEquals("Abe", names.values.single())
    }

    @Test fun searchFindsByNameOrDigitsAndPutsPrefixMatchesFirst() {
        val all = listOf(ben, ana, anaWork, ContactEntry("Joana Lim", "0919 000 1111"))
        assertEquals(listOf("Ana Cruz", "Ana Cruz", "Joana Lim"), searchContacts(all, "ana").map { it.name })
        assertEquals(listOf("Ben Reyes"), searchContacts(all, "918 555").map { it.name })
        assertTrue(searchContacts(all, "").isEmpty())
        assertTrue(searchContacts(all, "xyz").isEmpty())
        assertTrue(searchContacts(all, "91").isEmpty()) // under three digits only names are searched
    }

    @Test fun searchHonoursTheLimitAndDropsRepeatedNumbers() {
        val many = (1..20).map { ContactEntry("Ana $it", "0917555%04d".format(it)) }
        assertEquals(5, searchContacts(many, "ana", limit = 5).size)
        assertEquals(1, searchContacts(listOf(ana, ana.copy(number = "+63 917 555 0199")), "ana").size)
    }

    private fun message(address: String) = DeviceMessage(DeviceMessageKind.SMS, 1, address, "hi", "", 1_000, false, false, -1, 0, 0, false)

    @Test fun conversationsTakeTheContactNameForTitleAndSearch() {
        val thread = DeviceConversation("k", 5, "+639175550199", listOf(message("+639175550199")))
        val other = DeviceConversation("k2", 6, "EXAMPLEBANK", listOf(message("EXAMPLEBANK")))
        val named = withContactNames(listOf(thread, other), contactNameMap(listOf(ana)))
        assertEquals("Ana Cruz", named[0].title)
        assertEquals("EXAMPLEBANK", named[1].title) // senders that are not numbers never match a contact
        assertEquals(listOf("k"), searchDeviceConversations(named, "cruz").map { it.key })
        assertEquals(listOf("k"), searchDeviceConversations(named, "5550199").map { it.key }) // the number still finds it
        assertEquals(thread, withContactNames(listOf(thread), emptyMap()).single())
    }
}
