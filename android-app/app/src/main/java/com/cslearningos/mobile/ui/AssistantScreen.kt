@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    LaunchedEffect(assistant.messages.size) {
        if (assistant.messages.isNotEmpty()) {
            listState.animateScrollToItem(assistant.messages.lastIndex)
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WorkbenchColors.SurfaceSoft.copy(alpha = 0.94f))
    ) {
        AssistantTopBar(
            onBack = viewModel::showHome,
            onNewChat = viewModel.assistantActions::newChat
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
            .border(BorderStroke(1.dp, WorkbenchColors.Line))
            .background(WorkbenchColors.Surface.copy(alpha = 0.94f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WorkbenchButton(stringResource(R.string.common_back), onBack)
        Text(
            text = stringResource(R.string.assistant_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f)
        )
        WorkbenchButton(stringResource(R.string.assistant_new_chat), onNewChat)
    }
}

@Composable
private fun AssistantEmptyState(onQuickMessage: (String) -> Unit) {
    val explainLabel = stringResource(R.string.assistant_quick_explain)
    val draftLabel = stringResource(R.string.assistant_quick_draft)
    val reviewLabel = stringResource(R.string.assistant_quick_review)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.assistant_empty_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )
        ToolbarRow {
            WorkbenchButton(
                text = explainLabel,
                onClick = { onQuickMessage(explainLabel) },
                primary = true
            )
            WorkbenchButton(
                text = draftLabel,
                onClick = { onQuickMessage(draftLabel) }
            )
            WorkbenchButton(
                text = reviewLabel,
                onClick = { onQuickMessage(reviewLabel) }
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: AssistantMessage,
    onOpenCitation: (String, String) -> Unit,
    onOpenDraft: () -> Unit,
    onSaveCapture: () -> Unit,
    onConfigureAi: () -> Unit
) {
    val isUser = message.role == AssistantMessageRole.User
    val shape = RoundedCornerShape(12.dp)
    val containerColor = if (isUser) WorkbenchColors.Accent.copy(alpha = 0.14f) else WorkbenchColors.Surface
    val borderColor = if (isUser) WorkbenchColors.Accent.copy(alpha = 0.48f) else WorkbenchColors.Line
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = if (isUser) stringResource(R.string.common_manual) else stringResource(R.string.assistant_title),
            color = WorkbenchColors.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = message.body.ifBlank { stringResource(R.string.assistant_streaming) },
            color = WorkbenchColors.InkStrong,
            fontSize = 15.sp,
            lineHeight = 23.sp
        )
        if (message.citations.isNotEmpty()) {
            Text(
                text = stringResource(R.string.assistant_sources),
                color = WorkbenchColors.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            message.citations.forEach { citation ->
                Text(
                    text = citation.title,
                    color = WorkbenchColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onOpenCitation(citation.type, citation.id) }
                )
            }
        }
        when (val action = message.action) {
            is AssistantMessageAction.OpenEditableDraft -> {
                WorkbenchButton(
                    text = stringResource(R.string.assistant_open_draft),
                    onClick = onOpenDraft,
                    primary = true
                )
            }

            is AssistantMessageAction.SaveCapture -> {
                WorkbenchButton(
                    text = stringResource(R.string.assistant_save_capture),
                    onClick = onSaveCapture
                )
            }

            AssistantMessageAction.ConfigureAi -> {
                WorkbenchButton(
                    text = stringResource(R.string.assistant_configure_ai),
                    onClick = onConfigureAi,
                    primary = true
                )
            }

            null -> Unit
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, WorkbenchColors.Line))
            .background(WorkbenchColors.Surface.copy(alpha = 0.96f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WorkbenchTextField(
            value = value,
            onValueChange = onValueChange,
            label = stringResource(R.string.assistant_input_label),
            minLines = 2,
            onSubmit = onSend
        )
        if (enabled) {
            WorkbenchButton(
                text = stringResource(R.string.assistant_send),
                onClick = onSend,
                primary = true,
                enabled = value.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            WorkbenchButton(
                text = stringResource(R.string.assistant_stop),
                onClick = onStop,
                danger = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
