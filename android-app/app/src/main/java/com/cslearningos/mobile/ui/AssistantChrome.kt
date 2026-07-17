package com.cslearningos.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
            .background(WorkbenchColors.Surface)
            .padding(
                horizontal = AssistantUiTokens.HeaderHorizontalPadding,
                vertical = AssistantUiTokens.HeaderVerticalPadding
            )
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistantHeaderIconAction(contentDescription = stringResource(R.string.common_back), onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
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
                Icon(Icons.Filled.History, contentDescription = null)
            }
            AssistantHeaderIconAction(
                contentDescription = stringResource(R.string.assistant_new_chat),
                onClick = onNewChat,
                accent = true
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
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
            .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
            .background(WorkbenchColors.SurfaceCard)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.assistant_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        WorkbenchButton(
            text = stringResource(R.string.assistant_new_chat),
            onClick = { onNewChat(); onDismiss() },
            primary = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.assistant_history),
            color = WorkbenchColors.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.assistant_history_empty),
                color = WorkbenchColors.Muted,
                fontSize = 13.sp
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                items(history, key = { it.id }) { conversation ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = canOpenHistory) { onOpen(conversation.id) }
                            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conversation.title,
                                color = WorkbenchColors.InkStrong,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (conversation.preview.isNotBlank()) {
                                Text(
                                    text = conversation.preview,
                                    color = WorkbenchColors.Muted,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDelete(conversation.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.assistant_history_delete),
                                tint = WorkbenchColors.Muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
