package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.feature.assistant.domain.AssistantRequestMode
import com.cslearningos.mobile.feature.assistant.domain.AssistantAreaOption
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationAction
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationCitation
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationMessage
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationRole
import com.cslearningos.mobile.feature.assistant.domain.AssistantEditProposal
import com.cslearningos.mobile.feature.assistant.domain.AssistantEditTarget
import com.cslearningos.mobile.feature.assistant.domain.AssistantAgentInteraction
import com.cslearningos.mobile.feature.assistant.domain.AssistantReviewSession
import com.cslearningos.mobile.feature.assistant.domain.nextTarget
import com.cslearningos.mobile.feature.assistant.domain.parseAssistantObjectProposal
import com.cslearningos.mobile.feature.assistant.domain.parseAssistantDraftPlacement
import com.cslearningos.mobile.data.CaptureSlipType

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
    data class AgentInteraction(val interaction: AssistantAgentInteraction) : AssistantMessageAction

    data class OpenEditableDraft(
        val titleHint: String,
        val markdown: String,
        val areaId: String? = null,
        val nodeId: String? = null,
        val expectedRevision: Long? = null,
        val placementReason: String? = null
    ) : AssistantMessageAction

    data class OpenEditableQuizDraft(
        val quizId: String,
        val expectedRevision: Long,
        val nodeId: String?,
        val prompt: String,
        val answer: String,
        val explanation: String
    ) : AssistantMessageAction

    data class OpenNewQuizDraft(
        val prompt: String,
        val answer: String,
        val explanation: String
    ) : AssistantMessageAction

    data class OpenEditableCaptureDraft(
        val slipId: String,
        val expectedRevision: Long,
        val body: String,
        val topicHint: String,
        val sourceLabel: String,
        val type: CaptureSlipType
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

data class AssistantConversationSummary(
    val id: String,
    val title: String,
    val preview: String
)

fun AssistantConversationMessage.toUiMessage(): AssistantMessage =
    AssistantMessage(
        id = "history-${java.util.UUID.randomUUID()}",
        role = when (role) {
            AssistantConversationRole.User -> AssistantMessageRole.User
            AssistantConversationRole.Assistant -> AssistantMessageRole.Assistant
        },
        body = body,
        citations = citations.map { citation ->
            AssistantCitation(
                id = citation.id,
                type = citation.type,
                title = citation.title,
                excerpt = citation.excerpt
            )
        },
        action = action?.toUiAction()
    )

fun AssistantMessage.toStoredMessage(): AssistantConversationMessage =
    AssistantConversationMessage(
        role = when (role) {
            AssistantMessageRole.User -> AssistantConversationRole.User
            AssistantMessageRole.Assistant -> AssistantConversationRole.Assistant
        },
        body = body,
        citations = citations.map { citation ->
            AssistantConversationCitation(
                id = citation.id,
                type = citation.type,
                title = citation.title,
                excerpt = citation.excerpt
            )
        },
        action = action?.toStoredAction()
    )

private fun AssistantConversationAction.toUiAction(): AssistantMessageAction? = when (this) {
    is AssistantConversationAction.AgentInteraction -> AssistantMessageAction.AgentInteraction(interaction)

    is AssistantConversationAction.OpenEditableNodeDraft -> AssistantMessageAction.OpenEditableDraft(
        titleHint = titleHint,
        markdown = markdown,
        areaId = areaId,
        nodeId = nodeId,
        expectedRevision = expectedRevision,
        placementReason = placementReason
    )

    is AssistantConversationAction.OpenEditableQuizDraft -> AssistantMessageAction.OpenEditableQuizDraft(
        quizId = quizId,
        expectedRevision = expectedRevision,
        nodeId = nodeId,
        prompt = prompt,
        answer = answer,
        explanation = explanation
    )

    is AssistantConversationAction.OpenNewQuizDraft -> AssistantMessageAction.OpenNewQuizDraft(
        prompt = prompt,
        answer = answer,
        explanation = explanation
    )

    is AssistantConversationAction.OpenEditableCaptureDraft -> CaptureSlipType.entries
        .firstOrNull { it.name == typeName }
        ?.let { type ->
            AssistantMessageAction.OpenEditableCaptureDraft(
                slipId = slipId,
                expectedRevision = expectedRevision,
                body = body,
                topicHint = topicHint,
                sourceLabel = sourceLabel,
                type = type
            )
        }

    is AssistantConversationAction.SaveCapture -> AssistantMessageAction.SaveCapture(body)
    is AssistantConversationAction.RetryRequest -> AssistantMessageAction.RetryRequest(prompt)
    AssistantConversationAction.OpenDailyReview -> AssistantMessageAction.OpenDailyReview
    AssistantConversationAction.ConfigureAi -> AssistantMessageAction.ConfigureAi
}

private fun AssistantMessageAction.toStoredAction(): AssistantConversationAction? = when (this) {
    is AssistantMessageAction.AgentInteraction -> AssistantConversationAction.AgentInteraction(interaction)

    is AssistantMessageAction.OpenEditableDraft -> AssistantConversationAction.OpenEditableNodeDraft(
        nodeId = nodeId,
        expectedRevision = expectedRevision ?: 0L,
        titleHint = titleHint,
        markdown = markdown,
        areaId = areaId,
        placementReason = placementReason
    )

    is AssistantMessageAction.OpenEditableQuizDraft -> AssistantConversationAction.OpenEditableQuizDraft(
        quizId = quizId,
        expectedRevision = expectedRevision,
        nodeId = nodeId,
        prompt = prompt,
        answer = answer,
        explanation = explanation
    )

    is AssistantMessageAction.OpenNewQuizDraft -> AssistantConversationAction.OpenNewQuizDraft(
        prompt = prompt,
        answer = answer,
        explanation = explanation
    )

    is AssistantMessageAction.OpenEditableCaptureDraft -> AssistantConversationAction.OpenEditableCaptureDraft(
        slipId = slipId,
        expectedRevision = expectedRevision,
        body = body,
        topicHint = topicHint,
        sourceLabel = sourceLabel,
        typeName = type.name
    )

    is AssistantMessageAction.SaveCapture -> AssistantConversationAction.SaveCapture(body)
    is AssistantMessageAction.RetryRequest -> AssistantConversationAction.RetryRequest(prompt)
    AssistantMessageAction.OpenDailyReview -> AssistantConversationAction.OpenDailyReview
    AssistantMessageAction.ConfigureAi -> AssistantConversationAction.ConfigureAi
}

fun assistantEditAction(proposal: AssistantEditProposal): AssistantMessageAction = when (proposal) {
    is AssistantEditProposal.Node -> AssistantMessageAction.OpenEditableDraft(
        titleHint = proposal.titleHint,
        markdown = proposal.markdown,
        areaId = proposal.areaId,
        nodeId = proposal.target.id,
        expectedRevision = proposal.target.revision,
        placementReason = proposal.placementReason
    )

    is AssistantEditProposal.Quiz -> AssistantMessageAction.OpenEditableQuizDraft(
        quizId = proposal.target.id,
        expectedRevision = proposal.target.revision,
        nodeId = proposal.target.nodeId,
        prompt = proposal.prompt,
        answer = proposal.answer,
        explanation = proposal.explanation
    )

    is AssistantEditProposal.Capture -> AssistantMessageAction.OpenEditableCaptureDraft(
        slipId = proposal.target.id,
        expectedRevision = proposal.target.revision,
        body = proposal.body,
        topicHint = proposal.topicHint,
        sourceLabel = proposal.sourceLabel,
        type = proposal.type
    )
}

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
    val editTarget: AssistantEditTarget.Node? = null,
    val captureSuggestion: String? = null
)

fun assistantReplyDecision(
    mode: AssistantRequestMode,
    request: String,
    reply: String,
    areas: List<AssistantAreaOption>,
    editTarget: AssistantEditTarget.Node? = null
): AssistantReplyDecision =
    when (mode) {
        AssistantRequestMode.Answer -> {
            AssistantReplyDecision(
                visibleReply = reply,
                action = if (request.normalizedQuickPrompt() in GenericQuickPrompts) null else AssistantMessageAction.SaveCapture(reply)
            )
        }

        AssistantRequestMode.Draft -> {
            val target = editTarget ?: AssistantEditTarget.Node(
                id = null,
                revision = 0L,
                titleHint = request.take(MaximumDraftTitleHintCharacters),
                markdown = "",
                areaId = null
            )
            val proposal = parseAssistantObjectProposal(target, reply, areas) as? AssistantEditProposal.Node
            val placement = proposal?.let { parseAssistantDraftPlacement(reply, areas) }
            val nextTarget = proposal?.nextTarget() as? AssistantEditTarget.Node
            AssistantReplyDecision(
                visibleReply = proposal?.markdown ?: reply,
                action = proposal?.let(::assistantEditAction),
                editTarget = nextTarget,
                captureSuggestion = proposal?.captureSuggestion ?: placement?.captureSuggestion
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
    val editTarget: AssistantEditTarget? = null,
    val reviewSession: AssistantReviewSession? = null,
    val conversationHistory: List<AssistantConversationSummary> = emptyList(),
    val historyVisible: Boolean = false,
    val pendingDraftRequest: String? = null,
    val pendingAutoOpenMessageId: String? = null
)
