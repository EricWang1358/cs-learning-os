package com.cslearningos.mobile.feature.library.ui

import androidx.lifecycle.ViewModel
import com.cslearningos.mobile.feature.library.data.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LibraryViewModel(
    private val repository: LibraryRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState.asStateFlow()

    fun openLibraryArea(areaId: String) {
        mutableState.update { current ->
            current.copy(selectedAreaId = areaId, messageKey = null)
        }
    }
}
