package com.cslearningos.mobile.appshell.navigation

import com.cslearningos.mobile.ui.AppScreen

/**
 * Top-level routes owned by the Android app shell.
 *
 * Feature-specific state should not redefine this list. The shell uses these
 * routes to decide which feature surface is active.
 */
enum class AppRoute {
    Home,
    Capture,
    Library,
    Reader,
    Editor,
    Search,
    QuizEditor,
    Review,
    Backup,
    More
}

fun AppRoute.toAppScreen(): AppScreen =
    when (this) {
        AppRoute.Home -> AppScreen.Home
        AppRoute.Capture -> AppScreen.Capture
        AppRoute.Library -> AppScreen.Library
        AppRoute.Reader -> AppScreen.Reader
        AppRoute.Editor -> AppScreen.Editor
        AppRoute.Search -> AppScreen.Search
        AppRoute.QuizEditor -> AppScreen.QuizEditor
        AppRoute.Review -> AppScreen.Review
        AppRoute.Backup -> AppScreen.Backup
        AppRoute.More -> AppScreen.More
    }

fun AppScreen.toAppRoute(): AppRoute =
    when (this) {
        AppScreen.Home -> AppRoute.Home
        AppScreen.Capture -> AppRoute.Capture
        AppScreen.Library -> AppRoute.Library
        AppScreen.Reader -> AppRoute.Reader
        AppScreen.Editor -> AppRoute.Editor
        AppScreen.Search -> AppRoute.Search
        AppScreen.QuizEditor -> AppRoute.QuizEditor
        AppScreen.Review -> AppRoute.Review
        AppScreen.Backup -> AppRoute.Backup
        AppScreen.More -> AppRoute.More
    }

fun selectedBottomTabFor(route: AppRoute): AppRoute =
    when (route) {
        AppRoute.Home -> AppRoute.Home
        AppRoute.Capture -> AppRoute.Capture
        AppRoute.Library,
        AppRoute.Reader,
        AppRoute.Editor,
        AppRoute.Search,
        AppRoute.QuizEditor -> AppRoute.Library
        AppRoute.Review -> AppRoute.Review
        AppRoute.Backup,
        AppRoute.More -> AppRoute.More
    }
