export type NodeSummary = {
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
  last_read_at?: string
  read_count?: number
}

export type NodeDetail = NodeSummary & {
  body: string
  body_hash: string
  tags: string[]
  links: Array<{ target: string; kind: string }>
  sources: Array<{ source: string; source_type: string; note: string }>
  open_question_count: number
}

export type ApiNodesResponse = {
  nodes: NodeSummary[]
}

export type AreaSummary = {
  area: string
  label: string
  node_count: number
  first_order: number
}

export type ApiAreasResponse = {
  areas: AreaSummary[]
  system: Record<string, number>
}

export type ApiNodeResponse = {
  node: NodeDetail
}

export type NodeCreateDraft = {
  title: string
  area: string
  track: string
  summary: string
  tags: string
}

export type QuizSummary = {
  id: string
  title: string
  area: string
  display_order: number
  status: string
  visibility: string
  difficulty: string
  summary: string
  path: string
  weight: number
  updated_at: string
}

export type QuizDetail = QuizSummary & {
  body: string
  body_hash: string
  tags: string[]
  linked_nodes: Array<{ slug: string; kind: string; title: string }>
  sources: Array<{ source: string; source_type: string; note: string }>
  open_question_count: number
}

export type ApiQuizzesResponse = {
  quizzes: QuizSummary[]
}

export type ApiQuizResponse = {
  quiz: QuizDetail
}

export type TrackSummary = {
  track: string
  label: string
  node_count: number
  first_order: number
}

export type ApiTracksResponse = {
  area: string
  tracks: TrackSummary[]
}

export type ReaderQuestion = {
  id: number
  target_type: 'node' | 'quiz'
  target_id: string
  question: string
  status: string
  created_at: string
  resolved_at: string
  resolution_note: string
}

export type ApiReaderQuestionsResponse = {
  questions: ReaderQuestion[]
}

export type ApiReaderQuestionResponse = {
  question: ReaderQuestion
}

export type AiRevision = {
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

export type AiJob = {
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

export type ApiAiJobResponse = {
  job: AiJob
}

export type ApiAiJobsResponse = {
  jobs: AiJob[]
}

export type AiJobEvent = {
  id: number
  job_id: number
  level: string
  stage: string
  message: string
  created_at: string
}

export type ApiAiJobEventsResponse = {
  events: AiJobEvent[]
}

export type ReviewQueueItem = {
  target_type: 'quiz'
  target_id: string
  due_at: string
  interval_days: number
  ease_factor: number
  reps: number
  lapses: number
  updated_at: string
  title: string
  area: string
  difficulty: string
  summary: string
}

export type ApiDueReviewsResponse = {
  reviews: ReviewQueueItem[]
}

export type ReviewScheduleRecord = {
  target_type: 'quiz'
  target_id: string
  due_at: string
  interval_days: number
  ease_factor: number
  reps: number
  lapses: number
  updated_at: string
}

export type ApiQuizAttemptResponse = {
  attempt_id: number
  review: ReviewScheduleRecord
}

export type DailyBite = {
  id: string
  card_id: number | null
  source_type: 'node' | 'quiz'
  source_id: string
  title: string
  area: string
  difficulty: string
  question_type: 'blank' | 'multiple_choice'
  prompt: string
  answer: string
  options: string[]
  hint: string
  explanation: string[]
  summary: string
  linked_nodes: Array<{ slug: string; kind: string; title: string }>
  open_quiz_path: string
  open_node_path: string
  status: 'generated' | 'active' | 'archive'
  created_at: string
  updated_at: string
}

export type ApiBiteResponse = {
  bite: DailyBite
  next_cursor: string
}

export type ApiBitesResponse = {
  bites: DailyBite[]
}

export type ApiBiteCardResponse = {
  bite: DailyBite
}

export type BiteCardPayload = {
  source_type: 'node' | 'quiz'
  source_id: string
  title: string
  area: string
  difficulty: string
  question_type: 'blank' | 'multiple_choice'
  prompt: string
  answer: string
  options: string[]
  hint: string
  explanation: string[]
  status?: 'active' | 'archive'
}

export type SchemaMetaItem = {
  value: string
  updated_at: string
}

export type ApiSystemSchemaResponse = {
  schema: Record<string, SchemaMetaItem>
}

export type RepairIssue = {
  severity: 'info' | 'warning' | 'error'
  kind: string
  target_type?: string
  target_id?: string
  path?: string
  source_slug?: string
  target_slug?: string
  link_kind?: string
  [key: string]: unknown
}

export type ApiSystemRepairResponse = {
  ok: boolean
  generated_at: string
  issue_count: number
  issues: RepairIssue[]
}

export type PackageManifestFile = {
  path: string
  size_bytes: number
  mtime_ns: number
  sha256: string
}

export type PackageManifest = {
  package_format_version: string
  generated_at: string
  content_root: string
  counts: {
    nodes: number
    quizzes: number
    assets: number
    files: number
  }
  files: PackageManifestFile[]
  written_to?: string
}

export type ApiPackageExportResponse = {
  manifest: PackageManifest
}

export type LlmWikiPackItem = {
  type: 'node' | 'quiz'
  id: string
  title: string
  area: string
  track?: string
  display_order: number
  status: string
  visibility: string
  difficulty?: string
  summary: string
  path: string
  updated_at: string
  sha256: string
  tags: string[]
  links: string[]
  sources: Array<{
    source: string
    source_type: string
    note: string
  }>
}

export type LlmWikiPack = {
  llmwiki_format_version: string
  package_format_version: string
  generated_at: string
  profile: string
  content_root: string
  output: {
    default_path: string
    preview_trigger: string
    write_trigger: string
  }
  usage: {
    entrypoint: string
    purpose: string
    write_policy: string
  }
  memory_policy: {
    includes_full_body: boolean
    asset_policy: string
    loading: string
  }
  counts: PackageManifest['counts'] & {
    items: number
    markdown_files: number
    asset_references: number
  }
  report: {
    added: number
    updated: number
    skipped: number
    failed: number
    stale: number
    repaired: number
    exported_items: number
    exported_files: number
    body_fields_omitted: number
    warnings: string[]
  }
  items: LlmWikiPackItem[]
  files: PackageManifestFile[]
  written_to?: string
}

export type ApiLlmWikiExportResponse = {
  pack: LlmWikiPack
}

export type ApiAiPreflightResponse = {
  provider: string
  ok: boolean
  enabled?: boolean
  checks?: Record<string, boolean>
  codex_cli?: string
  model?: string
  model_provider?: string
  base_url?: string
  codex_home?: string
  ran_model: boolean
  message: string
}

export type ApiSystemRepairRunResponse = {
  report: Record<string, unknown>
  remaining: ApiSystemRepairResponse
}

export type SystemMetrics = {
  counts: {
    nodes: number
    quizzes: number
    open_questions: number
    active_ai_jobs: number
    failed_ai_jobs: number
    due_reviews: number
  }
  storage: {
    content_bytes: number
    db_bytes: number
    generated_bytes: number
    project_related_bytes: number
    github_repo_bytes: number
    github_repo_fallback_tracked_bytes: number
    partitions: StoragePartition[]
    exclusive_partitions: StoragePartition[]
    explained_project_bytes: number
  }
  paths: {
    project: string
    content: string
    db: string
    generated: string
  }
  github: {
    bytes: number
    source: string
    url: string
    message: string
    fallback_tracked_bytes: number
    cached?: boolean
  }
  collected_at?: string
  collection_ms?: number
  cached?: boolean
  refreshing?: boolean
  cache?: {
    cached: boolean
    refreshing: boolean
    ttl_seconds: number
    refresh_after: string
  }
  ai: {
    ok: boolean
    enabled?: boolean
    message: string
    provider?: string
    checks?: Record<string, boolean>
    model?: string
    model_provider?: string
    base_url?: string
    codex_home?: string
  }
}

export type StoragePartition = {
  key: string
  label: string
  bytes: number
  path: string
  summary: string
  kind: string
}

export type ApiSystemMetricsResponse = SystemMetrics

export type GraphItem = {
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

export type GraphPayload = {
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

export type ApiGraphResponse = GraphPayload

export type ApiErrorBody = {
  detail?: string | Array<{ loc?: Array<string | number>; msg?: string; type?: string }>
}

export type ViewMode = 'nodes' | 'quizzes' | 'question-queue' | 'review' | 'bite' | 'graph' | 'health' | 'sync'

export type SyncDevice = {
  id: string
  name: string
  scopes: string[]
  createdAt: string
  lastSeenAt: string
  revokedAt?: string
}

export type SyncHealth = {
  protocolVersion: number
  serverId: string
  pairedDevices: number
  advertisedBaseUrl?: string
}

export type SyncPairingToken = {
  token: string
  expiresAt: string
  endpoint: string
  pairingPayload: string
}

export type ApiSyncHealthResponse = SyncHealth

export type ApiSyncPairingTokenResponse = SyncPairingToken

export type ApiSyncDevicesResponse = {
  devices: SyncDevice[]
}

export type ApiSyncDeviceScopesResponse = {
  device: SyncDevice
}

export type ApiSyncRevokeResponse = {
  revoked: string
}

export type AiDraftScope = 'question' | 'selected' | 'page'
