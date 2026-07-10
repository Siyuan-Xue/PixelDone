package com.milesxue.pixeldone

import com.milesxue.pixeldone.domain.todo.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageDisplayNameTest {
    @Test
    fun concreteLanguagesAlwaysExposeTheirNativeNames() {
        assertEquals("English", AppLanguage.ENGLISH.nativeDisplayName)
        assertEquals("简体中文", AppLanguage.SIMPLIFIED_CHINESE.nativeDisplayName)
        assertEquals("العربية", AppLanguage.ARABIC.nativeDisplayName)
        assertEquals("Français", AppLanguage.FRENCH.nativeDisplayName)
        assertEquals("Русский", AppLanguage.RUSSIAN.nativeDisplayName)
        assertEquals("Español", AppLanguage.SPANISH.nativeDisplayName)
    }

    @Test
    fun systemLanguageRemainsLocalizedByTheUi() {
        assertNull(AppLanguage.SYSTEM.nativeDisplayName)
    }
}
