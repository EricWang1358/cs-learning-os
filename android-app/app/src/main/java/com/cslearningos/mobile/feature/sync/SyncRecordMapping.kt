package com.cslearningos.mobile.feature.sync

import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.BiteCardEntity
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.NodeFtsEntity
import com.cslearningos.mobile.data.QuizFtsEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.SyncStatus
import java.time.OffsetDateTime

/** Shared record -> Room entity conversions used by pull and package import. */
internal fun parseIsoMillis(value: String): Long? =
    runCatching {
        if (value.isBlank()) return null
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrNull()

internal fun syncStableRowId(id: String): Int =
    id.hashCode().let { if (it == Int.MIN_VALUE) 1 else kotlin.math.abs(it) }.coerceAtLeast(1)

internal fun SyncRecord.Node.toNodeEntity(
    local: LearningNodeEntity?,
    areaId: String,
    now: Long
): LearningNodeEntity =
    LearningNodeEntity(
        id = id,
        title = title,
        markdownBody = body,
        createdAt = local?.createdAt ?: now,
        updatedAt = parseIsoMillis(updatedAt) ?: now,
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

internal fun SyncRecord.Quiz.toQuizEntity(
    parsed: DesktopQuizMarkdown.ParsedDesktopQuiz,
    local: QuizItemEntity?,
    areaId: String,
    now: Long
): QuizItemEntity =
    QuizItemEntity(
        id = id,
        nodeId = local?.nodeId,
        prompt = parsed.prompt,
        answer = parsed.answer,
        explanation = parsed.explanation,
        source = QuizSource.markdown,
        sourceAnchor = local?.sourceAnchor,
        createdAt = local?.createdAt ?: now,
        updatedAt = parseIsoMillis(updatedAt) ?: now,
        revision = revision,
        syncStatus = SyncStatus.clean,
        deletedAt = null,
        area = areaId,
        track = local?.track ?: "general",
        visibility = visibility,
        isStarter = local?.isStarter ?: false,
        baseRevision = revision
    )

internal fun SyncRecord.ReaderQuestion.toQuestionEntity(
    local: ReaderQuestionEntity?,
    now: Long
): ReaderQuestionEntity =
    ReaderQuestionEntity(
        id = id,
        nodeId = targetId,
        body = question,
        createdAt = parseIsoMillis(createdAt) ?: local?.createdAt ?: now,
        resolvedAt = parseIsoMillis(resolvedAt),
        syncStatus = SyncStatus.clean,
        deletedAt = null
    )

internal fun SyncRecord.CaptureSlip.toSlipEntity(
    local: CaptureSlipEntity?,
    now: Long
): CaptureSlipEntity =
    CaptureSlipEntity(
        id = id,
        body = body,
        type = CaptureSlipType.entries.firstOrNull { it.name == slipType } ?: CaptureSlipType.concept_seed,
        topicHint = topicHint.ifBlank { null },
        sourceLabel = sourceLabel.ifBlank { null },
        linkedNodeId = local?.linkedNodeId,
        status = CaptureSlipStatus.entries.firstOrNull { it.name == status } ?: CaptureSlipStatus.inbox,
        createdAt = parseIsoMillis(createdAt) ?: local?.createdAt ?: now,
        updatedAt = parseIsoMillis(updatedAt) ?: now,
        revision = revision,
        syncStatus = SyncStatus.clean,
        deletedAt = null
    )

internal fun nodeFtsOf(node: LearningNodeEntity): NodeFtsEntity =
    NodeFtsEntity(
        rowId = syncStableRowId(node.id),
        nodeId = node.id,
        title = node.title,
        body = node.markdownBody
    )

internal fun quizFtsOf(quiz: QuizItemEntity): QuizFtsEntity =
    QuizFtsEntity(
        rowId = syncStableRowId(quiz.id),
        quizId = quiz.id,
        prompt = quiz.prompt,
        answer = quiz.answer
    )

internal fun SyncRecord.BiteCard.toBiteCardEntity(now: Long): BiteCardEntity =
    BiteCardEntity(
        id = id.toLongOrNull() ?: 0L,
        sourceType = sourceType,
        sourceId = sourceId,
        title = title,
        area = area,
        difficulty = difficulty,
        prompt = prompt,
        answer = answer,
        hint = hint,
        explanationJson = explanationJson,
        questionType = questionType,
        optionsJson = optionsJson,
        status = status,
        syncStatus = "clean",
        createdAt = now,
        updatedAt = now
    )
