package com.axiomstead.foldsms

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatStyleTest {
    @Test fun initialsUseUpToTwoWordsAndIgnoreSymbols() {
        assertEquals("BA", avatarInitials("Bank Alert"))
        assertEquals("A", avatarInitials("Alex"))
        assertEquals("PU", avatarInitials("  parcel   updates  extra "))
        assertEquals("6", avatarInitials("+63 ###"))
        assertEquals("#", avatarInitials("+-- !!"))
        assertEquals("#", avatarInitials(""))
    }

    @Test fun avatarToneIsStableForNegativeCustomIds() {
        assertEquals(1, avatarToneIndex(1, 3))
        assertEquals(1, avatarToneIndex(4, 3))
        assertEquals(2, avatarToneIndex(-1, 3))
        assertEquals(0, avatarToneIndex(-3, 3))
    }
}
