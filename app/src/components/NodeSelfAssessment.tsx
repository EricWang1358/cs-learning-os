import { useCallback, useState } from 'react'
import { fetchJson, postJson } from '../lib/apiClient'
import type { KgGapResponse, KgMasterySummary, KgVerificationResponse } from '../types/api'

export interface NodeSelfAssessmentProps {
  nodeId: string
  onOpenNode?: (slug: string) => void
}

const MASTERY_LABELS: Record<KgMasterySummary['state'], string> = {
  UNKNOWN: '未知',
  LEARNING: '学习中',
  FRAGILE: '脆弱',
  MASTERED: '已掌握',
}

export function NodeSelfAssessment({ nodeId, onOpenNode }: NodeSelfAssessmentProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [lastVerdict, setLastVerdict] = useState<'PASS' | 'FAIL' | null>(null)
  const [mastery, setMastery] = useState<KgMasterySummary | null>(null)
  const [gap, setGap] = useState<KgGapResponse | null>(null)
  const [error, setError] = useState('')

  const submit = useCallback(
    async (verdict: 'PASS' | 'FAIL') => {
      if (isSubmitting) return
      setIsSubmitting(true)
      setError('')
      setGap(null)
      try {
        const commandId = crypto.randomUUID().replaceAll('-', '')
        const result = await postJson<KgVerificationResponse>('/api/kg/verifications', {
          commandId,
          nodeId,
          quizItemId: nodeId,
          verdict,
        })
        setLastVerdict(verdict)
        setMastery(result.mastery)
        if (verdict === 'FAIL') {
          try {
            const gapData = await fetchJson<KgGapResponse>(
              `/api/kg/quizzes/${encodeURIComponent(nodeId)}/gap`,
            )
            setGap(gapData)
          } catch {
            // 前置诊断失败不阻塞 verdict 反馈
            setGap(null)
          }
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Self-assessment failed')
        setMastery(null)
        setGap(null)
      } finally {
        setIsSubmitting(false)
      }
    },
    [isSubmitting, nodeId],
  )

  return (
    <div className="node-self-assessment">
      <div className="self-assessment-actions">
        <button
          type="button"
          className="focus-toggle self-assessment-pass"
          disabled={isSubmitting}
          onClick={() => submit('PASS')}
          title="我理解了"
        >
          {isSubmitting && lastVerdict === 'PASS' ? '提交中…' : '懂了'}
        </button>
        <button
          type="button"
          className="focus-toggle self-assessment-fail"
          disabled={isSubmitting}
          onClick={() => submit('FAIL')}
          title="还没理解"
        >
          {isSubmitting && lastVerdict === 'FAIL' ? '提交中…' : '没懂'}
        </button>
      </div>
      {mastery && (
        <p className="self-assessment-result">
          <span className={`mastery-badge mastery-${mastery.state.toLowerCase()}`}>
            {MASTERY_LABELS[mastery.state]}
          </span>
          <span>得分 {mastery.score.toFixed(2)} · 尝试 {mastery.attempts}</span>
        </p>
      )}
      {gap?.weakestPrerequisite && (
        <div className="self-assessment-gap">
          <p>建议先复习这个前置节点：</p>
          <button
            type="button"
            className="text-link"
            onClick={() => onOpenNode?.(gap.weakestPrerequisite!.nodeId)}
          >
            {gap.weakestPrerequisite.title}
          </button>
          <span className="node-meta">
            {gap.weakestPrerequisite.mastery} · {gap.weakestPrerequisite.score.toFixed(2)} · 阻塞{' '}
            {gap.weakestPrerequisite.blocksCount} 个下游
          </span>
        </div>
      )}
      {gap && !gap.weakestPrerequisite && (
        <p className="self-assessment-gap-hint">该节点没有已登记的前置，建议回头精读本文。</p>
      )}
      {error && <p className="inline-error">{error}</p>}
    </div>
  )
}
