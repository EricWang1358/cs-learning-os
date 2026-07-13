package com.cslearningos.mobile.content.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cslearningos.mobile.content.application.ContentCommandFailure
import com.cslearningos.mobile.content.application.ContentCommandResult
import com.cslearningos.mobile.content.application.NodeSaveMode
import com.cslearningos.mobile.content.application.SaveNodeCommand
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.core.kernel.CommandId
import com.cslearningos.mobile.core.kernel.EntityRevision
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningDatabase
import kotlinx.coroutines.test.runTest
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
class RoomContentCommandAdapterTest {
    private lateinit var database: LearningDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LearningDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createPersistsNodeProcessedCommandAndPendingOutbox() = runTest {
        seedArea()
        val adapter = adapter(changeId = "change-create")

        val result = adapter.saveNode(createCommand())

        val success = assertSuccess(result)
        assertEquals("node-1", success.id.value)
        assertEquals(1L, success.revision.value)
        assertEquals("Untitled", success.title)
        val dao = database.learningDao()
        assertEquals("Intro text", dao.getNode("node-1")?.summary)
        assertEquals("content.node.save", dao.getProcessedCommand("command-1")?.commandType)
        dao.getOutboxForCommand("command-1")!!.also { outbox ->
            assertEquals("change-create", outbox.changeId)
            assertEquals("content.node", outbox.aggregateType)
            assertEquals("node-1", outbox.aggregateId)
            assertEquals("create", outbox.operation)
            assertNull(outbox.baseRevision)
            assertEquals(1L, outbox.newRevision)
            assertEquals(ContentNodeCodec.SchemaVersion, outbox.domainSchemaVersion)
            assertEquals("pending", outbox.state)
            assertEquals(1_000L, outbox.createdAt)
            assertEquals(ContentNodeCodec.encode(success), outbox.payloadJson)
            assertEquals(ContentNodeCodec.sha256Hex(outbox.payloadJson), outbox.payloadHash)
        }
    }

    @Test
    fun legacyDefaultAreaIsCreatedInsideTheNodeCommandTransaction() = runTest {
        val adapter = adapter()

        val result = adapter.saveNode(createCommand(areaId = "questions"))

        assertSuccess(result)
        database.learningDao().getArea("questions")!!.also { area ->
            assertEquals("questions", area.slug)
            assertEquals("questions", area.name)
            assertEquals(10, area.order)
            assertEquals(1_000L, area.createdAt)
            assertEquals(1_000L, area.updatedAt)
            assertNull(area.deletedAt)
        }
    }

    @Test
    fun invalidLegacyDefaultAreaCreateLeavesNoTransactionSideEffects() = runTest {
        val result = adapter().saveNode(
            createCommand(
                areaId = "questions",
                title = "",
                markdown = ""
            )
        )

        assertFailure(ContentCommandFailure.Validation("content.empty"), result)
        val dao = database.learningDao()
        assertNull(dao.getArea("questions"))
        assertNull(dao.getNode("node-1"))
        assertNull(dao.getProcessedCommand("command-1"))
        assertNull(dao.getOutboxForCommand("command-1"))
    }

    @Test
    fun explicitCreateWithoutAreaRemainsAValidationFailure() = runTest {
        val result = adapter().saveNode(createCommand(areaId = null))

        assertFailure(ContentCommandFailure.Validation("create.missing_area"), result)
    }

    @Test
    fun updateUsesExpectedRevisionAndRetainsNodeIdentity() = runTest {
        seedArea()
        val adapter = adapter()
        adapter.saveNode(createCommand())

        val result = adapter.saveNode(
            createCommand(
                commandId = "command-2",
                mode = NodeSaveMode.Update,
                expectedRevision = 1L,
                title = "Updated",
                markdown = "Updated body",
                now = 2_000L
            )
        )

        val success = assertSuccess(result)
        assertEquals(2L, success.revision.value)
        assertEquals("Updated", database.learningDao().getNode("node-1")?.title)
        assertEquals("update", database.learningDao().getOutboxForCommand("command-2")?.operation)
    }

    @Test
    fun sameCommandAndFingerprintReplaysFirstSuccessWithoutAnotherOutboxItem() = runTest {
        seedArea()
        val adapter = adapter()
        val command = createCommand()
        val first = adapter.saveNode(command)

        val replay = adapter.saveNode(command)

        assertEquals(first, replay)
        assertEquals(1L, database.learningDao().getNode("node-1")?.revision)
        assertTrue(database.learningDao().getOutboxForCommand("command-1") != null)
    }

    @Test
    fun sameCommandIdWithDifferentFingerprintIsRejected() = runTest {
        seedArea()
        val adapter = adapter()
        adapter.saveNode(createCommand())

        val result = adapter.saveNode(createCommand(title = "Different"))

        assertFailure(ContentCommandFailure.CommandReuseConflict, result)
        assertEquals("Untitled", database.learningDao().getNode("node-1")?.title)
    }

    @Test
    fun staleRevisionIsRejectedBeforeWrites() = runTest {
        seedArea()
        val adapter = adapter()
        adapter.saveNode(createCommand())

        val result = adapter.saveNode(
            createCommand(
                commandId = "command-2",
                mode = NodeSaveMode.Update,
                expectedRevision = 0L,
                title = "Should not save"
            )
        )

        assertFailure(ContentCommandFailure.StaleRevision(expected = 0L, actual = 1L), result)
        assertNull(database.learningDao().getProcessedCommand("command-2"))
        assertNull(database.learningDao().getOutboxForCommand("command-2"))
    }

    @Test
    fun projectionFailureRollsBackNodeProcessedCommandAndOutbox() = runTest {
        seedArea()
        val adapter = RoomContentCommandAdapter(
            database = database,
            projectionWriter = ContentProjectionWriter { _, _, _ -> error("projection failed") },
            changeIdFactory = { "change-failure" }
        )

        val result = adapter.saveNode(createCommand())

        assertFailure(ContentCommandFailure.Storage("content.command.storage"), result)
        val dao = database.learningDao()
        assertNull(dao.getNode("node-1"))
        assertNull(dao.getProcessedCommand("command-1"))
        assertNull(dao.getOutboxForCommand("command-1"))
    }

    private suspend fun seedArea() {
        database.learningDao().upsertArea(
            AreaEntity(
                id = "area-1",
                slug = "systems",
                name = "Systems",
                order = 1,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
    }

    private fun adapter(changeId: String = "change-default"): RoomContentCommandAdapter {
        var sequence = 0
        return RoomContentCommandAdapter(
            database = database,
            changeIdFactory = {
                if (sequence++ == 0) changeId else "$changeId-$sequence"
            }
        )
    }

    private fun createCommand(
        commandId: String = "command-1",
        mode: NodeSaveMode = NodeSaveMode.Create,
        expectedRevision: Long? = null,
        title: String = "",
        markdown: String = "Intro text",
        areaId: String? = "area-1",
        now: Long = 1_000L
    ) = SaveNodeCommand(
        commandId = CommandId(commandId),
        nodeId = NodeId("node-1"),
        mode = mode,
        expectedRevision = expectedRevision?.let(::EntityRevision),
        areaId = areaId,
        title = title,
        markdownBody = markdown,
        occurredAt = now
    )

    private fun assertSuccess(result: ContentCommandResult) =
        (result as? ContentCommandResult.Success)?.node
            ?: throw AssertionError("Expected success but was $result")

    private fun assertFailure(expected: ContentCommandFailure, result: ContentCommandResult) {
        assertEquals(ContentCommandResult.Failure(expected), result)
    }
}
