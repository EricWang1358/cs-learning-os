# Desktop Home Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dense, data-correct desktop home dashboard at `/` while preserving existing workspaces and keeping KG 3D code lazy-loaded.

**Architecture:** Add a `home` route mode, a focused `HomeDashboard` component, and a `homeData` loader that parallelizes existing read-only APIs into a small summary type. Keep mutation/navigation callbacks in `App.tsx` and keep existing routes unchanged.

**Tech Stack:** React 19, TypeScript, React Router, existing CSS tokens, Vite, Playwright smoke scripts.

---

### Task 1: Add home domain types and loader

**Files:**
- Create: `app/src/homeData.ts`
- Modify: `app/src/types/api.ts`
- Test: `app/scripts/home_dashboard_regression.mjs`

- [ ] **Step 1: Write a failing browser regression**

  Assert `/` exposes a `Home dashboard` region, a `Continue learning` action, and a `Knowledge graph` section without redirecting to `/nodes/*`.

- [ ] **Step 2: Run the regression and verify it fails**

  Run `npm run test:home-dashboard` from `app/`. Expected failure: the current root route redirects to the first node and no home region exists.

- [ ] **Step 3: Define `HomeSummary` and parallel loader**

  Export a summary with recent node, area total, due review count, active question count, Daily Bite availability, KG root/bottleneck counts, and sync health. Use `Promise.all` for independent requests and return localized fallback fields when optional requests fail.

- [ ] **Step 4: Run TypeScript**

  Run `npm run build`. Expected: the new types and loader compile before the view is wired.

### Task 2: Add the HomeDashboard presentation

**Files:**
- Create: `app/src/components/HomeDashboard.tsx`
- Modify: `app/src/App.css`

- [ ] **Step 1: Add semantic sections and action callbacks**

  Render a compact metric strip, continue-learning row, review/Bite/queue actions, KG summary, assistant queue, reading tracks, and quick capture actions. Use `aria-label` regions and real `<button>` elements for commands.

- [ ] **Step 2: Apply existing visual tokens**

  Use the current dark surface, yellow accent, muted text, success and danger tokens. Keep sections dense with separators and small control radii; add responsive rules for one-column mobile layout.

- [ ] **Step 3: Add loading/error/empty states**

  Each optional summary block displays `--`, “Unavailable”, or an inline error without hiding healthy sections.

### Task 3: Wire the root route without changing existing workspaces

**Files:**
- Modify: `app/src/lib/routes.ts`
- Modify: `app/src/types/api.ts`
- Modify: `app/src/App.tsx`

- [ ] **Step 1: Add `home` to `ViewMode` and route parsing**

  Map only `pathname === '/'` to `home`; keep `/nodes` mapped to `nodes`.

- [ ] **Step 2: Load home data only for `home`**

  Add a focused effect branch that invokes `loadHomeSummary`, stores loading/error state, and does not invoke the old “open top node” redirect.

- [ ] **Step 3: Render `HomeDashboard` before node workspaces**

  Pass navigation callbacks for existing routes. Keep the existing sidebar available, but label Home and Library as separate primary entries.

- [ ] **Step 4: Verify route behavior manually**

  Open `/`, `/nodes`, `/nodes/binary-search`, `/knowledge-graph`, and `/graph`; confirm only `/` renders the dashboard.

### Task 4: Add and run regression coverage

**Files:**
- Create: `app/scripts/home_dashboard_regression.mjs`
- Modify: `app/package.json`

- [ ] **Step 1: Assert home behavior and navigation**

  Verify dashboard rendering, non-redirecting root URL, recent-node resume link, KG navigation, and Library navigation at desktop and mobile viewports.

- [ ] **Step 2: Run focused regression**

  Run `npm run test:home-dashboard`. Expected: pass.

- [ ] **Step 3: Run full frontend verification**

  Run `npm run build`, `npm run test:navigation-focus`, `npm run test:graph-layout`, and `git diff --check`. Expected: all commands exit 0.

- [ ] **Step 4: Commit the implementation**

  Commit only the home route/component/loader/types/test files and the intended CSS changes with message `feat(desktop): add dense home dashboard`.
