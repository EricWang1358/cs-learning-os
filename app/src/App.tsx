import { startTransition, useEffect, useState, useDeferredValue } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
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
  created_at: string
  updated_at: string
  completed_at: string
  revision?: AiRevision
}

type ApiAiJobResponse = {
  job: AiJob
}

type ApiAiJobsResponse = {
  jobs: AiJob[]
}

type ApiErrorBody = {
  detail?: string
}

type ViewMode = 'nodes' | 'quizzes' | 'question-queue'

type MarkdownBlock =
  | { kind: 'code'; code: string; language: string }
  | { kind: 'heading'; level: 1 | 2 | 3; text: string }
  | { kind: 'paragraph'; text: string }
  | { kind: 'list'; items: string[] }

type TocItem = {
  id: string
  level: 1 | 2 | 3
  text: string
}

type RenderMarkdownBlock = {
  block: MarkdownBlock
  id: string
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

  return {
    viewMode: isQueue ? 'question-queue' as ViewMode : quizMatch || isQuizList ? 'quizzes' as ViewMode : 'nodes' as ViewMode,
    selectedSlug: nodeMatch ? decodeURIComponent(nodeMatch[1]) : '',
    selectedQuizId: quizMatch ? decodeURIComponent(quizMatch[1]) : '',
    activeArea: params.get('area') || 'all',
    activeTrack: params.get('track') || 'all',
    query: params.get('q') || '',
    isFocusMode: params.get('focus') === '1',
  }
}

function routeSearch(options: {
  activeArea?: string
  activeTrack?: string
  query?: string
  isFocusMode?: boolean
}) {
  const params = new URLSearchParams()
  if (options.activeArea && options.activeArea !== 'all') params.set('area', options.activeArea)
  if (options.activeTrack && options.activeTrack !== 'all') params.set('track', options.activeTrack)
  if (options.query) params.set('q', options.query)
  if (options.isFocusMode) params.set('focus', '1')
  const value = params.toString()
  return value ? `?${value}` : ''
}

function slugTitle(slug: string) {
  return slug
    .split('-')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function plainMarkdownText(text: string) {
  return text.replace(/`([^`]+)`/g, '$1').replace(/\*\*([^*]+?)\*\*/g, '$1')
}

function headingId(text: string, index: number) {
  const base = plainMarkdownText(text)
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fff]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return `section-${base || 'heading'}-${index}`
}

function withHeadingIds(blocks: MarkdownBlock[]): RenderMarkdownBlock[] {
  const renderBlocks: RenderMarkdownBlock[] = []
  let headingIndex = 0

  for (const block of blocks) {
    if (block.kind === 'heading') {
      renderBlocks.push({ block, id: headingId(block.text, headingIndex) })
      headingIndex += 1
    } else {
      renderBlocks.push({ block, id: '' })
    }
  }

  return renderBlocks
}

async function fetchJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`)
  if (!response.ok) {
    throw new Error(await responseErrorMessage(response))
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
    throw new Error(await responseErrorMessage(response))
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
    throw new Error(await responseErrorMessage(response))
  }
  return response.json() as Promise<T>
}

async function deleteJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    throw new Error(await responseErrorMessage(response))
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

function parseMarkdownBlocks(body: string): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = []
  const lines = body.split('\n')
  let paragraphBuffer: string[] = []
  let listBuffer: string[] = []
  let codeBuffer: string[] = []
  let codeLanguage = ''
  let isInCodeBlock = false

  const flushParagraph = () => {
    const text = paragraphBuffer.join(' ').replace(/\s+/g, ' ').trim()
    if (text) blocks.push({ kind: 'paragraph', text })
    paragraphBuffer = []
  }

  const flushList = () => {
    const items = listBuffer.map((item) => item.replace(/^-\s+/, '').trim()).filter(Boolean)
    if (items.length) blocks.push({ kind: 'list', items })
    listBuffer = []
  }

  const flushTextBlocks = () => {
    flushParagraph()
    flushList()
  }

  for (const line of lines) {
    const fence = line.match(/^```(\w+)?\s*$/)
    if (fence && !isInCodeBlock) {
      flushTextBlocks()
      isInCodeBlock = true
      codeLanguage = fence[1] ?? ''
      codeBuffer = []
      continue
    }
    if (fence && isInCodeBlock) {
      blocks.push({ kind: 'code', code: codeBuffer.join('\n'), language: codeLanguage })
      isInCodeBlock = false
      codeLanguage = ''
      codeBuffer = []
      continue
    }

    if (isInCodeBlock) {
      codeBuffer.push(line)
      continue
    }

    const trimmed = line.trim()
    if (!trimmed) {
      flushTextBlocks()
      continue
    }

    const heading = trimmed.match(/^(#{1,3})\s+(.+)$/)
    if (heading) {
      flushTextBlocks()
      blocks.push({
        kind: 'heading',
        level: heading[1].length as 1 | 2 | 3,
        text: heading[2].trim(),
      })
      continue
    }

    if (trimmed.startsWith('- ')) {
      flushParagraph()
      listBuffer.push(trimmed)
      continue
    }

    flushList()
    paragraphBuffer.push(trimmed)
  }

  if (isInCodeBlock) {
    blocks.push({ kind: 'code', code: codeBuffer.join('\n'), language: codeLanguage })
  }
  flushTextBlocks()

  return blocks
}

function InlineMarkdown({ text }: { text: string }) {
  const codeParts = text.split(/(`[^`]+`)/g)
  const renderStrongText = (value: string, partIndex: number) =>
    value.split(/(\*\*[^*]+?\*\*)/g).map((part, index) =>
      part.startsWith('**') && part.endsWith('**') ? (
        <strong key={`strong-${partIndex}-${index}`}>{part.slice(2, -2)}</strong>
      ) : (
        <span key={`text-${partIndex}-${index}`}>{part}</span>
      ),
    )

  return (
    <>
      {codeParts.map((part, index) =>
        part.startsWith('`') && part.endsWith('`') ? (
          <code key={`${part}-${index}`}>{part.slice(1, -1)}</code>
        ) : (
          renderStrongText(part, index)
        ),
      )}
    </>
  )
}

function MarkdownView({ body }: { body: string }) {
  const blocks = parseMarkdownBlocks(body)
  const renderBlocks = withHeadingIds(blocks)

  return (
    <div className="markdown-body">
      {renderBlocks.map(({ block, id }, index) => {
        const content =
          block.kind === 'code'
            ? block.code
            : block.kind === 'list'
              ? block.items.join(' ')
              : block.text
        const key = `${index}-${content.slice(0, 18)}`

        if (block.kind === 'code') {
          return (
            <figure className="code-block" key={key}>
              {block.language && <figcaption>{block.language}</figcaption>}
              <pre>
                <code>{block.code}</code>
              </pre>
            </figure>
          )
        }
        if (block.kind === 'heading') {
          if (block.level === 1) {
            return (
              <h1 id={id} key={key}>
                <InlineMarkdown text={block.text} />
              </h1>
            )
          }
          if (block.level === 2) {
            return (
              <h2 id={id} key={key}>
                <InlineMarkdown text={block.text} />
              </h2>
            )
          }
          return (
            <h3 id={id} key={key}>
              <InlineMarkdown text={block.text} />
            </h3>
          )
        }
        if (block.kind === 'list') {
          return (
            <ul key={key}>
              {block.items.map((item) => (
                <li key={item}>
                  <InlineMarkdown text={item} />
                </li>
              ))}
            </ul>
          )
        }
        return (
          <p key={key}>
            <InlineMarkdown text={block.text} />
          </p>
        )
      })}
    </div>
  )
}

function buildTableOfContents(body: string): TocItem[] {
  let headingIndex = 0
  return parseMarkdownBlocks(body).flatMap((block) => {
    if (block.kind !== 'heading') return []
    const item = {
      id: headingId(block.text, headingIndex),
      level: block.level,
      text: plainMarkdownText(block.text),
    }
    headingIndex += 1
    return [item]
  })
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
  const [activeAiJob, setActiveAiJob] = useState<AiJob | null>(null)

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
  }, [activeArea, activeTrack, deferredQuery, isFocusMode, location.pathname, navigate, query, viewMode])

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

  const activeAiJobs = aiJobs.filter((job) =>
    ['queued', 'solving', 'draft_ready', 'failed'].includes(job.status),
  )
  const totalQueueItems = readerQuestions.length + activeAiJobs.length
  const queueSearch = query.trim().toLowerCase()
  const queueItems = [
    ...readerQuestions.map((question) => ({
      kind: 'question' as const,
      id: `question-${question.id}`,
      sort: question.created_at,
      question,
    })),
    ...activeAiJobs.map((job) => ({
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
      : viewMode === 'quizzes'
        ? filteredQuizzes.length
        : filteredNodes.length
  const totalCount =
    viewMode === 'question-queue'
      ? totalQueueItems
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

    const questionIds = visibleReaderQuestions.map((item) => item.id)
    const instruction =
      questionDraft.trim() ||
      'Fold the open reader questions into the tutorial. Keep the Markdown body concise, bilingual where appropriate, and more step-by-step.'

    try {
      setIsAiRevising(true)
      setError('')
      setAiRevision(null)
      setAiElapsedSeconds(0)
      setAiStatus(`Creating AI job for ${targetType} "${targetId}"...`)
      const data = await postJson<ApiAiJobResponse>('/api/ai/jobs', {
        target_type: targetType,
        target_id: targetId,
        question_ids: questionIds,
        question: questionDraft.trim(),
        instruction,
        draft_body: isEditMode ? editDraft : currentBody,
      })
      setActiveAiJob(data.job)
      setQuestionDraft('')
      setReaderQuestions((current) =>
        current.filter((item) => !data.job.question_ids.includes(item.id)),
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
      if (activeAiJob?.status === 'draft_ready') {
        const applied = await postJson<ApiAiJobResponse>(`/api/ai/jobs/${activeAiJob.id}/apply`, {})
        setAiJobs((current) =>
          current.map((item) => (item.id === activeAiJob.id ? applied.job : item)),
        )
        setReaderQuestions((current) =>
          current.filter((item) => !activeAiJob.question_ids.includes(item.id)),
        )
        setActiveAiJob(null)
      } else if (aiRevision?.resolved_question_ids.length) {
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
      setError(saveError instanceof Error ? saveError.message : 'Unable to save Markdown')
    } finally {
      setIsEditSaving(false)
    }
  }

  const openQuestionCount =
    viewMode === 'quizzes'
      ? selectedQuiz?.open_question_count ?? 0
      : selectedNode?.open_question_count ?? 0
  const visibleReaderQuestions = readerQuestions

  return (
    <main className={`workspace-shell ${isFocusMode ? 'focus-mode' : ''} ${isEditMode ? 'editing-mode' : ''}`}>
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
        </section>
      </aside>

      <section className="node-column" aria-label="Knowledge nodes">
        <header className="search-header">
          <label htmlFor="node-search">
            {viewMode === 'question-queue'
              ? 'Question queue'
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
                : viewMode === 'quizzes'
                ? 'Search quiz prompts, tags, answers...'
                : 'Search concepts, tags, summaries...'
            }
          />
          <p>
            {isLoading
              ? 'Loading index...'
              : `${visibleCount} visible of ${totalCount} indexed ${
                  viewMode === 'question-queue'
                    ? 'open questions'
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
                  return (
                    <article key={item.id} className="node-card question-card">
                      <button
                        type="button"
                        className="question-card-main"
                        onClick={() => openQuestionTarget(item.question)}
                      >
                        <span className="node-meta">
                          Q #{item.question.id} / {item.question.target_type} / {item.question.target_id} /{' '}
                          {item.question.status}
                        </span>
                        <strong>{item.question.question}</strong>
                        <span>Open target and resolve this question in the tutorial.</span>
                      </button>
                      <div className="question-card-actions">
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
                        {['queued', 'solving'].includes(job.status) && (
                          <button type="button" className="focus-toggle" onClick={() => cancelAiJob(job)}>
                            Cancel
                          </button>
                        )}
                      </div>
                    </article>
                  ))}
                </div>
              ) : (
                <p>No AI jobs yet.</p>
              )}
            </section>
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
                    {aiRevision && (
                      <section className="ai-revision-card" aria-label="AI revision preview">
                        <p className="eyebrow">
                          AI draft / {aiRevision.provider} / {aiRevision.model}
                        </p>
                        <h3>{aiRevision.summary || 'Revision ready for review'}</h3>
                        {aiRevision.rationale.length > 0 && (
                          <ul>
                            {aiRevision.rationale.map((item) => (
                              <li key={item}>{item}</li>
                            ))}
                          </ul>
                        )}
                        {aiRevision.changed_sections.length > 0 && (
                          <p>Changed: {aiRevision.changed_sections.join(', ')}</p>
                        )}
                        {aiRevision.resolved_question_ids.length > 0 && (
                          <p>Will resolve Q #{aiRevision.resolved_question_ids.join(', #')} after save.</p>
                        )}
                        {aiRevision.suggested_new_nodes.length > 0 && (
                          <p>Suggested new nodes: {aiRevision.suggested_new_nodes.join(', ')}</p>
                        )}
                      </section>
                    )}
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
                  {isAiRevising ? 'Drafting...' : 'Resolve with AI'}
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
                    {aiRevision && (
                      <section className="ai-revision-card" aria-label="AI revision preview">
                        <p className="eyebrow">
                          AI draft / {aiRevision.provider} / {aiRevision.model}
                        </p>
                        <h3>{aiRevision.summary || 'Revision ready for review'}</h3>
                        {aiRevision.rationale.length > 0 && (
                          <ul>
                            {aiRevision.rationale.map((item) => (
                              <li key={item}>{item}</li>
                            ))}
                          </ul>
                        )}
                        {aiRevision.changed_sections.length > 0 && (
                          <p>Changed: {aiRevision.changed_sections.join(', ')}</p>
                        )}
                        {aiRevision.resolved_question_ids.length > 0 && (
                          <p>Will resolve Q #{aiRevision.resolved_question_ids.join(', #')} after save.</p>
                        )}
                        {aiRevision.suggested_new_nodes.length > 0 && (
                          <p>Suggested new nodes: {aiRevision.suggested_new_nodes.join(', ')}</p>
                        )}
                      </section>
                    )}
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
                  {isAiRevising ? 'Drafting...' : 'Resolve with AI'}
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
    </main>
  )
}

export default App
