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
3. Read [references/architecture.md](references/architecture.md) before changing the structure.
4. Read [references/node-schema.md](references/node-schema.md) before creating or editing a node.
5. Read [references/curation-rules.md](references/curation-rules.md) when deciding what should be highlighted, buried, linked, or revisited.
6. Read [references/content-standards.md](references/content-standards.md) before adding learning content.
7. Ask the user which content standard to use before adding content, unless the user explicitly names a standard.
8. Use `scripts/build_index.py` to generate or refresh the HTML and JSON index when the map has changed.

## Map Operations

### Add a learning node

Create one Markdown file for one durable concept, skill, project pattern, or recurring question. Prefer a concise note with metadata and links over a long essay.

### Place a node

Use these top-level areas unless the existing map already has a better taxonomy:
- `algorithms`
- `projects`
- `abilities`
- `cs-fundamentals`
- `tools`
- `questions`

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
