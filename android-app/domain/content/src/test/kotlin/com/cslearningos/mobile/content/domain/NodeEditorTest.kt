package com.cslearningos.mobile.content.domain

import com.cslearningos.mobile.core.kernel.EntityRevision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeEditorTest {
    @Test
    fun createBuildsNewNodeWithDomainDefaults() {
        val decision = NodeEditor.save(
            existing = null,
            expectedRevision = null,
            nodeId = NodeId("node-1"),
            area = ContentAreaRef(id = "area-1", slug = "fundamentals"),
            title = "Binary Search",
            markdownBody = "# Binary Search\n\nFind the middle element.",
            now = 1_000L
        )

        val accepted = decision as NodeSaveDecision.Accepted
        assertEquals(ContentOperation.Create, accepted.operation)
        assertEquals(NodeId("node-1"), accepted.node.id)
        assertEquals("Binary Search", accepted.node.title)
        assertEquals("# Binary Search\n\nFind the middle element.", accepted.node.markdownBody)
        assertEquals(1_000L, accepted.node.createdAt)
        assertEquals(1_000L, accepted.node.updatedAt)
        assertEquals(EntityRevision(1L), accepted.node.revision)
        assertNull(accepted.node.deletedAt)
        assertEquals(ContentAreaRef("area-1", "fundamentals"), accepted.node.area)
        assertEquals("general", accepted.node.track)
        assertEquals(1_000, accepted.node.order)
        assertEquals("Find the middle element.", accepted.node.summary)
        assertEquals("support", accepted.node.visibility)
        assertFalse(accepted.node.isStarter)
        assertFalse(accepted.node.isChecked)
    }

    @Test
    fun updatePreservesStableFieldsAndAdvancesRevisionOnce() {
        val existing = existingNode()
        val decision = NodeEditor.save(
            existing = existing,
            expectedRevision = EntityRevision(7L),
            nodeId = existing.id,
            area = ContentAreaRef("area-2", "algorithms"),
            title = "Updated title",
            markdownBody = "Updated body",
            now = 2_000L
        )

        val accepted = decision as NodeSaveDecision.Accepted
        assertEquals(ContentOperation.Update, accepted.operation)
        assertEquals(
            existing.copy(
                title = "Updated title",
                markdownBody = "Updated body",
                updatedAt = 2_000L,
                revision = EntityRevision(8L),
                area = ContentAreaRef("area-2", "algorithms")
            ),
            accepted.node
        )
    }

    @Test
    fun staleUpdateIsRejectedWithExpectedAndActualRevisions() {
        val existing = existingNode()

        val decision = NodeEditor.save(
            existing = existing,
            expectedRevision = EntityRevision(6L),
            nodeId = existing.id,
            area = existing.area,
            title = "Changed",
            markdownBody = "Changed body",
            now = 2_000L
        )

        assertEquals(
            NodeSaveDecision.Rejected(
                ContentFailure.StaleRevision(
                    expected = EntityRevision(6L),
                    actual = EntityRevision(7L)
                )
            ),
            decision
        )
    }

    @Test
    fun exhaustedRevisionIsRejectedWithoutThrowing() {
        val existing = existingNode().copy(revision = EntityRevision(Long.MAX_VALUE))

        val decision = NodeEditor.save(
            existing = existing,
            expectedRevision = existing.revision,
            nodeId = existing.id,
            area = existing.area,
            title = "Changed",
            markdownBody = "Changed body",
            now = 2_000L
        )

        assertEquals(
            NodeSaveDecision.Rejected(
                ContentFailure.Validation("node.revision_exhausted")
            ),
            decision
        )
    }

    @Test
    fun deletedNodeCannotBeUpdated() {
        val existing = existingNode().copy(deletedAt = 1_500L)

        val decision = NodeEditor.save(
            existing = existing,
            expectedRevision = existing.revision,
            nodeId = existing.id,
            area = existing.area,
            title = "Changed",
            markdownBody = "Changed body",
            now = 2_000L
        )

        assertEquals(NodeSaveDecision.Rejected(ContentFailure.Deleted), decision)
    }

    @Test
    fun updateWithoutExpectedRevisionIsRejected() {
        val existing = existingNode()

        val decision = NodeEditor.save(
            existing = existing,
            expectedRevision = null,
            nodeId = existing.id,
            area = existing.area,
            title = "Changed",
            markdownBody = "Changed body",
            now = 2_000L
        )

        assertEquals(
            NodeSaveDecision.Rejected(ContentFailure.Validation("update.missing_revision")),
            decision
        )
    }

    @Test
    fun blankTitleAndBodyAreRejected() {
        val decision = NodeEditor.save(
            existing = null,
            expectedRevision = null,
            nodeId = NodeId("node-1"),
            area = ContentAreaRef("area-1", "fundamentals"),
            title = " \t",
            markdownBody = "\n ",
            now = 1_000L
        )

        assertEquals(
            NodeSaveDecision.Rejected(ContentFailure.Validation("content.empty")),
            decision
        )
    }

    @Test
    fun blankTitleFallsBackAndSummaryUsesFirstNonHeadingContentLine() {
        val decision = NodeEditor.save(
            existing = null,
            expectedRevision = null,
            nodeId = NodeId("node-1"),
            area = ContentAreaRef("area-1", "fundamentals"),
            title = "  ",
            markdownBody = "\n# Heading\n  ## Subheading\n\n  First useful line.  \nSecond line.",
            now = 1_000L
        )

        val node = (decision as NodeSaveDecision.Accepted).node
        assertEquals("Untitled", node.title)
        assertEquals("First useful line.", node.summary)
    }

    @Test
    fun createWithExpectedRevisionIsRejected() {
        val decision = NodeEditor.save(
            existing = null,
            expectedRevision = EntityRevision(1L),
            nodeId = NodeId("node-1"),
            area = ContentAreaRef("area-1", "fundamentals"),
            title = "Title",
            markdownBody = "Body",
            now = 1_000L
        )

        assertTrue(decision is NodeSaveDecision.Rejected)
        assertEquals(
            ContentFailure.Validation("create.unexpected_revision"),
            (decision as NodeSaveDecision.Rejected).failure
        )
    }

    @Test
    fun existingNodeIdMustMatchRequestedNodeId() {
        val existing = existingNode()

        val decision = NodeEditor.save(
            existing = existing,
            expectedRevision = existing.revision,
            nodeId = NodeId("another-node"),
            area = existing.area,
            title = "Changed",
            markdownBody = "Changed body",
            now = 2_000L
        )

        assertEquals(
            NodeSaveDecision.Rejected(ContentFailure.Validation("node.id_mismatch")),
            decision
        )
    }

    private fun existingNode() = ContentNode(
        id = NodeId("node-1"),
        title = "Original title",
        markdownBody = "Original body",
        createdAt = 500L,
        updatedAt = 900L,
        revision = EntityRevision(7L),
        deletedAt = null,
        area = ContentAreaRef("area-1", "fundamentals"),
        track = "systems",
        order = 42,
        summary = "Stable summary",
        visibility = "core",
        isStarter = true,
        isChecked = true
    )
}
