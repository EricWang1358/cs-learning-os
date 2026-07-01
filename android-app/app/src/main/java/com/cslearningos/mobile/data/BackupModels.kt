package com.cslearningos.mobile.data

data class LearningBackup(
    val schemaVersion: Int,
    val exportedAt: Long,
    val nodes: List<LearningNodeEntity>,
    val quizzes: List<QuizItemEntity>,
    val reviewStates: List<ReviewStateEntity>,
    val attempts: List<ReviewAttemptEntity>
)
