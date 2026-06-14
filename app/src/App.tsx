import { lazy, startTransition, Suspense, useEffect, useRef, useState, useDeferredValue } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import './App.css'
import { DailyBitePanel } from './components/DailyBitePanel'
import { DailyReviewPanel, type DailyReviewRating } from './components/DailyReviewPanel'
import { HealthActionPanels } from './components/HealthActionPanels'
import { QuestionQueueDetail } from './components/QuestionQueueDetail'
import { ReaderQuestionPanel } from './components/ReaderQuestionPanel'
import { SearchHeaderControls } from './SearchHeaderControls'
import {
  defaultNodeSort,
  defaultQuizSort,
  visibleNodeSortOptions,
  visibleQuizSortOptions,
} from './searchSort'
import { ApiRequestError, deleteJson, fetchJson, postJson, putJson } from './lib/apiClient'
import { clearStoredEditDraft, readStoredEditDraft, writeStoredEditDraft } from './lib/editDraftStorage'
import {
  buildTableOfContents,
  replaceMarkdownLineRange,
  sectionReadingAnchor,
  type MarkdownSection,
  type ReadingReturnAnchor,
  type SectionEditDraft,
} from './lib/markdown'
import { graphApiPath, routeFromLocation, routeSearch } from './lib/routes'
import type {
  AiDraftScope,
  AiJob,
  AiJobEvent,
  AiRevision,
  ApiAiJobEventsResponse,
  ApiAiJobResponse,
  ApiAiJobsResponse,
  ApiAiPreflightResponse,
  ApiAreasResponse,
  ApiDueReviewsResponse,
  ApiGraphResponse,
  ApiLlmWikiExportResponse,
  ApiNodeResponse,
  ApiNodesResponse,
  ApiPackageExportResponse,
  ApiQuizResponse,
  ApiQuizAttemptResponse,
  ApiQuizzesResponse,
  ApiReaderQuestionResponse,
  ApiReaderQuestionsResponse,
  ApiSystemRepairResponse,
  ApiSystemSchemaResponse,
  ApiSystemMetricsResponse,
  ApiTracksResponse,
  AreaSummary,
  GraphPayload,
  NodeCreateDraft,
  NodeDetail,
  NodeSummary,
  QuizDetail,
  QuizSummary,
  ReaderQuestion,
  ReviewQueueItem,
  StoragePartition,
  SystemMetrics,
  TrackSummary,
} from './types/api'

const GraphNavigator = lazy(() =>
  import('./components/GraphNavigator').then((module) => ({ default: module.GraphNavigator })),
)

const MarkdownView = lazy(() =>
  import('./components/MarkdownView').then((module) => ({ default: module.MarkdownView })),
)

type LineDiffRow = {
  kind: 'same' | 'added' | 'removed'
  text: string
}

type LineDiffSummary = {
  added: number
  removed: number
  rows: LineDiffRow[]
}

const SYSTEM_AREAS = ['all', 'archive', 'trash']
const READ_MARK_MIN_INTERVAL_SECONDS = 300

const areaLabels: Record<string, string> = {
  all: 'All nodes',
  algorithms: 'Algorithms',
  projects: 'Projects',
  abilities: 'Abilities',
  'cs-fundamentals': 'CS fundamentals',
  tools: 'Tools',
  questions: 'Questions',
  archive: 'Archive',
  trash: 'Trashbin',
}

const stableAreas = ['abilities', 'algorithms', 'cs-fundamentals', 'projects', 'tools', 'questions']

const trackLabels: Record<string, string> = {
  general: 'General',
  'c-and-memory': 'C and memory',
  'gdb-debugging': 'GDB debugging',
  'x86-64-assembly': 'x86-64 assembly',
  'bomb-lab': 'Bomb Lab',
  networking: 'Networking',
}

function slugTitle(slug: string) {
  return slug
    .split('-')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function slugifyInput(value: string) {
  return value
    .trim()
    .toLowerCase()
    .replace(/_/g, '-')
    .replace(/[^a-z0-9-]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-+|-+$/g, '')
}

function formatZoneTime(date: Date, timeZone: string) {
  return new Intl.DateTimeFormat('en-US', {
    timeZone,
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date)
}

function formatTraceTime(isoTime?: string) {
  if (!isoTime) return 'Not recorded yet'
  const date = new Date(isoTime)
  if (Number.isNaN(date.getTime())) return 'Unknown'
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function formatBeijingDateTime(isoTime?: string) {
  if (!isoTime) return 'unknown Beijing time'
  const date = new Date(isoTime)
  if (Number.isNaN(date.getTime())) return 'unknown Beijing time'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

function partitionColor(index: number) {
  const colors = ['#fcd535', '#0ecb81', '#60a5fa', '#f97316', '#c084fc', '#f43f5e', '#94a3b8']
  return colors[index % colors.length]
}

function pieGradient(partitions: StoragePartition[], totalBytes: number) {
  if (!totalBytes || !partitions.length) return 'conic-gradient(rgba(252, 213, 53, 0.22) 0 360deg)'
  let cursor = 0
  const stops = partitions.map((partition, index) => {
    const start = cursor
    const size = Math.max(0, (partition.bytes / totalBytes) * 360)
    cursor += size
    return `${partitionColor(index)} ${start.toFixed(2)}deg ${cursor.toFixed(2)}deg`
  })
  return `conic-gradient(${stops.join(', ')})`
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(value >= 10 ? 1 : 2)} ${units[unitIndex]}`
}

function buildLineDiffSummary(before: string, after: string, maxRows = 80): LineDiffSummary {
  const beforeLines = before.trim().split('\n')
  const afterLines = after.trim().split('\n')
  const table = Array.from({ length: beforeLines.length + 1 }, () =>
    Array(afterLines.length + 1).fill(0) as number[],
  )

  for (let beforeIndex = beforeLines.length - 1; beforeIndex >= 0; beforeIndex -= 1) {
    for (let afterIndex = afterLines.length - 1; afterIndex >= 0; afterIndex -= 1) {
      table[beforeIndex][afterIndex] =
        beforeLines[beforeIndex] === afterLines[afterIndex]
          ? table[beforeIndex + 1][afterIndex + 1] + 1
          : Math.max(table[beforeIndex + 1][afterIndex], table[beforeIndex][afterIndex + 1])
    }
  }

  const rows: LineDiffRow[] = []
  let added = 0
  let removed = 0
  let beforeIndex = 0
  let afterIndex = 0

  while (beforeIndex < beforeLines.length || afterIndex < afterLines.length) {
    if (beforeLines[beforeIndex] === afterLines[afterIndex]) {
      if (rows.length < maxRows) rows.push({ kind: 'same', text: beforeLines[beforeIndex] ?? '' })
      beforeIndex += 1
      afterIndex += 1
    } else if (
      afterIndex < afterLines.length &&
      (beforeIndex >= beforeLines.length ||
        table[beforeIndex][afterIndex + 1] >= table[beforeIndex + 1][afterIndex])
    ) {
      added += 1
      if (rows.length < maxRows) rows.push({ kind: 'added', text: afterLines[afterIndex] })
      afterIndex += 1
    } else {
      removed += 1
      if (rows.length < maxRows) rows.push({ kind: 'removed', text: beforeLines[beforeIndex] })
      beforeIndex += 1
    }
  }

  return { added, removed, rows }
}

function MarkdownToc({ body }: { body: string }) {
  const items = buildTableOfContents(body)

  if (!items.length) {
    return null
  }

  return (
    <aside className="focus-toc" aria-label="Markdown table of contents">
      <p className="eyebrow">On this page</p>
      <nav>
        {items.map((item) => (
          <a className={`toc-level-${item.level}`} href={`#${item.id}`} key={item.id}>
            {item.text}
          </a>
        ))}
      </nav>
    </aside>
  )
}

function AiRevisionCard({
  revision,
  diff,
}: {
  revision: AiRevision
  diff: ReturnType<typeof buildLineDiffSummary> | null
}) {
  const patchOps = revision.patch_ops ?? []
  return (
    <section className="ai-revision-card" aria-label="AI revision preview">
      <p className="eyebrow">
        AI draft / {revision.provider} / {revision.model}
      </p>
      <h3>{revision.summary || 'Revision ready for review'}</h3>
      {patchOps.length > 0 && (
        <details className="patch-preview" open>
          <summary>Patch ops: {patchOps.length}</summary>
          <ol>
            {patchOps.map((op, index) => (
              <li key={`${op.op}-${op.section}-${index}`}>
                <strong>{op.op}</strong> / {op.section || 'unspecified section'}
              </li>
            ))}
          </ol>
        </details>
      )}
      {diff && (
        <div className="diff-summary" aria-label="AI draft line diff">
          <span className="diff-added">+{diff.added}</span>
          <span className="diff-removed">-{diff.removed}</span>
          <details>
            <summary>Preview line changes</summary>
            <div className="diff-preview">
              {diff.rows.map((row, index) => (
                <pre key={`${row.kind}-${index}`} className={`diff-line ${row.kind}`}>
                  {row.kind === 'added' ? '+ ' : row.kind === 'removed' ? '- ' : '  '}
                  {row.text}
                </pre>
              ))}
            </div>
          </details>
        </div>
      )}
      {revision.rationale.length > 0 && (
        <ul>
          {revision.rationale.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
      {revision.changed_sections.length > 0 && (
        <p>Changed: {revision.changed_sections.join(', ')}</p>
      )}
      {revision.resolved_question_ids.length > 0 && (
        <p>Will resolve Q #{revision.resolved_question_ids.join(', #')} after save.</p>
      )}
      {revision.suggested_new_nodes.length > 0 && (
        <p>Suggested new nodes: {revision.suggested_new_nodes.join(', ')}</p>
      )}
    </section>
  )
}

function DraftConflictCard({
  message,
  onQueue,
  onFreshDraft,
  isBusy,
  isFreshDraftEnabled,
}: {
  message: string
  onQueue: () => void
  onFreshDraft: () => void
  isBusy: boolean
  isFreshDraftEnabled: boolean
}) {
  return (
    <section className="draft-conflict-card" aria-label="AI draft conflict">
      <p className="eyebrow">Draft conflict</p>
      <h3>Target Markdown changed</h3>
      <p>{message}</p>
      <div className="editor-actions">
        <button type="button" className="focus-toggle" onClick={onQueue}>
          Return to Q Queue
        </button>
        <button type="button" className="focus-toggle ai-action" disabled={isBusy || !isFreshDraftEnabled} onClick={onFreshDraft}>
          {!isFreshDraftEnabled ? 'AI disabled' : isBusy ? 'Creating...' : 'Create fresh draft'}
        </button>
      </div>
    </section>
  )
}


function clearLocationHash() {
  if (window.location.hash) {
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`)
  }
}

function clearStaleHeadingHash() {
  const hash = window.location.hash
  if (!hash.startsWith('#section-')) return

  const target = document.getElementById(decodeURIComponent(hash.slice(1)))
  if (!target) {
    clearLocationHash()
  }
}

function scrollDetailToTop() {
  document.querySelector('.detail-panel')?.scrollTo({ top: 0, behavior: 'auto' })
  window.scrollTo({ top: 0, behavior: 'auto' })
}

function scrollToLocationHash() {
  const hash = window.location.hash
  if (!hash.startsWith('#section-')) return false

  const target = document.getElementById(decodeURIComponent(hash.slice(1)))
  if (!target) return false

  target.scrollIntoView({ behavior: 'auto', block: 'start' })
  return true
}

function markdownHeadingElements() {
  return Array.from(
    document.querySelectorAll<HTMLElement>(
      '.detail-main .markdown-body h1[id], .detail-main .markdown-body h2[id], .detail-main .markdown-body h3[id]',
    ),
  )
}

function activeReadingAnchor(mode: ReadingReturnAnchor['mode']): ReadingReturnAnchor | null {
  const headings = markdownHeadingElements()
  if (!headings.length) return null

  const viewportMarker = Math.max(96, window.innerHeight * 0.22)
  let activeHeadingIndex = 0
  for (let index = 0; index < headings.length; index += 1) {
    const heading = headings[index]
    if (heading.getBoundingClientRect().top <= viewportMarker) {
      activeHeadingIndex = index
    } else {
      break
    }
  }
  return {
    headingId: headings[activeHeadingIndex].id,
    headingIndex: activeHeadingIndex,
    mode,
  }
}

function sectionEndForHeading(heading: HTMLElement): HTMLElement {
  let cursor = heading.nextElementSibling as HTMLElement | null
  let lastContent = heading
  while (cursor && !/^H[1-3]$/.test(cursor.tagName)) {
    lastContent = cursor
    cursor = cursor.nextElementSibling as HTMLElement | null
  }
  return lastContent
}

function restoreReadingAnchor(anchor: ReadingReturnAnchor) {
  const headings = markdownHeadingElements()
  const fallbackIndex = Math.max(0, Math.min(anchor.headingIndex - 1, headings.length - 1))
  const heading = document.getElementById(anchor.headingId) ?? headings[fallbackIndex]
  if (!heading) return false

  const target = anchor.mode === 'section-end'
    ? sectionEndForHeading(heading as HTMLElement)
    : heading
  target.scrollIntoView({ behavior: 'auto', block: anchor.mode === 'section-end' ? 'end' : 'start' })
  return true
}

function App() {
  const location = useLocation()
  const navigate = useNavigate()
  const routeState = routeFromLocation(location.pathname, location.search)
  const viewMode = routeState.viewMode
  const selectedSlug = routeState.selectedSlug
  const selectedQuizId = routeState.selectedQuizId
  const activeArea = routeState.activeArea
  const activeTrack = routeState.activeTrack
  const query = routeState.query
  const nodeSort = routeState.nodeSort
  const quizSort = routeState.quizSort
  const availableNodeSortOptions = visibleNodeSortOptions(query)
  const availableQuizSortOptions = visibleQuizSortOptions(query)
  const graphPage = routeState.graphPage
  const isFocusMode = routeState.isFocusMode
  const isWidgetMode = routeState.isWidgetMode
  const [nodes, setNodes] = useState<NodeSummary[]>([])
  const [areas, setAreas] = useState<AreaSummary[]>([])
  const [areaSystemCounts, setAreaSystemCounts] = useState<Record<string, number>>({})
  const [quizzes, setQuizzes] = useState<QuizSummary[]>([])
  const [tracks, setTracks] = useState<TrackSummary[]>([])
  const [selectedNode, setSelectedNode] = useState<NodeDetail | null>(null)
  const [selectedQuiz, setSelectedQuiz] = useState<QuizDetail | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [readerQuestions, setReaderQuestions] = useState<ReaderQuestion[]>([])
  const [aiJobs, setAiJobs] = useState<AiJob[]>([])
  const [questionDraft, setQuestionDraft] = useState('')
  const [isQuestionSaving, setIsQuestionSaving] = useState(false)
  const [isEditMode, setIsEditMode] = useState(false)
  const [editDraft, setEditDraft] = useState('')
  const [editModeError, setEditModeError] = useState('')
  const [sectionEditDraft, setSectionEditDraft] = useState<SectionEditDraft | null>(null)
  const [sectionEditError, setSectionEditError] = useState('')
  const [actionNotice, setActionNotice] = useState('')
  const [undoTrashSlug, setUndoTrashSlug] = useState('')
  const [questionFeedback, setQuestionFeedback] = useState('')
  const [isNewNodeOpen, setIsNewNodeOpen] = useState(false)
  const [newNodeDraft, setNewNodeDraft] = useState<NodeCreateDraft>({
    title: '',
    area: 'questions',
    track: 'general',
    summary: '',
    tags: '',
  })
  const [newNodeFeedback, setNewNodeFeedback] = useState('')
  const [isNodeCreating, setIsNodeCreating] = useState(false)
  const [isEditSaving, setIsEditSaving] = useState(false)
  const [isAiRevising, setIsAiRevising] = useState(false)
  const [aiRevision, setAiRevision] = useState<AiRevision | null>(null)
  const [aiStatus, setAiStatus] = useState('')
  const [clockNow, setClockNow] = useState(() => new Date())
  const [draftConflict, setDraftConflict] = useState('')
  const [activeAiJob, setActiveAiJob] = useState<AiJob | null>(null)
  const [selectedQuestionIds, setSelectedQuestionIds] = useState<number[]>([])
  const [questionScopes, setQuestionScopes] = useState<Record<number, AiDraftScope>>({})
  const [jobEvents, setJobEvents] = useState<Record<number, AiJobEvent[]>>({})
  const [systemMetrics, setSystemMetrics] = useState<SystemMetrics | null>(null)
  const [systemRepair, setSystemRepair] = useState<ApiSystemRepairResponse | null>(null)
  const [systemSchema, setSystemSchema] = useState<ApiSystemSchemaResponse | null>(null)
  const [packageExport, setPackageExport] = useState<ApiPackageExportResponse | null>(null)
  const [llmWikiExport, setLlmWikiExport] = useState<ApiLlmWikiExportResponse | null>(null)
  const [aiPreflight, setAiPreflight] = useState<ApiAiPreflightResponse | null>(null)
  const [reviewQueue, setReviewQueue] = useState<ReviewQueueItem[]>([])
  const [selectedReviewQuiz, setSelectedReviewQuiz] = useState<QuizDetail | null>(null)
  const [selectedReviewId, setSelectedReviewId] = useState('')
  const [reviewError, setReviewError] = useState('')
  const [isReviewQuizLoading, setIsReviewQuizLoading] = useState(false)
  const [isReviewRating, setIsReviewRating] = useState(false)
  const [healthActionNotice, setHealthActionNotice] = useState('')
  const [graphPayload, setGraphPayload] = useState<GraphPayload | null>(null)
  const [graphCache, setGraphCache] = useState<Record<string, GraphPayload>>({})
  const [isAreaNavExpanded, setIsAreaNavExpanded] = useState(true)
  const [readTraceError, setReadTraceError] = useState('')
  const readingReturnAnchorRef = useRef<ReadingReturnAnchor | null>(null)

  const deferredQuery = useDeferredValue(query)

  useEffect(() => {
    const timer = window.setInterval(() => setClockNow(new Date()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (viewMode !== 'nodes' || !isFocusMode || !selectedNode?.slug || selectedNode.slug !== selectedSlug) return
    let isActive = true
    const markRead = async () => {
      try {
        setReadTraceError('')
        const data = await postJson<ApiNodeResponse>(`/api/nodes/${selectedNode.slug}/read`, {
          read_at: new Date().toISOString(),
          min_interval_seconds: READ_MARK_MIN_INTERVAL_SECONDS,
        })
        if (!isActive) return
        setSelectedNode((current) => (current?.slug === data.node.slug ? data.node : current))
        setNodes((current) => current.map((node) => (node.slug === data.node.slug ? data.node : node)))
      } catch (readError) {
        if (!isActive) return
        setReadTraceError(readError instanceof Error ? readError.message : 'Unable to update reading trace')
      }
    }
    markRead()
    return () => {
      isActive = false
    }
  }, [isFocusMode, selectedNode?.slug, selectedSlug, viewMode])

  const navigateToNode = (slug: string, options: { focus?: boolean; replace?: boolean } = {}) => {
    navigate(
      `/nodes/${encodeURIComponent(slug)}${routeSearch({
        activeArea,
        activeTrack,
        query,
        nodeSort,
        isFocusMode: options.focus ?? isFocusMode,
      })}`,
      { replace: options.replace },
    )
  }

  const navigateToQuiz = (quizId: string, options: { focus?: boolean; replace?: boolean } = {}) => {
    navigate(
      `/quizzes/${encodeURIComponent(quizId)}${routeSearch({
        activeArea,
        activeTrack,
        query,
        quizSort,
        isFocusMode: options.focus ?? isFocusMode,
      })}`,
      { replace: options.replace },
    )
  }

  const navigateToQueue = () => {
    navigate(`/queue${routeSearch({ query })}`)
  }

  const navigateToBite = () => {
    navigate('/bite')
  }

  const refreshAreas = async () => {
    const data = await fetchJson<ApiAreasResponse>('/api/areas')
    setAreas(data.areas)
    setAreaSystemCounts(data.system)
  }

  const openNewNodeForm = () => {
    setIsNewNodeOpen((current) => !current)
    setNewNodeFeedback('')
    const safeArea = activeArea !== 'all' && activeArea !== 'archive' && activeArea !== 'trash' ? activeArea : 'questions'
    setNewNodeDraft((current) => ({
      ...current,
      area: safeArea,
      track: activeTrack !== 'all' ? activeTrack : current.track,
    }))
  }

  const navigateToArea = (area: string) => {
    const targetSlug = selectedSlug || nodes[0]?.slug
    if (targetSlug) {
      navigate(`/nodes/${encodeURIComponent(targetSlug)}${routeSearch({ activeArea: area, nodeSort })}`)
    } else {
      navigate(`/nodes${routeSearch({ activeArea: area, nodeSort })}`)
    }
  }

  const toggleFocusRoute = () => {
    const nextFocus = !isFocusMode
    if (viewMode === 'quizzes' && selectedQuizId) {
      navigateToQuiz(selectedQuizId, { focus: nextFocus, replace: true })
    } else if (viewMode === 'nodes' && selectedSlug) {
      navigateToNode(selectedSlug, { focus: nextFocus, replace: true })
    }
  }

  useEffect(() => {
    let isActive = true

    async function loadIndex() {
      try {
        setIsLoading(true)
        setError('')
        if (viewMode === 'quizzes') {
          const quizSortParam = encodeURIComponent(quizSort)
          const data = deferredQuery.trim()
            ? await fetchJson<ApiQuizzesResponse>(
                `/api/quiz-search?q=${encodeURIComponent(deferredQuery.trim())}&sort=${quizSortParam}`,
              )
            : await fetchJson<ApiQuizzesResponse>(`/api/quizzes?sort=${quizSortParam}`)

          if (!isActive) return

          startTransition(() => {
            setQuizzes(data.quizzes)
            if (location.pathname === '/quizzes' && data.quizzes[0]?.id) {
              navigate(
                `/quizzes/${encodeURIComponent(data.quizzes[0].id)}${routeSearch({
                  activeArea,
                  activeTrack,
                  query,
                  quizSort,
                  isFocusMode,
                })}`,
                { replace: true },
              )
            }
          })
        } else if (viewMode === 'question-queue') {
          const [questionsData, jobsData] = await Promise.all([
            fetchJson<ApiReaderQuestionsResponse>('/api/reader-questions?status=active'),
            fetchJson<ApiAiJobsResponse>('/api/ai/jobs?status=active'),
          ])

          if (!isActive) return

          startTransition(() => {
            setReaderQuestions(questionsData.questions)
            setAiJobs(jobsData.jobs)
          })
        } else if (viewMode === 'review') {
          const data = await fetchJson<ApiDueReviewsResponse>('/api/review/due?limit=50')

          if (!isActive) return

          startTransition(() => {
            setReviewQueue(data.reviews)
            setSelectedReviewId((current) => current || data.reviews[0]?.target_id || '')
            setReviewError('')
          })
        } else if (viewMode === 'bite') {
          setError('')
        } else if (viewMode === 'graph') {
          const cacheKey = `${location.pathname}?page=${graphPage}`
          const cached = graphCache[cacheKey]
          setGraphPayload(cached ?? null)
          if (cached) {
            return
          }
          const data = await fetchJson<ApiGraphResponse>(graphApiPath(location.pathname, graphPage))

          if (!isActive) return

          startTransition(() => {
            setGraphPayload(data)
            setGraphCache((current) => ({ ...current, [cacheKey]: data }))
          })
        } else if (viewMode === 'health') {
          const [metricsData, repairData, schemaData, manifestData, preflightData] = await Promise.all([
            fetchJson<ApiSystemMetricsResponse>('/api/system/metrics'),
            fetchJson<ApiSystemRepairResponse>('/api/system/repair'),
            fetchJson<ApiSystemSchemaResponse>('/api/system/schema'),
            fetchJson<ApiPackageExportResponse>('/api/package/export'),
            fetchJson<ApiAiPreflightResponse>('/api/ai/preflight'),
          ])

          if (!isActive) return

          startTransition(() => {
            setSystemMetrics(metricsData)
            setSystemRepair(repairData)
            setSystemSchema(schemaData)
            setPackageExport(manifestData)
            setAiPreflight(preflightData)
          })
        } else {
          const data = deferredQuery.trim()
            ? await fetchJson<ApiNodesResponse>(
                `/api/search?q=${encodeURIComponent(deferredQuery.trim())}&sort=${encodeURIComponent(nodeSort)}`,
              )
            : await fetchJson<ApiNodesResponse>(`/api/nodes?sort=${encodeURIComponent(nodeSort)}`)

          if (!isActive) return

          startTransition(() => {
            setNodes(data.nodes)
            const topSlug = data.nodes[0]?.slug
            const shouldOpenTopNode =
              topSlug &&
              (
                location.pathname === '/' ||
                location.pathname === '/nodes'
              )
            if (shouldOpenTopNode) {
              navigate(
                `/nodes/${encodeURIComponent(topSlug)}${routeSearch({
                  activeArea,
                  activeTrack,
                  query,
                  nodeSort,
                  isFocusMode,
                })}`,
                { replace: true },
              )
            }
          })
        }
      } catch (loadError) {
        if (!isActive) return
        setError(loadError instanceof Error ? loadError.message : 'Unable to load index')
      } finally {
        if (isActive) setIsLoading(false)
      }
    }

    loadIndex()

    return () => {
      isActive = false
    }
  }, [activeArea, activeTrack, deferredQuery, graphCache, graphPage, isFocusMode, location.pathname, navigate, nodeSort, query, quizSort, selectedSlug, viewMode])

  useEffect(() => {
    if (viewMode !== 'health' || !systemMetrics?.cache?.refreshing) return
    let isActive = true
    const timer = window.setInterval(async () => {
      try {
        const metricsData = await fetchJson<ApiSystemMetricsResponse>('/api/system/metrics')
        if (!isActive) return
        startTransition(() => {
          setSystemMetrics(metricsData)
        })
        if (!metricsData.cache?.refreshing) {
          window.clearInterval(timer)
        }
      } catch (loadError) {
        if (isActive) {
          setError(loadError instanceof Error ? loadError.message : 'Unable to refresh health metrics')
        }
      }
    }, 4000)

    return () => {
      isActive = false
      window.clearInterval(timer)
    }
  }, [systemMetrics?.cache?.refreshing, viewMode])

  useEffect(() => {
    let isActive = true

    async function loadAreas() {
      try {
        const data = await fetchJson<ApiAreasResponse>('/api/areas')
        if (!isActive) return
        setAreas(data.areas)
        setAreaSystemCounts(data.system)
      } catch (loadError) {
        if (isActive) {
          setError(loadError instanceof Error ? loadError.message : 'Unable to load areas')
        }
      }
    }

    loadAreas()

    return () => {
      isActive = false
    }
  }, [])

  useEffect(() => {
    if (viewMode !== 'nodes' || SYSTEM_AREAS.includes(activeArea)) {
      return
    }

    let isActive = true

    async function loadTracks() {
      try {
        const data = await fetchJson<ApiTracksResponse>(`/api/areas/${activeArea}/tracks`)
        if (!isActive) return
        setTracks(data.tracks)
        if (activeTrack !== 'all' && !data.tracks.some((track) => track.track === activeTrack)) {
          const fallbackSlug = selectedSlug || nodes[0]?.slug
          if (fallbackSlug) {
            navigate(
              `/nodes/${encodeURIComponent(fallbackSlug)}${routeSearch({
                activeArea,
                query,
                nodeSort,
                isFocusMode,
              })}`,
              { replace: true },
            )
          }
        }
      } catch (loadError) {
        if (isActive) {
          setError(loadError instanceof Error ? loadError.message : 'Unable to load tracks')
        }
      }
    }

    loadTracks()

    return () => {
      isActive = false
    }
  }, [activeArea, activeTrack, isFocusMode, navigate, nodeSort, nodes, query, selectedSlug, viewMode])

  useEffect(() => {
    if (!selectedSlug) {
      return
    }

    let isActive = true

    async function loadDetail() {
      try {
        setIsDetailLoading(true)
        setSelectedNode(null)
        const data = await fetchJson<ApiNodeResponse>(`/api/nodes/${selectedSlug}`)
        if (isActive) setSelectedNode(data.node)
      } catch (loadError) {
        if (isActive) {
          setError(loadError instanceof Error ? loadError.message : 'Unable to load detail')
        }
      } finally {
        if (isActive) setIsDetailLoading(false)
      }
    }

    loadDetail()

    return () => {
      isActive = false
    }
  }, [selectedSlug])

  useEffect(() => {
    if (!selectedQuizId) {
      return
    }

    let isActive = true

    async function loadQuizDetail() {
      try {
        setIsDetailLoading(true)
        setSelectedQuiz(null)
        const data = await fetchJson<ApiQuizResponse>(`/api/quizzes/${selectedQuizId}`)
        if (isActive) setSelectedQuiz(data.quiz)
      } catch (loadError) {
        if (isActive) {
          setError(loadError instanceof Error ? loadError.message : 'Unable to load quiz detail')
        }
      } finally {
        if (isActive) setIsDetailLoading(false)
      }
    }

    loadQuizDetail()

    return () => {
      isActive = false
    }
  }, [selectedQuizId])

  useEffect(() => {
    if (viewMode !== 'review' || !selectedReviewId) {
      return
    }

    let isActive = true

    async function loadReviewQuiz() {
      try {
        setIsReviewQuizLoading(true)
        const data = await fetchJson<ApiQuizResponse>(`/api/quizzes/${selectedReviewId}`)
        if (isActive) setSelectedReviewQuiz(data.quiz)
      } catch (loadError) {
        if (isActive) {
          setReviewError(loadError instanceof Error ? loadError.message : 'Unable to load review quiz')
        }
      } finally {
        if (isActive) setIsReviewQuizLoading(false)
      }
    }

    loadReviewQuiz()

    return () => {
      isActive = false
    }
  }, [selectedReviewId, viewMode])

  useEffect(() => {
    if (viewMode === 'question-queue') return

    const targetType = viewMode === 'quizzes' ? 'quiz' : 'node'
    const targetId = viewMode === 'quizzes' ? selectedQuizId : selectedSlug
    if (!targetId) {
      return
    }

    let isActive = true

    async function loadReaderQuestions() {
      try {
        const data = await fetchJson<ApiReaderQuestionsResponse>(
          `/api/reader-questions?target_type=${targetType}&target_id=${encodeURIComponent(
            targetId,
          )}&status=open`,
        )
        if (isActive) setReaderQuestions(data.questions)
      } catch (loadError) {
        if (isActive) {
          setError(
            loadError instanceof Error ? loadError.message : 'Unable to load reader questions',
          )
        }
      }
    }

    loadReaderQuestions()

    return () => {
      isActive = false
    }
  }, [selectedQuizId, selectedSlug, viewMode])

  useEffect(() => {
    if (viewMode === 'question-queue') return

    const targetType = viewMode === 'quizzes' ? 'quiz' : 'node'
    const targetId = viewMode === 'quizzes' ? selectedQuizId : selectedSlug
    if (!targetId) return

    let isActive = true

    async function loadActiveJob() {
      try {
        const data = await fetchJson<ApiAiJobsResponse>(
          `/api/ai/jobs?target_type=${targetType}&target_id=${encodeURIComponent(targetId)}`,
        )
        if (!isActive) return
        const job = data.jobs.find((item) =>
          ['queued', 'solving', 'draft_ready', 'failed'].includes(item.status),
        )
        setActiveAiJob(job ?? null)
        if (job?.status === 'draft_ready' && job.revision) {
          setAiStatus(`Job #${job.id}: draft_ready. Open Q Queue to review/apply.`)
        } else if (job?.status === 'failed') {
          setAiStatus(job.error_summary || job.error || 'AI job failed')
        } else if (job) {
          setAiStatus(`Job #${job.id}: ${job.stage}`)
        } else {
          setAiStatus('')
          setAiRevision(null)
        }
      } catch (loadError) {
        if (isActive) {
          setError(loadError instanceof Error ? loadError.message : 'Unable to load AI jobs')
        }
      }
    }

    loadActiveJob()

    return () => {
      isActive = false
    }
  }, [selectedQuizId, selectedSlug, viewMode])

  useEffect(() => {
    const hasLoadedBody =
      viewMode === 'nodes'
        ? selectedNode?.slug === selectedSlug && Boolean(selectedNode.body)
        : viewMode === 'quizzes'
          ? selectedQuiz?.id === selectedQuizId && Boolean(selectedQuiz.body)
          : false
    if (!hasLoadedBody) return

    clearStaleHeadingHash()
    window.addEventListener('hashchange', clearStaleHeadingHash)
    return () => window.removeEventListener('hashchange', clearStaleHeadingHash)
  }, [
    selectedNode?.body,
    selectedNode?.slug,
    selectedQuiz?.body,
    selectedQuiz?.id,
    selectedQuizId,
    selectedSlug,
    viewMode,
  ])

  useEffect(() => {
    const hasLoadedBody =
      viewMode === 'nodes'
        ? selectedNode?.slug === selectedSlug && Boolean(selectedNode.body)
        : viewMode === 'quizzes'
          ? selectedQuiz?.id === selectedQuizId && Boolean(selectedQuiz.body)
          : false
    if (!hasLoadedBody) return

    requestAnimationFrame(() => {
      const returnAnchor = readingReturnAnchorRef.current
      if (returnAnchor) {
        if (restoreReadingAnchor(returnAnchor)) {
          readingReturnAnchorRef.current = null
          return
        }
        if (isEditMode || sectionEditDraft) return
      }
      if (!scrollToLocationHash()) {
        scrollDetailToTop()
      }
    })
  }, [
    isEditMode,
    location.hash,
    location.pathname,
    location.search,
    sectionEditDraft,
    selectedNode?.body,
    selectedNode?.slug,
    selectedQuiz?.body,
    selectedQuiz?.id,
    selectedQuizId,
    selectedSlug,
    viewMode,
  ])

  useEffect(() => {
    const activeJobId = activeAiJob?.id
    const activeJobStatus = activeAiJob?.status
    if (!activeJobId || !activeJobStatus || !['queued', 'solving'].includes(activeJobStatus)) return

    let isActive = true
    const poll = async () => {
      try {
        const data = await fetchJson<ApiAiJobResponse>(`/api/ai/jobs/${activeJobId}`)
        if (!isActive) return
        setActiveAiJob(data.job)
        setAiJobs((current) => [data.job, ...current.filter((job) => job.id !== data.job.id)])
        if (data.job.status === 'draft_ready' && data.job.revision) {
          setAiStatus(`Job #${data.job.id}: draft_ready. Open Q Queue to review/apply.`)
        } else if (data.job.status === 'failed') {
          setAiStatus(data.job.error_summary || data.job.error || 'AI job failed')
        } else {
          setAiStatus(`Job #${data.job.id}: ${data.job.stage}`)
        }
      } catch (pollError) {
        if (isActive) {
          setAiStatus(pollError instanceof Error ? pollError.message : 'Unable to poll AI job')
        }
      }
    }

    poll()
    const timer = window.setInterval(poll, 2500)

    return () => {
      isActive = false
      window.clearInterval(timer)
    }
  }, [activeAiJob?.id, activeAiJob?.status])

  const filteredNodes = nodes.filter((node) => {
    if (activeArea === 'trash') return node.visibility === 'trash'
    if (activeArea === 'archive') return node.visibility === 'archive'
    if (activeArea === 'all') return !['archive', 'trash'].includes(node.visibility)
    return (
      node.area === activeArea &&
      !['archive', 'trash'].includes(node.visibility) &&
      (activeTrack === 'all' || node.track === activeTrack)
    )
  })

  const filteredQuizzes = quizzes.filter((quiz) => {
    if (activeArea === 'archive') return quiz.visibility === 'archive'
    if (activeArea === 'all') return quiz.visibility !== 'archive'
    return quiz.area === activeArea && quiz.visibility !== 'archive'
  })

  const reviewItems = reviewQueue.map((review) => ({
    id: `${review.target_type}-${review.target_id}`,
    quizId: review.target_id,
    title: review.title,
    summary: review.summary,
    area: review.area,
    difficulty: review.difficulty,
    dueAt: review.due_at ? formatBeijingDateTime(review.due_at) : 'new',
    intervalLabel: review.reps ? `${review.reps} reps / ${review.interval_days.toFixed(1)}d` : 'new card',
  }))

  const selectedReviewItem = reviewQueue.find((review) => review.target_id === selectedReviewId) ?? reviewQueue[0] ?? null
  const selectedDailyReviewQuiz = selectedReviewQuiz
    ? {
        id: selectedReviewQuiz.id,
        title: selectedReviewQuiz.title,
        summary: selectedReviewQuiz.summary,
        area: selectedReviewQuiz.area,
        difficulty: selectedReviewQuiz.difficulty,
        body: selectedReviewQuiz.body,
        answer: 'Review the revealed Markdown answer and explanation, then grade this card.',
        tags: selectedReviewQuiz.tags,
        openQuestionCount: selectedReviewQuiz.open_question_count,
      }
    : null
  const repairIssues = systemRepair?.issues ?? []
  const healthIssues = repairIssues.map((issue, index) => ({
    id: `${issue.kind}-${issue.target_id ?? issue.path ?? index}`,
    title: issue.kind,
    summary: issue.path || issue.target_slug || issue.link_kind || 'Inspect this local data consistency warning.',
    severity: issue.severity,
    target: [issue.target_type, issue.target_id].filter(Boolean).join(':') || issue.source_slug,
    actionLabel: issue.severity === 'error' ? 'Inspect' : 'Review',
  }))
  const schemaMetadata = Object.entries(systemSchema?.schema ?? {}).map(([key, meta]) => ({
    id: key,
    label: key,
    value: meta.value,
    note: meta.updated_at ? `updated ${formatBeijingDateTime(meta.updated_at)}` : '',
  }))
  const packageManifestEntries = packageExport
    ? [
        {
          id: 'package-format',
          label: 'package format',
          value: packageExport.manifest.package_format_version,
          note: `${packageExport.manifest.counts.files} files`,
        },
        { id: 'nodes', label: 'nodes', value: String(packageExport.manifest.counts.nodes) },
        { id: 'quizzes', label: 'quizzes', value: String(packageExport.manifest.counts.quizzes) },
        {
          id: 'markdown',
          label: 'Markdown files',
          value: String(packageExport.manifest.files.filter((file) => file.path.endsWith('.md')).length),
          note: packageExport.manifest.files.find((file) => file.path.endsWith('.md'))?.path ?? '',
        },
      ]
    : []
  const llmWikiPackSummary = llmWikiExport
    ? {
        formatVersion: llmWikiExport.pack.llmwiki_format_version,
        profile: llmWikiExport.pack.profile,
        itemCount: llmWikiExport.pack.counts.items,
        fileCount: llmWikiExport.pack.counts.files,
        markdownFileCount: llmWikiExport.pack.counts.markdown_files,
        nodeCount: llmWikiExport.pack.counts.nodes,
        quizCount: llmWikiExport.pack.counts.quizzes,
        assetCount: llmWikiExport.pack.counts.asset_references,
        outputPath: llmWikiExport.pack.written_to ?? llmWikiExport.pack.output.default_path,
        includesFullBody: llmWikiExport.pack.memory_policy.includes_full_body,
        usage: llmWikiExport.pack.usage.purpose,
        memoryPolicy: llmWikiExport.pack.memory_policy.loading,
        generatedAt: formatBeijingDateTime(llmWikiExport.pack.generated_at),
        writtenTo: llmWikiExport.pack.written_to,
        warnings: llmWikiExport.pack.report.warnings,
      }
    : undefined
  const aiPreflightChecks = aiPreflight
    ? [
        {
          id: 'provider',
          label: aiPreflight.provider,
          ok: aiPreflight.ok,
          message: aiPreflight.message,
        },
        ...Object.entries(aiPreflight.checks ?? {}).map(([key, ok]) => ({
          id: key,
          label: key,
          ok,
          message: ok ? 'configured' : 'not configured',
        })),
      ]
    : []
  const contentIndexSummary = systemMetrics
    ? {
        totalItems: systemMetrics.counts.nodes + systemMetrics.counts.quizzes,
        indexedItems: (packageExport?.manifest.counts.nodes ?? systemMetrics.counts.nodes)
          + (packageExport?.manifest.counts.quizzes ?? systemMetrics.counts.quizzes),
        staleItems: repairIssues.filter((issue) => issue.kind === 'content_hash_mismatch' || issue.kind.includes('stale')).length,
        lastIndexedAt: systemMetrics.collected_at ? formatBeijingDateTime(systemMetrics.collected_at) : '',
        entries: [
          { id: 'nodes', label: 'nodes', count: systemMetrics.counts.nodes },
          { id: 'quizzes', label: 'quizzes', count: systemMetrics.counts.quizzes },
          { id: 'due-reviews', label: 'due reviews', count: systemMetrics.counts.due_reviews },
          { id: 'open-questions', label: 'open questions', count: systemMetrics.counts.open_questions },
        ],
      }
    : undefined

  const totalStorageBytes = systemMetrics
    ? systemMetrics.storage.content_bytes + systemMetrics.storage.db_bytes + systemMetrics.storage.generated_bytes
    : 0
  const projectRelatedBytes = systemMetrics?.storage.project_related_bytes ?? totalStorageBytes
  const storagePartitions = [...(systemMetrics?.storage.exclusive_partitions ?? [])].sort(
    (left, right) => right.bytes - left.bytes,
  )
  const metricPartitions = [...(systemMetrics?.storage.partitions ?? [])].sort(
    (left, right) => right.bytes - left.bytes,
  )

  const activeAiJobs = aiJobs.filter((job) =>
    ['queued', 'solving', 'draft_ready', 'failed'].includes(job.status),
  )
  const contentAreas = Array.from(
    new Map(areas.map((area) => [area.area, area])).keys(),
  ).sort((left, right) => {
    const leftStableIndex = stableAreas.indexOf(left)
    const rightStableIndex = stableAreas.indexOf(right)
    if (leftStableIndex !== -1 || rightStableIndex !== -1) {
      if (leftStableIndex === -1) return 1
      if (rightStableIndex === -1) return -1
      return leftStableIndex - rightStableIndex
    }
    return left.localeCompare(right)
  })
  const systemSidebarAreas = ['archive', 'trash']
  const areaCounts = new Map<string, number>()
  areaCounts.set('all', areaSystemCounts.all ?? 0)
  areaCounts.set('archive', areaSystemCounts.archive ?? 0)
  areaCounts.set('trash', areaSystemCounts.trash ?? 0)
  for (const area of areas) {
    areaCounts.set(area.area, area.node_count)
  }
  const jobsByQuestionId = new Map<number, AiJob>()
  for (const job of activeAiJobs) {
    for (const questionId of job.question_ids) {
      const existing = jobsByQuestionId.get(questionId)
      if (!existing || job.updated_at.localeCompare(existing.updated_at) > 0) {
        jobsByQuestionId.set(questionId, job)
      }
    }
  }
  const orphanJobs = activeAiJobs.filter((job) => job.question_ids.length === 0)
  const totalQueueItems = readerQuestions.length + orphanJobs.length
  const queueSearch = query.trim().toLowerCase()
  const queueItems = [
    ...readerQuestions.map((question) => ({
      kind: 'question' as const,
      id: `question-${question.id}`,
      sort: question.created_at,
      question,
      job: jobsByQuestionId.get(question.id) ?? null,
    })),
    ...orphanJobs.map((job) => ({
      kind: 'job' as const,
      id: `job-${job.id}`,
      sort: job.updated_at || job.created_at,
      job,
    })),
  ]
    .filter((item) => {
      if (!queueSearch) return true
      const text =
        item.kind === 'question'
          ? `${item.question.target_type} ${item.question.target_id} ${item.question.status} ${item.question.question}`
          : `${item.job.target_type} ${item.job.target_id} ${item.job.status} ${item.job.stage} ${item.job.instruction} ${item.job.error_summary}`
      return text.toLowerCase().includes(queueSearch)
    })
    .sort((left, right) => right.sort.localeCompare(left.sort))

  const visibleCount = (() => {
    if (viewMode === 'question-queue') return queueItems.length
    if (viewMode === 'graph') return graphPayload?.children.length ?? 0
    if (viewMode === 'health') return storagePartitions.length
    if (viewMode === 'review') return reviewQueue.length
    if (viewMode === 'bite') return 1
    if (viewMode === 'quizzes') return filteredQuizzes.length
    return filteredNodes.length
  })()
  const totalCount = (() => {
    if (viewMode === 'question-queue') return totalQueueItems
    if (viewMode === 'graph') return graphPayload?.pagination.total ?? 0
    if (viewMode === 'health') return storagePartitions.length
    if (viewMode === 'review') return reviewQueue.length
    if (viewMode === 'bite') return 1
    if (viewMode === 'quizzes') return quizzes.length
    return nodes.length
  })()
  const visibleTracks =
    viewMode === 'nodes' && !SYSTEM_AREAS.includes(activeArea) ? tracks : []
  const visibleReaderQuestions = readerQuestions
  const aiEnabled = aiPreflight?.enabled !== false
  const aiDisabledMessage = aiPreflight?.message || 'AI drafting is disabled for this beta. Questions are still saved locally.'
  const isAiJobRunning = Boolean(activeAiJob && ['queued', 'solving'].includes(activeAiJob.status))
  const activeAiJobStartedAt = activeAiJob ? new Date(activeAiJob.started_at || activeAiJob.created_at).getTime() : Number.NaN
  const aiElapsedSeconds = isAiJobRunning && !Number.isNaN(activeAiJobStartedAt)
    ? Math.max(0, Math.floor((clockNow.getTime() - activeAiJobStartedAt) / 1000))
    : 0
  const aiStatusText = isAiJobRunning
    ? `${aiStatus} ${aiElapsedSeconds}s elapsed. Job #${activeAiJob?.id} is persisted; no refresh needed. Open Q Queue when the draft is ready.`
    : aiStatus
  const aiStatusClass = isAiJobRunning ? 'ai-status running' : 'ai-status error'
  const readerQuestionHint = questionFeedback
    || (isQuestionSaving
      ? 'Saving your question...'
      : !questionDraft.trim()
        ? 'Write a question before saving or drafting.'
        : '')
  const aiDraftHint =
    !aiEnabled
      ? 'AI drafting is off for this beta. Save questions locally and review them later.'
      : isAiRevising
      ? 'Creating a draft job...'
      : isAiJobRunning
        ? 'An AI job is already running for this target. Check Q Queue for progress.'
        : !visibleReaderQuestions.length && !questionDraft.trim()
          ? 'Add or save a question first, then draft with AI.'
          : ''
  const editTargetType = viewMode === 'quizzes' ? 'quiz' : viewMode === 'nodes' ? 'node' : null
  const editTargetId = editTargetType === 'quiz' ? selectedQuizId : editTargetType === 'node' ? selectedSlug : null
  const editTargetBody = editTargetType === 'quiz' ? selectedQuiz?.body : editTargetType === 'node' ? selectedNode?.body : ''
  const editTargetBodyHash = editTargetType === 'quiz'
    ? selectedQuiz?.body_hash
    : editTargetType === 'node'
      ? selectedNode?.body_hash
      : ''

  useEffect(() => {
    if (!isEditMode || !editTargetType || !editTargetId || !editTargetBodyHash || activeAiJob?.status === 'draft_ready') return
    if (editDraft === editTargetBody) {
      clearStoredEditDraft(editTargetType, editTargetId)
      return
    }
    writeStoredEditDraft(editTargetType, editTargetId, editTargetBodyHash, editDraft)
  }, [activeAiJob?.status, editDraft, editTargetBody, editTargetBodyHash, editTargetId, editTargetType, isEditMode])

  const exitEditMode = (shouldConfirm = true) => {
    if (!isEditMode) return true
    if (shouldConfirm && editDraft.trim() && !window.confirm('Discard unsaved Markdown edits?')) {
      return false
    }
    if (shouldConfirm && editTargetType && editTargetId) {
      clearStoredEditDraft(editTargetType, editTargetId)
    }
    setIsEditMode(false)
    setEditDraft('')
    setEditModeError('')
    setAiRevision(null)
    setAiStatus('')
    setDraftConflict('')
    setSectionEditDraft(null)
    setSectionEditError('')
    return true
  }

  const exitSectionEdit = (shouldConfirm = true) => {
    if (!sectionEditDraft) return true
    if (
      shouldConfirm &&
      sectionEditDraft.draft !== sectionEditDraft.text &&
      !window.confirm('Discard unsaved section edits?')
    ) {
      setActionNotice('Navigation stayed here because section edits are unsaved.')
      setSectionEditError('Navigation stayed here because section edits are unsaved.')
      return false
    }
    setSectionEditDraft(null)
    setSectionEditError('')
    return true
  }

  const exitEditingBeforeNavigation = () => {
    if (!exitEditMode()) return false
    if (!exitSectionEdit()) return false
    setActionNotice('')
    return true
  }

  const openQuestionTarget = (item: ReaderQuestion) => {
    if (!exitEditingBeforeNavigation()) return
    if (item.target_type === 'node') {
      navigateToNode(item.target_id, { focus: true })
    } else {
      navigateToQuiz(item.target_id, { focus: true })
    }
  }

  const toggleQuestionSelection = (questionId: number) => {
    setSelectedQuestionIds((current) =>
      current.includes(questionId)
        ? current.filter((item) => item !== questionId)
        : [...current, questionId],
    )
  }

  const questionIdsForScope = (question: ReaderQuestion, scope: AiDraftScope) => {
    const sameTargetQuestions = readerQuestions.filter(
      (item) => item.target_type === question.target_type && item.target_id === question.target_id,
    )
    if (scope === 'page') {
      return sameTargetQuestions.map((item) => item.id)
    }
    if (scope === 'selected') {
      const selectedForTarget = sameTargetQuestions
        .map((item) => item.id)
        .filter((id) => selectedQuestionIds.includes(id))
      return selectedForTarget.length ? selectedForTarget : [question.id]
    }
    return [question.id]
  }

  const draftQuestionFromQueue = async (question: ReaderQuestion) => {
    if (!aiEnabled) {
      setAiStatus(aiDisabledMessage)
      setError('')
      return
    }
    const scope = questionScopes[question.id] ?? 'question'
    const targetType = question.target_type
    const targetId = question.target_id
    try {
      setIsAiRevising(true)
      setError('')
      setAiStatus(`Creating AI job for ${targetType} "${targetId}"...`)
      const data = await postJson<ApiAiJobResponse>('/api/ai/jobs', {
        target_type: targetType,
        target_id: targetId,
        question_ids: questionIdsForScope(question, scope),
        question: '',
        instruction: `Draft a focused tutorial improvement for scope: ${scope}. Keep it concise, concrete, and reviewable.`,
      })
      setAiJobs((current) => [data.job, ...current.filter((item) => item.id !== data.job.id)])
      setActiveAiJob(data.job)
      setSelectedQuestionIds((current) =>
        current.filter((id) => !data.job.question_ids.includes(id)),
      )
      setAiStatus(`Job #${data.job.id}: ${data.job.stage}`)
    } catch (draftError) {
      const message = draftError instanceof Error ? draftError.message : 'Unable to create AI job'
      setAiStatus(message)
      setError(message)
    } finally {
      setIsAiRevising(false)
    }
  }

  const openJobTarget = (job: AiJob) => {
    if (!exitEditingBeforeNavigation()) return
    if (job.target_type === 'node') {
      navigateToNode(job.target_id, { focus: true })
    } else {
      navigateToQuiz(job.target_id, { focus: true })
    }
  }

  const goBack = () => {
    if (!exitEditingBeforeNavigation()) return
    navigate(-1)
  }

  const navigateGraph = (href: string) => {
    if (!exitEditingBeforeNavigation()) return
    navigate(href)
  }

  const navigateGraphPage = (page: number) => {
    if (!exitEditingBeforeNavigation()) return
    navigate(`${location.pathname}${routeSearch({ page })}`)
  }

  const adjustOpenQuestionCount = (targetType: 'node' | 'quiz', targetId: string, delta: number) => {
    if (targetType === 'node') {
      setSelectedNode((current) =>
        current?.slug === targetId
          ? { ...current, open_question_count: Math.max(0, current.open_question_count + delta) }
          : current,
      )
    } else {
      setSelectedQuiz((current) =>
        current?.id === targetId
          ? { ...current, open_question_count: Math.max(0, current.open_question_count + delta) }
          : current,
      )
    }
  }

  const decrementOpenQuestionCount = (item: ReaderQuestion) => {
    if (item.status !== 'open') return
    adjustOpenQuestionCount(item.target_type, item.target_id, -1)
  }

  const dismissReaderQuestion = async (item: ReaderQuestion) => {
    try {
      await postJson<ApiReaderQuestionResponse>(`/api/reader-questions/${item.id}/dismiss`, {
        resolution_note: 'Dismissed from Q Queue',
      })
      setReaderQuestions((current) => current.filter((question) => question.id !== item.id))
      decrementOpenQuestionCount(item)
    } catch (dismissError) {
      setError(dismissError instanceof Error ? dismissError.message : 'Unable to dismiss question')
    }
  }

  const deleteReaderQuestion = async (item: ReaderQuestion) => {
    if (!window.confirm(`Delete Q #${item.id}?`)) return
    try {
      await deleteJson<{ ok: boolean }>(`/api/reader-questions/${item.id}`)
      setReaderQuestions((current) => current.filter((question) => question.id !== item.id))
      decrementOpenQuestionCount(item)
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : 'Unable to delete question')
    }
  }

  const reviewAiJob = (job: AiJob) => {
    if (!job.revision) return
    setActiveAiJob(job)
    setAiRevision(job.revision)
    setEditDraft(job.revision.revised_body)
    setDraftConflict('')
    setIsEditMode(true)
    if (job.target_type === 'node') {
      navigateToNode(job.target_id, { focus: true })
    } else {
      navigateToQuiz(job.target_id, { focus: true })
    }
  }

  const cancelAiJob = async (job: AiJob) => {
    try {
      const data = await postJson<ApiAiJobResponse>(`/api/ai/jobs/${job.id}/cancel`, {})
      setAiJobs((current) => current.map((item) => (item.id === job.id ? data.job : item)))
      if (activeAiJob?.id === job.id) setActiveAiJob(data.job)
    } catch (cancelError) {
      setError(cancelError instanceof Error ? cancelError.message : 'Unable to cancel AI job')
    }
  }

  const rejectAiJob = async (job: AiJob) => {
    try {
      const data = await postJson<ApiAiJobResponse>(`/api/ai/jobs/${job.id}/reject`, {
        reason: 'Rejected from Q Queue',
      })
      setAiJobs((current) => current.map((item) => (item.id === job.id ? data.job : item)))
      if (activeAiJob?.id === job.id) {
        setActiveAiJob(null)
        setAiRevision(null)
        setAiStatus('')
      }
    } catch (rejectError) {
      setError(rejectError instanceof Error ? rejectError.message : 'Unable to reject AI job')
    }
  }

  const loadJobEvents = async (job: AiJob) => {
    try {
      const data = await fetchJson<ApiAiJobEventsResponse>(`/api/ai/jobs/${job.id}/events`)
      setJobEvents((current) => ({ ...current, [job.id]: data.events }))
    } catch (eventError) {
      setError(eventError instanceof Error ? eventError.message : 'Unable to load AI job events')
    }
  }

  const retryAiJob = async (job: AiJob) => {
    if (!aiEnabled) {
      setAiStatus(aiDisabledMessage)
      setError('')
      return
    }
    try {
      setAiStatus(`Retrying job #${job.id}...`)
      const data = await postJson<ApiAiJobResponse>(`/api/ai/jobs/${job.id}/retry`, {})
      setAiJobs((current) => [data.job, ...current.filter((item) => item.id !== job.id)])
      setActiveAiJob(data.job)
    } catch (retryError) {
      setError(retryError instanceof Error ? retryError.message : 'Unable to retry AI job')
    }
  }

  const returnToQueueFromConflict = () => {
    setDraftConflict('')
    navigate('/queue')
  }

  const createFreshDraftFromConflict = async () => {
    if (!activeAiJob) return
    if (!aiEnabled) {
      setAiStatus(aiDisabledMessage)
      return
    }
    const questionIds = activeAiJob.question_ids
    setDraftConflict('')
    setAiRevision(null)
    setActiveAiJob(null)
    setEditDraft(viewMode === 'quizzes' ? selectedQuiz?.body ?? '' : selectedNode?.body ?? '')
    try {
      setIsAiRevising(true)
      setAiStatus(`Creating fresh AI job for ${activeAiJob.target_type} "${activeAiJob.target_id}"...`)
      const data = await postJson<ApiAiJobResponse>('/api/ai/jobs', {
        target_type: activeAiJob.target_type,
        target_id: activeAiJob.target_id,
        question_ids: questionIds,
        question: '',
        instruction: 'Regenerate this stale draft against the current Markdown. Keep the change concise and reviewable.',
      })
      setAiJobs((current) => [data.job, ...current.filter((item) => item.id !== data.job.id)])
      setActiveAiJob(data.job)
      setAiStatus(`Job #${data.job.id}: ${data.job.stage}`)
    } catch (freshError) {
      const message = freshError instanceof Error ? freshError.message : 'Unable to create fresh draft'
      setAiStatus(message)
      setError(message)
    } finally {
      setIsAiRevising(false)
    }
  }

  const submitReaderQuestion = async () => {
    const question = questionDraft.trim()
    const targetType = viewMode === 'quizzes' ? 'quiz' : 'node'
    const targetId = viewMode === 'quizzes' ? selectedQuizId : selectedSlug
    if (!question) {
      setQuestionFeedback('Write a question before saving.')
      return
    }
    if (!targetId) {
      setQuestionFeedback('No target is loaded yet; wait for the page to finish loading.')
      return
    }

    try {
      setIsQuestionSaving(true)
      setQuestionFeedback('Saving your question...')
      const data = await postJson<ApiReaderQuestionResponse>('/api/reader-questions', {
        target_type: targetType,
        target_id: targetId,
        question,
      })
      setReaderQuestions((current) => [data.question, ...current])
      setQuestionDraft('')
      setQuestionFeedback('Question saved into Q Queue.')
      adjustOpenQuestionCount(targetType, targetId, 1)
    } catch (saveError) {
      const message = saveError instanceof Error ? saveError.message : 'Unable to save reader question'
      setQuestionFeedback(message)
      setError(message)
    } finally {
      setIsQuestionSaving(false)
    }
  }

  const createNodeFromForm = async () => {
    const title = newNodeDraft.title.trim()
    if (!title) {
      setNewNodeFeedback('Title is required before creating a node.')
      return
    }

    try {
      setIsNodeCreating(true)
      setNewNodeFeedback('Creating node file...')
      const area = slugifyInput(newNodeDraft.area) || 'questions'
      const track = slugifyInput(newNodeDraft.track) || 'general'
      const data = await postJson<ApiNodeResponse>('/api/nodes', {
        title,
        area,
        track,
        summary: newNodeDraft.summary.trim(),
        tags: newNodeDraft.tags
          .split(',')
          .map((tag) => slugifyInput(tag))
          .filter(Boolean),
        visibility: 'support',
        status: 'draft',
        order: 1000,
      })
      setNodes((current) => [data.node, ...current.filter((node) => node.slug !== data.node.slug)])
      setSelectedNode(data.node)
      setEditDraft(data.node.body)
      setEditModeError('')
      setSectionEditDraft(null)
      setSectionEditError('')
      setIsEditMode(true)
      setIsNewNodeOpen(false)
      setNewNodeDraft({
        title: '',
        area: data.node.area,
        track: data.node.track,
        summary: '',
        tags: '',
      })
      await refreshAreas()
      setNewNodeFeedback('')
      navigate(
        `/nodes/${encodeURIComponent(data.node.slug)}${routeSearch({
          activeArea: data.node.area,
          activeTrack: data.node.track,
          query,
          isFocusMode: true,
        })}`,
      )
    } catch (createError) {
      const message = createError instanceof Error ? createError.message : 'Unable to create node'
      setNewNodeFeedback(message)
      setError(message)
    } finally {
      setIsNodeCreating(false)
    }
  }

  const requestAiRevision = async () => {
    if (!aiEnabled) {
      setQuestionFeedback(aiDisabledMessage)
      setAiStatus(aiDisabledMessage)
      return
    }
    const targetType = viewMode === 'quizzes' ? 'quiz' : 'node'
    const targetId = viewMode === 'quizzes' ? selectedQuizId : selectedSlug
    const currentBody = viewMode === 'quizzes' ? selectedQuiz?.body : selectedNode?.body
    if (!targetId || !currentBody) {
      setQuestionFeedback('No loaded target body yet; wait for the page to finish loading.')
      return
    }
    if (isAiRevising) {
      setQuestionFeedback('A draft request is already being created.')
      return
    }
    if (isAiJobRunning) {
      setQuestionFeedback('An AI job is already running for this target. Open Q Queue to track it.')
      return
    }

    const visibleQuestionIds = visibleReaderQuestions.map((item) => item.id)
    const selectedVisibleQuestionIds = selectedQuestionIds.filter((id) =>
      visibleQuestionIds.includes(id),
    )
    const questionIds = selectedVisibleQuestionIds.length
      ? selectedVisibleQuestionIds
      : visibleReaderQuestions[0]
        ? [visibleReaderQuestions[0].id]
        : []
    const newQuestion = questionDraft.trim()
    const instruction = 'Draft a focused tutorial improvement for the selected reader question. Keep it concise, concrete, and reviewable.'

    try {
      setIsAiRevising(true)
      setError('')
      setQuestionFeedback('')
      setAiRevision(null)
      setDraftConflict('')
      setAiStatus(`Creating AI job for ${targetType} "${targetId}"...`)
      const data = await postJson<ApiAiJobResponse>('/api/ai/jobs', {
        target_type: targetType,
        target_id: targetId,
        question_ids: questionIds,
        question: newQuestion,
        instruction,
        draft_body: isEditMode ? editDraft : currentBody,
      })
      setActiveAiJob(data.job)
      setQuestionDraft('')
      setAiJobs((current) => [data.job, ...current.filter((item) => item.id !== data.job.id)])
      setSelectedQuestionIds((current) =>
        current.filter((id) => !data.job.question_ids.includes(id)),
      )
      setAiStatus(`Job #${data.job.id}: ${data.job.stage}`)
    } catch (revisionError) {
      const message =
        revisionError instanceof Error ? revisionError.message : 'Unable to generate AI revision'
      setAiStatus(message)
      setError(message)
    } finally {
      setIsAiRevising(false)
    }
  }

  const startEditMode = () => {
    if (!editTargetType || !editTargetId || !editTargetBody || !editTargetBodyHash) {
      setActionNotice('Markdown body is still loading; try Edit mode again in a moment.')
      return
    }
    const currentScrollTop = window.scrollY
    readingReturnAnchorRef.current = activeReadingAnchor('section-end')
    const storedDraft = readStoredEditDraft(editTargetType, editTargetId, editTargetBodyHash)
    setEditDraft(storedDraft?.body ?? editTargetBody)
    setActionNotice(storedDraft ? `Restored unsaved draft from ${formatTraceTime(storedDraft.savedAt)}.` : '')
    setEditModeError('')
    setSectionEditDraft(null)
    setSectionEditError('')
    setAiRevision(null)
    setAiStatus('')
    setDraftConflict('')
    if (!isFocusMode) {
      toggleFocusRoute()
    }
    setIsEditMode(true)
    requestAnimationFrame(() => {
      window.scrollTo({ top: currentScrollTop, behavior: 'auto' })
    })
  }

  const startSectionEdit = (section: MarkdownSection) => {
    const body = viewMode === 'quizzes' ? selectedQuiz?.body : selectedNode?.body
    if (body) {
      readingReturnAnchorRef.current = sectionReadingAnchor(body, section, 'heading-start')
    }
    setSectionEditDraft({ ...section, draft: section.text })
    setSectionEditError('')
    setActionNotice('')
    setAiRevision(null)
    setAiStatus('')
    setDraftConflict('')
  }

  const cancelEditMode = () => {
    exitEditMode()
  }

  const cancelSectionEdit = () => {
    if (
      sectionEditDraft &&
      sectionEditDraft.draft !== sectionEditDraft.text &&
      !window.confirm('Discard unsaved section edits?')
    ) {
      return
    }
    const body = viewMode === 'quizzes' ? selectedQuiz?.body : selectedNode?.body
    if (body && sectionEditDraft) {
      readingReturnAnchorRef.current = sectionReadingAnchor(body, sectionEditDraft, 'heading-start')
    }
    setSectionEditDraft(null)
    setSectionEditError('')
  }

  const saveSectionEdit = async () => {
    if (!sectionEditDraft) {
      setActionNotice('No section is open for editing.')
      return
    }
    const currentBody = viewMode === 'quizzes' ? selectedQuiz?.body : selectedNode?.body
    if (!currentBody) {
      setSectionEditError('Markdown body is still loading; wait a moment and save again.')
      return
    }
    const draft = sectionEditDraft.draft.trimEnd()
    if (!draft.trim()) {
      setSectionEditError('Section cannot be empty.')
      return
    }
    const expectedPrefix = `${'#'.repeat(sectionEditDraft.level)} `
    if (!sectionEditDraft.isEditable || !draft.trimStart().startsWith(expectedPrefix)) {
      setSectionEditError(
        `Keep the opening heading line unchanged at this level, for example "${expectedPrefix}${sectionEditDraft.title}".`,
      )
      return
    }
    if (!window.confirm('Save this Markdown section to the local source file?')) return

    const body = replaceMarkdownLineRange(
      currentBody,
      sectionEditDraft.startLine,
      sectionEditDraft.endLine,
      draft,
    )
    readingReturnAnchorRef.current = sectionReadingAnchor(currentBody, sectionEditDraft, 'section-end')

    try {
      setIsEditSaving(true)
      setError('')
      setSectionEditError('')
      if (viewMode === 'quizzes' && selectedQuizId) {
        const data = await putJson<ApiQuizResponse>(`/api/quizzes/${selectedQuizId}/body`, {
          body,
          base_body_hash: selectedQuiz?.body_hash ?? '',
        })
        setSelectedQuiz(data.quiz)
      } else if (selectedSlug) {
        const data = await putJson<ApiNodeResponse>(`/api/nodes/${selectedSlug}/body`, {
          body,
          base_body_hash: selectedNode?.body_hash ?? '',
        })
        setSelectedNode(data.node)
      }
      setSectionEditDraft(null)
    } catch (saveError) {
      const message = saveError instanceof Error ? saveError.message : 'Unable to save Markdown section'
      setSectionEditError(message)
      setError(message)
    } finally {
      setIsEditSaving(false)
    }
  }

  const saveEditMode = async () => {
    const body = editDraft.trim()
    if (!body) {
      setEditModeError('Markdown cannot be empty.')
      return
    }
    if (!window.confirm('Save these Markdown changes to the local source file?')) return

    try {
      setIsEditSaving(true)
      setEditModeError('')
      setError('')
      if (activeAiJob?.status === 'draft_ready') {
        const applied = await postJson<ApiAiJobResponse>(`/api/ai/jobs/${activeAiJob.id}/apply`, {
          body,
        })
        setAiJobs((current) =>
          current.map((item) => (item.id === activeAiJob.id ? applied.job : item)),
        )
        if (viewMode === 'quizzes') {
          if (selectedQuizId) {
            const data = await fetchJson<ApiQuizResponse>(`/api/quizzes/${selectedQuizId}`)
            setSelectedQuiz(data.quiz)
          }
        } else if (selectedSlug) {
          const data = await fetchJson<ApiNodeResponse>(`/api/nodes/${selectedSlug}`)
          setSelectedNode(data.node)
        }
        setReaderQuestions((current) =>
          current.filter((item) => !activeAiJob.question_ids.includes(item.id)),
        )
        adjustOpenQuestionCount(activeAiJob.target_type, activeAiJob.target_id, -activeAiJob.question_ids.length)
        setActiveAiJob(null)
      } else {
        if (viewMode === 'quizzes' && selectedQuizId) {
          const data = await putJson<ApiQuizResponse>(`/api/quizzes/${selectedQuizId}/body`, {
            body,
            base_body_hash: selectedQuiz?.body_hash ?? '',
          })
          setSelectedQuiz(data.quiz)
        } else if (selectedSlug) {
          const data = await putJson<ApiNodeResponse>(`/api/nodes/${selectedSlug}/body`, {
            body,
            base_body_hash: selectedNode?.body_hash ?? '',
          })
          setSelectedNode(data.node)
        }
      }
      if (!activeAiJob && aiRevision?.resolved_question_ids.length) {
        await Promise.all(
          aiRevision.resolved_question_ids.map((questionId) =>
            postJson<ApiReaderQuestionResponse>(`/api/reader-questions/${questionId}/resolve`, {
              resolution_note: aiRevision.summary || 'Resolved by AI-assisted Markdown revision',
            }),
          ),
        )
        setReaderQuestions((current) =>
          current.filter((item) => !aiRevision.resolved_question_ids.includes(item.id)),
        )
        const targetType = viewMode === 'quizzes' ? 'quiz' : 'node'
        const targetId = viewMode === 'quizzes' ? selectedQuizId : selectedSlug
        if (targetId) {
          adjustOpenQuestionCount(targetType, targetId, -aiRevision.resolved_question_ids.length)
        }
      }
      if (editTargetType && editTargetId) {
        clearStoredEditDraft(editTargetType, editTargetId)
      }
      exitEditMode(false)
    } catch (saveError) {
      const message = saveError instanceof Error ? saveError.message : 'Unable to save Markdown'
      if (saveError instanceof ApiRequestError && saveError.status === 409 && activeAiJob) {
        setDraftConflict(
          'This AI draft is stale because the target Markdown changed after the draft was created. Your editor content is still here. Reopen the target or create a fresh draft from Q Queue before applying.',
        )
        setError('')
      } else {
        setEditModeError(message)
        setError(message)
      }
    } finally {
      setIsEditSaving(false)
    }
  }

  const moveSelectedNodeToTrash = async () => {
    if (!selectedNode) return
    if (!window.confirm(`Move "${selectedNode.title}" to Trashbin?`)) return
    try {
      setActionNotice('Moving node to Trashbin...')
      const data = await postJson<ApiNodeResponse>(`/api/nodes/${selectedNode.slug}/trash`, {})
      setSelectedNode(data.node)
      setNodes((current) => current.map((node) => (node.slug === data.node.slug ? data.node : node)))
      await refreshAreas()
      setUndoTrashSlug(data.node.slug)
      setActionNotice('Node moved to Trashbin. Restore it from Trashbin if needed.')
      navigateToArea('trash')
    } catch (trashError) {
      const message = trashError instanceof Error ? trashError.message : 'Unable to move node to Trashbin'
      setActionNotice(message)
      setError(message)
    }
  }

  const restoreSelectedNode = async () => {
    if (!selectedNode) return
    await restoreNodeBySlug(selectedNode.slug)
  }

  const restoreNodeBySlug = async (slug: string) => {
    try {
      setActionNotice('Restoring node...')
      const data = await postJson<ApiNodeResponse>(`/api/nodes/${slug}/restore`, {})
      setSelectedNode(data.node)
      setNodes((current) => current.map((node) => (node.slug === data.node.slug ? data.node : node)))
      await refreshAreas()
      setUndoTrashSlug('')
      exitEditMode(false)
      setActionNotice('Node restored as support visibility.')
      navigate(
        `/nodes/${encodeURIComponent(data.node.slug)}${routeSearch({
          activeArea: data.node.area,
          activeTrack: data.node.track,
          query,
          isFocusMode: true,
        })}`,
      )
    } catch (restoreError) {
      const message = restoreError instanceof Error ? restoreError.message : 'Unable to restore node'
      setActionNotice(message)
      setError(message)
    }
  }

  const moveSelectedNodeToArchive = async () => {
    if (!selectedNode) return
    if (!window.confirm(`Move "${selectedNode.title}" to Archive?`)) return
    try {
      setActionNotice('Moving node to Archive...')
      const data = await postJson<ApiNodeResponse>(`/api/nodes/${selectedNode.slug}/archive`, {})
      setSelectedNode(data.node)
      setNodes((current) => current.map((node) => (node.slug === data.node.slug ? data.node : node)))
      await refreshAreas()
      exitEditMode(false)
      setActionNotice('Node moved to Archive. Restore it from Archive if needed.')
      navigateToArea('archive')
    } catch (archiveError) {
      const message = archiveError instanceof Error ? archiveError.message : 'Unable to move node to Archive'
      setActionNotice(message)
      setError(message)
    }
  }

  const restoreSelectedNodeFromArchive = async () => {
    if (!selectedNode) return
    try {
      setActionNotice('Restoring node from Archive...')
      const data = await postJson<ApiNodeResponse>(`/api/nodes/${selectedNode.slug}/unarchive`, {})
      setSelectedNode(data.node)
      setNodes((current) => current.map((node) => (node.slug === data.node.slug ? data.node : node)))
      await refreshAreas()
      exitEditMode(false)
      setActionNotice('Node restored from Archive.')
      navigate(
        `/nodes/${encodeURIComponent(data.node.slug)}${routeSearch({
          activeArea: data.node.area,
          activeTrack: data.node.track,
          query,
          isFocusMode: true,
        })}`,
      )
    } catch (archiveError) {
      const message = archiveError instanceof Error ? archiveError.message : 'Unable to restore node from Archive'
      setActionNotice(message)
      setError(message)
    }
  }

  const permanentlyDeleteSelectedNode = async () => {
    if (!selectedNode) return
    if (!window.confirm(`Permanently delete "${selectedNode.title}"? This cannot be undone.`)) return
    try {
      setActionNotice('Deleting node permanently...')
      await deleteJson<{ ok: boolean }>(`/api/nodes/${selectedNode.slug}`)
      setNodes((current) => current.filter((node) => node.slug !== selectedNode.slug))
      await refreshAreas()
      setSelectedNode(null)
      setUndoTrashSlug('')
      setActionNotice('Node permanently deleted.')
      navigate('/nodes?area=trash')
    } catch (deleteError) {
      const message = deleteError instanceof Error ? deleteError.message : 'Unable to permanently delete node'
      setActionNotice(message)
      setError(message)
    }
  }

  const refreshHealthActions = async () => {
    const [repairData, schemaData, manifestData, preflightData] = await Promise.all([
      fetchJson<ApiSystemRepairResponse>('/api/system/repair'),
      fetchJson<ApiSystemSchemaResponse>('/api/system/schema'),
      fetchJson<ApiPackageExportResponse>('/api/package/export'),
      fetchJson<ApiAiPreflightResponse>('/api/ai/preflight'),
    ])
    setSystemRepair(repairData)
    setSystemSchema(schemaData)
    setPackageExport(manifestData)
    setAiPreflight(preflightData)
  }

  const exportPackageManifest = async () => {
    try {
      const data = await fetchJson<ApiPackageExportResponse>('/api/package/export?write=true')
      setPackageExport(data)
      setHealthActionNotice(data.manifest.written_to ? `Manifest written to ${data.manifest.written_to}` : 'Package manifest refreshed.')
    } catch (exportError) {
      setHealthActionNotice(exportError instanceof Error ? exportError.message : 'Unable to export package manifest')
    }
  }

  const exportLlmWikiPack = async () => {
    try {
      const data = await fetchJson<ApiLlmWikiExportResponse>('/api/llmwiki/export?write=true')
      setLlmWikiExport(data)
      setHealthActionNotice(data.pack.written_to ? `LLM Wiki pack written to ${data.pack.written_to}` : 'LLM Wiki pack refreshed.')
    } catch (exportError) {
      setHealthActionNotice(exportError instanceof Error ? exportError.message : 'Unable to export LLM Wiki pack')
    }
  }

  const runAiPreflight = async () => {
    try {
      const data = await fetchJson<ApiAiPreflightResponse>('/api/ai/preflight')
      setAiPreflight(data)
      setHealthActionNotice(data.message)
    } catch (preflightError) {
      setHealthActionNotice(preflightError instanceof Error ? preflightError.message : 'Unable to run AI preflight')
    }
  }

  const submitReviewRating = async (rating: DailyReviewRating) => {
    const quizId = selectedReviewQuiz?.id ?? selectedReviewItem?.target_id
    if (!quizId) return
    try {
      setIsReviewRating(true)
      setReviewError('')
      await postJson<ApiQuizAttemptResponse>(`/api/quizzes/${quizId}/attempts`, {
        grade: rating,
        elapsed_ms: 0,
        note: 'Daily review',
      })
      const data = await fetchJson<ApiDueReviewsResponse>('/api/review/due?limit=50')
      setReviewQueue(data.reviews)
      const nextId = data.reviews.find((item) => item.target_id !== quizId)?.target_id ?? data.reviews[0]?.target_id ?? ''
      setSelectedReviewId(nextId)
      setSelectedReviewQuiz(null)
      setActionNotice(`Review recorded: ${rating}.`)
    } catch (reviewSaveError) {
      setReviewError(reviewSaveError instanceof Error ? reviewSaveError.message : 'Unable to record review')
    } finally {
      setIsReviewRating(false)
    }
  }

  const openQuestionCount =
    viewMode === 'quizzes'
      ? selectedQuiz?.open_question_count ?? 0
      : selectedNode?.open_question_count ?? 0
  const currentBodyForDiff = viewMode === 'quizzes' ? selectedQuiz?.body ?? '' : selectedNode?.body ?? ''
  const aiDraftDiff = aiRevision
    ? buildLineDiffSummary(currentBodyForDiff, aiRevision.revised_body)
    : null

  return (
    <main className={`workspace-shell ${isFocusMode ? 'focus-mode' : ''} ${isEditMode ? 'editing-mode' : ''} ${viewMode === 'graph' ? 'graph-mode' : ''} ${viewMode === 'health' ? 'health-mode' : ''} ${viewMode === 'bite' ? 'bite-mode' : ''} ${isWidgetMode ? 'bite-widget-mode' : ''}`}>
      <aside className="sidebar" aria-label="Knowledge areas">
        <div className="brand-block">
          <p className="eyebrow">CS Learning OS</p>
          <h1>Knowledge Workbench</h1>
        </div>

        <section className="world-clock" aria-label="World clock">
          <div>
            <span>Beijing</span>
            <strong>{formatZoneTime(clockNow, 'Asia/Shanghai')}</strong>
          </div>
          <div>
            <span>US East</span>
            <strong>{formatZoneTime(clockNow, 'America/New_York')}</strong>
          </div>
        </section>

        <nav className="area-nav">
          <button
            type="button"
            className={activeArea === 'all' ? 'active' : ''}
            onClick={() => {
              if (!exitEditingBeforeNavigation()) return
              navigateToArea('all')
            }}
          >
            <span>{areaLabels.all}</span>
            <strong>{areaCounts.get('all') ?? 0}</strong>
          </button>
          <button
            type="button"
            className="area-collapse-toggle"
            onClick={() => setIsAreaNavExpanded((current) => !current)}
            aria-expanded={isAreaNavExpanded}
          >
            <span>Knowledge areas</span>
            <strong>{isAreaNavExpanded ? 'Hide' : 'Show'}</strong>
          </button>
          {isAreaNavExpanded && contentAreas.map((area) => (
            <button
              key={area}
              type="button"
              className={area === activeArea ? 'active' : ''}
              onClick={() => {
                if (!exitEditingBeforeNavigation()) return
                navigateToArea(area)
              }}
            >
              <span>{areaLabels[area] ?? slugTitle(area)}</span>
              <strong>
                {areaCounts.get(area) ?? 0}
              </strong>
            </button>
          ))}
          {systemSidebarAreas.map((area) => (
            <button
              key={area}
              type="button"
              className={area === activeArea ? 'active' : ''}
              onClick={() => {
                if (!exitEditingBeforeNavigation()) return
                navigateToArea(area)
              }}
            >
              <span>{areaLabels[area] ?? slugTitle(area)}</span>
              <strong>{areaCounts.get(area) ?? 0}</strong>
            </button>
          ))}
        </nav>

        <section className="practice-switch">
          <p className="eyebrow">Review system</p>
          <button
            type="button"
            className={viewMode === 'quizzes' ? 'active' : ''}
            onClick={() => {
              if (!exitEditingBeforeNavigation()) return
              navigate(`/quizzes${routeSearch({ query, quizSort })}`)
            }}
          >
            Practice / Quiz Bank
          </button>
          <button
            type="button"
            className={viewMode === 'bite' ? 'active' : ''}
            onClick={() => {
              if (!exitEditingBeforeNavigation()) return
              navigateToBite()
            }}
          >
            Daily Bite
          </button>
          <button
            type="button"
            className={viewMode === 'review' ? 'active' : ''}
            onClick={() => {
              if (!exitEditingBeforeNavigation()) return
              navigate('/review')
            }}
          >
            Daily review
          </button>
          <button
            type="button"
            className={viewMode === 'question-queue' ? 'active' : ''}
            onClick={() => {
              if (!exitEditingBeforeNavigation()) return
              navigateToQueue()
            }}
          >
            Q Queue
          </button>
          <button
            type="button"
            className={viewMode === 'graph' ? 'active' : ''}
            onClick={() => {
              if (!exitEditingBeforeNavigation()) return
              navigate('/graph')
            }}
          >
            Knowledge navigator
          </button>
          <button
            type="button"
            className={viewMode === 'health' ? 'active' : ''}
            onClick={() => {
              if (!exitEditingBeforeNavigation()) return
              navigate('/health')
            }}
          >
            System health
          </button>
        </section>
      </aside>

      {viewMode === 'graph' ? (
        <Suspense
          fallback={
            <section className="graph-navigator-shell" aria-label="Knowledge graph navigator">
              <p className="detail-loading">Loading graph navigator...</p>
            </section>
          }
        >
          <GraphNavigator
            payload={graphPayload}
            isLoading={isLoading}
            error={error}
            onNavigate={navigateGraph}
            onPage={navigateGraphPage}
          />
        </Suspense>
      ) : (
      <section className="node-column" aria-label="Knowledge nodes">
        {viewMode === 'health' ? (
          <header className="search-header cockpit-header">
            <p className="eyebrow">Health metrics</p>
            <h2>Storage ledger</h2>
            <p>
              {isLoading
                ? 'Loading health metrics...'
                : `${visibleCount} size partitions, sorted by local footprint.`}
            </p>
          </header>
        ) : viewMode === 'bite' ? (
          <header className="search-header cockpit-header">
            <p className="eyebrow">Daily Bite</p>
            <h2>One Blank</h2>
            <p>Lightweight recall, linked back to your quiz Markdown.</p>
          </header>
        ) : viewMode === 'review' ? (
          <header className="search-header cockpit-header">
            <p className="eyebrow">Daily review</p>
            <h2>Review Queue</h2>
            <p>
              {isLoading
                ? 'Loading due reviews...'
                : `${reviewQueue.length} due or new quiz cards ready.`}
            </p>
          </header>
        ) : (
          <SearchHeaderControls
            mode={
              viewMode === 'quizzes'
                ? 'quizzes'
                : viewMode === 'question-queue'
                  ? 'question-queue'
                  : 'nodes'
            }
            query={query}
            nodeSort={nodeSort}
            quizSort={quizSort}
            nodeSortOptions={availableNodeSortOptions}
            quizSortOptions={availableQuizSortOptions}
            isLoading={isLoading}
            visibleCount={visibleCount}
            totalCount={totalCount}
            isNewNodeOpen={isNewNodeOpen}
            onNewNodeToggle={openNewNodeForm}
            onQueryChange={(nextQuery) => {
              if (viewMode === 'quizzes') {
                const nextSort = quizSort === defaultQuizSort(query) ? defaultQuizSort(nextQuery) : quizSort
                navigate(
                  `${selectedQuizId ? `/quizzes/${encodeURIComponent(selectedQuizId)}` : '/quizzes'}${routeSearch({
                    activeArea,
                    activeTrack,
                    query: nextQuery,
                    quizSort: nextSort,
                    isFocusMode,
                  })}`,
                  { replace: true },
                )
              } else if (viewMode === 'question-queue') {
                navigate(`/queue${routeSearch({ query: nextQuery })}`, { replace: true })
              } else {
                const nextSort = nodeSort === defaultNodeSort(query) ? defaultNodeSort(nextQuery) : nodeSort
                navigate(
                  `${selectedSlug ? `/nodes/${encodeURIComponent(selectedSlug)}` : '/nodes'}${routeSearch({
                    activeArea,
                    activeTrack,
                    query: nextQuery,
                    nodeSort: nextSort,
                    isFocusMode,
                  })}`,
                  { replace: true },
                )
              }
            }}
            onNodeSortChange={(nextSort) => {
              navigate(
                `/nodes${routeSearch({
                  activeArea,
                  activeTrack,
                  query,
                  nodeSort: nextSort,
                  isFocusMode,
                })}`,
              )
            }}
            onQuizSortChange={(nextSort) => {
              navigate(
                `/quizzes${routeSearch({
                  activeArea,
                  activeTrack,
                  query,
                  quizSort: nextSort,
                  isFocusMode,
                })}`,
              )
            }}
          />
        )}

        {viewMode === 'nodes' && isNewNodeOpen && (
          <section className="new-node-panel" aria-label="Create new node">
            <p className="eyebrow">Create node</p>
            <input
              value={newNodeDraft.title}
              onChange={(event) => {
                setNewNodeFeedback('')
                setNewNodeDraft((current) => ({ ...current, title: event.target.value }))
              }}
              placeholder="Title, e.g. Pointer Arithmetic"
              aria-label="New node title"
            />
            <div className="new-node-grid">
              <input
                value={newNodeDraft.area}
                onChange={(event) => setNewNodeDraft((current) => ({ ...current, area: event.target.value }))}
                placeholder="area"
                aria-label="New node area"
              />
              <input
                value={newNodeDraft.track}
                onChange={(event) => setNewNodeDraft((current) => ({ ...current, track: event.target.value }))}
                placeholder="track"
                aria-label="New node track"
              />
            </div>
            <p className="inline-hint">
              Area, track, and tags accept normal text; they will be saved as slug values.
            </p>
            <input
              value={newNodeDraft.summary}
              onChange={(event) => setNewNodeDraft((current) => ({ ...current, summary: event.target.value }))}
              placeholder="Short summary"
              aria-label="New node summary"
            />
            <input
              value={newNodeDraft.tags}
              onChange={(event) => setNewNodeDraft((current) => ({ ...current, tags: event.target.value }))}
              placeholder="tags, comma separated"
              aria-label="New node tags"
            />
            {newNodeFeedback && <p className={newNodeFeedback.includes('Request failed') ? 'inline-error' : 'inline-hint'}>{newNodeFeedback}</p>}
            <button
              type="button"
              className="focus-toggle ai-action"
              disabled={isNodeCreating}
              onClick={createNodeFromForm}
            >
              {isNodeCreating ? 'Creating...' : 'Create and edit'}
            </button>
          </section>
        )}

        {actionNotice && (
          <div className="action-notice">
            <span>{actionNotice}</span>
            {undoTrashSlug && (
              <button type="button" className="text-link" onClick={() => restoreNodeBySlug(undoTrashSlug)}>
                Undo move to trash
              </button>
            )}
          </div>
        )}
        {error && <p className="error-banner">{error}</p>}

        {viewMode === 'nodes' && visibleTracks.length > 0 && (
          <section className="track-panel" aria-label="Reading tracks">
            <div>
              <p className="eyebrow">Reading tracks</p>
              <strong>{areaLabels[activeArea] ?? slugTitle(activeArea)}</strong>
            </div>
            <div className="track-list">
              <button
                type="button"
                className={activeTrack === 'all' ? 'active' : ''}
                onClick={() => navigateToNode(selectedSlug, { replace: true })}
              >
                All tracks
              </button>
              {visibleTracks.map((track) => (
                <button
                  key={track.track}
                  type="button"
                  className={activeTrack === track.track ? 'active' : ''}
                  onClick={() =>
                    navigate(
                      `/nodes/${encodeURIComponent(selectedSlug)}${routeSearch({
                        activeArea,
                        activeTrack: track.track,
                        query,
                        nodeSort,
                        isFocusMode,
                      })}`,
                      { replace: true },
                    )
                  }
                >
                  <span>{trackLabels[track.track] ?? track.label ?? slugTitle(track.track)}</span>
                  <strong>{track.node_count}</strong>
                </button>
              ))}
            </div>
            <p>
              Ordered by frontmatter <code>track</code> and <code>order</code>, so the path can change
              without editing React.
            </p>
          </section>
        )}

        <div className="node-list">
          {viewMode === 'review'
            ? reviewItems.map((review) => (
                <button
                  key={review.id}
                  type="button"
                  className={`node-card ${review.quizId === selectedReviewId ? 'selected' : ''}`}
                  onClick={() => setSelectedReviewId(review.quizId)}
                >
                  <span className="node-meta">{review.area} / {review.difficulty} / {review.intervalLabel}</span>
                  <strong>{review.title}</strong>
                  {review.summary && <span>{review.summary}</span>}
                  <span>Due {review.dueAt}</span>
                </button>
              ))
            : viewMode === 'bite'
            ? (
                <article className="node-card selected bite-list-card">
                  <span className="node-meta">Micro drill</span>
                  <strong>Daily Bite</strong>
                  <span>One fill-in blank pulled from your quiz bank.</span>
                </article>
              )
            : viewMode === 'question-queue'
            ? queueItems.map((item) => {
                if (item.kind === 'question') {
                  const linkedJob = item.job
                  return (
                    <article key={item.id} className="node-card question-card">
                      <div className="question-select-row">
                        <input
                          type="checkbox"
                          checked={selectedQuestionIds.includes(item.question.id)}
                          onChange={() => toggleQuestionSelection(item.question.id)}
                          aria-label={`Select Q #${item.question.id}`}
                        />
                        <span className="node-meta">
                          Q #{item.question.id} / {item.question.target_type} / {item.question.target_id} /{' '}
                          {item.question.status}
                        </span>
                      </div>
                      <button type="button" className="question-card-main" onClick={() => openQuestionTarget(item.question)}>
                        <strong>{item.question.question}</strong>
                        <span>
                          {linkedJob
                            ? `Latest draft job #${linkedJob.id}: ${linkedJob.status}`
                            : 'Open target or draft a focused AI improvement.'}
                        </span>
                      </button>
                      {linkedJob && (
                        <span className="node-meta">
                          Job #{linkedJob.id} / {linkedJob.stage}
                        </span>
                      )}
                      <div className="question-card-actions">
                        {!linkedJob && (
                          <>
                            <label className="scope-select">
                              <span>Scope</span>
                              <select
                                value={questionScopes[item.question.id] ?? 'question'}
                                onChange={(event) =>
                                  setQuestionScopes((current) => ({
                                    ...current,
                                    [item.question.id]: event.target.value as AiDraftScope,
                                  }))
                                }
                              >
                                <option value="question">This question</option>
                                <option value="selected">Selected questions</option>
                                <option value="page">Current page</option>
                              </select>
                            </label>
                            <button
                              type="button"
                              className="text-link"
                              disabled={!aiEnabled || isAiRevising}
                              onClick={() => draftQuestionFromQueue(item.question)}
                            >
                              {aiEnabled ? 'Draft' : 'AI disabled'}
                            </button>
                          </>
                        )}
                        {linkedJob?.status === 'draft_ready' && linkedJob.revision && (
                          <button type="button" className="text-link" onClick={() => reviewAiJob(linkedJob)}>
                            Review draft
                          </button>
                        )}
                        {linkedJob?.status === 'failed' && (
                          <button type="button" className="text-link" disabled={!aiEnabled} onClick={() => retryAiJob(linkedJob)}>
                            {aiEnabled ? 'Retry' : 'Retry disabled'}
                          </button>
                        )}
                        <button
                          type="button"
                          className="text-link"
                          onClick={() => dismissReaderQuestion(item.question)}
                        >
                          Dismiss
                        </button>
                        <button
                          type="button"
                          className="text-link danger-link"
                          onClick={() => deleteReaderQuestion(item.question)}
                        >
                          Delete
                        </button>
                      </div>
                    </article>
                  )
                }

                const jobError = item.job.error_summary || item.job.error
                return (
                  <article key={item.id} className="node-card question-card job-card">
                    <button
                      type="button"
                      className="question-card-main"
                      onClick={() =>
                        item.job.status === 'draft_ready' && item.job.revision
                          ? reviewAiJob(item.job)
                          : openJobTarget(item.job)
                      }
                    >
                      <span className="node-meta">
                        Job #{item.job.id} / {item.job.target_type} / {item.job.target_id} / {item.job.status}
                      </span>
                      <strong>{item.job.status === 'failed' ? 'AI job needs attention' : item.job.stage}</strong>
                      <span>
                        {item.job.revision?.summary ||
                          jobError ||
                          item.job.instruction ||
                          'AI job is queued for this target.'}
                      </span>
                    </button>
                    <div className="question-card-actions">
                      {item.job.status === 'draft_ready' && item.job.revision && (
                        <button type="button" className="text-link" onClick={() => reviewAiJob(item.job)}>
                          Review draft
                        </button>
                      )}
                      {item.job.status === 'failed' && (
                        <button type="button" className="text-link" disabled={!aiEnabled} onClick={() => retryAiJob(item.job)}>
                          {aiEnabled ? 'Retry' : 'Retry disabled'}
                        </button>
                      )}
                      {['queued', 'solving'].includes(item.job.status) && (
                        <button type="button" className="text-link danger-link" onClick={() => cancelAiJob(item.job)}>
                          Cancel
                        </button>
                      )}
                    </div>
                  </article>
                )
              })
            : viewMode === 'health'
              ? systemMetrics
                ? (
                    <>
                      <article className="node-card compact-card health-summary-card">
                        <span className="node-meta">project related files</span>
                        <strong>{formatBytes(projectRelatedBytes)}</strong>
                        <span>
                          Exclusive partitions explain {formatBytes(systemMetrics.storage.explained_project_bytes)}.
                        </span>
                        <span>
                          {systemMetrics.cache?.refreshing
                            ? 'Background refresh is running; cached/fallback data is shown now.'
                            : systemMetrics.cache?.cached
                              ? `Update: ${formatBeijingDateTime(systemMetrics.collected_at)} Beijing`
                              : 'Fresh fallback returned immediately; full scan will update the cache.'}
                        </span>
                      </article>
                      <article className="node-card compact-card health-summary-card">
                        <span className="node-meta">github upload size</span>
                        <strong>{formatBytes(systemMetrics.storage.github_repo_bytes)}</strong>
                        <span>
                          {systemMetrics.github.source === 'github-api'
                            ? 'Remote repository size reported by GitHub.'
                            : 'Fallback estimate from local tracked files.'}
                        </span>
                      </article>
                      {storagePartitions.map((partition) => {
                        const percent = projectRelatedBytes ? (partition.bytes / projectRelatedBytes) * 100 : 0
                        return (
                          <article className="node-card compact-card health-partition-card" key={partition.key}>
                            <div className="partition-card-head">
                              <div>
                                <span className="node-meta">{partition.kind}</span>
                                <strong>{partition.label}</strong>
                              </div>
                              <span className="partition-percent">{percent.toFixed(1)}%</span>
                            </div>
                            <div className="health-meter">
                              <span style={{ width: `${Math.max(3, percent)}%` }} />
                            </div>
                            <span>{formatBytes(partition.bytes)} · {partition.summary}</span>
                            <span className="path-note">{partition.path}</span>
                          </article>
                        )
                      })}
                      <article className="node-card compact-card health-summary-card">
                        <span className="node-meta">metric index</span>
                        <strong>{metricPartitions.length} tracked metrics</strong>
                        <span>
                          {metricPartitions
                            .slice(0, 4)
                            .map((partition) => `${partition.label}: ${formatBytes(partition.bytes)}`)
                            .join(' / ')}
                        </span>
                      </article>
                      <article className="node-card compact-card health-summary-card">
                        <span className="node-meta">knowledge inventory</span>
                        <strong>{systemMetrics.counts.nodes} nodes · {systemMetrics.counts.quizzes} quizzes</strong>
                        <span>{systemMetrics.counts.open_questions} open questions</span>
                      </article>
                      <article className="node-card compact-card health-summary-card">
                        <span className="node-meta">ai workflow</span>
                        <strong>{systemMetrics.counts.active_ai_jobs} active · {systemMetrics.counts.failed_ai_jobs} failed</strong>
                        <span>{systemMetrics.ai.message}</span>
                      </article>
                    </>
                  )
                : <p className="detail-loading">Loading health metrics...</p>
            : viewMode === 'quizzes'
            ? filteredQuizzes.map((quiz) => (
                <button
                  key={quiz.id}
                  type="button"
                  className={`node-card ${quiz.id === selectedQuizId ? 'selected' : ''}`}
                  onClick={() => {
                    if (!exitEditingBeforeNavigation()) return
                    navigateToQuiz(quiz.id)
                  }}
                >
                  <span className="node-meta">
                    {areaLabels[quiz.area] ?? slugTitle(quiz.area)} / {quiz.difficulty} / weight {quiz.weight}
                  </span>
                  <strong>{quiz.title}</strong>
                  <span>{quiz.summary}</span>
                </button>
              ))
            : filteredNodes.map((node) => (
                <button
                  key={node.slug}
                  type="button"
                  className={`node-card ${node.slug === selectedSlug ? 'selected' : ''}`}
                  onClick={() => {
                    if (!exitEditingBeforeNavigation()) return
                    navigateToNode(node.slug)
                  }}
                >
                  <span className="node-meta">
                    {areaLabels[node.area] ?? slugTitle(node.area)} / {trackLabels[node.track] ?? slugTitle(node.track)} /{' '}
                    #{node.display_order}
                  </span>
                  <strong>{node.title}</strong>
                  <span>{node.summary}</span>
                </button>
              ))}
        </div>
      </section>
      )}

      {viewMode !== 'graph' && (
      <aside className="detail-panel" aria-label="Node detail">
        {(viewMode === 'nodes' || viewMode === 'quizzes') && (selectedNode || selectedQuiz) && !isEditMode && !isFocusMode && (
          <button
            type="button"
            className="focus-edge-toggle"
            onClick={toggleFocusRoute}
            aria-label="Focus reading"
            title="Focus reading"
          >
            <span aria-hidden="true" />
          </button>
        )}
        {viewMode === 'bite' ? (
          <DailyBitePanel
            onOpenQuiz={(quizId) => navigateToQuiz(quizId, { focus: true })}
            onOpenNode={(slug) => navigateToNode(slug, { focus: true })}
          />
        ) : viewMode === 'review' ? (
          <DailyReviewPanel
            reviews={reviewItems}
            selectedQuiz={selectedDailyReviewQuiz}
            isReviewsLoading={isLoading}
            isQuizLoading={isReviewQuizLoading}
            isRating={isReviewRating}
            reviewsError={reviewError}
            quizError={reviewError}
            onSelectReview={(review) => setSelectedReviewId(review.quizId)}
            onRateReview={(rating) => submitReviewRating(rating)}
            renderQuizBody={({ quiz, isAnswerRevealed, onRevealAnswer }) =>
              isAnswerRevealed ? (
                <Suspense fallback={<p className="detail-loading">Loading Markdown renderer...</p>}>
                  <MarkdownView body={quiz.body ?? ''} />
                </Suspense>
              ) : (
                <div className="empty-state">
                  <h2>Recall first</h2>
                  <p>Try to answer from memory before revealing the Markdown explanation.</p>
                  <button type="button" className="focus-toggle ai-action" onClick={onRevealAnswer}>
                    Reveal answer
                  </button>
                </div>
              )
            }
          />
        ) : viewMode === 'question-queue' ? (
          <QuestionQueueDetail
            activeAiJobs={activeAiJobs}
            jobEvents={jobEvents}
            readerQuestions={readerQuestions}
            onCancelAiJob={cancelAiJob}
            onDeleteReaderQuestion={deleteReaderQuestion}
            onDismissReaderQuestion={dismissReaderQuestion}
            onLoadJobEvents={loadJobEvents}
            onOpenJobTarget={openJobTarget}
            onOpenQuestionTarget={openQuestionTarget}
            onRejectAiJob={rejectAiJob}
            onRetryAiJob={retryAiJob}
            onReviewAiJob={reviewAiJob}
          />
        ) : viewMode === 'health' ? (
          <div className="health-detail">
            <section className="detail-heading health-heading">
              <p className="eyebrow">Program health</p>
              <h2>System Health</h2>
              <p>
                A local observability cockpit for content size, SQLite growth, AI job health, and future
                maintenance warnings.
              </p>
              {systemMetrics?.cache && (
                <p className="cache-note">
                  {systemMetrics.cache.refreshing
                    ? 'Heavy storage scan is refreshing in the background.'
                    : systemMetrics.cache.cached
                      ? 'Using cached storage metrics.'
                      : 'Showing immediate fallback metrics while the first heavy scan is queued.'}
                  {typeof systemMetrics.collection_ms === 'number' && systemMetrics.collection_ms > 0
                    ? ` Last scan took ${systemMetrics.collection_ms} ms.`
                    : ''}
                  {` Update: ${formatBeijingDateTime(systemMetrics.collected_at)} Beijing.`}
                </p>
              )}
            </section>
            {systemMetrics ? (
              <>
                <section className="health-pie-panel" aria-label="Storage distribution">
                  <div
                    className="storage-pie"
                    style={{ background: pieGradient(storagePartitions, projectRelatedBytes) }}
                    aria-hidden="true"
                  >
                    <div className="pie-center">
                      <span>Total</span>
                      <strong>{formatBytes(projectRelatedBytes)}</strong>
                      <small>{formatBytes(systemMetrics.storage.explained_project_bytes)} explained</small>
                    </div>
                  </div>
                  <div className="pie-legend">
                    <p className="eyebrow">Size-sorted distribution</p>
                    {storagePartitions.map((partition, index) => {
                      const percent = projectRelatedBytes ? (partition.bytes / projectRelatedBytes) * 100 : 0
                      return (
                        <div className="legend-row" key={partition.key}>
                          <span className="legend-dot" style={{ background: partitionColor(index) }} />
                          <span>{partition.label}</span>
                          <strong>{percent.toFixed(1)}%</strong>
                        </div>
                      )
                    })}
                  </div>
                </section>
                <section className="health-grid compact-health-grid" aria-label="Upload and metrics">
                  <article className="health-card accent-card">
                    <p className="eyebrow">Git upload estimate</p>
                    <h3>{formatBytes(systemMetrics.storage.github_repo_bytes)}</h3>
                    <p>
                      {systemMetrics.github.message} Current source: {systemMetrics.github.source}. Local tracked
                      files: {formatBytes(systemMetrics.storage.github_repo_fallback_tracked_bytes)}.
                    </p>
                  </article>
                  <article className="health-card">
                    <p className="eyebrow">Metric index</p>
                    <ul className="metric-list">
                      {metricPartitions.map((partition) => (
                        <li key={partition.key}>
                          <strong>{formatBytes(partition.bytes)}</strong> {partition.label}
                        </li>
                      ))}
                    </ul>
                  </article>
                </section>
                {healthActionNotice && <p className="inline-hint">{healthActionNotice}</p>}
                <HealthActionPanels
                  integrityIssues={healthIssues}
                  repairIssues={healthIssues}
                  packageManifest={packageManifestEntries}
                  llmWikiPack={llmWikiPackSummary}
                  aiPreflightChecks={aiPreflightChecks}
                  aiPreflightEnabled={aiEnabled}
                  schemaMetadata={schemaMetadata}
                  contentIndex={contentIndexSummary}
                  onExportPackage={exportPackageManifest}
                  onExportLlmWikiPack={exportLlmWikiPack}
                  onRunAiPreflight={runAiPreflight}
                  onRefreshSchemaMetadata={() => {
                    refreshHealthActions().catch((refreshError) => {
                      setHealthActionNotice(refreshError instanceof Error ? refreshError.message : 'Unable to refresh health data')
                    })
                  }}
                  onRefreshContentIndex={() => {
                    refreshHealthActions().catch((refreshError) => {
                      setHealthActionNotice(refreshError instanceof Error ? refreshError.message : 'Unable to refresh health data')
                    })
                  }}
                  onInspectIssue={(issue) => setHealthActionNotice(issue.summary || issue.title)}
                  onRepairIssue={(issue) => setHealthActionNotice(`Manual repair required: ${issue.title}`)}
                />
              </>
            ) : (
              <p className="detail-loading">Loading health metrics...</p>
            )}
          </div>
        ) : viewMode === 'quizzes' && selectedQuiz?.id === selectedQuizId ? (
          <>
            {isFocusMode && !isEditMode && <MarkdownToc body={selectedQuiz.body} />}
            <div className="detail-main">
              <div className="detail-heading">
                <div className="detail-toolbar">
                  <p className="eyebrow">{selectedQuiz.area} / quiz</p>
                  <div className="toolbar-actions">
                    <button
                      type="button"
                      className="focus-toggle"
                      onClick={() => {
                        if (isEditMode) {
                          exitEditMode()
                        } else {
                          startEditMode()
                        }
                      }}
                    >
                      {isEditMode ? 'Exit edit mode' : 'Edit mode'}
                    </button>
                    <button
                      type="button"
                      className="focus-toggle"
                      onClick={goBack}
                    >
                      Back
                    </button>
                    <button
                      type="button"
                      className="focus-toggle"
                      onClick={toggleFocusRoute}
                    >
                      {isFocusMode ? 'Show map' : 'Focus reading'}
                    </button>
                  </div>
                </div>
                <h2>{selectedQuiz.title}</h2>
                <p>{selectedQuiz.summary}</p>
              </div>

              <div className="tag-row">
                {selectedQuiz.open_question_count > 0 && (
                  <span className="needs-review">Q to be solved: {selectedQuiz.open_question_count}</span>
                )}
                <span>{selectedQuiz.difficulty}</span>
                {selectedQuiz.tags.map((tag) => (
                  <span key={tag}>{tag}</span>
                ))}
              </div>

              <section className="detail-section">
                <h3>Quiz body</h3>
                {isEditMode ? (
                  <div className="markdown-editor">
                    <textarea
                      value={editDraft}
                      onChange={(event) => {
                        setEditModeError('')
                        setEditDraft(event.target.value)
                      }}
                      aria-label="Markdown editor"
                    />
                    {editModeError && <p className="inline-error">{editModeError}</p>}
                    <div className="editor-actions">
                      <button
                        type="button"
                        className="focus-toggle"
                        disabled={isEditSaving}
                        onClick={saveEditMode}
                      >
                        {isEditSaving ? 'Saving...' : 'Save Markdown'}
                      </button>
                      <button type="button" className="focus-toggle" onClick={cancelEditMode}>
                        Cancel
                      </button>
                    </div>
                    {draftConflict && (
                      <DraftConflictCard
                        message={draftConflict}
                        onQueue={returnToQueueFromConflict}
                        onFreshDraft={createFreshDraftFromConflict}
                        isBusy={isAiRevising}
                        isFreshDraftEnabled={aiEnabled}
                      />
                    )}
                    {aiRevision && <AiRevisionCard revision={aiRevision} diff={aiDraftDiff} />}
                    {aiStatus && !aiRevision && <p className={aiStatusClass}>{aiStatusText}</p>}
                  </div>
                ) : (
                  <Suspense fallback={<p className="detail-loading">Loading Markdown renderer...</p>}>
                  <MarkdownView
                    body={selectedQuiz.body}
                    editingSection={sectionEditDraft}
                    isSectionSaving={isEditSaving}
                    onCancelSectionEdit={cancelSectionEdit}
                    onChangeSectionDraft={(draft) => {
                      setSectionEditError('')
                      setSectionEditDraft((current) => (current ? { ...current, draft } : current))
                    }}
                    onSaveSectionEdit={saveSectionEdit}
                    onStartSectionEdit={startSectionEdit}
                    sectionEditError={sectionEditError}
                  />
                  </Suspense>
                )}
              </section>

              <section className="detail-section split">
                <div>
                  <h3>Linked review</h3>
                  {selectedQuiz.linked_nodes.length ? (
                    <ul>
                      {selectedQuiz.linked_nodes.map((link) => (
                        <li key={`${link.kind}-${link.slug}`}>
                          <button
                            type="button"
                            className="text-link"
                            onClick={() => {
                              if (!exitEditingBeforeNavigation()) return
                              navigateToNode(link.slug, { focus: true })
                            }}
                          >
                            {link.kind}: {link.title}
                          </button>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p>No linked review nodes yet.</p>
                  )}
                </div>
                <div>
                  <h3>Sources</h3>
                  {selectedQuiz.sources.length ? (
                    <ul>
                      {selectedQuiz.sources.map((source) => (
                        <li key={source.source}>{source.source}</li>
                      ))}
                    </ul>
                  ) : (
                    <p>No sources recorded.</p>
                  )}
                </div>
              </section>

              {isDetailLoading && <p className="detail-loading">Refreshing detail...</p>}
            </div>
            {isFocusMode && !isEditMode && (
              <ReaderQuestionPanel
                aiDraftHint={aiDraftHint}
                aiEnabled={aiEnabled}
                aiStatus={aiStatus}
                aiStatusClass={aiStatusClass}
                aiStatusText={aiStatusText}
                isAiRevising={isAiRevising}
                isQuestionSaving={isQuestionSaving}
                openQuestionCount={openQuestionCount}
                placeholder="Example: Why does sete write only %al here?"
                questionDraft={questionDraft}
                questionFeedback={questionFeedback}
                readerQuestionHint={readerQuestionHint}
                visibleReaderQuestions={visibleReaderQuestions}
                onDraftWithAi={requestAiRevision}
                onQuestionDraftChange={(value) => {
                  setQuestionFeedback('')
                  setQuestionDraft(value)
                }}
                onSubmitQuestion={submitReaderQuestion}
              />
            )}
          </>
        ) : viewMode === 'nodes' && selectedNode?.slug === selectedSlug ? (
          <>
            {isFocusMode && !isEditMode && <MarkdownToc body={selectedNode.body} />}
            <div className="detail-main">
              <div className="detail-heading">
                <div className="detail-toolbar">
                  <p className="eyebrow">{selectedNode.area}</p>
                  <div className="toolbar-actions">
                    <button
                      type="button"
                      className="focus-toggle"
                      onClick={() => {
                        if (isEditMode) {
                          exitEditMode()
                        } else {
                          startEditMode()
                        }
                      }}
                    >
                      {isEditMode ? 'Exit edit mode' : 'Edit mode'}
                    </button>
                    <button
                      type="button"
                      className="focus-toggle"
                      onClick={goBack}
                    >
                      Back
                    </button>
                    <button
                      type="button"
                      className="focus-toggle"
                      onClick={toggleFocusRoute}
                    >
                      {isFocusMode ? 'Show map' : 'Focus reading'}
                    </button>
                    {selectedNode.visibility === 'trash' ? (
                      <>
                        <button type="button" className="focus-toggle ai-action" onClick={restoreSelectedNode}>
                          Restore
                        </button>
                        <button type="button" className="focus-toggle danger-button" onClick={permanentlyDeleteSelectedNode}>
                          Delete forever
                        </button>
                      </>
                    ) : (
                      <button type="button" className="focus-toggle danger-button" onClick={moveSelectedNodeToTrash}>
                        Move to trash
                      </button>
                    )}
                  </div>
                </div>
                <h2>{selectedNode.title}</h2>
                <p>{selectedNode.summary}</p>
              </div>

              <div className="tag-row">
                {selectedNode.open_question_count > 0 && (
                  <span className="needs-review">Q to be solved: {selectedNode.open_question_count}</span>
                )}
                {selectedNode.tags.map((tag) => (
                  <span key={tag}>{tag}</span>
                ))}
              </div>

              <section className="detail-section">
                <h3>Note body</h3>
                {isEditMode ? (
                  <div className="markdown-editor">
                    <textarea
                      value={editDraft}
                      onChange={(event) => {
                        setEditModeError('')
                        setEditDraft(event.target.value)
                      }}
                      aria-label="Markdown editor"
                    />
                    {editModeError && <p className="inline-error">{editModeError}</p>}
                    <div className="editor-actions">
                      <button
                        type="button"
                        className="focus-toggle"
                        disabled={isEditSaving}
                        onClick={saveEditMode}
                      >
                        {isEditSaving ? 'Saving...' : 'Save Markdown'}
                      </button>
                      <button type="button" className="focus-toggle" onClick={cancelEditMode}>
                        Cancel
                      </button>
                    </div>
                    {draftConflict && (
                      <DraftConflictCard
                        message={draftConflict}
                        onQueue={returnToQueueFromConflict}
                        onFreshDraft={createFreshDraftFromConflict}
                        isBusy={isAiRevising}
                        isFreshDraftEnabled={aiEnabled}
                      />
                    )}
                    {aiRevision && <AiRevisionCard revision={aiRevision} diff={aiDraftDiff} />}
                    {aiStatus && !aiRevision && <p className={aiStatusClass}>{aiStatusText}</p>}
                  </div>
                ) : (
                  <Suspense fallback={<p className="detail-loading">Loading Markdown renderer...</p>}>
                  <MarkdownView
                    body={selectedNode.body}
                    editingSection={sectionEditDraft}
                    isSectionSaving={isEditSaving}
                    onCancelSectionEdit={cancelSectionEdit}
                    onChangeSectionDraft={(draft) => {
                      setSectionEditError('')
                      setSectionEditDraft((current) => (current ? { ...current, draft } : current))
                    }}
                    onSaveSectionEdit={saveSectionEdit}
                    onStartSectionEdit={startSectionEdit}
                    sectionEditError={sectionEditError}
                  />
                  </Suspense>
                )}
              </section>

              <section className="detail-section split">
                <div>
                  <h3>Links</h3>
                  {selectedNode.links.length ? (
                    <ul>
                      {selectedNode.links.map((link) => (
                        <li key={`${link.kind}-${link.target}`}>
                          <button
                            type="button"
                            className="text-link"
                            onClick={() => {
                              if (!exitEditingBeforeNavigation()) return
                              navigateToNode(link.target, { focus: true })
                            }}
                          >
                            {link.kind}: {slugTitle(link.target)}
                          </button>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p>No linked nodes yet.</p>
                  )}
                </div>
                <div>
                  <h3>Sources</h3>
                  {selectedNode.sources.length ? (
                    <ul>
                      {selectedNode.sources.map((source) => (
                        <li key={source.source}>{source.source}</li>
                      ))}
                    </ul>
                  ) : (
                    <p>No sources recorded.</p>
                  )}
                </div>
              </section>

              {selectedNode.visibility !== 'trash' && (
                <section className="detail-section archive-actions" aria-label="Archive actions">
                  <p className="eyebrow">Archive</p>
                  {selectedNode.visibility === 'archive' ? (
                    <button type="button" className="focus-toggle" onClick={restoreSelectedNodeFromArchive}>
                      Restore from archive
                    </button>
                  ) : (
                    <button type="button" className="focus-toggle" onClick={moveSelectedNodeToArchive}>
                      Move to archive
                    </button>
                  )}
                </section>
              )}

              <section className="detail-section reading-trace" aria-label="Node reading trace">
                <div>
                  <p className="eyebrow">Reading trace</p>
                  <h3>Memory hooks</h3>
                </div>
                <dl>
                  <div>
                    <dt>Last read</dt>
                    <dd>{formatTraceTime(selectedNode.last_read_at)}</dd>
                  </div>
                  <div>
                    <dt>Last edit</dt>
                    <dd>{formatTraceTime(selectedNode.updated_at)}</dd>
                  </div>
                  <div>
                    <dt>Read count</dt>
                    <dd>{selectedNode.read_count ?? 0}</dd>
                  </div>
                </dl>
                {readTraceError && <p className="inline-error">{readTraceError}</p>}
              </section>

              {isDetailLoading && <p className="detail-loading">Refreshing detail...</p>}
            </div>
            {isFocusMode && !isEditMode && (
              <ReaderQuestionPanel
                aiDraftHint={aiDraftHint}
                aiEnabled={aiEnabled}
                aiStatus={aiStatus}
                aiStatusClass={aiStatusClass}
                aiStatusText={aiStatusText}
                isAiRevising={isAiRevising}
                isQuestionSaving={isQuestionSaving}
                openQuestionCount={openQuestionCount}
                placeholder="Example: This explanation skips why %eax changes here."
                questionDraft={questionDraft}
                questionFeedback={questionFeedback}
                readerQuestionHint={readerQuestionHint}
                visibleReaderQuestions={visibleReaderQuestions}
                onDraftWithAi={requestAiRevision}
                onQuestionDraftChange={(value) => {
                  setQuestionFeedback('')
                  setQuestionDraft(value)
                }}
                onSubmitQuestion={submitReaderQuestion}
              />
            )}
          </>
        ) : (
          <div className="empty-state">
            <h2>Select a node</h2>
            <p>Choose a card to inspect details, links, and source traces.</p>
          </div>
        )}
      </aside>
      )}
    </main>
  )
}

export default App

