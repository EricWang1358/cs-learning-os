import { useEffect, useRef, useState } from 'react'
import type { NodeSummary } from './types/api'

export type LibraryNodeRowProps = {
  node: NodeSummary
  selected?: boolean
  onOpen: (node: NodeSummary) => void
  onOrderCommit: (node: NodeSummary, nextOrder: number) => boolean | Promise<boolean>
  formatUpdatedAt?: (updatedAt: string) => string
}

const DEFAULT_TIME_ZONE = 'Asia/Shanghai'

function formatUpdatedAt(updatedAt: string) {
  const date = new Date(updatedAt)
  if (Number.isNaN(date.getTime())) return 'Unknown time'
  return new Intl.DateTimeFormat('en-US', {
    timeZone: DEFAULT_TIME_ZONE,
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function orderLabel(order: number) {
  return Number.isInteger(order) && order > 0 ? String(order) : '-'
}

function statusClass(status: string) {
  const normalized = status.trim().toLowerCase().replace(/[^a-z0-9_-]+/g, '-')
  return normalized ? `library-node-status--${normalized}` : ''
}

export function LibraryNodeRow({
  node,
  selected = false,
  onOpen,
  onOrderCommit,
  formatUpdatedAt: formatTime = formatUpdatedAt,
}: LibraryNodeRowProps) {
  const [isEditingOrder, setIsEditingOrder] = useState(false)
  const [draftOrder, setDraftOrder] = useState(orderLabel(node.display_order))
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (isEditingOrder) {
      inputRef.current?.focus()
      inputRef.current?.select()
    }
  }, [isEditingOrder])

  const beginOrderEdit = () => {
    setDraftOrder(orderLabel(node.display_order))
    setIsEditingOrder(true)
  }

  const cancelOrderEdit = () => {
    setDraftOrder(orderLabel(node.display_order))
    setIsEditingOrder(false)
  }

  const commitOrder = async () => {
    const nextOrder = Number(draftOrder.trim())
    if (!Number.isInteger(nextOrder) || nextOrder <= 0) return
    const committed = await onOrderCommit(node, nextOrder)
    if (committed) {
      setDraftOrder(orderLabel(node.display_order))
      setIsEditingOrder(false)
    }
  }

  const handleOrderKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault()
      commitOrder()
    } else if (event.key === 'Escape') {
      event.preventDefault()
      cancelOrderEdit()
    }
  }

  const handleOrderDisplayKeyDown = (event: React.KeyboardEvent<HTMLSpanElement>) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      beginOrderEdit()
    }
  }

  return (
    <article
      className={`library-node-row${selected ? ' is-selected' : ''}`}
      data-testid="library-node-row"
      aria-label={`Library node ${node.title}`}
    >
      <div className="library-node-row-grid">
        <div
          className="library-node-order-shell"
          onDoubleClick={beginOrderEdit}
          title="Double-click to edit sequence"
        >
          {isEditingOrder ? (
            <input
              ref={inputRef}
              className="library-node-order-input"
              value={draftOrder}
              inputMode="numeric"
              aria-label={`Sequence for ${node.title}`}
              onChange={(event) => setDraftOrder(event.target.value)}
              onBlur={cancelOrderEdit}
              onKeyDown={handleOrderKeyDown}
            />
          ) : (
            <span
              className="library-node-order"
              data-testid="library-node-order"
              role="button"
              tabIndex={0}
              onKeyDown={handleOrderDisplayKeyDown}
              aria-label={`Edit order for ${node.title}`}
              title={`Sequence ${orderLabel(node.display_order)}. Double-click to edit`}
            >
              {orderLabel(node.display_order)}
            </span>
          )}
        </div>

        <button
          type="button"
          className="library-node-row-main"
          aria-label={`Open library node ${node.title}`}
          onClick={() => onOpen(node)}
        >
          <span className="library-node-row-content">
            <strong className="library-node-row-title">{node.title}</strong>
            <span className="library-node-row-meta">
              <span>{node.area || 'Unassigned area'}</span>
              <span aria-hidden="true">/</span>
              <span>{node.track || 'General'}</span>
            </span>
            <span className="library-node-row-summary">{node.summary || 'No summary available'}</span>
          </span>
          <span className="library-node-row-trailing">
            <time dateTime={node.updated_at}>{formatTime(node.updated_at)}</time>
            <span className={`library-node-status ${statusClass(node.status)}`}>{node.status || 'unknown'}</span>
          </span>
        </button>
      </div>
    </article>
  )
}
