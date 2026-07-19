package com.cslearningos.mobile.feature.sync

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.NodeFtsEntity
import com.cslearningos.mobile.data.QuizFtsEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.SyncStatus
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPackageImporterTest {

    private fun packageBytes(
        manifest: String = defaultManifest,
        nodes: String = "[]",
        quizzes: String = "[]",
        slips: String = "[]"
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("manifest.json", manifest)
            put("records/nodes.json", nodes)
            put("records/quizzes.json", quizzes)
            put("records/capture_slips.json", slips)
        }
        return out.toByteArray()
    }

    private val defaultManifest = """
        {
          "format": "cs-learning-os-package",
          "formatVersion": 1,
          "protocolVersion": 1,
          "serverId": "srv",
          "exportedAt": "2026-07-17T10:00:00+00:00",
          "counts": { "nodes": 1, "quizzes": 1, "capture_slips": 1 }
        }
    """.trimIndent()

    private val nodeJson = """
        [{
          "type": "node", "id": "n1", "title": "Paging", "area": "systems", "track": "memory",
          "summary": "s", "body": "# Paging\n\nBody", "visibility": "core", "revision": 3,
          "updatedAt": "2026-07-17T10:00:00+00:00", "hash": "h"
        }]
    """.trimIndent()

    private val quizJson = """
        [{
          "type": "quiz", "id": "q1", "title": "Q", "area": "systems", "difficulty": "easy",
          "summary": "", "body": "# Q\n\n## Prompt\n\nP?\n\n## Answer\n\nA.\n\n## Explanation\n\nE.",
          "visibility": "practice", "revision": 1, "updatedAt": "2026-07-17T10:00:00+00:00", "hash": "h"
        }]
    """.trimIndent()

    private val slipJson = """
        [{
          "type": "capture_slip", "id": "s1", "body": "TLB 是页表缓存", "slipType": "concept_seed",
          "topicHint": "TLB", "sourceLabel": "phone", "status": "inbox", "revision": 1,
          "createdAt": "2026-07-17T10:00:00+00:00", "updatedAt": "2026-07-17T10:00:00+00:00"
        }]
    """.trimIndent()

    @Test
    fun parsesWellFormedPackage() {
        val preview = SyncPackageImporter.parse(packageBytes(nodes = nodeJson, quizzes = quizJson, slips = slipJson))
        assertEquals("srv", preview.serverId)
        assertEquals(1, preview.nodes.size)
        assertEquals(1, preview.quizzes.size)
        assertEquals(1, preview.captureSlips.size)
        assertEquals("Paging", preview.nodes[0].title)
        assertEquals(3L, preview.nodes[0].revision)
    }

    @Test
    fun rejectsMalformedPackages() {
        assertThrows(SyncPackageException::class.java) {
            SyncPackageImporter.parse("not a zip".toByteArray())
        }
        assertThrows(SyncPackageException::class.java) {
            SyncPackageImporter.parse(packageBytes(manifest = """{"format":"other","formatVersion":1}"""))
        }
        assertThrows(SyncPackageException::class.java) {
            SyncPackageImporter.parse(packageBytes(manifest = """{"format":"cs-learning-os-package","formatVersion":9}"""))
        }
        assertThrows(SyncPackageException::class.java) {
            SyncPackageImporter.parse(packageBytes(nodes = """[{"type":"quiz","id":"x"}]"""))
        }
    }

    @Test
    fun planImportClassifiesAddUpdateConflictSkip() = runTest {
        val dao = FakeDao()
        dao.nodes["n1"] = localNode("n1", SyncStatus.dirty)
        dao.nodes["n2"] = localNode("n2", SyncStatus.clean)
        val twoNodes = """[
          {"type":"node","id":"n1","title":"T1","area":"systems","track":"general","summary":"","body":"# T1","visibility":"core","revision":2,"updatedAt":"","hash":"h"},
          {"type":"node","id":"n2","title":"T2","area":"systems","track":"general","summary":"","body":"# T2","visibility":"core","revision":2,"updatedAt":"","hash":"h"},
          {"type":"node","id":"n3","title":"T3","area":"new-area","track":"general","summary":"","body":"# T3","visibility":"core","revision":1,"updatedAt":"","hash":"h"}
        ]"""
        val badQuiz = """[{"type":"quiz","id":"q9","title":"B","area":"systems","difficulty":"easy","summary":"","body":"no sections","visibility":"practice","revision":1,"updatedAt":"","hash":"h"}]"""
        val preview = SyncPackageImporter.parse(packageBytes(nodes = twoNodes, quizzes = badQuiz))

        val plan = SyncPackageImporter.planImport(dao.proxy(), preview, now = 100L)

        assertEquals(1, plan.added)      // n3 is new
        assertEquals(1, plan.updated)    // n2 clean
        assertEquals(1, plan.conflicted) // n1 dirty
        assertEquals(1, plan.skipped)    // q9 unparseable
        assertTrue(plan.areas.any { it.id == "new-area" })
    }

    @Test
    fun applyImportWritesEntitiesAndFts() = runTest {
        val dao = FakeDao()
        val preview = SyncPackageImporter.parse(packageBytes(nodes = nodeJson, quizzes = quizJson, slips = slipJson))
        val plan = SyncPackageImporter.planImport(dao.proxy(), preview, now = 100L)

        SyncPackageImporter.applyImport(dao.proxy(), plan)

        val node = dao.nodes.getValue("n1")
        assertEquals(SyncStatus.clean, node.syncStatus)
        assertEquals(3L, node.baseRevision)
        assertEquals("systems", node.areaId)
        assertTrue(dao.areas.containsKey("systems"))
        val quiz = dao.quizzes.getValue("q1")
        assertEquals("P?", quiz.prompt)
        assertTrue(dao.nodeFts.any { it.nodeId == "n1" })
        assertTrue(dao.quizFts.any { it.quizId == "q1" })
        assertTrue(dao.slips.containsKey("s1"))
    }

    private fun localNode(id: String, status: SyncStatus) = LearningNodeEntity(
        id = id,
        title = "Local $id",
        markdownBody = "# Local",
        createdAt = 1L,
        updatedAt = 1L,
        lastReadAt = null,
        revision = 1,
        syncStatus = status,
        deletedAt = null
    )

    private class FakeDao {
        val nodes = linkedMapOf<String, LearningNodeEntity>()
        val quizzes = linkedMapOf<String, QuizItemEntity>()
        val slips = linkedMapOf<String, CaptureSlipEntity>()
        val areas = linkedMapOf<String, AreaEntity>()
        val nodeFts = mutableListOf<NodeFtsEntity>()
        val quizFts = mutableListOf<QuizFtsEntity>()

        @Suppress("UNCHECKED_CAST")
        fun proxy(): LearningDao =
            Proxy.newProxyInstance(
                LearningDao::class.java.classLoader,
                arrayOf(LearningDao::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "getNode" -> nodes[args[0]]
                    "getQuiz" -> quizzes[args[0]]
                    "getCaptureSlip" -> slips[args[0]]
                    "getArea" -> areas[args[0]]
                    "getAreaBySlug" -> areas.values.firstOrNull { it.slug == args[0] }
                    "applySyncBatch" -> {
                        (args[0] as List<AreaEntity>).forEach { areas[it.id] = it }
                        (args[1] as List<LearningNodeEntity>).forEach { nodes[it.id] = it }
                        (args[2] as List<QuizItemEntity>).forEach { quizzes[it.id] = it }
                        (args[4] as List<CaptureSlipEntity>).forEach { slips[it.id] = it }
                        nodeFts += args[8] as List<NodeFtsEntity>
                        quizFts += args[9] as List<QuizFtsEntity>
                        Unit
                    }
                    else -> when {
                        method.returnType == Boolean::class.javaPrimitiveType -> false
                        method.returnType == Int::class.javaPrimitiveType -> 0
                        method.returnType == Long::class.javaPrimitiveType -> 0L
                        else -> null
                    }
                }
            } as LearningDao
    }
}
