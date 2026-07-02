package com.cslearningos.mobile.feature.backup.ui

import com.cslearningos.mobile.ui.backup.BackupDocument

enum class BackupAction {
    ExportReady,
    RestoreCompleted
}

data class BackupUiState(
    val isExporting: Boolean = false,
    val isRestoring: Boolean = false,
    val pendingDocument: BackupDocument? = null,
    val errorKey: String? = null,
    val lastAction: BackupAction? = null
)
