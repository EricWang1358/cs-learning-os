package com.cslearningos.mobile.ui

import android.content.Context
import androidx.annotation.StringRes
import com.cslearningos.mobile.R

data class MobileBottomNavItem(
    @StringRes val labelResId: Int,
    val screen: AppScreen,
    @StringRes val contentDescriptionResId: Int
)

fun mobileBottomNavItems(): List<MobileBottomNavItem> =
    listOf(
        MobileBottomNavItem(R.string.nav_home_label, AppScreen.Home, R.string.nav_home_description),
        MobileBottomNavItem(R.string.nav_capture_label, AppScreen.Capture, R.string.nav_capture_description),
        MobileBottomNavItem(R.string.nav_library_label, AppScreen.Library, R.string.nav_library_description),
        MobileBottomNavItem(R.string.nav_review_label, AppScreen.Review, R.string.nav_review_description),
        MobileBottomNavItem(R.string.nav_more_label, AppScreen.More, R.string.nav_more_description)
    )

@StringRes
fun appScreenLabelResId(screen: AppScreen): Int =
    when (screen) {
        AppScreen.Home -> R.string.nav_home_label
        AppScreen.Assistant -> R.string.assistant_title
        AppScreen.Capture -> R.string.nav_capture_label
        AppScreen.Library -> R.string.nav_library_label
        AppScreen.Reader -> R.string.common_read
        AppScreen.Editor -> R.string.common_edit
        AppScreen.Search -> R.string.common_search
        AppScreen.QuizEditor -> R.string.quiz_editor_title
        AppScreen.Review -> R.string.nav_review_label
        AppScreen.Backup -> R.string.backup_title
        AppScreen.More -> R.string.nav_more_label
        AppScreen.AssistantGuide -> R.string.assistant_guide_title
    }

@StringRes
fun systemLanguageLabelResId(language: SystemLanguage): Int =
    when (language) {
        SystemLanguage.FollowSystem -> R.string.language_follow_system
        SystemLanguage.English -> R.string.language_english
        SystemLanguage.Chinese -> R.string.language_chinese
    }

@StringRes
fun appearanceModeLabelResId(mode: AppearanceMode): Int =
    when (mode) {
        AppearanceMode.FollowSystem -> R.string.appearance_follow_system
        AppearanceMode.Day -> R.string.appearance_day
        AppearanceMode.Night -> R.string.appearance_night
    }

fun readableAreaLabel(context: Context?, area: String): String =
    readableAreaLabelResId(area)
        ?.let { resId -> context?.getString(resId) ?: englishAreaFallback(area) }
        ?: slugToLabel(area)

fun readableTrackLabel(context: Context?, track: String): String =
    slugToLabel(track)

@StringRes
private fun readableAreaLabelResId(area: String): Int? =
    when (area) {
        "cs-fundamentals" -> R.string.library_area_cs_fundamentals
        "algorithms" -> R.string.library_area_algorithms
        "projects" -> R.string.library_area_projects
        "abilities" -> R.string.library_area_abilities
        "tools" -> R.string.library_area_tools
        "questions" -> R.string.library_area_questions
        else -> null
    }

private fun slugToLabel(value: String): String =
    value
        .split('-', '_')
        .joinToString(" ") { token -> token.replaceFirstChar(Char::uppercaseChar) }

private fun englishAreaFallback(area: String): String =
    when (area) {
        "cs-fundamentals" -> "CS Fundamentals"
        "algorithms" -> "Algorithms"
        "projects" -> "Projects"
        "abilities" -> "Abilities"
        "tools" -> "Tools"
        "questions" -> "Questions"
        else -> slugToLabel(area)
    }
