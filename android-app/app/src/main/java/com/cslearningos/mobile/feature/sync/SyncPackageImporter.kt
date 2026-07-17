package com.cslearningos.mobile.feature.sync

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.NodeFtsEntity
import com.cslearningos.mobile.data.QuizFtsEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.SyncStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class SyncPackageException(message: String) : Exception(message)

/**
 * Validates and applies offline sync packages (Phase 5). The ZIP layout
 * matches the desktop export: manifest.json plus per-type record files
 * whose entries share the pull-endpoint record schema.
 */
object SyncPackageImporter {

    const val MaxPackageBytes = 25L * 1024 * 1024
    const val MaxRecordsPerType = 2_000
    private const val MaxEntryBytes = 8 * 1024 * 1024

    data class PackagePreview(
        val serverId: String,
        val exportedAt: String,
        val nodes: List<SyncRecord.Node>,
        val quizzes: List<SyncRecord.Quiz>,
        val captureSlips: List<SyncRecord.CaptureSlip>
    ) {
        val totalRecords: Int get() = nodes.size + quizzes.size + captureSlips.size
    }

    data class ImportPlan(
        val preview: PackagePreview,
        val added: Int,
        val updated: Int,
        val conflicted: Int,
        val skipped: Int,
        val areas: List<AreaEntity>,
        val nodes: List<LearningNodeEntity>,
        val quizzes: List<QuizItemEntity>,
        val captureSlips: List<CaptureSlipEntity>,
        val nodeFts: List<NodeFtsEntity>,
        val quizFts: List<QuizFtsEntity>
    ) {
        val applicable: Int get() = added + updated
    }

    fun parse(bytes: ByteArray): PackagePreview {
        try {
            return parseUnsafe(bytes)
        } catch (error: SyncPackageException) {
            throw error
        } catch (error: Exception) {
            throw SyncPackageException("records_invalid")
        }
    }

    private fun parseUnsafe(bytes: ByteArray): PackagePreview {
        if (bytes.size > MaxPackageBytes) throw SyncPackageException("package_too_large")
        val entries = readZipEntries(bytes)
        val manifestJson = entries["manifest.json"]
            ?: throw SyncPackageException("manifest_missing")
        val manifest = runCatching { JSONObject(String(manifestJson, Charsets.UTF_8)) }
            .getOrElse { throw SyncPackageException("manifest_invalid") }
        if (manifest.optString("format") != "cs-learning-os-package") {
            throw SyncPackageException("format_mismatch")
        }
        if (manifest.optInt("formatVersion") != 1) {
            throw SyncPackageException("version_unsupported")
        }
        val nodes = parseRecords<SyncRecord.Node>(entries, "records/nodes.json", SyncRecord.Node.TYPE)
        val quizzes = parseRecords<SyncRecord.Quiz>(entries, "records/quizzes.json", SyncRecord.Quiz.TYPE)
        val slips = parseRecords<SyncRecord.CaptureSlip>(entries, "records/capture_slips.json", SyncRecord.CaptureSlip.TYPE)
        return PackagePreview(
            serverId = manifest.optString("serverId"),
            exportedAt = manifest.optString("exportedAt"),
            nodes = nodes,
            quizzes = quizzes,
            captureSlips = slips
        )
    }

    private inline fun <reified T : SyncRecord> parseRecords(
        entries: Map<String, ByteArray>,
        name: String,
        expectedType: String
    ): List<T> {
        val raw = entries[name] ?: return emptyList()
        if (raw.size > MaxEntryBytes) throw SyncPackageException("package_too_large")
        val array = runCatching { JSONArray(String(raw, Charsets.UTF_8)) }
            .getOrElse { throw SyncPackageException("records_invalid") }
        if (array.length() > MaxRecordsPerType) throw SyncPackageException("too_many_records")
        return buildList {
            for (index in 0 until array.length()) {
                val item = runCatching { array.getJSONObject(index) }
                    .getOrElse { throw SyncPackageException("records_invalid") }
                val record = runCatching { parseSyncRecord(item) }
                    .getOrElse { throw SyncPackageException("records_invalid") }
                if (record == null || record.type != expectedType || record !is T) {
                    throw SyncPackageException("record_type_mismatch")
                }
                add(record)
            }
        }
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var total = 0L
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                if (name.contains("..")) throw SyncPackageException("unsafe_entry")
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val read = zip.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > MaxPackageBytes * 4) throw SyncPackageException("package_too_large")
                    buffer.write(chunk, 0, read)
                }
                entries[name] = buffer.toByteArray()
            }
        }
        return entries
    }

    suspend fun planImport(
        dao: LearningDao,
        preview: PackagePreview,
        now: Long
    ): ImportPlan {
        var added = 0
        var updated = 0
        var conflicted = 0
        var skipped = 0

        val areas = linkedMapOf<String, AreaEntity>()
        suspend fun ensureArea(label: String): String {
            if (label.isBlank()) return label
            val existing = dao.getArea(label) ?: dao.getAreaBySlug(label)
            if (existing != null) return existing.id
            val created = areas.getOrPut(label) {
                AreaEntity(
                    id = label,
                    slug = label,
                    name = label,
                    order = 1000,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null
                )
            }
            return created.id
        }

        val nodes = mutableListOf<LearningNodeEntity>()
        val nodeFts = mutableListOf<NodeFtsEntity>()
        preview.nodes.forEach { record ->
            val local = dao.getNode(record.id)
            when {
                local == null || local.syncStatus == SyncStatus.clean -> {
                    val entity = record.toNodeEntity(local, ensureArea(record.area), now)
                    nodes += entity
                    nodeFts += nodeFtsOf(entity)
                    if (local == null) added += 1 else updated += 1
                }
                else -> conflicted += 1
            }
        }

        val quizzes = mutableListOf<QuizItemEntity>()
        val quizFts = mutableListOf<QuizFtsEntity>()
        preview.quizzes.forEach { record ->
            val local = dao.getQuiz(record.id)
            val parsed = DesktopQuizMarkdown.parse(record.body)
            when {
                parsed == null -> skipped += 1
                local == null || local.syncStatus == SyncStatus.clean -> {
                    val entity = record.toQuizEntity(parsed, local, ensureArea(record.area), now)
                    quizzes += entity
                    quizFts += quizFtsOf(entity)
                    if (local == null) added += 1 else updated += 1
                }
                else -> conflicted += 1
            }
        }

        val slips = mutableListOf<CaptureSlipEntity>()
        preview.captureSlips.forEach { record ->
            val local = dao.getCaptureSlip(record.id)
            when {
                local == null || local.syncStatus == SyncStatus.clean -> {
                    slips += record.toSlipEntity(local, now)
                    if (local == null) added += 1 else updated += 1
                }
                else -> conflicted += 1
            }
        }

        return ImportPlan(
            preview = preview,
            added = added,
            updated = updated,
            conflicted = conflicted,
            skipped = skipped,
            areas = areas.values.toList(),
            nodes = nodes,
            quizzes = quizzes,
            captureSlips = slips,
            nodeFts = nodeFts,
            quizFts = quizFts
        )
    }

    suspend fun applyImport(dao: LearningDao, plan: ImportPlan) {
        dao.applySyncBatch(
            areas = plan.areas,
            nodes = plan.nodes,
            quizzes = plan.quizzes,
            questions = emptyList(),
            captureSlips = plan.captureSlips,
            attempts = emptyList(),
            reviewStates = emptyList(),
            nodeFts = plan.nodeFts,
            quizFts = plan.quizFts,
            deletedNodeFtsIds = emptyList(),
            deletedQuizFtsIds = emptyList()
        )
    }
}
