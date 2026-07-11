package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DashboardScreenState(
    val summary: DashboardSummary
)

@Composable
fun DashboardScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val screenState = state.toDashboardScreenState()
    DashboardGrid(summary = screenState.summary, viewModel = viewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardGrid(summary: DashboardSummary, viewModel: LearningViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ContinueReadingCard(summary = summary, viewModel = viewModel, modifier = Modifier.weight(0.5f))
        DashboardActionCard(action = DashboardAction.Capture, summary = summary, onClick = viewModel::showCapture, modifier = Modifier.weight(0.5f))
        DashboardActionCard(action = DashboardAction.Create, summary = summary, onClick = viewModel::startNewNode, modifier = Modifier.weight(0.5f))
        DashboardActionCard(action = DashboardAction.Search, summary = summary, onClick = viewModel::showSearch, modifier = Modifier.weight(0.5f))
    }
}

@Composable
private fun DashboardActionCard(action: DashboardAction, summary: DashboardSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    WorkbenchCard(accent = action == DashboardAction.Capture) {
        Eyebrow(action.eyebrow())
        Text(action.firstScreenLabel(summary), color = WorkbenchColors.InkStrong, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            action.body(),
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        WorkbenchButton(action.firstScreenLabel(summary), onClick, primary = action == DashboardAction.Capture)
    }
}

@Composable
private fun ContinueReadingCard(summary: DashboardSummary, viewModel: LearningViewModel, modifier: Modifier = Modifier) {
    val node = summary.recentNode
    if (node == null) {
        EmptyWorkbenchCard(
            title = stringResource(R.string.dashboard_continue_empty_title),
            body = stringResource(R.string.dashboard_continue_empty_body)
        )
        return
    }

    WorkbenchCard(accent = true, modifier = modifier) {
        Eyebrow(stringResource(R.string.dashboard_continue_eyebrow))
        Text(node.title, color = WorkbenchColors.InkStrong, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            dashboardPreviewMarkdown(node.markdownBody, stringResource(R.string.library_no_body_yet)),
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        WorkbenchButton(stringResource(R.string.dashboard_continue_button), { viewModel.openNode(node) }, primary = true)
    }
}

private fun dashboardPreviewMarkdown(markdown: String, fallback: String): String =
    markdown
        .lineSequence()
        .map { it.trim().trimStart('#', '-', '>', ' ') }
        .firstOrNull { it.isNotBlank() && !it.startsWith(":::") }
        ?: fallback

@Composable
private fun DashboardAction.firstScreenLabel(summary: DashboardSummary): String =
    when (this) {
        DashboardAction.Search -> stringResource(R.string.dashboard_action_search)
        DashboardAction.Capture -> stringResource(R.string.dashboard_action_capture)
        DashboardAction.Create -> stringResource(R.string.dashboard_action_create)
        DashboardAction.Review -> stringResource(R.string.dashboard_action_review, summary.dueReviewCount)
    }

private fun DashboardAction.clickHandler(viewModel: LearningViewModel): () -> Unit =
    when (this) {
        DashboardAction.Search -> viewModel::showSearch
        DashboardAction.Capture -> viewModel::showCapture
        DashboardAction.Create -> viewModel::startNewNode
        DashboardAction.Review -> viewModel::showReview
    }

@Composable
private fun DashboardAction.eyebrow(): String =
    when (this) {
        DashboardAction.Search -> stringResource(R.string.dashboard_action_search_eyebrow)
        DashboardAction.Capture -> stringResource(R.string.dashboard_action_capture_eyebrow)
        DashboardAction.Create -> stringResource(R.string.dashboard_action_create_eyebrow)
        DashboardAction.Review -> stringResource(R.string.dashboard_action_review_eyebrow)
    }

@Composable
private fun DashboardAction.body(): String =
    when (this) {
        DashboardAction.Search -> stringResource(R.string.dashboard_action_search_body)
        DashboardAction.Capture -> stringResource(R.string.dashboard_action_capture_body)
        DashboardAction.Create -> stringResource(R.string.dashboard_action_create_body)
        DashboardAction.Review -> stringResource(R.string.dashboard_action_review_body)
    }

private fun DashboardAction.metric(summary: DashboardSummary): String? =
    when (this) {
        DashboardAction.Capture -> summary.captureSlipCount.takeIf { it > 0 }?.toString()
        DashboardAction.Review -> summary.dueReviewCount.toString()
        else -> null
    }

private fun LearningUiState.toDashboardScreenState(): DashboardScreenState =
    DashboardScreenState(
        summary = buildDashboardSummary(this)
    )
