package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.R
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.feature.assistant.data.KnowledgeAssistantService
import com.cslearningos.mobile.feature.assistant.domain.AssistantRequestMode
import com.cslearningos.mobile.feature.assistant.domain.KnowledgeAssistantSession
import com.cslearningos.mobile.feature.assistant.domain.assistantRequestModeFor
import com.cslearningos.mobile.feature.settings.data.safeAiError
import com.cslearningos.mobile.ui.AiProviderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AssistantDestination {
    data class Node(val node: LearningNodeEntity) : AssistantDestination
    data class Quiz(val quiz: QuizItemEntity) : AssistantDestination
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

    val state: StateFlow<AssistantUiState> = mutableState.asStateFlow()

    fun newChat() {
        activeReplyMessageId = null
        replyJob?.cancel()
        mutableState.value = AssistantUiState()
    }

    fun setInput(value: String) {
        mutableState.update { it.copy(input = value) }
    }

    fun sendQuickMessage(value: String, settings: AiProviderSettings) {
        mutableState.update { it.copy(input = value) }
        send(settings)
    }

    fun send(settings: AiProviderSettings): Boolean {
        val snapshot = mutableState.value
        val input = snapshot.input.trim()
        if (input.isBlank() || snapshot.isBusy) return false

        val mode = assistantRequestModeFor(input)
        val userMessage = AssistantMessage(
            id = messageId("user"),
            role = AssistantMessageRole.User,
            body = input
        )
        val responseMessageId = messageId("assistant")
        activeReplyMessageId = responseMessageId
        mutableState.update { current ->
            current.copy(
                input = "",
                messages = current.messages + userMessage + AssistantMessage(
                    id = responseMessageId,
                    role = AssistantMessageRole.Assistant,
                    body = "",
                    isStreaming = true
                ),
                isBusy = true,
                lastRequestMode = mode
            )
        }
        replyJob = scope.launch {
            try {
                val localContext = session.findLocalContext(input)
                updateMessage(responseMessageId) { it.copy(citations = localContext) }
                if (!settings.isConfigured) {
                    updateMessage(responseMessageId) {
                        it.copy(
                            body = string(R.string.assistant_ai_needed),
                            action = AssistantMessageAction.ConfigureAi,
                            isStreaming = false
                        )
                    }
                    return@launch
                }
                session.streamReply(
                    settings = settings,
                    mode = mode,
                    history = snapshot.messages + userMessage,
                    message = input,
                    context = localContext,
                    onDelta = { delta -> updateMessage(responseMessageId) { message -> message.copy(body = message.body + delta) } }
                )
                updateMessage(responseMessageId) { message ->
                    val body = message.body.trim()
                    message.copy(
                        body = body.ifBlank { string(R.string.assistant_local_empty) },
                        action = body.takeIf(String::isNotBlank)?.let { answer ->
                            when (mode) {
                                AssistantRequestMode.Draft -> AssistantMessageAction.OpenEditableDraft(
                                    titleHint = input.take(MaximumTitleHintCharacters),
                                    markdown = answer
                                )
                                AssistantRequestMode.Answer -> AssistantMessageAction.SaveCapture(answer)
                            }
                        },
                        isStreaming = false
                    )
                }
            } catch (error: CancellationException) {
                updateMessage(responseMessageId) { message ->
                    message.copy(
                        body = message.body.ifBlank { string(R.string.assistant_stopped) },
                        isStreaming = false
                    )
                }
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
            } finally {
                if (activeReplyMessageId == responseMessageId) {
                    activeReplyMessageId = null
                    mutableState.update { it.copy(isBusy = false) }
                }
            }
        }
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

    private companion object {
        const val MaximumTitleHintCharacters = 72
    }
}
