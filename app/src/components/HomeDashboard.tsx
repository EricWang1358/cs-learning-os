import type { HomeSummary } from '../homeData'

function displayCount(value: number | null) {
  return value === null ? '--' : String(value)
}

function formatRecentRead(iso?: string) {
  if (!iso) return 'Not read yet'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return 'Read recently'
  return `Last read ${new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)}`
}

export function HomeDashboard({
  summary,
  isLoading,
  error,
  onOpenNode,
  onOpenReview,
  onOpenBite,
  onOpenQueue,
  onOpenQuizzes,
  onOpenGraph,
  onOpenKnowledgeGraph,
  onOpenHealth,
  onOpenSync,
  onCreateNode,
  onRestartServer,
}: {
  summary: HomeSummary
  isLoading: boolean
  error: string
  onOpenNode: (slug: string) => void
  onOpenReview: () => void
  onOpenBite: () => void
  onOpenQueue: () => void
  onOpenQuizzes: () => void
  onOpenGraph: () => void
  onOpenKnowledgeGraph: () => void
  onOpenHealth: () => void
  onOpenSync: () => void
  onCreateNode: () => void
  onRestartServer: () => void
}) {
  const recentNode = summary.recentNode

  return (
    <section className="home-dashboard" aria-label="Home dashboard">
      <header className="home-dashboard-header">
        <div>
          <p className="eyebrow">Today / learning operations</p>
          <h2>{(() => { const h = new Date().getHours(); return h >= 5 && h < 12 ? 'Good morning.' : h >= 12 && h < 18 ? 'Good afternoon.' : h >= 18 && h < 23 ? 'Good evening.' : "It's late — get some rest." })()}</h2>
          <p>Continue the current learning chain, clear today&apos;s review, and grow a knowledge tree.</p>
        </div>
        <div className="home-dashboard-actions">
          <a className="dashboard-action" href="/nodes">Open library</a>
          <button type="button" className="dashboard-action" onClick={onCreateNode}>+ Create node</button>
        </div>
      </header>

      {error && <p className="error-banner">{error}</p>}
      {summary.warnings.length > 0 && (
        <p className="inline-hint" role="status">Some dashboard signals are unavailable. Workspaces remain available.</p>
      )}

      <div className="home-metrics" aria-label="Home metrics">
        <section className="home-metric-group" aria-label="Learning signals">
          <h3>Learning</h3>
          <div className="home-metric-group-items">
            <div><strong>{displayCount(summary.dueReviewCount)}</strong><span>reviews due</span></div>
            <div><strong>{displayCount(summary.openQuestionCount)}</strong><span>open questions</span></div>
          </div>
        </section>
        <section className="home-metric-group" aria-label="Knowledge graph signals">
          <h3>Knowledge graph</h3>
          <div className="home-metric-group-items">
            <div><strong>{displayCount(summary.totalNodes)}</strong><span>nodes indexed</span></div>
            <div><strong>{displayCount(summary.kgRootCount)}</strong><span>roots</span></div>
            <div><strong>{displayCount(summary.bottleneckCount)}</strong><span>bottlenecks</span></div>
          </div>
        </section>
        <section className="home-metric-group" aria-label="System signals">
          <h3>System</h3>
          <div className="home-metric-group-items">
            <div><strong className={summary.syncHealth ? 'home-good' : undefined}>{summary.syncHealth ? 'OK' : '--'}</strong><span>sync health</span></div>
            <div><strong><button type="button" onClick={onRestartServer} style={{background:'none',border:'none',cursor:'pointer',padding:0,color:'inherit',font:'inherit'}} title="Restart backend server">⟳</button></strong><span>restart</span></div>
          </div>
        </section>
      </div>

      <div className="home-dashboard-grid">
        <section className="home-section home-continue-section" aria-label="Continue learning">
          <div className="home-section-heading">
            <h3>Continue learning</h3>
            <span>{isLoading ? 'Loading' : 'Last session'}</span>
          </div>
          {recentNode ? (
            <div className="home-continue-row">
              <div>
                <p className="eyebrow">{recentNode.area} / {recentNode.track}</p>
                <h4>{recentNode.title}</h4>
                <p>{recentNode.summary || 'Open this node to continue reading.'}</p>
                <span className="home-meta">{formatRecentRead(recentNode.last_read_at)}</span>
              </div>
              <button type="button" className="dashboard-action primary" onClick={() => onOpenNode(recentNode.slug)}>
                Resume reading
              </button>
            </div>
          ) : (
            <div className="home-empty-row">
              <p>No reading trace yet. Open the library and choose a node to begin.</p>
              <a className="dashboard-action" href="/nodes">Browse nodes</a>
            </div>
          )}
          <div className="home-action-row">
            <button type="button" className="home-action-cell" onClick={onOpenReview}>
              <strong>Daily review</strong><span>{displayCount(summary.dueReviewCount)} due</span><em>START -&gt;</em>
            </button>
            <button type="button" className="home-action-cell" onClick={onOpenBite}>
              <strong>Daily Bite</strong><span>{summary.dailyBite ? 'One drill ready' : 'Unavailable'}</span><em>DRILL -&gt;</em>
            </button>
            <button type="button" className="home-action-cell" onClick={onOpenQueue}>
              <strong>Q Queue</strong><span>{displayCount(summary.openQuestionCount)} open</span><em>OPEN -&gt;</em>
            </button>
          </div>
        </section>

        <section className="home-section home-kg-section" aria-label="Knowledge graph">
          <div className="home-section-heading">
            <h3>Knowledge graph</h3>
          </div>
          <div className="home-kg-summary">
            <div className="home-kg-stats">
              <p><span>Roots</span><strong>{displayCount(summary.kgRootCount)}</strong></p>
              <p><span>Bottlenecks</span><strong>{displayCount(summary.bottleneckCount)}</strong></p>
              <button type="button" className="dashboard-action" onClick={onOpenKnowledgeGraph}>Open KG workspace</button>
            </div>
          </div>
        </section>
      </div>

      <div className="home-dashboard-lower">
        <section className="home-section" aria-label="Assistant queue">
          <div className="home-section-heading"><h3>Assistant queue</h3><span>{displayCount(summary.openQuestionCount)} open</span></div>
          <div className="home-list-row"><span>Questions to resolve</span><strong>{displayCount(summary.openQuestionCount)}</strong></div>
          <div className="home-list-row"><span>Daily Bite source</span><strong>{summary.dailyBite ? 'READY' : '--'}</strong></div>
          <div className="home-list-row"><span>Sync gateway</span><strong className={summary.syncHealth ? 'home-good' : undefined}>{summary.syncHealth ? 'READY' : '--'}</strong></div>
        </section>
        <section className="home-section" aria-label="Quick capture">
          <div className="home-section-heading"><h3>Quick capture</h3></div>
          <div className="home-capture-grid">
            <button type="button" onClick={onCreateNode}>+ Create node</button>
            <button type="button" onClick={onOpenKnowledgeGraph}>+ Create KG root</button>
            <button type="button" onClick={onOpenQueue}>Ask assistant</button>
            <a href="/nodes">Open library</a>
          </div>
        </section>
      </div>

      <section className="home-section home-workspaces-section" aria-label="Workspaces">
        <div className="home-section-heading"><h3>Workspaces</h3></div>
        <div className="home-workspace-grid">
          <button type="button" onClick={onOpenQuizzes}><strong>Practice / Quiz Bank</strong><span>Review indexed prompts</span></button>
          <button type="button" onClick={onOpenBite}><strong>Daily Bite</strong><span>Run one recall drill</span></button>
          <button type="button" onClick={onOpenReview}><strong>Daily review</strong><span>Clear due cards</span></button>
          <button type="button" onClick={onOpenQueue}><strong>Q Queue</strong><span>Resolve reader questions</span></button>
          <button type="button" onClick={onOpenGraph}><strong>Knowledge navigator</strong><span>Browse the graph layers</span></button>
          <button type="button" onClick={onOpenKnowledgeGraph}><strong>Knowledge tree 3D</strong><span>Explore the 3D workspace</span></button>
          <button type="button" onClick={onOpenHealth}><strong>System health</strong><span>Inspect local storage and jobs</span></button>
          <button type="button" onClick={onOpenSync}><strong>Sync</strong><span>Pair devices and monitor gateway</span></button>
        </div>
      </section>
    </section>
  )
}
