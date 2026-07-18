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
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { API_BASE, fetchJson } from '../lib/apiClient'
import type {
  ApiGraphResponse,
  ApiKgBottlenecksResponse,
  ApiKgQuestionsResponse,
  KgMasteryState,
  KgQuestionSummary,
} from '../types/api'
import { fetchExport3d } from '../graph3d/fetchGraph'
import type { GraphExport, SceneNode } from '../graph3d/types'
import { mergeHeadingsIntoExport, type NavigationHeadingItem } from '../graph3d/headingMerge'
import { KnowledgeGraph3D } from '../graph3d/KnowledgeGraph3D'

const MASTERY_LABELS: Record<KgMasteryState, string> = {
  UNKNOWN: 'Unknown',
  LEARNING: 'Learning',
  FRAGILE: 'Fragile',
  MASTERED: 'Mastered',
}

type RootRef = { id: string; isQuestion: boolean; label: string }

type SortMode = 'problemNo' | 'area' | 'alpha'

const AREA_LABELS: Record<string, string> = {
  'cs-fundamentals': 'CS Fundamentals',
  'algorithms': 'Algorithms',
  'networkprogramming': 'Network Programming',
  'projects': 'Projects',
  'tools': 'Tools',
  'r-language': 'R Language',
  'attacklab': 'Attack Lab',
  'knowledge-graph': 'Knowledge Graph',
  'abilities': 'Abilities',
  'stack-prerequisite': 'Stack Prerequisites',
  'stubs': 'Stubs',
}

function sortQuestions(questions: KgQuestionSummary[], mode: SortMode): KgQuestionSummary[] {
  const sorted = [...questions]
  switch (mode) {
    case 'problemNo':
      sorted.sort((a, b) => (a.problemNo - b.problemNo) || a.title.localeCompare(b.title))
      break
    case 'area':
      sorted.sort((a, b) =>
        (a.areaId || '').localeCompare(b.areaId || '') || (a.problemNo - b.problemNo)
      )
      break
    case 'alpha':
      sorted.sort((a, b) => a.title.localeCompare(b.title))
      break
  }
  return sorted
}

function groupByArea(questions: KgQuestionSummary[]): Map<string, KgQuestionSummary[]> {
  const map = new Map<string, KgQuestionSummary[]>()
  for (const q of questions) {
    const key = q.areaId || 'other'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(q)
  }
  // Sort area groups: cs-fundamentals first, then alphabetical
  const sorted = new Map([...map.entries()].sort(([a], [b]) => {
    if (a === 'cs-fundamentals') return -1
    if (b === 'cs-fundamentals') return 1
    return a.localeCompare(b)
  }))
  return sorted
}

export function KnowledgeGraphPanel() {
  const location = useLocation()
  const navigate = useNavigate()
  const requestedRoot = useMemo(() => {
    const params = new URLSearchParams(location.search)
    const id = params.get('root')
    if (!id) return null
    return {
      id,
      isQuestion: params.get('rootIsQuestion') === 'true',
    }
  }, [location.search])
  const [questions, setQuestions] = useState<KgQuestionSummary[]>([])
  const [bottlenecks, setBottlenecks] = useState<ApiKgBottlenecksResponse['items']>([])
  const [root, setRoot] = useState<RootRef | null>(null)
  const rootRef = useRef<RootRef | null>(null)
  const [rootHistory, setRootHistory] = useState<RootRef[]>([])
  const [graphData, setGraphData] = useState<GraphExport | null>(null)
  const [graphError, setGraphError] = useState('')
  const [listError, setListError] = useState('')
  const [isLoadingGraph, setIsLoadingGraph] = useState(false)

  // Search, sort, and area grouping
  const [searchQuery, setSearchQuery] = useState('')
  const [expandedAreas, setExpandedAreas] = useState<Set<string>>(new Set())
  const [sortBy, setSortBy] = useState<SortMode>('problemNo')
  const [groupEnabled, setGroupEnabled] = useState(true)

  const selectRoot = useCallback(
    (next: RootRef, options: { remember?: boolean; clearHistory?: boolean } = {}) => {
      const current = rootRef.current
      if (options.clearHistory) {
        setRootHistory([])
      } else if (options.remember && current && current.id !== next.id) {
        setRootHistory((history) => [...history, current].slice(-20))
      }
      rootRef.current = next
      setRoot(next)
    },
    [],
  )

  const goBackToPreviousRoot = useCallback(() => {
    const previous = rootHistory[rootHistory.length - 1]
    if (!previous) return
    setRootHistory((history) => history.slice(0, -1))
    rootRef.current = previous
    setRoot(previous)
  }, [rootHistory])

  const loadLists = useCallback(async (selectFirst: boolean) => {
    try {
      const [questionData, bottleneckData] = await Promise.all([
        fetchJson<ApiKgQuestionsResponse>('/api/kg/questions'),
        fetchJson<ApiKgBottlenecksResponse>('/api/kg/bottlenecks?minDistinctQuestions=1&limit=10'),
      ])
      setQuestions(questionData.questions)
      setBottlenecks(bottleneckData.items)
      setListError('')
      if (selectFirst) {
        if (requestedRoot) {
          selectRoot(
            { id: requestedRoot.id, isQuestion: requestedRoot.isQuestion, label: requestedRoot.id },
            { clearHistory: true },
          )
        } else if (questionData.questions.length > 0 && !rootRef.current) {
          const first = questionData.questions[0]
          selectRoot(
            { id: first.questionId, isQuestion: true, label: first.title },
            { clearHistory: true },
          )
        }
      }
    } catch (err) {
      setListError(err instanceof Error ? err.message : 'Failed to load knowledge graph data')
    }
  }, [requestedRoot, selectRoot])

  useEffect(() => {
    void loadLists(true)
  }, [loadLists])

  const rootId = root?.id ?? ''
  const rootIsQuestion = root?.isQuestion ?? false
  useEffect(() => {
    if (!rootId) {
      setGraphData(null)
      return
    }
    let cancelled = false
    setIsLoadingGraph(true)
    setGraphError('')
    fetchExport3d(API_BASE, rootId, rootIsQuestion)
      .then(async (result) => {
        if (!rootIsQuestion) {
          const resolvedTitle = result.data.nodes.find((node) => node.id === rootId)?.title
          if (resolvedTitle) {
            rootRef.current = { id: rootId, isQuestion: false, label: resolvedTitle }
            setRoot((current) => (current?.id === rootId ? { ...current, label: resolvedTitle } : current))
          }
        }
        // reroot 到单个知识节点时, 参照导航图谱 node 层, 把该节点笔记的
        // 章节子标题合并为卫星节点(kind='heading'); 问题树根视图保持纯 DAG。
        if (!rootIsQuestion) {
          try {
            const nav = await fetchJson<ApiGraphResponse>(
              `/api/graph/node/${encodeURIComponent(rootId)}?page=1`,
            )
            const headings = (nav.children ?? []) as NavigationHeadingItem[]
            return { ...result, data: mergeHeadingsIntoExport(result.data, rootId, headings) }
          } catch {
            return result // 无子标题或节点不存在 → 原样展示
          }
        }
        return result
      })
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
  }, [rootId, rootIsQuestion])

  const rerootToNode = useCallback(
    (node: SceneNode) => {
      // 子标题卫星: 打开笔记对应章节(与导航图谱 heading 跳转一致), 不参与 reroot
      if (node.kind === 'heading') {
        if (node.href) navigate(node.href)
        return
      }
      if (node.isRoot) return
      selectRoot({ id: node.id, isQuestion: false, label: node.title }, { remember: true })
    },
    [navigate, selectRoot],
  )

  const openNode = useCallback(
    (node: SceneNode) => {
      // 子标题卫星单/双击都应打开对应章节
      if (node.kind === 'heading' && node.href) {
        navigate(node.href)
        return
      }
      navigate(`/nodes/${encodeURIComponent(node.id)}?focus=1`)
    },
    [navigate],
  )

  const rootLabel = useMemo(() => {
    if (!root) return 'No root selected'
    return root.isQuestion ? `Question: ${root.label}` : `Rerooted: ${root.label}`
  }, [root])

  // Derived: filtered, sorted, grouped question list
  const processedQuestions = useMemo(() => {
    let filtered = questions
    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase()
      filtered = questions.filter(
        (item) =>
          item.title.toLowerCase().includes(q) ||
          (item.areaId || '').toLowerCase().includes(q) ||
          String(item.problemNo).includes(q),
      )
    }
    return {
      all: sortQuestions(filtered, sortBy),
      grouped: groupEnabled ? groupByArea(sortQuestions(filtered, sortBy)) : null,
      total: filtered.length,
      matched: filtered.length,
    }
  }, [questions, searchQuery, sortBy, groupEnabled])

  const toggleArea = useCallback((areaId: string) => {
    setExpandedAreas((prev) => {
      const next = new Set(prev)
      if (next.has(areaId)) next.delete(areaId); else next.add(areaId)
      return next
    })
  }, [])

  return (
    <section className="kgraph-shell" aria-label="Knowledge tree 3D">
      <aside className="kgraph-rail">
        {/* Search & filter controls */}
        <div className="kgraph-rail-section kgraph-controls">
          <div className="kgraph-search-row">
            <input
              type="search"
              className="kgraph-search-input"
              placeholder="Search questions, area…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Filter question roots"
            />
          </div>
          <div className="kgraph-filter-row">
            <label className="kgraph-filter-label">
              <select
                className="kgraph-filter-select"
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as SortMode)}
              >
                <option value="problemNo">By problem #</option>
                <option value="area">By area</option>
                <option value="alpha">A–Z</option>
              </select>
            </label>
            <button
              type="button"
              className={`kgraph-group-toggle ${groupEnabled ? 'active' : ''}`}
              onClick={() => setGroupEnabled((v) => !v)}
              aria-pressed={groupEnabled}
              title={groupEnabled ? 'Hide area groups' : 'Group by area'}
            >
              📁
            </button>
          </div>
        </div>

        {/* Question list */}
        <div className="kgraph-rail-section">
          <p className="eyebrow">
            Problem roots
            {searchQuery && (
              <span className="kgraph-count"> · {processedQuestions.matched} of {questions.length}</span>
            )}
          </p>
          {questions.length === 0 ? (
            <p className="kgraph-empty-hint">
              No registered question roots. Open a node's graph link to inspect its prerequisite subtree.
            </p>
          ) : processedQuestions.matched === 0 ? (
            <p className="kgraph-empty-hint">No questions match "{searchQuery}".</p>
          ) : processedQuestions.grouped ? (
            /* Grouped by area */
            [...processedQuestions.grouped.entries()].map(([areaId, areaQuestions]) => {
              const isExpanded = expandedAreas.has(areaId)
              const areaLabel = AREA_LABELS[areaId] || areaId
              return (
                <div key={areaId} className="kgraph-area-group">
                  <button
                    type="button"
                    className="kgraph-area-header"
                    onClick={() => toggleArea(areaId)}
                    aria-expanded={isExpanded}
                  >
                    <span className="kgraph-area-caret">{isExpanded ? '▾' : '▸'}</span>
                    <span className="kgraph-area-label">{areaLabel}</span>
                    <span className="kgraph-area-count">{areaQuestions.length}</span>
                  </button>
                  {isExpanded && (
                    <ul className="kgraph-question-list">
                      {areaQuestions.map((question) => (
                        <li key={question.questionId}>
                          <button
                            type="button"
                            className={
                              root?.isQuestion && root.id === question.questionId ? 'active' : ''
                            }
                            onClick={() => {
                              selectRoot({
                                id: question.questionId,
                                isQuestion: true,
                                label: question.title,
                              }, { clearHistory: true })
                            }}
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
              )
            })
          ) : (
            /* Flat list (grouping disabled) */
            <ul className="kgraph-question-list">
              {processedQuestions.all.map((question) => (
                <li key={question.questionId}>
                  <button
                    type="button"
                    className={
                      root?.isQuestion && root.id === question.questionId ? 'active' : ''
                    }
                    onClick={() => {
                      selectRoot({
                        id: question.questionId,
                        isQuestion: true,
                        label: question.title,
                      }, { clearHistory: true })
                    }}
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
                      selectRoot({ id: item.nodeId, isQuestion: false, label: item.title }, { remember: true })
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
          <div className="kgraph-header-row">
            <div>
              <h2>Knowledge Graph</h2>
              <p>{rootLabel}</p>
            </div>
            <button
              type="button"
              className="focus-toggle"
              disabled={rootHistory.length === 0}
              onClick={goBackToPreviousRoot}
              aria-label="Back to previous graph root"
              title="Return to the previous reroot"
            >
              ← Previous root
            </button>
          </div>
        </header>
        {listError ? <p className="kgraph-error">{listError}</p> : null}
        <div className="kgraph-canvas">
          {graphError ? (
            <p className="kgraph-error">{graphError}</p>
          ) : graphData ? (
            <KnowledgeGraph3D
              data={graphData}
              layout="layered"
              onNodeClick={rerootToNode}
              onNodeDoubleClick={openNode}
              highlightShared
            />
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
