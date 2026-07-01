package com.cslearningos.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreSettingsModelsTest {
    @Test
    fun moreSectionsCollapseIntoSettingsListOrder() {
        val sections = moreSectionSummaries(
            language = SystemLanguage.FollowSystem,
            appearance = AppearanceMode.FollowSystem,
            aiConfigured = false,
            effectiveLanguage = SystemLanguage.English
        )

        assertEquals(
            listOf(MoreSectionId.System, MoreSectionId.Service, MoreSectionId.Data, MoreSectionId.Support),
            sections.map { it.id }
        )
        assertEquals("Follow system / Follow system", sections.first().value)
    }

    @Test
    fun serviceCopyDescribesImplementedCaptureDraftingNotFuturePlaceholder() {
        val section = moreSectionSummaries(
            language = SystemLanguage.English,
            appearance = AppearanceMode.Night,
            aiConfigured = true,
            effectiveLanguage = SystemLanguage.English
        ).first { it.id == MoreSectionId.Service }

        assertEquals("Optional AI provider for capture drafting.", section.body)
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
    fun appearanceModeResolvesDarkTheme() {
        assertTrue(AppearanceMode.FollowSystem.usesDarkTheme(systemDark = true))
        assertFalse(AppearanceMode.FollowSystem.usesDarkTheme(systemDark = false))
        assertFalse(AppearanceMode.Day.usesDarkTheme(systemDark = true))
        assertTrue(AppearanceMode.Night.usesDarkTheme(systemDark = false))
    }

    @Test
    fun chineseCopyCanDriveMoreSettings() {
        val copy = moreSettingsCopy(SystemLanguage.FollowSystem, "zh-Hans-CN")
        val sections = moreSectionSummaries(
            language = SystemLanguage.FollowSystem,
            appearance = AppearanceMode.Night,
            aiConfigured = true,
            effectiveLanguage = SystemLanguage.Chinese
        )

        assertEquals("\u66f4\u591a", copy.title)
        assertEquals("\u7cfb\u7edf", sections.first().title)
        assertEquals("\u5df2\u914d\u7f6e", sections[1].value)
    }
}
