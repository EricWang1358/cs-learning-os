# Android Occam Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver one smaller, safer Android workflow with complete object-aware AI editing, concise onboarding, and a restrained motion system.

**Architecture:** Preserve the existing local-first feature modules. Consolidate assistant editing into one typed target, remove duplicate UI paths instead of adding new abstractions, and make docs/tutorials reflect executable workflows.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, coroutines/StateFlow, JUnit 4, Markdown.

---

### Task 1: Close Assistant Correctness Gaps

**Files:**

- `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/domain/AssistantConversation.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/domain/AssistantObjectProposal.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/data/AssistantConversationCodec.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantCoordinator.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantUiModels.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/assistant/ui/AssistantAppBridge.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/data/LibraryRepository.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/data/ReviewRepository.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/feature/assistant/domain/AssistantObjectProposalTest.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/feature/assistant/data/AssistantConversationCodecTest.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`

- [ ] Add failing tests for duplicate directives, stale revisions, deleted targets, and history-restored proposals.

```kotlin
assertNull(parseAssistantObjectProposal(target, duplicatedReply, emptyList()))
assertEquals(expectedAction, restored.messages.last().action)
assertTrue(runCatching { repository.saveManualQuiz(id = "deleted", expectedRevision = 2L, ...) }.isFailure)
```
- [ ] Replace legacy Node working draft with the typed target used by Quiz and Capture.
- [ ] Require exact directive counts and empty residual payload before creating an action.
- [ ] Carry expected revision through actions and editor state; reject stale/deleted writes.
- [ ] Persist and restore pending proposal actions.
- [ ] Run assistant/repository tests and commit.

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.cslearningos.mobile.feature.assistant.*" --tests "com.cslearningos.mobile.data.LearningRepositoryPolicyTest"`

### Task 2: Remove Duplicate Product Paths

**Files:**

- `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardScreen.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardModels.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/CaptureScreen.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreSettingsModels.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/ui/DashboardModelsTest.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/ui/MoreSettingsModelsTest.kt`

- [ ] Write failing model tests for four Home actions and four More sections.

```kotlin
assertEquals(4, buildDashboardSummary(LearningUiState()).firstScreenActions.size)
assertEquals(listOf(System, Service, Data, Guide), orderedMoreSectionIds())
```
- [ ] Remove duplicate Home cards, Reader AI menu item, Capture AI path, Library overview/map, notification/support sections, and duplicate backup button.
- [ ] Use one Review Edit menu with Manual and AI options.
- [ ] Keep all visible action groups at four commands or fewer.
- [ ] Run UI model tests and commit.

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.ui.DashboardModelsTest --tests com.cslearningos.mobile.ui.MoreSettingsModelsTest`

### Task 3: Build the Five-Minute Tutorial

**Files:**

- `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
- `android-app/app/src/main/res/values/strings.xml`
- `android-app/app/src/main/res/values-zh/strings.xml`
- `android-app/app/src/test/java/com/cslearningos/mobile/ui/AppLocalizationTest.kt`
- `docs/first-run.md`
- `docs/data-recovery.md`
- `README.md`
- `android-app/README.md`
- `app/README.md`

- [ ] Write failing localization tests for tutorial/recovery resources.

```kotlin
assertTrue(defaultStrings.contains("restore replaces current local data"))
assertTrue(chineseStrings.contains("恢复会替换当前本地数据"))
```
- [ ] Replace tutorial cards with four concise steps and direct navigation actions.
- [ ] State restore replacement and permanent-delete consequences in app and docs.
- [ ] Fix mojibake and stale network policy; replace Vite template onboarding.
- [ ] Run localization/tests and commit.

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.ui.AppLocalizationTest`

### Task 4: Simplify Motion and Narrow-Width Layout

**Files:**

- `android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchMotion.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/AssistantScreen.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/ui/ToolbarLayoutPolicyTest.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/ui/WorkbenchComponentsModelTest.kt`

- [ ] Add failing policy tests for the four motion classes and compact action layout.

```kotlin
assertTrue(WorkbenchMotion.StateMillis < WorkbenchMotion.DisclosureMillis)
assertTrue(WorkbenchMotion.DisclosureMillis < WorkbenchMotion.NavigationMillis)
```
- [ ] Remove global/double content-size animations and per-delta animated scrolling.
- [ ] Restrict More click handling to headers with expanded semantics.
- [ ] Make Assistant header and Library metrics wrap safely at 320dp.
- [ ] Run UI policy tests and commit.

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.ui.ToolbarLayoutPolicyTest --tests com.cslearningos.mobile.ui.WorkbenchComponentsModelTest`

### Task 5: Supervision and Release

**Files:**

- `android-app/app/build.gradle`
- `docs/release-notes.md`
- `docs/state-machine.md`
- `docs/superpowers/specs/2026-07-11-android-occam-quality-design.md`
- `docs/superpowers/plans/2026-07-11-android-occam-quality.md`

- [ ] Run full unit tests, architecture harness, and debug build.
- [ ] Dispatch independent `gpt-5.5 medium` spec and code-quality reviewers.
- [ ] Fix every P1/P2 finding and repeat both reviews until clear.
- [ ] Bump Android Beta version and release notes.
- [ ] Commit, tag, run `verify-android-beta.ps1`, push branch/tag, and report APK.

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-android-beta.ps1`
