package com.cslearningos.mobile.content.domain

import com.cslearningos.mobile.core.kernel.EntityRevision

enum class ContentOperation {
    Create,
    Update
}

sealed interface ContentFailure {
    data class Validation(val code: String) : ContentFailure
    data object Deleted : ContentFailure
    data class StaleRevision(
        val expected: EntityRevision,
        val actual: EntityRevision
    ) : ContentFailure
}

sealed interface NodeSaveDecision {
    data class Accepted(
        val node: ContentNode,
        val operation: ContentOperation
    ) : NodeSaveDecision

    data class Rejected(val failure: ContentFailure) : NodeSaveDecision
}

object NodeEditor {
    fun save(
        existing: ContentNode?,
        expectedRevision: EntityRevision?,
        nodeId: NodeId,
        area: ContentAreaRef,
        title: String,
        markdownBody: String,
        now: Long
    ): NodeSaveDecision {
        if (title.isBlank() && markdownBody.isBlank()) {
            return NodeSaveDecision.Rejected(ContentFailure.Validation("content.empty"))
        }

        return if (existing == null) {
            create(expectedRevision, nodeId, area, title, markdownBody, now)
        } else {
            update(existing, expectedRevision, nodeId, area, title, markdownBody, now)
        }
    }

    private fun create(
        expectedRevision: EntityRevision?,
        nodeId: NodeId,
        area: ContentAreaRef,
        title: String,
        markdownBody: String,
        now: Long
    ): NodeSaveDecision {
        if (expectedRevision != null) {
            return NodeSaveDecision.Rejected(
                ContentFailure.Validation("create.unexpected_revision")
            )
        }

        return NodeSaveDecision.Accepted(
            node = ContentNode(
                id = nodeId,
                title = title.ifBlank { UNTITLED },
                markdownBody = markdownBody,
                createdAt = now,
                updatedAt = now,
                revision = EntityRevision(1L),
                deletedAt = null,
                area = area,
                track = DEFAULT_TRACK,
                order = DEFAULT_ORDER,
                summary = deriveSummary(markdownBody),
                visibility = SUPPORT_VISIBILITY,
                isStarter = false,
                isChecked = false
            ),
            operation = ContentOperation.Create
        )
    }

    private fun update(
        existing: ContentNode,
        expectedRevision: EntityRevision?,
        nodeId: NodeId,
        area: ContentAreaRef,
        title: String,
        markdownBody: String,
        now: Long
    ): NodeSaveDecision {
        if (existing.id != nodeId) {
            return NodeSaveDecision.Rejected(ContentFailure.Validation("node.id_mismatch"))
        }
        if (existing.deletedAt != null) {
            return NodeSaveDecision.Rejected(ContentFailure.Deleted)
        }
        if (expectedRevision == null) {
            return NodeSaveDecision.Rejected(
                ContentFailure.Validation("update.missing_revision")
            )
        }
        if (expectedRevision != existing.revision) {
            return NodeSaveDecision.Rejected(
                ContentFailure.StaleRevision(
                    expected = expectedRevision,
                    actual = existing.revision
                )
            )
        }
        if (existing.revision.value == Long.MAX_VALUE) {
            return NodeSaveDecision.Rejected(
                ContentFailure.Validation("node.revision_exhausted")
            )
        }

        return NodeSaveDecision.Accepted(
            node = existing.copy(
                title = title.ifBlank { UNTITLED },
                markdownBody = markdownBody,
                updatedAt = now,
                revision = EntityRevision(existing.revision.value + 1L),
                area = area
            ),
            operation = ContentOperation.Update
        )
    }

    private fun deriveSummary(markdownBody: String): String = markdownBody.lineSequence()
        .firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }
        ?.trim()
        .orEmpty()

    private const val UNTITLED = "Untitled"
    private const val DEFAULT_TRACK = "general"
    private const val DEFAULT_ORDER = 1_000
    private const val SUPPORT_VISIBILITY = "support"
}
