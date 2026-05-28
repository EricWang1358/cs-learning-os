# Implementation Log

## 2026-05-28: Backend Skeleton

Completed:
- Created `backend/` with SQLite schema, Markdown ingest, and FastAPI route definitions.
- Added `backend/requirements.txt`.
- Added `backend/requirements-dev.txt` for API smoke tests.
- Ingested 5 Markdown nodes into `var/knowledge.db`.
- Verified SQLite contents:
  - 5 nodes
  - 15 tags
  - FTS search for `graph` returns `graph-traversal`
- Verified FastAPI endpoints with `backend/smoke_test.py`:
  - `GET /api/health`
  - `GET /api/nodes`
  - `GET /api/search?q=graph`
  - `GET /api/nodes/binary-search`

Notes:
- Runtime database files live under `var/` and are ignored by Git.
- Markdown remains the source of truth.
- Backend dependencies are installed in the project-level `.venv/`, not globally.

## 2026-05-28: Frontend Scaffold

Completed:
- Upgraded Node through `nvm` to `v22.22.3`.
- Created the Vite React TypeScript app in `app/`.
- Installed npm dependencies locally under `app/node_modules/`.
- Verified `npm run build` passes.

Notes:
- The first build needed elevated permissions because TypeScript writes incremental build info under `node_modules/.tmp`.
- The default Vite template was replaced in the first UI implementation pass.

## 2026-05-28: Frontend QA Skill

Completed:
- Checked the official curated skill list for an existing frontend-related skill.
- Installed `playwright-interactive` from `openai/skills` using git fallback after the zip download was reset.
- Read the installed skill instructions.

Notes:
- Restart Codex to pick up newly installed skills automatically.
- `playwright-interactive` is strongest for persistent UI debugging and QA, not React architecture guidance.
- It adds a stricter QA loop: inventory user-visible claims, test controls, inspect visual states, verify viewport fit, and capture evidence.

## 2026-05-28: Frontend Development Skills

Completed:
- Installed `react-best-practices` from `vercel-labs/agent-skills`.
- Installed `web-design-guidelines` from `vercel-labs/agent-skills`.
- Read both installed skill instructions.

Notes:
- Restart Codex to pick up newly installed skills automatically.
- `react-best-practices` is the primary React performance and implementation reference.
- `web-design-guidelines` is the UI review reference and fetches the latest Vercel web interface guidelines before review.
- These skills complement `playwright-interactive`: implementation guidance first, then browser/visual QA.

## 2026-05-28: First React Workbench UI

Completed:
- Replaced the default Vite screen with a three-column knowledge workbench.
- Added API-backed node listing through `GET /api/nodes`.
- Added FTS-backed global search through `GET /api/search`.
- Added node detail loading through `GET /api/nodes/{slug}`.
- Added stable area navigation and Archive entry.
- Added responsive mobile layout.
- Added `app/scripts/frontend_smoke_test.mjs`.
- Added `npm run test:smoke`.
- Installed Playwright as an app dev dependency.

Verified:
- `npm run lint`
- `npm run build`
- `npm run test:smoke`

Smoke test coverage:
- Desktop initial view loads and shows indexed nodes.
- Searching `graph` shows `Graph Traversal`.
- Area navigation to `Projects` shows `Project Pattern: CRUD App`.
- Archive entry handles an empty archive state.
- Mobile initial view loads.

Generated QA screenshots:
- `generated/qa/desktop-home.png`
- `generated/qa/desktop-search-graph.png`
- `generated/qa/desktop-projects.png`
- `generated/qa/mobile-home.png`

Notes:
- Browser MCP setup failed in this session due to a local `node_repl` sandbox startup issue, so Playwright smoke tests were used for UI verification.
- Area navigation is intentionally stable and does not shrink to search results.

## 2026-05-28: Detail Reading and Search Fixes

Completed:
- Replaced raw `<pre>` note display with lightweight Markdown rendering for headings, paragraphs, and lists.
- Made node links clickable; clicking a related/prerequisite link selects that node.
- Added focus reading mode so the detail panel can expand while the left navigation and card list collapse.
- Added `VITE_API_BASE` support so the frontend can target different local API ports.
- Hardened backend search:
  - sanitize user input before SQLite FTS5 `MATCH`
  - fall back to `LIKE` search when FTS cannot safely handle the input
  - avoid crashing on regex-like queries such as `graph.*(`
- Extended frontend smoke test to cover regex-like search, clickable links, focus mode, and configurable frontend URL.

Verified:
- `npm run lint`
- `npm run build`

Pending verification:
- Re-run backend smoke test and frontend smoke test against a freshly restarted API server.
- This was not completed in this pass because the environment rejected new local service startup commands.

## 2026-05-28: Standard A and GDB/C Content

Completed:
- Defined `Standard A: Bilingual Practical Exam Note`.
- Added the future workflow rule: ask which content standard to use before adding content unless the user explicitly names one.
- Added Standard A documentation to `skill/references/content-standards.md` and `docs/content-standards.md`.
- Added GDB/C nodes based on the user's exam prompt:
  - `c-memory-basics`
  - `gdb-basics`
  - `gdb-disassemble`
  - `gdb-stepi`
  - `gdb-examine-memory`
  - `gdb-examine-stack-string`
- Updated `content/nodes/cs-fundamentals/index.md`.
- Re-ingested Markdown into SQLite.

Verified:
- Database ingest reports 11 total nodes.
- API search handles `x/20xw $sp`.
- API search handles regex-like input `graph.*(` without crashing.
- `gdb-examine-memory` returns linked nodes.
- `npm run lint`
- `npm run build`

Notes:
- Python/PowerShell console output may display Chinese as mojibake, but `ascii()` checks confirmed the database stores correct Unicode.
- Static `content/index.json` regeneration hit a local file permission error; the React app uses FastAPI/SQLite and does not depend on this static index.

## 2026-05-28: Reader Question Expansion and Tutorial Tone

Completed:
- Added reader-question handling rules to `skill/references/curation-rules.md`.
- Updated Standard A to require a more tutorial-like tone:
  - start from likely confusion
  - show what to type
  - explain how to read the result
  - keep English and Chinese aligned
- Added reusable bridge nodes:
  - `c-language-characteristics`
  - `debugging-levels-vscode-python-vs-gdb`
- Reworked `gdb-basics` to explain how GDB differs from VSCode Python breakpoint debugging.
- Rewrote `gdb-disassemble` with a clearer tutorial path and examples of instruction categories such as `mov`, `add`, `cmp`, `je`, `call`, `ret`, `push`, and `pop`.
- Rewrote `gdb-stepi` to explain that one step means one machine instruction at the instruction pointer, not one C line, Python statement, byte, or clock cycle.
- Re-ingested Markdown into SQLite.

Verified:
- Database ingest reports 13 total nodes.
- API can fetch `c-language-characteristics`.
- API can search `VSCode Python GDB`.
- `gdb-stepi` returns linked nodes.
- `npm run lint`
- `npm run build`

Design note:
- Reader questions that clarify a local sentence should update the source node.
- Reader questions that expose reusable prerequisites or cross-topic bridges should become new linked nodes.

## 2026-05-28: Markdown Code Rendering

Completed:
- Updated the React Markdown renderer to parse fenced code blocks.
- Added code block rendering with language captions.
- Added inline code rendering for backtick spans inside paragraphs and list items.
- Styled code blocks as readable scrollable code frames.
- Updated frontend smoke test to verify code block rendering on a GDB node.

Verified:
- `npm run lint`
- `npm run build`
- `npm run test:smoke`

Notes:
- This is a general renderer fix and applies to all current and future Markdown nodes.

## 2026-05-28: Markdown Block Parsing Screenshot Regression

Completed:
- Fixed the Markdown renderer so headings, paragraphs, lists, and fenced code blocks become separate visual blocks instead of one oversized heading.
- Added a smoke-test regression check for `Debugging Loop`, the screenshot-reported case.
- Added a dedicated QA screenshot for the corrected Markdown rendering.

Verified:
- `npm run lint`
- `npm run build`
- `npm run test:smoke`

Notes:
- Screenshot review is part of the frontend QA loop; a passing build is not enough when the rendered reading experience looks wrong.

## 2026-05-28: Quiz Bank Foundation

Completed:
- Added a separate quiz-bank content layer under `content/quizzes/`.
- Added SQLite tables for quizzes, quiz tags, linked review nodes, quiz sources, and quiz FTS.
- Added backend API endpoints:
  - `GET /api/quizzes`
  - `GET /api/quizzes/{quiz_id}`
  - `GET /api/quiz-search`
- Added React `Practice / Quiz Bank` mode.
- Added Standard Q documentation for future quiz items.
- Added the first x86-64 `%rax` tracing quiz item.
- Added quiz-bank design notes to `docs/quiz-bank-design.md`.

Verified:
- Re-ingested SQLite: 13 nodes and 1 quiz.
- Backend smoke test passes against in-process FastAPI test client.
- `npm run lint`
- `npm run build`

Pending verification:
- Frontend smoke screenshot against the running local browser app is blocked because port `8000` is still serving an older FastAPI process that returns 404 for `/api/quizzes`.

Notes:
- The quiz-bank layer is intentionally separate from knowledge nodes so future daily review, weights, and attempt history can attach to quiz IDs.

## 2026-05-28: GDB and x86-64 Reading Expansion

Completed:
- Expanded the GDB/assembly learning path beyond basic commands.
- Added quiz-required x86-64 nodes:
  - `x86-64-registers`
  - `x86-64-addressing-and-leaq`
  - `x86-64-cmp-and-jumps`
  - `x86-64-instruction-cheatsheet`
- Added `bomb-lab-debugging-workflow` as a method-focused Bomb Lab guide.
- Updated `gdb-disassemble` to point toward the new instruction-reading path.
- Updated the `%rax` tracing quiz to link to the exact prerequisite nodes it tests.
- Re-ingested Markdown into SQLite.

Verified:
- Database ingest reports 18 nodes and 1 quiz.
- Backend smoke test passes.
- `npm run lint`
- `npm run build`

Notes:
- Bomb Lab coverage should focus on a repeatable reverse-engineering workflow and official/course references, not answer dumping.

## 2026-05-28: Bomb Lab Answer Shapes

Completed:
- Added `bomb-lab-answer-shapes` as a pattern-oriented reference for what solved Bomb Lab answers commonly look like.
- Included common phase shapes:
  - exact string
  - six-number sequence
  - switch/jump-table index-value pair
  - recursive target
  - character mapping
  - linked-list permutation
  - secret-phase tree path
- Linked the answer-shape node from the Bomb Lab workflow and CS fundamentals index.

Notes:
- These examples are pattern references, not guaranteed answers for a specific personalized bomb binary.

## 2026-05-28: Data-Driven Track Navigation

Completed:
- Added `track` and `order` frontmatter support for knowledge nodes.
- Added SQLite columns `track` and `display_order` with lightweight migration support.
- Added `GET /api/areas/{area}/tracks`.
- Updated CS fundamentals nodes into reader-facing tracks:
  - `c-and-memory`
  - `gdb-debugging`
  - `x86-64-assembly`
  - `bomb-lab`
  - `networking`
- Added React track pills that appear for selected areas and filter nodes by track.
- Added `docs/navigation-design.md`.

Verified:
- Re-ingested SQLite: 19 nodes and 1 quiz.
- Backend smoke test passes.
- `npm run lint`
- `npm run build`

Notes:
- Reading order is now content metadata, not React hardcoding.

## 2026-05-28: Shark Tank Passcode Quiz

Completed:
- Added `shark-tank-passcode-calling-convention` as a Standard Q quiz item.
- Added `x86-64-calling-convention` to explain register-based argument passing and return values.
- Updated `x86-64-cmp-and-jumps` with the `sete` plus `movzbl` boolean-return pattern.
- Updated `x86-64-instruction-cheatsheet` with `sete`, `setne`, and `movzbl`.
- Re-ingested Markdown into SQLite.

Verified:
- Database ingest reports 20 nodes and 2 quizzes.
- Backend smoke test passes.
- `npm run lint`
- `npm run build`

Notes:
- The passcode derivation is `2 * code + 0x137 = 0x7a69`, so `code = 0x3c99` (`15513` decimal).
