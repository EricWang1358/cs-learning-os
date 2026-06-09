import type { GraphPayload } from '../types/api'

function graphChildPosition(index: number, total: number) {
  const slots = Math.max(total, 1)
  const angle = slots === 1 ? -90 : -90 + (index * 360) / slots
  const radiusX = 38
  const radiusY = 30
  const x = 50 + Math.cos((angle * Math.PI) / 180) * radiusX
  const y = 53 + Math.sin((angle * Math.PI) / 180) * radiusY
  return { x, y, style: { left: `${x}%`, top: `${y}%` } }
}

export function GraphNavigator({
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
            : `${children.length} visible of ${pagination.total} - page ${pagination.page}/${pagination.total_pages}`}
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
