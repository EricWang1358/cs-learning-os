@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.cslearningos.mobile.appshell.navigation.AppRoute
import com.cslearningos.mobile.appshell.navigation.selectedBottomTabFor as selectedBottomRouteFor
import com.cslearningos.mobile.appshell.navigation.toAppRoute
import com.cslearningos.mobile.appshell.navigation.toAppScreen
import com.cslearningos.mobile.appshell.state.AppShellState
import com.cslearningos.mobile.appshell.state.AppShellViewModel
import com.cslearningos.mobile.appshell.state.toAppShellState
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.SearchResultEntity
import com.cslearningos.mobile.domain.ReviewRating
import com.cslearningos.mobile.ui.backup.BackupDocument
import com.cslearningos.mobile.ui.backup.BackupTransferCoordinator
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(10.dp)

fun selectedBottomTabFor(screen: AppScreen): AppScreen =
    selectedBottomRouteFor(screen.toAppRoute()).toAppScreen()

@Composable
fun LearningOsApp(
    shellViewModel: AppShellViewModel = viewModel(),
    learningViewModel: LearningViewModel = viewModel()
) {
    val learningState by learningViewModel.state.collectAsStateWithLifecycle()
    val shellState by shellViewModel.state.collectAsStateWithLifecycle()
    val localizedContext = rememberLocalizedAppContext(learningState.systemLanguage)

    LaunchedEffect(learningState.screen, learningState.message) {
        shellViewModel.syncFrom(learningState.toAppShellState())
    }

    WorkbenchTheme(appearanceMode = learningState.appearanceMode) {
        CompositionLocalProvider(LocalContext provides localizedContext) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .workbenchGrid()
                    .safeDrawingPadding()
            ) {
                when {
                    maxWidth >= 1100.dp -> LandscapeWorkbench(shellState = shellState, state = learningState, viewModel = learningViewModel)
                    maxWidth >= 840.dp -> TwoPaneWorkbench(shellState = shellState, state = learningState, viewModel = learningViewModel)
                    else -> PortraitWorkbench(shellState = shellState, state = learningState, viewModel = learningViewModel)
                }
            }
        }
    }
}

@Composable
private fun PortraitWorkbench(
    shellState: AppShellState,
    state: LearningUiState,
    viewModel: LearningViewModel
) {
    if (shellState.route == AppRoute.Assistant) {
        AssistantScreen(state = state, viewModel = viewModel)
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                if (useCompactPortraitBrand(shellState.route.toAppScreen())) {
                    CompactBrandBlock(route = shellState.route, state = state)
                } else {
                    BrandBlock(
                        state = state,
                        onAssistantClick = viewModel::showAssistant
                    )
                }
            }
            item { StatusBanner(shellState.message) }
            item { NoticeTray(state = state, viewModel = viewModel) }
            item {
                AnimatedContent(
                    targetState = shellState.route,
                    transitionSpec = {
                        (fadeIn(tween(WorkbenchMotion.CompactFadeMillis)) + slideInHorizontally(tween(WorkbenchMotion.CompactExpandMillis)) { it / 12 }) togetherWith
                            (fadeOut(tween(WorkbenchMotion.CompactFadeMillis)) + slideOutHorizontally(tween(WorkbenchMotion.CompactExpandMillis)) { -it / 12 })
                    },
                    label = "portrait-route"
                ) { route -> ScreenContent(route = route, state = state, viewModel = viewModel, isDetailPane = true) }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        MobileBottomNav(
            currentRoute = shellState.route,
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun TwoPaneWorkbench(
    shellState: AppShellState,
    state: LearningUiState,
    viewModel: LearningViewModel
) {
    if (shellState.route == AppRoute.Assistant) {
        AssistantScreen(state = state, viewModel = viewModel)
        return
    }
    Row(modifier = Modifier.fillMaxSize()) {
        WorkbenchSidebar(
            currentRoute = shellState.route,
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
            item { StatusBanner(shellState.message) }
            item { NoticeTray(state = state, viewModel = viewModel) }
            item { ScreenContent(route = shellState.route, state = state, viewModel = viewModel, isDetailPane = true) }
        }
    }
}

@Composable
private fun LandscapeWorkbench(
    shellState: AppShellState,
    state: LearningUiState,
    viewModel: LearningViewModel
) {
    if (shellState.route == AppRoute.Assistant) {
        AssistantScreen(state = state, viewModel = viewModel)
        return
    }
    Row(modifier = Modifier.fillMaxSize()) {
        WorkbenchSidebar(
            currentRoute = shellState.route,
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
                item { StatusBanner(shellState.message) }
                item { NoticeTray(state = state, viewModel = viewModel) }
                item { ScreenContent(route = shellState.route, state = state, viewModel = viewModel, isDetailPane = false) }
            }
        }
        Box(
            modifier = Modifier
                .weight(1.02f)
                .fillMaxHeight()
                .background(WorkbenchColors.Surface.copy(alpha = 0.86f))
        ) {
            DetailPane(route = shellState.route, state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun WorkbenchSidebar(
    currentRoute: AppRoute,
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
        BrandBlock(
            state = state,
            onAssistantClick = if (currentRoute == AppRoute.Home) viewModel::showAssistant else null
        )
        NavButton(stringResource(R.string.nav_home_label), currentRoute == AppRoute.Home, onClick = { viewModel.openTopLevelRoute(AppRoute.Home) })
        NavButton(stringResource(R.string.nav_capture_label), currentRoute == AppRoute.Capture, onClick = { viewModel.openTopLevelRoute(AppRoute.Capture) })
        NavButton(stringResource(R.string.nav_library_label), currentRoute == AppRoute.Library, onClick = { viewModel.openTopLevelRoute(AppRoute.Library) })
        NavButton(
            stringResource(R.string.nav_review_count, state.dueQuizzes.size),
            currentRoute == AppRoute.Review,
            onClick = { viewModel.openTopLevelRoute(AppRoute.Review) }
        )
        NavButton(
            stringResource(R.string.nav_more_label),
            currentRoute == AppRoute.More,
            onClick = { viewModel.openTopLevelRoute(AppRoute.More) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        WorkbenchCard {
            Eyebrow(stringResource(R.string.sidebar_offline_first_eyebrow))
            Text(
                text = stringResource(R.string.sidebar_offline_first_body),
                color = WorkbenchColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun BrandBlock(state: LearningUiState, onAssistantClick: (() -> Unit)? = null) {
    val assistantDescription = stringResource(R.string.assistant_open_description)
    val brandModifier = if (onAssistantClick == null) {
        Modifier
    } else {
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onAssistantClick)
            .semantics {
                role = Role.Button
                contentDescription = assistantDescription
            }
            .padding(6.dp)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = brandModifier) {
            Eyebrow(stringResource(R.string.brand_eyebrow))
            Text(
                text = stringResource(R.string.brand_title_multiline),
                color = WorkbenchColors.Accent,
                fontSize = 30.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Black
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatPill(stringResource(R.string.common_nodes), state.nodes.size.toString(), Modifier.weight(1f))
            StatPill(stringResource(R.string.common_due), state.dueQuizzes.size.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactBrandBlock(route: AppRoute, state: LearningUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(WorkbenchMotion.CompactExpandMillis, easing = FastOutSlowInEasing))
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(appScreenLabelResId(route.toAppScreen())),
                color = WorkbenchColors.InkStrong,
                fontSize = 19.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactMetricBadge(stringResource(R.string.common_nodes), state.nodes.size.toString())
            CompactMetricBadge(stringResource(R.string.common_due), state.dueQuizzes.size.toString())
        }
    }
}

@Composable
private fun CompactMetricBadge(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(WorkbenchColors.Surface.copy(alpha = 0.64f))
            .border(BorderStroke(1.dp, WorkbenchColors.LineStrong.copy(alpha = 0.7f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = WorkbenchColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(value, color = WorkbenchColors.InkStrong, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun MobileBottomNav(
    currentRoute: AppRoute,
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
            val selected = selectedBottomRouteFor(currentRoute) == item.screen.toAppRoute()
            BottomNavTab(
                item = item,
                selected = selected,
                dueCount = state.dueQuizzes.size,
                onClick = { viewModel.openTopLevelRoute(item.screen.toAppRoute()) },
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
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) WorkbenchColors.Accent.copy(alpha = 0.16f) else WorkbenchColors.Surface.copy(alpha = 0.18f),
        animationSpec = tween(WorkbenchMotion.CompactFadeMillis, easing = FastOutSlowInEasing),
        label = "bottom-nav-background"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) WorkbenchColors.Accent else WorkbenchColors.Muted,
        animationSpec = tween(WorkbenchMotion.CompactFadeMillis, easing = FastOutSlowInEasing),
        label = "bottom-nav-icon"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) WorkbenchColors.InkStrong else WorkbenchColors.Muted,
        animationSpec = tween(WorkbenchMotion.CompactFadeMillis, easing = FastOutSlowInEasing),
        label = "bottom-nav-label"
    )
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .animateContentSize(tween(WorkbenchMotion.CompactExpandMillis, easing = FastOutSlowInEasing))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(
                imageVector = item.icon(),
                contentDescription = stringResource(item.contentDescriptionResId),
                tint = iconColor,
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
            text = stringResource(item.labelResId),
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

private fun MobileBottomNavItem.icon(): ImageVector =
    when (screen) {
        AppScreen.Home -> Icons.Filled.Home
        AppScreen.Assistant -> Icons.Filled.Home
        AppScreen.Capture -> Icons.Filled.Add
        AppScreen.Library -> Icons.AutoMirrored.Filled.List
        AppScreen.Review -> Icons.Filled.CheckCircle
        AppScreen.More -> Icons.Filled.Menu
        else -> Icons.Filled.Home
    }

@Composable
private fun ScreenContent(route: AppRoute, state: LearningUiState, viewModel: LearningViewModel, isDetailPane: Boolean) {
    when (route) {
        AppRoute.Home -> DashboardScreen(state, viewModel)
        AppRoute.Assistant -> AssistantScreen(state, viewModel)
        AppRoute.Capture -> CaptureScreen(state, viewModel)
        AppRoute.Library -> LibraryScreen(state, viewModel)
        AppRoute.Reader -> if (isDetailPane) ReaderScreen(state, viewModel) else LibraryScreen(state, viewModel)
        AppRoute.Editor -> if (isDetailPane) EditorScreen(state, viewModel) else LibraryScreen(state, viewModel)
        AppRoute.Search -> SearchScreen(state, viewModel)
        AppRoute.QuizEditor -> if (isDetailPane) QuizEditorScreen(state, viewModel) else LibraryScreen(state, viewModel)
        AppRoute.Review -> ReviewScreen(state, viewModel)
        AppRoute.Backup -> BackupScreen(state, viewModel)
        AppRoute.More -> MoreScreen(state, viewModel)
    }
}

@Composable
private fun DetailPane(route: AppRoute, state: LearningUiState, viewModel: LearningViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StatusBanner(state.toAppShellState().message)
        NoticeTray(state = state, viewModel = viewModel)
        when (route) {
            AppRoute.Reader -> ReaderScreen(state, viewModel)
            AppRoute.Editor -> EditorScreen(state, viewModel)
            AppRoute.QuizEditor -> QuizEditorScreen(state, viewModel)
            else -> DetailEmptyState(state)
        }
    }
}

private fun LearningViewModel.openTopLevelRoute(route: AppRoute) {
    when (selectedBottomRouteFor(route)) {
        AppRoute.Home -> showHome()
        AppRoute.Assistant -> showAssistant()
        AppRoute.Capture -> showCapture()
        AppRoute.Library -> showLibrary()
        AppRoute.Review -> showReview()
        AppRoute.More -> showMore()
        else -> showHome()
    }
}

@Composable
private fun DetailEmptyState(state: LearningUiState) {
    val selected = state.selectedNode
    WorkbenchCard(accent = selected != null) {
        Eyebrow(stringResource(if (selected == null) R.string.detail_panel_eyebrow else R.string.selected_node_eyebrow))
        Text(
            text = selected?.title ?: stringResource(R.string.detail_empty_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 25.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 29.sp
        )
        Text(
            text = selected?.markdownBody?.lineSequence()?.firstOrNull { it.isNotBlank() }
                ?: stringResource(R.string.detail_empty_body),
            color = WorkbenchColors.Muted,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun NoticeTray(state: LearningUiState, viewModel: LearningViewModel) {
    val latest = state.notices.firstOrNull() ?: return
    val context = LocalContext.current
    val title = latest.title.resolve(context)
    val body = latest.body.resolve(context)
    WorkbenchCard(accent = title.contains("ready", ignoreCase = true)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow(stringResource(R.string.notice_eyebrow))
                Text(title, color = WorkbenchColors.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(body, color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
            WorkbenchButton(stringResource(R.string.common_dismiss), { viewModel.dismissNotice(latest.id) })
        }
    }
}

@Composable
private fun ReaderScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val node = state.selectedNode
    if (node == null) {
        EmptyWorkbenchCard(
            stringResource(R.string.reader_empty_title),
            stringResource(R.string.reader_empty_body)
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailHeading(
            eyebrow = stringResource(R.string.reader_heading_eyebrow),
            title = node.title,
            body = stringResource(
                R.string.reader_heading_body,
                formatTime(node.updatedAt),
                node.lastReadAt?.let(::formatTime) ?: stringResource(R.string.common_not_recorded)
            )
        )
        val readerQuestionLabel = readerQuestionButtonLabel(
            openQuestionCount = state.readerQuestions.count { it.nodeId == node.id },
            expanded = state.readerQuestionPanelExpanded,
            context = LocalContext.current
        )
        ToolbarRow {
            WorkbenchButton(stringResource(R.string.common_back), viewModel::showHome)
            WorkbenchButton(stringResource(R.string.reader_edit_mode_button), { viewModel.editNode(node) }, primary = true)
            WorkbenchButton(stringResource(R.string.assistant_improve_object), { viewModel.assistantActions.reviseNode(node) })
            WorkbenchButton(stringResource(R.string.common_delete), viewModel::deleteSelectedNode, danger = true)
            WorkbenchMenuButton(
                text = stringResource(R.string.nav_more_label),
                options = listOf(
                    WorkbenchMenuOption(
                        text = stringResource(R.string.assistant_quick_draft),
                        onClick = { viewModel.assistantActions.reviseNode(node) }
                    ),
                    WorkbenchMenuOption(
                        text = stringResource(R.string.reader_add_quiz_button),
                        onClick = viewModel::startQuizForSelectedNode
                    ),
                    WorkbenchMenuOption(
                        text = readerQuestionLabel,
                        onClick = viewModel::toggleReaderQuestionPanel
                    )
                )
            )
        }
        AnimatedVisibility(
            visible = state.readerQuestionPanelExpanded,
            enter = fadeIn(tween(WorkbenchMotion.DetailFadeMillis)) + expandVertically(tween(WorkbenchMotion.DetailExpandMillis)),
            exit = fadeOut(tween(WorkbenchMotion.DetailFadeMillis)) + shrinkVertically(tween(WorkbenchMotion.DetailExpandMillis))
        ) {
            ReaderQuestionCapture(state = state, viewModel = viewModel, nodeId = node.id)
        }
        MarkdownRenderer(markdown = node.markdownBody)
    }
}

@Composable
private fun ReaderQuestionCapture(state: LearningUiState, viewModel: LearningViewModel, nodeId: String) {
    val openQuestions = state.readerQuestions.filter { it.nodeId == nodeId }
    WorkbenchCard(accent = openQuestions.isNotEmpty()) {
        Eyebrow(stringResource(R.string.reader_questions_eyebrow))
        Text(
            text = stringResource(R.string.reader_questions_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = stringResource(R.string.reader_questions_body),
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        WorkbenchTextField(
            value = state.readerQuestionDraft,
            onValueChange = viewModel::setReaderQuestionDraft,
            label = stringResource(R.string.reader_question_example),
            minLines = 3
        )
        WorkbenchButton(
            text = stringResource(R.string.reader_save_question),
            onClick = viewModel::saveReaderQuestion,
            primary = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (openQuestions.isEmpty()) {
            Text(stringResource(R.string.reader_open_question_count, 0), color = WorkbenchColors.InkStrong, fontSize = 12.sp, fontWeight = FontWeight.Black)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.reader_open_question_count, openQuestions.size),
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
        WorkbenchButton(stringResource(R.string.reader_mark_resolved), onResolve)
    }
}

@Composable
private fun EditorScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val context = LocalContext.current
    val areas = state.areas.filter { it.deletedAt == null }
    val selectedArea = areas.firstOrNull { it.id == state.editorAreaId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailHeading(
            eyebrow = stringResource(if (state.editorNodeId == null) R.string.editor_new_node_eyebrow else R.string.editor_edit_mode_eyebrow),
            title = stringResource(if (state.editorNodeId == null) R.string.editor_create_title else R.string.editor_edit_title),
            body = stringResource(R.string.editor_heading_body)
        )
        WorkbenchTextField(
            value = state.editorTitle,
            onValueChange = viewModel::setEditorTitle,
            label = stringResource(R.string.editor_title_field)
        )
        if (areas.isNotEmpty()) {
            WorkbenchMenuButton(
                text = selectedArea?.let { displayAreaName(context, it) }
                    ?: stringResource(R.string.editor_choose_area),
                options = areas.map { area ->
                    WorkbenchMenuOption(displayAreaName(context, area)) {
                        viewModel.setEditorAreaId(area.id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                expandToContainer = true
            )
        }
        WorkbenchTextField(
            value = state.editorBody,
            onValueChange = viewModel::setEditorBody,
            label = stringResource(R.string.editor_body_field),
            minLines = 16
        )
        ToolbarRow {
            WorkbenchButton(stringResource(R.string.editor_save_markdown), viewModel::saveNode, primary = true)
            WorkbenchButton(stringResource(R.string.common_cancel), viewModel::cancelEditor)
        }
    }
}

@Composable
private fun SearchScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = stringResource(R.string.search_eyebrow),
            title = stringResource(R.string.search_title),
            body = stringResource(R.string.search_body)
        )
        WorkbenchTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            label = stringResource(R.string.search_field_label)
        )
        WorkbenchButton(stringResource(R.string.common_search), viewModel::runSearch, primary = true, modifier = Modifier.fillMaxWidth())
        if (state.searchResults.isEmpty()) {
            EmptyWorkbenchCard(stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_body))
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
            eyebrow = stringResource(R.string.quiz_editor_eyebrow),
            title = stringResource(R.string.quiz_editor_title),
            body = stringResource(
                R.string.quiz_editor_body,
                state.selectedNode?.title ?: stringResource(R.string.quiz_editor_default_link)
            )
        )
        WorkbenchTextField(state.quizPrompt, viewModel::setQuizPrompt, stringResource(R.string.quiz_prompt_field), minLines = 3)
        WorkbenchTextField(state.quizAnswer, viewModel::setQuizAnswer, stringResource(R.string.quiz_answer_field), minLines = 3)
        WorkbenchTextField(state.quizExplanation, viewModel::setQuizExplanation, stringResource(R.string.quiz_explanation_field), minLines = 4)
        ToolbarRow {
            WorkbenchButton(stringResource(R.string.quiz_save_button), viewModel::saveQuiz, primary = true)
            WorkbenchButton(stringResource(R.string.common_cancel), viewModel::cancelEditor)
        }
    }
}

@Composable
private fun ReviewScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val quiz = state.selectedQuiz
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = stringResource(R.string.review_eyebrow),
            title = stringResource(R.string.review_title),
            body = stringResource(R.string.review_body, state.dueQuizzes.size, state.quizzes.size)
        )
        if (quiz == null) {
            EmptyWorkbenchCard(
                title = stringResource(R.string.review_empty_title),
                body = stringResource(R.string.review_empty_body)
            )
            return
        }
        WorkbenchCard(accent = true) {
            Eyebrow(quiz.source.name)
            Text(quiz.prompt, color = WorkbenchColors.InkStrong, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
            if (!state.quizAnswerVisible) {
                Text(stringResource(R.string.review_answer_hidden), color = WorkbenchColors.Muted, fontSize = 14.sp)
                WorkbenchButton(stringResource(R.string.review_reveal_answer), viewModel::revealCurrentQuizAnswer, primary = true, modifier = Modifier.fillMaxWidth())
            }
            AnimatedVisibility(
                visible = state.quizAnswerVisible,
                enter = fadeIn(tween(WorkbenchMotion.CompactFadeMillis)) + expandVertically(tween(WorkbenchMotion.CompactExpandMillis)),
                exit = fadeOut(tween(WorkbenchMotion.CompactFadeMillis)) + shrinkVertically(tween(WorkbenchMotion.CompactExpandMillis))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    AnswerBlock(quiz)
                    ToolbarRow {
                        WorkbenchButton(stringResource(R.string.common_edit), { viewModel.editQuiz(quiz) })
                        WorkbenchButton(stringResource(R.string.assistant_improve_object), { viewModel.assistantActions.reviseQuiz(quiz) })
                        WorkbenchButton(stringResource(R.string.review_again), { viewModel.answerCurrentQuiz(ReviewRating.Again) }, danger = true)
                        WorkbenchButton(stringResource(R.string.review_hard), { viewModel.answerCurrentQuiz(ReviewRating.Hard) })
                        WorkbenchButton(stringResource(R.string.review_good), { viewModel.answerCurrentQuiz(ReviewRating.Good) }, primary = true)
                    }
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
        Eyebrow(stringResource(R.string.review_answer_eyebrow))
        Text(quiz.answer, color = WorkbenchColors.InkStrong, fontSize = 17.sp, lineHeight = 24.sp)
        if (quiz.explanation.isNotBlank()) {
            Text(quiz.explanation, color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}
