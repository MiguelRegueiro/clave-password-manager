package com.clave.vault

import org.junit.Assert.*
import org.junit.Test

class ThemeModeTest {
    @Test fun followsSystemByDefault() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("unknown"))
        assertTrue(ThemeMode.SYSTEM.isDark(true))
        assertFalse(ThemeMode.SYSTEM.isDark(false))
    }
    @Test fun explicitChoiceOverridesSystem() {
        assertTrue(ThemeMode.fromStored("DARK").isDark(false))
        assertFalse(ThemeMode.fromStored("LIGHT").isDark(true))
    }
}
