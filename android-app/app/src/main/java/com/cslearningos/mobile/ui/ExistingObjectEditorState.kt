package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity

internal fun LearningUiState.forExistingCaptureEditor(slip: CaptureSlipEntity): LearningUiState = copy(
    screen = AppScreen.Capture,
    captureEditorId = slip.id,
    captureExpectedRevision = slip.revision,
    captureDraft = slip.body,
    captureTopicHint = slip.topicHint.orEmpty(),
    captureSourceLabel = slip.sourceLabel.orEmpty(),
    captureType = slip.type,
    pendingAiDraftSlipId = null,
    message = null
)

internal fun LearningUiState.clearedCaptureEditor(): LearningUiState = copy(
    captureEditorId = null,
    captureExpectedRevision = null,
    captureDraft = "",
    captureTopicHint = "",
    captureSourceLabel = ""
)

internal fun LearningUiState.afterNodeSaved(node: LearningNodeEntity): LearningUiState = copy(
    screen = AppScreen.Reader,
    selectedNode = node,
    editorAreaId = null,
    editorExpectedRevision = null,
    editorSourceCaptureSlipId = null,
    message = uiText(R.string.message_node_saved)
)

internal fun LearningUiState.afterCaptureSaved(): LearningUiState =
    clearedCaptureEditor().copy(message = uiText(R.string.message_capture_saved_to_inbox))

internal fun LearningUiState.afterQuizSaved(quiz: QuizItemEntity): LearningUiState = copy(
    screen = if (quiz.nodeId != null && selectedNode != null) AppScreen.Reader else AppScreen.Review,
    selectedQuiz = quiz,
    reviewedQuiz = null,
    reviewAreaId = quiz.area,
    reviewSetupVisible = quiz.nodeId != null && selectedNode != null,
    quizAnswerVisible = false,
    quizEditorId = null,
    quizExpectedRevision = null,
    quizAreaId = null,
    message = uiText(R.string.message_quiz_saved)
)

internal fun LearningUiState.withObjectSaveRejected(): LearningUiState =
    copy(message = uiText(R.string.message_assistant_source_unavailable))

suspend fun LearningRepository.saveNodeFromEditor(state: LearningUiState): LearningNodeEntity =
    saveNode(
        id = state.editorNodeId,
        expectedRevision = state.editorExpectedRevision,
        title = state.editorTitle,
        markdownBody = state.editorBody,
        areaId = state.editorAreaId
    )

suspend fun LearningRepository.saveCaptureFromEditor(state: LearningUiState): CaptureSlipEntity =
    saveCaptureSlip(
        id = state.captureEditorId,
        expectedRevision = state.captureExpectedRevision,
        body = state.captureDraft,
        type = state.captureType,
        topicHint = state.captureTopicHint,
        sourceLabel = state.captureSourceLabel
    )

suspend fun LearningRepository.saveQuizFromEditor(state: LearningUiState): QuizItemEntity =
    saveManualQuiz(
        id = state.quizEditorId,
        expectedRevision = state.quizExpectedRevision,
        nodeId = state.quizNodeIdForSave(),
        areaId = state.quizAreaIdForSave(),
        prompt = state.quizPrompt,
        answer = state.quizAnswer,
        explanation = state.quizExplanation
    )

internal fun LearningUiState.forExistingQuizEditorWithoutSelectedNode(quiz: QuizItemEntity): LearningUiState =
    forExistingQuizEditor(quiz).copy(selectedNode = null)

internal fun LearningUiState.quizNodeIdForSave(): String? =
    if (quizEditorId != null) selectedQuiz?.nodeId else selectedNode?.id ?: selectedQuiz?.nodeId

internal fun LearningUiState.quizAreaIdForSave(): String? =
    if (quizNodeIdForSave() == null) quizAreaId else null
