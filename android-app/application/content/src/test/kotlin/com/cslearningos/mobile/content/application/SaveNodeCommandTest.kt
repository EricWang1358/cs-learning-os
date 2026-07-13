package com.cslearningos.mobile.content.application

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import com.cslearningos.mobile.content.domain.ContentAreaRef
import com.cslearningos.mobile.content.domain.ContentNode
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.core.kernel.CommandId
import com.cslearningos.mobile.core.kernel.EntityRevision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SaveNodeCommandTest {
    @Test
    fun createRequiresNoExpectedRevision() {
        assertThrows(IllegalArgumentException::class.java) {
            command(mode = NodeSaveMode.Create, expectedRevision = EntityRevision(3L))
        }
    }

    @Test
    fun updateRequiresExpectedRevision() {
        assertThrows(IllegalArgumentException::class.java) {
            command(mode = NodeSaveMode.Update, expectedRevision = null)
        }
    }

    @Test
    fun fingerprintIsDeterministicAndExcludesCommandIdentityAndTime() {
        val original = command(commandId = "command-1", occurredAt = 100L)
        val replay = command(commandId = "command-2", occurredAt = 200L)

        assertEquals(CommandFingerprint.of(original), CommandFingerprint.of(original))
        assertEquals(CommandFingerprint.of(original), CommandFingerprint.of(replay))
    }

    @Test
    fun fingerprintPayloadStartsWithStableContentNodeSaveType() {
        DataInputStream(ByteArrayInputStream(CommandFingerprint.encoded(command()))).use { input ->
            assertEquals("content.node.save", input.readLengthPrefixed())
            assertEquals(NodeSaveMode.Update.name, input.readLengthPrefixed())
        }
    }

    @Test
    fun fingerprintChangesForEveryIncludedField() {
        val original = command()
        val variants = listOf(
            command(mode = NodeSaveMode.Create, expectedRevision = null),
            command(nodeId = "node-2"),
            command(expectedRevision = EntityRevision(4L)),
            command(areaId = "area-2"),
            command(title = "Different title"),
            command(markdownBody = "Different body")
        )

        variants.forEach { variant ->
            assertNotEquals(CommandFingerprint.of(original), CommandFingerprint.of(variant))
        }
    }

    @Test
    fun fingerprintDistinguishesNullAndEmptyNullableFields() {
        val withNoArea = command(areaId = null)
        val withEmptyArea = command(areaId = "")
        val updateWithoutRevision = command(mode = NodeSaveMode.Create, expectedRevision = null)
        val updateWithZeroRevision = command(mode = NodeSaveMode.Update, expectedRevision = EntityRevision(0L))

        assertNotEquals(CommandFingerprint.of(withNoArea), CommandFingerprint.of(withEmptyArea))
        assertNotEquals(CommandFingerprint.of(updateWithoutRevision), CommandFingerprint.of(updateWithZeroRevision))
    }

    @Test
    fun contentCommandPortUsesStructuredResultAndDomainNode() {
        val node = contentNode()
        val success: ContentCommandResult = ContentCommandResult.Success(node)
        val validation: ContentCommandResult = ContentCommandResult.Failure(ContentCommandFailure.Validation("content.invalid"))
        val missing: ContentCommandResult = ContentCommandResult.Failure(ContentCommandFailure.Missing("area"))
        val deleted: ContentCommandResult = ContentCommandResult.Failure(ContentCommandFailure.Deleted)
        val stale: ContentCommandResult = ContentCommandResult.Failure(ContentCommandFailure.StaleRevision(4L, 5L))
        val reuse: ContentCommandResult = ContentCommandResult.Failure(ContentCommandFailure.CommandReuseConflict)
        val storage: ContentCommandResult = ContentCommandResult.Failure(ContentCommandFailure.Storage("database.unavailable"))
        val port = ContentCommandPort { ContentCommandResult.Success(node) }

        assertEquals(node, (success as ContentCommandResult.Success).node)
        assertEquals("content.invalid", ((validation as ContentCommandResult.Failure).failure as ContentCommandFailure.Validation).code)
        assertEquals("area", ((missing as ContentCommandResult.Failure).failure as ContentCommandFailure.Missing).target)
        assertEquals(ContentCommandFailure.Deleted, (deleted as ContentCommandResult.Failure).failure)
        assertEquals(ContentCommandFailure.StaleRevision(4L, 5L), (stale as ContentCommandResult.Failure).failure)
        assertEquals(ContentCommandFailure.CommandReuseConflict, (reuse as ContentCommandResult.Failure).failure)
        assertEquals("database.unavailable", ((storage as ContentCommandResult.Failure).failure as ContentCommandFailure.Storage).code)
        assertNotNull(port)
    }

    private fun command(
        commandId: String = "command-1",
        nodeId: String = "node-1",
        mode: NodeSaveMode = NodeSaveMode.Update,
        expectedRevision: EntityRevision? = EntityRevision(3L),
        areaId: String? = "area-1",
        title: String = "Original title",
        markdownBody: String = "Original body",
        occurredAt: Long = 100L
    ) = SaveNodeCommand(
        commandId = CommandId(commandId),
        nodeId = NodeId(nodeId),
        mode = mode,
        expectedRevision = expectedRevision,
        areaId = areaId,
        title = title,
        markdownBody = markdownBody,
        occurredAt = occurredAt
    )

    private fun contentNode() = ContentNode(
        id = NodeId("node-1"),
        title = "Original title",
        markdownBody = "Original body",
        createdAt = 1L,
        updatedAt = 2L,
        revision = EntityRevision(3L),
        deletedAt = null,
        area = ContentAreaRef("area-1", "fundamentals"),
        track = "general",
        order = 1,
        summary = "Original body",
        visibility = "support",
        isStarter = false,
        isChecked = false
    )

    private fun DataInputStream.readLengthPrefixed(): String {
        val bytes = ByteArray(readInt())
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }
}
