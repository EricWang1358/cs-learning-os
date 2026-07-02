package com.cslearningos.mobile.appshell.state

import androidx.lifecycle.ViewModel
import com.cslearningos.mobile.appshell.navigation.AppRoute
import com.cslearningos.mobile.ui.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns app-wide shell concerns only. Feature-specific behavior should live in
 * feature view-models and navigate through this shell when needed.
 */
class AppShellViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(AppShellState())
    val state: StateFlow<AppShellState> = mutableState.asStateFlow()

    fun navigate(route: AppRoute) {
        mutableState.update { current ->
            current.copy(route = route)
        }
    }

    fun syncFrom(shellState: AppShellState) {
        if (mutableState.value == shellState) {
            return
        }
        mutableState.value = shellState
    }

    fun showMessage(message: UiText?) {
        mutableState.update { current ->
            current.copy(message = message)
        }
    }
}
