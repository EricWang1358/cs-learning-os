import { startTransition, useEffect, useState, useDeferredValue, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import bash from 'highlight.js/lib/languages/bash'
import c from 'highlight.js/lib/languages/c'
import cpp from 'highlight.js/lib/languages/cpp'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import markdown from 'highlight.js/lib/languages/markdown'
import powershell from 'highlight.js/lib/languages/powershell'
import python from 'highlight.js/lib/languages/python'
import typescript from 'highlight.js/lib/languages/typescript'
import x86asm from 'highlight.js/lib/languages/x86asm'
import hljs from 'highlight.js/lib/core'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import './App.css'

type NodeSummary = {
  slug: string
  title: string
  area: string
  track: string
  display_order: number
  status: string
  visibility: string
  summary: string
  path: string
  updated_at: string
}

type NodeDetail = NodeSummary & {
  body: string
  tags: string[]
  links: Array<{ target: string; kind: string }>
  sources: Array<{ source: string; source_type: string; note: string }>
  open_question_count: number
}

type ApiNodesResponse = {
  nodes: NodeSummary[]
}

type ApiNodeResponse = {
  node: NodeDetail
}

type QuizSummary = {
  id: string
  title: string
  area: string
  status: string
  visibility: string
  difficulty: string
  summary: string
  path: string
  weight: number
  updated_at: string
}

type QuizDetail = QuizSummary & {
  body: string
  tags: string[]
  linked_nodes: Array<{ slug: string; kind: string; title: string }>
  sources: Array<{ source: string; source_type: string; note: string }>
  open_question_count: number
}

type ApiQuizzesResponse = {
  quizzes: QuizSummary[]
}

type ApiQuizResponse = {
  quiz: QuizDetail
}

type TrackSummary = {
  track: string
  label: string
  node_count: number
  first_order: number
}

type ApiTracksResponse = {
  area: string
  tracks: TrackSummary[]
}

type ReaderQuestion = {
  id: number
  target_type: 'node' | 'quiz'
  target_id: string
  question: string
  status: string
  created_at: string
  resolved_at: string
  resolution_note: string
}

type ApiReaderQuestionsResponse = {
  questions: ReaderQuestion[]
}

type ApiReaderQuestionResponse = {
  question: ReaderQuestion
}

type AiRevision = {
  revised_body: string
  patch_ops: Array<{
    op: 'replace' | 'append_after' | 'append_end'
    section: string
    find: string
    replace: string
  }>
  summary: string
  rationale: string[]
  changed_sections: string[]
  resolved_question_ids: number[]
  suggested_new_nodes: string[]
  model: string
  provider: string
}

type AiJob = {
  id: number
  target_type: 'node' | 'quiz'
  target_id: string
  question_ids: number[]
  provider: string
  model: string
  status: string
  stage: string
  instruction: string
  error: string
  error_summary: string
  error_code: string
  retry_of: number | null
  attempt: number
  base_body_hash: string
  created_at: string
  updated_at: string
  completed_at: string
  started_at: string
  revision?: AiRevision
}

type ApiAiJobResponse = {
  job: AiJob
}

type ApiAiJobsResponse = {
  jobs: AiJob[]
}

type AiJobEvent = {
  id: number
  job_id: number
  level: string
  stage: string
  message: string
  created_at: string
}

type ApiAiJobEventsResponse = {
  events: AiJobEvent[]
}

type SystemMetrics = {
  counts: {
    nodes: number
    quizzes: number
    open_questions: number
    active_ai_jobs: number
    failed_ai_jobs: number
  }
  storage: {
    content_bytes: number
    db_bytes: number
    generated_bytes: number
  }
  paths: {
    content: string
    db: string
    generated: string
  }
  ai: {
    ok: boolean
    message: string
    provider?: string
    checks?: Record<string, boolean>
    model?: string
    model_provider?: string
    base_url?: string
    codex_home?: string
  }
}

type ApiSystemMetricsResponse = SystemMetrics

type GraphItem = {
  type: 'root' | 'area' | 'track' | 'node' | 'heading'
  id: string
  label: string
  meta: string
  hint: string
  child_count: number
  has_children: boolean
  href: string
  level?: number
}

type GraphPayload = {
  center: GraphItem
  path: GraphItem[]
  children: GraphItem[]
  pagination: {
    page: number
    page_size: number
    total: number
    total_pages: number
    has_prev: boolean
    has_next: boolean
  }
  actions: Array<{ kind: string; label: string; href: string }>
}

type ApiGraphResponse = GraphPayload

type ApiErrorBody = {
  detail?: string
}

class ApiRequestError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
  }
}

type ViewMode = 'nodes' | 'quizzes' | 'question-queue' | 'graph' | 'health'
type AiDraftScope = 'question' | 'selected' | 'page'

type TocItem = {
  id: string
  level: 1 | 2 | 3
  text: string
}

type ParsedHeading = TocItem & {
  lineNumber: number
}

const codeHighlightLanguages = {
  asm: x86asm,
  bash,
  c,
  cpp,
  javascript,
  json,
  markdown,
  powershell,
  python,
  sh: bash,
  shell: bash,
  ts: typescript,
  typescript,
  x86asm,
}

Object.entries(codeHighlightLanguages).forEach(([name, language]) => {
  hljs.registerLanguage(name, language)
})

type LineDiffRow = {
  kind: 'same' | 'added' | 'removed'
  text: string
}

type LineDiffSummary = {
  added: number
  removed: number
  rows: LineDiffRow[]
}

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://127.0.0.1:8000'

const areaLabels: Record<string, string> = {
  all: 'All nodes',
  algorithms: 'Algorithms',
  projects: 'Projects',
  abilities: 'Abilities',
  'cs-fundamentals': 'CS fundamentals',
  tools: 'Tools',
  questions: 'Questions',
  archive: 'Archive',
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

function routeFromLocation(pathname: string, search: string) {
  const params = new URLSearchParams(search)
  const nodeMatch = pathname.match(/^\/nodes\/([^/]+)$/)
  const quizMatch = pathname.match(/^\/quizzes\/([^/]+)$/)
  const isQuizList = pathname === '/quizzes'
  const isQueue = pathname === '/queue'
  const isGraph = pathname === '/graph' || pathname.startsWith('/graph/')
  const isHealth = pathname === '/health'

  return {
    viewMode: isGraph
      ? 'graph' as ViewMode
      : isHealth
        ? 'health' as ViewMode
        : isQueue
          ? 'question-queue' as ViewMode
          : quizMatch || isQuizList
            ? 'quizzes' as ViewMode
            : 'nodes' as ViewMode,
    selectedSlug: nodeMatch ? decodeURIComponent(nodeMatch[1]) : '',
    selectedQuizId: quizMatch ? decodeURIComponent(quizMatch[1]) : '',
    activeArea: params.get('area') || 'all',
    activeTrack: params.get('track') || 'all',
    query: params.get('q') || '',
    graphPage: Number(params.get('page') || '1'),
    isFocusMode: params.get('focus') === '1',
  }
}

function routeSearch(options: {
  activeArea?: string
  activeTrack?: string
  query?: string
  isFocusMode?: boolean
  page?: number
}) {
  const params = new URLSearchParams()
  if (options.activeArea && options.activeArea !== 'all') params.set('area', options.activeArea)
  if (options.activeTrack && options.activeTrack !== 'all') params.set('track', options.activeTrack)
  if (options.query) params.set('q', options.query)
  if (options.isFocusMode) params.set('focus', '1')
  if (options.page && options.page > 1) params.set('page', String(options.page))
  const value = params.toString()
  return value ? `?${value}` : ''
}

function graphApiPath(pathname: string, page: number) {
  const search = routeSearch({ page })
  if (pathname === '/graph') return `/api/graph${search}`
  return `/api${pathname}${search}`
}

function slugTitle(slug: string) {
  return slug
    .split('-')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
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

function plainMarkdownText(text: string) {
  return text.replace(/`([^`]+)`/g, '$1').replace(/\*\*([^*]+?)\*\*/g, '$1')
}

function reactNodeText(value: ReactNode): string {
  if (value === null || value === undefined || typeof value === 'boolean') return ''
  if (typeof value === 'string' || typeof value === 'number') return String(value)
  if (Array.isArray(value)) return value.map(reactNodeText).join('')
  if (typeof value === 'object' && 'props' in value) {
    const props = value.props as { children?: ReactNode }
    return reactNodeText(props.children)
  }
  return ''
}

function headingId(text: string, index: number) {
  const base = plainMarkdownText(text)
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fff]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return `section-${base || 'heading'}-${index}`
}

function markdownNodeLine(node: unknown) {
  const positionedNode = node as { position?: { start?: { line?: number } } }
  return positionedNode.position?.start?.line
}

function parseMarkdownHeadings(body: string): ParsedHeading[] {
  let headingIndex = 0
  return body.split('\n').flatMap((line, lineIndex) => {
    const heading = line.trim().match(/^(#{1,3})\s+(.+)$/)
    if (!heading) return []
    const text = plainMarkdownText(heading[2].trim())
    const item = {
      id: headingId(text, headingIndex),
      level: heading[1].length as 1 | 2 | 3,
      lineNumber: lineIndex + 1,
      text,
    }
    headingIndex += 1
    return [item]
  })
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

async function fetchJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`)
  if (!response.ok) {
    throw new ApiRequestError(response.status, await responseErrorMessage(response))
  }
  return response.json() as Promise<T>
}

async function postJson<T>(path: string, payload: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    throw new ApiRequestError(response.status, await responseErrorMessage(response))
  }
  return response.json() as Promise<T>
}

async function putJson<T>(path: string, payload: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    throw new ApiRequestError(response.status, await responseErrorMessage(response))
  }
  return response.json() as Promise<T>
}

async function deleteJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    throw new ApiRequestError(response.status, await responseErrorMessage(response))
  }
  return response.json() as Promise<T>
}

async function responseErrorMessage(response: Response) {
  try {
    const body = (await response.json()) as ApiErrorBody
    if (body.detail) {
      return `Request failed ${response.status}: ${body.detail}`
    }
  } catch {
    // Fall through to the generic message when the backend did not return JSON.
  }
  return `Request failed: ${response.status} ${response.statusText}`.trim()
}

function MarkdownView({ body }: { body: string }) {
  const headingIdsByLine = new Map(parseMarkdownHeadings(body).map((heading) => [heading.lineNumber, heading.id]))

  return (
    <div className="markdown-body">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          code({ children, className }) {
            const language = /language-([a-zA-Z0-9_-]+)/.exec(className ?? '')?.[1]
            const code = reactNodeText(children).replace(/\n$/, '')
            if (!language || !hljs.getLanguage(language)) {
              return <code className={className}>{children}</code>
            }
            const highlighted = hljs.highlight(code, { language, ignoreIllegals: true }).value
            return (
              <code
                className={`hljs language-${language}`}
                dangerouslySetInnerHTML={{ __html: highlighted }}
              />
            )
          },
          h1({ children, node }) {
            const text = plainMarkdownText(reactNodeText(children))
            const id = headingIdsByLine.get(markdownNodeLine(node) ?? -1) ?? headingId(text, 0)
            return <h1 id={id}>{children}</h1>
          },
          h2({ children, node }) {
            const text = plainMarkdownText(reactNodeText(children))
            const id = headingIdsByLine.get(markdownNodeLine(node) ?? -1) ?? headingId(text, 0)
            return <h2 id={id}>{children}</h2>
          },
          h3({ children, node }) {
            const text = plainMarkdownText(reactNodeText(children))
            const id = headingIdsByLine.get(markdownNodeLine(node) ?? -1) ?? headingId(text, 0)
            return <h3 id={id}>{children}</h3>
          },
        }}
      >
        {body}
      </ReactMarkdown>
    </div>
  )
}

function buildTableOfContents(body: string): TocItem[] {
  return parseMarkdownHeadings(body).map(({ id, level, text }) => ({ id, level, text }))
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
}: {
  message: string
  onQueue: () => void
  onFreshDraft: () => void
  isBusy: boolean
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
        <button type="button" className="focus-toggle ai-action" disabled={isBusy} onClick={onFreshDraft}>
          {isBusy ? 'Creating...' : 'Create fresh draft'}
        </button>
      </div>
    </section>
  )
}

function graphChildPosition(index: number, total: number) {
  const slots = Math.max(total, 1)
  const angle = slots === 1 ? -90 : -90 + (index * 360) / slots
  const radiusX = 38
  const radiusY = 30
  const x = 50 + Math.cos((angle * Math.PI) / 180) * radiusX
  const y = 53 + Math.sin((angle * Math.PI) / 180) * radiusY
  return { x, y, style: { left: `${x}%`, top: `${y}%` } }
}

function GraphNavigator({
  payload,
  isLoading,
  error,
  onNavigate,
  onPage,
}: {
  payload: GraphPayload | null
  isLoading: boolean
  error: string
  onNavigate: (href: string) => void
  onPage: (page: number) => void
}) {
  if (isLoading && !payload) {
    return (
      <section className="graph-navigator-shell" aria-label="Knowledge graph navigator">
        <p className="detail-loading">Loading graph navigator...</p>
      </section>
    )
  }

  if (!payload) {
    return (
      <section className="graph-navigator-shell" aria-label="Knowledge graph navigator">
        <div className="empty-state">
          <h2>Graph unavailable</h2>
          <p>{error || 'The graph endpoint did not return a payload.'}</p>
        </div>
      </section>
    )
  }

  const { center, children, pagination, path, actions } = payload

  return (
    <section className="graph-navigator-shell" aria-label="Knowledge graph navigator">
      <header className="graph-control-bar">
        <button type="button" className="focus-toggle" onClick={() => onNavigate('/graph')}>
          Workbench
        </button>
        <nav className="graph-breadcrumb" aria-label="Graph breadcrumb">
          {path.map((item, index) => (
            <button
              key={`${item.type}-${item.id}-${index}`}
              type="button"
              disabled={!item.href}
              onClick={() => item.href && onNavigate(item.href)}
            >
              {item.label}
            </button>
          ))}
        </nav>
        <div className="graph-action-row">
          {actions.map((action) => (
            <button
              key={`${action.kind}-${action.href}`}
              type="button"
              className="focus-toggle ai-action"
              onClick={() => onNavigate(action.href)}
            >
              {action.label}
            </button>
          ))}
        </div>
      </header>

      {error && <p className="error-banner">{error}</p>}

      <div className="graph-canvas" data-level={center.type}>
        <div className="graph-axis">
          <span>Workbench</span>
        </div>
        <svg className="graph-link-layer" aria-hidden="true" viewBox="0 0 100 100" preserveAspectRatio="none">
          {children.map((item, index) => {
            const position = graphChildPosition(index, children.length)
            return (
              <line
                key={`link-${item.type}-${item.id}`}
                x1="50"
                y1="53"
                x2={position.x}
                y2={position.y}
              />
            )
          })}
        </svg>
        <article className="graph-center-card">
          <p className="eyebrow">{center.meta}</p>
          <h2>{center.label}</h2>
          <p>{center.hint}</p>
          {center.child_count > 0 && <strong>{center.child_count} linked entries</strong>}
        </article>
        {children.map((item, index) => {
          const position = graphChildPosition(index, children.length)
          const canRead = item.type === 'node'
          return (
            <article
              className={`graph-child-card ${item.type}`}
              key={`${item.type}-${item.id}`}
              style={position.style}
            >
              <button
                type="button"
                className="graph-child-main"
                onClick={() => onNavigate(item.href)}
              >
                <span>{item.meta}</span>
                <strong>{item.label}</strong>
                <small>{item.hint}</small>
              </button>
              {canRead && (
                <button
                  type="button"
                  className="text-link"
                  onClick={() => onNavigate(`/nodes/${encodeURIComponent(item.id)}?focus=1`)}
                >
                  Read
                </button>
              )}
            </article>
          )
        })}
      </div>

      <footer className="graph-pagination">
        <span>
          {pagination.total === 0
            ? 'No entries'
            : `${children.length} visible of ${pagination.total} · page ${pagination.page}/${pagination.total_pages}`}
        </span>
        <div>
          <button
            type="button"
            className="focus-toggle"
            disabled={!pagination.has_prev}
            onClick={() => onPage(pagination.page - 1)}
          >
            Prev
          </button>
          <button
            type="button"
            className="focus-toggle"
            disabled={!pagination.has_next}
            onClick={() => onPage(pagination.page + 1)}
          >
            Next
          </button>
        </div>
      </footer>
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
  const graphPage = routeState.graphPage
  const isFocusMode = routeState.isFocusMode
  const [nodes, setNodes] = useState<NodeSummary[]>([])
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
  const [isEditSaving, setIsEditSaving] = useState(false)
  const [isAiRevising, setIsAiRevising] = useState(false)
  const [aiRevision, setAiRevision] = useState<AiRevision | null>(null)
  const [aiStatus, setAiStatus] = useState('')
  const [aiElapsedSeconds, setAiElapsedSeconds] = useState(0)
  const [draftConflict, setDraftConflict] = useState('')
  const [activeAiJob, setActiveAiJob] = useState<AiJob | null>(null)
  const [selectedQuestionIds, setSelectedQuestionIds] = useState<number[]>([])
  const [questionScopes, setQuestionScopes] = useState<Record<number, AiDraftScope>>({})
  const [jobEvents, setJobEvents] = useState<Record<number, AiJobEvent[]>>({})
  const [systemMetrics, setSystemMetrics] = useState<SystemMetrics | null>(null)
  const [graphPayload, setGraphPayload] = useState<GraphPayload | null>(null)
  const [graphCache, setGraphCache] = useState<Record<string, GraphPayload>>({})

  const deferredQuery = useDeferredValue(query)

  const navigateToNode = (slug: string, options: { focus?: boolean; replace?: boolean } = {}) => {
    navigate(
      `/nodes/${encodeURIComponent(slug)}${routeSearch({
        activeArea,
        activeTrack,
        query,
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
        isFocusMode: options.focus ?? isFocusMode,
      })}`,
      { replace: options.replace },
    )
  }

  const navigateToQueue = () => {
    navigate(`/queue${routeSearch({ query })}`)
  }

  const navigateToArea = (area: string) => {
    const targetSlug = selectedSlug || nodes[0]?.slug
    if (targetSlug) {
      navigate(`/nodes/${encodeURIComponent(targetSlug)}${routeSearch({ activeArea: area })}`)
    } else {
      navigate(`/nodes${routeSearch({ activeArea: area })}`)
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
          const data = deferredQuery.trim()
            ? await fetchJson<ApiQuizzesResponse>(
                `/api/quiz-search?q=${encodeURIComponent(deferredQuery.trim())}`,
              )
            : await fetchJson<ApiQuizzesResponse>('/api/quizzes')

          if (!isActive) return

          startTransition(() => {
            setQuizzes(data.quizzes)
            if (location.pathname === '/quizzes' && data.quizzes[0]?.id) {
              navigate(
                `/quizzes/${encodeURIComponent(data.quizzes[0].id)}${routeSearch({
                  activeArea,
                  activeTrack,
                  query,
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
        } else if (viewMode === 'graph') {
          const cacheKey = `${location.pathname}?page=${graphPage}`
          const cached = graphCache[cacheKey]
          if (cached) {
            setGraphPayload(cached)
            return
          }
          const data = await fetchJson<ApiGraphResponse>(graphApiPath(location.pathname, graphPage))

          if (!isActive) return

          startTransition(() => {
            setGraphPayload(data)
            setGraphCache((current) => ({ ...current, [cacheKey]: data }))
          })
        } else if (viewMode === 'health') {
          const metricsData = await fetchJson<ApiSystemMetricsResponse>('/api/system/metrics')

          if (!isActive) return

          startTransition(() => {
            setSystemMetrics(metricsData)
          })
        } else {
          const data = deferredQuery.trim()
            ? await fetchJson<ApiNodesResponse>(
                `/api/search?q=${encodeURIComponent(deferredQuery.trim())}`,
              )
            : await fetchJson<ApiNodesResponse>('/api/nodes')

          if (!isActive) return

          startTransition(() => {
            setNodes(data.nodes)
            if ((location.pathname === '/' || location.pathname === '/nodes') && data.nodes[0]?.slug) {
              navigate(
                `/nodes/${encodeURIComponent(data.nodes[0].slug)}${routeSearch({
                  activeArea,
                  activeTrack,
                  query,
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
  }, [activeArea, activeTrack, deferredQuery, graphCache, graphPage, isFocusMode, location.pathname, navigate, query, viewMode])

  useEffect(() => {
    if (viewMode !== 'nodes' || activeArea === 'all' || activeArea === 'archive') {
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
  }, [activeArea, activeTrack, isFocusMode, navigate, nodes, query, selectedSlug, viewMode])

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
      if (!scrollToLocationHash()) {
        scrollDetailToTop()
      }
    })
  }, [
    location.hash,
    location.pathname,
    location.search,
    selectedNode?.body,
    selectedNode?.slug,
    selectedQuiz?.body,
    selectedQuiz?.id,
    selectedQuizId,
    selectedSlug,
    viewMode,
  ])

  useEffect(() => {
    if (!activeAiJob || !['queued', 'solving'].includes(activeAiJob.status)) {
      return
    }

    const startedAt = Date.now()
    const timer = window.setInterval(() => {
      setAiElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000))
    }, 1000)

    return () => window.clearInterval(timer)
  }, [activeAiJob])

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
    if (activeArea === 'archive') return node.visibility === 'archive'
    if (activeArea === 'all') return node.visibility !== 'archive'
    return (
      node.area === activeArea &&
      node.visibility !== 'archive' &&
      (activeTrack === 'all' || node.track === activeTrack)
    )
  })

  const filteredQuizzes = quizzes.filter((quiz) => {
    if (activeArea === 'archive') return quiz.visibility === 'archive'
    if (activeArea === 'all') return quiz.visibility !== 'archive'
    return quiz.area === activeArea && quiz.visibility !== 'archive'
  })

  const totalStorageBytes = systemMetrics
    ? systemMetrics.storage.content_bytes + systemMetrics.storage.db_bytes + systemMetrics.storage.generated_bytes
    : 0

  const activeAiJobs = aiJobs.filter((job) =>
    ['queued', 'solving', 'draft_ready', 'failed'].includes(job.status),
  )
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

  const visibleCount =
    viewMode === 'question-queue'
      ? queueItems.length
      : viewMode === 'graph'
        ? graphPayload?.children.length ?? 0
        : viewMode === 'health'
          ? systemMetrics
            ? 1
            : 0
      : viewMode === 'quizzes'
        ? filteredQuizzes.length
        : filteredNodes.length
  const totalCount =
    viewMode === 'question-queue'
      ? totalQueueItems
      : viewMode === 'graph'
        ? graphPayload?.pagination.total ?? 0
        : viewMode === 'health'
          ? 1
      : viewMode === 'quizzes'
        ? quizzes.length
        : nodes.length
  const visibleTracks =
    viewMode === 'nodes' && activeArea !== 'all' && activeArea !== 'archive' ? tracks : []
  const isAiJobRunning = Boolean(activeAiJob && ['queued', 'solving'].includes(activeAiJob.status))
  const aiStatusText = isAiJobRunning
    ? `${aiStatus} ${aiElapsedSeconds}s elapsed. Job #${activeAiJob?.id} is persisted; no refresh needed. Open Q Queue when the draft is ready.`
    : aiStatus
  const aiStatusClass = isAiJobRunning ? 'ai-status running' : 'ai-status error'

  const exitEditMode = (shouldConfirm = true) => {
    if (!isEditMode) return true
    if (shouldConfirm && editDraft.trim() && !window.confirm('Discard unsaved Markdown edits?')) {
      return false
    }
    setIsEditMode(false)
    setEditDraft('')
    setAiRevision(null)
    setAiStatus('')
    setAiElapsedSeconds(0)
    setDraftConflict('')
    return true
  }

  const openQuestionTarget = (item: ReaderQuestion) => {
    exitEditMode(false)
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
    exitEditMode(false)
    if (job.target_type === 'node') {
      navigateToNode(job.target_id, { focus: true })
    } else {
      navigateToQuiz(job.target_id, { focus: true })
    }
  }

  const goBack = () => {
    if (!exitEditMode()) return
    navigate(-1)
  }

  const navigateGraph = (href: string) => {
    if (!exitEditMode()) return
    navigate(href)
  }

  const navigateGraphPage = (page: number) => {
    if (!exitEditMode()) return
    navigate(`${location.pathname}${routeSearch({ page })}`)
  }

  const dismissReaderQuestion = async (item: ReaderQuestion) => {
    try {
      await postJson<ApiReaderQuestionResponse>(`/api/reader-questions/${item.id}/dismiss`, {
        resolution_note: 'Dismissed from Q Queue',
      })
      setReaderQuestions((current) => current.filter((question) => question.id !== item.id))
    } catch (dismissError) {
      setError(dismissError instanceof Error ? dismissError.message : 'Unable to dismiss question')
    }
  }

  const deleteReaderQuestion = async (item: ReaderQuestion) => {
    if (!window.confirm(`Delete Q #${item.id}?`)) return
    try {
      await deleteJson<{ ok: boolean }>(`/api/reader-questions/${item.id}`)
      setReaderQuestions((current) => current.filter((question) => question.id !== item.id))
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
    try {
      setAiStatus(`Retrying job #${job.id}...`)
      const data = await postJson<ApiAiJobResponse>(`/api/ai/jobs/${job.id}/retry`, {})
      setAiJobs((current) => [data.job, ...current.filter((item) => item.id !== job.id)])
      setActiveAiJob(data.job)
      setAiElapsedSeconds(0)
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
    if (!question || !targetId) return

    try {
      setIsQuestionSaving(true)
      const data = await postJson<ApiReaderQuestionResponse>('/api/reader-questions', {
        target_type: targetType,
        target_id: targetId,
        question,
      })
      setReaderQuestions((current) => [data.question, ...current])
      setQuestionDraft('')
      if (targetType === 'node') {
        setSelectedNode((current) =>
          current
            ? { ...current, open_question_count: current.open_question_count + 1 }
            : current,
        )
      } else {
        setSelectedQuiz((current) =>
          current
            ? { ...current, open_question_count: current.open_question_count + 1 }
            : current,
        )
      }
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to save reader question')
    } finally {
      setIsQuestionSaving(false)
    }
  }

  const requestAiRevision = async () => {
    const targetType = viewMode === 'quizzes' ? 'quiz' : 'node'
    const targetId = viewMode === 'quizzes' ? selectedQuizId : selectedSlug
    const currentBody = viewMode === 'quizzes' ? selectedQuiz?.body : selectedNode?.body
    if (!targetId || !currentBody || isAiRevising || isAiJobRunning) return

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
      setAiRevision(null)
      setAiElapsedSeconds(0)
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
    const body = viewMode === 'quizzes' ? selectedQuiz?.body : selectedNode?.body
    if (!body) return
    setEditDraft(body)
    setAiRevision(null)
    setAiStatus('')
    setDraftConflict('')
    if (!isFocusMode) {
      toggleFocusRoute()
    }
    setIsEditMode(true)
  }

  const cancelEditMode = () => {
    exitEditMode()
  }

  const saveEditMode = async () => {
    const body = editDraft.trim()
    if (!body) return
    if (!window.confirm('Save these Markdown changes to the local source file?')) return

    try {
      setIsEditSaving(true)
      if (activeAiJob?.status === 'draft_ready') {
        const applied = await postJson<ApiAiJobResponse>(`/api/ai/jobs/${activeAiJob.id}/apply`, {
          body,
        })
        setAiJobs((current) =>
          current.map((item) => (item.id === activeAiJob.id ? applied.job : item)),
        )
        if (viewMode === 'quizzes') {
          setSelectedQuiz((current) => (current ? { ...current, body } : current))
        } else {
          setSelectedNode((current) => (current ? { ...current, body } : current))
        }
        setReaderQuestions((current) =>
          current.filter((item) => !activeAiJob.question_ids.includes(item.id)),
        )
        setActiveAiJob(null)
      } else {
        if (viewMode === 'quizzes' && selectedQuizId) {
          const data = await putJson<ApiQuizResponse>(`/api/quizzes/${selectedQuizId}/body`, {
            body,
          })
          setSelectedQuiz(data.quiz)
        } else if (selectedSlug) {
          const data = await putJson<ApiNodeResponse>(`/api/nodes/${selectedSlug}/body`, {
            body,
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
        setError(message)
      }
    } finally {
      setIsEditSaving(false)
    }
  }

  const openQuestionCount =
    viewMode === 'quizzes'
      ? selectedQuiz?.open_question_count ?? 0
      : selectedNode?.open_question_count ?? 0
  const visibleReaderQuestions = readerQuestions
  const currentBodyForDiff = viewMode === 'quizzes' ? selectedQuiz?.body ?? '' : selectedNode?.body ?? ''
  const aiDraftDiff = aiRevision
    ? buildLineDiffSummary(currentBodyForDiff, aiRevision.revised_body)
    : null

  return (
    <main className={`workspace-shell ${isFocusMode ? 'focus-mode' : ''} ${isEditMode ? 'editing-mode' : ''} ${viewMode === 'graph' ? 'graph-mode' : ''}`}>
      <aside className="sidebar" aria-label="Knowledge areas">
        <div className="brand-block">
          <p className="eyebrow">CS Learning OS</p>
          <h1>Knowledge Workbench</h1>
        </div>

        <nav className="area-nav">
          {['all', ...stableAreas, 'archive'].map((area) => (
            <button
              key={area}
              type="button"
              className={area === activeArea ? 'active' : ''}
              onClick={() => {
                if (!exitEditMode()) return
                navigateToArea(area)
              }}
            >
              <span>{areaLabels[area] ?? area}</span>
              <strong>
                {area === 'all'
                  ? nodes.filter((node) => node.visibility !== 'archive').length
                  : area === 'archive'
                    ? nodes.filter((node) => node.visibility === 'archive').length
                    : nodes.filter((node) => node.area === area).length}
              </strong>
            </button>
          ))}
        </nav>

        <section className="practice-switch">
          <p className="eyebrow">Review system</p>
          <button
            type="button"
            className={viewMode === 'quizzes' ? 'active' : ''}
            onClick={() => {
              if (!exitEditMode()) return
              navigate(`/quizzes${routeSearch({ query })}`)
            }}
          >
            Practice / Quiz Bank
          </button>
          <button
            type="button"
            className={viewMode === 'question-queue' ? 'active' : ''}
            onClick={() => {
              if (!exitEditMode()) return
              navigateToQueue()
            }}
          >
            Q Queue
          </button>
        </section>

        <section className="system-note">
          <h2>Current loop</h2>
          <p>Markdown stays source of truth. SQLite powers search. React is the daily surface.</p>
          <div className="loop-actions">
            <button
              type="button"
              className={viewMode === 'graph' ? 'active' : ''}
              onClick={() => {
                if (!exitEditMode()) return
                navigate('/graph')
              }}
            >
              Knowledge navigator
            </button>
            <button
              type="button"
              className={viewMode === 'health' ? 'active' : ''}
              onClick={() => {
                if (!exitEditMode()) return
                navigate('/health')
              }}
            >
              System health
            </button>
          </div>
        </section>
      </aside>

      {viewMode === 'graph' ? (
        <GraphNavigator
          payload={graphPayload}
          isLoading={isLoading}
          error={error}
          onNavigate={navigateGraph}
          onPage={navigateGraphPage}
        />
      ) : (
      <section className="node-column" aria-label="Knowledge nodes">
        <header className="search-header">
          <label htmlFor="node-search">
            {viewMode === 'question-queue'
              ? 'Question queue'
              : viewMode === 'health'
                  ? 'Program health'
              : viewMode === 'quizzes'
                ? 'Quiz search'
                : 'Global search'}
          </label>
          <input
            id="node-search"
            value={query}
            onChange={(event) => {
              const nextQuery = event.target.value
              if (viewMode === 'quizzes') {
                navigate(
                  `${selectedQuizId ? `/quizzes/${encodeURIComponent(selectedQuizId)}` : '/quizzes'}${routeSearch({
                    activeArea,
                    activeTrack,
                    query: nextQuery,
                    isFocusMode,
                  })}`,
                  { replace: true },
                )
              } else if (viewMode === 'question-queue') {
                navigate(`/queue${routeSearch({ query: nextQuery })}`, { replace: true })
              } else if (viewMode === 'health') {
                navigate('/health', { replace: true })
              } else {
                navigate(
                  `${selectedSlug ? `/nodes/${encodeURIComponent(selectedSlug)}` : '/nodes'}${routeSearch({
                    activeArea,
                    activeTrack,
                    query: nextQuery,
                    isFocusMode,
                  })}`,
                  { replace: true },
                )
              }
            }}
            placeholder={
              viewMode === 'question-queue'
                ? 'Open questions are loaded directly...'
                : viewMode === 'health'
                    ? 'Health is live metrics; search is disabled here.'
                : viewMode === 'quizzes'
                ? 'Search quiz prompts, tags, answers...'
                : 'Search concepts, tags, summaries...'
            }
            disabled={viewMode === 'health'}
          />
          <p>
            {isLoading
              ? 'Loading index...'
              : `${visibleCount} visible of ${totalCount} indexed ${
                  viewMode === 'question-queue'
                    ? 'open questions'
                    : viewMode === 'health'
                        ? 'health dashboards'
                    : viewMode === 'quizzes'
                      ? 'quizzes'
                      : 'nodes'
                }`}
          </p>
        </header>

        {error && <p className="error-banner">{error}</p>}

        {viewMode === 'nodes' && visibleTracks.length > 0 && (
          <section className="track-panel" aria-label="Reading tracks">
            <div>
              <p className="eyebrow">Reading tracks</p>
              <strong>{areaLabels[activeArea] ?? activeArea}</strong>
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
                        isFocusMode,
                      })}`,
                      { replace: true },
                    )
                  }
                >
                  <span>{trackLabels[track.track] ?? track.label}</span>
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
          {viewMode === 'question-queue'
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
                              disabled={isAiRevising}
                              onClick={() => draftQuestionFromQueue(item.question)}
                            >
                              Draft
                            </button>
                          </>
                        )}
                        {linkedJob?.status === 'draft_ready' && linkedJob.revision && (
                          <button type="button" className="text-link" onClick={() => reviewAiJob(linkedJob)}>
                            Review draft
                          </button>
                        )}
                        {linkedJob?.status === 'failed' && (
                          <button type="button" className="text-link" onClick={() => retryAiJob(linkedJob)}>
                            Retry
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
                        <button type="button" className="text-link" onClick={() => retryAiJob(item.job)}>
                          Retry
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
              ? (
                  <>
                    <article className="node-card compact-card">
                      <span className="node-meta">content / markdown source</span>
                      <strong>{systemMetrics ? formatBytes(systemMetrics.storage.content_bytes) : 'Loading...'}</strong>
                      <span>{systemMetrics?.paths.content ?? 'Content directory metrics are loading.'}</span>
                    </article>
                    <article className="node-card compact-card">
                      <span className="node-meta">sqlite / local index</span>
                      <strong>{systemMetrics ? formatBytes(systemMetrics.storage.db_bytes) : 'Loading...'}</strong>
                      <span>{systemMetrics?.paths.db ?? 'SQLite path is loading.'}</span>
                    </article>
                    <article className="node-card compact-card">
                      <span className="node-meta">ai / jobs</span>
                      <strong>{systemMetrics?.counts.active_ai_jobs ?? 0} active</strong>
                      <span>
                        {systemMetrics?.ai.message ?? 'AI preflight will appear after metrics load.'}
                      </span>
                    </article>
                  </>
                )
            : viewMode === 'quizzes'
            ? filteredQuizzes.map((quiz) => (
                <button
                  key={quiz.id}
                  type="button"
                  className={`node-card ${quiz.id === selectedQuizId ? 'selected' : ''}`}
                  onClick={() => {
                    if (!exitEditMode()) return
                    navigateToQuiz(quiz.id)
                  }}
                >
                  <span className="node-meta">
                    {areaLabels[quiz.area] ?? quiz.area} / {quiz.difficulty} / weight {quiz.weight}
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
                    if (!exitEditMode()) return
                    navigateToNode(node.slug)
                  }}
                >
                  <span className="node-meta">
                    {areaLabels[node.area] ?? node.area} / {trackLabels[node.track] ?? node.track} /{' '}
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
        {viewMode === 'question-queue' ? (
          <div className="queue-detail">
            <h2>Q Queue</h2>
            <p>
              Track open questions and AI jobs together. Drafts wait here until you explicitly review and
              save them.
            </p>
            <section className="detail-section">
              <h3>Open questions</h3>
              {readerQuestions.length ? (
                <div className="job-list">
                  {readerQuestions.map((item) => (
                    <article className="ai-revision-card compact-card" key={item.id}>
                      <p className="eyebrow">
                        Q #{item.id} / {item.target_type} / {item.target_id} / {item.status}
                      </p>
                      <h3>{item.question}</h3>
                      <div className="question-card-actions">
                        <button type="button" className="focus-toggle" onClick={() => openQuestionTarget(item)}>
                          Open target
                        </button>
                        <button type="button" className="focus-toggle" onClick={() => dismissReaderQuestion(item)}>
                          Dismiss
                        </button>
                        <button type="button" className="focus-toggle danger-button" onClick={() => deleteReaderQuestion(item)}>
                          Delete
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
              ) : (
                <p>No open reader questions.</p>
              )}
            </section>
            <section className="detail-section">
              <h3>AI jobs</h3>
              {activeAiJobs.length ? (
                <div className="job-list">
                  {activeAiJobs.map((job) => (
                    <article className="ai-revision-card" key={job.id}>
                      <p className="eyebrow">
                        Job #{job.id} / {job.target_type} / {job.target_id}
                      </p>
                      <h3>{job.status}: {job.stage}</h3>
                      {job.revision?.summary && <p>{job.revision.summary}</p>}
                      {(job.error_summary || job.error) && (
                        <p className="ai-status error">{job.error_summary || job.error}</p>
                      )}
                      <div className="question-card-actions">
                        {job.status === 'draft_ready' && job.revision && (
                          <button type="button" className="focus-toggle ai-action" onClick={() => reviewAiJob(job)}>
                            Review draft
                          </button>
                        )}
                        <button type="button" className="focus-toggle" onClick={() => openJobTarget(job)}>
                          Open target
                        </button>
                        {job.status === 'failed' && (
                          <button type="button" className="focus-toggle ai-action" onClick={() => retryAiJob(job)}>
                            Retry
                          </button>
                        )}
                        {job.status === 'draft_ready' && (
                          <button type="button" className="focus-toggle" onClick={() => rejectAiJob(job)}>
                            Reject draft
                          </button>
                        )}
                        <button type="button" className="focus-toggle" onClick={() => loadJobEvents(job)}>
                          Show events
                        </button>
                        {['queued', 'solving'].includes(job.status) && (
                          <button type="button" className="focus-toggle" onClick={() => cancelAiJob(job)}>
                            Cancel
                          </button>
                        )}
                      </div>
                      {jobEvents[job.id]?.length > 0 && (
                        <ol className="job-event-list">
                          {jobEvents[job.id].map((event) => (
                            <li key={event.id}>
                              <strong>{event.stage}</strong>: {event.message}
                            </li>
                          ))}
                        </ol>
                      )}
                    </article>
                  ))}
                </div>
              ) : (
                <p>No AI jobs yet.</p>
              )}
            </section>
          </div>
        ) : viewMode === 'health' ? (
          <div className="health-detail">
            <section className="detail-heading health-heading">
              <p className="eyebrow">Program health</p>
              <h2>System Health</h2>
              <p>
                A local observability cockpit for content size, SQLite growth, AI job health, and future
                maintenance warnings.
              </p>
            </section>
            {systemMetrics ? (
              <>
                <section className="health-grid" aria-label="Storage usage">
                  <article className="health-card accent-card">
                    <p className="eyebrow">Total local footprint</p>
                    <h3>{formatBytes(totalStorageBytes)}</h3>
                    <p>Tracked across content, SQLite, and generated app artifacts.</p>
                  </article>
                  <article className="health-card">
                    <p className="eyebrow">Content</p>
                    <h3>{formatBytes(systemMetrics.storage.content_bytes)}</h3>
                    <div className="health-meter">
                      <span
                        style={{
                          width: `${totalStorageBytes ? Math.max(4, (systemMetrics.storage.content_bytes / totalStorageBytes) * 100) : 0}%`,
                        }}
                      />
                    </div>
                    <p>{systemMetrics.paths.content}</p>
                  </article>
                  <article className="health-card">
                    <p className="eyebrow">SQLite DB</p>
                    <h3>{formatBytes(systemMetrics.storage.db_bytes)}</h3>
                    <div className="health-meter">
                      <span
                        style={{
                          width: `${totalStorageBytes ? Math.max(4, (systemMetrics.storage.db_bytes / totalStorageBytes) * 100) : 0}%`,
                        }}
                      />
                    </div>
                    <p>{systemMetrics.paths.db}</p>
                  </article>
                  <article className="health-card">
                    <p className="eyebrow">Generated</p>
                    <h3>{formatBytes(systemMetrics.storage.generated_bytes)}</h3>
                    <div className="health-meter">
                      <span
                        style={{
                          width: `${totalStorageBytes ? Math.max(4, (systemMetrics.storage.generated_bytes / totalStorageBytes) * 100) : 0}%`,
                        }}
                      />
                    </div>
                    <p>{systemMetrics.paths.generated}</p>
                  </article>
                </section>
                <section className="detail-section split">
                  <div className="health-card">
                    <p className="eyebrow">Knowledge inventory</p>
                    <ul className="metric-list">
                      <li><strong>{systemMetrics.counts.nodes}</strong> nodes</li>
                      <li><strong>{systemMetrics.counts.quizzes}</strong> quizzes</li>
                      <li><strong>{systemMetrics.counts.open_questions}</strong> open reader questions</li>
                    </ul>
                  </div>
                  <div className="health-card">
                    <p className="eyebrow">AI workflow</p>
                    <ul className="metric-list">
                      <li><strong>{systemMetrics.counts.active_ai_jobs}</strong> active jobs</li>
                      <li><strong>{systemMetrics.counts.failed_ai_jobs}</strong> failed jobs</li>
                      <li><strong>{systemMetrics.ai.ok ? 'Ready' : 'Needs setup'}</strong> {systemMetrics.ai.message}</li>
                    </ul>
                  </div>
                </section>
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
                      onChange={(event) => setEditDraft(event.target.value)}
                      aria-label="Markdown editor"
                    />
                    <div className="editor-actions">
                      <button
                        type="button"
                        className="focus-toggle"
                        disabled={!editDraft.trim() || isEditSaving}
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
                      />
                    )}
                    {aiRevision && <AiRevisionCard revision={aiRevision} diff={aiDraftDiff} />}
                    {aiStatus && !aiRevision && <p className={aiStatusClass}>{aiStatusText}</p>}
                  </div>
                ) : (
                  <MarkdownView body={selectedQuiz.body} />
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
                              if (!exitEditMode()) return
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
              <aside className="reader-question-panel" aria-label="Reader questions">
                <div>
                  <p className="eyebrow">Q to be solved</p>
                  <h3>What is unclear?</h3>
                  <p>
                    Save questions while reading. Later, ask the LLM to fold them back into the
                    tutorial.
                  </p>
                </div>
                <textarea
                  value={questionDraft}
                  onChange={(event) => setQuestionDraft(event.target.value)}
                  placeholder="Example: Why does sete write only %al here?"
                />
                <button
                  type="button"
                  className="focus-toggle"
                  disabled={!questionDraft.trim() || isQuestionSaving}
                  onClick={submitReaderQuestion}
                >
                  {isQuestionSaving ? 'Saving...' : 'Save question'}
                </button>
                <button
                  type="button"
                  className="focus-toggle ai-action"
                  disabled={isAiRevising || (!visibleReaderQuestions.length && !questionDraft.trim())}
                  onClick={requestAiRevision}
                >
                  {isAiRevising ? 'Drafting...' : 'Draft with AI'}
                </button>
                {aiStatus && <p className={aiStatusClass}>{aiStatusText}</p>}
                <div className="reader-question-list">
                  <strong>{openQuestionCount} open</strong>
                  {visibleReaderQuestions.map((item) => (
                    <p key={item.id}>{item.question}</p>
                  ))}
                </div>
              </aside>
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
                      onChange={(event) => setEditDraft(event.target.value)}
                      aria-label="Markdown editor"
                    />
                    <div className="editor-actions">
                      <button
                        type="button"
                        className="focus-toggle"
                        disabled={!editDraft.trim() || isEditSaving}
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
                      />
                    )}
                    {aiRevision && <AiRevisionCard revision={aiRevision} diff={aiDraftDiff} />}
                    {aiStatus && !aiRevision && <p className={aiStatusClass}>{aiStatusText}</p>}
                  </div>
                ) : (
                  <MarkdownView body={selectedNode.body} />
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
                              if (!exitEditMode()) return
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

              {isDetailLoading && <p className="detail-loading">Refreshing detail...</p>}
            </div>
            {isFocusMode && !isEditMode && (
              <aside className="reader-question-panel" aria-label="Reader questions">
                <div>
                  <p className="eyebrow">Q to be solved</p>
                  <h3>What is unclear?</h3>
                  <p>
                    Save questions while reading. Later, ask the LLM to fold them back into the
                    tutorial.
                  </p>
                </div>
                <textarea
                  value={questionDraft}
                  onChange={(event) => setQuestionDraft(event.target.value)}
                  placeholder="Example: This explanation skips why %eax changes here."
                />
                <button
                  type="button"
                  className="focus-toggle"
                  disabled={!questionDraft.trim() || isQuestionSaving}
                  onClick={submitReaderQuestion}
                >
                  {isQuestionSaving ? 'Saving...' : 'Save question'}
                </button>
                <button
                  type="button"
                  className="focus-toggle ai-action"
                  disabled={isAiRevising || (!visibleReaderQuestions.length && !questionDraft.trim())}
                  onClick={requestAiRevision}
                >
                  {isAiRevising ? 'Drafting...' : 'Draft with AI'}
                </button>
                {aiStatus && <p className={aiStatusClass}>{aiStatusText}</p>}
                <div className="reader-question-list">
                  <strong>{openQuestionCount} open</strong>
                  {visibleReaderQuestions.map((item) => (
                    <p key={item.id}>{item.question}</p>
                  ))}
                </div>
              </aside>
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
