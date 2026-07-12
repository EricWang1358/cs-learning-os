package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.feature.assistant.domain.AssistantAgentInteraction
import com.cslearningos.mobile.feature.assistant.domain.AssistantEditTarget
import com.cslearningos.mobile.feature.assistant.domain.AssistantRequestMode
import com.cslearningos.mobile.feature.assistant.domain.AssistantReviewSession
import com.cslearningos.mobile.feature.assistant.domain.assistantRequestModeFor

internal fun AssistantUiState.requestModeFor(input: String): AssistantRequestMode =
    when (reviewSession) {
        AssistantReviewSession.AwaitingTopic -> AssistantRequestMode.ReviewQuestion
        is AssistantReviewSession.AwaitingAnswer -> AssistantRequestMode.ReviewEvaluation
        is AssistantReviewSession.Evaluated -> AssistantRequestMode.Answer
        null -> if (editTarget != null) {
            AssistantRequestMode.Draft
        } else if (pendingDraftRequest != null) {
            AssistantRequestMode.Draft
        } else {
            assistantRequestModeFor(input)
        }
    }

internal fun AssistantUiState.queueAssistantTurn(
    input: String,
    mode: AssistantRequestMode,
    userMessageId: String,
    responseMessageId: String
): AssistantUiState =
    copy(
        input = "",
        messages = messages +
            AssistantMessage(
                id = userMessageId,
                role = AssistantMessageRole.User,
                body = input
            ) +
            AssistantMessage(
                id = responseMessageId,
                role = AssistantMessageRole.Assistant,
                body = "",
                isStreaming = true
            ),
        isBusy = true,
        lastRequestMode = mode
    )

internal fun AssistantUiState.shouldShowDraftChecklist(
    input: String,
    mode: AssistantRequestMode
): Boolean =
    mode == AssistantRequestMode.Draft &&
        editTarget == null &&
        !input.isApprovedDraftRequest() &&
        pendingDraftRequest == null

internal fun AssistantUiState.showDraftChecklist(
    responseMessageId: String,
    request: String,
    interaction: AssistantAgentInteraction.SelectContext,
    body: String
): AssistantUiState =
    copy(
        messages = messages.map { message ->
            if (message.id == responseMessageId) {
                message.copy(
                    body = body,
                    action = AssistantMessageAction.AgentInteraction(interaction),
                    isStreaming = false
                )
            } else {
                message
            }
        },
        isBusy = false,
        pendingDraftRequest = request
    )

internal fun AssistantUiState.completeAssistantTurn(
    responseMessageId: String,
    visibleBody: String,
    action: AssistantMessageAction?,
    captureSuggestion: String?,
    nextEditTarget: AssistantEditTarget?,
    nextReviewSession: AssistantReviewSession?
): AssistantUiState =
    copy(
        messages = messages.map { message ->
            if (message.id == responseMessageId) {
                message.copy(
                    body = visibleBody,
                    action = action,
                    captureSuggestion = captureSuggestion,
                    isStreaming = false
                )
            } else {
                message
            }
        },
        editTarget = nextEditTarget,
        reviewSession = nextReviewSession,
        pendingAutoOpenMessageId = action.autoOpenMessageIdOrNull(responseMessageId)
    )

internal fun AssistantUiState.restoreConversation(
    messages: List<AssistantMessage>,
    editTarget: AssistantEditTarget?
): AssistantUiState =
    AssistantUiState(
        messages = messages,
        editTarget = editTarget
    )

internal fun AssistantUiState.consumePendingAutoOpenMessage(messageId: String): AssistantUiState =
    if (pendingAutoOpenMessageId == messageId) {
        copy(pendingAutoOpenMessageId = null)
    } else {
        this
    }

private fun String.isApprovedDraftRequest(): Boolean =
    trim().startsWith(ApprovedDraftPrefix, ignoreCase = true)

internal fun AssistantMessageAction?.autoOpenMessageIdOrNull(messageId: String): String? =
    when (this) {
        is AssistantMessageAction.OpenEditableDraft,
        is AssistantMessageAction.OpenEditableQuizDraft,
        is AssistantMessageAction.OpenEditableCaptureDraft -> messageId
        else -> null
    }

internal const val ApprovedDraftPrefix = "Generate the editable draft for:"
