# Worker Protocol

This document defines how future Codex/agent workers should split work as CS Learning OS grows. These workers are collaboration roles for development, not runtime background jobs.

## Goal

Reduce context loss, accidental cross-file regressions, and token waste by keeping each worker narrow.

The default priority is correctness of stateful flows, not maximum parallelism. Back-stack behavior, focus reading restoration, Q Queue, edit mode, and AI draft review are easy to regress when one agent holds too much vague context.

## Core Pattern

Use one coordinator and several scoped workers.

```text
Coordinator
  -> Docs/Memory Worker
  -> Frontend Worker
  -> Backend Worker
  -> Content Worker
  -> QA Worker
  -> Review Worker
```

The coordinator owns task routing and final integration. Worker outputs should be compact enough to paste into the next worker without rereading the whole repository.

## Shared Context Pack

Before substantial work, the coordinator should assemble a short context pack:

```text
Objective:
Relevant files:
State invariants:
Known risks:
Allowed write scope:
Verification required:
Unrelated dirty files:
```

Keep the context pack under roughly 120 lines. Link to docs instead of copying long sections.

## App.tsx Split Context Pack

Use this compact pack before refactoring `App.tsx`. It is a reminder, not a replacement for `docs/state-machine.md`.

Objective: split `App.tsx` without changing state semantics.

Relevant files: `app/src/App.tsx`, route helpers/components introduced by the split, API client modules touched by those components, `docs/state-machine.md`.

State invariants:

- URL is canonical for selected node, selected quiz, queue, graph, health, area, track, search query, focus mode, and Markdown section hash.
- Browser Back must restore the route, selected item, `?focus=1`, and `#section-...` target together.
- Navigation without a hash must clear stale section hashes and reset detail scroll to top; graph and health must not inherit node hashes or focus state.
- Focus reading is a reading mode, not just a layout flag. It must preserve route/shareability and only record durable reads after the intended dwell/debounce path.
- Edit mode is transient UI state. Link navigation while editing must exit edit mode or confirm discard, and focus mode must not hide save/cancel/review controls.
- AI review is human-gated. Drafts must never auto-save; only `draft_ready` jobs can be applied; failed, cancelled, rejected, or retried jobs must not resolve reader questions.
- AI apply must wait for backend success before treating Markdown, SQLite/FTS, job status, and linked questions as updated.

Known risks: duplicated route parsing after extraction, stale hash scroll effects, hidden edit controls in focus layout, queue/question state being resolved too early, and components assuming backend writes succeeded before refreshed data returns.

Allowed write scope: prefer `app/src` for split work plus narrow docs updates. Do not change backend contracts unless a Backend Worker owns the handoff.

Verification required: run the narrow frontend checks available in the repo, then smoke browser Back/hash/focus-reading, edit cancel/save, and AI review apply/reject paths if the touched code can affect them.

## Worker Roles

| Worker | Scope | Must not do |
| --- | --- | --- |
| Coordinator | Break tasks down, choose workers, protect unrelated worktree changes, decide final commit boundaries. | Implement broad changes directly when a narrower worker can do it. |
| Docs/Memory Worker | Maintain README, state-machine notes, worker protocol, release notes, AI policy, and context packs. | Invent current behavior without checking code or latest docs. |
| Frontend Worker | Work in `app/src`, especially route shells, search/sort, focus reading, editor, Q Queue, graph, and health UI. | Change backend contracts without a backend handoff. |
| Backend Worker | Work in `backend`, SQLite schema, ingest, AI jobs, health metrics, file-write compensation, and APIs. | Change UI assumptions without a frontend handoff. |
| Content Worker | Work under the active content root, usually `data/content`, and demo fixtures when explicitly selected. | Write real study material into `content-demo`, root-level `data/nodes`, root-level `data/quizzes`, or legacy `content`. |
| QA Worker | Reproduce bugs, write narrow regression checks, run lint/build/smoke, inspect browser behavior, and clean test residues. | Make product changes while debugging unless explicitly reassigned. |
| Review Worker | Review staged diffs for state regressions, data-policy violations, missing tests, and private-content leaks. | Rewrite the patch unless the coordinator asks for a fix pass. |

## Handoff Format

Each worker should end with:

```text
Changed files:
Behavior changed:
State assumptions:
Verification:
Risks left:
Recommended next worker:
```

Avoid dumping raw logs. Summarize failures with the command, failure point, and likely cause.

## Routing Rules

- Bugs involving browser Back, URL params, selected node/quiz, hashes, focus mode, or scroll restoration start with Docs/Memory plus Frontend.
- Bugs involving Markdown writes, ingest, trash/restore/delete, AI apply, or reader-question resolution start with Docs/Memory plus Backend.
- Bugs involving flaky tests, fake Codex, screenshots, local browser reproduction, or leftover smoke data start with QA.
- New learning content starts with Content, after resolving Standard A or Standard Q.
- Large refactors start with Docs/Memory to write invariants, then Frontend or Backend, then Review.

## Token Budget Rules

- Prefer `rg` summaries, file excerpts, and diffs over full-file reads.
- Do not load `data/content` unless the task is content-specific.
- Do not load historical logs unless the task asks for history.
- Prefer `docs/state-machine.md` for current state invariants and `README.md` for runtime setup.
- Preserve a short `context pack` in the handoff so the next worker can avoid rediscovery.

## Current Priority Queue

1. Stabilize stateful navigation and focus reading flows.
2. Split `App.tsx` into smaller frontend modules.
3. Replace stale frontend/design docs with project-specific guidance.
4. Add deterministic smoke mode for AI draft workflows.
5. Expand `/health` into actionable diagnostics.
6. Add `docs/ai-policy.md` and `docs/release-checklist.md`.
