---
name: personal-knowledge-map
description: Build and maintain a personal CS learning knowledge map made of small Markdown nodes, explicit indexes, backlinks, source links, HTML navigation, and LLM-curated related suggestions. Use when Codex needs to organize scattered learning across algorithms, projects, abilities, and computer science fundamentals; add new notes; decide where a concept belongs; create search indexes; or generate personalized next-study links.
---

# Personal Knowledge Map

Use this skill to maintain a large but searchable learning map for computer science self-study. Treat the map as a living graph, not a textbook.

## Core Idea

The knowledge base should be made of many small Markdown nodes plus generated indexes. Do not put everything into one giant file. Each node should be useful alone, easy to search, and connected to neighboring ideas.

The LLM is responsible for judgment calls:
- where a new concept belongs
- whether a note is high-value enough to promote
- what related nodes should appear at the end
- which low-frequency topics should stay searchable but unobtrusive

## First-Step Workflow

1. If the user is starting from scratch, create a map from `assets/starter-map`.
2. If the user already has notes, inspect the folder tree and existing indexes before adding anything.
3. Resolve the active content root before writing:
   - Prefer `CS_LEARNING_CONTENT` if it is set.
   - Otherwise, prefer `data/content/` for this project.
   - Use `content-demo/` only for demo/smoke-test content.
   - Treat `content/` as an ignored legacy local copy unless explicitly selected for migration.
   - If `data/nodes/` or `data/quizzes/` exists, treat it as orphaned root-level content caused by a bad content-root selection; do not add new material there.
4. Read [references/architecture.md](references/architecture.md) before changing the structure.
5. Read [references/node-schema.md](references/node-schema.md) before creating or editing a node. Pay special attention to **Frontmatter Integrity** (see section below): the backend will reject files that lack a trailing newline after the closing `---`.
6. Read [references/knowledge-graph.md](references/knowledge-graph.md) before touching frontmatter links or anything tree-shaped. The system has two graph layers: frontmatter links power content navigation, while `kg_question`/`kg_edge`/`kg_mastery` form the explicit KnowledgeGraph DAG. They are related but not interchangeable.
7. Read [references/curation-rules.md](references/curation-rules.md) when deciding what should be highlighted, buried, linked, or revisited.
8. Read [references/content-standards.md](references/content-standards.md) before adding learning content.

### Verification Gate / 验证门

Run BEFORE writing any new node. Every item must pass.

**Context & root:**
- [ ] Folder tree and existing indexes inspected: know what nodes already exist
      in the target area/track before adding new ones.
- [ ] Active content root resolved (priority order):
      `CS_LEARNING_CONTENT` > `data/content/` > `content-demo/`.
      Never write to ignored `content/` or orphan `data/nodes/`.
- [ ] User has been asked which content standard to use (Standard A or Q),
      unless the user explicitly named one.

**Reference documents read — confirm each:**
- [ ] [references/architecture.md](references/architecture.md) — before changing
      structure.
- [ ] [references/node-schema.md](references/node-schema.md) — before creating
      or editing a node.
- [ ] [references/knowledge-graph.md](references/knowledge-graph.md) — before
      touching frontmatter links. (If file does not exist, note it.)
- [ ] [references/curation-rules.md](references/curation-rules.md) — before
      deciding visibility.
- [ ] [references/content-standards.md](references/content-standards.md) —
      before writing content.
- [ ] [references/textbook-writing.md](references/textbook-writing.md) — when
      user asks for textbook-grade quality.

**Prerequisite integrity:**
- [ ] All `prerequisites` slugs resolve to existing `.md` files under the
      active content root. (`find . -name "<slug>.md"`.)
- [ ] All `related` slugs verified the same way.
- [ ] `prerequisites` chain is acyclic (no circular dependencies).
- [ ] For each prerequisite, read it in full. If `status: demo` or shorter
      than 50 lines of body, deepen it before depending on it.
- [ ] Every unfamiliar term the new node introduces is defined inline (1–2
      sentences) OR covered by an existing prerequisite of sufficient depth.
      If neither, create a new prerequisite node first.

**KnowledgeGraph:**
- [ ] Existing `kg_question` + `kg_edge` entries checked for prerequisite
      nodes. If missing, flag for `build_kg_forest.py` after writing.

**Placement gate (cs-fundamentals):**
- [ ] New cs-fundamentals nodes are intro-level prerequisites or foundational
      bridges. If not, choose a more specific area/track or archive.

### Writing Gate / 写入门

During and immediately after writing:

**File integrity (both standards):**
- [ ] File begins with `---` (no UTF-8 BOM — first three bytes are `2d 2d 2d`,
      not `ef bb bf`).
- [ ] File ends with `\n` after closing `---`. Verify:
      `xxd "$file" | tail -1` — last hex byte must be `0a`.
- [ ] `area:` and `track:` are both present and non-empty. `track:` is a
      meaningful sub-category used consistently with peers.

**Standard A node (tutorial):**
- [ ] `slug:` in frontmatter matches the filename (without `.md`).
- [ ] File path: `<content-root>/nodes/<area>/<slug>.md` — not in root-level
      `data/nodes/` or `data/quizzes/`.
- [ ] `visibility:` is `core`, `support`, or `archive` — decision justified
      per curation-rules.md.

**Frontmatter metadata:**
- [ ] `sources:` contains at least one authoritative URL (HTTPS) or reference.
- [ ] `summary:` is a single-sentence value proposition.
- [ ] `order:` is an integer, positive, and unique within `area` + `track`.
      Query: `sqlite3 knowledge.db "SELECT display_order FROM nodes WHERE
      area='...' AND track='...' AND display_order = <order>;"`
- [ ] No `mastery` field in frontmatter (mastery is dynamic graph data).
- [ ] For Standard A nodes only: `order:` is an integer, positive, and unique
      within `area` + `track`.

**Standard Q quiz — additional frontmatter:**
- [ ] `id:` matches the filename (without `.md`), not `slug:`.
- [ ] File path: `<content-root>/quizzes/<area>/<slug>.md`.
- [ ] `difficulty:` is `easy`, `medium`, or `hard`.
- [ ] `weight:` is a positive integer.
- [ ] `linked_nodes:` points to existing Standard A node slugs.
- [ ] `track:` is present (same rule as Standard A — required, non-empty).
- [ ] `visibility:` is `practice` (for active quizzes) or `seed`.

**Content structure (Standard A):**
- [ ] At least one `## Worked Example` or `## Practice` section with step-by-
      step reasoning, where each step cites the rule it applies.
- [ ] At least two `## Reader Questions` written from the perspective of a
      confused beginner.
- [ ] `## Common Confusions` or `## Common Mistakes` present.
- [ ] `## Quick Recall` — a compact mnemonic hook.
- [ ] `## Suggested Next` — curated by reasoning, not just tags. Points to
      real existing nodes.

**Bilingual completeness:**
- [ ] Every `## Section Title / 中文标题` heading has BOTH English and Chinese
      content in that section. The Chinese explains the same idea with
      equivalent depth, not a single sentence. Code blocks and tables stay
      English-only.

**KnowledgeGraph Write Gate (frontmatter links):**
- [ ] Link classification correct: `prerequisites` = depends-on,
      `related` = lateral suggestion (never establishes a tree edge).
- [ ] Every target slug is a real Markdown-backed node (not DB-only).
- [ ] No self-links, no duplicate entries in the same list.
- [ ] No cycles: A → B → A is forbidden.
- [ ] Diamond (one prerequisite shared by multiple nodes) is valid — reuse
      is encouraged.

**Node design:**
- [ ] One durable concept per node — not two topics in one file.
- [ ] Node is compact — prefer short + well-linked over long + self-contained.
- [ ] Placement decision is explained briefly (in the body or commit message).

### Post-Write Verification / 写入后验证

Must run after every batch of new/changed nodes:

- [ ] `python3 scripts/build_kg_forest.py` — integrates frontmatter links
      into kg_question / kg_edge for the 3D tree visualization.
- [ ] `python3 scripts/build_index.py data/content` — rebuilds HTML/JSON
      indexes so Library Workbench reflects new nodes.
- [ ] After build: verify `kg_question` and `kg_edge` entries were created.
      `sqlite3 knowledge.db "SELECT question_id FROM kg_question WHERE
      root_node_id = '<new-slug>';"`
- [ ] After build: verify HTML index file exists and is non-empty.
      `ls -la data/content/nodes/*/index.md` or equivalent.
- [ ] Read every new/changed Markdown node in full (not frontmatter only).
      Inspect every heading, example, and Suggested Next for missing
      definitions, stale links, copied answers, and taxonomy drift.
- [ ] Verify all hyperlinks: every `[label](slug.md)` resolves to a real file.
      Use: `find data/content -name "<slug>.md"` for each link.
- [ ] Frontmatter integrity re-check: `xxd "$file" | tail -1` ends with `0a`
      for every new/changed file.
- [ ] For Standard Q quizzes: verify `linked_nodes:` slugs resolve to real
      Standard A node files. Every quiz must link to at least one node.
- [ ] KnowledgeGraph Write Gate rule 6: after write, read the tree/subtree
      snapshot and verify reachability, parent direction, scope, and endpoint
      titles. A successful HTTP response is not enough by itself.
- [ ] Check Library Workbench UI: new node appears with correct area/track/
      order, no "missing" or "duplicate" warnings, Chinese text renders
      correctly.
### Standard Q Note

When creating Standard Q quiz content additionally make it Daily-Bite-friendly
unless the user explicitly says it should not be used for bite-sized review.

---

## Map Operations

## KnowledgeGraph Write Gate

Before creating or moving any prerequisite relationship:

1. Resolve exact node slugs from the active content root. Do not use display
   titles, file paths, guessed slugs, or a `related` link as a parent edge.
2. Classify the relationship: `prerequisites` means the current node depends
   on the target; `related` is a lateral suggestion and never establishes a
   prerequisite edge.
3. Ensure every target node exists as a real Markdown-backed node before an
   edge is written. Never leave a DB-only or guessed node id.
4. Reject self-links, duplicate links, and any prerequisite cycle. A diamond
   (one prerequisite shared by multiple nodes) is valid and should be reused.
5. For KnowledgeGraph proposals, use the two-phase flow: propose, inspect and
   edit the returned tree, then confirm. AI output is never persisted directly.
6. After a write, read the tree/subtree snapshot and verify reachability,
   parent direction, scope, and endpoint titles. A successful HTTP response is
   not enough by itself.

Do not write `mastery` into Markdown frontmatter. Mastery is dynamic graph data
updated by verification events; use it to choose the next learning action.

## Frontmatter Integrity Rule / 前置元数据完整性规则

The app's backend reads node Markdown files by parsing frontmatter delimited by
`---`. A broken frontmatter causes 400 errors ("Node source file has no
frontmatter") on common operations such as "Move to trash", "Restore", or
"Edit order".

### Critical Format Rules

Every `.md` node file must follow this exact pattern (note the trailing newline
after the closing `---`):

```text
---↵
title: "..."↵
area: "..."↵
...↵
order: N↵
---↵
```

1. **Opening `---`** must be the very first bytes — no blank line, no BOM, no
   whitespace before it. The backend regex is `^(---\s*\n.*?\n---\s*\n)`.
2. **Trailing newline:** The closing `---` MUST be followed by `\n`. Without it,
   `split_markdown_frontmatter` returns empty → app throws 400 "no frontmatter".
3. **Encoding:** UTF-8 **without BOM** (bytes `EF BB BF` at offset 0 break the
   `^---` anchor). Verify with `xxd "$file" | head -1` — first three bytes must
   be `2d 2d 2d` (---), NOT `ef bb bf`.
4. **Line endings:** LF (Unix) or CRLF (Windows) both work — `\s*` in the regex
   absorbs `\r`. Do not mix within one file.
5. **Key-value format:** YAML `key: value`. Quoted strings (`"..."`) recommended
   for values with colons, brackets, or special characters.

### Common Breakage Patterns

| Mistake | Symptom | Detective Tool |
|---------|---------|---------------|
| No `\n` after closing `---` | `split_markdown_frontmatter` → `("", text)` | `xxd "$f" \| tail -1` — last byte is `2d` not `0a` |
| UTF-8 BOM before `---` | Regex `^---` never matches | `xxd "$f" \| head -1` — starts with `ef bb bf` |
| PowerShell `Set-Content` | Chinese garbled to `缂/锟/璇�` | Read tool shows correct? If PowerShell reads wrong, trust Read |
| `WriteAllText` without trailing `\n` | Same as "no trailing newline" | File content ends on line `---` (line N, no N+1) |
| Tab characters in YAML | Parser rejects mixed indentation | `grep -P "^\t"` in the file |

### Safe Writing Patterns

**Python (safe):**
```python
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)  # ensure content ends with '\n'
```

**Node.js / TypeScript (safe):**
```typescript
writeFileSync(path, content, 'utf-8');
```

**PowerShell (use with care):**
```powershell
# WRONG — corrupts encoding:
Set-Content -Encoding utf8 $path $content
# RIGHT:
[System.IO.File]::WriteAllText($path, $content,
    [System.Text.UTF8Encoding]::new($false))
# Then verify the file ends with \n:
if ((Get-Content -Tail 1 $path) -eq "---") { "missing trailing newline" }
```

After any write, verify: `xxd "$f" | tail -1` must end in `0a`.

## Private Library Write Rule

For this project, real tutorials and quiz content go into the private library, not the app shell:

```text
data/content/
```

Before writing a file:
- Resolve the active content root from `CS_LEARNING_CONTENT` when available.
- If no override exists, use `data/content/`.
- Create learning nodes under `<active-content-root>/nodes/<area>/`.
- Create quiz-bank items under `<active-content-root>/quizzes/<area>/`.
- Keep sources under `<active-content-root>/sources/`.
- Never create root-level `data/nodes/` or `data/quizzes/`.
- Never add real personal study material to `content-demo/`; it is only for small demo and smoke-test fixtures.

After writing or moving content, rebuild the selected SQLite index with the same content root and database path used by the app.

## Daily Bite Authoring Rule

Daily Bite is the lightweight recall surface for this learning OS. It can derive a one-blank micro-card from quiz Markdown, and it can also store user-edited custom Bite cards in SQLite `bite_cards`.

When the user asks for addictive daily practice, bite-sized review, flashcards, Anki-like cards, or daily questions:
- Prefer creating or improving Standard Q quiz Markdown first, not isolated app-shell data.
- Shape the quiz so Daily Bite can extract one prompt, one answer, one hint, and three concise explanation lines.
- Use numbered Prompt and Answer pairs when a quiz contains multiple small recall items.
- Keep answer lines short enough to be typed into a fill-in blank.
- Add a `## Hint` section when the answer is not inferable from the prompt.
- Put the first three `## Explanation` sentences in a concise form before any deeper walkthrough.
- Do not require AI generation for Daily Bite; the card must work offline from Markdown.
- Treat custom Bite CRUD as a local runtime layer. Editing or archiving a custom Bite must not mutate the source quiz Markdown.

## Tutorial Image Rule

When a tutorial needs screenshots or diagrams:
- Store real tutorial images under `<active-content-root>/assets/<topic>/`, usually `data/content/assets/<topic>/`.
- Use lowercase kebab-case filenames such as `bomblab-prep-02.png`; avoid spaces, parentheses, Chinese punctuation, and Windows paths.
- Reference images from Markdown with content-asset URLs: `![Alt text](/content-assets/<topic>/<file>.png)`.
- Put the image on its own line, followed by a short italic caption on the next line.
- Add only high-signal images that teach a step, state change, UI location, or visual mental model.
- Keep large raw source files out of Git unless they are intentionally app-shell assets.
- After adding images, verify that each referenced file exists and the rendered page does not overflow in focus reading.

### Add a learning node

Create one Markdown file for one durable concept, skill, project pattern, or recurring question. Prefer a concise note with metadata and links over a long essay.

Write it under the active content root, usually `data/content/nodes/<area>/...` for real local notes. Do not write real study material into `content-demo/`, the legacy ignored `content/` folder, or root-level `data/nodes/`.

### Place a node

Use these top-level areas unless the existing map already has a better taxonomy:
- `algorithms`
- `projects`
- `abilities`
- `cs-fundamentals`
- `tools`
- `questions`

`cs-fundamentals` is broad but guarded. Only add intro C, GDB, x86-64, binary representation, memory, CSAPP/Bomb Lab basics, or reusable foundational bridge nodes there. Do not use it as a catch-all for advanced, project-specific, tool-only, or rare material.

### Link a node

Every promoted node should include:
- prerequisites
- related nodes
- practice or usage contexts
- source links, if known

### Decide visibility

Use three levels:
- `core`: essential and frequently reused
- `support`: useful, but not central
- `archive`: searchable, rare, niche, or low-confidence

### Add LLM suggestions

At the end of each substantial node, add a short `Suggested Next` section. This should be curated by reasoning, not hard-coded only by tags.

## Search Strategy

Prefer layered search:
- title and slug for exact lookup
- tags for broad filtering
- `summary` for semantic scanning
- backlinks for graph navigation
- HTML index for human browsing
- JSON index for future scripts or retrieval

When uncertain, preserve findability. Low-value does not mean deletion; it usually means `archive` visibility plus good metadata.

## Output Style

When updating the map:
- explain the placement decision briefly
- keep Markdown nodes compact
- update indexes after structural changes
- avoid overengineering until repeated use proves the need

When evaluating the user's idea:
- separate what should be deterministic from what should be LLM-judged
- name the next irreversible design decision, if any
- recommend one small next experiment
