package com.axiomstead.foldsms

import org.junit.Assert.*
import org.junit.Test

class ThemeChoiceTest {
    @Test fun absentAndUnrecognizedPreferencesFollowDevice() {
        assertEquals(ThemeChoice.DEVICE, ThemeChoice.fromStored(null))
        assertEquals(ThemeChoice.DEVICE, ThemeChoice.fromStored("future-theme"))
        ThemeChoice.entries.forEach { assertEquals(it, ThemeChoice.fromStored(it.name)) }
    }

    @Test fun deviceFollowsAndroidAndExplicitChoicesOverrideIt() {
        assertFalse(ThemeChoice.DEVICE.usesDarkColors(false))
        assertTrue(ThemeChoice.DEVICE.usesDarkColors(true))
        assertFalse(ThemeChoice.LIGHT.usesDarkColors(true))
        assertTrue(ThemeChoice.DARK.usesDarkColors(false))
    }
}
