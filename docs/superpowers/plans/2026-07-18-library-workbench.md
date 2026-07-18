# Library Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the desktop Library as a dense date-grouped workbench with stable recent-first sorting, URL-preserving search, collapsible groups, and desktop-only inline sequence editing.

**Architecture:** Keep the existing `/nodes` route and API list contract. Add a pure frontend grouping/sorting module so date boundaries and tie-breakers are testable without React. Add a narrow backend endpoint for updating one node's `display_order` with optimistic version and Area + Track uniqueness validation. Keep list presentation in `App.tsx` but isolate row/group rendering and sequence editing into focused components.

**Tech Stack:** React 19, TypeScript, Vite, FastAPI, SQLite, pytest, Playwright.

---

## File Map

- Create: `app/src/libraryGrouping.ts` for Beijing-time date bucketing, deterministic sorting, and group visibility state.
- Create: `app/src/libraryGrouping.test.ts` for pure grouping/sorting tests.
- Create: `app/src/LibraryNodeRow.tsx` for dense row markup and Enter-only sequence editing.
- Create: `app/src/LibraryDateGroup.tsx` for group header, collapse state, and row rendering.
- Modify: `app/src/SearchHeaderControls.tsx` for the compact non-sticky workbench toolbar and clear action.
- Modify: `app/src/App.tsx` to build Library groups, preserve URL state, wire order updates, and render the new components.
- Modify: `app/src/App.css` for the workbench toolbar, date separators, dense rows, hover editing affordance, and responsive behavior.
- Modify: `backend/node_router.py` to expose `PATCH /api/nodes/{slug}/display-order`.
- Modify: `backend/node_lifecycle_service.py` to validate and persist order changes with a version check.
- Modify: `backend/api_serializers.py` only if the endpoint needs a dedicated error/result serializer.
- Create: `backend/test_node_display_order.py` for success, duplicate, invalid integer, missing node, and stale-version behavior.
- Modify: `app/scripts/frontend_smoke_test.mjs` with focused Library grouping/order assertions if the existing smoke flow can run against the seeded backend.
- Create: `app/scripts/library_workbench_regression.mjs` for a short deterministic Playwright UI regression.

## Task 1: Pure Library grouping and sorting model

**Files:**
- Create: `app/src/libraryGrouping.ts`
- Create: `app/src/libraryGrouping.test.ts`

- [ ] **Step 1: Write failing tests for Beijing date buckets and tie-breakers**

Cover these exact cases:

```ts
const now = '2026-07-18T10:00:00+08:00'
expect(bucketForUpdatedAt('2026-07-18T01:00:00+08:00', now)).toBe('today')
expect(bucketForUpdatedAt('2026-07-17T23:59:00+08:00', now)).toBe('two-days')
expect(bucketForUpdatedAt('2026-07-16T12:00:00+08:00', now)).toBe('week')
expect(bucketForUpdatedAt('2026-07-10T23:59:00+08:00', now)).toBe('older')
```

Also assert `updated_at DESC`, `display_order ASC`, title fallback, and hidden empty groups.

- [ ] **Step 2: Run the focused test and verify it fails**

Run `node --test app/src/libraryGrouping.test.ts`. Node 22's built-in test runner strips the type annotations in this dependency-free pure module. Expected result: missing module/functions.

- [ ] **Step 3: Implement pure helpers**

Export:

```ts
export type LibraryDateBucket = 'today' | 'two-days' | 'week' | 'older'
export function bucketForUpdatedAt(updatedAt: string, now: Date): LibraryDateBucket
export function sortLibraryNodes<T extends { updated_at: string; display_order: number; title: string }>(nodes: T[]): T[]
export function groupLibraryNodes<T extends { updated_at: string }>(nodes: T[], now: Date): Map<LibraryDateBucket, T[]>
```

Use Beijing calendar-day keys with `Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' })`; do not use browser-local midnight.

- [ ] **Step 4: Run focused tests and commit**

Run `node --test app/src/libraryGrouping.test.ts` and `npm run lint`. Commit as `test: define library date grouping and ordering`.

## Task 2: Desktop display-order API

**Files:**
- Modify: `backend/node_router.py`
- Modify: `backend/node_lifecycle_service.py`
- Create: `backend/test_node_display_order.py`

- [ ] **Step 1: Write failing API tests**

Seed three nodes in one Area + Track and assert:

```python
response = client.patch(
    '/api/nodes/alpha/display-order',
    json={'display_order': 20, 'expected_updated_at': original_updated_at},
)
assert response.status_code == 200
assert response.json()['node']['display_order'] == 20
```

Add tests for duplicate order (`409`), stale `expected_updated_at` (`409`), non-positive/non-integer values (`422`), and unknown slug (`404`). Ensure a rejected request leaves Markdown and SQLite unchanged.

- [ ] **Step 2: Implement service validation and transaction**

Add a service function that loads the node, checks the expected timestamp, parses a positive integer, queries the same `area` + `track` for duplicate `display_order`, updates the SQLite row, and returns the refreshed row. Update the node's `updated_at` consistently with existing lifecycle writes.

- [ ] **Step 3: Add the route**

Register `PATCH /api/nodes/{slug}/display-order`, pass through structured validation errors, and serialize the updated node using the existing node serializer.

- [ ] **Step 4: Run backend tests and commit**

Run `python -m pytest backend/test_node_display_order.py -q` and the existing node smoke subset. Commit as `feat: add desktop node display order endpoint`.

## Task 3: Dense row and date-group components

**Files:**
- Create: `app/src/LibraryNodeRow.tsx`
- Create: `app/src/LibraryDateGroup.tsx`
- Modify: `app/src/App.tsx`

- [ ] **Step 1: Add row component tests/fixtures**

Define props for a node, selected state, `onOpen`, and `onOrderCommit`. Render the sequence number as a button-like text control with `onDoubleClick`; only an Enter key calls `onOrderCommit`. Escape restores the original value. Blur only exits edit mode.

- [ ] **Step 2: Implement `LibraryNodeRow`**

Use accessible labels (`Edit order for <title>`, `Library node <title>`), preserve node navigation, and expose `data-testid="library-node-row"` and `data-testid="library-node-order"` for regression tests.

- [ ] **Step 3: Implement `LibraryDateGroup`**

Render a semantic `<section>` with a button group header, count, chevron state, and rows. Keep group state controlled by `App.tsx`; do not persist it to the backend.

- [ ] **Step 4: Run lint and commit**

Run `npm run lint`. Commit as `feat: add dense library row and date groups`.

## Task 4: Workbench toolbar and App integration

**Files:**
- Modify: `app/src/SearchHeaderControls.tsx`
- Modify: `app/src/App.tsx`
- Modify: `app/src/App.css`

- [ ] **Step 1: Preserve URL behavior with tests**

Keep `q`, `area`, `track`, and sort parameters when search or sort changes. Add a regression assertion that changing sort after typing retains `q`.

- [ ] **Step 2: Implement compact toolbar**

Replace the large sticky search header only for Library with a compact toolbar containing search input, clear button, Area/Track controls, sort control, and Expand all/Collapse all actions. Keep Quiz and Queue semantics while applying shared spacing and focus styles.

- [ ] **Step 3: Integrate grouping**

In `App.tsx`, compute the sorted visible node list, group it using the pure helpers, initialize `today`/`two-days` open and older groups closed, and render `LibraryDateGroup` instead of one flat `.node-list` for `viewMode === 'nodes'`.

- [ ] **Step 4: Wire order editing**

On Enter, validate a positive integer locally, reject known duplicate values in the current Area + Track without a network request, then call the new PATCH endpoint with `expected_updated_at`. On `409`, restore the original value and show the application dialog. On success, replace the node from the server response and recompute ordering.

- [ ] **Step 5: Add maintenance warning**

Compute missing/duplicate sequence numbers within each Area + Track and show a compact non-blocking warning above the groups. It must link to the affected filtered view but never mutate data.

- [ ] **Step 6: Run lint/build and commit**

Run `npm run lint` and `npm run build`. Commit as `feat: integrate library workbench view`.

## Task 5: Visual and responsive regression

**Files:**
- Modify: `app/src/App.css`
- Create: `app/scripts/library_workbench_regression.mjs`

- [ ] **Step 1: Add Playwright regression flow**

Against `http://127.0.0.1:5173/nodes`, assert the toolbar is not sticky, date headings are present, older groups start collapsed, expand/collapse changes visible row counts, search updates `q`, and order editing only saves after Enter.

- [ ] **Step 2: Add desktop/mobile screenshots**

Capture a 1440px desktop Library and 390px mobile Library. Assert no horizontal overflow, selected rows keep their left rail, and the toolbar remains usable on mobile.

- [ ] **Step 3: Run all verification**

Run:

```powershell
npm run lint
npm run build
node app/scripts/library_workbench_regression.mjs
python -m pytest backend/test_node_display_order.py -q
```

Expected: all commands exit 0; Vite may retain the existing chunk-size warning.

- [ ] **Step 4: Commit verification artifacts only if tracked by the existing QA convention**

Do not commit generated screenshots unless the repository's existing QA workflow tracks them. Keep temporary screenshots under `generated/qa/` and remove untracked temporary files before final handoff.
