package com.cslearningos.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun decodeRejectsCollectionWithMoreThanTwoThousandRecordsBeforeMapping() {
        val records = (1..2_001).joinToString(",") { "{}" }
        val rawJson = """
            {
              "schemaVersion": 1,
              "exportedAt": 1700000000000,
              "nodes": [$records],
              "quizzes": [],
              "reviewStates": [],
              "attempts": []
            }
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(rawJson)
        }

        assertTrue(error.message.orEmpty().contains("array"))
    }

    @Test
    fun decodeRejectsNodeMarkdownBodyLongerThanOneMillionCharacters() {
        val oversizedBody = "x".repeat(1_000_001)
        val rawJson = """
            {
              "schemaVersion": 1,
              "exportedAt": 1700000000000,
              "nodes": [
                {
                  "id": "node-1",
                  "title": "Paging",
                  "markdownBody": "$oversizedBody",
                  "createdAt": 1,
                  "updatedAt": 2,
                  "lastReadAt": null,
                  "revision": 1,
                  "syncStatus": "clean",
                  "deletedAt": null
                }
              ],
              "quizzes": [],
              "reviewStates": [],
              "attempts": []
            }
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(rawJson)
        }

        assertTrue(error.message.orEmpty().contains("markdownBody"))
    }

    @Test
    fun decodeRejectsDeepUnknownJsonPayloadBeforeJsonObjectParsing() {
        var unknownPayload = "0"
        repeat(65) {
            unknownPayload = """{"unknown":$unknownPayload}"""
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(backupJson(extra = "\"unknown\":$unknownPayload"))
        }

        assertTrue(error.message.orEmpty().contains("nesting"))
    }

    @Test
    fun decodeRejectsOversizedUnknownTopLevelArrayBeforeJsonObjectParsing() {
        val scalars = (1..2_001).joinToString(",") { "0" }

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(backupJson(extra = "\"unknown\":[$scalars]"))
        }

        assertTrue(error.message.orEmpty().contains("array"))
    }

    @Test
    fun decodeRejectsOversizedUnknownPerRecordArrayBeforeJsonObjectParsing() {
        val scalars = (1..2_001).joinToString(",") { "0" }
        val node = nodeJson(title = "Paging")
            .removeSuffix("}")
            .plus(", \"unknown\":[$scalars]}")

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(backupJson(nodes = "[$node]"))
        }

        assertTrue(error.message.orEmpty().contains("array"))
    }

    @Test
    fun decodeRejectsUnmatchedJsonClosureBeforeJsonObjectParsing() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("}")
        }

        assertTrue(error.message.orEmpty().contains("closure"))
    }

    @Test
    fun decodeAllowsBracesAndEscapedQuotesInsideJsonStrings() {
        val title = "brace } and quote \\\""

        val decoded = BackupCodec.decode(
            backupJson(nodes = "[${nodeJson(title = title)}]")
        )

        assertEquals("brace } and quote \"", decoded.nodes.single().title)
    }

    @Test
    fun decodeRejectsOversizedNodeTitleBeforeMapping() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(
                backupJson(nodes = "[${nodeJson(title = "t".repeat(100_001))}]")
            )
        }

        assertTrue(error.message.orEmpty().contains("title"))
    }

    @Test
    fun decodeRejectsOversizedQuizPromptBeforeMapping() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(
                backupJson(quizzes = "[${quizJson(prompt = "q".repeat(100_001))}]")
            )
        }

        assertTrue(error.message.orEmpty().contains("prompt"))
    }

    @Test
    fun decodeRejectsOversizedCaptureBodyBeforeMapping() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(
                backupJson(captureSlips = "[${captureSlipJson(body = "c".repeat(100_001))}]")
            )
        }

        assertTrue(error.message.orEmpty().contains("body"))
    }

    private fun backupJson(
        nodes: String = "[]",
        quizzes: String = "[]",
        captureSlips: String = "[]",
        extra: String = ""
    ): String =
        """
        {
          "schemaVersion": 1,
          "exportedAt": 1700000000000,
          "nodes": $nodes,
          "quizzes": $quizzes,
          "reviewStates": [],
          "attempts": [],
          "captureSlips": $captureSlips${if (extra.isBlank()) "" else ", $extra"}
        }
        """.trimIndent()

    private fun nodeJson(title: String): String =
        """
        {
          "id": "node-1",
          "title": "$title",
          "markdownBody": "# Paging",
          "createdAt": 1,
          "updatedAt": 2,
          "lastReadAt": null,
          "revision": 1,
          "syncStatus": "clean",
          "deletedAt": null
        }
        """.trimIndent()

    private fun quizJson(prompt: String): String =
        """
        {
          "id": "quiz-1",
          "nodeId": null,
          "prompt": "$prompt",
          "answer": "answer",
          "explanation": "explanation",
          "source": "manual",
          "sourceAnchor": null,
          "createdAt": 1,
          "updatedAt": 2,
          "revision": 1,
          "syncStatus": "clean",
          "deletedAt": null
        }
        """.trimIndent()

    private fun captureSlipJson(body: String): String =
        """
        {
          "id": "capture-1",
          "body": "$body",
          "type": "question",
          "topicHint": null,
          "sourceLabel": null,
          "linkedNodeId": null,
          "status": "inbox",
          "createdAt": 1,
          "updatedAt": 2,
          "revision": 1,
          "syncStatus": "clean",
          "deletedAt": null
        }
        """.trimIndent()
}
