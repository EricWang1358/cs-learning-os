@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoreScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "settings",
            title = "More",
            body = "System tools live here: AI provider, readable export, full backup, import safety, and support."
        )
        AiProviderPanel(state = state, viewModel = viewModel)
        DataToolsPanel(viewModel = viewModel)
        SupportPanel()
    }
}

@Composable
private fun AiProviderPanel(state: LearningUiState, viewModel: LearningViewModel) {
    val settings = state.aiProviderSettings
    WorkbenchCard(accent = true) {
        Eyebrow("service")
        Text("LLM model for capture drafts", color = WorkbenchColors.InkStrong, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            text = "Saved on this phone. Capture slips are never sent unless you explicitly run a future AI draft action.",
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        SettingsRow(label = "Provider") {
            WorkbenchTextField(settings.provider, viewModel::setAiProvider, "DeepSeek / OpenAI compatible")
        }
        SettingsRow(label = "API key") {
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
        SettingsRow(label = "Base URL") {
            WorkbenchTextField(settings.baseUrl, viewModel::setAiBaseUrl, "https://api.deepseek.com/v1")
        }
        SettingsRow(label = "Model") {
            WorkbenchTextField(settings.model, viewModel::setAiModel, "deepseek-v4-flash")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(checked = settings.thinkingEnabled, onCheckedChange = viewModel::setAiThinkingEnabled)
                Text(
                    text = if (settings.thinkingEnabled) "Thinking on" else "Thinking off",
                    color = WorkbenchColors.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        SettingsRow(label = "Connection") {
            ToolbarRow {
                WorkbenchButton("Validate", viewModel::validateAiSettings, primary = true)
                WorkbenchButton("Pull models", viewModel::pullAiModels)
            }
        }
    }
}

@Composable
private fun DataToolsPanel(viewModel: LearningViewModel) {
    WorkbenchCard {
        Eyebrow("local data")
        Text("Export, backup, and import", color = WorkbenchColors.InkStrong, fontSize = 21.sp, fontWeight = FontWeight.Black)
        Text(
            text = "Readable Markdown/TXT export is separate from full JSON backup. Restore remains a full-phone recovery tool.",
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        ToolbarRow {
            WorkbenchButton("Full backup", viewModel::showBackup, primary = true)
            WorkbenchButton("Markdown export soon", viewModel::showBackup)
            WorkbenchButton("Import preview soon", viewModel::showBackup)
        }
    }
}

@Composable
private fun SupportPanel() {
    WorkbenchCard {
        Eyebrow("support")
        Text("Local-first contract", color = WorkbenchColors.InkStrong, fontSize = 21.sp, fontWeight = FontWeight.Black)
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
            .background(WorkbenchColors.Surface.copy(alpha = 0.48f))
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
