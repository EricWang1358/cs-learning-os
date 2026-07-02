package com.cslearningos.mobile.feature.backup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cslearningos.mobile.feature.backup.domain.ExportBackupUseCase
import com.cslearningos.mobile.feature.backup.domain.RestoreBackupUseCase
import com.cslearningos.mobile.ui.backup.BackupTransferCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BackupViewModel(
    private val exportBackupUseCase: ExportBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = mutableUiState.asStateFlow()

    fun exportBackup(exportedAtMillis: Long) {
        mutableUiState.update { current ->
            current.copy(isExporting = true, errorKey = null)
        }
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                val rawJson = exportBackupUseCase(exportedAtMillis)
                BackupTransferCoordinator.createBackupDocument(
                    rawJson = rawJson,
                    exportedAt = exportedAtMillis
                )
            }.onSuccess { document ->
                mutableUiState.update { current ->
                    current.copy(
                        isExporting = false,
                        pendingDocument = document,
                        errorKey = null,
                        lastAction = BackupAction.ExportReady
                    )
                }
            }.onFailure {
                mutableUiState.update { current ->
                    current.copy(
                        isExporting = false,
                        errorKey = "backup_export_failed"
                    )
                }
            }
        }
    }

    fun restoreBackup(rawJson: String) {
        mutableUiState.update { current ->
            current.copy(isRestoring = true, errorKey = null)
        }
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                restoreBackupUseCase(rawJson)
            }.onSuccess {
                mutableUiState.update { current ->
                    current.copy(
                        isRestoring = false,
                        errorKey = null,
                        lastAction = BackupAction.RestoreCompleted
                    )
                }
            }.onFailure {
                mutableUiState.update { current ->
                    current.copy(
                        isRestoring = false,
                        errorKey = "backup_restore_failed"
                    )
                }
            }
        }
    }
}
