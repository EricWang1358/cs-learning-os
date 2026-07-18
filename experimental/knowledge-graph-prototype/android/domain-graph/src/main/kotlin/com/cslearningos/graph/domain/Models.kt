package com.cslearningos.graph.domain

/** 题目分类(RFC §3.2) */
enum class KgCategory { CS_BASIC, ALGORITHM, SYSTEM_DESIGN, BEHAVIORAL }

/**
 * 边作用域(RFC §3.1):
 * - [GLOBAL]: 全局 DAG 边, 对所有问题的树可见; 插入时需在 GLOBAL ∪ 全部 PROBLEM_LOCAL 边集上查环(ADR-1 保守规则)
 * - [PROBLEM_LOCAL]: 某问题私有分支边, 只出现在该问题的树视图中(部分继承的私有部分)
 */
enum class EdgeScope { GLOBAL, PROBLEM_LOCAL }

/** 掌握度状态机(RFC §3.5 规则引擎的输出状态) */
enum class MasteryState { UNKNOWN, LEARNING, FRAGILE, MASTERED }

/** 一次验证(测验)的判定结果 */
enum class Verdict { PASS, FAIL }

/** 领域错误(RFC §3.2, 与后端/桌面端共享同一套语义) */
sealed interface KgError {
    /** 插入边会成环, [path] 为成环路径(依赖顺序, 首尾相同节点闭合) */
    data class CycleDetected(val path: List<String>) : KgError

    /** 同一 commandId 携带不同指纹重放 → 幂等冲突 */
    data class CommandConflict(val commandId: String) : KgError

    /** 实体不存在, [kind] 如 "node"/"question"/"edge"/"proposal"/"quizItem"/"jdBatch" */
    data class NotFound(val kind: String, val id: String) : KgError

    /** 提案已超过 TTL */
    data class ProposalExpired(val proposalId: String) : KgError

    /** 提案/请求负载形状非法 */
    data class ProposalShapeInvalid(val reason: String) : KgError

    /** 存储层或其他未分类错误 */
    data class Storage(val message: String) : KgError
}

/**
 * 统一返回类型(内存版 DomainResult, 对齐 core:kernel 的 Ok/Err 两态语义)
 */
sealed interface DomainResult<out T> {
    data class Ok<T>(val value: T) : DomainResult<T>
    data class Err(val error: KgError) : DomainResult<Nothing>
}

/**
 * 嵌套前置结构(RFC §3.2): 同时表达多层链与"引用已有节点"(共享子树).
 * [existingNodeId] 非空时复用已有 learning_node(零拷贝共享), 否则创建新节点.
 */
data class PrerequisiteSpec(
    val title: String,
    val existingNodeId: String? = null,
    val markdownBody: String = "",
    val children: List<PrerequisiteSpec> = emptyList(),
)

/**
 * 节点掌握度投影(RFC §3.3 kg_mastery; 可重建投影, quiz 证据为事实源).
 * 纯数据, 状态迁移规则见 [MasteryEngine.applyVerdict].
 */
data class NodeMastery(
    val nodeId: String,
    val state: MasteryState = MasteryState.UNKNOWN,
    val score: Double = 0.0,
    val attempts: Int = 0,
    val failStreak: Int = 0,
    val lastVerdict: Verdict? = null,
    val updatedAt: Long = 0L,
)
