@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.SearchResultEntity
import com.cslearningos.mobile.domain.ReviewRating
import java.text.DateFormat
import java.util.Date

private val PanelShape = RoundedCornerShape(14.dp)
private val CardShape = RoundedCornerShape(10.dp)

@Composable
fun LearningOsApp(viewModel: LearningViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WorkbenchTheme {
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BrandBlock(state) }
        item { MobileNav(state, viewModel) }
        item { StatusBanner(state.message) }
        item { ScreenContent(state, viewModel, isDetailPane = true) }
        item { Spacer(modifier = Modifier.height(28.dp)) }
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
        NavButton("Library", state.screen == AppScreen.Home, viewModel::showHome)
        NavButton("Search", state.screen == AppScreen.Search, viewModel::showSearch)
        NavButton("Review (${state.dueQuizzes.size})", state.screen == AppScreen.Review, viewModel::showReview)
        NavButton("Backup", state.screen == AppScreen.Backup, viewModel::showBackup)
        Spacer(modifier = Modifier.height(4.dp))
        WorkbenchCard {
            Eyebrow("offline first")
            Text(
                text = "Local Markdown, Room database, explicit JSON backup. No account, no network.",
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
private fun MobileNav(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NavButton("Library", state.screen == AppScreen.Home, viewModel::showHome, Modifier.weight(1f))
            NavButton("Search", state.screen == AppScreen.Search, viewModel::showSearch, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NavButton("Review ${state.dueQuizzes.size}", state.screen == AppScreen.Review, viewModel::showReview, Modifier.weight(1f))
            NavButton("Backup", state.screen == AppScreen.Backup, viewModel::showBackup, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScreenContent(state: LearningUiState, viewModel: LearningViewModel, isDetailPane: Boolean) {
    when (state.screen) {
        AppScreen.Home -> LibraryScreen(state, viewModel)
        AppScreen.Reader -> if (isDetailPane) ReaderScreen(state, viewModel) else LibraryScreen(state, viewModel)
        AppScreen.Editor -> if (isDetailPane) EditorScreen(state, viewModel) else LibraryScreen(state, viewModel)
        AppScreen.Search -> SearchScreen(state, viewModel)
        AppScreen.QuizEditor -> if (isDetailPane) QuizEditorScreen(state, viewModel) else LibraryScreen(state, viewModel)
        AppScreen.Review -> ReviewScreen(state, viewModel)
        AppScreen.Backup -> BackupScreen(state, viewModel)
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "global search",
            title = "Local library",
            body = "${state.nodes.size} active nodes. Last read is updated when you open a note."
        )
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
        state.nodes.forEach { node ->
            NodeCard(
                node = node,
                selected = state.selectedNode?.id == node.id,
                onOpen = { viewModel.openNode(node) },
                onEdit = { viewModel.editNode(node) }
            )
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
        }
        MarkdownRenderer(markdown = node.markdownBody)
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
            body = "${state.dueQuizzes.size} due now / ${state.quizzes.size} total cards."
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
            title = "Backup and restore",
            body = "Export JSON when moving devices. Restore overwrites local active data, so keep a copy first."
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

@Composable
private fun SectionHeader(eyebrow: String, title: String, body: String) {
    WorkbenchCard(accent = true) {
        Eyebrow(eyebrow)
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 25.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
        Text(body, color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun DetailHeading(eyebrow: String, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, WorkbenchColors.Line), PanelShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(WorkbenchColors.Accent.copy(alpha = 0.18f), Color.Transparent),
                    radius = 620f
                ),
                PanelShape
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Eyebrow(eyebrow)
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 31.sp, fontWeight = FontWeight.Black, lineHeight = 35.sp)
        Text(body, color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun WorkbenchCard(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (accent) 14.dp else 8.dp,
                shape = CardShape,
                ambientColor = WorkbenchColors.Surface.copy(alpha = 0.36f),
                spotColor = WorkbenchColors.Surface.copy(alpha = 0.42f)
            )
            .drawBehind {
                if (accent) {
                    drawLine(
                        color = WorkbenchColors.Accent,
                        start = Offset(0f, 10.dp.toPx()),
                        end = Offset(0f, size.height - 10.dp.toPx()),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = WorkbenchColors.SurfaceCard),
        border = BorderStroke(1.dp, if (accent) WorkbenchColors.Accent else WorkbenchColors.Line)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (accent) {
                        Brush.linearGradient(
                            listOf(WorkbenchColors.Accent.copy(alpha = 0.14f), Color.Transparent)
                        )
                    } else {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = content
        )
    }
}

@Composable
private fun InteractiveCard(onClick: () -> Unit, accent: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    WorkbenchCard(
        accent = accent || pressed,
        modifier = Modifier
            .heightIn(min = 72.dp)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        content = content
    )
}

@Composable
private fun EmptyWorkbenchCard(title: String, body: String) {
    WorkbenchCard {
        Eyebrow("empty state")
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(body, color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun WorkbenchButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    val container = when {
        primary -> WorkbenchColors.Accent
        danger -> WorkbenchColors.SurfaceCard
        else -> WorkbenchColors.SurfaceCard
    }
    val content = when {
        primary -> WorkbenchColors.SurfaceSoft
        danger -> WorkbenchColors.Danger
        else -> WorkbenchColors.Accent
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (danger) WorkbenchColors.Danger.copy(alpha = 0.58f) else WorkbenchColors.LineStrong),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = WorkbenchColors.SurfaceCard.copy(alpha = 0.44f),
            disabledContentColor = WorkbenchColors.Muted
        )
    ) {
        Text(text, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun NavButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    WorkbenchButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        primary = selected
    )
}

@Composable
private fun WorkbenchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = WorkbenchColors.Muted) },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = WorkbenchColors.Ink,
            unfocusedTextColor = WorkbenchColors.Ink,
            cursorColor = WorkbenchColors.Accent,
            focusedBorderColor = WorkbenchColors.Accent,
            unfocusedBorderColor = WorkbenchColors.LineStrong,
            focusedContainerColor = WorkbenchColors.Surface,
            unfocusedContainerColor = WorkbenchColors.Surface
        )
    )
}

@Composable
private fun StatusBanner(message: String) {
    if (message.isBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(WorkbenchColors.Accent.copy(alpha = 0.11f))
            .border(BorderStroke(1.dp, WorkbenchColors.Accent.copy(alpha = 0.32f)), CardShape)
            .padding(12.dp)
    ) {
        Text(message, color = WorkbenchColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text = text.uppercase(),
        color = WorkbenchColors.Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(WorkbenchColors.Accent.copy(alpha = 0.12f), WorkbenchColors.SurfaceCard)
                )
            )
            .border(BorderStroke(1.dp, WorkbenchColors.Accent.copy(alpha = 0.28f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), color = WorkbenchColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(value, color = WorkbenchColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MetaPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(WorkbenchColors.Surface.copy(alpha = 0.56f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(10.dp))
            .padding(9.dp)
    ) {
        Text(label.uppercase(), color = WorkbenchColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(value, color = WorkbenchColors.InkStrong, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolbarRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

private fun Modifier.workbenchGrid(): Modifier = drawBehind {
    drawRect(WorkbenchColors.Surface)
    val step = 44.dp.toPx()
    val lineColor = WorkbenchColors.Accent.copy(alpha = 0.035f)
    var x = 0f
    while (x <= size.width) {
        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
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
