package com.cslearningos.graph.domain

import kotlin.math.round

/**
 * 掌握度/漏洞诊断规则引擎(RFC §3.5, v1 默认实现, 离线可用).
 * 纯函数: 不读写任何存储, 状态迁移只依赖入参.
 */
object MasteryEngine {

    /** PASS 加分步长 */
    const val PASS_DELTA = 0.2

    /** FAIL 减分步长 */
    const val FAIL_DELTA = 0.25

    /** MASTERED 得分门槛(含) */
    const val MASTERED_THRESHOLD = 0.8

    /** 弱节点得分门槛(严格小于) */
    const val WEAK_THRESHOLD = 0.6

    /** 门槛比较的浮点容差 */
    private const val EPS = 1e-9

    /** "近 3 次无 FAIL" 的窗口大小(含本次判定) */
    const val RECENT_WINDOW = 3

    /** 判定弱节点(RFC §3.5): FRAGILE/LEARNING 且 score < 0.6; UNKNOWN/MASTERED 不算弱 */
    fun isWeak(mastery: NodeMastery): Boolean =
        (mastery.state == MasteryState.FRAGILE || mastery.state == MasteryState.LEARNING) &&
            mastery.score < WEAK_THRESHOLD

    /**
     * 应用一次验证判定, 返回新的掌握度投影(不修改入参).
     *
     * RFC §3.5 公式:
     * - FAIL: failStreak+1; score = max(0, score-0.25); state = FRAGILE(attempts>=1) 否则 LEARNING;
     * - PASS: failStreak=0; score = min(1, score+0.2);
     *   score>=0.8 且近 3 次无 FAIL → MASTERED, 否则 LEARNING;
     * - 首次判定后 UNKNOWN 不再保留(首次 PASS → LEARNING).
     *
     * [current] 为 null 时按 UNKNOWN 零值起步; [now] 写入 updatedAt.
     * [recentVerdicts] 为该节点最近至多 [RECENT_WINDOW] 次历史判定(不含本次, 按时间升序),
     * 由调用方从 kg_mastery_event 查询; MASTERED 判定的窗口 = recentVerdicts.takeLast(2) + 本次.
     * 说明: score 落库前保留 4 位小数以消除 0.2/0.25 连加的浮点累计误差(0.6+0.2 精确得 0.8).
     */
    fun applyVerdict(
        nodeId: String,
        current: NodeMastery?,
        recentVerdicts: List<Verdict>,
        verdict: Verdict,
        now: Long,
    ): NodeMastery {
        val cur = current ?: NodeMastery(nodeId = nodeId)
        return when (verdict) {
            Verdict.FAIL -> {
                val attempts = cur.attempts + 1
                val failStreak = cur.failStreak + 1
                val score = round4((cur.score - FAIL_DELTA).coerceAtLeast(0.0))
                val state = if (attempts >= 1) MasteryState.FRAGILE else MasteryState.LEARNING
                cur.copy(
                    nodeId = nodeId,
                    state = state,
                    score = score,
                    attempts = attempts,
                    failStreak = failStreak,
                    lastVerdict = Verdict.FAIL,
                    updatedAt = now,
                )
            }
            Verdict.PASS -> {
                val attempts = cur.attempts + 1
                val failStreak = 0
                val score = round4((cur.score + PASS_DELTA).coerceAtMost(1.0))
                // 近 3 次(含本次)无 FAIL: 历史取最后 2 条 + 本次 PASS 组成窗口
                val window = recentVerdicts.takeLast(RECENT_WINDOW - 1) + Verdict.PASS
                val noRecentFail = Verdict.FAIL !in window
                val state =
                    if (score + EPS >= MASTERED_THRESHOLD && noRecentFail) MasteryState.MASTERED
                    else MasteryState.LEARNING
                cur.copy(
                    nodeId = nodeId,
                    state = state,
                    score = score,
                    attempts = attempts,
                    failStreak = failStreak,
                    lastVerdict = Verdict.PASS,
                    updatedAt = now,
                )
            }
        }
    }

    /**
     * 漏洞诊断(RFC §3.5): 取失败节点的直接前置中 (1-score)*(1+blocksCount) 最高者;
     * 并列时按 nodeId 升序保证确定性. [directPrerequisites] 为空 → LEAF_REINFORCE 且 weakestPrerequisite=null.
     */
    fun diagnoseGap(
        quizItemId: String,
        failedNodeId: String,
        directPrerequisites: List<GapCandidate>,
    ): GapDiagnosis {
        if (directPrerequisites.isEmpty()) {
            return GapDiagnosis(
                quizItemId = quizItemId,
                failedNodeId = failedNodeId,
                weakestPrerequisite = null,
                suggestedAction = SuggestedAction.LEAF_REINFORCE,
            )
        }
        val weakest = directPrerequisites
            .map { it to (1.0 - it.mastery.score) * (1.0 + it.blocksCount) }
            .sortedWith(compareByDescending<Pair<GapCandidate, Double>> { it.second }.thenBy { it.first.nodeId })
            .first().first
        return GapDiagnosis(
            quizItemId = quizItemId,
            failedNodeId = failedNodeId,
            weakestPrerequisite = WeakestPrerequisite(
                nodeId = weakest.nodeId,
                title = weakest.title,
                mastery = weakest.mastery,
                blocksCount = weakest.blocksCount,
                recentFailures = weakest.recentFailures,
            ),
            suggestedAction = SuggestedAction.REINFORCE_PREREQUISITE,
        )
    }

    /** diagnoseGap 的输入候选: 一个直接前置及其展示/排序特征 */
    data class GapCandidate(
        val nodeId: String,
        val title: String,
        val mastery: NodeMastery,
        val blocksCount: Int,
        val recentFailures: Int,
    )

    /** 保留 4 位小数(消除浮点累计误差) */
    private fun round4(v: Double): Double = round(v * 10000.0) / 10000.0
}
