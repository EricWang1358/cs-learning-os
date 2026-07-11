package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.feature.assistant.domain.AssistantRequestMode
import com.cslearningos.mobile.feature.assistant.domain.AssistantAreaOption
import com.cslearningos.mobile.feature.assistant.domain.AssistantWorkingDraft
import com.cslearningos.mobile.feature.assistant.domain.AssistantReviewSession
import com.cslearningos.mobile.feature.assistant.domain.parseAssistantDraftPlacement

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
        val areaId: String? = null,
        val nodeId: String? = null,
        val placementReason: String? = null
    ) : AssistantMessageAction

    data class SaveCapture(
        val body: String
    ) : AssistantMessageAction

    data class RetryRequest(
        val prompt: String
    ) : AssistantMessageAction

    data object OpenDailyReview : AssistantMessageAction

    data object ConfigureAi : AssistantMessageAction
}

data class AssistantMessage(
    val id: String,
    val role: AssistantMessageRole,
    val body: String,
    val citations: List<AssistantCitation> = emptyList(),
    val action: AssistantMessageAction? = null,
    val captureSuggestion: String? = null,
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
    val message = messages.firstOrNull { it.id == messageId } ?: return null
    val action = (message.action as? AssistantMessageAction.SaveCapture)
        ?: message.captureSuggestion?.let(AssistantMessageAction::SaveCapture)
        ?: return null
    return CaptureSaveActionClaim(
        action = action,
        messages = messages.map { message ->
            if (message.id == messageId) {
                message.copy(
                    action = message.action.takeUnless { it is AssistantMessageAction.SaveCapture },
                    captureSuggestion = null
                )
            } else message
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
    assistantReplyDecision(mode, request, reply, areas).action

data class AssistantReplyDecision(
    val visibleReply: String,
    val action: AssistantMessageAction?,
    val workingDraft: AssistantWorkingDraft? = null,
    val captureSuggestion: String? = null
)

fun assistantReplyDecision(
    mode: AssistantRequestMode,
    request: String,
    reply: String,
    areas: List<AssistantAreaOption>,
    workingDraft: AssistantWorkingDraft? = null
): AssistantReplyDecision =
    when (mode) {
        AssistantRequestMode.Answer -> {
            AssistantReplyDecision(
                visibleReply = reply,
                action = if (request.normalizedQuickPrompt() in GenericQuickPrompts) null else AssistantMessageAction.SaveCapture(reply)
            )
        }

        AssistantRequestMode.Draft -> {
            val placement = parseAssistantDraftPlacement(reply, areas)
            val updatesKnownDraft = workingDraft != null || placement.areaId != null
            val markdown = if (updatesKnownDraft) {
                placement.markdown.ifBlank { workingDraft?.markdown.orEmpty() }
            } else {
                ""
            }
            val nextDraft = markdown.takeIf(String::isNotBlank)?.let {
                AssistantWorkingDraft(
                    titleHint = workingDraft?.titleHint ?: request.take(MaximumDraftTitleHintCharacters),
                    markdown = it,
                    areaId = if (workingDraft?.nodeId != null) {
                        workingDraft.areaId
                    } else {
                        placement.areaId ?: workingDraft?.areaId
                    },
                    nodeId = workingDraft?.nodeId,
                    placementReason = placement.placementReason ?: workingDraft?.placementReason
                )
            }
            AssistantReplyDecision(
                visibleReply = placement.markdown,
                action = nextDraft?.let { draft ->
                    AssistantMessageAction.OpenEditableDraft(
                        titleHint = draft.titleHint,
                        markdown = draft.markdown,
                        areaId = draft.areaId,
                        nodeId = draft.nodeId,
                        placementReason = draft.placementReason
                    )
                },
                workingDraft = nextDraft,
                captureSuggestion = placement.captureSuggestion
            )
        }

        AssistantRequestMode.ReviewQuestion,
        AssistantRequestMode.ReviewEvaluation -> AssistantReplyDecision(
            visibleReply = reply,
            action = null
        )
    }

data class AssistantDraftPlacement(
    val markdown: String,
    val areaId: String?,
    val captureSuggestion: String?
)

private fun assistantDraftPlacement(
    reply: String,
    areas: List<AssistantAreaOption>
): AssistantDraftPlacement {
    val match = AssistantAreaDirective.find(reply)
    val requestedAreaId = match?.groupValues?.get(1)?.trim()
    val areaId = areas.firstOrNull { it.id == requestedAreaId }?.id
    val captureSuggestion = AssistantCaptureDirective
        .findAll(reply)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n\n")
        .takeIf { it.isNotBlank() }
    return AssistantDraftPlacement(
        markdown = reply.replace(AssistantAreaDirective, "").replace(AssistantCaptureDirective, "").trim(),
        areaId = areaId,
        captureSuggestion = captureSuggestion
    )
}

private fun String.normalizedQuickPrompt(): String = trim().trimEnd(':', '：')

private val AssistantAreaDirective = Regex("^\\s*<!--\\s*cs-area:\\s*([^>]+?)\\s*-->\\s*", RegexOption.MULTILINE)
private val AssistantCaptureDirective = Regex("^\\s*<!--\\s*cs-capture:\\s*([^>]+?)\\s*-->\\s*", RegexOption.MULTILINE)
private val GenericQuickPrompts = setOf("解释一个概念", "Explain a concept")
private const val MaximumDraftTitleHintCharacters = 72

data class AssistantUiState(
    val input: String = "",
    val messages: List<AssistantMessage> = emptyList(),
    val isBusy: Boolean = false,
    val lastRequestMode: AssistantRequestMode = AssistantRequestMode.Answer,
    val workingDraft: AssistantWorkingDraft? = null,
    val reviewSession: AssistantReviewSession? = null
)
