package com.cslearningos.mobile.feature.backup.data

import com.cslearningos.mobile.data.LearningRepository

class LearningRepositoryBackupRepository(
    private val repository: LearningRepository
) : BackupRepository {
    override suspend fun exportBackup(exportedAtMillis: Long): String =
        repository.exportBackup(now = exportedAtMillis)

    override suspend fun restoreBackup(rawJson: String) {
        repository.restoreBackup(rawJson)
    }
}
