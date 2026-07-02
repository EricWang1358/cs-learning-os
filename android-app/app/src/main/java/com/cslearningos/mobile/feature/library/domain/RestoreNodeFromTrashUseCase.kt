package com.cslearningos.mobile.feature.library.domain

import com.cslearningos.mobile.feature.library.data.LibraryRepository

class RestoreNodeFromTrashUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(nodeId: String, now: Long = System.currentTimeMillis()) {
        repository.restoreNodeFromTrash(nodeId = nodeId, now = now)
    }
}
