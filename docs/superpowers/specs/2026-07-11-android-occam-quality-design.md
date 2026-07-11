# Android Occam Quality Design

## Goal

Make the Android beta easier to understand and more trustworthy by removing duplicate paths, completing the object-aware assistant safely, using one restrained motion language, and providing a five-minute tutorial plus recovery guidance.

## Decision

Choose a surgical simplification release.

- Do not change the stack, database schema, navigation tabs, or local-first ownership model.
- Do not move the existing engineering-document tree in this release.
- Do not add onboarding modals, decorative animation, sync, web search, or another AI workflow.
- Finish or remove incomplete paths. A visible control must lead to a complete, testable loop.

Rejected alternatives:

- A broad documentation-directory migration creates broken links and review noise without improving the app.
- A visual redesign would hide unresolved identity, history, and conflict bugs under new styling.

## Smallest Product Loop

The phone has four top-level jobs:

1. Capture a thought locally.
2. Find and edit knowledge by Area.
3. Review one question and rate it.
4. Ask the assistant or request an editable revision.

Home exposes those jobs once. It keeps Continue Reading when useful, but removes duplicate search, metrics, and Library preview cards. Counts stay in the compact header and Review action.

## Assistant Contract

Use one persisted typed edit target for Node, Quiz, and Capture. Remove the split between legacy `workingDraft` and `pendingObjectTarget`.

- Each target stores object ID, expected revision, and all editable fields.
- A model reply is accepted only when every required directive appears exactly once and no unrelated payload remains.
- Missing, deleted, or changed targets fail visibly. They are never recreated or silently revived.
- The pending proposal and confirmation action survive conversation history restoration.
- AI never writes a final object. It opens the matching editor; Save performs the repository transaction.

## Screen Simplification

### Home

- Keep one four-action strip: Capture, Library, Review, Assistant/Search.
- Keep Continue Reading when a recent Node exists.
- Remove the large search card, separate metric card, and Library preview card.

### Capture

- Composer has one Save action.
- Saved slips have one `Improve with AI` action, plus Make Node and Archive.
- Remove the static AI-flow explainer and the older competing AI-draft/preflight controls from the visible path.

### Library and Reader

- Area folders are the map. Remove Overview and Area Map explanation panels from the primary list.
- Reader has one visible AI action. Remove the duplicate AI item from More.

### Review

- After reveal, show one Edit menu (`Manual edit`, `Improve with AI`) plus Again, Hard, Good.
- Preserve quiz ID, association, and review state for both edit paths.

### More

Use four sections only: System, AI Service, Data, Guide.

- Notifications remain in the shared notice tray, not a second inbox.
- Support text becomes a short footer in Guide.
- Backup and Import become one `Backup and restore` entry.
- Destructive demo removal and permanent deletion retain confirmation.

## Tutorial and Documentation

The embedded Guide is actionable and takes under five minutes:

1. Capture one thought.
2. Find it or create a Node in an Area.
3. Reveal and rate one review question.
4. Export a backup before restore or permanent deletion.

AI is described as optional and user-triggered. Restore is described as full replacement of local data.

Create:

- `docs/first-run.md`: short desktop/Android user path.
- `docs/data-recovery.md`: export, restore replacement, Trash, permanent delete, reinstall.

Update the root and Android READMEs to link to these documents and remove mojibake/stale network-policy language. Replace the Vite template README. Keep historical design files out of the active onboarding map without moving them in this release.

## Motion System

Motion has four owners:

- press/ripple feedback;
- 120-140 ms color/fade state changes;
- 160-180 ms disclosure expansion owned by `AnimatedVisibility`;
- 200-220 ms route or drawer movement with a shorter scrim fade.

Remove global card `animateContentSize`, More's double size animation, streaming scroll animation on every text delta, and bottom-navigation content-size animation. Route transitions stay a small fade/shift. No interaction may combine route movement, card resize, and child expansion.

## Quality Gate

- No duplicate visible controls call the same object action.
- No screen has more than four primary actions in one decision group.
- All destructive actions require confirmation or are reversible.
- Typed assistant proposals fail closed and preserve identity/revision.
- Tutorial, app strings, and recovery docs describe actual behavior.
- Unit tests, architecture harness, debug build, and Android beta verifier pass.
- Independent spec and code-quality reviewers report no P1/P2 findings.
