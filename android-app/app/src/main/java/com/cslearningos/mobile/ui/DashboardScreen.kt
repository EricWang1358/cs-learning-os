package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cslearningos.mobile.data.LearningNodeEntity

private data class DashboardScreenState(
    val summary: DashboardSummary,
    val searchQuery: String,
    val previewNodes: List<LearningNodeEntity>
)

@Composable
fun DashboardScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val screenState = state.toDashboardScreenState()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FirstScreenActionStrip(summary = screenState.summary, viewModel = viewModel)
        DashboardHero(state = screenState, viewModel = viewModel)
        TodayStack(summary = screenState.summary)
        ContinueReadingCard(summary = screenState.summary, viewModel = viewModel)
        LibraryPreview(state = screenState, viewModel = viewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstScreenActionStrip(summary: DashboardSummary, viewModel: LearningViewModel) {
    WorkbenchCard(accent = true) {
        Eyebrow(stringResource(R.string.dashboard_start_here_eyebrow))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            summary.firstScreenActions.forEach { action ->
                WorkbenchActionTile(
                    eyebrow = action.eyebrow(),
                    title = action.firstScreenLabel(summary),
                    body = action.body(),
                    metric = action.metric(summary),
                    onClick = action.clickHandler(viewModel),
                    accent = action == DashboardAction.Capture || action == DashboardAction.Review,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DashboardHero(state: DashboardScreenState, viewModel: LearningViewModel) {
    WorkbenchCard {
        Eyebrow(stringResource(R.string.dashboard_hero_eyebrow))
        Text(
            text = stringResource(R.string.dashboard_hero_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 32.sp
        )
        Text(
            text = stringResource(R.string.dashboard_hero_body),
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        WorkbenchTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            label = stringResource(R.string.dashboard_search_field),
            minLines = 1
        )
        WorkbenchButton(
            text = stringResource(R.string.dashboard_search_now),
            onClick = {
                viewModel.showSearch()
                viewModel.runSearch()
            },
            primary = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TodayStack(summary: DashboardSummary) {
    WorkbenchCard {
        Eyebrow(stringResource(R.string.dashboard_today_eyebrow))
        Text(stringResource(R.string.dashboard_today_title), color = WorkbenchColors.InkStrong, fontSize = 21.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardMetric(stringResource(R.string.common_nodes), summary.nodeCount.toString(), Modifier.weight(1f))
            DashboardMetric(stringResource(R.string.common_due), summary.dueReviewCount.toString(), Modifier.weight(1f))
            DashboardMetric(stringResource(R.string.common_slips), summary.captureSlipCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ContinueReadingCard(summary: DashboardSummary, viewModel: LearningViewModel) {
    val node = summary.recentNode
    if (node == null) {
        EmptyWorkbenchCard(
            title = stringResource(R.string.dashboard_continue_empty_title),
            body = stringResource(R.string.dashboard_continue_empty_body)
        )
        return
    }

    WorkbenchCard(accent = true) {
        Eyebrow(stringResource(R.string.dashboard_continue_eyebrow))
        Text(node.title, color = WorkbenchColors.InkStrong, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text(
            dashboardPreviewMarkdown(node.markdownBody, stringResource(R.string.library_no_body_yet)),
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        WorkbenchButton(stringResource(R.string.dashboard_continue_button), { viewModel.openNode(node) }, primary = true)
    }
}

@Composable
private fun LibraryPreview(state: DashboardScreenState, viewModel: LearningViewModel) {
    WorkbenchCard {
        Eyebrow(stringResource(R.string.dashboard_library_preview_eyebrow))
        Text(stringResource(R.string.dashboard_library_preview_title), color = WorkbenchColors.InkStrong, fontSize = 21.sp, fontWeight = FontWeight.Black)
        if (state.previewNodes.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_library_preview_empty_body),
                color = WorkbenchColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.previewNodes.forEach { node ->
                    DashboardNodePreview(node = node, onOpen = { viewModel.openNode(node) })
                }
            }
        }
        WorkbenchButton(stringResource(R.string.dashboard_browse_library), viewModel::showLibrary)
    }
}

@Composable
private fun DashboardMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(WorkbenchColors.Surface.copy(alpha = 0.62f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(label.uppercase(), color = WorkbenchColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(value, color = WorkbenchColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DashboardNodePreview(node: LearningNodeEntity, onOpen: () -> Unit) {
    InteractiveCard(onClick = onOpen, accent = false) {
        Eyebrow(stringResource(R.string.library_node_eyebrow))
        Text(node.title, color = WorkbenchColors.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(
            dashboardPreviewMarkdown(node.markdownBody, stringResource(R.string.library_no_body_yet)),
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
        summary = buildDashboardSummary(this),
        searchQuery = searchQuery,
        previewNodes = nodes.take(3)
    )
