package com.cslearningos.mobile.feature.library.ui

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.ui.LibraryCheckedFilter

data class LibraryUiState(
    val areas: List<AreaEntity> = emptyList(),
    val nodes: List<LearningNodeEntity> = emptyList(),
    val selectedAreaId: String? = null,
    val checkedFilter: LibraryCheckedFilter = LibraryCheckedFilter.All,
    val messageKey: String? = null
)
