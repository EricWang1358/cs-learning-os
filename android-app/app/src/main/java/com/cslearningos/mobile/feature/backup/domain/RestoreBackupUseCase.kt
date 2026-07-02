package com.cslearningos.mobile.feature.backup.domain

import com.cslearningos.mobile.feature.backup.data.BackupRepository

class RestoreBackupUseCase(
    private val repository: BackupRepository
) {
    suspend operator fun invoke(rawJson: String) {
        repository.restoreBackup(rawJson)
    }
}
