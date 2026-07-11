package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.QuizItemEntity

internal fun LearningUiState.forNewQuizEditor(): LearningUiState = copy(
    screen = AppScreen.QuizEditor,
    quizEditorId = null,
    quizExpectedRevision = null,
    quizPrompt = "",
    quizAnswer = "",
    quizExplanation = "",
    message = null
)

internal fun LearningUiState.forExistingQuizEditor(quiz: QuizItemEntity): LearningUiState = copy(
    screen = AppScreen.QuizEditor,
    selectedQuiz = quiz,
    quizEditorId = quiz.id,
    quizExpectedRevision = quiz.revision,
    quizPrompt = quiz.prompt,
    quizAnswer = quiz.answer,
    quizExplanation = quiz.explanation
)
