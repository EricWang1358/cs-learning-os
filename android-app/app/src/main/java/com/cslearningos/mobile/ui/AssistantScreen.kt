@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cslearningos.mobile.R
import com.cslearningos.mobile.feature.assistant.ui.AssistantMessage
import com.cslearningos.mobile.feature.assistant.ui.AssistantMessageAction
import com.cslearningos.mobile.feature.assistant.ui.AssistantMessageRole

@Composable
fun AssistantScreen(
    state: LearningUiState,
    viewModel: LearningViewModel,
    modifier: Modifier = Modifier
) {
    val assistant = state.assistant
    val listState = rememberLazyListState()
    val lastMessage = assistant.messages.lastOrNull()
    LaunchedEffect(lastMessage?.id, lastMessage?.isStreaming) {
        if (assistant.messages.isNotEmpty()) {
            listState.scrollToItem(assistant.messages.lastIndex)
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WorkbenchColors.SurfaceSoft.copy(alpha = AssistantUiTokens.ScreenSurfaceAlpha))
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AssistantUiTokens.ListPadding),
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
                        areas = state.areas
                    )
                }
            }
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
        Text(
            text = stringResource(R.string.assistant_empty_title),
            color = WorkbenchColors.InkStrong,
            fontSize = AssistantUiTokens.EmptyStateTitleSize,
            fontWeight = FontWeight.Medium,
            lineHeight = AssistantUiTokens.EmptyStateTitleLineHeight,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AssistantUiTokens.QuickActionGap)
        ) {
            AssistantQuickActionCard(
                icon = "?",
                text = explainLabel,
                accent = true,
                modifier = Modifier.weight(1f),
                onClick = { onQuickMessage("$explainLabel: ") }
            )
            AssistantQuickActionCard(
                icon = "+",
                text = draftLabel,
                modifier = Modifier.weight(1f),
                onClick = { onQuickMessage(draftLabel) }
            )
            AssistantQuickActionCard(
                icon = "Q",
                text = reviewLabel,
                modifier = Modifier.weight(1f),
                onClick = onStartReview
            )
        }
    }
}

@Composable
private fun AssistantQuickActionCard(
    icon: String,
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(AssistantUiTokens.QuickActionCornerRadius)
    Column(
        modifier = modifier
            .height(AssistantUiTokens.QuickActionHeight)
            .clip(shape)
            .background(WorkbenchColors.SurfaceCard.copy(alpha = if (accent) 0.98f else 0.74f))
            .border(
                BorderStroke(
                    1.dp,
                    if (accent) WorkbenchColors.Accent.copy(alpha = 0.52f) else WorkbenchColors.Line.copy(alpha = 0.82f)
                ),
                shape
            )
            .clickable(onClick = onClick)
            .padding(AssistantUiTokens.QuickActionPadding),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = icon,
            color = if (accent) WorkbenchColors.AccentStrong else WorkbenchColors.Muted,
            fontSize = AssistantUiTokens.QuickActionIconSize,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = text,
            color = WorkbenchColors.InkStrong,
            fontSize = AssistantUiTokens.QuickActionTextSize,
            lineHeight = AssistantUiTokens.QuickActionLineHeight,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
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
    areas: List<com.cslearningos.mobile.data.AreaEntity>
) {
    val isUser = message.role == AssistantMessageRole.User
    val shape = RoundedCornerShape(AssistantUiTokens.MessageCornerRadius)
    val containerColor = if (isUser) {
        WorkbenchColors.Accent.copy(alpha = AssistantUiTokens.UserBubbleAlpha)
    } else {
        WorkbenchColors.SurfaceCard
    }
    val borderColor = if (isUser) {
        WorkbenchColors.Accent.copy(alpha = AssistantUiTokens.UserBubbleBorderAlpha)
    } else {
        WorkbenchColors.Line
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
                .border(BorderStroke(1.dp, borderColor), shape)
                .padding(AssistantUiTokens.MessagePadding),
            verticalArrangement = Arrangement.spacedBy(AssistantUiTokens.MessageItemSpacing)
        ) {
            if (!isUser && !message.isStreaming && message.body.isNotBlank()) {
                MarkdownRenderer(markdown = message.body, card = false)
            } else {
                Text(
                    text = message.body.ifBlank { stringResource(R.string.assistant_streaming) },
                    color = WorkbenchColors.InkStrong,
                    fontSize = AssistantUiTokens.MessageTextSize,
                    lineHeight = AssistantUiTokens.MessageLineHeight
                )
            }
            if (message.citations.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.assistant_sources),
                    color = WorkbenchColors.Muted,
                    fontSize = AssistantUiTokens.SourceLabelSize,
                    fontWeight = FontWeight.Bold
                )
                message.citations.forEach { citation ->
                    Text(
                        text = citation.title,
                        color = WorkbenchColors.AccentStrong,
                        fontSize = AssistantUiTokens.CitationTextSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AssistantUiTokens.CitationMinHeight)
                            .clickable { onOpenCitation(citation.type, citation.id) }
                            .padding(vertical = AssistantUiTokens.CitationVerticalPadding)
                    )
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
                    onReply = onAgentActionReply
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

internal object AssistantUiTokens {
    const val ScreenSurfaceAlpha = 0.94f
    const val TopBarSurfaceAlpha = 0.94f
    const val ComposerSurfaceAlpha = 0.96f
    val ListPadding = 18.dp
    val ListItemSpacing = 12.dp

    val HeaderHorizontalPadding = 16.dp
    val HeaderVerticalPadding = 4.dp
    val HeaderItemSpacing = 4.dp
    val HeaderActionMinHeight = 48.dp
    val HeaderActionCornerRadius = 8.dp
    val HeaderActionHorizontalPadding = 8.dp
    val HeaderActionVerticalPadding = 12.dp
    val HeaderTitleSize = 17.sp
    val HeaderActionSize = 13.sp

    val EmptyStateMinHeight = 480.dp
    val EmptyStateTopPadding = 20.dp
    val EmptyStateSpacing = 28.dp
    val EmptyStateTitleSize = 27.sp
    val EmptyStateTitleLineHeight = 34.sp
    val QuickActionGap = 10.dp
    val QuickActionHeight = 112.dp
    val QuickActionCornerRadius = 18.dp
    val QuickActionPadding = 14.dp
    val QuickActionIconSize = 18.sp
    val QuickActionTextSize = 13.sp
    val QuickActionLineHeight = 18.sp

    val MessageCornerRadius = 16.dp
    val MessagePadding = 14.dp
    val MessageItemSpacing = 8.dp
    val UserMessageMaxWidth = 296.dp
    val AssistantMessageMaxWidth = 336.dp
    const val UserBubbleAlpha = 0.16f
    const val UserBubbleBorderAlpha = 0.4f
    val MessageTextSize = 15.sp
    val MessageLineHeight = 23.sp
    val SourceLabelSize = 11.sp
    val CitationTextSize = 13.sp
    val CitationMinHeight = 48.dp
    val CitationVerticalPadding = 12.dp

    val ComposerPadding = 12.dp
    val ComposerItemSpacing = 8.dp
    val ComposerCornerRadius = 12.dp
    val ComposerMinHeight = 52.dp
    val InputHorizontalPadding = 14.dp
    const val InputHintAlpha = 0.62f
    val InputTextSize = 15.sp
    val InputLineHeight = 22.sp
}
