package com.cslearningos.mobile.core.kernel

@JvmInline
value class CommandId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class EntityRevision(val value: Long) {
    init {
        require(value >= 0L)
    }
}

sealed interface DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>
    data class Failure(val error: DomainFailure) : DomainResult<Nothing>
}

sealed interface DomainFailure {
    data class Validation(val code: String) : DomainFailure
    data class Conflict(val code: String) : DomainFailure
    data class Missing(val code: String) : DomainFailure
}
