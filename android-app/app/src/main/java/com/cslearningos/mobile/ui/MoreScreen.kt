@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoreScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = stringResource(R.string.more_eyebrow),
            title = stringResource(R.string.more_title),
            body = stringResource(R.string.more_body)
        )
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
    val backgroundColor by animateColorAsState(
        targetValue = if (expanded) WorkbenchColors.Surface.copy(alpha = 0.62f) else WorkbenchColors.Surface.copy(alpha = 0.34f),
        animationSpec = tween(WorkbenchMotion.CompactFadeMillis, easing = FastOutSlowInEasing),
        label = "more-section-background"
    )
    val borderColor by animateColorAsState(
        targetValue = if (expanded) WorkbenchColors.Accent.copy(alpha = 0.56f) else WorkbenchColors.Line,
        animationSpec = tween(WorkbenchMotion.CompactFadeMillis, easing = FastOutSlowInEasing),
        label = "more-section-border"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(WorkbenchMotion.CompactExpandMillis, easing = FastOutSlowInEasing))
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(
                BorderStroke(1.dp, borderColor),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onToggle)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Eyebrow(section.title)
                Text(section.body, color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(section.value, color = WorkbenchColors.InkStrong, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(
                    if (expanded) stringResource(R.string.common_close) else stringResource(R.string.common_expand),
                    color = WorkbenchColors.Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(WorkbenchMotion.CompactFadeMillis)) + expandVertically(tween(WorkbenchMotion.CompactExpandMillis)),
            exit = fadeOut(tween(WorkbenchMotion.CompactFadeMillis)) + shrinkVertically(tween(WorkbenchMotion.CompactExpandMillis))
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
        Text(status.title.resolve(context), color = WorkbenchColors.InkStrong, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(status.body.resolve(context), color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun DataToolsContent(state: LearningUiState, viewModel: LearningViewModel) {
    var showRemoveDemoConfirm by rememberSaveable { mutableStateOf(false) }
    var deleteForeverNodeId by rememberSaveable { mutableStateOf<String?>(null) }
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
        Text(
            text = stringResource(R.string.more_delete_forever_warning),
            color = WorkbenchColors.Danger,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.more_trashbin_count, state.trashNodes.size),
            color = WorkbenchColors.InkStrong,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
        if (state.trashNodes.isEmpty()) {
            Text(
                text = stringResource(R.string.more_trashbin_empty),
                color = WorkbenchColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
        state.trashNodes.take(6).forEach { node ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WorkbenchColors.Surface.copy(alpha = 0.58f))
                    .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Eyebrow(node.area)
                Text(node.title, color = WorkbenchColors.InkStrong, fontSize = 16.sp, fontWeight = FontWeight.Black)
                ToolbarRow {
                    WorkbenchButton(stringResource(R.string.common_restore), { viewModel.restoreNode(node) }, primary = true)
                    WorkbenchButton(stringResource(R.string.common_delete_forever), { deleteForeverNodeId = node.id }, danger = true)
                }
            }
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
    val deleteNode = state.trashNodes.firstOrNull { it.id == deleteForeverNodeId }
    if (deleteNode != null) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.more_delete_forever_confirm_title),
            body = stringResource(R.string.more_delete_forever_confirm_body, deleteNode.title),
            confirmLabel = stringResource(R.string.common_delete_forever),
            onDismiss = { deleteForeverNodeId = null },
            onConfirm = {
                deleteForeverNodeId = null
                viewModel.permanentlyDeleteNode(deleteNode)
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
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(body, color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
        WorkbenchButton(actionLabel, onAction, primary = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ConfirmDestructiveDialog(
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
