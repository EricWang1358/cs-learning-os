# Object-Aware Assistant Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user launch the Android assistant from an existing Node, Capture slip, or Review quiz and confirm an AI-proposed revision in the correct existing editor without changing object identity or silently mutating data.

**Architecture:** Replace the Node-only assistant working draft with a sealed, persisted edit-target model. The assistant coordinator owns target-aware prompt construction and parses explicit, type-specific directives into one of three confirmation actions. `AssistantAppBridge` maps each action into the existing UI state; repositories remain the only persistence boundary and preserve IDs and review state for edits.

**Tech Stack:** Kotlin, Compose Material 3, Room, coroutines/StateFlow, JUnit 4.

---

### Task 1: Define and Persist Typed Assistant Edit Targets

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/domain/AssistantConversation.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/data/AssistantConversationCodec.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantUiModels.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/assistant/data/AssistantConversationCodecTest.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/assistant/ui/AssistantActionClaimsTest.kt`

- [ ] **Step 1: Write failing codec tests for Node, Quiz, and Capture targets.**

```kotlin
assertEquals(AssistantWorkingTarget.Quiz("q1", "n1", "Prompt", "Answer", "Why"),
    AssistantConversationCodec.decode(AssistantConversationCodec.encode(conversation)).workingTarget)
```

- [ ] **Step 2: Run the assistant codec tests and verify the Quiz/Capture target references are unresolved.**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.feature.assistant.data.AssistantConversationCodecTest`

- [ ] **Step 3: Add sealed `AssistantWorkingTarget` variants.**

```kotlin
sealed interface AssistantWorkingTarget {
    data class Node(val titleHint: String, val markdown: String, val areaId: String?, val nodeId: String?) : AssistantWorkingTarget
    data class Quiz(val quizId: String, val nodeId: String?, val prompt: String, val answer: String, val explanation: String) : AssistantWorkingTarget
    data class Capture(val slipId: String, val body: String, val topicHint: String, val sourceLabel: String, val typeName: String) : AssistantWorkingTarget
}
```

- [ ] **Step 4: Encode a `kind` field and only decode known kinds; decode legacy `working_draft` as a Node target.**

- [ ] **Step 5: Run codec and action tests, then commit the typed model.**

### Task 2: Build Explicit Target-Aware Assistant Actions

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/domain/KnowledgeAssistantPrompt.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/domain/KnowledgeAssistantSession.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantUiModels.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantCoordinator.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/assistant/ui/AssistantActionClaimsTest.kt`

- [ ] **Step 1: Write failing action tests for quiz and capture directives.**

```kotlin
assertEquals(AssistantMessageAction.OpenEditableQuizDraft("q1", "Question", "Answer", "Explanation"), decision.action)
assertEquals(AssistantMessageAction.OpenEditableCaptureDraft("s1", "Body", "Hint", "Source", CaptureSlipType.idea), decision.action)
```

- [ ] **Step 2: Run the action test class and verify the new actions are unresolved.**

- [ ] **Step 3: Add `OpenEditableQuizDraft` and `OpenEditableCaptureDraft`; retain `OpenEditableDraft` for Nodes.**

- [ ] **Step 4: Make the system prompt require exact `cs-quiz-*` or `cs-capture-*` directives only for the active target and prohibit unrelated object creation.**

- [ ] **Step 5: Add coordinator entry methods `reviseNode`, `reviseQuiz`, and `reviseCapture`; each sets one typed target and an intent-specific starter prompt.**

- [ ] **Step 6: Parse only complete valid directives, keep the old target when a reply is incomplete, and expose one confirmation action. Run assistant tests and commit.**

### Task 3: Preserve Existing Quiz and Capture Identity Through Editors

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningUiModels.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/data/ReviewRepository.kt`
- Modify: capture repository/save implementation under `android-app/app/src/main/java/com/cslearningos/mobile/feature/capture/`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/ui/QuizEditorStateTest.kt`

- [ ] **Step 1: Write failing repository tests that editing a capture retains its ID and editing a quiz retains its review state.**

```kotlin
assertEquals("capture-1", repository.saveCaptureSlip(id = "capture-1", ...).id)
assertEquals(existingReviewState, dao.reviewStates.getValue("quiz-1"))
```

- [ ] **Step 2: Add `captureEditorId` and focused UI-state transitions; pass optional IDs to repository save calls.**

- [ ] **Step 3: For existing objects, preserve creation metadata and associations; only new objects create default review state.**

- [ ] **Step 4: Run repository and state tests, then commit.**

### Task 4: Wire Confirmation and Quick AI Entry Points

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantAppBridge.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/AssistantScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Modify: capture screen composable under `android-app/app/src/main/java/com/cslearningos/mobile/ui/`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/ui/QuizEditorStateTest.kt`

- [ ] **Step 1: Write failing bridge/action-claim tests proving each typed assistant action maps to its matching editor state and original ID.**

- [ ] **Step 2: Add assistant message buttons labelled as reviewable revisions, never direct save mutations.**

- [ ] **Step 3: Add compact “Improve with AI” actions for the reader Node, visible Capture slip, and revealed Review quiz answer; each calls the matching bridge method and opens Assistant.**

- [ ] **Step 4: Route Node actions to Node editor, Quiz actions to Quiz editor, and Capture actions to Capture editor. Preserve the visible success/failure banner after returning from Assistant.**

- [ ] **Step 5: Run UI-state/action tests and commit.**

### Task 5: Reliability, Review, and Release

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantCoordinator.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/AssistantScreen.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/assistant/ui/AssistantActionClaimsTest.kt`
- Modify: `android-app/app/build.gradle`
- Modify: `docs/release-notes.md`

- [ ] **Step 1: Add tests that retry preserves the original request and a claimed confirmation action cannot run twice.**

- [ ] **Step 2: Guard completion updates by response message ID so cancelled replies cannot clear a newer request; show save failures and unavailable citations in Assistant.**

- [ ] **Step 3: Run `:app:testDebugUnitTest`, `:app:assembleDebug`, and `scripts/verify-android-architecture.ps1`.**

- [ ] **Step 4: Perform requirement review and code-quality review with separate subagents; fix all P1/P2 findings and rerun verification.**

- [ ] **Step 5: Bump Android Beta version, update release notes, commit, tag, push, and run `scripts/verify-android-beta.ps1`.**
