export type HealthIssueSeverity = 'info' | 'warning' | 'error'

export type HealthIssue = {
  id: string
  title: string
  summary?: string
  severity?: HealthIssueSeverity
  target?: string
  actionLabel?: string
}

export type PackageManifestEntry = {
  id: string
  label: string
  value: string
  note?: string
}

export type AiPreflightCheck = {
  id: string
  label: string
  ok: boolean
  message?: string
}

export type SchemaMetadataItem = {
  id: string
  label: string
  value: string | number
  note?: string
}

export type ContentIndexSummary = {
  totalItems: number
  indexedItems: number
  staleItems?: number
  lastIndexedAt?: string
  entries?: Array<{
    id: string
    label: string
    count: number
    note?: string
  }>
}

export type HealthActionPanelsProps = {
  integrityIssues?: HealthIssue[]
  repairIssues?: HealthIssue[]
  packageManifest?: PackageManifestEntry[]
  aiPreflightChecks?: AiPreflightCheck[]
  schemaMetadata?: SchemaMetadataItem[]
  contentIndex?: ContentIndexSummary
  onRepairIssue?: (issue: HealthIssue) => void
  onInspectIssue?: (issue: HealthIssue) => void
  onExportPackage?: () => void
  onRunAiPreflight?: () => void
  onRefreshSchemaMetadata?: () => void
  onRefreshContentIndex?: () => void
}

function formatCount(value: number) {
  return new Intl.NumberFormat('en-US').format(value)
}

function healthIssueLabel(issue: HealthIssue) {
  return issue.severity ? issue.severity.toUpperCase() : 'ISSUE'
}

function renderIssueList({
  emptyText,
  issues,
  onInspectIssue,
  onRepairIssue,
}: {
  emptyText: string
  issues: HealthIssue[]
  onInspectIssue?: (issue: HealthIssue) => void
  onRepairIssue?: (issue: HealthIssue) => void
}) {
  if (!issues.length) {
    return <p>{emptyText}</p>
  }

  return (
    <div className="job-list">
      {issues.map((issue) => (
        <article className="ai-revision-card compact-card" key={issue.id}>
          <p className="eyebrow">
            {healthIssueLabel(issue)}
            {issue.target ? ` / ${issue.target}` : ''}
          </p>
          <h3>{issue.title}</h3>
          {issue.summary && <p>{issue.summary}</p>}
          {(onInspectIssue || onRepairIssue) && (
            <div className="question-card-actions">
              {onInspectIssue && (
                <button type="button" className="focus-toggle" onClick={() => onInspectIssue(issue)}>
                  Inspect
                </button>
              )}
              {onRepairIssue && (
                <button type="button" className="focus-toggle ai-action" onClick={() => onRepairIssue(issue)}>
                  {issue.actionLabel || 'Repair'}
                </button>
              )}
            </div>
          )}
        </article>
      ))}
    </div>
  )
}

export function HealthActionPanels({
  aiPreflightChecks = [],
  contentIndex,
  integrityIssues = [],
  onExportPackage,
  onInspectIssue,
  onRefreshContentIndex,
  onRefreshSchemaMetadata,
  onRepairIssue,
  onRunAiPreflight,
  packageManifest = [],
  repairIssues = [],
  schemaMetadata = [],
}: HealthActionPanelsProps) {
  const failedPreflightChecks = aiPreflightChecks.filter((check) => !check.ok).length
  const indexedPercent = contentIndex?.totalItems
    ? Math.round((contentIndex.indexedItems / contentIndex.totalItems) * 100)
    : 0

  return (
    <div className="health-detail">
      <section className="health-grid" aria-label="Health action summary">
        <article className="health-card">
          <p className="eyebrow">Integrity</p>
          <h3>{formatCount(integrityIssues.length)}</h3>
          <p>{integrityIssues.length === 1 ? 'Issue needs review.' : 'Issues need review.'}</p>
        </article>
        <article className="health-card">
          <p className="eyebrow">Repair queue</p>
          <h3>{formatCount(repairIssues.length)}</h3>
          <p>{repairIssues.length === 1 ? 'Repair is available.' : 'Repairs are available.'}</p>
        </article>
        <article className="health-card">
          <p className="eyebrow">AI preflight</p>
          <h3>{failedPreflightChecks ? `${failedPreflightChecks} blocked` : 'Ready'}</h3>
          <p>{aiPreflightChecks.length ? `${formatCount(aiPreflightChecks.length)} checks configured.` : 'No checks supplied.'}</p>
        </article>
        <article className="health-card">
          <p className="eyebrow">Content index</p>
          <h3>{contentIndex ? `${indexedPercent}%` : 'Pending'}</h3>
          <p>
            {contentIndex
              ? `${formatCount(contentIndex.indexedItems)} of ${formatCount(contentIndex.totalItems)} items indexed.`
              : 'No index summary supplied.'}
          </p>
        </article>
      </section>

      <section className="detail-section" aria-label="Health repair">
        <h3>Integrity issues</h3>
        {renderIssueList({
          emptyText: 'No integrity issues reported.',
          issues: integrityIssues,
          onInspectIssue,
        })}
      </section>

      <section className="detail-section" aria-label="Repair issues">
        <h3>Repair issues</h3>
        {renderIssueList({
          emptyText: 'No repair issues queued.',
          issues: repairIssues,
          onInspectIssue,
          onRepairIssue,
        })}
      </section>

      <section className="detail-section" aria-label="Package export">
        <div className="detail-toolbar">
          <h3>Package Export manifest</h3>
          {onExportPackage && (
            <button type="button" className="focus-toggle ai-action" onClick={onExportPackage}>
              Export package
            </button>
          )}
        </div>
        {packageManifest.length ? (
          <ul className="metric-list">
            {packageManifest.map((entry) => (
              <li key={entry.id}>
                <strong>{entry.value}</strong> {entry.label}
                {entry.note ? ` / ${entry.note}` : ''}
              </li>
            ))}
          </ul>
        ) : (
          <p>No package manifest supplied.</p>
        )}
      </section>

      <section className="detail-section" aria-label="AI preflight">
        <div className="detail-toolbar">
          <h3>AI Preflight</h3>
          {onRunAiPreflight && (
            <button type="button" className="focus-toggle ai-action" onClick={onRunAiPreflight}>
              Run preflight
            </button>
          )}
        </div>
        {aiPreflightChecks.length ? (
          <div className="job-list">
            {aiPreflightChecks.map((check) => (
              <article className="ai-revision-card compact-card" key={check.id}>
                <p className="eyebrow">{check.ok ? 'PASS' : 'BLOCKED'}</p>
                <h3>{check.label}</h3>
                {check.message && <p className={check.ok ? 'ai-status' : 'ai-status error'}>{check.message}</p>}
              </article>
            ))}
          </div>
        ) : (
          <p>No AI preflight checks supplied.</p>
        )}
      </section>

      <section className="detail-section" aria-label="Schema and content index">
        <div className="detail-toolbar">
          <h3>Schema metadata</h3>
          {onRefreshSchemaMetadata && (
            <button type="button" className="focus-toggle" onClick={onRefreshSchemaMetadata}>
              Refresh schema
            </button>
          )}
        </div>
        {schemaMetadata.length ? (
          <ul className="metric-list">
            {schemaMetadata.map((item) => (
              <li key={item.id}>
                <strong>{item.value}</strong> {item.label}
                {item.note ? ` / ${item.note}` : ''}
              </li>
            ))}
          </ul>
        ) : (
          <p>No schema metadata supplied.</p>
        )}
      </section>

      <section className="detail-section" aria-label="Content index summary">
        <div className="detail-toolbar">
          <h3>Content Index summary</h3>
          {onRefreshContentIndex && (
            <button type="button" className="focus-toggle" onClick={onRefreshContentIndex}>
              Refresh index
            </button>
          )}
        </div>
        {contentIndex ? (
          <>
            <div className="tag-row">
              <span>{formatCount(contentIndex.totalItems)} total</span>
              <span>{formatCount(contentIndex.indexedItems)} indexed</span>
              {typeof contentIndex.staleItems === 'number' && (
                <span className={contentIndex.staleItems ? 'needs-review' : undefined}>
                  {formatCount(contentIndex.staleItems)} stale
                </span>
              )}
              {contentIndex.lastIndexedAt && <span>Updated {contentIndex.lastIndexedAt}</span>}
            </div>
            {contentIndex.entries?.length ? (
              <ul className="metric-list">
                {contentIndex.entries.map((entry) => (
                  <li key={entry.id}>
                    <strong>{formatCount(entry.count)}</strong> {entry.label}
                    {entry.note ? ` / ${entry.note}` : ''}
                  </li>
                ))}
              </ul>
            ) : (
              <p>No index breakdown supplied.</p>
            )}
          </>
        ) : (
          <p>No content index summary supplied.</p>
        )}
      </section>
    </div>
  )
}
