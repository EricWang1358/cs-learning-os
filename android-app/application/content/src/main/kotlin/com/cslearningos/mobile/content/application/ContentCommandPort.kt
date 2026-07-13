package com.cslearningos.mobile.content.application

import com.cslearningos.mobile.content.domain.ContentNode

fun interface ContentCommandPort {
    suspend fun saveNode(command: SaveNodeCommand): ContentCommandResult
}

sealed interface ContentCommandResult {
    data class Success(val node: ContentNode) : ContentCommandResult
    data class Failure(val failure: ContentCommandFailure) : ContentCommandResult
}

sealed interface ContentCommandFailure {
    data class Validation(val code: String) : ContentCommandFailure
    data class Missing(val target: String) : ContentCommandFailure
    data object Deleted : ContentCommandFailure
    data class StaleRevision(val expected: Long, val actual: Long) : ContentCommandFailure
    data object CommandReuseConflict : ContentCommandFailure
    data class Storage(val code: String) : ContentCommandFailure
}
