package com.cslearningos.mobile.feature.sync

import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.SyncStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergePolicyTest {

    private fun localNode(
        syncStatus: SyncStatus = SyncStatus.clean,
        baseRevision: Long = 0
    ) = LearningNodeEntity(
        id = "n1",
        title = "Local",
        markdownBody = "# Local",
        createdAt = 1L,
        updatedAt = 2L,
        lastReadAt = null,
        revision = 1L,
        syncStatus = syncStatus,
        deletedAt = null,
        baseRevision = baseRevision
    )

    @Test
    fun appliesWhenLocalMissing() {
        assertEquals(MergeDecision.APPLY, decideNodeMerge(null, remoteRevision = 3, tombstone = false))
    }

    @Test
    fun skipsTombstoneWhenLocalMissing() {
        assertEquals(MergeDecision.SKIP, decideNodeMerge(null, remoteRevision = 3, tombstone = true))
    }

    @Test
    fun softDeletesCleanLocalOnTombstone() {
        assertEquals(
            MergeDecision.SOFT_DELETE,
            decideNodeMerge(localNode(baseRevision = 2), remoteRevision = 3, tombstone = true)
        )
    }

    @Test
    fun keepsDirtyLocalOnTombstone() {
        assertEquals(
            MergeDecision.KEEP_LOCAL,
            decideNodeMerge(localNode(syncStatus = SyncStatus.dirty), remoteRevision = 3, tombstone = true)
        )
        assertEquals(
            MergeDecision.KEEP_LOCAL,
            decideNodeMerge(localNode(syncStatus = SyncStatus.conflicted), remoteRevision = 3, tombstone = true)
        )
    }

    @Test
    fun skipsReplayWhenBaseRevisionCoversRemote() {
        assertEquals(
            MergeDecision.SKIP,
            decideNodeMerge(localNode(baseRevision = 3), remoteRevision = 3, tombstone = false)
        )
        assertEquals(
            MergeDecision.SKIP,
            decideNodeMerge(localNode(baseRevision = 4), remoteRevision = 3, tombstone = false)
        )
    }

    @Test
    fun appliesOverCleanLocalWhenRemoteAdvanced() {
        assertEquals(
            MergeDecision.APPLY,
            decideNodeMerge(localNode(baseRevision = 2), remoteRevision = 3, tombstone = false)
        )
    }

    @Test
    fun appliesWithConflictWhenLocalDirtyAndRemoteAdvanced() {
        assertEquals(
            MergeDecision.APPLY_WITH_CONFLICT,
            decideNodeMerge(localNode(syncStatus = SyncStatus.dirty, baseRevision = 2), remoteRevision = 3, tombstone = false)
        )
    }
}

class SyncModelsTest {

    @Test
    fun parsesManifestWithNullables() {
        val manifest = parseSyncManifest(
            JSONObject(
                """
                {
                  "reset": false,
                  "protocolVersion": 1,
                  "serverId": "srv-1",
                  "cursor": 42,
                  "hasMore": false,
                  "changes": [
                    { "type": "node", "id": "n1", "revision": 3, "hash": "abc", "tombstone": false, "area": "algorithms" },
                    { "type": "quiz", "id": "q1", "revision": null, "hash": null, "tombstone": true, "area": null }
                  ]
                }
                """.trimIndent()
            )
        )
        assertEquals(42L, manifest.cursor)
        assertEquals(2, manifest.changes.size)
        assertEquals("algorithms", manifest.changes[0].area)
        assertEquals(3L, manifest.changes[0].revision)
        assertNull(manifest.changes[1].area)
        assertNull(manifest.changes[1].revision)
        assertTrue(manifest.changes[1].tombstone)
    }

    @Test
    fun scopeFingerprintChangesWithScope() {
        val a = SyncScope(areas = listOf("algorithms"))
        val b = SyncScope(areas = listOf("algorithms", "systems"))
        val c = SyncScope(areas = listOf("algorithms"))
        assertNotEquals(a.fingerprint(), b.fingerprint())
        assertEquals(a.fingerprint(), c.fingerprint())
    }

    @Test
    fun parsesNodeRecord() {
        val record = parseSyncRecord(
            JSONObject(
                """
                {
                  "type": "node", "id": "n1", "title": "T", "area": "algorithms", "track": "general",
                  "summary": "s", "body": "# T", "visibility": "core", "revision": 2,
                  "updatedAt": "2026-07-17T10:00:00+00:00", "hash": "h"
                }
                """.trimIndent()
            )
        ) as SyncRecord.Node
        assertEquals("n1", record.id)
        assertEquals(2L, record.revision)
        assertEquals("core", record.visibility)
    }
}

class DesktopQuizMarkdownTest {

    @Test
    fun parsesPromptAnswerExplanationSections() {
        val parsed = DesktopQuizMarkdown.parse(
            """
            # Quiz Title

            ## Prompt

            What is a page fault?

            ## Answer

            A trap raised when a referenced page is not resident.

            ## Explanation

            The CPU walks the page table and raises if the present bit is clear.
            """.trimIndent()
        )
        checkNotNull(parsed)
        assertEquals("What is a page fault?", parsed!!.prompt)
        assertEquals("A trap raised when a referenced page is not resident.", parsed.answer)
        assertTrue(parsed.explanation.contains("page table"))
    }

    @Test
    fun rejectsBodyWithoutPromptOrAnswer() {
        assertNull(DesktopQuizMarkdown.parse("# Just a note\n\nNo quiz sections here."))
        assertNull(DesktopQuizMarkdown.parse("## Prompt\n\nOnly a prompt."))
    }
}
