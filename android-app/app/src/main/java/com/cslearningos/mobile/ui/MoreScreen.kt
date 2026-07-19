@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.cslearningos.mobile.data.BiteCardEntity
import com.cslearningos.mobile.data.LearningDatabase
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoreScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Eyebrow(stringResource(R.string.more_eyebrow))
            Text(
                text = stringResource(R.string.more_body),
                color = WorkbenchColors.Muted.copy(alpha = 0.92f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
        MoreSettingsList(state = state, viewModel = viewModel)
    }
}

@Composable
private fun MoreSettingsList(
    state: LearningUiState,
    viewModel: LearningViewModel
) {
    val sections = moreSectionSummaries(state)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sections.forEach { section ->
            MoreSectionRow(
                section = section,
                expanded = state.expandedMoreSection == section.id,
                onToggle = { viewModel.toggleMoreSection(section.id) }
            ) {
                when (section.id) {
                    MoreSectionId.System -> SystemSettingsContent(state = state, viewModel = viewModel)
                    MoreSectionId.Service -> AiProviderContent(state = state, viewModel = viewModel)
                    MoreSectionId.Sync -> SyncSectionContent(state = state, viewModel = viewModel)
                    MoreSectionId.DailyBite -> DailyBiteContent(state = state, viewModel = viewModel)
                    MoreSectionId.Data -> DataToolsContent(state = state, viewModel = viewModel)
                    MoreSectionId.Guide -> GuideContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun MoreSectionRow(
    section: MoreSectionSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val expansionState = stringResource(
        if (expanded) R.string.more_section_expanded else R.string.more_section_collapsed
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (expanded) WorkbenchColors.Surface.copy(alpha = 0.62f) else WorkbenchColors.Surface.copy(alpha = 0.34f),
        animationSpec = tween(WorkbenchMotion.StateMillis, easing = FastOutSlowInEasing),
        label = "more-section-background"
    )
    val borderColor by animateColorAsState(
        targetValue = if (expanded) WorkbenchColors.Accent.copy(alpha = 0.56f) else WorkbenchColors.Line,
        animationSpec = tween(WorkbenchMotion.StateMillis, easing = FastOutSlowInEasing),
        label = "more-section-border"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                BorderStroke(1.dp, borderColor),
                RoundedCornerShape(16.dp)
            )
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggle)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    stateDescription = expansionState
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Eyebrow(section.title)
                Text(section.body, color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(section.value, color = WorkbenchColors.InkStrong, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (expanded) stringResource(R.string.common_close) else stringResource(R.string.common_expand),
                    color = WorkbenchColors.Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(WorkbenchMotion.StateMillis)) + expandVertically(tween(WorkbenchMotion.DisclosureMillis)),
            exit = fadeOut(tween(WorkbenchMotion.StateMillis)) + shrinkVertically(tween(WorkbenchMotion.DisclosureMillis))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SystemSettingsContent(state: LearningUiState, viewModel: LearningViewModel) {
    SettingsRow(label = stringResource(R.string.more_system_language_label)) {
        ToolbarRow {
            SystemLanguage.entries.forEach { language ->
                WorkbenchButton(
                    text = systemLanguageLabel(language),
                    onClick = { viewModel.setSystemLanguage(language) },
                    primary = state.systemLanguage == language
                )
            }
        }
        Text(
            text = stringResource(R.string.more_system_language_note),
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
    SettingsRow(label = stringResource(R.string.more_appearance_label)) {
        ToolbarRow {
            AppearanceMode.entries.forEach { mode ->
                WorkbenchButton(
                    text = appearanceModeLabel(mode),
                    onClick = { viewModel.setAppearanceMode(mode) },
                    primary = state.appearanceMode == mode
                )
            }
        }
        Text(
            text = stringResource(R.string.more_appearance_note),
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun AiProviderContent(state: LearningUiState, viewModel: LearningViewModel) {
    val settings = state.aiProviderSettings
    AiServiceStatusBlock(status = state.aiServiceStatus)
    SettingsRow(label = stringResource(R.string.more_provider_label)) {
        WorkbenchTextField(settings.provider, viewModel::setAiProvider, "DeepSeek / OpenAI compatible")
        Text(
            text = stringResource(R.string.more_provider_helper),
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
    SettingsRow(label = stringResource(R.string.more_api_key_label)) {
        if (settings.apiKeyVisible) {
            WorkbenchTextField(
                value = settings.apiKey,
                onValueChange = viewModel::setAiApiKey,
                label = "sk-...",
                minLines = 1
            )
        } else {
            Text(
                text = if (settings.apiKey.isBlank()) stringResource(R.string.common_not_configured) else settings.apiKey.maskSecret(),
                color = WorkbenchColors.InkStrong,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        ToolbarRow {
            WorkbenchButton(stringResource(if (settings.apiKeyVisible) R.string.common_hide else R.string.common_show), viewModel::toggleAiKeyVisibility)
        }
    }
    SettingsRow(label = stringResource(R.string.more_base_url_label)) {
        WorkbenchTextField(settings.baseUrl, viewModel::setAiBaseUrl, "https://api.deepseek.com/v1")
    }
    SettingsRow(label = stringResource(R.string.more_model_label)) {
        WorkbenchTextField(settings.model, viewModel::setAiModel, "deepseek-v4-flash")
        if (state.availableAiModels.isNotEmpty()) {
            Text(
                text = stringResource(R.string.more_pulled_models_label),
                color = WorkbenchColors.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            LoadedModelPicker(
                selectedModel = settings.model,
                availableModels = state.availableAiModels,
                onSelect = viewModel::selectAiModel
            )
        }
        ToolbarRow {
            WorkbenchButton(
                text = stringResource(R.string.more_thinking_on),
                onClick = { viewModel.setAiThinkingEnabled(true) },
                primary = settings.thinkingEnabled
            )
            WorkbenchButton(
                text = stringResource(R.string.more_thinking_off),
                onClick = { viewModel.setAiThinkingEnabled(false) },
                primary = !settings.thinkingEnabled
            )
        }
    }
    SettingsRow(label = stringResource(R.string.more_connection_label)) {
        ToolbarRow {
            WorkbenchButton(stringResource(R.string.common_save_settings), viewModel::saveAiSettings, enabled = !state.aiBusy)
            WorkbenchButton(stringResource(R.string.common_validate), viewModel::validateAiSettings, primary = true, enabled = !state.aiBusy)
            WorkbenchButton(stringResource(R.string.common_models), viewModel::pullAiModels, enabled = !state.aiBusy)
        }
    }
}

@Composable
private fun LoadedModelPicker(
    selectedModel: String,
    availableModels: List<String>,
    onSelect: (String) -> Unit
) {
    WorkbenchMenuButton(
        text = selectedModel,
        options = availableModels.distinct().take(MaxLoadedModelChoices).map { modelId ->
            WorkbenchMenuOption(modelId) { onSelect(modelId) }
        },
        primary = true,
        modifier = Modifier.fillMaxWidth(),
        expandToContainer = true
    )
}

private const val MaxLoadedModelChoices = 8

@Composable
private fun AiServiceStatusBlock(status: AiServiceStatus) {
    val context = LocalContext.current
    val borderColor = when (status.kind) {
        AiServiceStatusKind.Success -> WorkbenchColors.Success
        AiServiceStatusKind.Warning -> WorkbenchColors.AccentStrong
        AiServiceStatusKind.Error -> WorkbenchColors.Danger
        AiServiceStatusKind.Loading -> WorkbenchColors.Accent
        AiServiceStatusKind.Info,
        AiServiceStatusKind.Idle -> WorkbenchColors.LineStrong
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WorkbenchColors.Surface.copy(alpha = 0.62f))
            .border(BorderStroke(1.dp, borderColor.copy(alpha = 0.72f)), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Eyebrow(stringResource(R.string.more_ai_status_eyebrow))
        Text(status.title.resolve(context), color = WorkbenchColors.InkStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(status.body.resolve(context), color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun DataToolsContent(state: LearningUiState, viewModel: LearningViewModel) {
    var showRemoveDemoConfirm by rememberSaveable { mutableStateOf(false) }
    SettingsRow(label = stringResource(R.string.more_local_data_label)) {
        Text(
            text = stringResource(R.string.more_local_data_body),
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        ToolbarRow {
            WorkbenchButton(stringResource(R.string.more_backup_restore), viewModel::showBackup, primary = true)
            WorkbenchButton(stringResource(R.string.more_remove_demo), { showRemoveDemoConfirm = true }, danger = true)
        }
    }
    if (showRemoveDemoConfirm) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.more_remove_demo_confirm_title),
            body = stringResource(R.string.more_remove_demo_confirm_body),
            confirmLabel = stringResource(R.string.more_remove_demo),
            onDismiss = { showRemoveDemoConfirm = false },
            onConfirm = {
                showRemoveDemoConfirm = false
                viewModel.clearStarterContent()
            }
        )
    }
}

@Composable
private fun GuideContent(viewModel: LearningViewModel) {
    val stepTitles = stringArrayResource(R.array.more_guide_step_titles)
    val stepBodies = stringArrayResource(R.array.more_guide_step_bodies)
    val actions = listOf(
        stringResource(R.string.more_guide_open_capture) to viewModel::showCapture,
        stringResource(R.string.more_guide_open_library) to viewModel::showLibrary,
        stringResource(R.string.more_guide_open_review) to viewModel::showReview,
        stringResource(R.string.more_guide_open_backup) to viewModel::showBackup
    )
    SettingsRow(label = stringResource(R.string.more_guide_label)) {
        Text(
            text = stringResource(R.string.more_guide_intro),
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        stepTitles.zip(stepBodies).forEachIndexed { index, (title, body) ->
            GuideStepCard(
                step = index + 1,
                title = title,
                body = body,
                actionLabel = actions[index].first,
                onAction = actions[index].second
            )
        }
        SettingsRow(label = stringResource(R.string.more_support_label)) {
            Text(
                text = stringResource(R.string.more_support_body),
                color = WorkbenchColors.Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
        SettingsRow(label = stringResource(R.string.assistant_guide_entry_label)) {
            Text(
                text = stringResource(R.string.assistant_guide_entry_body),
                color = WorkbenchColors.Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
            WorkbenchButton(
                text = stringResource(R.string.assistant_guide_open),
                onClick = viewModel::showAssistantGuide,
                primary = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GuideStepCard(step: Int, title: String, body: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WorkbenchColors.Surface.copy(alpha = 0.58f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Eyebrow(stringResource(R.string.more_guide_step_badge, step))
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(body, color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
        WorkbenchButton(actionLabel, onAction, primary = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ConfirmDestructiveDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = WorkbenchColors.Danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun SettingsRow(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WorkbenchColors.SurfaceCard.copy(alpha = 0.62f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Eyebrow(label)
        content()
    }
}

private fun String.maskSecret(): String =
    if (isBlank()) "" else take(6) + "..." + takeLast(4)

// ---- Daily Bite ----

@Composable
private fun DailyBiteContent(state: LearningUiState, viewModel: LearningViewModel) {
    val context = LocalContext.current
    val db = remember { Room.databaseBuilder(context, LearningDatabase::class.java, "learning-os.db").build() }
    val cards by db.learningDao().observeBiteCards().collectAsStateWithLifecycle(initialValue = emptyList())

    // In-memory today state (resets on app restart, acceptable for now)
    val todayReviewed = remember { mutableStateOf(mutableSetOf<Long>()) }
    val todaySeed = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) }

    val unreviewedCards = cards.filterNot { it.id in todayReviewed.value }
    val totalCount = cards.size
    val remainingCount = unreviewedCards.size
    val currentCard = if (unreviewedCards.isNotEmpty()) {
        unreviewedCards[kotlin.math.abs(todaySeed) % unreviewedCards.size]
    } else null

    var showAnswer by rememberSaveable { mutableStateOf(false) }

    // Advance review: mark card as reviewed for today, reset answer
    val advanceReview: (Long) -> Unit = { cardId ->
        todayReviewed.value = todayReviewed.value.toMutableSet().apply { add(cardId) }
        showAnswer = false
    }

    // --- Empty state: no bite cards in database ---
    if (cards.isEmpty()) {
        SettingsRow(label = stringResource(R.string.more_daily_bite_label)) {
            Text(
                text = stringResource(R.string.more_daily_bite_empty),
                color = WorkbenchColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
        return
    }

    // --- All cards reviewed today ---
    if (remainingCount == 0) {
        SettingsRow(label = stringResource(R.string.more_daily_bite_label)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🟢", fontSize = 14.sp)
                Text(
                    text = stringResource(R.string.more_daily_bite_done_title),
                    color = WorkbenchColors.InkStrong,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

    val card = currentCard ?: return

    SettingsRow(label = stringResource(R.string.more_daily_bite_label)) {
        // Status row: colored dot + remaining count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "●",
                color = WorkbenchColors.AccentStrong,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.more_daily_bite_status_remaining, remainingCount),
                color = WorkbenchColors.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Prompt card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WorkbenchColors.SurfaceCard.copy(alpha = 0.72f))
                .border(BorderStroke(1.dp, WorkbenchColors.Accent.copy(alpha = 0.38f)), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = card.prompt,
                color = WorkbenchColors.InkStrong,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
            if (card.hint.isNotBlank() && !showAnswer) {
                Text(
                    text = "Hint: ${card.hint}",
                    color = WorkbenchColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            AnimatedVisibility(visible = showAnswer) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = card.answer,
                        color = WorkbenchColors.Success,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!showAnswer) {
                WorkbenchButton(
                    text = stringResource(R.string.more_daily_bite_reveal),
                    onClick = { showAnswer = true },
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
            } else {
                WorkbenchButton(
                    text = stringResource(R.string.more_daily_bite_again),
                    onClick = { advanceReview(card.id) },
                    modifier = Modifier.weight(1f)
                )
                WorkbenchButton(
                    text = stringResource(R.string.more_daily_bite_good),
                    onClick = { advanceReview(card.id) },
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
