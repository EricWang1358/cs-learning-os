# Desktop Home Dashboard Design

## Goal

Replace the implicit “open the first node” desktop root route with a dense, task-oriented dashboard that preserves every existing workspace while making recent learning, review work, assistant queue, sync health, and the knowledge-graph entry visible at a glance.

## Decisions

- `/` becomes a dedicated `home` view; `/nodes` remains the explicit library/list route.
- The home screen is a dashboard, not a marketing landing page: compact metric strip, one primary continue-learning row, a lightweight KG summary, and dense operational tables.
- The KG preview is data-only. Three.js and `react-force-graph-3d` stay behind the existing lazy `/knowledge-graph` route.
- Existing routes and mutations remain unchanged: `/nodes/:slug`, `/quizzes`, `/review`, `/queue`, `/graph`, `/knowledge-graph`, `/health`, and `/sync`.
- Existing Binance-inspired colors remain the source of truth: dark surfaces, yellow accent, muted text, green success, red danger. New dashboard structure uses existing tokens rather than a new visual theme.

## Architecture

`App.tsx` owns route selection and navigation only for the new home view. `HomeDashboard.tsx` owns dashboard rendering and user actions. `homeData.ts` owns the parallel read-only API requests and normalization into a small `HomeSummary` type. The dashboard receives one summary object plus navigation callbacks, so it does not know about App-wide node, quiz, AI, or sync state.

The loader reads these existing endpoints in parallel:

- `/api/search?sort=last-read` for the most recently read node
- `/api/areas` for total node count
- `/api/review/due?limit=50` for due review count
- `/api/reader-questions?status=active` for assistant queue count
- `/api/bite/daily` for Daily Bite availability
- `/api/kg/questions` and `/api/kg/bottlenecks?minDistinctQuestions=1&limit=10` for KG summary
- `/api/sync/v1/health` for desktop-to-phone status

Each section can render partial data. A failed optional request produces a localized unavailable state and does not blank the rest of the dashboard. The loader exposes `isLoading`, `error`, and `summary` so the first render is deterministic.

## Interaction Model

- “Resume reading” navigates to the recent node detail route and preserves focus only when explicitly requested.
- “Daily review”, “Daily Bite”, “Q Queue”, “Open KG”, “Create node”, and “Open library” navigate to their existing routes or forms.
- The primary navigation exposes Home, Library, Review, KG, Queue, and System; existing side navigation remains available inside workspaces.
- No dashboard card invents progress values. Metrics use data already returned by the backend; unknown values render `--`.

## Visual Rules

- Use compact rows and separators instead of nested rounded cards.
- Keep radii at the existing small control scale; major sections use only a thin border and surface contrast.
- Preserve keyboard focus rings, disabled states, loading states, and readable text wrapping at mobile widths.
- Keep Three.js out of the home bundle and avoid adding a new charting dependency.

## Testing

- Add a route regression that `/` renders `HomeDashboard` and does not navigate to `/nodes/<first-slug>`.
- Add a frontend smoke flow that verifies the home metrics, resume link, KG link, and library navigation.
- Run TypeScript/Vite build, existing navigation regression, graph layout regression, and the new home smoke check.
