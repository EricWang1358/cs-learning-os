/**
 * KnowledgeGraphPanel — desktop entry for the KnowledgeGraph deep module
 * (RFC-knowledge-graph). Problem-rooted knowledge DAG rendered in 3D.
 *
 * Data flow (all against /api/kg, see backend/kg_router.py):
 * - question roots: GET /api/kg/questions → pick a root → GET /api/kg/export3d
 * - reroot: click a node → export3d with rootIsQuestion=false (pure query)
 * - bottlenecks: GET /api/kg/bottlenecks → click reroots to the weak node
 * - create question: POST /api/kg/questions (idempotent commandId)
 *
 * Self-contained state (unlike SyncPanel, which is prop-driven from App.tsx) so
 * App.tsx only lazy-loads this panel — keeping three.js out of the main chunk.
 */
import { useCallback, useEffect, useMemo, useState } from 'react'
import { API_BASE, fetchJson, postJson } from '../lib/apiClient'
import type {
  ApiKgBottlenecksResponse,
  ApiKgQuestionsResponse,
  KgCategory,
  KgCreateQuestionResponse,
  KgMasteryState,
  KgQuestionSummary,
} from '../types/api'
import { fetchExport3d } from '../graph3d/fetchGraph'
import type { GraphExport, SceneNode } from '../graph3d/types'
import { KnowledgeGraph3D } from '../graph3d/KnowledgeGraph3D'

const KG_CATEGORIES: KgCategory[] = ['CS_BASIC', 'ALGORITHM', 'SYSTEM_DESIGN', 'BEHAVIORAL']

const MASTERY_LABELS: Record<KgMasteryState, string> = {
  UNKNOWN: 'Unknown',
  LEARNING: 'Learning',
  FRAGILE: 'Fragile',
  MASTERED: 'Mastered',
}

function newCommandId(): string {
  return crypto.randomUUID().replaceAll('-', '')
}

type RootRef = { id: string; isQuestion: boolean; label: string }

export function KnowledgeGraphPanel() {
  const [questions, setQuestions] = useState<KgQuestionSummary[]>([])
  const [bottlenecks, setBottlenecks] = useState<ApiKgBottlenecksResponse['items']>([])
  const [root, setRoot] = useState<RootRef | null>(null)
  const [graphData, setGraphData] = useState<GraphExport | null>(null)
  const [graphError, setGraphError] = useState('')
  const [listError, setListError] = useState('')
  const [isLoadingGraph, setIsLoadingGraph] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [newCategory, setNewCategory] = useState<KgCategory>('CS_BASIC')
  const [isCreating, setIsCreating] = useState(false)

  const loadLists = useCallback(async (selectFirst: boolean) => {
    try {
      const [questionData, bottleneckData] = await Promise.all([
        fetchJson<ApiKgQuestionsResponse>('/api/kg/questions'),
        fetchJson<ApiKgBottlenecksResponse>('/api/kg/bottlenecks?minDistinctQuestions=1&limit=10'),
      ])
      setQuestions(questionData.questions)
      setBottlenecks(bottleneckData.items)
      setListError('')
      if (selectFirst && questionData.questions.length > 0) {
        setRoot((current) => {
          if (current) return current
          const first = questionData.questions[0]
          return { id: first.questionId, isQuestion: true, label: first.title }
        })
      }
    } catch (err) {
      setListError(err instanceof Error ? err.message : 'Failed to load knowledge graph data')
    }
  }, [])

  useEffect(() => {
    void loadLists(true)
  }, [loadLists])

  useEffect(() => {
    if (!root) {
      setGraphData(null)
      return
    }
    let cancelled = false
    setIsLoadingGraph(true)
    setGraphError('')
    fetchExport3d(API_BASE, root.id, root.isQuestion)
      .then((result) => {
        if (!cancelled) setGraphData(result.data)
      })
      .catch((err) => {
        if (!cancelled) {
          setGraphData(null)
          setGraphError(err instanceof Error ? err.message : 'Failed to load 3D export')
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoadingGraph(false)
      })
    return () => {
      cancelled = true
    }
  }, [root])

  const rerootToNode = useCallback((node: SceneNode) => {
    if (node.isRoot) return
    setRoot({ id: node.id, isQuestion: false, label: node.title })
  }, [])

  const createQuestion = async () => {
    const title = newTitle.trim()
    if (!title || isCreating) return
    setIsCreating(true)
    try {
      const created = await postJson<KgCreateQuestionResponse>('/api/kg/questions', {
        commandId: newCommandId(),
        title,
        category: newCategory,
      })
      setNewTitle('')
      await loadLists(false)
      setRoot({ id: created.questionId, isQuestion: true, label: created.title })
    } catch (err) {
      setListError(err instanceof Error ? err.message : 'Failed to create question')
    } finally {
      setIsCreating(false)
    }
  }

  const rootLabel = useMemo(() => {
    if (!root) return 'No root selected'
    return root.isQuestion ? `Question: ${root.label}` : `Rerooted: ${root.label}`
  }, [root])

  return (
    <section className="kgraph-shell" aria-label="Knowledge tree 3D">
      <aside className="kgraph-rail">
        <div className="kgraph-rail-section">
          <p className="eyebrow">Problem roots</p>
          <form
            className="kgraph-create-form"
            onSubmit={(event) => {
              event.preventDefault()
              void createQuestion()
            }}
          >
            <input
              type="text"
              value={newTitle}
              placeholder="New question (e.g. LC 300 LIS)"
              onChange={(event) => setNewTitle(event.target.value)}
            />
            <div className="kgraph-create-row">
              <select
                value={newCategory}
                onChange={(event) => setNewCategory(event.target.value as KgCategory)}
                aria-label="Question category"
              >
                {KG_CATEGORIES.map((category) => (
                  <option key={category} value={category}>
                    {category}
                  </option>
                ))}
              </select>
              <button type="submit" className="focus-toggle" disabled={!newTitle.trim() || isCreating}>
                {isCreating ? 'Creating…' : 'Create'}
              </button>
            </div>
          </form>
          {questions.length === 0 ? (
            <p className="kgraph-empty-hint">
              No questions yet. Create one above — each question grows its own prerequisite tree.
            </p>
          ) : (
            <ul className="kgraph-question-list">
              {questions.map((question) => (
                <li key={question.questionId}>
                  <button
                    type="button"
                    className={
                      root?.isQuestion && root.id === question.questionId ? 'active' : ''
                    }
                    onClick={() =>
                      setRoot({
                        id: question.questionId,
                        isQuestion: true,
                        label: question.title,
                      })
                    }
                  >
                    <span className="kgraph-question-title">
                      #{question.problemNo} {question.title}
                    </span>
                    <span className="kgraph-question-meta">{question.category}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="kgraph-rail-section">
          <p className="eyebrow">Bottlenecks</p>
          {bottlenecks.length === 0 ? (
            <p className="kgraph-empty-hint">
              No weak prerequisite nodes yet. Verifications recorded during review will surface
              them here.
            </p>
          ) : (
            <ul className="kgraph-bottleneck-list">
              {bottlenecks.map((item) => (
                <li key={item.nodeId}>
                  <button
                    type="button"
                    onClick={() =>
                      setRoot({ id: item.nodeId, isQuestion: false, label: item.title })
                    }
                  >
                    <span className="kgraph-question-title">{item.title}</span>
                    <span className={`mastery-badge mastery-${item.mastery.toLowerCase()}`}>
                      {MASTERY_LABELS[item.mastery]}
                    </span>
                    <span className="kgraph-question-meta">
                      blocks {item.blocksCount} · {item.dependentFailCount} tree
                      {item.dependentFailCount === 1 ? '' : 's'}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </aside>

      <div className="kgraph-main">
        <header className="search-header cockpit-header kgraph-header">
          <p className="eyebrow">Knowledge tree OS</p>
          <h2>Knowledge Graph</h2>
          <p>{rootLabel}</p>
        </header>
        {listError ? <p className="kgraph-error">{listError}</p> : null}
        <div className="kgraph-canvas">
          {graphError ? (
            <p className="kgraph-error">{graphError}</p>
          ) : graphData ? (
            <KnowledgeGraph3D data={graphData} onNodeClick={rerootToNode} highlightShared />
          ) : (
            <p className="kgraph-empty-hint">
              {isLoadingGraph
                ? 'Loading 3D graph…'
                : root
                  ? 'No graph data for this root.'
                  : questions.length === 0
                    ? 'Create your first question to start growing a knowledge tree.'
                    : 'Select a question on the left.'}
            </p>
          )}
        </div>
      </div>
    </section>
  )
}
