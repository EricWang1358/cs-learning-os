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

type ViewMode = 'nodes' | 'quizzes'

type MarkdownBlock =
  | { kind: 'code'; code: string; language: string }
  | { kind: 'heading'; level: 1 | 2 | 3; text: string }
  | { kind: 'paragraph'; text: string }
  | { kind: 'list'; items: string[] }

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

async function fetchJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`)
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
  const parts = text.split(/(`[^`]+`)/g)

  return (
    <>
      {parts.map((part, index) =>
        part.startsWith('`') && part.endsWith('`') ? (
          <code key={`${part}-${index}`}>{part.slice(1, -1)}</code>
        ) : (
          <span key={`${part}-${index}`}>{part}</span>
        ),
      )}
    </>
  )
}

function MarkdownView({ body }: { body: string }) {
  const blocks = parseMarkdownBlocks(body)

  return (
    <div className="markdown-body">
      {blocks.map((block, index) => {
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
          if (block.level === 1) return <h1 key={key}>{block.text}</h1>
          if (block.level === 2) return <h2 key={key}>{block.text}</h2>
          return <h3 key={key}>{block.text}</h3>
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

  const visibleCount = viewMode === 'quizzes' ? filteredQuizzes.length : filteredNodes.length
  const totalCount = viewMode === 'quizzes' ? quizzes.length : nodes.length
  const visibleTracks =
    viewMode === 'nodes' && activeArea !== 'all' && activeArea !== 'archive' ? tracks : []

  return (
    <main className={`workspace-shell ${isFocusMode ? 'focus-mode' : ''}`}>
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
        </section>

        <section className="system-note">
          <h2>Current loop</h2>
          <p>Markdown stays source of truth. SQLite powers search. React is the daily surface.</p>
        </section>
      </aside>

      <section className="node-column" aria-label="Knowledge nodes">
        <header className="search-header">
          <label htmlFor="node-search">
            {viewMode === 'quizzes' ? 'Quiz search' : 'Global search'}
          </label>
          <input
            id="node-search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={
              viewMode === 'quizzes'
                ? 'Search quiz prompts, tags, answers...'
                : 'Search concepts, tags, summaries...'
            }
          />
          <p>
            {isLoading
              ? 'Loading index...'
              : `${visibleCount} visible of ${totalCount} indexed ${
                  viewMode === 'quizzes' ? 'quizzes' : 'nodes'
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
          {viewMode === 'quizzes'
            ? filteredQuizzes.map((quiz) => (
                <button
                  key={quiz.id}
                  type="button"
                  className={`node-card ${quiz.id === selectedQuizId ? 'selected' : ''}`}
                  onClick={() => setSelectedQuizId(quiz.id)}
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
                  onClick={() => setSelectedSlug(node.slug)}
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
        {viewMode === 'quizzes' && selectedQuiz ? (
          <>
            <div className="detail-heading">
              <div className="detail-toolbar">
                <p className="eyebrow">{selectedQuiz.area} / quiz</p>
                <button
                  type="button"
                  className="focus-toggle"
                  onClick={() => setIsFocusMode((current) => !current)}
                >
                  {isFocusMode ? 'Show map' : 'Focus reading'}
                </button>
              </div>
              <h2>{selectedQuiz.title}</h2>
              <p>{selectedQuiz.summary}</p>
            </div>

            <div className="tag-row">
              <span>{selectedQuiz.difficulty}</span>
              {selectedQuiz.tags.map((tag) => (
                <span key={tag}>{tag}</span>
              ))}
            </div>

            <section className="detail-section">
              <h3>Quiz body</h3>
              <MarkdownView body={selectedQuiz.body} />
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
          </>
        ) : viewMode === 'nodes' && selectedNode ? (
          <>
            <div className="detail-heading">
              <div className="detail-toolbar">
                <p className="eyebrow">{selectedNode.area}</p>
                <button
                  type="button"
                  className="focus-toggle"
                  onClick={() => setIsFocusMode((current) => !current)}
                >
                  {isFocusMode ? 'Show map' : 'Focus reading'}
                </button>
              </div>
              <h2>{selectedNode.title}</h2>
              <p>{selectedNode.summary}</p>
            </div>

            <div className="tag-row">
              {selectedNode.tags.map((tag) => (
                <span key={tag}>{tag}</span>
              ))}
            </div>

            <section className="detail-section">
              <h3>Note body</h3>
              <MarkdownView body={selectedNode.body} />
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
