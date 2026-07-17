@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cslearningos.mobile.R
import com.cslearningos.mobile.feature.assistant.domain.AssistantReviewSession
import com.cslearningos.mobile.feature.assistant.ui.AssistantMessage
import com.cslearningos.mobile.feature.assistant.ui.AssistantMessageAction
import com.cslearningos.mobile.feature.assistant.ui.AssistantMessageRole
import com.cslearningos.mobile.feature.assistant.ui.AssistantUiState

@Composable
fun AssistantScreen(
    state: LearningUiState,
    viewModel: LearningViewModel,
    modifier: Modifier = Modifier
) {
    val assistant = state.assistant
    val listState = rememberLazyListState()
    val lastMessage = assistant.messages.lastOrNull()
    LaunchedEffect(lastMessage?.id) {
        if (assistant.messages.isNotEmpty()) {
            listState.scrollToItem(assistant.messages.lastIndex)
        }
    }
    LaunchedEffect(lastMessage?.body?.length, lastMessage?.isStreaming) {
        val lastIndex = assistant.messages.lastIndex
        if (lastIndex < 0) return@LaunchedEffect
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisibleIndex >= lastIndex - 1) {
            listState.scrollToItem(lastIndex)
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WorkbenchColors.Surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AssistantTopBar(
                onBack = viewModel::showHome,
                onNewChat = viewModel.assistantActions::newChat,
                onHistory = viewModel.assistantActions::showHistory,
                historyEnabled = !assistant.isBusy
            )
            StatusBanner(state.message)
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(AssistantUiTokens.ListPadding),
                verticalArrangement = Arrangement.spacedBy(AssistantUiTokens.ListItemSpacing)
            ) {
                if (assistant.messages.isEmpty()) {
                    item {
                        AssistantEmptyState(
                            onQuickMessage = viewModel.assistantActions::prefillQuickPrompt,
                            onStartReview = viewModel.assistantActions::startInterviewReview
                        )
                    }
                }
                items(assistant.messages, key = { it.id }) { message ->
                    AssistantMessageBubble(
                        message = message,
                        onOpenCitation = viewModel.assistantActions::openCitation,
                        onOpenDraft = { viewModel.assistantActions.openDraft(message.id) },
                        onOpenQuizDraft = { viewModel.assistantActions.openQuizDraft(message.id) },
                        onOpenCaptureDraft = { viewModel.assistantActions.openCaptureDraft(message.id) },
                        onSaveCapture = { viewModel.assistantActions.saveReplyToCapture(message.id) },
                        onRetry = { viewModel.assistantActions.retryMessage(message.id) },
                        onOpenDailyReview = viewModel.assistantActions::openDailyReview,
                        onConfigureAi = viewModel::showAiServiceSettings,
                        onAgentActionReply = viewModel.assistantActions::replyToAgentAction,
                        onConfirmAreaMove = viewModel.assistantActions::confirmAreaMove,
                        areas = state.areas
                    )
                }
            }
            AssistantQuickBar(
                assistant = assistant,
                onQuickMessage = viewModel.assistantActions::prefillQuickPrompt,
                onStartReview = viewModel.assistantActions::startInterviewReview,
                onExitMode = viewModel.assistantActions::clearAssistModes
            )
            AssistantComposer(value = assistant.input, onValueChange = viewModel.assistantActions::setInput, onSend = viewModel.assistantActions::sendMessage, onStop = viewModel.assistantActions::cancelReply, enabled = !assistant.isBusy, modifier = Modifier.imePadding())
        }
        AnimatedVisibility(
            visible = assistant.historyVisible,
            enter = fadeIn(tween(WorkbenchMotion.StateMillis)) + slideInHorizontally(tween(WorkbenchMotion.NavigationMillis)) { -it },
            exit = fadeOut(tween(WorkbenchMotion.StateMillis)) + slideOutHorizontally(tween(WorkbenchMotion.NavigationMillis)) { -it }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WorkbenchColors.Ink.copy(alpha = 0.24f))
                        .clickable(onClick = viewModel.assistantActions::hideHistory)
                )
                AssistantHistoryDrawer(
                    history = assistant.conversationHistory,
                    onDismiss = viewModel.assistantActions::hideHistory,
                    onOpen = viewModel.assistantActions::openHistoryConversation,
                    onDelete = viewModel.assistantActions::deleteHistoryConversation,
                    onNewChat = viewModel.assistantActions::newChat,
                    canOpenHistory = !assistant.isBusy
                )
            }
        }
    }
}

@Composable
private fun AssistantEmptyState(
    onQuickMessage: (String) -> Unit,
    onStartReview: () -> Unit
) {
    val explainLabel = stringResource(R.string.assistant_quick_explain)
    val draftLabel = stringResource(R.string.assistant_quick_draft)
    val reviewLabel = stringResource(R.string.assistant_quick_review)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AssistantUiTokens.EmptyStateMinHeight)
            .padding(top = AssistantUiTokens.EmptyStateTopPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AssistantUiTokens.EmptyStateSpacing, Alignment.CenterVertically)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(WorkbenchColors.AccentContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = WorkbenchColors.OnAccentContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = stringResource(R.string.assistant_empty_title),
                color = WorkbenchColors.InkStrong,
                fontSize = AssistantUiTokens.EmptyStateTitleSize,
                fontWeight = FontWeight.Bold,
                lineHeight = AssistantUiTokens.EmptyStateTitleLineHeight,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.assistant_empty_body),
                color = WorkbenchColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AssistantUiTokens.QuickActionGap)
        ) {
            AssistantSuggestionRow(
                icon = Icons.Filled.Lightbulb,
                title = explainLabel,
                body = stringResource(R.string.assistant_quick_explain_body),
                onClick = { onQuickMessage("$explainLabel: ") }
            )
            AssistantSuggestionRow(
                icon = Icons.Filled.Create,
                title = draftLabel,
                body = stringResource(R.string.assistant_quick_draft_body),
                onClick = { onQuickMessage(draftLabel) }
            )
            AssistantSuggestionRow(
                icon = Icons.Filled.CheckCircle,
                title = reviewLabel,
                body = stringResource(R.string.assistant_quick_review_body),
                onClick = onStartReview
            )
        }
    }
}

@Composable
private fun AssistantSuggestionRow(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AssistantUiTokens.QuickActionHeight)
            .clip(RoundedCornerShape(AssistantUiTokens.QuickActionCornerRadius))
            .background(WorkbenchColors.SurfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(WorkbenchColors.AccentContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WorkbenchColors.OnAccentContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = WorkbenchColors.InkStrong,
                fontSize = AssistantUiTokens.QuickActionTextSize,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                color = WorkbenchColors.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = WorkbenchColors.Muted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AssistantQuickBar(
    assistant: AssistantUiState,
    onQuickMessage: (String) -> Unit,
    onStartReview: () -> Unit,
    onExitMode: () -> Unit
) {
    val reviewModeText = when (assistant.reviewSession) {
        AssistantReviewSession.AwaitingTopic -> stringResource(R.string.assistant_mode_review_topic)
        is AssistantReviewSession.AwaitingAnswer -> stringResource(R.string.assistant_mode_review_answer)
        else -> null
    }
    val draftModeActive = reviewModeText == null && assistant.editTarget != null
    val modeActive = reviewModeText != null || draftModeActive
    val chipsVisible = assistant.messages.isNotEmpty() && !assistant.isBusy && !modeActive
    if (!modeActive && !chipsVisible) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WorkbenchColors.Surface)
            .padding(horizontal = AssistantUiTokens.ComposerPadding)
    ) {
        if (modeActive) {
            AssistantModeIndicator(
                text = reviewModeText ?: stringResource(R.string.assistant_mode_draft),
                isReview = reviewModeText != null,
                onExit = onExitMode
            )
        }
        if (chipsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val explainLabel = stringResource(R.string.assistant_quick_explain)
                val draftLabel = stringResource(R.string.assistant_quick_draft)
                AssistantQuickChip(
                    icon = Icons.Filled.Lightbulb,
                    label = explainLabel,
                    modifier = Modifier.weight(1f),
                    onClick = { onQuickMessage("$explainLabel: ") }
                )
                AssistantQuickChip(
                    icon = Icons.Filled.Create,
                    label = draftLabel,
                    modifier = Modifier.weight(1f),
                    onClick = { onQuickMessage(draftLabel) }
                )
                AssistantQuickChip(
                    icon = Icons.Filled.CheckCircle,
                    label = stringResource(R.string.assistant_quick_review),
                    modifier = Modifier.weight(1f),
                    onClick = onStartReview
                )
            }
        }
    }
}

@Composable
private fun AssistantQuickChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = AssistantUiTokens.ChipHeight)
            .clip(RoundedCornerShape(AssistantUiTokens.ChipHeight / 2))
            .background(WorkbenchColors.SurfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = WorkbenchColors.AccentStrong,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            color = WorkbenchColors.InkStrong,
            fontSize = AssistantUiTokens.ChipTextSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun AssistantModeIndicator(
    text: String,
    isReview: Boolean,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(AssistantUiTokens.ChipHeight / 2))
            .background(WorkbenchColors.AccentContainer)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isReview) Icons.Filled.CheckCircle else Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = WorkbenchColors.OnAccentContainer,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = text,
            color = WorkbenchColors.OnAccentContainer,
            fontSize = AssistantUiTokens.ChipTextSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        val exitDescription = stringResource(R.string.assistant_mode_exit)
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .size(26.dp)
                .semantics { contentDescription = exitDescription }
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = WorkbenchColors.OnAccentContainer,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: AssistantMessage,
    onOpenCitation: (String, String) -> Unit,
    onOpenDraft: () -> Unit,
    onOpenQuizDraft: () -> Unit,
    onOpenCaptureDraft: () -> Unit,
    onSaveCapture: () -> Unit,
    onRetry: () -> Unit,
    onOpenDailyReview: () -> Unit,
    onConfigureAi: () -> Unit,
    onAgentActionReply: (String) -> Unit,
    onConfirmAreaMove: (com.cslearningos.mobile.feature.assistant.domain.AssistantAgentInteraction.MoveNodeArea) -> Unit,
    areas: List<com.cslearningos.mobile.data.AreaEntity>
) {
    val isUser = message.role == AssistantMessageRole.User
    val shape = if (isUser) {
        RoundedCornerShape(
            topStart = AssistantUiTokens.MessageCornerRadius,
            topEnd = AssistantUiTokens.MessageCornerRadius,
            bottomEnd = AssistantUiTokens.MessageTailRadius,
            bottomStart = AssistantUiTokens.MessageCornerRadius
        )
    } else {
        RoundedCornerShape(
            topStart = AssistantUiTokens.MessageCornerRadius,
            topEnd = AssistantUiTokens.MessageCornerRadius,
            bottomEnd = AssistantUiTokens.MessageCornerRadius,
            bottomStart = AssistantUiTokens.MessageTailRadius
        )
    }
    val containerColor = if (isUser) {
        WorkbenchColors.AccentContainer
    } else {
        WorkbenchColors.SurfaceCard
    }
    val contentColor = if (isUser) {
        WorkbenchColors.OnAccentContainer
    } else {
        WorkbenchColors.InkStrong
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(
                    max = if (isUser) {
                        AssistantUiTokens.UserMessageMaxWidth
                    } else {
                        AssistantUiTokens.AssistantMessageMaxWidth
                    }
                )
                .clip(shape)
                .background(containerColor)
                .padding(AssistantUiTokens.MessagePadding),
            verticalArrangement = Arrangement.spacedBy(AssistantUiTokens.MessageItemSpacing)
        ) {
            if (!isUser && !message.isStreaming && message.body.isNotBlank()) {
                AssistantMessageBody(markdown = message.body)
            } else if (message.isStreaming && message.body.isBlank()) {
                AssistantThinkingIndicator()
            } else {
                Text(
                    text = message.body.ifBlank { stringResource(R.string.assistant_streaming) },
                    color = contentColor,
                    fontSize = AssistantUiTokens.MessageTextSize,
                    lineHeight = AssistantUiTokens.MessageLineHeight
                )
            }
            if (message.citations.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.assistant_sources),
                    color = if (isUser) contentColor.copy(alpha = 0.72f) else WorkbenchColors.Muted,
                    fontSize = AssistantUiTokens.SourceLabelSize,
                    fontWeight = FontWeight.Bold
                )
                message.citations.forEach { citation ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AssistantUiTokens.CitationCornerRadius))
                            .background(WorkbenchColors.Surface.copy(alpha = AssistantUiTokens.CitationSurfaceAlpha))
                            .clickable { onOpenCitation(citation.type, citation.id) }
                            .padding(
                                horizontal = AssistantUiTokens.CitationHorizontalPadding,
                                vertical = AssistantUiTokens.CitationVerticalPadding
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = citation.title,
                            color = if (isUser) contentColor else WorkbenchColors.AccentStrong,
                            fontSize = AssistantUiTokens.CitationTextSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                    }
                }
            }
            val draftAction = message.action as? AssistantMessageAction.OpenEditableDraft
            if (draftAction != null && draftAction.nodeId == null) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val areaLabel = areas.firstOrNull { it.id == draftAction.areaId }
                    ?.let { area -> displayAreaName(context, area) }
                    ?: stringResource(R.string.editor_choose_area)
                Text(
                    text = stringResource(R.string.assistant_placement, areaLabel),
                    color = WorkbenchColors.Muted,
                    fontSize = AssistantUiTokens.SourceLabelSize,
                    fontWeight = FontWeight.Bold
                )
                draftAction.placementReason?.let { reason ->
                    Text(
                        text = stringResource(R.string.assistant_placement_reason, reason),
                        color = WorkbenchColors.Muted,
                        fontSize = AssistantUiTokens.SourceLabelSize,
                        lineHeight = 17.sp
                    )
                }
            }
            when (message.action) {
                is AssistantMessageAction.AgentInteraction -> AssistantAgentInteractionCard(
                    interaction = message.action.interaction,
                    onReply = onAgentActionReply,
                    onConfirmAreaMove = onConfirmAreaMove
                )

                is AssistantMessageAction.OpenEditableDraft -> WorkbenchButton(
                    text = stringResource(R.string.assistant_open_draft),
                    onClick = onOpenDraft,
                    primary = true
                )

                is AssistantMessageAction.OpenEditableQuizDraft -> WorkbenchButton(
                    text = stringResource(R.string.assistant_open_draft),
                    onClick = onOpenQuizDraft,
                    primary = true
                )

                is AssistantMessageAction.OpenNewQuizDraft -> WorkbenchButton(
                    text = stringResource(R.string.assistant_open_draft),
                    onClick = onOpenQuizDraft,
                    primary = true
                )

                is AssistantMessageAction.OpenEditableCaptureDraft -> WorkbenchButton(
                    text = stringResource(R.string.assistant_open_draft),
                    onClick = onOpenCaptureDraft,
                    primary = true
                )

                is AssistantMessageAction.SaveCapture -> WorkbenchButton(
                    text = stringResource(R.string.assistant_save_capture),
                    onClick = onSaveCapture
                )

                is AssistantMessageAction.RetryRequest -> WorkbenchButton(
                    text = stringResource(R.string.assistant_retry),
                    onClick = onRetry,
                    primary = true
                )

                AssistantMessageAction.OpenDailyReview -> WorkbenchButton(
                    text = stringResource(R.string.assistant_open_daily_review),
                    onClick = onOpenDailyReview,
                    primary = true
                )

                AssistantMessageAction.ConfigureAi -> WorkbenchButton(
                    text = stringResource(R.string.assistant_configure_ai),
                    onClick = onConfigureAi,
                    primary = true
                )

                null -> Unit
            }
            if (message.captureSuggestion != null) {
                WorkbenchButton(
                    text = stringResource(R.string.assistant_save_capture),
                    onClick = onSaveCapture
                )
            }
        }
    }
}

@Composable
private fun AssistantThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "assistant-thinking")
    val alpha by transition.animateFloat(
        initialValue = 0.32f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(860, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "assistant-thinking-alpha"
    )
    Text(
        text = stringResource(R.string.assistant_streaming),
        color = WorkbenchColors.Muted,
        fontSize = AssistantUiTokens.MessageTextSize,
        lineHeight = AssistantUiTokens.MessageLineHeight,
        modifier = Modifier.alpha(alpha)
    )
}

internal object AssistantUiTokens {
    val ListPadding = 18.dp
    val ListItemSpacing = 12.dp

    val HeaderHorizontalPadding = 16.dp
    val HeaderVerticalPadding = 4.dp
    val HeaderItemSpacing = 4.dp
    val HeaderTitleSize = 17.sp

    val EmptyStateMinHeight = 480.dp
    val EmptyStateTopPadding = 20.dp
    val EmptyStateSpacing = 28.dp
    val EmptyStateTitleSize = 24.sp
    val EmptyStateTitleLineHeight = 31.sp
    val QuickActionGap = 10.dp
    val QuickActionHeight = 68.dp
    val QuickActionCornerRadius = 18.dp
    val QuickActionTextSize = 15.sp
    val ChipHeight = 36.dp
    val ChipTextSize = 12.sp

    val MessageCornerRadius = 20.dp
    val MessageTailRadius = 6.dp
    val MessagePadding = 14.dp
    val MessageItemSpacing = 8.dp
    val UserMessageMaxWidth = 296.dp
    val AssistantMessageMaxWidth = 336.dp
    val MessageTextSize = 15.sp
    val MessageLineHeight = 23.sp
    val SourceLabelSize = 11.sp
    val CitationTextSize = 13.sp
    val CitationCornerRadius = 12.dp
    val CitationHorizontalPadding = 12.dp
    val CitationVerticalPadding = 9.dp
    const val CitationSurfaceAlpha = 0.6f

    val ComposerPadding = 12.dp
    val ComposerItemSpacing = 10.dp
    val ComposerCornerRadius = 28.dp
    val ComposerMinHeight = 52.dp
    val InputHorizontalPadding = 16.dp
    const val InputHintAlpha = 0.62f
    val InputTextSize = 15.sp
    val InputLineHeight = 22.sp
    val SendButtonSize = 44.dp
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Assistant · Quick Bar Day")
@Composable
private fun AssistantQuickBarDayPreview() {
    WorkbenchTheme(appearanceMode = AppearanceMode.Day, dynamicColor = false) {
        Column(modifier = Modifier.background(WorkbenchColors.Surface)) {
            AssistantQuickBar(
                assistant = AssistantUiState(
                    messages = listOf(
                        AssistantMessage(id = "u1", role = AssistantMessageRole.User, body = "什么是缺页中断？")
                    )
                ),
                onQuickMessage = {},
                onStartReview = {},
                onExitMode = {}
            )
            AssistantQuickBar(
                assistant = AssistantUiState(
                    messages = listOf(
                        AssistantMessage(id = "u1", role = AssistantMessageRole.User, body = "虚拟内存")
                    ),
                    reviewSession = AssistantReviewSession.AwaitingTopic
                ),
                onQuickMessage = {},
                onStartReview = {},
                onExitMode = {}
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Assistant · Empty Day")
@Composable
private fun AssistantEmptyDayPreview() {
    WorkbenchTheme(appearanceMode = AppearanceMode.Day, dynamicColor = false) {
        Column(modifier = Modifier.background(WorkbenchColors.Surface)) {
            AssistantEmptyState(onQuickMessage = {}, onStartReview = {})
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Assistant · Bubbles Night")
@Composable
private fun AssistantBubblesNightPreview() {
    WorkbenchTheme(appearanceMode = AppearanceMode.Night, dynamicColor = false) {
        Column(
            modifier = Modifier
                .background(WorkbenchColors.Surface)
                .padding(AssistantUiTokens.ListPadding),
            verticalArrangement = Arrangement.spacedBy(AssistantUiTokens.ListItemSpacing)
        ) {
            AssistantMessageBubble(
                message = AssistantMessage(
                    id = "u1",
                    role = AssistantMessageRole.User,
                    body = "为什么 TLB miss 会触发页表遍历？"
                ),
                onOpenCitation = { _, _ -> },
                onOpenDraft = {},
                onOpenQuizDraft = {},
                onOpenCaptureDraft = {},
                onSaveCapture = {},
                onRetry = {},
                onOpenDailyReview = {},
                onConfigureAi = {},
                onAgentActionReply = {},
                onConfirmAreaMove = {},
                areas = emptyList()
            )
            AssistantMessageBubble(
                message = AssistantMessage(
                    id = "a1",
                    role = AssistantMessageRole.Assistant,
                    body = "因为 TLB 只是页表的缓存。",
                    citations = listOf(
                        com.cslearningos.mobile.feature.assistant.ui.AssistantCitation(
                            id = "n1",
                            type = "node",
                            title = "虚拟内存与页面置换",
                            excerpt = ""
                        )
                    )
                ),
                onOpenCitation = { _, _ -> },
                onOpenDraft = {},
                onOpenQuizDraft = {},
                onOpenCaptureDraft = {},
                onSaveCapture = {},
                onRetry = {},
                onOpenDailyReview = {},
                onConfigureAi = {},
                onAgentActionReply = {},
                onConfirmAreaMove = {},
                areas = emptyList()
            )
            AssistantMessageBubble(
                message = AssistantMessage(
                    id = "a2",
                    role = AssistantMessageRole.Assistant,
                    body = "",
                    isStreaming = true
                ),
                onOpenCitation = { _, _ -> },
                onOpenDraft = {},
                onOpenQuizDraft = {},
                onOpenCaptureDraft = {},
                onSaveCapture = {},
                onRetry = {},
                onOpenDailyReview = {},
                onConfigureAi = {},
                onAgentActionReply = {},
                onConfirmAreaMove = {},
                areas = emptyList()
            )
        }
    }
}
