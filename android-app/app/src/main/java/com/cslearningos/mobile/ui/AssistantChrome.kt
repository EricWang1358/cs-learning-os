package com.cslearningos.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cslearningos.mobile.R
import com.cslearningos.mobile.feature.assistant.ui.AssistantConversationSummary

@Composable
internal fun AssistantTopBar(
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onHistory: () -> Unit,
    historyEnabled: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WorkbenchColors.Surface.copy(alpha = AssistantUiTokens.TopBarSurfaceAlpha))
            .padding(
                horizontal = AssistantUiTokens.HeaderHorizontalPadding,
                vertical = AssistantUiTokens.HeaderVerticalPadding
            )
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistantBackAction(onClick = onBack)
        }
        Text(
            text = stringResource(R.string.assistant_title),
            color = WorkbenchColors.InkStrong,
            fontSize = AssistantUiTokens.HeaderTitleSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 88.dp)
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AssistantUiTokens.HeaderItemSpacing)
        ) {
            AssistantHeaderIconAction(
                contentDescription = stringResource(R.string.assistant_history),
                onClick = onHistory,
                enabled = historyEnabled,
                accent = false
            ) {
                Icon(Icons.Filled.Menu, contentDescription = null)
            }
            AssistantHeaderIconAction(
                contentDescription = stringResource(R.string.assistant_new_chat),
                onClick = onNewChat,
                accent = true
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null)
            }
        }
    }
}

@Composable
private fun AssistantBackAction(onClick: () -> Unit) {
    AssistantHeaderIconAction(contentDescription = stringResource(R.string.common_back), onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
    }
}

@Composable
internal fun AssistantHistoryDrawer(
    history: List<AssistantConversationSummary>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNewChat: () -> Unit,
    canOpenHistory: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 280.dp, max = 320.dp)
            .background(WorkbenchColors.SurfaceSoft)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.assistant_title), color = WorkbenchColors.InkStrong, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        WorkbenchButton(stringResource(R.string.assistant_new_chat), { onNewChat(); onDismiss() }, primary = true, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.assistant_history), color = WorkbenchColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (history.isEmpty()) {
            Text(stringResource(R.string.assistant_history_empty))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(history, key = { it.id }) { conversation ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { onOpen(conversation.id) },
                            enabled = canOpenHistory,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(conversation.title, color = WorkbenchColors.InkStrong, fontWeight = FontWeight.Bold)
                                if (conversation.preview.isNotBlank()) {
                                    Text(conversation.preview, color = WorkbenchColors.Muted, fontSize = 12.sp, maxLines = 2)
                                }
                            }
                        }
                        TextButton(onClick = { onDelete(conversation.id) }) { Text(stringResource(R.string.common_delete)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantHeaderIconAction(
    contentDescription: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    enabled: Boolean = true,
    icon: @Composable () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled) {
        CompositionLocalProvider(
            LocalContentColor provides when {
                !enabled -> WorkbenchColors.Muted.copy(alpha = 0.42f)
                accent -> WorkbenchColors.AccentStrong
                else -> WorkbenchColors.Muted
            }
        ) {
            Box(modifier = Modifier.semantics { this.contentDescription = contentDescription }) {
                icon()
            }
        }
    }
}
