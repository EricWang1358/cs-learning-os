package com.cslearningos.graph.domain

/** 问题摘要(RFC §3.2 DTO) */
data class QuestionSummary(
    val questionId: String,
    val rootNodeId: String,
    val problemNo: Int,
    val title: String,
    val category: KgCategory,
)

/** 树视图中的节点(UI 直渲); [depth] 为距树根的最长拓扑距离, [parentCount] 为本视图边集内的父边数 */
data class KGNodeDto(
    val nodeId: String,
    val title: String,
    val depth: Int,
    val parentCount: Int,
    val mastery: NodeMastery,
)

/** 树视图中的边; [parent] 依赖 [child](child 是前置) */
data class KGEdgeDto(
    val edgeId: String,
    val parent: String,
    val child: String,
    val scope: EdgeScope,
)

/**
 * 树物化视图(RFC §3.1 "树 = 视图"):
 * [layers] 按深度分层(第 0 层为根); [sharedNodeIds] 为视图内 parentCount >= 2 的共享节点集合
 */
data class TreeSnapshot(
    val rootNodeId: String,
    val questionId: String?,
    val nodes: List<KGNodeDto>,
    val edges: List<KGEdgeDto>,
    val layers: List<List<String>>,
    val sharedNodeIds: Set<String>,
)

/** recordVerification 的返回: 更新后的掌握度 + 建议下一步补强的前置(得分最低, 至多 3 个) */
data class MasteryUpdate(
    val nodeId: String,
    val mastery: NodeMastery,
    val suggestedNext: List<KGNodeDto>,
)

/** 漏洞诊断命中的最弱前置 */
data class WeakestPrerequisite(
    val nodeId: String,
    val title: String,
    val mastery: NodeMastery,
    val blocksCount: Int,
    val recentFailures: Int,
)

/** diagnoseGap 的建议动作 */
enum class SuggestedAction {
    /** 有前置可追: 去补强 [GapDiagnosis.weakestPrerequisite] */
    REINFORCE_PREREQUISITE,

    /** 失败节点无前置(叶子): 就地反复练习该叶子 */
    LEAF_REINFORCE,
}

/** 漏洞诊断结果(RFC §3.5: 取直接前置中 (1-score)*(1+blocksCount) 最高者) */
data class GapDiagnosis(
    val quizItemId: String,
    val failedNodeId: String,
    val weakestPrerequisite: WeakestPrerequisite?,
    val suggestedAction: SuggestedAction,
)

/** 瓶颈节点(RFC §3.5: 被 >= minDistinctQuestions 棵不同问题的弱节点依赖的祖先) */
data class BottleneckNode(
    val nodeId: String,
    val title: String,
    val mastery: NodeMastery,
    val blocksCount: Int,
    val distinctQuestionCount: Int,
    val recentFailures: Int,
)

/** 单个分类的进度 */
data class CategoryProgress(
    val total: Int,
    val mastered: Int,
    val progress: Double,
)

/** JD 批次专家进度(RFC §3.2 DTO); [progress] = mastered / total(total=0 时为 0) */
data class ExpertiseProgress(
    val total: Int,
    val mastered: Int,
    val fragile: Int,
    val learning: Int,
    val unknown: Int,
    val progress: Double,
    val perCategory: Map<KgCategory, CategoryProgress>,
)

/** confirmProposal 的落库结果 */
data class ConfirmResult(
    val createdQuestionIds: List<String>,
    val createdNodeIds: List<String>,
    val reusedNodeIds: List<String>,
)

/** AI 前置链提案(不落库, 待确认) */
data class PrerequisiteProposal(
    val proposalId: String,
    val tree: PrerequisiteSpec,
    val reusedNodeIds: List<String>,
    val expiresAt: Long,
)

/** JD 拆解提案中的一道题 */
data class JdProposalQuestion(
    val category: KgCategory,
    val seq: Int,
    val title: String,
    val seedPrerequisites: List<PrerequisiteSpec> = emptyList(),
)

/** AI JD 拆解提案(不落库, 待确认) */
data class JdProposal(
    val proposalId: String,
    val batchId: String,
    val questions: List<JdProposalQuestion>,
    val expiresAt: Long,
)

/** 3D 导出契约(RFC §3.4): [payloadJson] 为 schemaVersion=1 的稳定 JSON, [contentHash] 为节点+边集合的 SHA-256 */
data class GraphExport(
    val payloadJson: String,
    val contentHash: String,
    val nodeCount: Int,
    val edgeCount: Int,
)
