/// <reference types="node" />

import assert from 'node:assert/strict'
import test from 'node:test'
import {
  bucketForUpdatedAt,
  groupLibraryNodes,
  isMaintainedDisplayOrder,
  sortLibraryNodes,
  type LibraryDateBucket,
} from './libraryGrouping.ts'

const now = new Date('2026-07-18T10:00:00+08:00')

test('uses Beijing calendar days for date buckets', () => {
  assert.equal(bucketForUpdatedAt('2026-07-18T01:00:00+08:00', now), 'today')
  assert.equal(bucketForUpdatedAt('2026-07-17T23:59:00+08:00', now), 'two-days')
  assert.equal(bucketForUpdatedAt('2026-07-16T12:00:00+08:00', now), 'week')
  assert.equal(bucketForUpdatedAt('2026-07-11T12:00:00+08:00', now), 'week')
  assert.equal(bucketForUpdatedAt('2026-07-10T23:59:00+08:00', now), 'older')
})

test('handles Beijing midnight boundaries independent of local timezone', () => {
  assert.equal(bucketForUpdatedAt('2026-07-17T15:59:59Z', now), 'two-days')
  assert.equal(bucketForUpdatedAt('2026-07-17T16:00:00Z', now), 'today')
  assert.equal(bucketForUpdatedAt('2026-07-18T01:59:59Z', now), 'today')
})

test('sorts updated time descending with deterministic tie-breakers', () => {
  const nodes = [
    { title: 'Zulu', display_order: 2, updated_at: '2026-07-18T01:00:00+08:00' },
    { title: 'Alpha', display_order: 1, updated_at: '2026-07-18T01:00:00+08:00' },
    { title: 'Beta', display_order: 1, updated_at: '2026-07-18T01:00:00+08:00' },
    { title: 'Newest', display_order: 99, updated_at: '2026-07-19T01:00:00+08:00' },
  ]

  const sorted = sortLibraryNodes(nodes)
  assert.deepEqual(sorted.map((node) => node.title), ['Newest', 'Alpha', 'Beta', 'Zulu'])
  assert.deepEqual(nodes.map((node) => node.title), ['Zulu', 'Alpha', 'Beta', 'Newest'])
})

test('treats default 1000 as an unmaintained display order', () => {
  assert.equal(isMaintainedDisplayOrder(1), true)
  assert.equal(isMaintainedDisplayOrder(1000), false)
  assert.equal(isMaintainedDisplayOrder(0), false)

  const nodes = [
    { title: 'Placeholder', display_order: 1000, updated_at: '2026-07-18T01:00:00+08:00' },
    { title: 'Maintained', display_order: 9, updated_at: '2026-07-18T01:00:00+08:00' },
  ]

  assert.deepEqual(sortLibraryNodes(nodes).map((node) => node.title), ['Maintained', 'Placeholder'])
})

test('groups in bucket order and omits empty groups', () => {
  const nodes = [
    { title: 'old', updated_at: '2026-07-01T01:00:00+08:00' },
    { title: 'today', updated_at: '2026-07-18T01:00:00+08:00' },
    { title: 'week', updated_at: '2026-07-14T01:00:00+08:00' },
  ]

  const groups = groupLibraryNodes(nodes, now)
  assert.deepEqual([...groups.keys()], ['today', 'week', 'older'] satisfies LibraryDateBucket[])
  assert.equal(groups.has('two-days'), false)
  assert.deepEqual(groups.get('today')?.map((node) => node.title), ['today'])
})

test('invalid timestamps fall back to older', () => {
  assert.equal(bucketForUpdatedAt('not-a-date', now), 'older')
  assert.equal(bucketForUpdatedAt('2026-07-18T11:00:00+08:00', now), 'older')
})
