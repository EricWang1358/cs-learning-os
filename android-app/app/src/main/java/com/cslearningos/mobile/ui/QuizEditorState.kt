package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.QuizItemEntity

internal fun LearningUiState.forNewQuizEditor(): LearningUiState = copy(
    screen = AppScreen.QuizEditor,
    quizEditorId = null,
    quizExpectedRevision = null,
    quizPrompt = "",
    quizAnswer = "",
    quizExplanation = "",
    quizAreaId = null,
    message = null
)

internal fun LearningUiState.forNewQuizDraftEditor(
    prompt: String,
    answer: String,
    explanation: String
): LearningUiState = copy(
    screen = AppScreen.QuizEditor,
    selectedNode = null,
    selectedQuiz = null,
    quizEditorId = null,
    quizExpectedRevision = null,
    quizPrompt = prompt,
    quizAnswer = answer,
    quizExplanation = explanation,
    quizAreaId = null,
    message = uiText(com.cslearningos.mobile.R.string.message_assistant_draft_ready)
)

internal fun LearningUiState.forExistingQuizEditor(quiz: QuizItemEntity): LearningUiState = copy(
    screen = AppScreen.QuizEditor,
    selectedQuiz = quiz,
    quizEditorId = quiz.id,
    quizExpectedRevision = quiz.revision,
    quizPrompt = quiz.prompt,
    quizAnswer = quiz.answer,
    quizExplanation = quiz.explanation,
    quizAreaId = null
)
