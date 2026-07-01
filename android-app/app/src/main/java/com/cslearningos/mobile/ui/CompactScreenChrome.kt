package com.cslearningos.mobile.ui

enum class ScreenChromeStyle {
    Hero,
    Compact
}

enum class ScreenHelpPlacement {
    Top,
    AfterPrimaryActionsCollapsed,
    InlineDetail
}

enum class ScreenPrimaryFlow {
    ContextFirst,
    TaskFirst
}

data class ScreenChromePolicy(
    val style: ScreenChromeStyle,
    val helpPlacement: ScreenHelpPlacement,
    val primaryFlow: ScreenPrimaryFlow
)

fun screenChromePolicy(screen: AppScreen): ScreenChromePolicy =
    when (screen) {
        AppScreen.Home -> ScreenChromePolicy(
            style = ScreenChromeStyle.Hero,
            helpPlacement = ScreenHelpPlacement.Top,
            primaryFlow = ScreenPrimaryFlow.ContextFirst
        )

        AppScreen.Reader,
        AppScreen.Editor,
        AppScreen.QuizEditor -> ScreenChromePolicy(
            style = ScreenChromeStyle.Compact,
            helpPlacement = ScreenHelpPlacement.InlineDetail,
            primaryFlow = ScreenPrimaryFlow.TaskFirst
        )

        else -> ScreenChromePolicy(
            style = ScreenChromeStyle.Compact,
            helpPlacement = ScreenHelpPlacement.AfterPrimaryActionsCollapsed,
            primaryFlow = ScreenPrimaryFlow.TaskFirst
        )
    }

fun screenHelpInitiallyExpanded(screen: AppScreen): Boolean =
    screenChromePolicy(screen).helpPlacement == ScreenHelpPlacement.Top
