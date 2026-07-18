package com.cslearningos.graph.domain

/** 问题(Question)标识: 登记为 root 的学习节点, 对应 kg_question.question_id */
@JvmInline
value class QuestionId(val value: String)

/** 边标识: 对应 kg_edge.edge_id */
@JvmInline
value class EdgeId(val value: String)

/** AI 提案标识: 对应 kg_proposal.proposal_id */
@JvmInline
value class ProposalId(val value: String)

/** JD 拆解批次标识: 一批 JD → 题目的归属分组 */
@JvmInline
value class JdBatchId(val value: String)
