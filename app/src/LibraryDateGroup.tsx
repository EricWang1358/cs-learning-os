import type { NodeSummary } from './types/api'
import type { LibraryDateBucket } from './libraryGrouping'
import { LibraryNodeRow } from './LibraryNodeRow'
import type { LibraryNodeRowProps } from './LibraryNodeRow'

export type LibraryDateGroupProps = Pick<LibraryNodeRowProps, 'onOpen' | 'onOrderCommit' | 'formatUpdatedAt'> & {
  bucket: LibraryDateBucket
  label: string
  nodes: NodeSummary[]
  isOpen: boolean
  onToggle: () => void
  selectedSlug?: string
}

const bucketId = (bucket: LibraryDateBucket) => `library-date-group-${bucket}`

export function LibraryDateGroup({
  bucket,
  label,
  nodes,
  isOpen,
  onToggle,
  onOpen,
  onOrderCommit,
  formatUpdatedAt,
  selectedSlug,
}: LibraryDateGroupProps) {
  const contentId = bucketId(bucket)

  return (
    <section className="library-date-group" data-testid={`library-date-group-${bucket}`} aria-labelledby={`${contentId}-heading`}>
      <header className="library-date-group-header">
        <button
          type="button"
          className="library-date-group-toggle"
          aria-expanded={isOpen}
          aria-controls={`${contentId}-content`}
          onClick={onToggle}
        >
          <span id={`${contentId}-heading`} className="library-date-group-label">
            {label}
          </span>
          <span className="library-date-group-count">{nodes.length}</span>
          <span className="library-date-group-chevron" aria-hidden="true">
            {isOpen ? 'v' : '>'}
          </span>
        </button>
      </header>
      <div id={`${contentId}-content`} className="library-date-group-content" hidden={!isOpen}>
        {nodes.map((node) => (
          <LibraryNodeRow
            key={node.slug}
            node={node}
            selected={node.slug === selectedSlug}
            onOpen={onOpen}
            onOrderCommit={onOrderCommit}
            formatUpdatedAt={formatUpdatedAt}
          />
        ))}
      </div>
    </section>
  )
}
