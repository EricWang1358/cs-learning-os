package com.cslearningos.mobile.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private const val EnglishLanguageTag = "en"
private const val ChineseLanguageTag = "zh-CN"

fun appLanguageTag(language: SystemLanguage, systemLanguageTag: String): String? =
    when (language) {
        SystemLanguage.FollowSystem -> null
        SystemLanguage.English -> EnglishLanguageTag
        SystemLanguage.Chinese -> ChineseLanguageTag
    }

fun appLocale(language: SystemLanguage, systemLanguageTag: String): Locale? =
    appLanguageTag(language, systemLanguageTag)?.let(Locale::forLanguageTag)

fun resourceLocale(language: SystemLanguage, systemLanguageTag: String): Locale =
    appLocale(language, systemLanguageTag)
        ?: systemLanguageTag.toLocaleOrNull()
        ?: Locale.ENGLISH

fun Context.localizedAppContext(language: SystemLanguage, systemLanguageTag: String): Context {
    val locale = appLocale(language, systemLanguageTag) ?: return this
    Locale.setDefault(locale)

    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLocales(LocaleList(locale))
    }
    return createConfigurationContext(configuration)
}

@Composable
fun rememberLocalizedAppContext(language: SystemLanguage): Context {
    val baseContext = LocalContext.current
    val systemLanguageTag = LocalConfiguration.current.locales[0]?.toLanguageTag().orEmpty()
    return remember(baseContext, language, systemLanguageTag) {
        baseContext.localizedAppContext(language, systemLanguageTag)
    }
}

private fun String.toLocaleOrNull(): Locale? {
    if (isBlank()) return null
    val locale = Locale.forLanguageTag(this)
    return locale.takeUnless { it == Locale.ROOT }
}
