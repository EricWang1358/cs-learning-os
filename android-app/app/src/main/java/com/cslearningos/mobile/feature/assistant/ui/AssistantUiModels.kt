package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.feature.assistant.domain.AssistantRequestMode
import com.cslearningos.mobile.feature.assistant.domain.AssistantAreaOption

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
        val markdown: String,
        val areaId: String? = null
    ) : AssistantMessageAction

    data class SaveCapture(
        val body: String
    ) : AssistantMessageAction

    data class RetryRequest(
        val prompt: String
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

fun retryAssistantRequest(messages: List<AssistantMessage>, messageId: String): String? =
    (messages.firstOrNull { it.id == messageId }?.action as? AssistantMessageAction.RetryRequest)
        ?.prompt

fun assistantReplyAction(
    mode: AssistantRequestMode,
    request: String,
    reply: String,
    areas: List<AssistantAreaOption>
): AssistantMessageAction? =
    when (mode) {
        AssistantRequestMode.Answer -> {
            if (request.trim() in GenericQuickPrompts) null else AssistantMessageAction.SaveCapture(reply)
        }

        AssistantRequestMode.Draft -> {
            val placement = assistantDraftPlacement(reply, areas)
            AssistantMessageAction.OpenEditableDraft(
                titleHint = request.take(MaximumDraftTitleHintCharacters),
                markdown = placement.markdown,
                areaId = placement.areaId
            )
        }
    }

data class AssistantDraftPlacement(
    val markdown: String,
    val areaId: String?
)

private fun assistantDraftPlacement(
    reply: String,
    areas: List<AssistantAreaOption>
): AssistantDraftPlacement {
    val match = AssistantAreaDirective.find(reply)
    val requestedAreaId = match?.groupValues?.get(1)?.trim()
    val areaId = areas.firstOrNull { it.id == requestedAreaId }?.id
    return AssistantDraftPlacement(
        markdown = reply.replaceFirst(AssistantAreaDirective, "").trim(),
        areaId = areaId
    )
}

private val AssistantAreaDirective = Regex("^\\s*<!--\\s*cs-area:\\s*([^>]+?)\\s*-->\\s*")
private val GenericQuickPrompts = setOf("解释一个概念", "Explain a concept")
private const val MaximumDraftTitleHintCharacters = 72

data class AssistantUiState(
    val input: String = "",
    val messages: List<AssistantMessage> = emptyList(),
    val isBusy: Boolean = false,
    val lastRequestMode: AssistantRequestMode = AssistantRequestMode.Answer
)
