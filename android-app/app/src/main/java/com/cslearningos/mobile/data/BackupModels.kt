package com.cslearningos.mobile.data

data class LearningBackup(
    val schemaVersion: Int,
    val exportedAt: Long,
    val areas: List<AreaEntity> = emptyList(),
    val nodes: List<LearningNodeEntity>,
    val quizzes: List<QuizItemEntity>,
    val reviewStates: List<ReviewStateEntity>,
    val attempts: List<ReviewAttemptEntity>,
    val readerQuestions: List<ReaderQuestionEntity> = emptyList(),
    val captureSlips: List<CaptureSlipEntity> = emptyList()
)
