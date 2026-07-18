export type LibraryDateBucket = 'today' | 'two-days' | 'week' | 'older'

type LibraryNodeForSort = {
  updated_at: string
  display_order: number
  title: string
}

const BUCKET_ORDER: LibraryDateBucket[] = ['today', 'two-days', 'week', 'older']
const BEIJING_TIME_ZONE = 'Asia/Shanghai'

/** Return a stable Beijing calendar-day key without depending on the browser locale. */
function beijingDateKey(value: string | Date): string {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: BEIJING_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)
  const year = parts.find((part) => part.type === 'year')?.value ?? ''
  const month = parts.find((part) => part.type === 'month')?.value ?? ''
  const day = parts.find((part) => part.type === 'day')?.value ?? ''
  return year && month && day ? `${year}-${month}-${day}` : ''
}

function calendarDayDistance(from: string, to: string): number {
  const fromTime = Date.parse(`${from}T00:00:00+08:00`)
  const toTime = Date.parse(`${to}T00:00:00+08:00`)
  if (!Number.isFinite(fromTime) || !Number.isFinite(toTime)) return Number.POSITIVE_INFINITY
  return Math.floor((toTime - fromTime) / 86_400_000)
}

/**
 * Bucket a node by its updated_at date using Beijing calendar boundaries.
 * Invalid or future timestamps are treated as older so they do not appear in
 * the current-day sections by accident.
 */
export function bucketForUpdatedAt(updatedAt: string, now: Date): LibraryDateBucket {
  const updatedTime = Date.parse(updatedAt)
  if (!Number.isFinite(updatedTime) || updatedTime > now.getTime()) return 'older'

  const updatedKey = beijingDateKey(updatedAt)
  const nowKey = beijingDateKey(now)
  if (!updatedKey || !nowKey) return 'older'

  const age = calendarDayDistance(updatedKey, nowKey)
  if (age === 0) return 'today'
  if (age === 1) return 'two-days'
  if (age >= 2 && age <= 7) return 'week'
  return 'older'
}

/** Sort by recent update, then sequence number, then title, without mutating input. */
export function sortLibraryNodes<T extends LibraryNodeForSort>(nodes: T[]): T[] {
  return [...nodes].sort((left, right) => {
    const leftTime = Date.parse(left.updated_at)
    const rightTime = Date.parse(right.updated_at)
    const leftValid = Number.isFinite(leftTime)
    const rightValid = Number.isFinite(rightTime)
    if (leftValid && rightValid && leftTime !== rightTime) return rightTime - leftTime
    if (leftValid !== rightValid) return leftValid ? -1 : 1

    const leftOrder = Number.isFinite(left.display_order) ? left.display_order : Number.POSITIVE_INFINITY
    const rightOrder = Number.isFinite(right.display_order) ? right.display_order : Number.POSITIVE_INFINITY
    if (leftOrder !== rightOrder) return leftOrder - rightOrder
    return left.title.localeCompare(right.title, 'en', { sensitivity: 'base' })
  })
}

/** Group nodes in display order, omitting empty buckets. */
export function groupLibraryNodes<T extends { updated_at: string }>(nodes: T[], now: Date): Map<LibraryDateBucket, T[]> {
  const groups = new Map<LibraryDateBucket, T[]>()
  for (const node of nodes) {
    const bucket = bucketForUpdatedAt(node.updated_at, now)
    const group = groups.get(bucket)
    if (group) group.push(node)
    else groups.set(bucket, [node])
  }

  return new Map(BUCKET_ORDER.flatMap((bucket) => {
    const group = groups.get(bucket)
    return group?.length ? [[bucket, group] as const] : []
  }))
}
