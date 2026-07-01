@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoreScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val systemLanguageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val copy = moreSettingsCopy(state.systemLanguage, systemLanguageTag)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = copy.eyebrow,
            title = copy.title,
            body = copy.body
        )
        MoreSettingsList(state = state, viewModel = viewModel, copy = copy, systemLanguageTag = systemLanguageTag)
    }
}

@Composable
private fun MoreSettingsList(
    state: LearningUiState,
    viewModel: LearningViewModel,
    copy: MoreSettingsCopy,
    systemLanguageTag: String
) {
    val sections = moreSectionSummaries(
        language = state.systemLanguage,
        appearance = state.appearanceMode,
        aiConfigured = state.aiProviderSettings.isConfigured,
        effectiveLanguage = resolveSystemLanguage(state.systemLanguage, systemLanguageTag)
    )
    WorkbenchCard {
        sections.forEach { section ->
            MoreSectionRow(
                section = section,
                expanded = state.expandedMoreSection == section.id,
                copy = copy,
                onToggle = { viewModel.toggleMoreSection(section.id) }
            ) {
                when (section.id) {
                    MoreSectionId.System -> SystemSettingsContent(state = state, viewModel = viewModel, copy = copy)
                    MoreSectionId.Service -> AiProviderContent(state = state, viewModel = viewModel, copy = copy)
                    MoreSectionId.Notifications -> NotificationsContent(state = state, viewModel = viewModel)
                    MoreSectionId.Data -> DataToolsContent(state = state, viewModel = viewModel, copy = copy)
                    MoreSectionId.Support -> SupportContent(copy = copy)
                }
            }
        }
    }
}

@Composable
private fun MoreSectionRow(
    section: MoreSectionSummary,
    expanded: Boolean,
    copy: MoreSettingsCopy,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (expanded) WorkbenchColors.Surface.copy(alpha = 0.62f) else WorkbenchColors.Surface.copy(alpha = 0.34f))
            .border(
                BorderStroke(1.dp, if (expanded) WorkbenchColors.Accent.copy(alpha = 0.56f) else WorkbenchColors.Line),
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
                Text(if (expanded) copy.collapse else copy.expand, color = WorkbenchColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
        if (expanded) {
            content()
        }
    }
}

@Composable
private fun NotificationsContent(state: LearningUiState, viewModel: LearningViewModel) {
    SettingsRow(label = "Task inbox") {
        if (state.notices.isEmpty()) {
            Text(
                text = "No notifications yet. Capture and AI draft progress will appear here.",
                color = WorkbenchColors.Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
        state.notices.forEach { notice ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WorkbenchColors.Surface.copy(alpha = 0.58f))
                    .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Eyebrow("notice")
                Text(notice.title, color = WorkbenchColors.InkStrong, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(notice.body, color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
                WorkbenchButton("Dismiss", { viewModel.dismissNotice(notice.id) })
            }
        }
    }
}

@Composable
private fun SystemSettingsContent(state: LearningUiState, viewModel: LearningViewModel, copy: MoreSettingsCopy) {
    SettingsRow(label = copy.systemLanguage) {
        ToolbarRow {
            SystemLanguage.entries.forEach { language ->
                WorkbenchButton(
                    text = language.label,
                    onClick = { viewModel.setSystemLanguage(language) },
                    primary = state.systemLanguage == language
                )
            }
        }
        Text(
            text = copy.languageNote,
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
    SettingsRow(label = copy.appearance) {
        ToolbarRow {
            AppearanceMode.entries.forEach { mode ->
                WorkbenchButton(
                    text = mode.label,
                    onClick = { viewModel.setAppearanceMode(mode) },
                    primary = state.appearanceMode == mode
                )
            }
        }
        Text(
            text = copy.appearanceNote,
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun AiProviderContent(state: LearningUiState, viewModel: LearningViewModel, copy: MoreSettingsCopy) {
    val settings = state.aiProviderSettings
    AiServiceStatusBlock(status = state.aiServiceStatus)
    SettingsRow(label = copy.provider) {
        WorkbenchTextField(settings.provider, viewModel::setAiProvider, "DeepSeek / OpenAI compatible")
        Text(
            text = "Auto-saved on this phone. Save confirms the current fields; Validate tests /models; Pull lists selectable model IDs.",
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
    SettingsRow(label = copy.apiKey) {
        if (settings.apiKeyVisible) {
            WorkbenchTextField(
                value = settings.apiKey,
                onValueChange = viewModel::setAiApiKey,
                label = "sk-...",
                minLines = 1
            )
        } else {
            Text(
                text = if (settings.apiKey.isBlank()) "Not configured" else settings.apiKey.maskSecret(),
                color = WorkbenchColors.InkStrong,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        ToolbarRow {
            WorkbenchButton(if (settings.apiKeyVisible) "Hide" else "Show", viewModel::toggleAiKeyVisibility)
        }
    }
    SettingsRow(label = copy.baseUrl) {
        WorkbenchTextField(settings.baseUrl, viewModel::setAiBaseUrl, "https://api.deepseek.com/v1")
    }
    SettingsRow(label = copy.model) {
        WorkbenchTextField(settings.model, viewModel::setAiModel, "deepseek-v4-flash")
        if (state.availableAiModels.isNotEmpty()) {
            Text(
                text = "Pulled models",
                color = WorkbenchColors.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            ToolbarRow {
                state.availableAiModels.take(8).forEach { modelId ->
                    WorkbenchButton(
                        text = modelId,
                        onClick = { viewModel.selectAiModel(modelId) },
                        primary = settings.model == modelId
                    )
                }
            }
        }
        ToolbarRow {
            WorkbenchButton(
                text = "Thinking on",
                onClick = { viewModel.setAiThinkingEnabled(true) },
                primary = settings.thinkingEnabled
            )
            WorkbenchButton(
                text = "Thinking off",
                onClick = { viewModel.setAiThinkingEnabled(false) },
                primary = !settings.thinkingEnabled
            )
        }
    }
    SettingsRow(label = copy.connection) {
        ToolbarRow {
            WorkbenchButton("Save settings", viewModel::saveAiSettings, enabled = !state.aiBusy)
            WorkbenchButton(copy.validate, viewModel::validateAiSettings, primary = true, enabled = !state.aiBusy)
            WorkbenchButton("Models", viewModel::pullAiModels, enabled = !state.aiBusy)
        }
    }
}

@Composable
private fun AiServiceStatusBlock(status: AiServiceStatus) {
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
        Eyebrow("service status")
        Text(status.title, color = WorkbenchColors.InkStrong, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(status.body, color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun DataToolsContent(state: LearningUiState, viewModel: LearningViewModel, copy: MoreSettingsCopy) {
    SettingsRow(label = copy.localData) {
        Text(
            text = "Readable Markdown/TXT export is separate from full JSON backup. Trashbin keeps deleted nodes recoverable before permanent deletion.",
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        ToolbarRow {
            WorkbenchButton("Backup JSON", viewModel::showBackup, primary = true)
            WorkbenchButton("Export MD/TXT", viewModel::exportReadableMarkdown)
            WorkbenchButton("Restore JSON", viewModel::showBackup)
            WorkbenchButton("Remove demo", viewModel::clearStarterContent, danger = true)
        }
        Text(
            text = "Trashbin: ${state.trashNodes.size} node(s)",
            color = WorkbenchColors.InkStrong,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
        if (state.trashNodes.isEmpty()) {
            Text(
                text = "No trashed nodes. Deleting from Reader moves a node here first.",
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
                    WorkbenchButton("Restore", { viewModel.restoreNode(node) }, primary = true)
                    WorkbenchButton("Delete forever", { viewModel.permanentlyDeleteNode(node) }, danger = true)
                }
            }
        }
    }
}

@Composable
private fun SupportContent(copy: MoreSettingsCopy) {
    SettingsRow(label = copy.supportContract) {
        Text(
            text = "The app is usable without an account, backend, or API key. AI and sync are adapters, not the source of truth.",
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
    }
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
