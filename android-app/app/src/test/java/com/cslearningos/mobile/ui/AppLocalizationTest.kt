package com.cslearningos.mobile.ui

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun tutorialAndRecoveryCopyIsActionableInEnglishAndChinese() {
        val defaultStrings = resourceText("values/strings.xml")
        val chineseStrings = resourceText("values-zh/strings.xml")

        listOf("Capture", "Library", "Review", "More").forEach { label ->
            assertTrue(defaultStrings.contains(label))
        }
        assertTrue(defaultStrings.contains("local first"))
        assertTrue(defaultStrings.contains("More - Service"))
        assertTrue(defaultStrings.contains("generated output is always a proposal"))
        assertTrue(defaultStrings.contains("restore replaces current local data"))
        assertTrue(defaultStrings.contains("Study Sync"))
        assertTrue(defaultStrings.contains("phone-friendly study subset"))
        assertTrue(defaultStrings.contains("Complex knowledge graph structure stays desktop-first"))
        assertTrue(defaultStrings.contains("%3\$d bites"))
        assertTrue(defaultStrings.contains("export a backup"))
        assertTrue(defaultStrings.contains("Delete forever"))
        assertTrue(defaultStrings.contains("only recoverable from a backup"))
        assertFalse(defaultStrings.contains("adapter"))
        assertFalse(defaultStrings.contains("source of truth"))
        assertTrue(defaultStrings.contains("more_section_expanded"))

        assertTrue(chineseStrings.contains("Room"))
        assertTrue(chineseStrings.contains("API Key"))
        assertTrue(chineseStrings.contains("HTTPS"))
        assertTrue(chineseStrings.contains("Markdown"))
        assertTrue(chineseStrings.contains("复习同步"))
        assertTrue(chineseStrings.contains("适合手机使用的学习子集"))
        assertTrue(chineseStrings.contains("复杂知识图谱结构仍以电脑端为准"))
        assertTrue(chineseStrings.contains("%3\$d 每日一练"))
        assertTrue(chineseStrings.contains("more_guide_step_bodies"))
        assertTrue(chineseStrings.contains("more_section_expanded"))
    }

    private fun resourceText(relativePath: String): String {
        val root = File(requireNotNull(System.getProperty("user.dir")))
        return root.resolve("src/main/res").resolve(relativePath).readText(Charsets.UTF_8)
    }
}
