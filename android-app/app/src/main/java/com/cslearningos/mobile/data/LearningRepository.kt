package com.cslearningos.mobile.data

import com.cslearningos.mobile.core.common.AndroidArchitectureConstants
import com.cslearningos.mobile.domain.ReviewRating
import com.cslearningos.mobile.feature.capture.data.CaptureRepository
import com.cslearningos.mobile.feature.library.data.LibraryRepository
import com.cslearningos.mobile.feature.review.data.ReviewRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlin.math.absoluteValue

class LearningRepository private constructor(
    private val dao: LearningDao,
    private val libraryRepository: LibraryRepository,
    private val captureRepository: CaptureRepository,
    private val reviewRepository: ReviewRepository
) {
    constructor(dao: LearningDao) : this(
        dao = dao,
        libraryRepository = LibraryRepository(dao),
        captureRepository = CaptureRepository(dao),
        reviewRepository = ReviewRepository(dao)
    )

    val areas: Flow<List<AreaEntity>> = libraryRepository.areas
    val nodes: Flow<List<LearningNodeEntity>> = libraryRepository.nodes
    val trashNodes: Flow<List<LearningNodeEntity>> = libraryRepository.trashNodes
    val quizzes: Flow<List<QuizItemEntity>> = reviewRepository.quizzes
    val openReaderQuestions: Flow<List<ReaderQuestionEntity>> = dao.observeOpenReaderQuestions()
    val inboxCaptureSlips: Flow<List<CaptureSlipEntity>> = captureRepository.inboxCaptureSlips

    fun dueQuizzes(now: Long): Flow<List<QuizItemEntity>> = reviewRepository.dueQuizzes(now)

    suspend fun getNode(id: String): LearningNodeEntity? = libraryRepository.getNode(id)

    suspend fun getQuiz(id: String): QuizItemEntity? = reviewRepository.getQuiz(id)

    suspend fun saveNode(
        id: String?,
        title: String,
        markdownBody: String,
        areaId: String? = null,
        now: Long = System.currentTimeMillis()
    ): LearningNodeEntity = libraryRepository.saveNode(
        id = id,
        title = title,
        markdownBody = markdownBody,
        areaId = areaId,
        now = now
    )

    suspend fun markRead(nodeId: String, now: Long = System.currentTimeMillis()) {
        libraryRepository.markRead(nodeId = nodeId, now = now)
    }

    suspend fun moveNodeToTrash(nodeId: String, now: Long = System.currentTimeMillis()) {
        libraryRepository.moveNodeToTrash(nodeId = nodeId, now = now)
    }

    suspend fun restoreNodeFromTrash(nodeId: String, now: Long = System.currentTimeMillis()) {
        libraryRepository.restoreNodeFromTrash(nodeId = nodeId, now = now)
    }

    suspend fun permanentlyDeleteNode(nodeId: String, now: Long = System.currentTimeMillis()) {
        libraryRepository.permanentlyDeleteNode(nodeId = nodeId, now = now)
    }

    suspend fun clearStarterContent(now: Long = System.currentTimeMillis()) {
        libraryRepository.clearStarterContent(now = now)
    }

    suspend fun saveReaderQuestion(
        nodeId: String,
        body: String,
        now: Long = System.currentTimeMillis()
    ): ReaderQuestionEntity {
        val question = ReaderQuestionEntity(
            id = UUID.randomUUID().toString(),
            nodeId = nodeId,
            body = body.trim(),
            createdAt = now,
            resolvedAt = null,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )
        dao.upsertReaderQuestion(question)
        return question
    }

    suspend fun resolveReaderQuestion(questionId: String, now: Long = System.currentTimeMillis()) {
        val question = dao.getReaderQuestion(questionId) ?: return
        dao.upsertReaderQuestion(
            question.copy(
                resolvedAt = now,
                syncStatus = SyncStatus.dirty
            )
        )
    }

    suspend fun saveCaptureSlip(
        body: String,
        type: CaptureSlipType,
        topicHint: String?,
        sourceLabel: String?,
        status: CaptureSlipStatus = CaptureSlipStatus.inbox,
        now: Long = System.currentTimeMillis()
    ): CaptureSlipEntity = captureRepository.saveCaptureSlip(
        body = body,
        type = type,
        topicHint = topicHint,
        sourceLabel = sourceLabel,
        status = status,
        now = now
    )

    suspend fun updateCaptureSlipStatus(
        slipId: String,
        status: CaptureSlipStatus,
        now: Long = System.currentTimeMillis()
    ): CaptureSlipEntity? = captureRepository.updateCaptureSlipStatus(
        slipId = slipId,
        status = status,
        now = now
    )

    suspend fun archiveCaptureSlip(slipId: String, now: Long = System.currentTimeMillis()) {
        captureRepository.archiveCaptureSlip(slipId = slipId, now = now)
    }

    suspend fun markCaptureSlipConverted(
        slipId: String,
        nodeId: String,
        now: Long = System.currentTimeMillis()
    ) {
        captureRepository.markCaptureSlipConverted(slipId = slipId, nodeId = nodeId, now = now)
    }

    suspend fun seedStarterContent(pack: StarterContentPackage) {
        libraryRepository.seedStarterContent(pack)
    }

    suspend fun saveManualQuiz(
        nodeId: String?,
        prompt: String,
        answer: String,
        explanation: String,
        now: Long = System.currentTimeMillis()
    ): QuizItemEntity = reviewRepository.saveManualQuiz(
        nodeId = nodeId,
        prompt = prompt,
        answer = answer,
        explanation = explanation,
        now = now
    )

    suspend fun answerQuiz(
        quizId: String,
        rating: ReviewRating,
        now: Long = System.currentTimeMillis()
    ) {
        reviewRepository.answerQuiz(quizId = quizId, rating = rating, now = now)
    }

    suspend fun createArea(name: String, now: Long = System.currentTimeMillis()): AreaEntity =
        libraryRepository.createArea(name = name, now = now)

    suspend fun renameArea(areaId: String, name: String, now: Long = System.currentTimeMillis()) {
        libraryRepository.renameArea(areaId = areaId, name = name, now = now)
    }

    suspend fun deleteAreaIfEmpty(areaId: String, now: Long = System.currentTimeMillis()): Boolean =
        libraryRepository.deleteAreaIfEmpty(areaId = areaId, now = now)

    suspend fun moveNodeToArea(nodeId: String, targetAreaId: String, now: Long = System.currentTimeMillis()) {
        libraryRepository.moveNodeToArea(nodeId = nodeId, targetAreaId = targetAreaId, now = now)
    }

    suspend fun toggleNodeChecked(nodeId: String, now: Long = System.currentTimeMillis()) {
        libraryRepository.toggleNodeChecked(nodeId = nodeId, now = now)
    }

    suspend fun search(rawQuery: String): List<SearchResultEntity> = libraryRepository.search(rawQuery)

    suspend fun exportBackup(now: Long = System.currentTimeMillis()): String =
        BackupCodec.encode(
            LearningBackup(
                schemaVersion = BackupCodec.SchemaVersion,
                exportedAt = now,
                areas = dao.getAllAreas(),
                nodes = dao.getAllNodes(),
                quizzes = dao.getAllQuizzes(),
                reviewStates = dao.getAllReviewStates(),
                attempts = dao.getAllAttempts(),
                readerQuestions = dao.getAllReaderQuestions(),
                captureSlips = dao.getAllCaptureSlips()
            )
        )

    suspend fun exportReadableMarkdown(now: Long = System.currentTimeMillis()): String {
        val areasById = dao.getAllAreas().associateBy { it.id }
        val activeNodes = dao.getAllNodes()
            .filter { it.deletedAt == null && it.visibility != TrashVisibility }
            .sortedWith(compareBy<LearningNodeEntity> { it.area }.thenBy { it.track }.thenBy { it.order }.thenBy { it.title })
        val activeQuestions = dao.getAllReaderQuestions()
            .filter { it.deletedAt == null && it.resolvedAt == null }
            .groupBy { it.nodeId }
        val inboxSlips = dao.getAllCaptureSlips()
            .filter {
                it.deletedAt == null && it.status in setOf(
                    CaptureSlipStatus.inbox,
                    CaptureSlipStatus.ai_queued,
                    CaptureSlipStatus.ai_drafting,
                    CaptureSlipStatus.ai_draft_ready
                )
            }
        val activeQuizzes = dao.getAllQuizzes()
            .filter { it.deletedAt == null }
            .sortedWith(compareBy<QuizItemEntity> { it.area }.thenBy { it.track }.thenBy { it.prompt })

        return buildString {
            appendLine("# CS Learning OS Markdown Export")
            appendLine()
            appendLine("- Exported at: $now")
            appendLine("- Format: readable Markdown/TXT for desktop review, printing, or manual merge.")
            appendLine()
            appendLine("## Nodes")
            appendLine()
            if (activeNodes.isEmpty()) {
                appendLine("_No active nodes._")
                appendLine()
            }
            activeNodes.forEach { node ->
                appendLine("## ${node.title}")
                appendLine()
                appendLine("Area: ${exportAreaLabel(node, areasById)} / Track: ${node.track} / Order: ${node.order}")
                if (node.summary.isNotBlank()) {
                    appendLine()
                    appendLine("> ${node.summary}")
                }
                appendLine()
                appendLine(node.markdownBody.trim())
                appendLine()
                val questions = activeQuestions[node.id].orEmpty()
                if (questions.isNotEmpty()) {
                    appendLine("### Open Questions")
                    questions.forEach { question -> appendLine("- ${question.body}") }
                    appendLine()
                }
            }

            appendLine("## Capture Slip Inbox")
            appendLine()
            if (inboxSlips.isEmpty()) {
                appendLine("_No open capture slips._")
                appendLine()
            } else {
                inboxSlips.sortedByDescending { it.createdAt }.forEach { slip ->
                    appendLine("- [${slip.type.name}] ${slip.body}")
                    slip.topicHint?.let { appendLine("  - Topic: $it") }
                    slip.sourceLabel?.let { appendLine("  - Source: $it") }
                }
                appendLine()
            }

            appendLine("## Quiz Cards")
            appendLine()
            if (activeQuizzes.isEmpty()) {
                appendLine("_No active quiz cards._")
            } else {
                activeQuizzes.forEach { quiz ->
                    appendLine("### ${quiz.prompt}")
                    appendLine()
                    appendLine("Answer: ${quiz.answer}")
                    if (quiz.explanation.isNotBlank()) {
                        appendLine()
                        appendLine(quiz.explanation)
                    }
                    appendLine()
                }
            }
        }.trimEnd() + "\n"
    }

    suspend fun restoreBackup(rawJson: String) {
        val backup = BackupCodec.decode(rawJson)
        dao.restoreBackup(
            areas = if (backup.areas.isEmpty()) inferAreasFromNodes(backup.nodes) else backup.areas,
            nodes = backup.nodes,
            quizzes = backup.quizzes,
            states = backup.reviewStates,
            attempts = backup.attempts,
            questions = backup.readerQuestions,
            captureSlips = backup.captureSlips,
            nodeFts = backup.nodes
                .filter { it.deletedAt == null && it.visibility != TrashVisibility }
                .map {
                    NodeFtsEntity(
                        rowId = stableRowId(it.id),
                        nodeId = it.id,
                        title = it.title,
                        body = it.markdownBody
                    )
                },
            quizFts = backup.quizzes
                .filter { it.deletedAt == null && it.visibility != TrashVisibility }
                .map {
                    QuizFtsEntity(
                        rowId = stableRowId(it.id),
                        quizId = it.id,
                        prompt = it.prompt,
                        answer = it.answer
                    )
                }
        )
    }

    private fun stableRowId(id: String): Int = id.hashCode().absoluteValue.coerceAtLeast(MinimumStableRowId)

    private fun inferAreasFromNodes(nodes: List<LearningNodeEntity>): List<AreaEntity> =
        nodes
            .map { it.areaId.ifBlank { it.area } to it.area }
            .distinctBy { it.first }
            .mapIndexed { index, (areaId, slug) ->
                AreaEntity(
                    id = areaId,
                    slug = slug,
                    name = slug,
                    order = (index + 1) * AndroidArchitectureConstants.AreaOrderStep,
                    createdAt = BackupRestoreTimestamp,
                    updatedAt = BackupRestoreTimestamp,
                    deletedAt = null
                )
            }

    private fun exportAreaLabel(node: LearningNodeEntity, areasById: Map<String, AreaEntity>): String =
        areasById[node.areaId]
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: node.area

    private companion object {
        const val BackupRestoreTimestamp = 0L
        const val MinimumStableRowId = 1
        const val TrashVisibility = "trash"
    }
}
