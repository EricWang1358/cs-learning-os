package com.cslearningos.mobile.feature.backup.ui

import com.cslearningos.mobile.feature.backup.data.BackupRepository
import com.cslearningos.mobile.feature.backup.domain.ExportBackupUseCase
import com.cslearningos.mobile.feature.backup.domain.RestoreBackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun exportBackupBuildsPendingDocumentAndRestoreDelegatesToRepository() = runTest {
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeBackupRepository(exportedJson = """{"schemaVersion":1}""")
            val viewModel = BackupViewModel(
                exportBackupUseCase = ExportBackupUseCase(repository),
                restoreBackupUseCase = RestoreBackupUseCase(repository),
                ioDispatcher = dispatcher
            )

            viewModel.exportBackup(exportedAtMillis = EXPORTED_AT)
            dispatcher.scheduler.advanceUntilIdle()

            val exportedState = viewModel.uiState.value
            assertFalse(exportedState.isExporting)
            assertNull(exportedState.errorKey)
            assertTrue(exportedState.pendingDocument != null)
            assertEquals("""{"schemaVersion":1}""", exportedState.pendingDocument?.content)
            assertEquals("text/plain", exportedState.pendingDocument?.mimeType)
            assertEquals(BackupAction.ExportReady, exportedState.lastAction)

            viewModel.restoreBackup(RAW_JSON_TO_RESTORE)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(RAW_JSON_TO_RESTORE, repository.restoredJson)
            assertFalse(viewModel.uiState.value.isRestoring)
            assertEquals(BackupAction.RestoreCompleted, viewModel.uiState.value.lastAction)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeBackupRepository(
        private val exportedJson: String
    ) : BackupRepository {
        var restoredJson: String? = null

        override suspend fun exportBackup(exportedAtMillis: Long): String = exportedJson

        override suspend fun restoreBackup(rawJson: String) {
            restoredJson = rawJson
        }
    }

    private companion object {
        const val EXPORTED_AT = 1_720_123_456_000L
        const val RAW_JSON_TO_RESTORE = """{"schemaVersion":1,"nodes":[],"quizzes":[],"reviewStates":[],"attempts":[]}"""
    }
}
