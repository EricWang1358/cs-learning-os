package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.feature.assistant.domain.AssistantRequestMode

enum class AssistantMessageRole {
    User,
    Assistant
}

data class AssistantCitation(
    val id: String,
    val type: String,
    val title: String,
    val excerpt: String
)

sealed interface AssistantMessageAction {
    data class OpenEditableDraft(
        val titleHint: String,
        val markdown: String
    ) : AssistantMessageAction

    data class SaveCapture(
        val body: String
    ) : AssistantMessageAction

    data object ConfigureAi : AssistantMessageAction
}

data class AssistantMessage(
    val id: String,
    val role: AssistantMessageRole,
    val body: String,
    val citations: List<AssistantCitation> = emptyList(),
    val action: AssistantMessageAction? = null,
    val isStreaming: Boolean = false
)

data class CaptureSaveActionClaim(
    val action: AssistantMessageAction.SaveCapture,
    val messages: List<AssistantMessage>
)

fun claimCaptureSaveAction(
    messages: List<AssistantMessage>,
    messageId: String
): CaptureSaveActionClaim? {
    val action = messages
        .firstOrNull { it.id == messageId }
        ?.action as? AssistantMessageAction.SaveCapture
        ?: return null
    return CaptureSaveActionClaim(
        action = action,
        messages = messages.map { message ->
            if (message.id == messageId) message.copy(action = null) else message
        }
    )
}

data class AssistantUiState(
    val input: String = "",
    val messages: List<AssistantMessage> = emptyList(),
    val isBusy: Boolean = false,
    val lastRequestMode: AssistantRequestMode = AssistantRequestMode.Answer
)
