package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.LearningNodeEntity

enum class DashboardAction {
    Search,
    Capture,
    Create,
    Review
}

data class DashboardSummary(
    val primaryActions: List<DashboardAction>,
    val firstScreenActions: List<DashboardAction>,
    val nodeCount: Int,
    val dueReviewCount: Int,
    val openQuestionCount: Int,
    val captureSlipCount: Int,
    val recentNode: LearningNodeEntity?
)

fun buildDashboardSummary(state: LearningUiState): DashboardSummary =
    DashboardSummary(
        primaryActions = listOf(DashboardAction.Search, DashboardAction.Capture, DashboardAction.Create, DashboardAction.Review),
        firstScreenActions = listOf(DashboardAction.Search, DashboardAction.Capture, DashboardAction.Create, DashboardAction.Review),
        nodeCount = state.nodes.size,
        dueReviewCount = state.dueQuizzes.size,
        openQuestionCount = state.readerQuestions.size,
        captureSlipCount = state.captureSlips.size,
        recentNode = state.nodes.maxByOrNull { it.lastReadAt ?: it.updatedAt }
    )
