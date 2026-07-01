package com.cslearningos.mobile.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cslearningos.mobile.data.LearningNodeEntity

@Composable
fun DashboardScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val summary = buildDashboardSummary(state)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FirstScreenActionStrip(summary = summary, viewModel = viewModel)
        DashboardHero(state = state, viewModel = viewModel)
        TodayStack(summary = summary)
        ContinueReadingCard(summary = summary, viewModel = viewModel)
        LibraryPreview(state = state, viewModel = viewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstScreenActionStrip(summary: DashboardSummary, viewModel: LearningViewModel) {
    WorkbenchCard(accent = true) {
        Eyebrow("start here")
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
private fun DashboardHero(state: LearningUiState, viewModel: LearningViewModel) {
    WorkbenchCard {
        Eyebrow("learning command center")
        Text(
            text = "What are we learning next?",
            color = WorkbenchColors.InkStrong,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 32.sp
        )
        Text(
            text = "Search local knowledge, capture Markdown, or jump into due review from one offline command center.",
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        WorkbenchTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            label = "Search concepts, notes, quiz cards...",
            minLines = 1
        )
        WorkbenchButton(
            text = "Search now",
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
        Eyebrow("today")
        Text("Learning state", color = WorkbenchColors.InkStrong, fontSize = 21.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardMetric("Nodes", summary.nodeCount.toString(), Modifier.weight(1f))
            DashboardMetric("Due", summary.dueReviewCount.toString(), Modifier.weight(1f))
            DashboardMetric("Slips", summary.captureSlipCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ContinueReadingCard(summary: DashboardSummary, viewModel: LearningViewModel) {
    val node = summary.recentNode
    if (node == null) {
        EmptyWorkbenchCard(
            title = "No reading history yet",
            body = "Create or open a node. Your next visit will surface the most recent learning thread here."
        )
        return
    }

    WorkbenchCard(accent = true) {
        Eyebrow("continue")
        Text(node.title, color = WorkbenchColors.InkStrong, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text(
            dashboardPreviewMarkdown(node.markdownBody),
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        WorkbenchButton("Continue reading", { viewModel.openNode(node) }, primary = true)
    }
}

@Composable
private fun LibraryPreview(state: LearningUiState, viewModel: LearningViewModel) {
    WorkbenchCard {
        Eyebrow("library preview")
        Text("Your local knowledge base", color = WorkbenchColors.InkStrong, fontSize = 21.sp, fontWeight = FontWeight.Black)
        if (state.nodes.isEmpty()) {
            Text(
                "No nodes yet. Create the first Markdown node from the action card above.",
                color = WorkbenchColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.nodes.take(3).forEach { node ->
                    DashboardNodePreview(node = node, onOpen = { viewModel.openNode(node) })
                }
            }
        }
        WorkbenchButton("Browse full library", viewModel::showLibrary)
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
        Eyebrow("node")
        Text(node.title, color = WorkbenchColors.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(
            dashboardPreviewMarkdown(node.markdownBody),
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun dashboardPreviewMarkdown(markdown: String): String =
    markdown
        .lineSequence()
        .map { it.trim().trimStart('#', '-', '>', ' ') }
        .firstOrNull { it.isNotBlank() && !it.startsWith(":::") }
        ?: "No body yet. Add Markdown to make this node useful."

private fun DashboardAction.firstScreenLabel(summary: DashboardSummary): String =
    when (this) {
        DashboardAction.Search -> "Search"
        DashboardAction.Capture -> "Capture"
        DashboardAction.Create -> "Create"
        DashboardAction.Review -> "Review ${summary.dueReviewCount}"
    }

private fun DashboardAction.clickHandler(viewModel: LearningViewModel): () -> Unit =
    when (this) {
        DashboardAction.Search -> viewModel::showSearch
        DashboardAction.Capture -> viewModel::showCapture
        DashboardAction.Create -> viewModel::startNewNode
        DashboardAction.Review -> viewModel::showReview
    }

private fun DashboardAction.eyebrow(): String =
    when (this) {
        DashboardAction.Search -> "find"
        DashboardAction.Capture -> "slip"
        DashboardAction.Create -> "node"
        DashboardAction.Review -> "due"
    }

private fun DashboardAction.body(): String =
    when (this) {
        DashboardAction.Search -> "Search notes and quiz cards."
        DashboardAction.Capture -> "Catch unclear points fast."
        DashboardAction.Create -> "Write structured Markdown."
        DashboardAction.Review -> "Reveal, rate, repeat."
    }

private fun DashboardAction.metric(summary: DashboardSummary): String? =
    when (this) {
        DashboardAction.Capture -> summary.captureSlipCount.takeIf { it > 0 }?.toString()
        DashboardAction.Review -> summary.dueReviewCount.toString()
        else -> null
    }
