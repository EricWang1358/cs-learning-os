package com.cslearningos.mobile.appshell.state

import com.cslearningos.mobile.appshell.navigation.AppRoute
import com.cslearningos.mobile.appshell.navigation.toAppRoute
import com.cslearningos.mobile.ui.LearningUiState
import com.cslearningos.mobile.ui.UiText

/**
 * App-shell state is intentionally small: route selection plus cross-feature
 * message delivery that does not belong to a single feature view-model.
 */
data class AppShellState(
    val route: AppRoute = AppRoute.Home,
    val message: UiText? = null
)

fun LearningUiState.toAppShellState(): AppShellState =
    AppShellState(
        route = screen.toAppRoute(),
        message = message
    )
