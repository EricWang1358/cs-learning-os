package com.cslearningos.mobile.appshell.state

import com.cslearningos.mobile.appshell.navigation.AppRoute
import com.cslearningos.mobile.ui.AppScreen
import com.cslearningos.mobile.ui.LearningUiState
import com.cslearningos.mobile.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class AppShellStateTest {
    @Test
    fun learningUiStateMapsToShellRouteAndMessage() {
        val message = UiText.Dynamic("Saved")
        val learningState = LearningUiState(
            screen = AppScreen.Search,
            message = message
        )

        val shellState = learningState.toAppShellState()

        assertEquals(AppRoute.Search, shellState.route)
        assertEquals(message, shellState.message)
    }
}
