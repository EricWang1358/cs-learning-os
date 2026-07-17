@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.cslearningos.mobile.R
import com.cslearningos.mobile.ui.backup.BackupDocument
import com.cslearningos.mobile.ui.backup.BackupTransferCoordinator
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDocument by remember { mutableStateOf<BackupDocument?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val document = pendingDocument
        pendingDocument = null
        if (uri != null && document != null) {
            scope.launch {
                runCatching {
                    BackupTransferCoordinator.writeToUri(context.contentResolver, uri, document)
                }.onSuccess {
                    viewModel.noteBackupSavedToDevice()
                }.onFailure { error ->
                    viewModel.showBackupError(error, R.string.message_backup_export_failed)
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    BackupTransferCoordinator.readImportedText(context.contentResolver, uri)
                }.onSuccess { rawJson ->
                    viewModel.restoreBackupFromJson(rawJson)
                }.onFailure { error ->
                    viewModel.showBackupError(error)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = stringResource(R.string.backup_eyebrow),
            title = stringResource(R.string.backup_title),
            body = stringResource(R.string.backup_body)
        )
        state.pendingPackageImport?.let { preview ->
            WorkbenchCard(accent = true) {
                Eyebrow(stringResource(R.string.sync_package_import_title))
                Text(
                    text = stringResource(
                        R.string.sync_package_summary,
                        preview.nodeCount,
                        preview.quizCount,
                        preview.captureCount,
                        preview.exportedAt.take(10)
                    ),
                    color = WorkbenchColors.InkStrong,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.sync_package_counts,
                        preview.added,
                        preview.updated,
                        preview.conflicted,
                        preview.skipped
                    ),
                    color = WorkbenchColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Text(
                    text = stringResource(R.string.sync_package_confirm_hint),
                    color = WorkbenchColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                ToolbarRow {
                    WorkbenchButton(
                        text = stringResource(R.string.sync_package_apply),
                        primary = true,
                        onClick = viewModel::confirmPackageImport
                    )
                    WorkbenchButton(
                        text = stringResource(R.string.sync_package_cancel),
                        onClick = viewModel::dismissPackageImport
                    )
                }
            }
        }
        WorkbenchCard(accent = true) {
            Eyebrow(stringResource(R.string.backup_restore_warning_title))
            Text(
                text = stringResource(R.string.backup_restore_warning_body),
                color = WorkbenchColors.Danger,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }
        ToolbarRow {
            WorkbenchButton(
                text = stringResource(R.string.backup_share_full),
                primary = true,
                onClick = {
                    scope.launch {
                        runCatching {
                            val document = viewModel.createBackupDocument()
                            val uri = BackupTransferCoordinator.writeShareFile(context, document)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = document.mimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, document.fileName)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    intent,
                                    context.getString(R.string.backup_share_chooser_title)
                                )
                            )
                        }.onSuccess {
                            viewModel.noteBackupShared()
                        }.onFailure { error ->
                            viewModel.showBackupError(error, R.string.message_backup_export_failed)
                        }
                    }
                }
            )
            WorkbenchButton(
                text = stringResource(R.string.backup_save_local),
                onClick = {
                    scope.launch {
                        runCatching { viewModel.createBackupDocument() }
                            .onSuccess { document ->
                                pendingDocument = document
                                saveLauncher.launch(document.fileName)
                            }
                            .onFailure { error ->
                                viewModel.showBackupError(error, R.string.message_backup_export_failed)
                            }
                    }
                }
            )
            WorkbenchButton(
                text = stringResource(R.string.backup_import_file),
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                danger = true
            )
        }
        CollapsibleWorkbenchSection(
            eyebrow = stringResource(R.string.backup_actions_eyebrow),
            title = stringResource(R.string.backup_help_title),
            body = stringResource(R.string.backup_help_body),
            expandLabel = stringResource(R.string.common_open),
            collapseLabel = stringResource(R.string.common_close),
            initiallyExpanded = screenHelpInitiallyExpanded(AppScreen.Backup)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaPill(stringResource(R.string.backup_share_full), ".txt / JSON")
                MetaPill(stringResource(R.string.backup_save_local), "QQ / WeChat / Files")
                MetaPill(stringResource(R.string.backup_import_file), ".txt / .json")
            }
        }
    }
}
