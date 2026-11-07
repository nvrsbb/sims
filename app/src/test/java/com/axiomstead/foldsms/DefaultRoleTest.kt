package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRoleTest {
    private val access = SmsAccess(true, true, true, true, true, true, true)

    @Test fun theOfferAppearsOnlyWhenAccessIsOnTheRoleIsAvailableAndFoldSmsIsNotDefault() {
        assertTrue(shouldOfferDefault(access, dismissed = false, roleAvailable = true))
        assertFalse(shouldOfferDefault(access.copy(isDefaultApp = true), false, true))
        assertFalse(shouldOfferDefault(access, dismissed = true, roleAvailable = true))
        assertFalse(shouldOfferDefault(access, false, roleAvailable = false))
        assertFalse(shouldOfferDefault(access.copy(enabled = false), false, true))
        assertFalse(shouldOfferDefault(access.copy(messagingSupported = false), false, true))
    }

    @Test fun beingTheDefaultAppDoesNotStopReadingOrSending() {
        val self = access.copy(isDefaultApp = true)
        assertTrue(self.canRead && self.canSend)
        assertFalse(self.copy(hasDefaultApp = false).canRead) // no default app at all is still not usable
    }

    @Test fun aSmsLinkYieldsOnlyItsFirstNumber() {
        assertEquals("+15550100", recipientFromLink("smsto", "+15550100"))
        assertEquals("+15550100", recipientFromLink("SMS", "+15550100?body=hi%20there"))
        assertEquals("+15550100", recipientFromLink("sms", "+15550100,+15550199"))
        assertEquals("+15550100", recipientFromLink("mmsto", " +15550100;extra "))
        assertNull(recipientFromLink("https", "+15550100"))
        assertNull(recipientFromLink("sms", "?body=hi"))
        assertNull(recipientFromLink(null, "+15550100"))
    }
}
