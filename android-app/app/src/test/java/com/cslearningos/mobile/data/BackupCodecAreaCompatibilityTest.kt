package com.cslearningos.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecAreaCompatibilityTest {
    @Test
    fun decodeOldBackupInfersAreasAndUncheckedState() {
        val decoded = BackupCodec.decode(
            """
            {
              "schemaVersion": 1,
              "exportedAt": 1700000000000,
              "nodes": [
                {
                  "id": "node-1",
                  "title": "Paging",
                  "markdownBody": "# Paging",
                  "createdAt": 1,
                  "updatedAt": 2,
                  "lastReadAt": null,
                  "revision": 1,
                  "syncStatus": "clean",
                  "deletedAt": null,
                  "area": "systems",
                  "track": "virtual-memory",
                  "order": 20,
                  "summary": "",
                  "visibility": "support",
                  "isStarter": false
                }
              ],
              "quizzes": [],
              "reviewStates": [],
              "attempts": []
            }
            """.trimIndent()
        )

        assertEquals(1, decoded.areas.size)
        assertEquals("systems", decoded.areas.single().id)
        assertEquals("systems", decoded.nodes.single().areaId)
        assertEquals(false, decoded.nodes.single().isChecked)
    }

    @Test
    fun encodeRoundTripPreservesAreasAndCheckedNodes() {
        val backup = LearningBackup(
            schemaVersion = BackupCodec.SchemaVersion,
            exportedAt = 2_000L,
            areas = listOf(
                AreaEntity(
                    id = "systems",
                    slug = "systems",
                    name = "Systems",
                    order = 20,
                    createdAt = 1_000L,
                    updatedAt = 1_500L,
                    deletedAt = null
                )
            ),
            nodes = listOf(
                LearningNodeEntity(
                    id = "node-1",
                    title = "Paging",
                    markdownBody = "# Paging",
                    createdAt = 1_000L,
                    updatedAt = 1_500L,
                    lastReadAt = null,
                    revision = 1L,
                    syncStatus = SyncStatus.clean,
                    deletedAt = null,
                    area = "systems",
                    areaId = "systems",
                    track = "virtual-memory",
                    order = 20,
                    summary = "Summary",
                    visibility = "support",
                    isStarter = false,
                    isChecked = true
                )
            ),
            quizzes = emptyList(),
            reviewStates = emptyList(),
            attempts = emptyList()
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(backup))

        assertEquals("Systems", decoded.areas.single().name)
        assertTrue(decoded.nodes.single().isChecked)
        assertEquals("systems", decoded.nodes.single().areaId)
    }
}
