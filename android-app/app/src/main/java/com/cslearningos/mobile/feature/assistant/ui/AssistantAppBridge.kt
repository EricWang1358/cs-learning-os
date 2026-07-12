package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.R
import com.cslearningos.mobile.domain.MarkdownQuizParser
import com.cslearningos.mobile.ui.AppScreen
import com.cslearningos.mobile.ui.LearningUiState
import com.cslearningos.mobile.ui.assistantMarkdownDraft
import com.cslearningos.mobile.ui.titleFromAiMarkdown
import com.cslearningos.mobile.ui.uiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AssistantAppBridge(
    private val coordinator: AssistantCoordinator,
    private val currentSettings: () -> com.cslearningos.mobile.ui.AiProviderSettings,
    private val updateState: ((LearningUiState) -> LearningUiState) -> Unit,
    private val scope: CoroutineScope,
    private val onOpenNode: (com.cslearningos.mobile.data.LearningNodeEntity) -> Unit,
    private val onOpenDailyReview: () -> Unit,
    private val onShowAssistant: () -> Unit,
    private val onShowAssistantPreservingConversation: () -> Unit
) {
    fun newChat() = coordinator.newChat()

    fun showHistory() = coordinator.showHistory()

    fun hideHistory() = coordinator.hideHistory()

    fun openHistoryConversation(id: String) = coordinator.openHistoryConversation(id)

    fun deleteHistoryConversation(id: String) = coordinator.deleteHistoryConversation(id)

    fun setInput(value: String) = coordinator.setInput(value)

    fun prefillQuickPrompt(value: String) = coordinator.prefillQuickPrompt(value)

    fun reviseNode(node: com.cslearningos.mobile.data.LearningNodeEntity) {
        onShowAssistant()
        coordinator.reviseNode(node)
    }

    fun reviseQuiz(quiz: com.cslearningos.mobile.data.QuizItemEntity) {
        onShowAssistant()
        coordinator.reviseQuiz(quiz)
    }

    fun reviseCapture(slip: com.cslearningos.mobile.data.CaptureSlipEntity) {
        onShowAssistant()
        coordinator.reviseCapture(slip)
    }

    fun reviseNodeDraft(
        nodeId: String?,
        expectedRevision: Long?,
        titleHint: String,
        markdown: String,
        areaId: String?
    ) {
        onShowAssistantPreservingConversation()
        coordinator.reviseNodeDraft(
            nodeId = nodeId,
            expectedRevision = expectedRevision,
            titleHint = titleHint,
            markdown = markdown,
            areaId = areaId
        )
    }

    fun startInterviewReview() = coordinator.startInterviewReview()

    fun sendMessage() = coordinator.send(currentSettings())

    fun replyToAgentAction(reply: String) = coordinator.sendAgentActionReply(reply, currentSettings())

    fun retryMessage(messageId: String) = coordinator.retry(messageId, currentSettings())

    fun cancelReply() = coordinator.cancelReply()

    fun openDailyReview() = onOpenDailyReview()

    fun consumePendingAutoOpen(messageId: String) = coordinator.consumePendingAutoOpen(messageId)

    fun openDraft(messageId: String) {
        scope.launch {
            val action = coordinator.draftAction(messageId) ?: return@launch
            val nodeId = action.nodeId
            if (nodeId != null) {
                val node = (coordinator.resolveDestination("node", nodeId) as? AssistantDestination.Node)?.node
                if (node == null || node.deletedAt != null || action.expectedRevision != null && node.revision != action.expectedRevision) {
                    updateState { it.copy(message = uiText(R.string.message_assistant_source_unavailable)) }
                    return@launch
                }
            }
            val draft = assistantMarkdownDraft(action.markdown, action.titleHint)
            updateState {
                it.copy(
                    screen = AppScreen.Editor,
                    selectedNode = null,
                    editorNodeId = action.nodeId,
                    editorExpectedRevision = action.expectedRevision,
                    editorAreaId = action.areaId,
                    editorSourceCaptureSlipId = null,
                    editorTitle = draft.title,
                    editorBody = draft.body,
                    message = uiText(assistantDraftReadyMessageResId(action.markdown, action.areaId))
                )
            }
        }
    }

    fun openQuizDraft(messageId: String) {
        scope.launch {
            val action = coordinator.quizDraftAction(messageId) ?: return@launch
            val quiz = (coordinator.resolveDestination("quiz", action.quizId) as? AssistantDestination.Quiz)?.quiz
            if (quiz == null || quiz.deletedAt != null || quiz.revision != action.expectedRevision) {
                updateState { it.copy(message = uiText(R.string.message_assistant_source_unavailable)) }
                return@launch
            }
            updateState {
                it.copy(
                    screen = AppScreen.QuizEditor,
                    selectedNode = null,
                    selectedQuiz = quiz,
                    quizEditorId = action.quizId,
                    quizExpectedRevision = action.expectedRevision,
                    quizPrompt = action.prompt,
                    quizAnswer = action.answer,
                    quizExplanation = action.explanation,
                    message = uiText(R.string.message_assistant_draft_ready)
                )
            }
        }
    }

    fun openCaptureDraft(messageId: String) {
        scope.launch {
            val action = coordinator.captureDraftAction(messageId) ?: return@launch
            val slip = (coordinator.resolveDestination("capture", action.slipId) as? AssistantDestination.Capture)?.slip
            if (slip == null || slip.deletedAt != null || slip.revision != action.expectedRevision) {
                updateState { it.copy(message = uiText(R.string.message_assistant_source_unavailable)) }
                return@launch
            }
            updateState {
                it.copy(
                    screen = AppScreen.Capture,
                    captureEditorId = slip.id,
                    captureExpectedRevision = action.expectedRevision,
                    captureDraft = action.body,
                    captureTopicHint = action.topicHint,
                    captureSourceLabel = action.sourceLabel,
                    captureType = action.type,
                    message = uiText(R.string.message_assistant_draft_ready)
                )
            }
        }
    }

    fun saveReplyToCapture(messageId: String) {
        scope.launch {
            if (coordinator.saveReplyToCapture(messageId)) {
                updateState { it.copy(message = uiText(R.string.message_assistant_capture_saved)) }
            } else {
                updateState { it.copy(message = uiText(R.string.message_assistant_capture_save_failed)) }
            }
        }
    }

    fun openCitation(type: String, id: String) {
        scope.launch {
            when (val destination = coordinator.resolveDestination(type, id)) {
                is AssistantDestination.Node -> onOpenNode(destination.node)
                is AssistantDestination.Quiz -> updateState {
                    it.copy(
                        screen = AppScreen.Review,
                        selectedQuiz = destination.quiz,
                        quizAnswerVisible = false,
                        message = null
                    )
                }

                is AssistantDestination.Capture -> updateState {
                    it.copy(
                        screen = AppScreen.Capture,
                        captureEditorId = destination.slip.id,
                        captureExpectedRevision = destination.slip.revision,
                        captureDraft = destination.slip.body,
                        captureTopicHint = destination.slip.topicHint.orEmpty(),
                        captureSourceLabel = destination.slip.sourceLabel.orEmpty(),
                        captureType = destination.slip.type,
                        message = null
                    )
                }

                null -> updateState { it.copy(message = uiText(R.string.message_assistant_source_unavailable)) }
            }
        }
    }
}

internal fun assistantDraftReadyMessageResId(markdown: String, areaId: String?): Int =
    if (areaId == null && MarkdownQuizParser.parse(markdown).isNotEmpty()) {
        R.string.message_assistant_draft_choose_area_for_review
    } else {
        R.string.message_assistant_draft_ready
    }
