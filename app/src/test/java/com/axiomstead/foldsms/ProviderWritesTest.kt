package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderWritesTest {
    @Test fun finalStagesMapToSentOrFailedAndEverythingElseStaysQueued() {
        assertEquals(SMS_TYPE_SENT, storedTypeFor("SENT"))
        assertEquals(SMS_TYPE_FAILED, storedTypeFor("FAILED"))
        assertEquals(SMS_TYPE_FAILED, storedTypeFor("PARTIAL")) // some parts did not go out: never show it as sent
        for (stage in listOf("PREPARED", "SUBMITTED", "REJECTED", "")) assertNull(storedTypeFor(stage))
    }

    @Test fun dividedPartsJoinBackToTheConfirmedText() {
        assertEquals("Hello, wörld 😀 end", joinSmsParts(listOf("Hello, w", "örld 😀", " end")))
        assertEquals("", joinSmsParts(emptyList()))
    }
}
