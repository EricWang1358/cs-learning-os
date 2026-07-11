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
    val dueReviewCount: Int,
    val nodeCount: Int,
    val captureSlipCount: Int,
    val recentNode: LearningNodeEntity?
)

fun buildDashboardSummary(state: LearningUiState): DashboardSummary =
    DashboardSummary(
        primaryActions = listOf(DashboardAction.Capture, DashboardAction.Create, DashboardAction.Review, DashboardAction.Search),
        firstScreenActions = listOf(DashboardAction.Capture, DashboardAction.Create, DashboardAction.Review, DashboardAction.Search),
        dueReviewCount = state.dueQuizzes.size,
        nodeCount = state.nodes.count { it.deletedAt == null && it.visibility != "trash" },
        captureSlipCount = state.captureSlips.size,
        recentNode = state.nodes.maxByOrNull { it.lastReadAt ?: it.updatedAt }
    )
