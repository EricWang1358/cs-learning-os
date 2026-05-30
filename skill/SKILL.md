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
5. Read [references/node-schema.md](references/node-schema.md) before creating or editing a node.
6. Read [references/curation-rules.md](references/curation-rules.md) when deciding what should be highlighted, buried, linked, or revisited.
7. Read [references/content-standards.md](references/content-standards.md) before adding learning content.
8. Ask the user which content standard to use before adding content, unless the user explicitly names a standard.
9. Apply the quality gate: explanations must be tutorial-grade, with `Shark Tank Passcode: process_code and is_valid_code` as the minimum depth target for low-level systems and quiz content.
10. Apply the placement gate: new `cs-fundamentals` nodes must be intro-level prerequisites or foundational bridges; otherwise choose a more specific area/track or archive.
11. Use `scripts/build_index.py` to generate or refresh the HTML and JSON index only for the selected content root when the map has changed.

## Map Operations

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

## Tutorial Image Rule

When a tutorial needs screenshots or diagrams:
- Store web-visible images under `app/public/images/<topic>/`.
- Use lowercase kebab-case filenames such as `bomblab-prep-02.png`; avoid spaces, parentheses, Chinese punctuation, and Windows paths.
- Reference images from Markdown with root-relative URLs: `![Alt text](/images/<topic>/<file>.png)`.
- Put the image on its own line, followed by a short italic caption on the next line.
- Add only high-signal images that teach a step, state change, UI location, or visual mental model.
- Keep large raw source files out of Git unless they are intentionally part of the app experience.
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
