package com.cslearningos.mobile.appshell.state

import com.cslearningos.mobile.appshell.navigation.AppRoute
import com.cslearningos.mobile.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class AppShellViewModelTest {
    @Test
    fun navigateUpdatesCurrentRoute() {
        val viewModel = AppShellViewModel()

        viewModel.navigate(AppRoute.Review)

        assertEquals(AppRoute.Review, viewModel.state.value.route)
    }

    @Test
    fun syncFromCopiesShellStateIntoViewModel() {
        val viewModel = AppShellViewModel()
        val shellState = AppShellState(
            route = AppRoute.Backup,
            message = UiText.Dynamic("Queued")
        )

        viewModel.syncFrom(shellState)

        assertEquals(shellState, viewModel.state.value)
    }
}
