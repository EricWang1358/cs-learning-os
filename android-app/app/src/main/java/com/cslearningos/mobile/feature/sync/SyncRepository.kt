package com.cslearningos.mobile.feature.sync

import com.cslearningos.mobile.content.room.ContentNodeCodec
import com.cslearningos.mobile.feature.review.data.QuizOutboxCodec
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.BiteCardEntity
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
import com.cslearningos.mobile.data.ReviewAttemptEntity
import com.cslearningos.mobile.data.ReviewResult
import com.cslearningos.mobile.data.ReviewStateEntity
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.domain.ReviewRating
import com.cslearningos.mobile.domain.ReviewScheduleInput
import com.cslearningos.mobile.domain.ReviewScheduler
import org.json.JSONObject
import java.time.Instant
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
        var pulledBiteCards = 0
        var appliedAttempts = 0
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
            pulledBiteCards += outcome.pulledBiteCards
            appliedAttempts += outcome.appliedAttempts
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
            pulledBiteCards = pulledBiteCards,
            appliedAttempts = appliedAttempts,
            skippedAttempts = skippedAttempts,
            skippedRecords = skippedRecords,
            removed = removed,
            conflicts = conflicts,
            cursor = cursor,
            serverId = health.serverId
        )
    }

    /**
     * Uploads append-only local learning events: dirty capture slips and
     * reader questions (syncStatus reset on receipt), plus review attempts
     * newer than the upload high-water mark. Server dedupes by client IDs,
     * so retries are always safe.
     */
    suspend fun pushLocalChanges(): SyncPushReport {
        var uploadedAttempts = 0
        var uploadedCaptures = 0
        var uploadedQuestions = 0
        var uploadedNodes = 0
        var uploadedQuizzes = 0
        var uploadedBiteCards = 0
        var rejected = 0

        val pendingAttempts = dao.getAllAttempts()
            .filter { it.answeredAt > store.lastAttemptUploadAt }
            .sortedBy { it.answeredAt }
        if (pendingAttempts.isNotEmpty()) {
            val receipts = transport.pushAttempts(
                pendingAttempts.map { attempt ->
                    JSONObject()
                        .put("clientAttemptId", attempt.id)
                        .put("quizId", attempt.quizId)
                        .put("grade", attempt.result.name)
                        .put("answeredAt", isoOfMillis(attempt.answeredAt))
                        .put("elapsedMs", 0)
                        .put("note", "")
                }
            )
            val acceptedIds = receipts.filter { it.accepted }.mapTo(HashSet()) { it.id }
            val rejectedCount = receipts.count { !it.accepted }
            rejected += rejectedCount
            uploadedAttempts += acceptedIds.size
            if (rejectedCount == 0) {
                pendingAttempts
                    .filter { it.id in acceptedIds }
                    .maxOfOrNull { it.answeredAt }
                    ?.let { store.lastAttemptUploadAt = maxOf(store.lastAttemptUploadAt, it) }
            }
        }

        val dirtySlips = dao.getAllCaptureSlips()
            .filter { it.syncStatus == SyncStatus.dirty && it.deletedAt == null }
        if (dirtySlips.isNotEmpty()) {
            val receipts = transport.pushCaptures(
                dirtySlips.map { slip ->
                    JSONObject()
                        .put("id", slip.id)
                        .put("body", slip.body)
                        .put("type", slip.type.name)
                        .put("topicHint", slip.topicHint.orEmpty())
                        .put("sourceLabel", slip.sourceLabel.orEmpty())
                        .put("createdAt", isoOfMillis(slip.createdAt))
                }
            )
            receipts.forEach { receipt ->
                val slip = dirtySlips.firstOrNull { it.id == receipt.id }
                if (receipt.accepted && slip != null) {
                    dao.upsertCaptureSlip(slip.copy(syncStatus = SyncStatus.clean))
                    uploadedCaptures += 1
                } else {
                    rejected += 1
                }
            }
        }

        val dirtyQuestions = dao.getAllReaderQuestions()
            .filter { it.syncStatus == SyncStatus.dirty && it.deletedAt == null }
        if (dirtyQuestions.isNotEmpty()) {
            val receipts = transport.pushReaderQuestions(
                dirtyQuestions.map { question ->
                    JSONObject()
                        .put("clientId", question.id)
                        .put("nodeId", question.nodeId)
                        .put("question", question.body)
                        .put("createdAt", isoOfMillis(question.createdAt))
                }
            )
            receipts.forEach { receipt ->
                val question = dirtyQuestions.firstOrNull { it.id == receipt.id }
                if (receipt.accepted && question != null) {
                    dao.upsertReaderQuestion(question.copy(syncStatus = SyncStatus.clean))
                    uploadedQuestions += 1
                } else {
                    rejected += 1
                }
            }
        }

        // A later local edit uses the prior revision as its base. Send only
        // the oldest pending change per node until the desktop receipts it.
        val pendingNodeChanges = dao.getPendingNodeOutbox(MaxNodePushChanges)
            .groupBy { it.aggregateId }
            .mapNotNull { (_, changes) -> changes.firstOrNull() }
        val preparedNodeChanges = pendingNodeChanges.mapNotNull { change ->
            val node = runCatching { ContentNodeCodec.decode(change.payloadJson) }.getOrNull()
            if (node == null || node.id.value != change.aggregateId || node.revision.value != change.newRevision) {
                rejected += 1
                null
            } else {
                change to JSONObject()
                    .put("changeId", change.changeId)
                    .put("id", node.id.value)
                    .put("title", node.title)
                    .put("area", node.area.slug)
                    .put("track", node.track)
                    .put("summary", node.summary)
                    .put("body", node.markdownBody)
                    .put("visibility", node.visibility)
                    .put("baseRevision", change.baseRevision ?: JSONObject.NULL)
                    .put("revision", change.newRevision)
                    .put("tombstone", node.deletedAt != null)
            }
        }
        if (preparedNodeChanges.isNotEmpty()) {
            val receiptsById = transport.pushNodes(preparedNodeChanges.map { it.second })
                .associateBy { it.id }
            preparedNodeChanges.forEach { (change, _) ->
                val receipt = receiptsById[change.aggregateId]
                if (receipt?.accepted == true && receipt.revision != null) {
                    dao.acknowledgeNodeContentPush(
                        changeId = change.changeId,
                        nodeId = change.aggregateId,
                        localRevision = change.newRevision,
                        serverRevision = receipt.revision
                    )
                    uploadedNodes += 1
                } else {
                    rejected += 1
                }
            }
        }

        val pendingQuizChanges = dao.getPendingQuizOutbox(MaxQuizPushChanges)
            .groupBy { it.aggregateId }
            .mapNotNull { (_, changes) -> changes.firstOrNull() }
        val decodedQuizChanges = pendingQuizChanges.mapNotNull { change ->
            val quiz = runCatching { QuizOutboxCodec.decode(change.payloadJson) }.getOrNull()
            val localQuiz = dao.getQuiz(change.aggregateId)
            if (quiz == null || quiz.id != change.aggregateId || quiz.revision != change.newRevision) {
                rejected += 1
                null
            } else {
                Triple(change, quiz, localQuiz)
            }
        }
        val mobileQuizBites = decodedQuizChanges
            .filter { (change, _, localQuiz) ->
                localQuiz?.deletedAt == null &&
                    (change.baseRevision == null || localQuiz?.sourceAnchor == MobileBiteSourceAnchor)
            }
            .map { (change, quiz, _) ->
                change to JSONObject()
                    .put("changeId", change.changeId)
                    .put("id", quiz.id)
                    .put("clientId", "android-quiz-${quiz.id}")
                    .put("sourceType", "mobile_bite")
                    .put("sourceId", quiz.id)
                    .put("title", quiz.prompt.take(80).ifBlank { "Phone bite" })
                    .put("area", quiz.area.ifBlank { "questions" })
                    .put("prompt", quiz.prompt)
                    .put("answer", quiz.answer)
                    .put("hint", "")
                    .put("explanationJson", org.json.JSONArray(listOf(quiz.explanation)).toString())
                    .put("difficulty", "medium")
                    .put("questionType", "blank")
                    .put("optionsJson", "[]")
                    .put("status", "active")
                    .put("lastReviewedAt", "")
                    .put("reviewCount", 0)
                    .put("lastRating", "")
                    .put("nextReviewAt", "")
                    .put("masteryScore", 0.0)
            }
        if (mobileQuizBites.isNotEmpty()) {
            val receiptsById = transport.pushBiteCards(mobileQuizBites.map { it.second })
                .associateBy { it.id }
            mobileQuizBites.forEach { (change, _) ->
                val receipt = receiptsById[change.aggregateId]
                val localQuiz = dao.getQuiz(change.aggregateId)
                if (receipt?.accepted == true && localQuiz != null) {
                    dao.acknowledgeQuizContentPush(
                        changeId = change.changeId,
                        quizId = change.aggregateId,
                        localRevision = change.newRevision,
                        serverRevision = change.newRevision
                    )
                    dao.upsertQuiz(
                        localQuiz.copy(
                            syncStatus = SyncStatus.clean,
                            baseRevision = change.newRevision,
                            sourceAnchor = MobileBiteSourceAnchor
                        )
                    )
                    uploadedBiteCards += 1
                } else {
                    rejected += 1
                }
            }
        }
        val preparedQuizChanges = decodedQuizChanges
            .filterNot { (change, _, localQuiz) ->
                localQuiz?.deletedAt == null &&
                    (change.baseRevision == null || localQuiz?.sourceAnchor == MobileBiteSourceAnchor)
            }
            .map { (change, quiz, localQuiz) ->
                change to JSONObject()
                    .put("changeId", change.changeId)
                    .put("id", quiz.id)
                    .put("area", quiz.area)
                    .put("body", QuizOutboxCodec.desktopBody(quiz))
                    .put("visibility", quiz.visibility)
                    .put("baseRevision", change.baseRevision ?: JSONObject.NULL)
                    .put("revision", change.newRevision)
                    .put("tombstone", localQuiz?.revision == change.newRevision && localQuiz.deletedAt != null)
            }
        if (preparedQuizChanges.isNotEmpty()) {
            val receiptsById = transport.pushQuizzes(preparedQuizChanges.map { it.second })
                .associateBy { it.id }
            preparedQuizChanges.forEach { (change, _) ->
                val receipt = receiptsById[change.aggregateId]
                if (receipt?.accepted == true && receipt.revision != null) {
                    dao.acknowledgeQuizContentPush(
                        changeId = change.changeId,
                        quizId = change.aggregateId,
                        localRevision = change.newRevision,
                        serverRevision = receipt.revision
                    )
                    uploadedQuizzes += 1
                } else {
                    rejected += 1
                }
            }
        }

        val dirtyBiteCards = dao.getAllBiteCards()
            .filter { it.syncStatus == "dirty" }
        if (dirtyBiteCards.isNotEmpty()) {
            val preparedBiteCards = dirtyBiteCards.map { card ->
                val stableClientId = card.clientId.ifBlank {
                    if (card.sourceType == "mobile_bite" || card.sourceType == "manual") "android-bite-${card.id}" else ""
                }
                card to JSONObject()
                    .put("changeId", "bite-${stableClientId.ifBlank { card.id.toString() }}-${card.updatedAt}")
                    .put("id", card.id.toString())
                    .put("clientId", stableClientId)
                    .put("sourceType", card.sourceType.ifBlank { "mobile_bite" })
                    .put("sourceId", card.sourceId.ifBlank { stableClientId.ifBlank { card.id.toString() } })
                    .put("title", card.title)
                    .put("area", card.area.ifBlank { "questions" })
                    .put("prompt", card.prompt)
                    .put("answer", card.answer)
                    .put("hint", card.hint)
                    .put("explanationJson", card.explanationJson)
                    .put("difficulty", card.difficulty.ifBlank { "medium" })
                    .put("questionType", card.questionType.ifBlank { "blank" })
                    .put("optionsJson", card.optionsJson.ifBlank { "[]" })
                    .put("status", card.status.ifBlank { "active" })
                    .put("lastReviewedAt", if (card.lastReviewedAt > 0) isoOfMillis(card.lastReviewedAt) else "")
                    .put("reviewCount", card.reviewCount)
                    .put("lastRating", card.lastRating)
                    .put("nextReviewAt", if (card.nextReviewAt > 0) isoOfMillis(card.nextReviewAt) else "")
                    .put("masteryScore", card.masteryScore)
            }
            val receiptsById = transport.pushBiteCards(preparedBiteCards.map { it.second })
                .associateBy { it.id }
            preparedBiteCards.forEach { (card, payload) ->
                val receipt = receiptsById[card.id.toString()]
                if (receipt?.accepted == true) {
                    dao.upsertBiteCards(
                        listOf(
                            card.copy(
                                clientId = payload.optString("clientId"),
                                area = payload.optString("area", "questions"),
                                syncStatus = "clean"
                            )
                        )
                    )
                    uploadedBiteCards += 1
                } else {
                    rejected += 1
                }
            }
        }

        return SyncPushReport(
            uploadedAttempts = uploadedAttempts,
            uploadedCaptures = uploadedCaptures,
            uploadedQuestions = uploadedQuestions,
            uploadedNodes = uploadedNodes,
            uploadedQuizzes = uploadedQuizzes,
            uploadedBiteCards = uploadedBiteCards,
            rejected = rejected
        )
    }

    private data class PageOutcome(
        val pulledNodes: Int,
        val pulledQuizzes: Int,
        val pulledQuestions: Int,
        val pulledCaptureSlips: Int,
        val pulledBiteCards: Int,
        val appliedAttempts: Int,
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
        var pulledBiteCards = 0
        var appliedAttempts = 0
        var skippedAttempts = 0
        var skippedRecords = 0
        var removed = 0
        var conflicts = 0

        val areasToEnsure = linkedMapOf<String, AreaEntity>()
        val nodes = mutableListOf<LearningNodeEntity>()
        val quizzes = mutableListOf<QuizItemEntity>()
        val questions = mutableListOf<ReaderQuestionEntity>()
        val slips = mutableListOf<CaptureSlipEntity>()
        val biteCards = mutableListOf<BiteCardEntity>()
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
        val biteCardIdsToPull = mutableListOf<String>()
        val attemptIdsToPull = linkedSetOf<String>()

        for (change in manifest.changes) {
            when (change.type) {
                SyncRecord.ReviewAttempt.TYPE -> if (!change.tombstone) attemptIdsToPull += change.id

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

                SyncRecord.BiteCard.TYPE -> {
                    if (scope.includesArea(change.area)) {
                        biteCardIdsToPull += change.id
                    }
                }
            }
        }

        pullInBatches(SyncRecord.Node.TYPE, nodeIdsToPull, scope)
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

        pullInBatches(SyncRecord.Quiz.TYPE, quizIdsToPull, scope)
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

        pullInBatches(SyncRecord.ReaderQuestion.TYPE, questionIdsToPull, scope)
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

        pullInBatches(SyncRecord.CaptureSlip.TYPE, slipIdsToPull, scope)
            .filterIsInstance<SyncRecord.CaptureSlip>()
            .forEach { record ->
                val local = dao.getCaptureSlip(record.id)
                if (local == null || local.syncStatus == SyncStatus.clean) {
                    slips += record.toEntity(local)
                    pulledCaptureSlips += 1
                }
            }

        pullInBatches(SyncRecord.BiteCard.TYPE, biteCardIdsToPull, scope)
            .filterIsInstance<SyncRecord.BiteCard>()
            .forEach { record ->
                biteCards += record.toBiteCardEntity(now())
                pulledBiteCards += 1
            }

        // Desktop-originated attempts: idempotent by client attempt ID; each
        // affected quiz's scheduling state is rebuilt from the merged log.
        val reviewStates = mutableListOf<ReviewStateEntity>()
        val attemptsToInsert = mutableListOf<ReviewAttemptEntity>()
        if (attemptIdsToPull.isNotEmpty()) {
            val knownAttemptIds = dao.getAllAttempts().mapTo(HashSet()) { it.id }
            val newAttempts = mutableListOf<ReviewAttemptEntity>()
            pullInBatches(SyncRecord.ReviewAttempt.TYPE, attemptIdsToPull.toList(), scope)
                .filterIsInstance<SyncRecord.ReviewAttempt>()
                .forEach { record ->
                    if (record.id in knownAttemptIds) return@forEach
                    val result = DesktopGradeToReviewResult[record.grade]
                    if (result == null) {
                        skippedAttempts += 1
                        return@forEach
                    }
                    if (dao.getQuiz(record.quizId) == null) {
                        skippedAttempts += 1
                        return@forEach
                    }
                    newAttempts += ReviewAttemptEntity(
                        id = record.id,
                        quizId = record.quizId,
                        result = result,
                        answeredAt = parseIsoMillis(record.answeredAt) ?: now(),
                        scheduledDueAt = 0L
                    )
                }
            if (newAttempts.isNotEmpty()) {
                val mergedAttempts = dao.getAllAttempts() + newAttempts
                val dueByAttemptId = mutableMapOf<String, Long>()
                newAttempts
                    .mapTo(linkedSetOf()) { it.quizId }
                    .forEach { quizId ->
                        val rebuilt = rebuildReviewState(quizId, mergedAttempts, dueByAttemptId)
                        reviewStates += rebuilt
                    }
                newAttempts.forEach { attempt ->
                    attemptsToInsert += attempt.copy(
                        scheduledDueAt = dueByAttemptId[attempt.id] ?: attempt.answeredAt
                    )
                }
                appliedAttempts += newAttempts.size
            }
        }

        dao.applySyncBatch(
            areas = areasToEnsure.values.toList(),
            nodes = nodes,
            quizzes = quizzes,
            questions = questions,
            captureSlips = slips,
            attempts = attemptsToInsert,
            reviewStates = reviewStates,
            biteCards = biteCards,
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
            pulledBiteCards = pulledBiteCards,
            appliedAttempts = appliedAttempts,
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

    private suspend fun pullInBatches(
        entityType: String,
        ids: List<String>,
        scope: SyncScope
    ): List<SyncRecord> = buildList {
        ids.chunked(MaxPullIds).forEach { batch ->
            addAll(transport.pull(entityType, batch, scope))
        }
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

    private fun SyncRecord.Node.toEntity(local: LearningNodeEntity?, areaId: String): LearningNodeEntity =
        toNodeEntity(local, areaId, now())

    private fun SyncRecord.Quiz.toEntity(
        parsed: DesktopQuizMarkdown.ParsedDesktopQuiz,
        local: QuizItemEntity?,
        areaId: String
    ): QuizItemEntity =
        toQuizEntity(parsed, local, areaId, now())

    private fun SyncRecord.ReaderQuestion.toEntity(local: ReaderQuestionEntity?): ReaderQuestionEntity =
        toQuestionEntity(local, now())

    private fun SyncRecord.CaptureSlip.toEntity(local: CaptureSlipEntity?): CaptureSlipEntity =
        toSlipEntity(local, now())

    private fun isoOfMillis(millis: Long): String =
        Instant.ofEpochMilli(millis).toString()

    private fun isDirty(status: SyncStatus): Boolean =
        status == SyncStatus.dirty || status == SyncStatus.conflicted

    /**
     * Rebuilds one quiz's scheduling state by replaying the merged attempt
     * log (local + pulled) through the scheduler in answeredAt order. Also
     * records each attempt's scheduled due time for storage.
     */
    private fun rebuildReviewState(
        quizId: String,
        allAttempts: List<ReviewAttemptEntity>,
        dueByAttemptId: MutableMap<String, Long>
    ): ReviewStateEntity {
        var ease = DefaultReviewEase
        var interval = 0
        var dueAt = now()
        var lastResult = ReviewResult.again
        val quizAttempts = allAttempts
            .filter { it.quizId == quizId }
            .sortedBy { it.answeredAt }
        quizAttempts.forEach { attempt ->
            val rating = when (attempt.result) {
                ReviewResult.again -> ReviewRating.Again
                ReviewResult.hard -> ReviewRating.Hard
                ReviewResult.good -> ReviewRating.Good
            }
            val result = ReviewScheduler.next(
                ReviewScheduleInput(
                    ease = ease,
                    intervalDays = interval,
                    attemptCount = 0,
                    answeredAt = Instant.ofEpochMilli(attempt.answeredAt)
                ),
                rating
            )
            ease = result.ease
            interval = result.intervalDays
            dueAt = result.dueAt.toEpochMilli()
            lastResult = attempt.result
            dueByAttemptId[attempt.id] = dueAt
        }
        return ReviewStateEntity(
            quizId = quizId,
            ease = ease,
            intervalDays = interval,
            dueAt = dueAt,
            lastResult = lastResult,
            attemptCount = quizAttempts.size,
            updatedAt = now()
        )
    }

    private companion object {
        const val MaxManifestPages = 40
        const val MaxPullIds = 200
        const val MaxNodePushChanges = 100
        const val MaxQuizPushChanges = 100
        const val DefaultAreaOrder = 1000
        const val DefaultReviewEase = 2.5
        const val MobileBiteSourceAnchor = "mobile_bite"

        /** Desktop's 4-grade scale collapses onto the Android 3-grade scheduler. */
        val DesktopGradeToReviewResult = mapOf(
            "again" to ReviewResult.again,
            "hard" to ReviewResult.hard,
            "good" to ReviewResult.good,
            "easy" to ReviewResult.good
        )
    }
}
