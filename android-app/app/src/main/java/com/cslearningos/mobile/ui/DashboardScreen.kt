package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private object DashboardTokens {
    val SectionSpacing = 20.dp
    val SectionTitleSpacing = 10.dp
    val CardCornerRadius = 24.dp
    val TileCornerRadius = 20.dp
    val CardPadding = 18.dp
    val AssistantIconSize = 44.dp
    val TileIconSize = 40.dp
    val CardMinHeight = 116.dp
}

@Composable
fun DashboardScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val summary = buildDashboardSummary(state)
    Column(verticalArrangement = Arrangement.spacedBy(DashboardTokens.SectionSpacing)) {
        DashboardGreetingHeader(summary = summary)
        AssistantEntryCard(onClick = viewModel::showAssistant)
        ContinueLearningSection(summary = summary, areas = state.areas, viewModel = viewModel)
        QuickActionsSection(viewModel = viewModel)
    }
}

@Composable
private fun DashboardGreetingHeader(summary: DashboardSummary) {
    val context = LocalContext.current
    val greetingRes = when (LocalTime.now().hour) {
        in 5..11 -> R.string.dashboard_greeting_morning
        in 12..17 -> R.string.dashboard_greeting_afternoon
        else -> R.string.dashboard_greeting_evening
    }
    val datePattern = stringResource(R.string.dashboard_date_pattern)
    val locale = remember { context.resources.configuration.locales[0] }
    val dateText = remember(datePattern, locale) {
        LocalDate.now().format(DateTimeFormatter.ofPattern(datePattern, locale))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(greetingRes),
            color = WorkbenchColors.InkStrong,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = dateText,
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = if (summary.dueReviewCount > 0) {
                stringResource(R.string.dashboard_greeting_due_subtitle, summary.dueReviewCount)
            } else {
                stringResource(R.string.dashboard_greeting_clear_subtitle)
            },
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun AssistantEntryCard(onClick: () -> Unit) {
    val description = stringResource(R.string.dashboard_open_assistant)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DashboardTokens.CardCornerRadius))
            .background(WorkbenchColors.AccentContainer)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(DashboardTokens.CardPadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(DashboardTokens.AssistantIconSize)
                .clip(CircleShape)
                .background(WorkbenchColors.Surface.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = WorkbenchColors.OnAccentContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = stringResource(R.string.dashboard_assistant_entry_title),
                color = WorkbenchColors.OnAccentContainer,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.dashboard_assistant_entry_body),
                color = WorkbenchColors.OnAccentContainer.copy(alpha = 0.72f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = WorkbenchColors.OnAccentContainer.copy(alpha = 0.72f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ContinueLearningSection(
    summary: DashboardSummary,
    areas: List<com.cslearningos.mobile.data.AreaEntity>,
    viewModel: LearningViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(DashboardTokens.SectionTitleSpacing)) {
        DashboardSectionTitle(text = stringResource(R.string.dashboard_section_continue))
        val node = summary.recentNode
        if (node == null) {
            StartFirstNodeCard(onClick = viewModel::startNewNode)
        } else {
            val context = LocalContext.current
            val areaLabel = areas.firstOrNull { it.id == node.areaId && it.deletedAt == null }
                ?.let { displayAreaName(context, it) }
                ?: readableAreaLabel(context, node.area)
            ContinueReadingCard(
                title = node.title,
                preview = dashboardPreviewMarkdown(node.markdownBody, stringResource(R.string.library_no_body_yet)),
                areaLabel = areaLabel,
                onClick = { viewModel.openNode(node) }
            )
        }
        if (summary.dueReviewCount > 0) {
            ReviewDueBanner(
                dueCount = summary.dueReviewCount,
                onClick = viewModel::showReview
            )
        }
    }
}

@Composable
private fun ContinueReadingCard(
    title: String,
    preview: String,
    areaLabel: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DashboardTokens.CardMinHeight)
            .clip(RoundedCornerShape(DashboardTokens.CardCornerRadius))
            .background(WorkbenchColors.SurfaceCard)
            .clickable(onClick = onClick)
            .padding(DashboardTokens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dashboard_continue_eyebrow).uppercase(),
                color = WorkbenchColors.AccentStrong,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = areaLabel,
                color = WorkbenchColors.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = title,
            color = WorkbenchColors.InkStrong,
            fontSize = 19.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = preview,
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StartFirstNodeCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DashboardTokens.CardCornerRadius))
            .background(WorkbenchColors.SurfaceCard)
            .clickable(onClick = onClick)
            .padding(DashboardTokens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.dashboard_start_node_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.dashboard_start_node_body),
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun ReviewDueBanner(dueCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DashboardTokens.CardCornerRadius))
            .background(WorkbenchColors.SurfaceCard)
            .clickable(onClick = onClick)
            .padding(DashboardTokens.CardPadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(DashboardTokens.AssistantIconSize)
                .clip(CircleShape)
                .background(WorkbenchColors.AccentContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = WorkbenchColors.OnAccentContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.dashboard_review_card_title),
                color = WorkbenchColors.InkStrong,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.dashboard_review_card_body, dueCount),
                color = WorkbenchColors.Muted,
                fontSize = 13.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = WorkbenchColors.Muted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun QuickActionsSection(viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(DashboardTokens.SectionTitleSpacing)) {
        DashboardSectionTitle(text = stringResource(R.string.dashboard_section_actions))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionTile(
                icon = Icons.Filled.Add,
                label = stringResource(R.string.dashboard_action_capture),
                onClick = viewModel::showCapture,
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                icon = Icons.Filled.Create,
                label = stringResource(R.string.dashboard_action_create),
                onClick = viewModel::startNewNode,
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                icon = Icons.Filled.Search,
                label = stringResource(R.string.dashboard_action_search),
                onClick = viewModel::showSearch,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(DashboardTokens.TileCornerRadius))
            .background(WorkbenchColors.SurfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(DashboardTokens.TileIconSize)
                .clip(CircleShape)
                .background(WorkbenchColors.AccentContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WorkbenchColors.OnAccentContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            color = WorkbenchColors.InkStrong,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DashboardSectionTitle(text: String) {
    Text(
        text = text,
        color = WorkbenchColors.InkStrong,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

private fun dashboardPreviewMarkdown(markdown: String, fallback: String): String =
    markdown
        .lineSequence()
        .map { it.trim().trimStart('#', '-', '>', ' ') }
        .firstOrNull { it.isNotBlank() && !it.startsWith(":::") }
        ?: fallback

private val previewDashboardSummary = DashboardSummary(
    primaryActions = emptyList(),
    firstScreenActions = emptyList(),
    compactActions = emptyList(),
    dueReviewCount = 3,
    nodeCount = 42,
    captureSlipCount = 2,
    recentNode = null
)

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Dashboard · Day")
@Composable
private fun DashboardDayPreview() {
    WorkbenchTheme(appearanceMode = AppearanceMode.Day, dynamicColor = false) {
        Column(
            modifier = Modifier
                .background(WorkbenchColors.Surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(DashboardTokens.SectionSpacing)
        ) {
            DashboardGreetingHeader(summary = previewDashboardSummary)
            AssistantEntryCard(onClick = {})
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Dashboard · Night")
@Composable
private fun DashboardNightPreview() {
    WorkbenchTheme(appearanceMode = AppearanceMode.Night, dynamicColor = false) {
        Column(
            modifier = Modifier
                .background(WorkbenchColors.Surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(DashboardTokens.SectionSpacing)
        ) {
            ContinueReadingCard(
                title = "虚拟内存与页面置换",
                preview = "TLB miss 之后为什么会触发页表遍历……",
                areaLabel = "CS Fundamentals",
                onClick = {}
            )
            ReviewDueBanner(dueCount = 3, onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionTile(icon = Icons.Filled.Add, label = "捕捉", onClick = {}, modifier = Modifier.weight(1f))
                QuickActionTile(icon = Icons.Filled.Create, label = "创建", onClick = {}, modifier = Modifier.weight(1f))
                QuickActionTile(icon = Icons.Filled.Search, label = "搜索", onClick = {}, modifier = Modifier.weight(1f))
            }
        }
    }
}
