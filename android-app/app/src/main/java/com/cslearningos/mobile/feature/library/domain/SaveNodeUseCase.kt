package com.cslearningos.mobile.feature.library.domain

import com.cslearningos.mobile.feature.library.data.LibraryRepository

class SaveNodeUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(
        id: String?,
        title: String,
        markdownBody: String,
        areaId: String? = null,
        now: Long = System.currentTimeMillis()
    ) = repository.saveNode(
        id = id,
        title = title,
        markdownBody = markdownBody,
        areaId = areaId,
        now = now
    )
}
