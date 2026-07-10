package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.R
import com.cslearningos.mobile.ui.AppScreen
import com.cslearningos.mobile.ui.LearningUiState
import com.cslearningos.mobile.ui.titleFromAiMarkdown
import com.cslearningos.mobile.ui.uiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AssistantAppBridge(
    private val coordinator: AssistantCoordinator,
    private val currentSettings: () -> com.cslearningos.mobile.ui.AiProviderSettings,
    private val updateState: ((LearningUiState) -> LearningUiState) -> Unit,
    private val scope: CoroutineScope,
    private val onOpenNode: (com.cslearningos.mobile.data.LearningNodeEntity) -> Unit
) {
    fun newChat() = coordinator.newChat()

    fun setInput(value: String) = coordinator.setInput(value)

    fun prefillQuickPrompt(value: String) = coordinator.prefillQuickPrompt(value)

    fun sendMessage() = coordinator.send(currentSettings())

    fun retryMessage(messageId: String) = coordinator.retry(messageId, currentSettings())

    fun cancelReply() = coordinator.cancelReply()

    fun openDraft(messageId: String) {
        val action = coordinator.draftAction(messageId) ?: return
        updateState {
            it.copy(
                screen = AppScreen.Editor,
                selectedNode = null,
                editorNodeId = null,
                editorAreaId = action.areaId,
                editorSourceCaptureSlipId = null,
                editorTitle = titleFromAiMarkdown(action.markdown, action.titleHint),
                editorBody = action.markdown,
                message = uiText(R.string.message_assistant_draft_ready)
            )
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

                null -> updateState { it.copy(message = uiText(R.string.message_assistant_source_unavailable)) }
            }
        }
    }
}
