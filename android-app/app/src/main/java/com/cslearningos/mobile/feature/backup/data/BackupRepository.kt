package com.cslearningos.mobile.feature.backup.data

interface BackupRepository {
    suspend fun exportBackup(exportedAtMillis: Long): String

    suspend fun restoreBackup(rawJson: String)
}
