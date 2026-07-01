package com.cslearningos.mobile.ui

enum class SystemLanguage(val label: String) {
    FollowSystem("Follow system"),
    English("English"),
    Chinese("中文")
}

enum class AppearanceMode(val label: String) {
    FollowSystem("Follow system"),
    Day("Day"),
    Night("Night")
}

enum class MoreSectionId {
    System,
    Service,
    Notifications,
    Data,
    Support
}

fun orderedMoreSectionIds(): List<MoreSectionId> =
    listOf(
        MoreSectionId.System,
        MoreSectionId.Service,
        MoreSectionId.Notifications,
        MoreSectionId.Data,
        MoreSectionId.Support
    )

fun resolveSystemLanguage(language: SystemLanguage, systemLanguageTag: String): SystemLanguage =
    when (language) {
        SystemLanguage.FollowSystem ->
            if (systemLanguageTag.lowercase().startsWith("zh")) SystemLanguage.Chinese else SystemLanguage.English

        else -> language
    }

fun AppearanceMode.usesDarkTheme(systemDark: Boolean): Boolean =
    when (this) {
        AppearanceMode.FollowSystem -> systemDark
        AppearanceMode.Day -> false
        AppearanceMode.Night -> true
    }
