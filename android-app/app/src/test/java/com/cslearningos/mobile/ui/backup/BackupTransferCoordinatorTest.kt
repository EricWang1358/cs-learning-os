package com.cslearningos.mobile.ui.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTransferCoordinatorTest {
    @Test
    fun createBackupDocumentUsesTxtFilenameAndPlainTextMime() {
        val document = BackupTransferCoordinator.createBackupDocument(
            rawJson = """{"schemaVersion":1}""",
            exportedAt = 1_720_123_456_000L
        )

        assertTrue(document.fileName.startsWith("cs-learning-os-backup-"))
        assertTrue(document.fileName.endsWith(".txt"))
        assertEquals("text/plain", document.mimeType)
        assertEquals("""{"schemaVersion":1}""", document.content)
    }
}
