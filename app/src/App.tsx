import { startTransition, useEffect, useState, useDeferredValue } from 'react'
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

type ViewMode = 'nodes' | 'quizzes' | 'question-queue'

type HistoryEntry =
  | { viewMode: 'nodes'; selectedSlug: string; activeArea: string; activeTrack: string }
  | { viewMode: 'quizzes'; selectedQuizId: string; activeArea: string; activeTrack: string }

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
    throw new Error(`Request failed: ${response.status}`)
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
    throw new Error(`Request failed: ${response.status}`)
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
    throw new Error(`Request failed: ${response.status}`)
  }
  return response.json() as Promise<T>
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

function App() {
  const [viewMode, setViewMode] = useState<ViewMode>('nodes')
  const [nodes, setNodes] = useState<NodeSummary[]>([])
  const [quizzes, setQuizzes] = useState<QuizSummary[]>([])
  const [tracks, setTracks] = useState<TrackSummary[]>([])
  const [selectedSlug, setSelectedSlug] = useState<string>('')
  const [selectedQuizId, setSelectedQuizId] = useState<string>('')
  const [selectedNode, setSelectedNode] = useState<NodeDetail | null>(null)
  const [selectedQuiz, setSelectedQuiz] = useState<QuizDetail | null>(null)
  const [activeArea, setActiveArea] = useState('all')
  const [activeTrack, setActiveTrack] = useState('all')
  const [query, setQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [isFocusMode, setIsFocusMode] = useState(false)
  const [error, setError] = useState('')
  const [historyStack, setHistoryStack] = useState<HistoryEntry[]>([])
  const [readerQuestions, setReaderQuestions] = useState<ReaderQuestion[]>([])
  const [questionDraft, setQuestionDraft] = useState('')
  const [isQuestionSaving, setIsQuestionSaving] = useState(false)
  const [isEditMode, setIsEditMode] = useState(false)
  const [editDraft, setEditDraft] = useState('')
  const [isEditSaving, setIsEditSaving] = useState(false)

  const deferredQuery = useDeferredValue(query)

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
            setSelectedQuizId((current) => current || data.quizzes[0]?.id || '')
          })
        } else if (viewMode === 'question-queue') {
          const data = await fetchJson<ApiReaderQuestionsResponse>('/api/reader-questions?status=open')

          if (!isActive) return

          startTransition(() => {
            setReaderQuestions(data.questions)
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
            setSelectedSlug((current) => current || data.nodes[0]?.slug || '')
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
  }, [deferredQuery, viewMode])

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
        setActiveTrack((current) =>
          current === 'all' || data.tracks.some((track) => track.track === current)
            ? current
            : 'all',
        )
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
  }, [activeArea, viewMode])

  useEffect(() => {
    if (!selectedSlug) {
      return
    }

    let isActive = true

    async function loadDetail() {
      try {
        setIsDetailLoading(true)
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

  const visibleCount =
    viewMode === 'question-queue'
      ? readerQuestions.length
      : viewMode === 'quizzes'
        ? filteredQuizzes.length
        : filteredNodes.length
  const totalCount =
    viewMode === 'question-queue'
      ? readerQuestions.length
      : viewMode === 'quizzes'
        ? quizzes.length
        : nodes.length
  const visibleTracks =
    viewMode === 'nodes' && activeArea !== 'all' && activeArea !== 'archive' ? tracks : []

  const currentHistoryEntry =
    viewMode === 'quizzes'
      ? selectedQuizId
        ? { viewMode, selectedQuizId, activeArea, activeTrack }
        : null
      : viewMode === 'nodes' && selectedSlug
        ? { viewMode, selectedSlug, activeArea, activeTrack }
        : null

  const exitEditMode = (shouldConfirm = true) => {
    if (!isEditMode) return true
    if (shouldConfirm && editDraft.trim() && !window.confirm('Discard unsaved Markdown edits?')) {
      return false
    }
    setIsEditMode(false)
    setEditDraft('')
    return true
  }

  const pushCurrentHistory = () => {
    if (!currentHistoryEntry) return
    setHistoryStack((current) => [...current, currentHistoryEntry])
  }

  const openQuestionTarget = (item: ReaderQuestion) => {
    exitEditMode(false)
    if (item.target_type === 'node') {
      setViewMode('nodes')
      setSelectedSlug(item.target_id)
      setIsFocusMode(true)
    } else {
      setViewMode('quizzes')
      setSelectedQuizId(item.target_id)
      setIsFocusMode(true)
    }
  }

  const goBack = () => {
    if (!exitEditMode()) return
    setHistoryStack((current) => {
      const previous = current.at(-1)
      if (!previous) return current

      if (previous.viewMode === 'nodes') {
        setViewMode('nodes')
        setActiveArea(previous.activeArea)
        setActiveTrack(previous.activeTrack)
        setSelectedSlug(previous.selectedSlug)
      } else {
        setViewMode('quizzes')
        setActiveArea(previous.activeArea)
        setActiveTrack(previous.activeTrack)
        setSelectedQuizId(previous.selectedQuizId)
      }

      return current.slice(0, -1)
    })
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

  const startEditMode = () => {
    const body = viewMode === 'quizzes' ? selectedQuiz?.body : selectedNode?.body
    if (!body) return
    setEditDraft(body)
    setIsFocusMode(true)
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
  const visibleReaderQuestions = currentHistoryEntry ? readerQuestions : []

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
                setViewMode('nodes')
                setActiveArea(area)
                setActiveTrack('all')
                setQuery('')
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
              setViewMode('quizzes')
              setActiveArea('all')
              setActiveTrack('all')
              setQuery('')
            }}
          >
            Practice / Quiz Bank
          </button>
          <button
            type="button"
            className={viewMode === 'question-queue' ? 'active' : ''}
            onClick={() => {
              exitEditMode(false)
              setViewMode('question-queue')
              setActiveArea('all')
              setActiveTrack('all')
              setQuery('')
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
            onChange={(event) => setQuery(event.target.value)}
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
                onClick={() => setActiveTrack('all')}
              >
                All tracks
              </button>
              {visibleTracks.map((track) => (
                <button
                  key={track.track}
                  type="button"
                  className={activeTrack === track.track ? 'active' : ''}
                  onClick={() => setActiveTrack(track.track)}
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
            ? readerQuestions.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className="node-card question-card"
                  onClick={() => openQuestionTarget(item)}
                >
                  <span className="node-meta">
                    {item.target_type} / {item.target_id} / #{item.id}
                  </span>
                  <strong>{item.question}</strong>
                  <span>Open target and resolve this question in the tutorial.</span>
                </button>
              ))
            : viewMode === 'quizzes'
            ? filteredQuizzes.map((quiz) => (
                <button
                  key={quiz.id}
                  type="button"
                  className={`node-card ${quiz.id === selectedQuizId ? 'selected' : ''}`}
                  onClick={() => {
                    if (!exitEditMode()) return
                    setSelectedQuizId(quiz.id)
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
                    setSelectedSlug(node.slug)
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
          <div className="empty-state">
            <h2>Q to be solved</h2>
            <p>Select a question from the queue to open its source node or quiz.</p>
          </div>
        ) : viewMode === 'quizzes' && selectedQuiz ? (
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
                      disabled={!historyStack.length}
                      onClick={goBack}
                    >
                      Back
                    </button>
                    <button
                      type="button"
                      className="focus-toggle"
                      onClick={() => setIsFocusMode((current) => !current)}
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
                              pushCurrentHistory()
                              setViewMode('nodes')
                              setSelectedSlug(link.slug)
                              setIsFocusMode(true)
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
                <div className="reader-question-list">
                  <strong>{openQuestionCount} open</strong>
                  {visibleReaderQuestions.map((item) => (
                    <p key={item.id}>{item.question}</p>
                  ))}
                </div>
              </aside>
            )}
          </>
        ) : viewMode === 'nodes' && selectedNode ? (
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
                      disabled={!historyStack.length}
                      onClick={goBack}
                    >
                      Back
                    </button>
                    <button
                      type="button"
                      className="focus-toggle"
                      onClick={() => setIsFocusMode((current) => !current)}
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
                              pushCurrentHistory()
                              setSelectedSlug(link.target)
                              setIsFocusMode(true)
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
