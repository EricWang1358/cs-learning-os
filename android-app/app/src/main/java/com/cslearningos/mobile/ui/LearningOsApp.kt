@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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

private val CardShape = RoundedCornerShape(16.dp)
private const val ReviewAllAreasKey = "__all_review_areas__"

fun selectedBottomTabFor(screen: AppScreen): AppScreen =
    selectedBottomRouteFor(screen.toAppRoute()).toAppScreen()

private fun routeNavigationRank(route: AppRoute): Int {
    val tabRank = when (selectedBottomRouteFor(route)) {
        AppRoute.Home -> 0
        AppRoute.Capture -> 1
        AppRoute.Library -> 2
        AppRoute.Review -> 3
        AppRoute.More -> 4
        else -> 0
    }
    val detailRank = when (route) {
        selectedBottomRouteFor(route) -> 0
        else -> 1
    }
    return tabRank * 10 + detailRank
}

private fun hideGlobalStatusBanner(route: AppRoute, state: LearningUiState): Boolean =
    route == AppRoute.Review && state.reviewedQuiz != null

private fun findAreaByReviewKey(areas: List<com.cslearningos.mobile.data.AreaEntity>, reviewAreaKey: String?) =
    reviewAreaKey?.let { key -> areas.firstOrNull { it.id == key || it.slug == key } }

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
                    .background(WorkbenchColors.Surface)
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
    val listState = rememberLazyListState()
    LaunchedEffect(shellState.route) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(
        shellState.route,
        state.reviewSetupVisible,
        state.selectedQuiz?.id,
        state.reviewedQuiz?.id,
        state.quizAnswerVisible
    ) {
        if (shellState.route == AppRoute.Review) {
            listState.animateScrollToItem(0)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = shellState.route,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val direction = if (routeNavigationRank(targetState) >= routeNavigationRank(initialState)) 1 else -1
                slideInHorizontally(tween(WorkbenchMotion.NavigationMillis)) { fullWidth -> fullWidth * direction } togetherWith
                    slideOutHorizontally(tween(WorkbenchMotion.NavigationMillis)) { fullWidth -> -fullWidth * direction }
            },
            label = "portrait-page-route"
        ) { route ->
            if (route == AppRoute.Assistant) {
                AssistantScreen(state = state, viewModel = viewModel)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 116.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        if (route != AppRoute.Home) {
                            CompactBrandBlock(route = route, state = state)
                        }
                    }
                    if (!hideGlobalStatusBanner(route, state) && shellState.message != null) {
                        item { StatusBanner(shellState.message) }
                    }
                    if (state.notices.isNotEmpty()) {
                        item { NoticeTray(state = state, viewModel = viewModel) }
                    }
                    item {
                        ScreenContent(route = route, state = state, viewModel = viewModel, isDetailPane = true)
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
        AnimatedVisibility(
            visible = shellState.route != AppRoute.Assistant,
            enter = fadeIn(tween(WorkbenchMotion.StateMillis)),
            exit = fadeOut(tween(WorkbenchMotion.StateMillis)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MobileBottomNav(
                currentRoute = shellState.route,
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
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
            if (!hideGlobalStatusBanner(shellState.route, state)) {
                item { StatusBanner(shellState.message) }
            }
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
                if (!hideGlobalStatusBanner(shellState.route, state)) {
                    item { StatusBanner(shellState.message) }
                }
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
                fontWeight = FontWeight.ExtraBold
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
                fontWeight = FontWeight.ExtraBold,
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
        Text(label, color = WorkbenchColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = WorkbenchColors.InkStrong, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
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
            .clip(RoundedCornerShape(28.dp))
            .background(WorkbenchColors.SurfaceCard.copy(alpha = 0.97f))
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
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) WorkbenchColors.AccentContainer else WorkbenchColors.Surface.copy(alpha = 0f),
        animationSpec = tween(WorkbenchMotion.StateMillis, easing = FastOutSlowInEasing),
        label = "bottom-nav-indicator"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) WorkbenchColors.OnAccentContainer else WorkbenchColors.Muted,
        animationSpec = tween(WorkbenchMotion.StateMillis, easing = FastOutSlowInEasing),
        label = "bottom-nav-icon"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) WorkbenchColors.InkStrong else WorkbenchColors.Muted,
        animationSpec = tween(WorkbenchMotion.StateMillis, easing = FastOutSlowInEasing),
        label = "bottom-nav-label"
    )
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .widthIn(min = 52.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(indicatorColor),
            contentAlignment = Alignment.Center
        ) {
            Box {
                Icon(
                    imageVector = item.icon(),
                    contentDescription = stringResource(item.contentDescriptionResId),
                    tint = iconColor,
                    modifier = Modifier.height(21.dp)
                )
                if (item.screen == AppScreen.Review && dueCount > 0) {
                    Badge(
                        containerColor = WorkbenchColors.Danger,
                        contentColor = WorkbenchColors.Surface,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(dueCount.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(
            text = stringResource(item.labelResId),
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
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
        AppRoute.AssistantGuide -> AssistantGuideScreen(state, viewModel)
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
            AppRoute.AssistantGuide -> AssistantGuideScreen(state, viewModel)
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
            fontWeight = FontWeight.ExtraBold,
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
                Text(title, color = WorkbenchColors.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
        val openQuestionCount = remember(state.readerQuestions, node.id) {
            state.readerQuestions.count { it.nodeId == node.id }
        }
        val readerQuestionLabel = readerQuestionButtonLabel(
            openQuestionCount = openQuestionCount,
            expanded = state.readerQuestionPanelExpanded,
            context = LocalContext.current
        )
        ToolbarRow {
            WorkbenchButton(stringResource(R.string.common_back), viewModel::showHome)
            WorkbenchButton(stringResource(R.string.reader_edit_mode_button), { viewModel.editNode(node) }, primary = true)
            WorkbenchButton(stringResource(R.string.assistant_improve_object), { viewModel.assistantActions.reviseNode(node) })
            WorkbenchMenuButton(
                text = stringResource(R.string.nav_more_label),
                options = listOf(
                    WorkbenchMenuOption(
                        text = stringResource(R.string.common_delete),
                        onClick = viewModel::deleteSelectedNode
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
            enter = fadeIn(tween(WorkbenchMotion.StateMillis)) + expandVertically(tween(WorkbenchMotion.DisclosureMillis)),
            exit = fadeOut(tween(WorkbenchMotion.StateMillis)) + shrinkVertically(tween(WorkbenchMotion.DisclosureMillis))
        ) {
            ReaderQuestionCapture(state = state, viewModel = viewModel, nodeId = node.id)
        }
        MarkdownRenderer(markdown = node.markdownBody)
    }
}

@Composable
private fun ReaderQuestionCapture(state: LearningUiState, viewModel: LearningViewModel, nodeId: String) {
    val openQuestions = remember(state.readerQuestions, nodeId) {
        state.readerQuestions.filter { it.nodeId == nodeId }
    }
    WorkbenchCard(accent = openQuestions.isNotEmpty()) {
        Eyebrow(stringResource(R.string.reader_questions_eyebrow))
        Text(
            text = stringResource(R.string.reader_questions_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
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
            Text(stringResource(R.string.reader_open_question_count, 0), color = WorkbenchColors.InkStrong, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.reader_open_question_count, openQuestions.size),
                    color = WorkbenchColors.InkStrong,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
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
    val areas = remember(state.areas) { state.areas.filter { it.deletedAt == null } }
    val selectedArea = remember(areas, state.editorAreaId) { areas.firstOrNull { it.id == state.editorAreaId } }
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
        StatusBanner(state.message)
        ToolbarRow {
            WorkbenchButton(stringResource(R.string.editor_save_markdown), viewModel::saveNode, primary = true)
            WorkbenchButton(stringResource(R.string.assistant_improve_object), viewModel::reviseEditorDraftWithAssistant)
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
        Text(result.title, color = WorkbenchColors.InkStrong, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(result.snippet, color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun QuizEditorScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val context = LocalContext.current
    val linkedNodeId = state.quizNodeIdForSave()
    val areas = remember(state.areas) { state.areas.filter { it.deletedAt == null } }
    val selectedArea = remember(areas, state.quizAreaId, state.selectedQuiz?.area, state.quizEditorId) {
        areas.firstOrNull { it.id == state.quizAreaId }
            ?: state.selectedQuiz
                ?.area
                ?.takeIf { state.quizEditorId != null }
                ?.let { areaKey -> areas.firstOrNull { it.id == areaKey || it.slug == areaKey } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailHeading(
            eyebrow = stringResource(R.string.quiz_editor_eyebrow),
            title = stringResource(R.string.quiz_editor_title),
            body = stringResource(
                R.string.quiz_editor_body,
                state.selectedNode?.title ?: stringResource(R.string.quiz_editor_default_link)
            )
        )
        if (linkedNodeId == null && areas.isNotEmpty()) {
            WorkbenchMenuButton(
                text = selectedArea?.let { displayAreaName(context, it) }
                    ?: stringResource(R.string.quiz_editor_choose_area),
                options = areas.map { area ->
                    WorkbenchMenuOption(displayAreaName(context, area)) {
                        viewModel.setQuizAreaId(area.id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                expandToContainer = true
            )
        }
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
    val context = LocalContext.current
    if (state.reviewSetupVisible) {
        val summaries = remember(state.areas, state.dueQuizzes, state.quizzes) {
            buildReviewAreaSummaries(state.areas, state.dueQuizzes, state.quizzes)
        }
        var expandedAreaKey by remember { mutableStateOf<String?>(null) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.review_select_area_title), color = WorkbenchColors.InkStrong, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    stringResource(R.string.review_select_area_summary, state.dueQuizzes.size, state.quizzes.size),
                    color = WorkbenchColors.Muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            summaries.forEach { summary ->
                val label = findAreaByReviewKey(state.areas, summary.areaId)
                    ?.let { displayAreaName(context, it) }
                    ?: stringResource(R.string.review_all_areas)
                val areaKey = summary.areaId ?: ReviewAllAreasKey
                val expanded = expandedAreaKey == areaKey
                val areaCards = remember(state.quizzes, summary.areaId) {
                    reviewCardsForArea(state.quizzes, summary.areaId)
                }
                val dueIds = remember(state.dueQuizzes, summary.areaId) {
                    reviewCardsForArea(state.dueQuizzes, summary.areaId).mapTo(mutableSetOf()) { it.id }
                }
                ReviewAreaChoiceRow(
                    title = label,
                    detail = stringResource(R.string.review_area_due_total, summary.dueCount, summary.totalCount),
                    selected = expanded,
                    onClick = { expandedAreaKey = if (expanded) null else areaKey }
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(WorkbenchMotion.StateMillis)) + expandVertically(tween(WorkbenchMotion.DisclosureMillis)),
                    exit = fadeOut(tween(WorkbenchMotion.StateMillis)) + shrinkVertically(tween(WorkbenchMotion.DisclosureMillis))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (areaCards.isEmpty()) {
                            Text(
                                stringResource(R.string.review_area_empty_cards),
                                color = WorkbenchColors.Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        } else {
                            areaCards.forEach { quiz ->
                                ReviewQuizChoiceRow(
                                    quiz = quiz,
                                    due = quiz.id in dueIds,
                                    onClick = { viewModel.startReviewForQuiz(quiz, summary.areaId) }
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }
    state.reviewedQuiz?.let { quiz ->
        ReviewDetailScreen(
            quiz = quiz,
            feedbackMessage = state.message,
            onRetry = viewModel::retryReviewedQuiz,
            onPrevious = { viewModel.navigateReviewPrompt(-1) },
            onNext = { viewModel.navigateReviewPrompt(1) }
        )
        return
    }
    val quiz = state.selectedQuiz
    val reviewCards = remember(state.quizzes, state.reviewAreaId) {
        reviewCardsForArea(state.quizzes, state.reviewAreaId)
    }
    val dueCardsForArea = remember(state.dueQuizzes, state.reviewAreaId) {
        state.dueQuizzes.filter { state.reviewAreaId == null || it.area == state.reviewAreaId }
    }
    val progress = remember(quiz?.id, reviewCards) { reviewProgress(quiz?.id, reviewCards) }
    val areaLabel = findAreaByReviewKey(state.areas, state.reviewAreaId)
        ?.let { displayAreaName(context, it) }
        ?: stringResource(R.string.review_all_areas)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = stringResource(R.string.review_title),
            title = stringResource(R.string.review_title),
            body = if (quiz != null && progress.total > 0) {
                stringResource(R.string.review_progress_body, progress.current, progress.total, areaLabel)
            } else {
                stringResource(R.string.review_body, dueCardsForArea.size, reviewCards.size)
            }
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
            Text(quiz.prompt, color = WorkbenchColors.InkStrong, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
            if (!state.quizAnswerVisible) {
                Text(stringResource(R.string.review_prompt_hint), color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)
                WorkbenchButton(stringResource(R.string.review_reveal_answer), viewModel::revealCurrentQuizAnswer, primary = true, modifier = Modifier.fillMaxWidth())
            }
            AnimatedVisibility(
                visible = state.quizAnswerVisible,
                enter = fadeIn(tween(WorkbenchMotion.StateMillis)) + expandVertically(tween(WorkbenchMotion.DisclosureMillis)),
                exit = fadeOut(tween(WorkbenchMotion.StateMillis)) + shrinkVertically(tween(WorkbenchMotion.DisclosureMillis))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    AnswerBlock(quiz)
                    ToolbarRow {
                        WorkbenchMenuButton(
                            text = stringResource(R.string.common_edit),
                            options = listOf(
                                WorkbenchMenuOption(
                                    text = stringResource(R.string.review_manual_edit),
                                    onClick = { viewModel.editQuiz(quiz) }
                                ),
                                WorkbenchMenuOption(
                                    text = stringResource(R.string.assistant_improve_object),
                                    onClick = { viewModel.assistantActions.reviseQuiz(quiz) }
                                )
                            )
                        )
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
private fun ReviewDetailScreen(
    quiz: QuizItemEntity,
    feedbackMessage: UiText?,
    onRetry: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = stringResource(R.string.review_eyebrow),
            title = stringResource(R.string.review_detail_title),
            body = stringResource(R.string.review_detail_body)
        )
        WorkbenchCard(accent = true) {
            ReviewFeedbackCard(feedbackMessage)
            Eyebrow(quiz.source.name)
            Text(quiz.prompt, color = WorkbenchColors.InkStrong, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
            AnswerBlock(quiz)
            ToolbarRow {
                WorkbenchButton(stringResource(R.string.review_retry), onRetry)
                WorkbenchButton(stringResource(R.string.review_previous), onPrevious)
                WorkbenchButton(stringResource(R.string.review_next), onNext, primary = true)
            }
        }
    }
}

@Composable
private fun ReviewAreaChoiceRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) WorkbenchColors.Accent else WorkbenchColors.SurfaceCard.copy(alpha = 0.78f)
    val titleColor = if (selected) WorkbenchColors.SurfaceSoft else WorkbenchColors.InkStrong
    val detailColor = if (selected) WorkbenchColors.SurfaceSoft.copy(alpha = 0.78f) else WorkbenchColors.Muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(
                BorderStroke(1.dp, if (selected) WorkbenchColors.Accent else WorkbenchColors.Line.copy(alpha = 0.72f)),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, color = detailColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReviewQuizChoiceRow(
    quiz: QuizItemEntity,
    due: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WorkbenchColors.SurfaceCard.copy(alpha = 0.62f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line.copy(alpha = 0.50f)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            quiz.prompt,
            color = WorkbenchColors.InkStrong,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(if (due) R.string.review_card_due_badge else R.string.review_card_total_badge),
            color = if (due) WorkbenchColors.AccentStrong else WorkbenchColors.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ReviewFeedbackCard(message: UiText?) {
    val resolved = message.resolve() ?: stringResource(R.string.review_feedback_body)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WorkbenchColors.Success.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, WorkbenchColors.Success.copy(alpha = 0.28f)), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(R.string.review_feedback_title), color = WorkbenchColors.Success, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Text(resolved, color = WorkbenchColors.InkStrong, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun AnswerBlock(quiz: QuizItemEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(WorkbenchColors.Surface.copy(alpha = 0.56f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line.copy(alpha = 0.78f)), CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow(stringResource(R.string.review_answer_summary_eyebrow))
            Text(quiz.answer, color = WorkbenchColors.InkStrong, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        if (quiz.explanation.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WorkbenchColors.SurfaceCard.copy(alpha = 0.64f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Eyebrow(stringResource(R.string.review_explanation_eyebrow))
                Text(quiz.explanation, color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}
