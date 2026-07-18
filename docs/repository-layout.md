# Repository Layout

This repository keeps product surfaces separate from local runtime state and
development tooling. New code should land in the smallest existing boundary;
do not create a second app root or copy a client implementation into a demo
directory.

## Canonical roots

| Path | Ownership | Commit policy |
| --- | --- | --- |
| `app/` | React/Vite desktop client | Source, tests, and checked-in static assets |
| `backend/` | FastAPI desktop service and SQLite/content adapters | Source and tests |
| `android-app/` | Native Android client and Gradle modules | Source, tests, schemas, and docs |
| `content-demo/` | Small public fixture library used by onboarding and tests | Tracked Markdown fixtures only |
| `docs/` | Product contracts, architecture, release, and operational guides | Tracked documentation |
| `scripts/` | Cross-platform setup, verification, and maintenance commands | Tracked scripts only |
| `skill/` | Optional authoring skill package and starter-map assets | Tracked skill source/assets |
| `experimental/` | Prototypes and extracted modules not imported by production clients | Explicitly marked, reviewed separately |

## Runtime boundaries

The following paths are local state or generated output and must never be
committed:

- `data/`: personal SQLite database and imported content.
- `content/`: indexed/runtime content projection.
- `var/`: logs, exports, and service state.
- `generated/`, `app/generated/`: screenshots and QA artifacts.
- `tmp/`: temporary migration or browser files.
- `.playwright-cli/`, `.superpowers/`, `.claude/`: local automation state.
- `**/build/`, `app/dist/`, `node_modules/`, `.venv/`: build/dependency output.
- `experimental/**/*.zip`: packaged prototype snapshots; keep source files instead.

Use `content-demo/` for public examples. Never place personal notes or a
database under `app/`, `backend/`, or `android-app/`.

## Structural rules

1. Desktop changes stay within `app/`, `backend/`, and their documented
   cross-boundary API contracts.
2. Android changes stay under `android-app/`; Android source must not import
   desktop Python or React modules.
3. Shared behavior belongs in a documented API/data contract, not in a copied
   helper under a second root.
4. Experimental or historical prototypes live under `experimental/`, must be
   clearly marked in their own README, and must not be imported by production
   clients.
5. A structural change must update this map and pass the repository-layout
   verifier before merge.
