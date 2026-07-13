package com.cslearningos.mobile.assistant.domain

import kotlinx.coroutines.flow.Flow

@JvmInline
value class AssistantRunId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

enum class ModelRole(val wireValue: String) {
    System("system"),
    User("user"),
    Assistant("assistant")
}

data class ModelMessage(
    val role: ModelRole,
    val content: String
)

data class ModelRequest(
    val runId: AssistantRunId,
    val messages: List<ModelMessage>
) {
    init {
        require(messages.isNotEmpty())
    }
}

data class ModelCapabilities(
    val streaming: Boolean,
    val structuredOutput: Boolean,
    val toolCalls: Boolean,
    val contextWindowTokens: Int?
)

sealed interface ModelFailure {
    data class Authentication(val statusCode: Int) : ModelFailure
    data class RateLimited(val retryAfterSeconds: Long?) : ModelFailure
    data class Http(val statusCode: Int, val safeMessage: String) : ModelFailure
    data class Protocol(val safeMessage: String) : ModelFailure
    data class Transport(val safeMessage: String) : ModelFailure
}

sealed interface ModelEvent {
    data class Token(val runId: AssistantRunId, val value: String) : ModelEvent
    data class Completed(val runId: AssistantRunId) : ModelEvent
    data class Failed(val runId: AssistantRunId, val failure: ModelFailure) : ModelEvent
}

interface ModelGateway {
    suspend fun capabilities(): ModelCapabilities
    fun stream(request: ModelRequest): Flow<ModelEvent>
}
