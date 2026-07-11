package com.cslearningos.mobile.feature.backup.ui

import com.cslearningos.mobile.ui.LearningUiState

fun LearningUiState.resetTransientStateAfterRestore(): LearningUiState =
    copy(
        selectedNode = null,
        selectedQuiz = null,
        selectedLibraryAreaId = null,
        editorNodeId = null,
        editorAreaId = null,
        editorSourceCaptureSlipId = null,
        editorTitle = "",
        editorBody = "",
        searchQuery = "",
        searchResults = emptyList(),
        quizPrompt = "",
        quizAnswer = "",
        quizExplanation = "",
        readerQuestionDraft = "",
        readerQuestionPanelExpanded = false,
        captureDraft = "",
        captureTopicHint = "",
        captureSourceLabel = "",
        pendingAiDraftSlipId = null,
        quizAnswerVisible = false
    )
