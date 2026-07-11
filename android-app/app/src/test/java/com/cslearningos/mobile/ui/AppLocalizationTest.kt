package com.cslearningos.mobile.ui

import java.util.Locale
import java.io.File
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

        listOf("Capture", "Library", "Review", "Backup").forEach { label ->
            assertTrue(defaultStrings.contains("Open $label") || defaultStrings.contains(label))
        }
        assertTrue(defaultStrings.contains("find it in Library"))
        assertTrue(defaultStrings.contains("restore replaces current local data"))
        assertTrue(defaultStrings.contains("export first"))
        assertTrue(defaultStrings.contains("Delete forever"))
        assertTrue(defaultStrings.contains("only recoverable from a backup"))
        assertFalse(defaultStrings.contains("adapter"))
        assertFalse(defaultStrings.contains("source of truth"))

        assertTrue(chineseStrings.contains("在知识库里找到它"))
        assertTrue(chineseStrings.contains("恢复会替换当前本地数据"))
        assertTrue(chineseStrings.contains("请先导出"))
        assertTrue(chineseStrings.contains("永久删除"))
        assertTrue(chineseStrings.contains("只能从备份恢复"))
    }

    private fun resourceText(relativePath: String): String {
        val root = File(System.getProperty("user.dir"))
        return root.resolve("src/main/res").resolve(relativePath).readText(Charsets.UTF_8)
    }
}
