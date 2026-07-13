package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.feature.backup.ui.resetTransientStateAfterRestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class NodeSavePendingStateTest {
    @Test
    fun unchangedSaveRetryReusesCommandAndNodeIdentity() {
        val editor = LearningUiState(
            screen = AppScreen.Editor,
            editorAreaId = "systems",
            editorTitle = "Virtual memory",
            editorBody = "# Virtual memory"
        )

        val first = editor.withPendingNodeSave()
        val retry = first.withPendingNodeSave()

        assertNotNull(first.pendingNodeSave)
        assertEquals(first.pendingNodeSave, retry.pendingNodeSave)
    }

    @Test
    fun nodeContentEditsClearPendingIdentity() {
        val pending = LearningUiState(
            editorAreaId = "systems",
            editorTitle = "Virtual memory",
            editorBody = "# Virtual memory"
        ).withPendingNodeSave()

        assertNull(pending.withEditorTitle("Paging").pendingNodeSave)
        assertNull(pending.withEditorBody("# Paging").pendingNodeSave)
        assertNull(pending.withEditorAreaId("networks").pendingNodeSave)
    }

    @Test
    fun saveCancelAndRestoreClearPendingIdentity() {
        val pending = LearningUiState(
            screen = AppScreen.Editor,
            editorAreaId = "systems",
            editorTitle = "Virtual memory",
            editorBody = "# Virtual memory"
        ).withPendingNodeSave()

        assertNull(pending.afterNodeSaved(sampleNode()).pendingNodeSave)
        assertNull(pending.cancelNodeEditor().pendingNodeSave)
        assertNull(pending.resetTransientStateAfterRestore().pendingNodeSave)
    }

    @Test
    fun editorEntryStartsWithCleanPendingIdentity() {
        val pending = LearningUiState(
            pendingNodeSave = PendingNodeSave("command", "node", "fingerprint")
        )

        assertNull(pending.forNewNodeEditor("systems").pendingNodeSave)
        assertNull(pending.forExistingNodeEditor(sampleNode()).pendingNodeSave)
    }

    private fun sampleNode(): LearningNodeEntity = LearningNodeEntity(
        id = "node-1",
        title = "Virtual memory",
        markdownBody = "# Virtual memory",
        createdAt = 10L,
        updatedAt = 20L,
        lastReadAt = null,
        revision = 3L,
        syncStatus = SyncStatus.clean,
        deletedAt = null,
        area = "systems",
        areaId = "systems",
        track = "general",
        order = 1,
        summary = "Virtual memory",
        visibility = "support",
        isStarter = false,
        isChecked = false
    )
}
