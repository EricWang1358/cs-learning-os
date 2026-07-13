package com.cslearningos.mobile.ui

import androidx.test.core.app.ApplicationProvider
import com.cslearningos.mobile.content.application.ContentCommandFailure
import com.cslearningos.mobile.content.application.ContentCommandPort
import com.cslearningos.mobile.content.application.ContentCommandResult
import com.cslearningos.mobile.content.application.SaveNodeCommand
import com.cslearningos.mobile.content.domain.ContentAreaRef
import com.cslearningos.mobile.content.domain.ContentNode
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.core.kernel.EntityRevision
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.SyncStatus
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LearningViewModelNodeSaveCompletionTest {
    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setUp() {
        dispatcher = kotlinx.coroutines.test.StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun staleSuccessDoesNotReplaceNewerPendingDraftOrConvertCapture() = runTest(dispatcher.scheduler) {
        val harness = harness()
        val viewModel = harness.viewModel

        viewModel.saveNode()
        runCurrent()
        val commandA = harness.port.singleCommand()
        viewModel.setEditorBody("# Draft B")
        viewModel.saveNode()
        runCurrent()
        val commandB = harness.port.commandExcept(commandA.commandId.value)

        harness.port.complete(commandA, ContentCommandResult.Success(contentNode(commandA.nodeId.value)))
        runCurrent()

        viewModel.state.value.also { state ->
            assertEquals(AppScreen.Editor, state.screen)
            assertEquals("# Draft B", state.editorBody)
            assertEquals(commandB.commandId.value, state.pendingNodeSave?.commandId)
        }
        assertEquals(0, harness.captureWrites)
        completeCurrentDraft(harness, commandB)
        runCurrent()
    }

    @Test
    fun staleFailureDoesNotOverwriteNewerPendingDraft() = runTest(dispatcher.scheduler) {
        val harness = harness()
        val viewModel = harness.viewModel

        viewModel.saveNode()
        runCurrent()
        val commandA = harness.port.singleCommand()
        viewModel.setEditorTitle("Draft B")
        viewModel.saveNode()
        runCurrent()
        val commandB = harness.port.commandExcept(commandA.commandId.value)

        harness.port.complete(commandA, ContentCommandResult.Failure(ContentCommandFailure.Storage("storage")))
        runCurrent()

        viewModel.state.value.also { state ->
            assertEquals(AppScreen.Editor, state.screen)
            assertEquals("Draft B", state.editorTitle)
            assertEquals(commandB.commandId.value, state.pendingNodeSave?.commandId)
            assertNull(state.message)
        }
        completeCurrentDraft(harness, commandB)
        runCurrent()
    }

    @Test
    fun matchingSuccessTransitionsAndConvertsCapture() = runTest(dispatcher.scheduler) {
        val harness = harness()

        harness.viewModel.saveNode()
        runCurrent()
        val command = harness.port.singleCommand()
        harness.port.complete(command, ContentCommandResult.Success(contentNode(command.nodeId.value)))
        runCurrent()

        assertEquals(AppScreen.Reader, harness.viewModel.state.value.screen)
        assertEquals(1, harness.captureWrites)
    }

    private fun completeCurrentDraft(harness: Harness, command: SaveNodeCommand) {
        harness.port.complete(command, ContentCommandResult.Success(contentNode(command.nodeId.value)))
    }

    private fun harness(): Harness {
        var captureWrites = 0
        val dao = Proxy.newProxyInstance(
            LearningDao::class.java.classLoader,
            arrayOf(LearningDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeAreas", "observeNodes", "observeTrashNodes", "observeQuizzes", "observeOpenReaderQuestions",
                "observeInboxCaptureSlips", "observeArchivedCaptureSlips", "observeDueQuizzes" -> kotlinx.coroutines.flow.flowOf(emptyList<Any>())
                "getNode" -> null
                "getCaptureSlip" -> sampleSlip()
                "upsertCaptureSlip" -> {
                    captureWrites += 1
                    Unit
                }
                else -> defaultReturn(method.returnType)
            }
        } as LearningDao
        val port = DelayedContentCommands()
        val initialState = LearningUiState(
            screen = AppScreen.Editor,
            editorAreaId = "systems",
            editorSourceCaptureSlipId = "capture-1",
            editorTitle = "Draft A",
            editorBody = "# Draft A"
        )
        val viewModel = LearningViewModel(
            application = ApplicationProvider.getApplicationContext(),
            repository = LearningRepository(dao, port),
            initialState = initialState
        )
        return Harness(viewModel, port) { captureWrites }
    }

    private fun defaultReturn(type: Class<*>): Any? = when {
        type == Boolean::class.javaPrimitiveType -> false
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        List::class.java.isAssignableFrom(type) -> emptyList<Any>()
        else -> null
    }

    private fun sampleSlip() = CaptureSlipEntity(
        id = "capture-1",
        body = "Capture",
        type = CaptureSlipType.question,
        topicHint = null,
        sourceLabel = null,
        linkedNodeId = null,
        status = CaptureSlipStatus.inbox,
        createdAt = 1L,
        updatedAt = 1L,
        revision = 1L,
        syncStatus = SyncStatus.clean,
        deletedAt = null
    )

    private fun contentNode(id: String) = ContentNode(
        id = NodeId(id), title = "Saved", markdownBody = "# Saved", createdAt = 1L, updatedAt = 1L,
        revision = EntityRevision(1L), deletedAt = null, area = ContentAreaRef("systems", "systems"),
        track = "general", order = 1_000, summary = "", visibility = "support", isStarter = false, isChecked = false
    )

    private data class Harness(
        val viewModel: LearningViewModel,
        val port: DelayedContentCommands,
        private val captureWriteCount: () -> Int
    ) {
        val captureWrites: Int get() = captureWriteCount()
    }

    private class DelayedContentCommands : ContentCommandPort {
        private val commands = mutableListOf<SaveNodeCommand>()
        private val results = ConcurrentHashMap<String, CompletableDeferred<ContentCommandResult>>()

        override suspend fun saveNode(command: SaveNodeCommand): ContentCommandResult {
            synchronized(commands) { commands += command }
            return results.getOrPut(command.commandId.value) { CompletableDeferred() }.await()
        }

        fun singleCommand(): SaveNodeCommand = synchronized(commands) { commands.single() }

        fun commandExcept(commandId: String): SaveNodeCommand = synchronized(commands) {
            commands.single { it.commandId.value != commandId }
        }

        fun complete(command: SaveNodeCommand, result: ContentCommandResult) {
            assertTrue(results.getOrPut(command.commandId.value) { CompletableDeferred() }.complete(result))
        }
    }
}
