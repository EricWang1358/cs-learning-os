package com.cslearningos.mobile.feature.backup.domain

import com.cslearningos.mobile.feature.backup.data.BackupRepository

class ExportBackupUseCase(
    private val repository: BackupRepository
) {
    suspend operator fun invoke(exportedAtMillis: Long): String =
        repository.exportBackup(exportedAtMillis)
}
