# Android Assistant Area Move And Text Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android assistant safely propose and confirm a single Node Area move, while allowing users to select/copy completed assistant prose.

**Architecture:** Preserve existing assistant chat compatibility. Route only the new move proposal lifecycle through `AssistantRunMachine`; confirm through the existing repository move operation after ID/revision validation. Keep selectable message prose separate from action controls.

**Tech Stack:** Kotlin, Jetpack Compose, Room, JUnit/Robolectric, Android instrumentation tests.

---

### Task 1: Restore The Test Baseline

**Files:**
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/AppLocalizationTest.kt`

- [ ] Add assertions that match the current local-first English and Chinese guide copy rather than retired five-minute wording.
- [ ] Run `cd android-app; .\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.ui.AppLocalizationTest` and confirm green.
- [ ] Commit `test(android): align localization guide assertions`.

### Task 2: Define And Apply A Confirmed Move Proposal

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/domain/AssistantAgentInteraction.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantCoordinator.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantAppBridge.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/AssistantAgentActionCards.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/assistant/domain/AssistantAgentInteractionTest.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/assistant/ui/AssistantCoordinatorStateTest.kt`

- [ ] Write red tests for a proposal with an unknown Area, a stale revision, and one valid existing Area confirmation.
- [ ] Parse only a typed `move_node_area` proposal containing node ID, expected revision, target Area ID, and reason; reject every missing or mismatched field.
- [ ] Render a confirmation card only for a validated proposal; confirmation calls `moveNodeToArea` and never rewrites Markdown.
- [ ] Run the focused domain/UI tests and commit `feat(android): confirm assistant area moves`.

### Task 3: Bind Run IDs To The New Proposal Path

**Files:**
- Modify: `android-app/domain/assistant/src/main/kotlin/com/cslearningos/mobile/assistant/domain/AssistantRunMachine.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantCoordinator.kt`
- Test: `android-app/domain/assistant/src/test/kotlin/com/cslearningos/mobile/assistant/domain/AssistantRunMachineTest.kt`

- [ ] Write red tests showing a superseded or cancelled run cannot publish a move proposal.
- [ ] Dispatch start/context/model/parse/fail events through the machine for this proposal path and ignore events whose run ID is no longer active.
- [ ] Run the domain and coordinator tests and commit `fix(android): guard assistant move runs`.

### Task 4: Make Assistant Prose Selectable

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/AssistantMessageBody.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/AssistantScreen.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/ui/AssistantScreenTest.kt`

- [ ] Write a Compose red test that a completed assistant body is selectable while a draft/retry/confirmation action remains separately clickable.
- [ ] Extract the completed Markdown body into `AssistantMessageBody`, wrap only it in native `SelectionContainer`, and retain streaming/user rendering unchanged.
- [ ] Run the focused Compose test and commit `feat(android): enable assistant text selection`.

### Task 5: Full Verification

- [ ] Run `cd android-app; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --rerun-tasks`.
- [ ] Run `cd android-app; .\gradlew.bat :app:connectedDebugAndroidTest`.
- [ ] Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-android-architecture.ps1` from repository root and `git diff --check`.
- [ ] Install the debug APK on the emulator; verify a completed assistant reply can be selected/copied and a valid Area proposal requires confirmation before the node moves.
