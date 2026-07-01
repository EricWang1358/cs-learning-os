package com.cslearningos.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreSettingsModelsTest {
    @Test
    fun moreSectionsCollapseIntoSettingsListOrder() {
        assertEquals(
            listOf(
                MoreSectionId.System,
                MoreSectionId.Service,
                MoreSectionId.Notifications,
                MoreSectionId.Data,
                MoreSectionId.Support
            ),
            orderedMoreSectionIds()
        )
    }

    @Test
    fun languageAndAppearanceExposeMobileFriendlyChoices() {
        assertEquals(listOf("Follow system", "English", "\u4e2d\u6587"), SystemLanguage.entries.map { it.label })
        assertEquals(listOf("Follow system", "Day", "Night"), AppearanceMode.entries.map { it.label })
    }

    @Test
    fun followSystemLanguageUsesPhoneLanguageWhenKnown() {
        assertEquals(SystemLanguage.Chinese, resolveSystemLanguage(SystemLanguage.FollowSystem, "zh-CN"))
        assertEquals(SystemLanguage.English, resolveSystemLanguage(SystemLanguage.FollowSystem, "en-US"))
        assertEquals(SystemLanguage.Chinese, resolveSystemLanguage(SystemLanguage.Chinese, "en-US"))
    }

    @Test
    fun appLocaleHelpersSeparateAppWideLocaleFromSettingsDisplayCopy() {
        assertEquals("en", appLanguageTag(SystemLanguage.English, "zh-CN"))
        assertEquals("zh-CN", appLanguageTag(SystemLanguage.Chinese, "en-US"))
        assertEquals(null, appLanguageTag(SystemLanguage.FollowSystem, "zh-CN"))
    }

    @Test
    fun appearanceModeResolvesDarkTheme() {
        assertTrue(AppearanceMode.FollowSystem.usesDarkTheme(systemDark = true))
        assertFalse(AppearanceMode.FollowSystem.usesDarkTheme(systemDark = false))
        assertFalse(AppearanceMode.Day.usesDarkTheme(systemDark = true))
        assertTrue(AppearanceMode.Night.usesDarkTheme(systemDark = false))
    }

    @Test
    fun moreSettingsModelsKeepOnlyStateAndSelectionLogic() {
        assertEquals(5, orderedMoreSectionIds().size)
        assertEquals(MoreSectionId.System, orderedMoreSectionIds().first())
        assertEquals(MoreSectionId.Support, orderedMoreSectionIds().last())
    }
}
