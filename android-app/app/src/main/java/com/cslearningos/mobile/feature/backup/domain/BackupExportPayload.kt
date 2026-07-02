package com.cslearningos.mobile.feature.backup.domain

data class BackupExportPayload(
    val rawJson: String,
    val exportedAtMillis: Long
)
