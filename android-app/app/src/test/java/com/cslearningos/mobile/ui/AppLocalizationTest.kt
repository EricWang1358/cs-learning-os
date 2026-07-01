package com.cslearningos.mobile.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLocalizationTest {
    @Test
    fun explicitEnglishMapsToEnglishLocaleTag() {
        assertEquals("en", appLanguageTag(SystemLanguage.English, "zh-CN"))
    }

    @Test
    fun explicitChineseMapsToChineseLocaleTag() {
        assertEquals("zh-CN", appLanguageTag(SystemLanguage.Chinese, "en-US"))
    }

    @Test
    fun followSystemDoesNotForceAnOverrideLocale() {
        assertNull(appLanguageTag(SystemLanguage.FollowSystem, "zh-CN"))
        assertNull(appLanguageTag(SystemLanguage.FollowSystem, "en-US"))
    }

    @Test
    fun appLocaleUsesLanguageTagWhenOverrideExists() {
        assertEquals("zh-CN", appLocale(SystemLanguage.Chinese, "en-US")?.toLanguageTag())
        assertEquals("en", appLocale(SystemLanguage.English, "zh-CN")?.toLanguageTag())
    }

    @Test
    fun appLocaleReturnsNullWhenFollowingSystem() {
        assertNull(appLocale(SystemLanguage.FollowSystem, "zh-CN"))
    }

    @Test
    fun resourceLocaleUsesOverrideWhenPresent() {
        assertEquals(Locale.forLanguageTag("zh-CN"), resourceLocale(SystemLanguage.Chinese, "en-US"))
        assertEquals(Locale.forLanguageTag("en"), resourceLocale(SystemLanguage.English, "zh-CN"))
    }

    @Test
    fun resourceLocaleFallsBackToSystemWhenFollowingSystem() {
        assertEquals(Locale.forLanguageTag("zh-Hans-CN"), resourceLocale(SystemLanguage.FollowSystem, "zh-Hans-CN"))
        assertEquals(Locale.ENGLISH, resourceLocale(SystemLanguage.FollowSystem, ""))
    }
}
