package com.cslearningos.mobile.ui.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class BackupDocument(
    val fileName: String,
    val mimeType: String,
    val content: String
)

object BackupTransferCoordinator {
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())

    fun createBackupDocument(
        rawJson: String,
        exportedAt: Long = System.currentTimeMillis()
    ): BackupDocument {
        val timestamp = fileNameFormatter.format(Instant.ofEpochMilli(exportedAt))
        return BackupDocument(
            fileName = "cs-learning-os-backup-$timestamp.txt",
            mimeType = "text/plain",
            content = rawJson
        )
    }

    fun writeShareFile(context: Context, document: BackupDocument): Uri {
        val directory = File(context.cacheDir, "shared-backups").apply { mkdirs() }
        val file = File(directory, document.fileName)
        file.writeText(document.content, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun writeToUri(contentResolver: ContentResolver, uri: Uri, document: BackupDocument) {
        val outputStream = contentResolver.openOutputStream(uri)
            ?: throw IOException("Could not open backup destination.")
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(document.content)
        }
    }

    fun readImportedText(contentResolver: ContentResolver, uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open selected backup file.")
        return inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
    }
}
