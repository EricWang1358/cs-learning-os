package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.QuizItemEntity

internal fun LearningUiState.forExistingCaptureEditor(slip: CaptureSlipEntity): LearningUiState = copy(
    screen = AppScreen.Capture,
    captureEditorId = slip.id,
    captureDraft = slip.body,
    captureTopicHint = slip.topicHint.orEmpty(),
    captureSourceLabel = slip.sourceLabel.orEmpty(),
    captureType = slip.type,
    pendingAiDraftSlipId = null,
    message = null
)

internal fun LearningUiState.clearedCaptureEditor(): LearningUiState = copy(
    captureEditorId = null,
    captureDraft = "",
    captureTopicHint = "",
    captureSourceLabel = ""
)

internal fun LearningUiState.forExistingQuizEditorWithoutSelectedNode(quiz: QuizItemEntity): LearningUiState =
    forExistingQuizEditor(quiz).copy(selectedNode = null)

internal fun LearningUiState.quizNodeIdForSave(): String? =
    if (quizEditorId != null) selectedQuiz?.nodeId else selectedNode?.id ?: selectedQuiz?.nodeId
