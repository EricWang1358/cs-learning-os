package com.cslearningos.graph.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** MasteryEngine 规则引擎测试(RFC §3.5 公式与边界) */
class MasteryEngineTest {

    private fun mastery(
        state: MasteryState = MasteryState.LEARNING,
        score: Double = 0.5,
        attempts: Int = 1,
        failStreak: Int = 0,
    ) = NodeMastery("n1", state, score, attempts, failStreak)

    /** 从零值起步按序折叠判定序列, 历史判定逐条穿入 recentVerdicts(模拟 kg_mastery_event 查询) */
    private fun runSequence(verdicts: List<Verdict>): NodeMastery {
        var m: NodeMastery? = null
        val history = ArrayList<Verdict>()
        for ((i, v) in verdicts.withIndex()) {
            m = MasteryEngine.applyVerdict("n1", m, history.toList(), v, i.toLong())
            history.add(v)
        }
        return m!!
    }

    // ------------------------------------------------------------------
    // FAIL 分支
    // ------------------------------------------------------------------

    @Test
    fun `FAIL-首次判定-从零值起步进入FRAGILE`() {
        val m = MasteryEngine.applyVerdict("n1", null, emptyList(), Verdict.FAIL, now = 100L)
        assertEquals(MasteryState.FRAGILE, m.state)
        assertEquals(0.0, m.score, 1e-9)
        assertEquals(1, m.attempts)
        assertEquals(1, m.failStreak)
        assertEquals(Verdict.FAIL, m.lastVerdict)
        assertEquals(100L, m.updatedAt)
    }

    @Test
    fun `FAIL-减分025且不低于0`() {
        val m1 = MasteryEngine.applyVerdict("n1", mastery(score = 0.5), emptyList(), Verdict.FAIL, 1L)
        assertEquals(0.25, m1.score, 1e-9)
        val m2 = MasteryEngine.applyVerdict("n1", mastery(score = 0.1), emptyList(), Verdict.FAIL, 1L)
        assertEquals(0.0, m2.score, 1e-9) // 下界夹取 [0,1]
    }

    @Test
    fun `FAIL-failStreak累加且MASTERED也会跌回FRAGILE`() {
        val after = MasteryEngine.applyVerdict(
            "n1",
            mastery(MasteryState.MASTERED, score = 1.0, attempts = 4),
            emptyList(),
            Verdict.FAIL,
            1L,
        )
        assertEquals(MasteryState.FRAGILE, after.state)
        assertEquals(0.75, after.score, 1e-9)
        assertEquals(1, after.failStreak)
        assertEquals(5, after.attempts)
    }

    // ------------------------------------------------------------------
    // PASS 分支
    // ------------------------------------------------------------------

    @Test
    fun `PASS-首次判定-UNKNOWN转LEARNING`() {
        val m = MasteryEngine.applyVerdict("n1", null, emptyList(), Verdict.PASS, 7L)
        assertEquals(MasteryState.LEARNING, m.state)
        assertEquals(0.2, m.score, 1e-9)
        assertEquals(0, m.failStreak)
    }

    @Test
    fun `PASS-达到08进入MASTERED-门槛边界`() {
        val mastered = MasteryEngine.applyVerdict("n1", mastery(score = 0.6), emptyList(), Verdict.PASS, 1L)
        assertEquals(0.8, mastered.score, 1e-9)
        assertEquals(MasteryState.MASTERED, mastered.state)

        val notYet = MasteryEngine.applyVerdict("n1", mastery(score = 0.59), emptyList(), Verdict.PASS, 1L)
        assertEquals(0.79, notYet.score, 1e-9)
        assertEquals(MasteryState.LEARNING, notYet.state)
    }

    @Test
    fun `PASS-加分不超过1-上界夹取`() {
        val m = MasteryEngine.applyVerdict("n1", mastery(MasteryState.MASTERED, score = 0.95), emptyList(), Verdict.PASS, 1L)
        assertEquals(1.0, m.score, 1e-9)
        assertEquals(MasteryState.MASTERED, m.state)
    }

    @Test
    fun `PASS-failStreak复位`() {
        val m = MasteryEngine.applyVerdict(
            "n1",
            mastery(MasteryState.FRAGILE, score = 0.1, failStreak = 3),
            emptyList(),
            Verdict.PASS,
            1L,
        )
        assertEquals(0, m.failStreak)
        assertEquals(0.3, m.score, 1e-9)
        assertEquals(MasteryState.LEARNING, m.state)
    }

    @Test
    fun `连加四次PASS精确到08-无浮点累计误差`() {
        val m = runSequence(listOf(Verdict.PASS, Verdict.PASS, Verdict.PASS, Verdict.PASS))
        assertEquals(0.8, m.score, 1e-9)
        assertEquals(MasteryState.MASTERED, m.state)
    }

    // ------------------------------------------------------------------
    // MASTERED 的"近 3 次(含本次)无 FAIL"子句(RFC §3.5)
    // ------------------------------------------------------------------

    @Test
    fun `近3次窗口-连续PASS序列第4次MASTERED`() {
        // [PASS,PASS,PASS,PASS]: 第 4 次 score 达 0.8 且窗口 [P,P,P] 无 FAIL
        val seq = runSequence(listOf(Verdict.PASS, Verdict.PASS, Verdict.PASS, Verdict.PASS))
        assertEquals(MasteryState.MASTERED, seq.state)
        // 第 3 次仅 0.6 仍是 LEARNING(算术由公式直接推出)
        val third = runSequence(listOf(Verdict.PASS, Verdict.PASS, Verdict.PASS))
        assertEquals(0.6, third.score, 1e-9)
        assertEquals(MasteryState.LEARNING, third.state)
    }

    @Test
    fun `近3次窗口-FAIL_PASS_PASS即使score达到08以上也LEARNING`() {
        // 先 MASTERED(0.8) 再 FAIL, 随后两次 PASS: 0.55 → 0.75 → 0.95,
        // 末次窗口 = [FAIL,PASS] + 本次PASS 含 FAIL → 0.95 也 LEARNING
        val seq = runSequence(
            listOf(Verdict.PASS, Verdict.PASS, Verdict.PASS, Verdict.PASS, Verdict.FAIL, Verdict.PASS, Verdict.PASS),
        )
        assertEquals(0.95, seq.score, 1e-9)
        assertEquals(MasteryState.LEARNING, seq.state)
    }

    @Test
    fun `近3次窗口-PASS_FAIL_PASS_PASS仍LEARNING`() {
        // 历史 [PASS,FAIL,PASS] + 本次 PASS: takeLast(2) = [FAIL,PASS] 含 FAIL → LEARNING
        val m = MasteryEngine.applyVerdict(
            "n1",
            mastery(MasteryState.LEARNING, score = 0.75, attempts = 3),
            listOf(Verdict.PASS, Verdict.FAIL, Verdict.PASS),
            Verdict.PASS,
            9L,
        )
        assertEquals(0.95, m.score, 1e-9)
        assertEquals(MasteryState.LEARNING, m.state)
    }

    @Test
    fun `近3次窗口-再补一次PASS窗口滑动后FAIL滑出即MASTERED`() {
        // 历史 [FAIL,PASS,PASS] + 本次 PASS: 窗口滑动为 [PASS,PASS,PASS] 无 FAIL → MASTERED
        val m = MasteryEngine.applyVerdict(
            "n1",
            mastery(MasteryState.LEARNING, score = 0.95, attempts = 7),
            listOf(Verdict.FAIL, Verdict.PASS, Verdict.PASS),
            Verdict.PASS,
            10L,
        )
        assertEquals(1.0, m.score, 1e-9)
        assertEquals(MasteryState.MASTERED, m.state)
    }

    // ------------------------------------------------------------------
    // isWeak
    // ------------------------------------------------------------------

    @Test
    fun `isWeak-状态与得分双重条件`() {
        assertTrue(MasteryEngine.isWeak(mastery(MasteryState.FRAGILE, score = 0.5)))
        assertTrue(MasteryEngine.isWeak(mastery(MasteryState.LEARNING, score = 0.59)))
        assertFalse(MasteryEngine.isWeak(mastery(MasteryState.LEARNING, score = 0.6))) // 严格小于
        assertFalse(MasteryEngine.isWeak(mastery(MasteryState.UNKNOWN, score = 0.0)))
        assertFalse(MasteryEngine.isWeak(mastery(MasteryState.MASTERED, score = 0.9)))
    }

    // ------------------------------------------------------------------
    // diagnoseGap
    // ------------------------------------------------------------------

    @Test
    fun `diagnoseGap-取加权最高者-阻塞面优先于裸分数`() {
        val candidates = listOf(
            MasteryEngine.GapCandidate("A", "甲", mastery(score = 0.5), blocksCount = 0, recentFailures = 1),
            MasteryEngine.GapCandidate("B", "乙", mastery(score = 0.8), blocksCount = 4, recentFailures = 0),
        )
        // A: (1-0.5)*(1+0)=0.5; B: (1-0.8)*(1+4)=1.0 → 虽然 B 分数高, 但阻塞面大
        val d = MasteryEngine.diagnoseGap("quiz-1", "failed", candidates)
        assertEquals("quiz-1", d.quizItemId)
        assertEquals("failed", d.failedNodeId)
        assertEquals("B", d.weakestPrerequisite?.nodeId)
        assertEquals(SuggestedAction.REINFORCE_PREREQUISITE, d.suggestedAction)
        assertEquals(4, d.weakestPrerequisite?.blocksCount)
    }

    @Test
    fun `diagnoseGap-无前置-LeafReinforce`() {
        val d = MasteryEngine.diagnoseGap("quiz-1", "failed", emptyList())
        assertNull(d.weakestPrerequisite)
        assertEquals(SuggestedAction.LEAF_REINFORCE, d.suggestedAction)
    }

    @Test
    fun `diagnoseGap-并列按nodeId升序保证确定性`() {
        val candidates = listOf(
            MasteryEngine.GapCandidate("b", "乙", mastery(score = 0.5), blocksCount = 1, recentFailures = 0),
            MasteryEngine.GapCandidate("a", "甲", mastery(score = 0.5), blocksCount = 1, recentFailures = 0),
        )
        val d = MasteryEngine.diagnoseGap("quiz-1", "failed", candidates)
        assertEquals("a", d.weakestPrerequisite?.nodeId)
    }
}
