# Client Android-Parity Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish an accurate React-client parity roadmap, keep README Android facts current, and clean active Android WebView-era execution guidance without deleting useful historical records.

**Architecture:** Android implementation and verified tests define the current mobile baseline. The new root plan compares that baseline with the existing React/FastAPI client without claiming parity prematurely. README is the concise entry point; dated specs/plans remain historical unless they actively prescribe a retired, insecure workflow that a contributor could still follow.

**Tech Stack:** Markdown, repository search, Git link/diff checks.

---

### Task 1: Publish The Client Android-Parity Plan

**Files:**
- Create: `docs/client-android-parity-plan.md`

- [ ] Write a capability matrix covering local data, node/area workflows, capture, review, Trash, backup/restore, Markdown, AI provider setup, AI draft review, knowledge assistant, security limits, and sync. Each row must label Android as implemented, describe the existing Web client state, name API dependency, and classify the Web next step as align/defer/not-applicable.
- [ ] Add four ordered stages: contract/safety alignment, local workflow alignment, optional AI alignment, and explicitly deferred cross-device capabilities. Give every stage entry criteria, completion evidence, and a non-goal list.
- [ ] State that Android is not the backend authority and parity does not mean copying Android UI; both clients consume explicit contracts while preserving local-first ownership.
- [ ] Verify all referenced files and routes exist with `rg --files` and `rg -n`; run `git diff --check`.
- [ ] Commit as `docs: add client android parity plan`.

### Task 2: Refresh Root Android Documentation

**Files:**
- Modify: `README.md`
- Modify: `android-app/docs/android-app-usage.md`

- [ ] Update README Android beta content with current native Compose/local-first behavior, `usesCleartextTraffic=false` HTTPS-only optional provider boundary, encrypted local API-key handling, bounded backup import, and links to `android-app/README.md`, Android usage guide, architecture/recovery docs, and the client-parity plan.
- [ ] Rewrite the Android usage guide as UTF-8 Chinese text. Add a concise AI guide with: More -> Service configuration, HTTPS endpoint/model validation, Capture Slip -> AI draft -> review -> explicit Save Markdown, and Knowledge assistant use. State that AI is optional and generated output is a proposal, not an autonomous write.
- [ ] Do not promise sync, code/formula reader, batch assistant writes, or remote accounts. Preserve existing verified Home/Library screenshot references only when the files exist.
- [ ] Render/inspect Markdown text and run an encoding scan for Unicode replacement characters or mojibake markers; run `git diff --check`.
- [ ] Commit as `docs: refresh android usage guidance`.

### Task 3: Clean Retired Android Execution Instructions

**Files:**
- Modify only the current document or runbook that still exposes retired Android WebView, `http://10.0.2.2:5173`, or cleartext execution steps.
- Preserve date-stamped architecture and migration records when they are historical context rather than active instructions.

- [ ] Search active README, workflow, and runbook documents for retired execution strings such as `MainActivity.java`, `WebView shell entry`, `10.0.2.2:5173`, or broad cleartext allowances.
- [ ] If a current document still presents those steps as executable guidance, remove the instruction or relabel it as retired history. Do not delete historical specs/plans solely because they are dated.
- [ ] When a historical document is retained, ensure any reader can distinguish historical rationale from current operating guidance.
- [ ] Run `git diff --check` and inspect `git status --short` to confirm only intended documentation files changed.
- [ ] Commit as `docs: clean retired android webview guidance`.

### Task 4: Verify Documentation Contract

**Files:**
- Modify only if verification discovers a broken current link or contradictory statement.

- [ ] Run a repository link/path scan for the new plan and Android docs.
- [ ] Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-android-architecture.ps1` to ensure documentation cleanup did not break architecture documentation gates.
- [ ] Run `git diff --check main...HEAD` and inspect the final commit list.
- [ ] Commit a correction only when a verification failure proves one is needed.
