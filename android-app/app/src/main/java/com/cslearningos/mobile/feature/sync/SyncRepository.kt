package com.cslearningos.mobile.feature.sync

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.NodeFtsEntity
import com.cslearningos.mobile.data.QuizFtsEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.SyncStatus
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Pulls scoped changes from the paired desktop and applies them in one Room
 * transaction per manifest page. Review-attempt application is deferred to
 * Phase 3 (grade-scale mapping is designed there); attempt changes are
 * counted as skipped for now.
 */
class SyncRepository(
    private val dao: LearningDao,
    private val transport: SyncTransport,
    private val store: SyncStateStore,
    private val now: () -> Long = System::currentTimeMillis
) {

    suspend fun pullAndApply(scope: SyncScope): SyncReport {
        val health = transport.health()
        var cursor = store.cursor
        if (store.serverId.isNotBlank() && store.serverId != health.serverId) {
            cursor = 0L
        }
        if (store.scopeFingerprint.isNotBlank() && store.scopeFingerprint != scope.fingerprint()) {
            cursor = 0L
        }

        var pulledNodes = 0
        var pulledQuizzes = 0
        var pulledQuestions = 0
        var pulledCaptureSlips = 0
        var skippedAttempts = 0
        var skippedRecords = 0
        var removed = 0
        var conflicts = 0
        var resetSeen = false

        var page = 0
        while (page < MaxManifestPages) {
            page += 1
            val manifest = transport.manifest(cursor, health.serverId, scope)
            if (manifest.reset) {
                check(!resetSeen) { "Server requested cursor reset twice in one pull" }
                resetSeen = true
                cursor = 0L
                continue
            }

            val outcome = applyManifestPage(manifest, scope)
            pulledNodes += outcome.pulledNodes
            pulledQuizzes += outcome.pulledQuizzes
            pulledQuestions += outcome.pulledQuestions
            pulledCaptureSlips += outcome.pulledCaptureSlips
            skippedAttempts += outcome.skippedAttempts
            skippedRecords += outcome.skippedRecords
            removed += outcome.removed
            conflicts += outcome.conflicts
            cursor = manifest.cursor
            if (!manifest.hasMore) break
        }

        store.serverId = health.serverId
        store.cursor = cursor
        store.scopeFingerprint = scope.fingerprint()
        store.lastSyncAt = now()

        return SyncReport(
            pulledNodes = pulledNodes,
            pulledQuizzes = pulledQuizzes,
            pulledQuestions = pulledQuestions,
            pulledCaptureSlips = pulledCaptureSlips,
            skippedAttempts = skippedAttempts,
            skippedRecords = skippedRecords,
            removed = removed,
            conflicts = conflicts,
            cursor = cursor,
            serverId = health.serverId
        )
    }

    private data class PageOutcome(
        val pulledNodes: Int,
        val pulledQuizzes: Int,
        val pulledQuestions: Int,
        val pulledCaptureSlips: Int,
        val skippedAttempts: Int,
        val skippedRecords: Int,
        val removed: Int,
        val conflicts: Int
    )

    private suspend fun applyManifestPage(manifest: SyncManifest, scope: SyncScope): PageOutcome {
        var pulledNodes = 0
        var pulledQuizzes = 0
        var pulledQuestions = 0
        var pulledCaptureSlips = 0
        var skippedAttempts = 0
        var skippedRecords = 0
        var removed = 0
        var conflicts = 0

        val areasToEnsure = linkedMapOf<String, AreaEntity>()
        val nodes = mutableListOf<LearningNodeEntity>()
        val quizzes = mutableListOf<QuizItemEntity>()
        val questions = mutableListOf<ReaderQuestionEntity>()
        val slips = mutableListOf<CaptureSlipEntity>()
        val nodeFts = mutableListOf<NodeFtsEntity>()
        val quizFts = mutableListOf<QuizFtsEntity>()
        val deletedNodeFts = mutableListOf<String>()
        val deletedQuizFts = mutableListOf<String>()

        suspend fun areaIdFor(label: String): String {
            val existing = dao.getArea(label) ?: dao.getAreaBySlug(label)
            if (existing != null) return existing.id
            val timestamp = now()
            val created = AreaEntity(
                id = label,
                slug = label,
                name = label,
                order = DefaultAreaOrder,
                createdAt = timestamp,
                updatedAt = timestamp,
                deletedAt = null
            )
            areasToEnsure.putIfAbsent(label, created)
            return created.id
        }

        val nodeIdsToPull = mutableListOf<String>()
        val quizIdsToPull = mutableListOf<String>()
        val questionIdsToPull = mutableListOf<String>()
        val slipIdsToPull = mutableListOf<String>()

        for (change in manifest.changes) {
            when (change.type) {
                SyncRecord.ReviewAttempt.TYPE -> skippedAttempts += 1

                SyncRecord.Node.TYPE -> {
                    if (change.tombstone) {
                        val local = dao.getNode(change.id) ?: continue
                        when (decideNodeMerge(local, change.revision ?: Long.MAX_VALUE, tombstone = true)) {
                            MergeDecision.SOFT_DELETE -> {
                                dao.markNodeSyncedDeleted(change.id, now(), now())
                                deletedNodeFts += change.id
                                removed += 1
                            }
                            MergeDecision.KEEP_LOCAL -> {
                                nodes += local.copy(syncStatus = SyncStatus.conflicted)
                                conflicts += 1
                            }
                            else -> Unit
                        }
                        continue
                    }
                    val inScope = scope.includesArea(change.area) || change.id in scope.pinnedNodeIds
                    if (!inScope) {
                        if (removeSyncedLocalNodeIfClean(change.id)) {
                            removed += 1
                            deletedNodeFts += change.id
                        }
                        continue
                    }
                    nodeIdsToPull += change.id
                }

                SyncRecord.Quiz.TYPE -> {
                    if (change.tombstone) {
                        val local = dao.getQuiz(change.id) ?: continue
                        when (decideQuizMerge(local, change.revision ?: Long.MAX_VALUE, tombstone = true)) {
                            MergeDecision.SOFT_DELETE -> {
                                dao.markQuizSyncedDeleted(change.id, now(), now())
                                deletedQuizFts += change.id
                                removed += 1
                            }
                            MergeDecision.KEEP_LOCAL -> {
                                quizzes += local.copy(syncStatus = SyncStatus.conflicted)
                                conflicts += 1
                            }
                            else -> Unit
                        }
                        continue
                    }
                    val inScope = scope.includesArea(change.area)
                    if (!inScope) {
                        if (removeSyncedLocalQuizIfClean(change.id)) {
                            removed += 1
                            deletedQuizFts += change.id
                        }
                        continue
                    }
                    quizIdsToPull += change.id
                }

                SyncRecord.ReaderQuestion.TYPE -> if (!change.tombstone) questionIdsToPull += change.id

                SyncRecord.CaptureSlip.TYPE -> if (!change.tombstone) slipIdsToPull += change.id
            }
        }

        transport.pull(SyncRecord.Node.TYPE, nodeIdsToPull, scope)
            .filterIsInstance<SyncRecord.Node>()
            .forEach { record ->
                val local = dao.getNode(record.id)
                when (decideNodeMerge(local, record.revision, tombstone = false)) {
                    MergeDecision.APPLY, MergeDecision.APPLY_WITH_CONFLICT -> {
                        if (local != null && isDirty(local.syncStatus)) {
                            val conflictCopy = conflictCopyOf(local)
                            nodes += conflictCopy
                            nodeFts += nodeFtsOf(conflictCopy)
                            conflicts += 1
                        }
                        val areaId = areaIdFor(record.area)
                        val entity = record.toEntity(local, areaId)
                        nodes += entity
                        nodeFts += nodeFtsOf(entity)
                        pulledNodes += 1
                    }
                    else -> Unit
                }
            }

        transport.pull(SyncRecord.Quiz.TYPE, quizIdsToPull, scope)
            .filterIsInstance<SyncRecord.Quiz>()
            .forEach { record ->
                val local = dao.getQuiz(record.id)
                when (decideQuizMerge(local, record.revision, tombstone = false)) {
                    MergeDecision.APPLY, MergeDecision.APPLY_WITH_CONFLICT -> {
                        val parsed = DesktopQuizMarkdown.parse(record.body)
                        if (parsed == null) {
                            skippedRecords += 1
                            return@forEach
                        }
                        if (local != null && isDirty(local.syncStatus)) {
                            slips += conflictSlipOf(local)
                            conflicts += 1
                        }
                        val areaId = areaIdFor(record.area)
                        val entity = record.toEntity(parsed, local, areaId)
                        quizzes += entity
                        quizFts += quizFtsOf(entity)
                        pulledQuizzes += 1
                    }
                    else -> Unit
                }
            }

        transport.pull(SyncRecord.ReaderQuestion.TYPE, questionIdsToPull, scope)
            .filterIsInstance<SyncRecord.ReaderQuestion>()
            .forEach { record ->
                if (record.targetType != SyncRecord.Node.TYPE) {
                    skippedRecords += 1
                    return@forEach
                }
                val local = dao.getReaderQuestion(record.id)
                if (local == null || local.syncStatus == SyncStatus.clean) {
                    questions += record.toEntity(local)
                    pulledQuestions += 1
                }
            }

        transport.pull(SyncRecord.CaptureSlip.TYPE, slipIdsToPull, scope)
            .filterIsInstance<SyncRecord.CaptureSlip>()
            .forEach { record ->
                val local = dao.getCaptureSlip(record.id)
                if (local == null || local.syncStatus == SyncStatus.clean) {
                    slips += record.toEntity(local)
                    pulledCaptureSlips += 1
                }
            }

        dao.applySyncBatch(
            areas = areasToEnsure.values.toList(),
            nodes = nodes,
            quizzes = quizzes,
            questions = questions,
            captureSlips = slips,
            attempts = emptyList(),
            nodeFts = nodeFts,
            quizFts = quizFts,
            deletedNodeFtsIds = deletedNodeFts,
            deletedQuizFtsIds = deletedQuizFts
        )

        return PageOutcome(
            pulledNodes = pulledNodes,
            pulledQuizzes = pulledQuizzes,
            pulledQuestions = pulledQuestions,
            pulledCaptureSlips = pulledCaptureSlips,
            skippedAttempts = skippedAttempts,
            skippedRecords = skippedRecords,
            removed = removed,
            conflicts = conflicts
        )
    }

    private suspend fun removeSyncedLocalNodeIfClean(id: String): Boolean {
        val local = dao.getNode(id) ?: return false
        if (local.baseRevision <= 0 || local.syncStatus != SyncStatus.clean || local.deletedAt != null) return false
        dao.markNodeSyncedDeleted(id, now(), now())
        dao.deleteNodeFts(id)
        return true
    }

    private suspend fun removeSyncedLocalQuizIfClean(id: String): Boolean {
        val local = dao.getQuiz(id) ?: return false
        if (local.baseRevision <= 0 || local.syncStatus != SyncStatus.clean || local.deletedAt != null) return false
        dao.markQuizSyncedDeleted(id, now(), now())
        dao.deleteQuizFts(id)
        return true
    }

    private fun conflictCopyOf(local: LearningNodeEntity): LearningNodeEntity =
        local.copy(
            id = "conflict-${UUID.randomUUID()}",
            title = "${local.title} (phone conflict)",
            revision = 1,
            baseRevision = 0,
            syncStatus = SyncStatus.conflicted,
            updatedAt = now()
        )

    private fun conflictSlipOf(local: QuizItemEntity): CaptureSlipEntity {
        val timestamp = now()
        return CaptureSlipEntity(
            id = UUID.randomUUID().toString(),
            body = "Q: ${local.prompt}\nA: ${local.answer}\n\n${local.explanation}".trim(),
            type = CaptureSlipType.unclear,
            topicHint = "Phone quiz conflict",
            sourceLabel = "sync",
            linkedNodeId = local.nodeId,
            status = CaptureSlipStatus.inbox,
            createdAt = timestamp,
            updatedAt = timestamp,
            revision = 1,
            syncStatus = SyncStatus.conflicted,
            deletedAt = null
        )
    }

    private fun nodeFtsOf(node: LearningNodeEntity): NodeFtsEntity =
        NodeFtsEntity(
            rowId = stableRowId(node.id),
            nodeId = node.id,
            title = node.title,
            body = node.markdownBody
        )

    private fun quizFtsOf(quiz: QuizItemEntity): QuizFtsEntity =
        QuizFtsEntity(
            rowId = stableRowId(quiz.id),
            quizId = quiz.id,
            prompt = quiz.prompt,
            answer = quiz.answer
        )

    private fun SyncRecord.Node.toEntity(local: LearningNodeEntity?, areaId: String): LearningNodeEntity =
        LearningNodeEntity(
            id = id,
            title = title,
            markdownBody = body,
            createdAt = local?.createdAt ?: now(),
            updatedAt = parseIsoMillis(updatedAt) ?: now(),
            lastReadAt = local?.lastReadAt,
            revision = revision,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = area,
            areaId = areaId,
            track = track,
            order = local?.order ?: 1000,
            summary = summary,
            visibility = visibility,
            isStarter = local?.isStarter ?: false,
            isChecked = local?.isChecked ?: false,
            baseRevision = revision
        )

    private fun SyncRecord.Quiz.toEntity(
        parsed: DesktopQuizMarkdown.ParsedDesktopQuiz,
        local: QuizItemEntity?,
        areaId: String
    ): QuizItemEntity =
        QuizItemEntity(
            id = id,
            nodeId = local?.nodeId,
            prompt = parsed.prompt,
            answer = parsed.answer,
            explanation = parsed.explanation,
            source = QuizSource.markdown,
            sourceAnchor = local?.sourceAnchor,
            createdAt = local?.createdAt ?: now(),
            updatedAt = parseIsoMillis(updatedAt) ?: now(),
            revision = revision,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = areaId,
            track = local?.track ?: "general",
            visibility = visibility,
            isStarter = local?.isStarter ?: false,
            baseRevision = revision
        )

    private fun SyncRecord.ReaderQuestion.toEntity(local: ReaderQuestionEntity?): ReaderQuestionEntity =
        ReaderQuestionEntity(
            id = id,
            nodeId = targetId,
            body = question,
            createdAt = parseIsoMillis(createdAt) ?: local?.createdAt ?: now(),
            resolvedAt = parseIsoMillis(resolvedAt),
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )

    private fun SyncRecord.CaptureSlip.toEntity(local: CaptureSlipEntity?): CaptureSlipEntity =
        CaptureSlipEntity(
            id = id,
            body = body,
            type = CaptureSlipType.entries.firstOrNull { it.name == slipType } ?: CaptureSlipType.concept_seed,
            topicHint = topicHint.ifBlank { null },
            sourceLabel = sourceLabel.ifBlank { null },
            linkedNodeId = local?.linkedNodeId,
            status = CaptureSlipStatus.entries.firstOrNull { it.name == status } ?: CaptureSlipStatus.inbox,
            createdAt = parseIsoMillis(createdAt) ?: local?.createdAt ?: now(),
            updatedAt = parseIsoMillis(updatedAt) ?: now(),
            revision = revision,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )

    private fun parseIsoMillis(value: String): Long? =
        runCatching {
            if (value.isBlank()) return null
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        }.getOrNull()

    private fun isDirty(status: SyncStatus): Boolean =
        status == SyncStatus.dirty || status == SyncStatus.conflicted

    private fun stableRowId(id: String): Int =
        id.hashCode().let { if (it == Int.MIN_VALUE) 1 else kotlin.math.abs(it) }.coerceAtLeast(1)

    private companion object {
        const val MaxManifestPages = 40
        const val DefaultAreaOrder = 1000
    }
}
