package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.CaptureNodeDraft
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.content.application.ContentCommandPort
import com.cslearningos.mobile.content.application.ContentCommandResult
import com.cslearningos.mobile.content.application.NodeSaveMode
import com.cslearningos.mobile.content.application.SaveNodeCommand
import com.cslearningos.mobile.content.domain.ContentAreaRef
import com.cslearningos.mobile.content.domain.ContentNode
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.core.kernel.CommandId
import com.cslearningos.mobile.core.kernel.EntityRevision
import com.cslearningos.mobile.feature.backup.ui.resetTransientStateAfterRestore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

    @Test
    fun capturePromotionAndAiDraftReplacementClearPendingIdentity() {
        val pending = LearningUiState(
            pendingNodeSave = PendingNodeSave("command", "node", "fingerprint")
        )
        val slip = CaptureSlipEntity(
            id = "capture-1",
            body = "What is a page fault?",
            type = CaptureSlipType.question,
            topicHint = "Virtual memory",
            sourceLabel = null,
            linkedNodeId = null,
            status = CaptureSlipStatus.inbox,
            createdAt = 10L,
            updatedAt = 10L,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )
        val captureDraft = CaptureNodeDraft(
            title = "Page faults",
            markdownBody = "# Page faults",
            suggestedAreaId = "systems",
            suggestedNodeId = null
        )

        assertNull(pending.forCapturePromotionEditor(slip, captureDraft).pendingNodeSave)
        assertNull(
            pending.forAiCaptureDraftEditor(
                slip = slip,
                areaId = "systems",
                title = "AI draft",
                body = "# AI draft"
            ).pendingNodeSave
        )
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun staleAsyncSaveCompletionDoesNotReplaceNewerEditorDraft() = runTest {
        val original = editableState().withPendingNodeSave()
        val pending = requireNotNull(original.pendingNodeSave)
        val port = DelayedContentCommands()
        val completion = async { port.saveNode(commandFor(original, pending)) }
        runCurrent()
        assertEquals(pending.commandId, port.receivedCommand.commandId.value)

        val newerDraft = original.withEditorBody("# Newer draft")
        port.complete(ContentCommandResult.Success(contentNode(pending.nodeId)))
        completion.await()

        val resultingState = newerDraft.afterNodeSavedIfPendingMatches(pending, sampleNode())

        assertEquals(AppScreen.Editor, resultingState.screen)
        assertEquals("# Newer draft", resultingState.editorBody)
        assertNull(resultingState.pendingNodeSave)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun staleAsyncSaveFailureDoesNotAddAnErrorToNewerEditorDraft() = runTest {
        val original = editableState().withPendingNodeSave()
        val pending = requireNotNull(original.pendingNodeSave)
        val port = DelayedContentCommands()
        val completion = async { port.saveNode(commandFor(original, pending)) }
        runCurrent()
        assertEquals(pending.commandId, port.receivedCommand.commandId.value)

        val newerDraft = original.withEditorTitle("Newer title")
        port.complete(ContentCommandResult.Failure(com.cslearningos.mobile.content.application.ContentCommandFailure.Storage("storage")))
        completion.await()

        val resultingState = newerDraft.withObjectSaveRejectedIfPendingMatches(pending)

        assertEquals("Newer title", resultingState.editorTitle)
        assertNull(resultingState.message)
    }

    @Test
    fun matchingAsyncSaveCompletionMovesToReader() {
        val original = editableState().withPendingNodeSave()
        val pending = requireNotNull(original.pendingNodeSave)

        val resultingState = original.afterNodeSavedIfPendingMatches(pending, sampleNode())

        assertEquals(AppScreen.Reader, resultingState.screen)
        assertNull(resultingState.pendingNodeSave)
    }

    @Test
    fun matchingAsyncSaveFailureRetainsPendingIdentityForRetry() {
        val original = editableState().withPendingNodeSave()
        val pending = requireNotNull(original.pendingNodeSave)

        val resultingState = original.withObjectSaveRejectedIfPendingMatches(pending)

        assertEquals(pending, resultingState.pendingNodeSave)
        assertNotNull(resultingState.message)
    }

    private fun editableState(): LearningUiState = LearningUiState(
        screen = AppScreen.Editor,
        editorAreaId = "systems",
        editorTitle = "Original",
        editorBody = "# Original"
    )

    private fun commandFor(state: LearningUiState, pending: PendingNodeSave): SaveNodeCommand = SaveNodeCommand(
        commandId = CommandId(pending.commandId),
        nodeId = NodeId(pending.nodeId),
        mode = NodeSaveMode.Create,
        expectedRevision = null,
        areaId = state.editorAreaId,
        title = state.editorTitle,
        markdownBody = state.editorBody,
        occurredAt = 100L
    )

    private fun contentNode(id: String): ContentNode = ContentNode(
        id = NodeId(id),
        title = "Original",
        markdownBody = "# Original",
        createdAt = 100L,
        updatedAt = 100L,
        revision = EntityRevision(1L),
        deletedAt = null,
        area = ContentAreaRef("systems", "systems"),
        track = "general",
        order = 1_000,
        summary = "",
        visibility = "support",
        isStarter = false,
        isChecked = false
    )

    private class DelayedContentCommands : ContentCommandPort {
        private val result = CompletableDeferred<ContentCommandResult>()
        lateinit var receivedCommand: SaveNodeCommand

        override suspend fun saveNode(command: SaveNodeCommand): ContentCommandResult {
            receivedCommand = command
            return result.await()
        }

        fun complete(value: ContentCommandResult) {
            result.complete(value)
        }
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
