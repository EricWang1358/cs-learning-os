package com.cslearningos.graph.data

import kotlinx.serialization.Serializable

/**
 * Entity ↔ 持久化 DTO 映射。
 *
 * DTO 为裸类型(String/Int/Long/Double)数据类, 是 [KgStore] 端口与 application 层之间
 * 的交换货币; application 层的 value class(QuestionId/EdgeId/ProposalId/JdBatchId)与
 * 枚举(KgCategory/EdgeScope/MasteryState/Verdict)在 facade 边界收敛(其 name 与
 * [KgContract] 常量一一对应)。注意区分: RFC §3.2 读模型 KGNodeDto/KGEdgeDto 是
 * facade 组装的 UI 直渲 DTO, 不在本层。
 *
 * @Serializable 用于 replication_outbox 的 payload 投影(与既有 OutboxAppender 的
 * JSON 快照风格一致)。
 */

@Serializable
data class KgQuestionDto(
    val questionId: String,
    val rootNodeId: String,
    val areaId: String? = null,
    val problemNo: Int,
    val title: String,
    val category: String = KgContract.CATEGORY_CS_BASIC,
    val jdBatchId: String? = null,
    val status: String = KgContract.QUESTION_STATUS_ACTIVE,
    val revision: Long = 1,
    val createdAt: Long,
)

@Serializable
data class KgEdgeDto(
    val edgeId: String,
    val parentNodeId: String,
    val childNodeId: String,
    val scopeType: String = KgContract.SCOPE_GLOBAL,
    val scopeQuestionId: String? = null,
    val status: String = KgContract.EDGE_STATUS_ACTIVE,
    val createdBy: String = KgContract.CREATED_BY_USER,
    val revision: Long = 1,
    val createdAt: Long,
)

@Serializable
data class KgProposalDto(
    val proposalId: String,
    val kind: String,
    val payloadJson: String,
    val status: String = KgContract.PROPOSAL_STATUS_PENDING,
    val modelRef: String? = null,
    val commandId: String? = null,
    val expiresAt: Long,
    val createdAt: Long,
)

@Serializable
data class KgMasteryDto(
    val nodeId: String,
    val state: String = KgContract.MASTERY_UNKNOWN,
    val score: Double = 0.0,
    val attempts: Int = 0,
    val failStreak: Int = 0,
    val lastVerdict: String? = null,
    val updatedAt: Long,
    val revision: Long = 1,
)

@Serializable
data class KgMasteryEventDto(
    val eventId: String,
    val nodeId: String,
    val quizItemId: String,
    val verdict: String,
    val commandId: String,
    val createdAt: Long,
)

// ---------------- KgQuestion ----------------

fun KgQuestionEntity.toDto(): KgQuestionDto = KgQuestionDto(
    questionId = questionId,
    rootNodeId = rootNodeId,
    areaId = areaId,
    problemNo = problemNo,
    title = title,
    category = category,
    jdBatchId = jdBatchId,
    status = status,
    revision = revision,
    createdAt = createdAt,
)

fun KgQuestionDto.toEntity(): KgQuestionEntity = KgQuestionEntity(
    questionId = questionId,
    rootNodeId = rootNodeId,
    areaId = areaId,
    problemNo = problemNo,
    title = title,
    category = category,
    jdBatchId = jdBatchId,
    status = status,
    revision = revision,
    createdAt = createdAt,
)

// ---------------- KgEdge ----------------

fun KgEdgeEntity.toDto(): KgEdgeDto = KgEdgeDto(
    edgeId = edgeId,
    parentNodeId = parentNodeId,
    childNodeId = childNodeId,
    scopeType = scopeType,
    scopeQuestionId = scopeQuestionId,
    status = status,
    createdBy = createdBy,
    revision = revision,
    createdAt = createdAt,
)

fun KgEdgeDto.toEntity(): KgEdgeEntity = KgEdgeEntity(
    edgeId = edgeId,
    parentNodeId = parentNodeId,
    childNodeId = childNodeId,
    scopeType = scopeType,
    scopeQuestionId = scopeQuestionId,
    status = status,
    createdBy = createdBy,
    revision = revision,
    createdAt = createdAt,
)

// ---------------- KgProposal ----------------

fun KgProposalEntity.toDto(): KgProposalDto = KgProposalDto(
    proposalId = proposalId,
    kind = kind,
    payloadJson = payloadJson,
    status = status,
    modelRef = modelRef,
    commandId = commandId,
    expiresAt = expiresAt,
    createdAt = createdAt,
)

fun KgProposalDto.toEntity(): KgProposalEntity = KgProposalEntity(
    proposalId = proposalId,
    kind = kind,
    payloadJson = payloadJson,
    status = status,
    modelRef = modelRef,
    commandId = commandId,
    expiresAt = expiresAt,
    createdAt = createdAt,
)

// ---------------- KgMastery ----------------

fun KgMasteryEntity.toDto(): KgMasteryDto = KgMasteryDto(
    nodeId = nodeId,
    state = state,
    score = score,
    attempts = attempts,
    failStreak = failStreak,
    lastVerdict = lastVerdict,
    updatedAt = updatedAt,
    revision = revision,
)

fun KgMasteryDto.toEntity(): KgMasteryEntity = KgMasteryEntity(
    nodeId = nodeId,
    state = state,
    score = score,
    attempts = attempts,
    failStreak = failStreak,
    lastVerdict = lastVerdict,
    updatedAt = updatedAt,
    revision = revision,
)

// ---------------- KgMasteryEvent ----------------

fun KgMasteryEventEntity.toDto(): KgMasteryEventDto = KgMasteryEventDto(
    eventId = eventId,
    nodeId = nodeId,
    quizItemId = quizItemId,
    verdict = verdict,
    commandId = commandId,
    createdAt = createdAt,
)

fun KgMasteryEventDto.toEntity(): KgMasteryEventEntity = KgMasteryEventEntity(
    eventId = eventId,
    nodeId = nodeId,
    quizItemId = quizItemId,
    verdict = verdict,
    commandId = commandId,
    createdAt = createdAt,
)
