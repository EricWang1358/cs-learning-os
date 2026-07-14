package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.R
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.feature.assistant.data.KnowledgeAssistantService
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversation
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationMessage
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationRole
import com.cslearningos.mobile.feature.assistant.domain.AssistantAgentInteraction
import com.cslearningos.mobile.feature.assistant.domain.AssistantEditProposal
import com.cslearningos.mobile.feature.assistant.domain.AssistantEditTarget
import com.cslearningos.mobile.feature.assistant.domain.AssistantRequestMode
import com.cslearningos.mobile.feature.assistant.domain.AssistantReviewSession
import com.cslearningos.mobile.feature.assistant.domain.KnowledgeAssistantSession
import com.cslearningos.mobile.feature.assistant.domain.assistantRequestModeFor
import com.cslearningos.mobile.feature.assistant.domain.nextTarget
import com.cslearningos.mobile.feature.assistant.domain.parseAssistantObjectProposal
import com.cslearningos.mobile.feature.assistant.domain.parseAssistantAgentInteraction
import com.cslearningos.mobile.feature.assistant.domain.parseAssistantReviewEvaluation
import com.cslearningos.mobile.feature.assistant.domain.SelectContextItem
import com.cslearningos.mobile.feature.settings.data.safeAiError
import com.cslearningos.mobile.ui.AiProviderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

sealed interface AssistantDestination {
    data class Node(val node: LearningNodeEntity) : AssistantDestination
    data class Quiz(val quiz: QuizItemEntity) : AssistantDestination
    data class Capture(val slip: com.cslearningos.mobile.data.CaptureSlipEntity) : AssistantDestination
}

class AssistantCoordinator(
    private val repository: LearningRepository,
    service: KnowledgeAssistantService,
    private val string: (Int) -> String,
    private val scope: CoroutineScope
) {
    private val session = KnowledgeAssistantSession(repository, service)
    private val mutableState = MutableStateFlow(AssistantUiState())
    private val captureSaveMutex = Mutex()
    private var replyJob: Job? = null
    private var activeReplyMessageId: String? = null
    private var conversationId: String = newConversationId()

    val state: StateFlow<AssistantUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            val conversation = repository.latestAssistantConversation() ?: return@launch
            if (mutableState.value.messages.isEmpty()) {
                conversationId = conversation.id
                mutableState.value = AssistantUiState(
                    messages = conversation.messages.map(AssistantConversationMessage::toUiMessage),
                    editTarget = conversation.editTarget
                )
            }
        }
    }

    fun newChat() {
        activeReplyMessageId = null
        replyJob?.cancel()
        conversationId = newConversationId()
        mutableState.value = AssistantUiState()
    }

    fun showHistory() {
        if (mutableState.value.isBusy) return
        scope.launch {
            val history = repository.recentAssistantConversations(HistoryLimit).map { it.toSummary() }
            if (mutableState.value.isBusy) return@launch
            mutableState.update { it.copy(conversationHistory = history, historyVisible = true) }
        }
    }

    fun hideHistory() {
        mutableState.update { it.copy(historyVisible = false) }
    }

    fun openHistoryConversation(id: String) {
        if (mutableState.value.isBusy) return
        scope.launch {
            val conversation = repository.getAssistantConversation(id) ?: return@launch
            if (mutableState.value.isBusy) return@launch
            conversationId = conversation.id
            mutableState.value = AssistantUiState(
                messages = conversation.messages.map(AssistantConversationMessage::toUiMessage),
                editTarget = conversation.editTarget
            )
        }
    }

    fun deleteHistoryConversation(id: String) {
        scope.launch {
            repository.deleteAssistantConversation(id)
            if (conversationId == id) {
                conversationId = newConversationId()
                mutableState.value = AssistantUiState()
            } else {
                val history = repository.recentAssistantConversations(HistoryLimit).map { it.toSummary() }
                mutableState.update { it.copy(conversationHistory = history, historyVisible = true) }
            }
        }
    }

    fun setInput(value: String) {
        mutableState.update { it.copy(input = value) }
    }

    fun prefillQuickPrompt(value: String) = setInput(value)

    fun reviseNode(node: LearningNodeEntity) {
        mutableState.update {
            it.copy(
                input = "Improve this knowledge node while preserving its useful content.",
                editTarget = AssistantEditTarget.Node(
                    id = node.id,
                    revision = node.revision,
                    titleHint = node.title,
                    markdown = node.markdownBody,
                    areaId = node.areaId.ifBlank { node.area }
                ),
                reviewSession = null
            )
        }
    }

    fun reviseQuiz(quiz: QuizItemEntity) {
        mutableState.update {
            it.copy(
                input = "Improve this review question while preserving what is already useful.",
                editTarget = AssistantEditTarget.Quiz(
                    id = quiz.id,
                    revision = quiz.revision,
                    nodeId = quiz.nodeId,
                    prompt = quiz.prompt,
                    answer = quiz.answer,
                    explanation = quiz.explanation
                ),
                reviewSession = null
            )
        }
    }

    fun reviseCapture(slip: com.cslearningos.mobile.data.CaptureSlipEntity) {
        mutableState.update {
            it.copy(
                input = "Improve this capture slip.",
                editTarget = AssistantEditTarget.Capture(
                    id = slip.id,
                    revision = slip.revision,
                    body = slip.body,
                    topicHint = slip.topicHint.orEmpty(),
                    sourceLabel = slip.sourceLabel.orEmpty(),
                    type = slip.type
                ),
                reviewSession = null
            )
        }
    }

    fun reviseNodeDraft(
        nodeId: String?,
        expectedRevision: Long?,
        titleHint: String,
        markdown: String,
        areaId: String?
    ) {
        mutableState.update {
            it.copy(
                input = "Improve this knowledge node while preserving its useful content.",
                editTarget = AssistantEditTarget.Node(
                    id = nodeId,
                    revision = expectedRevision ?: 0L,
                    titleHint = titleHint,
                    markdown = markdown,
                    areaId = areaId
                ),
                reviewSession = null
            )
        }
    }

    fun startInterviewReview() {
        if (mutableState.value.isBusy) return
        val requestConversationId = conversationId
        val messageId = messageId("assistant")
        mutableState.update { current ->
            current.copy(
                messages = current.messages + AssistantMessage(
                    id = messageId,
                    role = AssistantMessageRole.Assistant,
                    body = string(R.string.assistant_review_topic_prompt)
                ),
                editTarget = null,
                reviewSession = AssistantReviewSession.AwaitingTopic
            )
        }
        scope.launch {
            val hints = session.reviewTopicHints()
            if (hints.isNotEmpty()) {
                val topicPrompt = string(R.string.assistant_review_topic_prompt) +
                    "\n\n${string(R.string.assistant_review_materials)} ${hints.joinToString(" / ")}".trimEnd()
                updateMessage(messageId) {
                    it.copy(body = topicPrompt)
                }
            }
            persistConversation(requestConversationId)
        }
    }

    fun send(
        settings: AiProviderSettings,
        forcePendingDraftReply: Boolean = false
    ): Boolean {
        val snapshot = mutableState.value
        val input = snapshot.input.trim()
        if (input.isBlank() || snapshot.isBusy) return false

        val mode = snapshot.requestModeFor(input, forcePendingDraftReply)
        val userMessageId = messageId("user")
        val responseMessageId = messageId("assistant")
        val requestConversationId = conversationId
        activeReplyMessageId = responseMessageId
        mutableState.update { current ->
            current.queueAssistantTurn(
                input = input,
                mode = mode,
                userMessageId = userMessageId,
                responseMessageId = responseMessageId
            )
        }
        if (snapshot.shouldShowDraftChecklist(input, mode)) {
            mutableState.update {
                it.showDraftChecklist(
                    responseMessageId = responseMessageId,
                    request = input,
                    interaction = input.toDraftChecklistInteraction(string),
                    body = string(R.string.assistant_draft_confirm_body)
                )
            }
            scope.launch { persistConversation(requestConversationId) }
            return true
        }
        replyJob = scope.launch {
            try {
                val userMessage = mutableState.value.messages.firstOrNull { it.id == userMessageId }
                    ?: AssistantMessage(id = userMessageId, role = AssistantMessageRole.User, body = input)
                val localContext = session.findLocalContext(input)
                val areas = session.availableAreas()
                val requestEditTarget = snapshot.editTarget ?: if (mode == AssistantRequestMode.Draft) {
                    AssistantEditTarget.Node(
                        id = null,
                        revision = 0L,
                        titleHint = input.take(MaximumNodeDraftTitleHintCharacters),
                        markdown = "",
                        areaId = null
                    )
                } else {
                    null
                }
                updateMessage(responseMessageId) { it.copy(citations = localContext) }
                mutableState.update { it.copy(pendingDraftRequest = null) }
                if (!settings.isConfigured) {
                    updateMessage(responseMessageId) {
                        it.copy(
                            body = string(R.string.assistant_ai_needed),
                            action = AssistantMessageAction.ConfigureAi,
                            isStreaming = false
                        )
                    }
                    persistConversation(requestConversationId)
                    return@launch
                }
                val streamBuffer = StringBuilder()
                val hideStructuredStreaming = shouldHideStructuredStreaming(mode, requestEditTarget)
                var structuredPlaceholderShown = false
                var lastStreamingUiFlushAt = 0L
                session.streamReply(
                    settings = settings,
                    mode = mode,
                    history = snapshot.messages + userMessage,
                    message = input,
                    context = localContext,
                    areas = areas,
                    objectTarget = requestEditTarget,
                    onDelta = { delta ->
                        streamBuffer.append(delta)
                        if (hideStructuredStreaming) {
                            if (!structuredPlaceholderShown) {
                                structuredPlaceholderShown = true
                                updateMessage(responseMessageId) { message ->
                                    message.copy(body = string(R.string.assistant_streaming))
                                }
                            }
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastStreamingUiFlushAt >= StreamingUiFlushIntervalMillis) {
                                lastStreamingUiFlushAt = now
                                updateMessage(responseMessageId) { message ->
                                    message.copy(body = streamBuffer.toString())
                                }
                            }
                        }
                    }
                )
                if (!hideStructuredStreaming) {
                    updateMessage(responseMessageId) { message ->
                        message.copy(body = streamBuffer.toString())
                    }
                }
                val rawReply = streamBuffer.toString().trim()
                val agentInteraction = parseAssistantAgentInteraction(rawReply)
                val replyForDecision = agentInteraction.visibleReply.ifBlank { rawReply }
                var action: AssistantMessageAction? = null
                var captureSuggestion: String? = null
                var visibleBody = replyForDecision
                var nextEditTarget = snapshot.editTarget
                var nextReviewSession = snapshot.reviewSession
                when (mode) {
                    AssistantRequestMode.ReviewQuestion -> {
                        nextReviewSession = AssistantReviewSession.AwaitingAnswer(
                            topic = input,
                            question = replyForDecision
                        )
                    }

                    AssistantRequestMode.ReviewEvaluation -> {
                        val review = snapshot.reviewSession as? AssistantReviewSession.AwaitingAnswer
                        val evaluation = parseAssistantReviewEvaluation(replyForDecision)
                        action = review?.let { session ->
                            evaluation.dailyReviewAnswer?.let { answer ->
                                AssistantMessageAction.OpenNewQuizDraft(
                                    prompt = session.question,
                                    answer = answer,
                                    explanation = evaluation.feedback
                                )
                            }
                        }
                        visibleBody = evaluation.feedback
                        nextReviewSession = review?.let { session ->
                            AssistantReviewSession.Evaluated(
                                topic = session.topic,
                                question = session.question,
                                quizId = null
                            )
                        }
                    }

                    AssistantRequestMode.Answer,
                    AssistantRequestMode.Draft -> {
                        val objectProposal = requestEditTarget?.let { target ->
                            parseAssistantObjectProposal(target, replyForDecision, areas)
                        }
                        if (objectProposal != null) {
                            action = assistantEditAction(objectProposal)
                            captureSuggestion = (objectProposal as? AssistantEditProposal.Node)?.captureSuggestion
                            nextEditTarget = when (objectProposal) {
                                is AssistantEditProposal.Capture -> null
                                else -> objectProposal.nextTarget()
                            }
                            visibleBody = string(R.string.assistant_draft_updated)
                        } else if (snapshot.editTarget != null) {
                            action = null
                            visibleBody = replyForDecision
                        } else {
                            val decision = assistantReplyDecision(
                                mode = mode,
                                request = input,
                                reply = replyForDecision,
                                areas = areas
                            )
                            action = decision.action
                            captureSuggestion = decision.captureSuggestion
                            visibleBody = if (mode == AssistantRequestMode.Draft && decision.editTarget != null) {
                                string(R.string.assistant_draft_updated)
                            } else {
                                decision.visibleReply
                            }
                        }
                    }
                }
                agentInteraction.interaction?.takeIf { interaction ->
                    interaction !is AssistantAgentInteraction.MoveNodeArea || areas.any { it.id == interaction.targetAreaId }
                }?.let { interaction ->
                    action = AssistantMessageAction.AgentInteraction(interaction)
                    captureSuggestion = null
                    nextEditTarget = snapshot.editTarget
                }
                mutableState.update {
                    it.completeAssistantTurn(
                        responseMessageId = responseMessageId,
                        visibleBody = visibleBody.ifBlank { string(R.string.assistant_local_empty) },
                        action = action,
                        captureSuggestion = captureSuggestion,
                        nextEditTarget = nextEditTarget,
                        nextReviewSession = nextReviewSession
                    )
                }
                persistConversation(requestConversationId)
            } catch (error: CancellationException) {
                updateMessage(responseMessageId) { message ->
                    message.copy(
                        body = message.body.ifBlank { string(R.string.assistant_stopped) },
                        isStreaming = false
                    )
                }
                persistConversation(requestConversationId)
                throw error
            } catch (error: Throwable) {
                updateMessage(responseMessageId) { message ->
                    val partialReply = message.body.trim()
                    val failureMessage = error.safeAiError()
                    message.copy(
                        body = if (partialReply.isBlank()) failureMessage else "$partialReply\n\n${string(R.string.assistant_reply_interrupted)} $failureMessage",
                        action = AssistantMessageAction.RetryRequest(input),
                        isStreaming = false
                    )
                }
                persistConversation(requestConversationId)
            } finally {
                if (activeReplyMessageId == responseMessageId) {
                    activeReplyMessageId = null
                    mutableState.update { it.copy(isBusy = false) }
                }
            }
        }
        return true
    }

    fun sendAgentActionReply(reply: String, settings: AiProviderSettings): Boolean {
        val shouldForcePendingDraftReply = mutableState.value.pendingDraftRequest != null
        setInput(reply)
        return send(settings, forcePendingDraftReply = shouldForcePendingDraftReply)
    }

    suspend fun confirmAreaMove(interaction: AssistantAgentInteraction.MoveNodeArea): Boolean {
        val node = repository.getNode(interaction.nodeId)
            ?.takeIf { it.deletedAt == null && it.revision == interaction.expectedRevision }
            ?: return false
        val targetAreaExists = repository.areas.first().any { area ->
            area.id == interaction.targetAreaId && area.deletedAt == null
        }
        if (!targetAreaExists || node.areaId == interaction.targetAreaId) return false

        repository.moveNodeToArea(node.id, interaction.targetAreaId)
        mutableState.update { state ->
            state.copy(messages = state.messages.map { message ->
                if ((message.action as? AssistantMessageAction.AgentInteraction)?.interaction == interaction) {
                    message.copy(action = null)
                } else {
                    message
                }
            })
        }
        persistConversation(conversationId)
        return true
    }
    fun retry(messageId: String, settings: AiProviderSettings): Boolean {
        val prompt = retryAssistantRequest(mutableState.value.messages, messageId) ?: return false
        setInput(prompt)
        return send(settings)
    }

    fun cancelReply() {
        replyJob?.cancel()
    }

    fun draftAction(messageId: String): AssistantMessageAction.OpenEditableDraft? =
        mutableState.value.messages
            .firstOrNull { it.id == messageId }
            ?.action as? AssistantMessageAction.OpenEditableDraft

    fun quizDraftAction(messageId: String): AssistantMessageAction.OpenEditableQuizDraft? =
        mutableState.value.messages
            .firstOrNull { it.id == messageId }
            ?.action as? AssistantMessageAction.OpenEditableQuizDraft

    fun newQuizDraftAction(messageId: String): AssistantMessageAction.OpenNewQuizDraft? =
        mutableState.value.messages
            .firstOrNull { it.id == messageId }
            ?.action as? AssistantMessageAction.OpenNewQuizDraft

    fun captureDraftAction(messageId: String): AssistantMessageAction.OpenEditableCaptureDraft? =
        mutableState.value.messages
            .firstOrNull { it.id == messageId }
            ?.action as? AssistantMessageAction.OpenEditableCaptureDraft

    fun consumePendingAutoOpen(messageId: String) {
        mutableState.update { current -> current.consumePendingAutoOpenMessage(messageId) }
    }

    suspend fun saveReplyToCapture(messageId: String): Boolean = captureSaveMutex.withLock {
        val claim = claimCaptureSaveAction(mutableState.value.messages, messageId) ?: return false
        mutableState.update { it.copy(messages = claim.messages) }
        runCatching {
            repository.saveCaptureSlip(
                body = claim.action.body,
                type = CaptureSlipType.concept_seed,
                topicHint = "",
                sourceLabel = string(R.string.assistant_title)
            )
        }.onFailure {
            mutableState.update { current ->
                current.copy(
                    messages = current.messages.map { message ->
                        if (message.id == messageId && message.action == null) {
                            message.copy(action = claim.action)
                        } else {
                            message
                        }
                    }
                )
            }
        }.isSuccess
    }

    suspend fun resolveDestination(type: String, id: String): AssistantDestination? =
        when (type) {
            "node" -> repository.getNode(id)?.let(AssistantDestination::Node)
            "quiz" -> repository.getQuiz(id)?.let(AssistantDestination::Quiz)
            "capture" -> repository.getCaptureSlip(id)?.let(AssistantDestination::Capture)
            else -> null
        }

    private fun updateMessage(messageId: String, transform: (AssistantMessage) -> AssistantMessage) {
        mutableState.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id == messageId) transform(message) else message
                }
            )
        }
    }

    private fun messageId(role: String): String =
        "$role-${System.currentTimeMillis()}-${mutableState.value.messages.size}"

    private suspend fun persistConversation(
        id: String = conversationId,
        snapshot: AssistantUiState = mutableState.value
    ) {
        val messages = snapshot.messages
            .filter { !it.isStreaming && it.body.isNotBlank() }
            .map(AssistantMessage::toStoredMessage)
        if (messages.isNotEmpty()) {
            repository.saveAssistantConversation(
                AssistantConversation(
                    id = id,
                    messages = messages,
                    editTarget = snapshot.editTarget
                )
            )
        }
    }

    private fun newConversationId(): String = UUID.randomUUID().toString()

    private companion object {
        const val HistoryLimit = 30
        const val MaximumNodeDraftTitleHintCharacters = 72
        const val StreamingUiFlushIntervalMillis = 64L
    }
}

private fun shouldHideStructuredStreaming(
    mode: AssistantRequestMode,
    editTarget: AssistantEditTarget?
): Boolean =
    mode == AssistantRequestMode.Draft || editTarget != null

private fun String.isAssistantDraftApproval(): Boolean =
    trim().startsWith(ApprovedDraftPrefix, ignoreCase = true)

private fun String.toDraftChecklistInteraction(string: (Int) -> String): AssistantAgentInteraction.SelectContext {
    val request = trim()
    return AssistantAgentInteraction.SelectContext(
        title = string(R.string.assistant_draft_confirm_title),
        body = string(R.string.assistant_draft_confirm_body),
        items = listOf(
            SelectContextItem(
                id = "create_node",
                title = string(R.string.assistant_draft_task_node),
                body = string(R.string.assistant_draft_task_node_body),
                selected = true
            ),
            SelectContextItem(
                id = "create_review_cards",
                title = string(R.string.assistant_draft_task_review),
                body = string(R.string.assistant_draft_task_review_body),
                selected = true
            ),
            SelectContextItem(
                id = "keep_capture_followups",
                title = string(R.string.assistant_draft_task_capture),
                body = string(R.string.assistant_draft_task_capture_body),
                selected = false
            )
        ),
        confirmReplyPrefix = "$ApprovedDraftPrefix $request\nSelected outputs:"
    )
}

private fun AssistantConversation.toSummary(): AssistantConversationSummary {
    val firstUserMessage = messages.firstOrNull { it.role == AssistantConversationRole.User }?.body.orEmpty()
    val latestMessage = messages.lastOrNull()?.body.orEmpty()
    return AssistantConversationSummary(
        id = id,
        title = firstUserMessage.lineSequence().firstOrNull()?.trim()?.take(36).orEmpty().ifBlank { "New conversation" },
        preview = latestMessage.lineSequence().firstOrNull()?.trim()?.take(72).orEmpty()
    )
}
