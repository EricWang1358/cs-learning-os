package com.cslearningos.mobile.ui

enum class SystemLanguage(val label: String) {
    FollowSystem("Follow system"),
    English("English"),
    Chinese("\u4e2d\u6587")
}

enum class AppearanceMode(val label: String) {
    FollowSystem("Follow system"),
    Day("Day"),
    Night("Night")
}

enum class MoreSectionId {
    System,
    Service,
    Data,
    Support
}

data class MoreSectionSummary(
    val id: MoreSectionId,
    val title: String,
    val body: String,
    val value: String
)

data class MoreSettingsCopy(
    val eyebrow: String,
    val title: String,
    val body: String,
    val expand: String,
    val collapse: String,
    val systemLanguage: String,
    val languageNote: String,
    val appearance: String,
    val appearanceNote: String,
    val provider: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val connection: String,
    val validate: String,
    val pullModels: String,
    val localData: String,
    val supportContract: String
)

private val EnglishMoreCopy = MoreSettingsCopy(
    eyebrow = "settings center",
    title = "More",
    body = "A compact settings list. Expand only the tool you need: system, service, data, or support.",
    expand = "Expand",
    collapse = "Collapse",
    systemLanguage = "System language",
    languageNote = "Stored locally. Follow system uses the phone language when the app has matching copy.",
    appearance = "Day / Night mode",
    appearanceNote = "Follow system tracks the Android display setting; Day and Night override it locally.",
    provider = "Provider",
    apiKey = "API key",
    baseUrl = "Base URL",
    model = "Model",
    connection = "Connection",
    validate = "Validate",
    pullModels = "Pull models",
    localData = "Local data",
    supportContract = "Local-first contract"
)

private val ChineseMoreCopy = MoreSettingsCopy(
    eyebrow = "\u8bbe\u7f6e\u4e2d\u5fc3",
    title = "\u66f4\u591a",
    body = "\u6536\u655b\u6210\u5217\u8868\u5f0f\u8bbe\u7f6e\uff1a\u7cfb\u7edf\u3001\u670d\u52a1\u3001\u6570\u636e\u3001\u652f\u6301\u9700\u8981\u65f6\u518d\u5c55\u5f00\u3002",
    expand = "\u5c55\u5f00",
    collapse = "\u6536\u8d77",
    systemLanguage = "\u7cfb\u7edf\u8bed\u8a00",
    languageNote = "\u8bbe\u7f6e\u4fdd\u5b58\u5728\u672c\u673a\u3002\u8ddf\u968f\u7cfb\u7edf\u65f6\uff0c\u4f1a\u4f18\u5148\u4f7f\u7528\u624b\u673a\u8bed\u8a00\u5bf9\u5e94\u7684\u6587\u6848\u3002",
    appearance = "\u767d\u5929 / \u9ed1\u591c\u6a21\u5f0f",
    appearanceNote = "\u8ddf\u968f\u7cfb\u7edf\u4f1a\u8ddf\u968f Android \u663e\u793a\u8bbe\u7f6e\uff1b\u767d\u5929\u548c\u9ed1\u591c\u662f App \u5185\u72ec\u7acb\u8986\u76d6\u3002",
    provider = "\u4f9b\u5e94\u5546",
    apiKey = "API \u5bc6\u94a5",
    baseUrl = "\u63a5\u53e3\u5730\u5740",
    model = "\u6a21\u578b",
    connection = "\u8fde\u63a5\u68c0\u67e5",
    validate = "\u9a8c\u8bc1",
    pullModels = "\u62c9\u53d6\u6a21\u578b",
    localData = "\u672c\u5730\u6570\u636e",
    supportContract = "\u672c\u5730\u4f18\u5148\u5951\u7ea6"
)

fun moreSectionSummaries(
    language: SystemLanguage,
    appearance: AppearanceMode,
    aiConfigured: Boolean,
    effectiveLanguage: SystemLanguage = language
): List<MoreSectionSummary> =
    if (effectiveLanguage == SystemLanguage.Chinese) {
        listOf(
            MoreSectionSummary(
                id = MoreSectionId.System,
                title = "\u7cfb\u7edf",
                body = "\u8bed\u8a00\u548c\u767d\u5929 / \u9ed1\u591c\u663e\u793a\u3002",
                value = "${language.label} / ${appearance.label}"
            ),
            MoreSectionSummary(
                id = MoreSectionId.Service,
                title = "\u670d\u52a1",
                body = "\u7528\u4e8e\u968f\u624b\u8bb0\u6269\u5199\u7684\u53ef\u9009 AI \u4f9b\u5e94\u5546\u3002",
                value = if (aiConfigured) "\u5df2\u914d\u7f6e" else "\u672a\u914d\u7f6e"
            ),
            MoreSectionSummary(
                id = MoreSectionId.Data,
                title = "\u6570\u636e",
                body = "\u5907\u4efd\u3001\u5bfc\u51fa\u3001\u5bfc\u5165\u548c\u7535\u8111\u540c\u6b65\u3002",
                value = "\u672c\u5730\u4f18\u5148"
            ),
            MoreSectionSummary(
                id = MoreSectionId.Support,
                title = "\u652f\u6301",
                body = "\u8bca\u65ad\u548c\u4ea7\u54c1\u5951\u7ea6\u3002",
                value = "\u79bb\u7ebf\u5b89\u5168"
            )
        )
    } else {
        listOf(
            MoreSectionSummary(
                id = MoreSectionId.System,
                title = "System",
                body = "Language and day/night display.",
                value = "${language.label} / ${appearance.label}"
            ),
            MoreSectionSummary(
                id = MoreSectionId.Service,
                title = "Service",
                body = "Optional AI provider for future capture drafts.",
                value = if (aiConfigured) "Configured" else "Not configured"
            ),
            MoreSectionSummary(
                id = MoreSectionId.Data,
                title = "Data",
                body = "Backup, export, import, and desktop sync.",
                value = "Local first"
            ),
            MoreSectionSummary(
                id = MoreSectionId.Support,
                title = "Support",
                body = "Diagnostics and product contract.",
                value = "Offline safe"
            )
        )
    }

fun moreSettingsCopy(language: SystemLanguage, systemLanguageTag: String): MoreSettingsCopy =
    if (resolveSystemLanguage(language, systemLanguageTag) == SystemLanguage.Chinese) {
        ChineseMoreCopy
    } else {
        EnglishMoreCopy
    }

fun resolveSystemLanguage(language: SystemLanguage, systemLanguageTag: String): SystemLanguage =
    when (language) {
        SystemLanguage.FollowSystem -> {
            if (systemLanguageTag.lowercase().startsWith("zh")) SystemLanguage.Chinese else SystemLanguage.English
        }
        else -> language
    }

fun AppearanceMode.usesDarkTheme(systemDark: Boolean): Boolean =
    when (this) {
        AppearanceMode.FollowSystem -> systemDark
        AppearanceMode.Day -> false
        AppearanceMode.Night -> true
    }
