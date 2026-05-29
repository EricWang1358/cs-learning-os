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

## 2026-05-29: Quiz Explanation Depth

Completed:
- Updated Standard Q to require non-skipping explanations:
  - line-by-line state changes
  - mental translation
  - operand-direction notes
  - branch decisions
  - hex arithmetic
- Expanded `x86-rax-trace-leaq-jump` with a full walkthrough for both functions.
- Expanded `shark-tank-passcode-calling-convention` with a full walkthrough covering calling convention, loop noise, `cmp`, `sete`, `movzbl`, and equation solving.
- Re-ingested Markdown into SQLite.

Verified:
- Database ingest reports 20 nodes and 2 quizzes.
- Backend smoke test passes.
- `npm run lint`
- `npm run build`

Notes:
- Future quiz additions should optimize for teaching the reasoning path, not just producing the final answer.

## 2026-05-29: x86-64 Register and mov Foundations

Completed:
- Expanded `x86-64-registers` with:
  - accumulator explanation
  - general-purpose registers
  - argument registers
  - stack registers
  - instruction pointer
  - register families such as `%rax/%eax/%ax/%al`
  - examples of what registers may represent in C-level reasoning
- Added `x86-64-mov-and-suffixes` for:
  - `movq = move quad-word`
  - suffixes `b/w/l/q`
  - source and destination operand types
  - immediate/register/memory examples
  - common beginner mistakes
- Linked the new node from quizzes and the instruction cheatsheet.
- Re-ingested Markdown into SQLite.

Verified:
- Database ingest reports 21 nodes and 2 quizzes.
- Backend smoke test passes.
- `npm run lint`
- `npm run build`

Notes:
- Basic instruction vocabulary belongs in knowledge nodes, not only inside quiz explanations.

## 2026-05-29: Detail Navigation Back Stack

Completed:
- Added a lightweight frontend history stack for detail-page jumps.
- Added a `Back` button next to `Focus reading` / `Show map`.
- Link clicks from node details and quiz linked reviews now push the current detail view before navigating.
- Back restores the previous node or quiz view.
- Updated frontend smoke test coverage for quiz-to-node link navigation and Back.

Verified:
- `npm run lint`
- `npm run build`

Notes:
- This is intentionally app-level navigation history, not full browser routing yet.

## 2026-05-29: Reader Question Inbox

Completed:
- Added `reader_questions` SQLite table for local reading annotations and unresolved questions.
- Added backend APIs:
  - `GET /api/reader-questions`
  - `POST /api/reader-questions`
  - `POST /api/reader-questions/{question_id}/resolve`
- Node and quiz detail responses now include `open_question_count`.
- Added focus-reading question panel in the React detail view.
- Added `Q to be solved` status pill when a node or quiz has open reader questions.
- Updated frontend smoke test to cover saving a reader question in focus reading.

Verified:
- Database ingest reports 21 nodes and 2 quizzes.
- Backend smoke test passes.
- `npm run lint`
- `npm run build`

Notes:
- Reader questions are stored separately from Markdown so they can later drive LLM revisions without immediately polluting source content.

## 2026-05-29: Browser Markdown Edit Mode

Completed:
- Added backend body-save endpoints:
  - `PUT /api/nodes/{slug}/body`
  - `PUT /api/quizzes/{quiz_id}/body`
- Save endpoints preserve Markdown frontmatter and replace only the body.
- Save endpoints update SQLite rows and FTS indexes immediately after writing the source file.
- Added `Edit mode` to node and quiz detail toolbars.
- Added Markdown textarea editor with `Save Markdown` and `Cancel`.
- Save asks for browser confirmation before writing to disk.
- Updated README with browser edit workflow.
- Updated frontend smoke test to cover entering and canceling edit mode.

Verified:
- Database ingest reports 21 nodes and 2 quizzes.
- Backend smoke test passes, including temporary edit and restore.
- `npm run lint`
- `npm run build`

Notes:
- Frontmatter editing remains file-only for now to avoid accidental metadata damage.

## 2026-05-29: Focus Edit Layout Compatibility

Completed:
- Fixed focus reading plus edit mode layout conflict.
- Edit mode now hides the reader-question side panel.
- Entering edit mode automatically opens focus mode.
- Focus edit mode uses a single-column wide editor layout.
- Updated frontend smoke test to assert the reader-question panel is hidden while editing.

Verified:
- `npm run lint`
- `npm run build`

Notes:
- Reading mode keeps the two-column detail plus question panel layout; edit mode prioritizes Markdown editing space.

## 2026-05-29: Edit Mode Navigation State Fix

Completed:
- Added explicit `Exit edit mode` behavior.
- Added shared edit-exit logic for toolbar exit, cancel, Back, card selection, and link navigation.
- Link navigation now exits edit mode before changing the selected node or quiz.
- Unsaved edit drafts prompt for confirmation before being discarded.
- Updated frontend smoke test to cover exiting edit mode and link navigation out of edit mode.

Verified:
- `npm run lint`
- `npm run build`

Notes:
- Edit mode is now treated as transient local UI state, never as the state of the selected node itself.

## 2026-05-29: Focus Tag Pill Stretch Fix

Completed:
- Fixed a focus/edit transition layout bug where tag pills could stretch into tall vertical capsules.
- Added explicit flex alignment and self-alignment to `.tag-row` and `.tag-row span`.
- Added `align-self: start` to focus-mode detail grid children.
- Reworked detail layout to use a stable `.detail-main` left column plus an independent reader-question side panel.
- Removed the reader-question panel's multi-row grid span so it cannot stretch unrelated rows.

Verified:
- `npm run lint`
- `npm run build`

Notes:
- The bug reproduced after entering edit mode from a long node, navigating by link, and exiting edit mode. The fix avoids splitting heading, tags, and body into separate grid rows that can inherit stale row height.

## 2026-05-29: Sticky Reader Question Panel

Completed:
- Made the focus-mode reader question panel remain sticky while reading long content.
- Changed focus-mode detail panel overflow to `visible` so sticky positioning can work reliably.
- Added `max-height` and internal scrolling to the question panel.
- Kept the panel static on narrow/mobile layouts to avoid covering the reading content.

Verified:
- `npm run lint`
- `npm run build`

Notes:
- If sticky is still blocked by an unexpected scroll container, the next fallback is a fixed mini dock for `Q to be solved`.

## 2026-05-29: Q Queue Entry

Completed:
- Added `Q Queue` as a first-class frontend mode.
- Queue loads directly from `reader_questions` via `/api/reader-questions?status=open`.
- Open questions can be selected to jump directly to their source node or quiz in focus mode.
- Added queue card styling and smoke test coverage for loading the queue.

Verified:
- Local SQLite currently has open questions, including `project-crud-app -> more detailed.`
- `npm run lint`
- `npm run build`

Notes:
- The queue avoids scanning all nodes/tags and gives future LLM workflows a direct unresolved-question inbox.

## 2026-05-29: Queue Cleanup and User Manual

Completed:
- Removed smoke-test-created open reader questions from the local SQLite queue.
- Updated backend smoke test to resolve its temporary reader question after verification.
- Updated frontend smoke test to resolve its temporary reader question after verification.
- Added `docs/使用说明书.md` with daily usage guidance and AI operating instructions.
- Updated README with Q Queue behavior.

Verified:
- Open reader question queue now contains only the user's real `project-crud-app -> more detailed.` item.
- Backend smoke test passes.
- `npm run lint`
- `npm run build`

Notes:
- Smoke tests should verify queue behavior without leaving open questions behind.

## 2026-05-29: App Shell and User Data Separation

Completed:
- Made FastAPI runtime paths configurable:
  - `CS_LEARNING_CONTENT`
  - `CS_LEARNING_DB`
- Updated `scripts/dev.ps1` to accept:
  - `-ContentDir`
  - `-DbPath`
- Updated README and data policy to describe app shell vs user data.
- Removed generated `content/index.html` and `content/index.json` from Git tracking.
- Added generated content index files to `.gitignore`.

Verified:
- Backend smoke test passes with default paths.
- `npm run lint`
- `npm run build`

Notes:
- The next migration step is to move real personal content into a private data directory or private content repository, leaving only demo/seed content in the app repository.

## 2026-05-29: Private Data Directory Migration

Completed:
- Copied the current real `content/` directory to `../cs-learning-data/content`.
- Copied the current local SQLite database to `../cs-learning-data/knowledge.db`.
- Added `content-demo/` as the Git-tracked demo content layer.
- Reduced `content-demo/` to a tiny synthetic sample instead of a second knowledge base.
- Changed backend default content path to `content-demo/`.
- Changed ingest default content path to `content-demo/`.
- Updated `scripts/dev.ps1` so this machine automatically prefers `../cs-learning-data` when present.
- Added `content/` to `.gitignore`.
- Removed real `content/` from Git tracking while keeping it on disk.

Verified:
- Demo content ingests with 2 nodes and 1 quiz, and backend smoke test passes.
- Private data re-ingests to `../cs-learning-data/knowledge.db` with 21 nodes and 2 quizzes.
- `npm run lint`
- `npm run build`

Notes:
- The app repository is now closer to an app shell. Personal knowledge lives in the external data layer.
- `knowledge.db` is treated as a local generated/user-data file, not a shared repository artifact.
- Removing `content/` from the current tree does not remove it from old Git commits; keep the repository private or rewrite history before public sharing.

## 2026-05-29: Markdown Bold Rendering

Completed:
- Added inline Markdown support for `**bold**` text.
- Applied inline rendering to headings as well as paragraphs and list items.
- Added a tiny demo heading `## **作用**` to cover the failure mode.
- Reworked frontend smoke coverage so it runs against the minimal `content-demo/` data set.

Verified:
- Demo content ingests with 2 nodes and 1 quiz.
- Backend smoke test passes.
- Frontend smoke test passes in a real browser.
- `npm run lint`
- `npm run build`

## 2026-05-29: Focus Mode Markdown TOC

Completed:
- Added a focus-mode table of contents generated from parsed Markdown headings.
- Reused the frontend Markdown parser and generated stable heading anchors.
- Added left-side sticky TOC layout for desktop focus reading.
- Kept TOC hidden in edit mode to avoid focus/edit UI conflicts.
- Added responsive fallback so the TOC stacks above content on narrower screens.

Verified:
- Frontend smoke test checks that the TOC appears in focus mode and hides in edit mode.
- Frontend smoke test checks bold headings such as `**作用**` appear correctly in the TOC.
- Backend smoke test passes.
- `npm run lint`
- `npm run build`

## 2026-05-29: URL-First Navigation

Completed:
- Added `react-router-dom` and wrapped the app with `BrowserRouter`.
- Made URL routes the source of truth for the main reading state:
  - `/nodes/:slug`
  - `/quizzes/:quizId`
  - `/queue`
- Moved focus mode into URL query state with `?focus=1`.
- Kept Markdown TOC section navigation as normal `#section...` hashes.
- Replaced the internal detail history stack with browser history.
- Prevented stale detail bodies from clearing valid section hashes during browser Back.

Verified:
- Browser Back restores prior node/quiz URLs.
- Browser Back restores prior Markdown section hashes when applicable.
- Frontend smoke test passes in a real browser.
- Backend smoke test passes.
- `npm run lint`
- `npm run build`

## 2026-05-29: Detail Scroll Restoration

Completed:
- Added route-aware scroll restoration for the detail panel.
- Node/quiz route changes without a hash now reset the reading pane to the top.
- Routes with `#section...` scroll to the matching Markdown heading after content loads.
- Added frontend smoke coverage for link navigation after manually scrolling the detail pane down.

Verified:
- Frontend smoke test passes in a real browser.
- Backend smoke test passes.
- `npm run lint`
- `npm run build`

## 2026-05-29: Q Queue Job Robustness

Completed:
- Unified `Q Queue` into one inbox-style surface for open reader questions and durable AI jobs.
- Added failed-job retry support through `POST /api/ai/jobs/{job_id}/retry`.
- Added compact AI error summaries so high-demand or CLI failures do not flood the UI with full stderr logs.
- Switched the default Codex CLI model from config inheritance to `CS_LEARNING_CODEX_MODEL`, defaulting to `gpt-5.4-mini`.
- Fixed AI job prompt construction so queued/solving questions are still passed into the revision prompt by explicit question id.
- Marked all questions attached to a successful AI job as `draft_ready`, then resolved them only after the draft is applied.

Notes:
- The app still cannot disable internal Codex CLI provider retries directly; it now avoids adding its own retry loop and keeps failures retryable from the queue.
- Failed jobs remain visible until retried; retry creates a new queued job and marks the old one as `retried`.

## 2026-05-29: Dev Script Foreground Supervisor

Completed:
- Changed `scripts/dev.ps1` to default to foreground supervisor mode.
- `Ctrl+C` in the terminal now stops both the FastAPI and Vite processes started by the script.
- Added `-Detached` for automation or old background-style startup.
- Documented the new behavior in README.

## 2026-05-29: Q Queue Reliability V1

Completed:
- Refined the AI job state model so reader questions stay `open` while drafts are queued/running/ready.
- Added AI job events for durable stage logs.
- Added fake Codex modes for deterministic smoke tests without spending model tokens.
- Added `base_body_hash` conflict checks before applying drafts.
- Moved AI draft apply into one backend API call that writes Markdown, refreshes SQLite/FTS, marks the job applied, and resolves linked questions.
- Added `Reject draft`; rejected drafts leave linked questions open.
- Added stale job recovery for old queued/solving jobs.
- Added a minimal `project-crud-app` demo node and covered the CRUD Q -> fake draft -> apply flow in smoke tests.

Verified:
- Backend smoke test passes against isolated demo DB.
- `npm run lint`
- `npm run build`

## 2026-05-29: AI Draft Patch And Conflict Review

Completed:
- Added `patch_ops` to the AI revision schema so local AI can propose compact exact-find patches instead of always rewriting full Markdown.
- Kept full `revised_body` as the safe review artifact; when only patch ops are returned, the backend composes the final body before exposing it to the UI.
- Added an AI draft preview card that shows patch op count, affected sections, and a line-level diff.
- Added stale-draft conflict handling: applying a draft now checks the target body hash and blocks if the Markdown changed after the draft was created.
- Added a draft conflict UI with return-to-queue and create-fresh-draft actions.
- Extended browser smoke coverage to simulate an external edit, verify the conflict UI, then return to the queue and apply safely.

Verified:
- Backend smoke test passes against isolated demo DB.
- Frontend smoke test passes in a real browser with fake Codex enabled.
- `npm run lint`
- `npm run build`

## 2026-05-29: Dynamic Codex Provider Config

Completed:
- Added project-local Codex HOME generation for backend AI jobs at `generated/codex-home`.
- The generated config preserves third-party provider settings such as `base_url`, model provider name, model, and auth mode.
- The backend copies the configured Codex auth file into the generated Codex HOME instead of assuming the official OpenAI API key path.
- Added environment overrides for `CS_LEARNING_CODEX_SOURCE_HOME`, `CS_LEARNING_CODEX_BASE_URL`, `CS_LEARNING_CODEX_MODEL_PROVIDER`, `CS_LEARNING_CODEX_AUTH_FILE`, and `CS_LEARNING_CODEX_HOME`.
- Added `tomli` fallback support for Python versions before 3.11.

Verified:
- Minimal Codex CLI JSON-schema smoke succeeds with the generated Codex HOME and third-party provider base URL.

## 2026-05-29: Unsafe Patch Guard

Completed:
- Tightened AI revision instructions so `replace` patch ops must match the full old block, not only a heading or first line.
- Added backend validation that rejects multi-line replacements whose `find` text is too small.
- Added backend validation that rejects replacement text that appears to append new content after the old text instead of replacing it.
- Added smoke coverage for the exact failure shape that duplicated the quiz `基础说明` block.

Verified:
- Backend smoke test passes.
- `npm run lint`
- `npm run build`
