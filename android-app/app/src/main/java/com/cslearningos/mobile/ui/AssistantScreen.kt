@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    LaunchedEffect(assistant.messages.lastOrNull()?.id, assistant.messages.lastOrNull()?.body?.length) {
        if (assistant.messages.isNotEmpty()) {
            listState.animateScrollToItem(assistant.messages.lastIndex)
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WorkbenchColors.SurfaceSoft.copy(alpha = AssistantUiTokens.ScreenSurfaceAlpha))
    ) {
        AssistantTopBar(
            onBack = viewModel::showHome,
            onNewChat = viewModel.assistantActions::newChat
        )
        StatusBanner(state.message)
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(AssistantUiTokens.ListPadding),
            verticalArrangement = Arrangement.spacedBy(AssistantUiTokens.ListItemSpacing)
        ) {
            if (assistant.messages.isEmpty()) {
                item { AssistantEmptyState(onQuickMessage = viewModel.assistantActions::sendQuickMessage) }
            }
            items(assistant.messages, key = { it.id }) { message ->
                AssistantMessageBubble(
                    message = message,
                    onOpenCitation = viewModel.assistantActions::openCitation,
                    onOpenDraft = { viewModel.assistantActions.openDraft(message.id) },
                    onSaveCapture = { viewModel.assistantActions.saveReplyToCapture(message.id) },
                    onRetry = { viewModel.assistantActions.retryMessage(message.id) },
                    onConfigureAi = viewModel::showAiServiceSettings
                )
            }
        }
        AssistantComposer(
            value = assistant.input,
            onValueChange = viewModel.assistantActions::setInput,
            onSend = viewModel.assistantActions::sendMessage,
            onStop = viewModel.assistantActions::cancelReply,
            enabled = !assistant.isBusy,
            modifier = Modifier.imePadding()
        )
    }
}

@Composable
private fun AssistantTopBar(onBack: () -> Unit, onNewChat: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WorkbenchColors.Surface.copy(alpha = AssistantUiTokens.TopBarSurfaceAlpha))
            .border(BorderStroke(1.dp, WorkbenchColors.Line))
            .padding(
                horizontal = AssistantUiTokens.HeaderHorizontalPadding,
                vertical = AssistantUiTokens.HeaderVerticalPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(AssistantUiTokens.HeaderItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantHeaderAction(text = stringResource(R.string.common_back), onClick = onBack)
        Text(
            text = stringResource(R.string.assistant_title),
            color = WorkbenchColors.InkStrong,
            fontSize = AssistantUiTokens.HeaderTitleSize,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f)
        )
        AssistantHeaderAction(
            text = stringResource(R.string.assistant_new_chat),
            onClick = onNewChat,
            accent = true
        )
    }
}

@Composable
private fun AssistantHeaderAction(text: String, onClick: () -> Unit, accent: Boolean = false) {
    Text(
        text = text,
        color = if (accent) WorkbenchColors.AccentStrong else WorkbenchColors.Muted,
        fontSize = AssistantUiTokens.HeaderActionSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .heightIn(min = AssistantUiTokens.HeaderActionMinHeight)
            .clip(RoundedCornerShape(AssistantUiTokens.HeaderActionCornerRadius))
            .clickable(onClick = onClick)
            .padding(
                horizontal = AssistantUiTokens.HeaderActionHorizontalPadding,
                vertical = AssistantUiTokens.HeaderActionVerticalPadding
            )
    )
}

@Composable
private fun AssistantEmptyState(onQuickMessage: (String) -> Unit) {
    val explainLabel = stringResource(R.string.assistant_quick_explain)
    val draftLabel = stringResource(R.string.assistant_quick_draft)
    val reviewLabel = stringResource(R.string.assistant_quick_review)
    Column(
        modifier = Modifier.padding(top = AssistantUiTokens.EmptyStateTopPadding),
        verticalArrangement = Arrangement.spacedBy(AssistantUiTokens.EmptyStateSpacing)
    ) {
        Text(
            text = stringResource(R.string.assistant_empty_title),
            color = WorkbenchColors.InkStrong,
            fontSize = AssistantUiTokens.EmptyStateTitleSize,
            fontWeight = FontWeight.ExtraBold
        )
        ToolbarRow {
            WorkbenchButton(
                text = explainLabel,
                onClick = { onQuickMessage(explainLabel) },
                primary = true
            )
            WorkbenchButton(text = draftLabel, onClick = { onQuickMessage(draftLabel) })
            WorkbenchButton(text = reviewLabel, onClick = { onQuickMessage(reviewLabel) })
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: AssistantMessage,
    onOpenCitation: (String, String) -> Unit,
    onOpenDraft: () -> Unit,
    onSaveCapture: () -> Unit,
    onRetry: () -> Unit,
    onConfigureAi: () -> Unit
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
                    fontWeight = FontWeight.Black
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
            when (message.action) {
                is AssistantMessageAction.OpenEditableDraft -> WorkbenchButton(
                    text = stringResource(R.string.assistant_open_draft),
                    onClick = onOpenDraft,
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

                AssistantMessageAction.ConfigureAi -> WorkbenchButton(
                    text = stringResource(R.string.assistant_configure_ai),
                    onClick = onConfigureAi,
                    primary = true
                )

                null -> Unit
            }
        }
    }
}

@Composable
private fun AssistantComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WorkbenchColors.Surface.copy(alpha = AssistantUiTokens.ComposerSurfaceAlpha))
            .border(BorderStroke(1.dp, WorkbenchColors.Line))
            .padding(AssistantUiTokens.ComposerPadding),
        horizontalArrangement = Arrangement.spacedBy(AssistantUiTokens.ComposerItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantInputField(
            value = value,
            onValueChange = onValueChange,
            onSubmit = onSend,
            modifier = Modifier.weight(1f)
        )
        if (enabled) {
            WorkbenchButton(
                text = stringResource(R.string.assistant_send),
                onClick = onSend,
                primary = true,
                enabled = value.isNotBlank()
            )
        } else {
            WorkbenchButton(
                text = stringResource(R.string.assistant_stop),
                onClick = onStop,
                danger = true
            )
        }
    }
}

@Composable
private fun AssistantInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AssistantUiTokens.ComposerCornerRadius)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(WorkbenchColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSubmit() }),
        textStyle = TextStyle(
            color = WorkbenchColors.Ink,
            fontSize = AssistantUiTokens.InputTextSize,
            lineHeight = AssistantUiTokens.InputLineHeight,
            fontWeight = FontWeight.Medium
        ),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .heightIn(min = AssistantUiTokens.ComposerMinHeight)
                    .clip(shape)
                    .background(WorkbenchColors.SurfaceCard)
                    .border(BorderStroke(1.dp, WorkbenchColors.LineStrong), shape)
                    .padding(horizontal = AssistantUiTokens.InputHorizontalPadding),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    Text(
                        text = stringResource(R.string.assistant_input_label),
                        color = WorkbenchColors.Muted.copy(alpha = AssistantUiTokens.InputHintAlpha),
                        fontSize = AssistantUiTokens.InputTextSize
                    )
                }
                innerTextField()
            }
        }
    )
}

private object AssistantUiTokens {
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
    val HeaderTitleSize = 18.sp
    val HeaderActionSize = 13.sp

    val EmptyStateTopPadding = 32.dp
    val EmptyStateSpacing = 12.dp
    val EmptyStateTitleSize = 23.sp

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
