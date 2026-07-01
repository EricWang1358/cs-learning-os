@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.SearchResultEntity
import com.cslearningos.mobile.domain.ReviewRating
import java.text.DateFormat
import java.util.Date

private val CardShape = RoundedCornerShape(10.dp)

data class MobileBottomNavItem(
    val label: String,
    val screen: AppScreen,
    val contentDescription: String
)

fun mobileBottomNavItems(): List<MobileBottomNavItem> =
    listOf(
        MobileBottomNavItem("Home", AppScreen.Home, "Home dashboard"),
        MobileBottomNavItem("Capture", AppScreen.Capture, "Quick capture"),
        MobileBottomNavItem("Library", AppScreen.Library, "Knowledge library"),
        MobileBottomNavItem("Review", AppScreen.Review, "Due review"),
        MobileBottomNavItem("More", AppScreen.More, "Settings and data")
    )

@Composable
fun LearningOsApp(viewModel: LearningViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WorkbenchTheme(appearanceMode = state.appearanceMode) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .workbenchGrid()
                .safeDrawingPadding()
        ) {
            when {
                maxWidth >= 1100.dp -> LandscapeWorkbench(state = state, viewModel = viewModel)
                maxWidth >= 840.dp -> TwoPaneWorkbench(state = state, viewModel = viewModel)
                else -> PortraitWorkbench(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun PortraitWorkbench(state: LearningUiState, viewModel: LearningViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                if (useCompactPortraitBrand(state.screen)) {
                    CompactBrandBlock(state)
                } else {
                    BrandBlock(state)
                }
            }
            item { StatusBanner(state.message) }
            item { NoticeTray(state = state, viewModel = viewModel) }
            item { ScreenContent(state, viewModel, isDetailPane = true) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        MobileBottomNav(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun TwoPaneWorkbench(state: LearningUiState, viewModel: LearningViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        WorkbenchSidebar(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
        )
        LazyColumn(
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(WorkbenchColors.Surface.copy(alpha = 0.88f))
                .border(BorderStroke(1.dp, WorkbenchColors.Line))
        ) {
            item { StatusBanner(state.message) }
            item { NoticeTray(state = state, viewModel = viewModel) }
            item { ScreenContent(state, viewModel, isDetailPane = true) }
        }
    }
}

@Composable
private fun LandscapeWorkbench(state: LearningUiState, viewModel: LearningViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        WorkbenchSidebar(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(WorkbenchColors.SurfaceSoft.copy(alpha = 0.94f))
                .border(BorderStroke(1.dp, WorkbenchColors.Line))
        ) {
            LazyColumn(
                contentPadding = PaddingValues(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item { StatusBanner(state.message) }
                item { ScreenContent(state, viewModel, isDetailPane = false) }
            }
        }
        Box(
            modifier = Modifier
                .weight(1.02f)
                .fillMaxHeight()
                .background(WorkbenchColors.Surface.copy(alpha = 0.86f))
        ) {
            DetailPane(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun WorkbenchSidebar(
    state: LearningUiState,
    viewModel: LearningViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(WorkbenchColors.Surface.copy(alpha = 0.96f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BrandBlock(state)
        NavButton("Home", state.screen == AppScreen.Home, viewModel::showHome)
        NavButton("Capture", state.screen == AppScreen.Capture, viewModel::showCapture)
        NavButton("Library", state.screen == AppScreen.Library, viewModel::showLibrary)
        NavButton("Review (${state.dueQuizzes.size})", state.screen == AppScreen.Review, viewModel::showReview)
        NavButton("More", state.screen == AppScreen.More || state.screen == AppScreen.Backup, viewModel::showMore)
        Spacer(modifier = Modifier.height(4.dp))
            WorkbenchCard {
            Eyebrow("offline first")
            Text(
                text = "Local Markdown, Room database, explicit JSON backup. No account required; AI is optional.",
                color = WorkbenchColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun BrandBlock(state: LearningUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Eyebrow("CS Learning OS")
            Text(
                text = "Knowledge\nWorkbench",
                color = WorkbenchColors.Accent,
                fontSize = 30.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatPill("Nodes", state.nodes.size.toString(), Modifier.weight(1f))
            StatPill("Due", state.dueQuizzes.size.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactBrandBlock(state: LearningUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WorkbenchColors.SurfaceCard.copy(alpha = 0.88f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Eyebrow("CS Learning OS")
            Text(
                text = state.screen.name,
                color = WorkbenchColors.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaPill("Nodes", state.nodes.size.toString())
            MetaPill("Due", state.dueQuizzes.size.toString())
        }
    }
}

@Composable
private fun MobileBottomNav(
    state: LearningUiState,
    viewModel: LearningViewModel,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(WorkbenchColors.SurfaceCard.copy(alpha = 0.96f))
            .border(BorderStroke(1.dp, WorkbenchColors.LineStrong), RoundedCornerShape(22.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        mobileBottomNavItems().forEach { item ->
            val selected = when (item.screen) {
                AppScreen.More -> state.screen == AppScreen.More || state.screen == AppScreen.Backup
                else -> state.screen == item.screen
            }
            BottomNavTab(
                item = item,
                selected = selected,
                dueCount = state.dueQuizzes.size,
                onClick = { item.open(viewModel) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomNavTab(
    item: MobileBottomNavItem,
    selected: Boolean,
    dueCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) WorkbenchColors.Accent.copy(alpha = 0.16f) else WorkbenchColors.Surface.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(
                imageVector = item.icon(),
                contentDescription = item.contentDescription,
                tint = if (selected) WorkbenchColors.Accent else WorkbenchColors.Muted,
                modifier = Modifier.height(22.dp)
            )
            if (item.screen == AppScreen.Review && dueCount > 0) {
                Badge(
                    containerColor = WorkbenchColors.Danger,
                    contentColor = WorkbenchColors.InkStrong,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(dueCount.toString(), fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Text(
            text = item.label,
            color = if (selected) WorkbenchColors.InkStrong else WorkbenchColors.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

private fun MobileBottomNavItem.open(viewModel: LearningViewModel) {
    when (screen) {
        AppScreen.Home -> viewModel.showHome()
        AppScreen.Capture -> viewModel.showCapture()
        AppScreen.Library -> viewModel.showLibrary()
        AppScreen.Review -> viewModel.showReview()
        AppScreen.More -> viewModel.showMore()
        else -> viewModel.showHome()
    }
}

private fun MobileBottomNavItem.icon(): ImageVector =
    when (screen) {
        AppScreen.Home -> Icons.Filled.Home
        AppScreen.Capture -> Icons.Filled.Add
        AppScreen.Library -> Icons.AutoMirrored.Filled.List
        AppScreen.Review -> Icons.Filled.CheckCircle
        AppScreen.More -> Icons.Filled.Menu
        else -> Icons.Filled.Home
    }

@Composable
private fun ScreenContent(state: LearningUiState, viewModel: LearningViewModel, isDetailPane: Boolean) {
    when (state.screen) {
        AppScreen.Home -> DashboardScreen(state, viewModel)
        AppScreen.Capture -> CaptureScreen(state, viewModel)
        AppScreen.Library -> LibraryScreen(state, viewModel)
        AppScreen.Reader -> if (isDetailPane) ReaderScreen(state, viewModel) else LibraryScreen(state, viewModel)
        AppScreen.Editor -> if (isDetailPane) EditorScreen(state, viewModel) else LibraryScreen(state, viewModel)
        AppScreen.Search -> SearchScreen(state, viewModel)
        AppScreen.QuizEditor -> if (isDetailPane) QuizEditorScreen(state, viewModel) else LibraryScreen(state, viewModel)
        AppScreen.Review -> ReviewScreen(state, viewModel)
        AppScreen.Backup -> BackupScreen(state, viewModel)
        AppScreen.More -> MoreScreen(state, viewModel)
    }
}

@Composable
private fun DetailPane(state: LearningUiState, viewModel: LearningViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StatusBanner(state.message)
        NoticeTray(state = state, viewModel = viewModel)
        when (state.screen) {
            AppScreen.Reader -> ReaderScreen(state, viewModel)
            AppScreen.Editor -> EditorScreen(state, viewModel)
            AppScreen.QuizEditor -> QuizEditorScreen(state, viewModel)
            else -> DetailEmptyState(state)
        }
    }
}

@Composable
private fun DetailEmptyState(state: LearningUiState) {
    val selected = state.selectedNode
    WorkbenchCard(accent = selected != null) {
        Eyebrow(if (selected == null) "detail panel" else "selected node")
        Text(
            text = selected?.title ?: "Select a node",
            color = WorkbenchColors.InkStrong,
            fontSize = 25.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 29.sp
        )
        Text(
            text = selected?.markdownBody?.lineSequence()?.firstOrNull { it.isNotBlank() }
                ?: "Choose a card to inspect details, edit Markdown, add quiz cards, and keep your local study loop moving.",
            color = WorkbenchColors.Muted,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun LibraryScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val groups = buildLibraryGroups(state.nodes)
    val overview = buildLibraryOverview(state.nodes)
    val map = buildLibraryMap(state.nodes, state.collapsedLibraryAreas)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LibraryOverviewCard(overview = overview)
        LibraryMapCard(map = map, onToggleArea = viewModel::toggleLibraryArea)
        WorkbenchButton(
            text = "+ New node",
            onClick = viewModel::startNewNode,
            primary = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.nodes.isEmpty()) {
            EmptyWorkbenchCard(
                title = "Build the first local node",
                body = "Create a Markdown note. The phone app works without desktop, account, network, or AI."
            )
        }
        groups.forEach { area ->
            val collapsed = area.area in state.collapsedLibraryAreas
            WorkbenchCard {
                Eyebrow("area")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        readableAreaLabel(area.area),
                        color = WorkbenchColors.InkStrong,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    WorkbenchButton(if (collapsed) "Open" else "Close", { viewModel.toggleLibraryArea(area.area) })
                }
                if (collapsed) {
                    Text(
                        text = "${area.tracks.sumOf { it.nodes.size }} nodes hidden. Open this area when you want to drill into tracks.",
                        color = WorkbenchColors.Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                } else {
                    area.tracks.forEach { track ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    readableTrackLabel(track.track),
                                    color = WorkbenchColors.Accent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    "${track.nodes.size} nodes",
                                    color = WorkbenchColors.Muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            track.nodes.forEach { item ->
                                LibraryNodeCard(
                                    item = item,
                                    selected = state.selectedNode?.id == item.id,
                                    onOpen = { viewModel.openNode(item.node) },
                                    onEdit = { viewModel.editNode(item.node) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeTray(state: LearningUiState, viewModel: LearningViewModel) {
    val latest = state.notices.firstOrNull() ?: return
    WorkbenchCard(accent = latest.title.contains("ready", ignoreCase = true)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("notification")
                Text(latest.title, color = WorkbenchColors.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(latest.body, color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
            WorkbenchButton("Dismiss", { viewModel.dismissNotice(latest.id) })
        }
    }
}

@Composable
private fun LibraryMapCard(map: LibraryMap, onToggleArea: (String) -> Unit) {
    WorkbenchCard(accent = true) {
        Eyebrow("map")
        Text("Area map", color = WorkbenchColors.InkStrong, fontSize = 21.sp, fontWeight = FontWeight.Black)
        Text(
            "Tap an area to collapse or expand. This mirrors the desktop knowledge map as a phone-friendly outline.",
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        map.areas.forEach { area ->
            InteractiveCard(onClick = { onToggleArea(area.area) }, accent = !area.collapsed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(area.label, color = WorkbenchColors.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text(area.trackPreview, color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    MetaPill(if (area.collapsed) "Closed" else "Open", "${area.nodeCount}N/${area.trackCount}T")
                }
            }
        }
    }
}

@Composable
private fun LibraryOverviewCard(overview: LibraryOverview) {
    WorkbenchCard(accent = true) {
        Eyebrow("library")
        Text("Knowledge map", color = WorkbenchColors.InkStrong, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            "Desktop-compatible structure: ${overview.structureLabel}. Start from a topic, then drill into tracks and ordered nodes.",
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetaPill("Areas", overview.areaCount.toString(), Modifier.weight(1f))
            MetaPill("Tracks", overview.trackCount.toString(), Modifier.weight(1f))
            MetaPill("Nodes", overview.nodeCount.toString(), Modifier.weight(1f))
        }
        MetaPill("Featured", overview.featuredAreaLabel)
    }
}

@Composable
private fun LibraryNodeCard(
    item: LibraryNodeSummary,
    selected: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    InteractiveCard(onClick = onOpen, accent = selected) {
        Eyebrow(item.meta)
        Text(
            text = item.title,
            color = WorkbenchColors.InkStrong,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.summary,
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkbenchButton("Read", onOpen, primary = true)
            WorkbenchButton("Edit", onEdit)
        }
    }
}

@Composable
private fun NodeCard(
    node: LearningNodeEntity,
    selected: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    InteractiveCard(onClick = onOpen, accent = selected) {
        Eyebrow("node / rev ${node.revision}")
        Text(
            text = node.title,
            color = WorkbenchColors.InkStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = previewMarkdown(node.markdownBody),
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetaPill("Updated", formatTime(node.updatedAt), Modifier.weight(1f))
            MetaPill("Last read", node.lastReadAt?.let(::formatTime) ?: "Not recorded", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkbenchButton("Read", onOpen, primary = true)
            WorkbenchButton("Edit", onEdit)
        }
    }
}

@Composable
private fun ReaderScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val node = state.selectedNode
    if (node == null) {
        EmptyWorkbenchCard("No node selected", "Open a node from the library first.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailHeading(
            eyebrow = "reading desk",
            title = node.title,
            body = "Updated ${formatTime(node.updatedAt)} / Last read ${node.lastReadAt?.let(::formatTime) ?: "Not recorded"}"
        )
        ToolbarRow {
            WorkbenchButton("Back", viewModel::showHome)
            WorkbenchButton("Edit mode", { viewModel.editNode(node) }, primary = true)
            WorkbenchButton("Add quiz", viewModel::startQuizForSelectedNode)
            WorkbenchButton("Delete", viewModel::deleteSelectedNode, danger = true)
            WorkbenchButton(
                readerQuestionButtonLabel(openQuestionCount = state.readerQuestions.count { it.nodeId == node.id }, expanded = state.readerQuestionPanelExpanded),
                viewModel::toggleReaderQuestionPanel
            )
        }
        if (state.readerQuestionPanelExpanded) {
            ReaderQuestionCapture(state = state, viewModel = viewModel, nodeId = node.id)
        }
        MarkdownRenderer(markdown = node.markdownBody)
    }
}

@Composable
private fun ReaderQuestionCapture(state: LearningUiState, viewModel: LearningViewModel, nodeId: String) {
    val openQuestions = state.readerQuestions.filter { it.nodeId == nodeId }
    WorkbenchCard(accent = openQuestions.isNotEmpty()) {
        Eyebrow("q to be solved")
        Text(
            text = "What is unclear?",
            color = WorkbenchColors.InkStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Save questions while reading. They stay unresolved until you mark them resolved, export them, or fold them into Markdown/quiz edits.",
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        WorkbenchTextField(
            value = state.readerQuestionDraft,
            onValueChange = viewModel::setReaderQuestionDraft,
            label = "Example: This explanation skips why %eax changes here.",
            minLines = 3
        )
        WorkbenchButton(
            text = "Save question",
            onClick = viewModel::saveReaderQuestion,
            primary = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (openQuestions.isEmpty()) {
            Text("0 open", color = WorkbenchColors.InkStrong, fontSize = 12.sp, fontWeight = FontWeight.Black)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${openQuestions.size} open",
                    color = WorkbenchColors.InkStrong,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                openQuestions.take(4).forEach { question ->
                    ReaderQuestionRow(question = question, onResolve = { viewModel.resolveReaderQuestion(question) })
                }
            }
        }
    }
}

@Composable
private fun ReaderQuestionRow(question: ReaderQuestionEntity, onResolve: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(WorkbenchColors.Surface.copy(alpha = 0.58f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), CardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = question.body,
            color = WorkbenchColors.InkStrong,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        WorkbenchButton("Resolved", onResolve)
    }
}

@Composable
private fun EditorScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailHeading(
            eyebrow = if (state.editorNodeId == null) "new node" else "edit mode",
            title = if (state.editorNodeId == null) "Create Markdown node" else "Edit Markdown",
            body = "Use Markdown headings and :::quiz blocks. Saving returns to the reader."
        )
        WorkbenchTextField(
            value = state.editorTitle,
            onValueChange = viewModel::setEditorTitle,
            label = "Title"
        )
        WorkbenchTextField(
            value = state.editorBody,
            onValueChange = viewModel::setEditorBody,
            label = "Markdown body",
            minLines = 16
        )
        ToolbarRow {
            WorkbenchButton("Save Markdown", viewModel::saveNode, primary = true)
            WorkbenchButton("Cancel", viewModel::cancelEditor)
        }
    }
}

@Composable
private fun SearchScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "global search",
            title = "Find local knowledge",
            body = "Search nodes and quiz cards stored on this device."
        )
        WorkbenchTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            label = "Search concepts, tags, summaries..."
        )
        WorkbenchButton("Search", viewModel::runSearch, primary = true, modifier = Modifier.fillMaxWidth())
        if (state.searchResults.isEmpty()) {
            EmptyWorkbenchCard("No search results yet", "Type a query and search. Results open directly into reader or review.")
        }
        state.searchResults.forEach { result ->
            SearchResultCard(result = result, onOpen = { viewModel.openSearchResult(result) })
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResultEntity, onOpen: () -> Unit) {
    InteractiveCard(onClick = onOpen, accent = result.type == "quiz") {
        Eyebrow(result.type)
        Text(result.title, color = WorkbenchColors.InkStrong, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(result.snippet, color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun QuizEditorScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailHeading(
            eyebrow = "quiz authoring",
            title = "Add review card",
            body = "Linked to ${state.selectedNode?.title ?: "general review"}. Question and answer are required."
        )
        WorkbenchTextField(state.quizPrompt, viewModel::setQuizPrompt, "Question", minLines = 3)
        WorkbenchTextField(state.quizAnswer, viewModel::setQuizAnswer, "Answer", minLines = 3)
        WorkbenchTextField(state.quizExplanation, viewModel::setQuizExplanation, "Explanation", minLines = 4)
        ToolbarRow {
            WorkbenchButton("Save quiz", viewModel::saveQuiz, primary = true)
            WorkbenchButton("Cancel", viewModel::cancelEditor)
        }
    }
}

@Composable
private fun ReviewScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val quiz = state.selectedQuiz
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "review system",
            title = "Daily review",
            body = "${state.dueQuizzes.size} due now / ${state.quizzes.size} total cards. Again keeps the current card in this session; Hard and Good advance."
        )
        if (quiz == null) {
            EmptyWorkbenchCard(
                title = "Queue is clear",
                body = "Add quiz cards from a note or Markdown :::quiz block. Due cards will appear here."
            )
            return
        }
        WorkbenchCard(accent = true) {
            Eyebrow(quiz.source.name)
            Text(quiz.prompt, color = WorkbenchColors.InkStrong, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
            if (!state.quizAnswerVisible) {
                Text("Answer is hidden until you reveal it.", color = WorkbenchColors.Muted, fontSize = 14.sp)
                WorkbenchButton("Reveal answer", viewModel::revealCurrentQuizAnswer, primary = true, modifier = Modifier.fillMaxWidth())
            } else {
                AnswerBlock(quiz)
                ToolbarRow {
                    WorkbenchButton("Again", { viewModel.answerCurrentQuiz(ReviewRating.Again) }, danger = true)
                    WorkbenchButton("Hard", { viewModel.answerCurrentQuiz(ReviewRating.Hard) })
                    WorkbenchButton("Good", { viewModel.answerCurrentQuiz(ReviewRating.Good) }, primary = true)
                }
            }
        }
    }
}

@Composable
private fun AnswerBlock(quiz: QuizItemEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(WorkbenchColors.Surface.copy(alpha = 0.72f))
            .border(BorderStroke(1.dp, WorkbenchColors.LineStrong), CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Eyebrow("answer")
        Text(quiz.answer, color = WorkbenchColors.InkStrong, fontSize = 17.sp, lineHeight = 24.sp)
        if (quiz.explanation.isNotBlank()) {
            Text(quiz.explanation, color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun BackupScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "local safety",
            title = "Backup, export, restore",
            body = "JSON is full app recovery. Markdown/TXT is readable study material for computer editing or printing."
        )
        ToolbarRow {
            WorkbenchButton("Export JSON", viewModel::exportBackup, primary = true)
            WorkbenchButton("Restore overwrite", viewModel::restoreBackup, danger = true)
        }
        WorkbenchTextField(
            value = state.backupText,
            onValueChange = viewModel::setBackupText,
            label = "Backup JSON",
            minLines = 14
        )
    }
}

private fun previewMarkdown(markdown: String): String =
    markdown
        .lineSequence()
        .map { it.trim().trimStart('#', '-', '>', ' ') }
        .firstOrNull { it.isNotBlank() && !it.startsWith(":::") }
        ?: "No body yet. Add Markdown to make this node useful."

private fun formatTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))
