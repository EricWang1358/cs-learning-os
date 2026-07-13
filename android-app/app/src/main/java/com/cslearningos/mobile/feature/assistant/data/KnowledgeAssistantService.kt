package com.cslearningos.mobile.feature.assistant.data

import com.cslearningos.mobile.assistant.domain.AssistantRunId
import com.cslearningos.mobile.assistant.domain.ModelEvent
import com.cslearningos.mobile.assistant.domain.ModelFailure
import com.cslearningos.mobile.assistant.domain.ModelMessage
import com.cslearningos.mobile.assistant.domain.ModelRequest
import com.cslearningos.mobile.assistant.domain.ModelRole
import com.cslearningos.mobile.assistant.openai.OpenAiModelGateway
import com.cslearningos.mobile.core.common.AndroidArchitectureConstants
import com.cslearningos.mobile.feature.assistant.domain.KnowledgeAssistantChatMessage
import java.util.UUID
import kotlinx.coroutines.flow.collect

interface KnowledgeAssistantService {
    suspend fun streamReply(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<KnowledgeAssistantChatMessage>,
        onDelta: suspend (String) -> Unit
    )
}

class OpenAiCompatibleKnowledgeAssistantService : KnowledgeAssistantService {
    override suspend fun streamReply(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<KnowledgeAssistantChatMessage>,
        onDelta: suspend (String) -> Unit
    ) {
        val runId = AssistantRunId(UUID.randomUUID().toString())
        val request = ModelRequest(
            runId = runId,
            messages = listOf(ModelMessage(ModelRole.System, systemPrompt)) +
                messages.mapNotNull(KnowledgeAssistantChatMessage::toModelMessage)
        )
        OpenAiModelGateway(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            connectTimeoutMillis = AndroidArchitectureConstants.AiConnectTimeoutMillis,
            readTimeoutMillis = AndroidArchitectureConstants.AiReadTimeoutMillis
        ).stream(request).collect { event ->
            when (event) {
                is ModelEvent.Token -> onDelta(event.value)
                is ModelEvent.Completed -> Unit
                is ModelEvent.Failed -> throw IllegalStateException(event.failure.safeMessage())
            }
        }
    }
}

private fun KnowledgeAssistantChatMessage.toModelMessage(): ModelMessage? {
    if (content.isBlank()) return null
    val modelRole = when (role.lowercase()) {
        ModelRole.System.wireValue -> ModelRole.System
        ModelRole.User.wireValue -> ModelRole.User
        else -> ModelRole.Assistant
    }
    return ModelMessage(modelRole, content)
}

private fun ModelFailure.safeMessage(): String = when (this) {
    is ModelFailure.Authentication -> "Model authentication failed (HTTP $statusCode)."
    is ModelFailure.RateLimited -> "The model provider is rate limited."
    is ModelFailure.Http -> "HTTP $statusCode: $safeMessage"
    is ModelFailure.Protocol -> safeMessage
    is ModelFailure.Transport -> safeMessage
}
