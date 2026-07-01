# Android Command Center Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Android app's list-first home with a mobile-first command center where search, creation, review, and today's learning state are obvious on entry.

**Architecture:** Keep existing Room, Markdown, quiz, review, backup, and reader-question behavior unchanged. Add a small pure dashboard summary model for testable home decisions, then render the home as a dedicated Compose screen so `LearningOsApp.kt` does not grow again.

**Tech Stack:** Kotlin, Jetpack Compose, Room-backed `LearningUiState`, JUnit local unit tests, existing Workbench visual components.

---

## Acceptance Checklist

- [ ] First screen is not a raw node list.
- [ ] Search, create Markdown node, and review due cards are visible without scrolling on normal portrait phones.
- [ ] The visual language stays aligned with Web: dark grid, yellow accent, bordered cards, uppercase meta, compact density.
- [ ] Existing Markdown create/edit/read behavior remains unchanged.
- [ ] Existing quiz authoring and review behavior remains unchanged.
- [ ] Reader Question capture remains reachable from the reader and its count appears on the home status stack.
- [ ] Library content is still accessible from home as a secondary preview and through the current navigation.
- [ ] Landscape/tablet layout still uses existing adaptive two/three-pane shell.
- [ ] Touch targets remain 48dp minimum through existing `WorkbenchButton` and card patterns.
- [ ] `testDebugUnitTest`, `assembleDebug`, and `android-doctor.ps1` pass after changes.

## File Structure

- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardModels.kt`
  - Pure summary builder for home counts, recent node, and primary action metadata.
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/DashboardModelsTest.kt`
  - Tests that the dashboard prioritizes search/create/review and derives today's learning counts.
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardScreen.kt`
  - Compose-only command center UI using existing Workbench components.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
  - Route `AppScreen.Home` to `DashboardScreen`; keep `LibraryScreen` available for landscape side panes and previews.

## Task 1: Dashboard Model Test

- [ ] **Step 1: Write failing tests**

Create `DashboardModelsTest.kt` with one test for action order and one test for derived counts/recent node.

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.DashboardModelsTest"
```

Expected: compile fails because `buildDashboardSummary` does not exist.

## Task 2: Dashboard Model Implementation

- [ ] **Step 1: Add pure model**

Create `DashboardModels.kt` with `DashboardSummary`, `DashboardAction`, and `buildDashboardSummary(state)`.

- [ ] **Step 2: Verify GREEN**

Run the same targeted test and expect success.

## Task 3: Compose Command Center

- [ ] **Step 1: Create DashboardScreen**

Render hero composer, primary action cards, today's status stack, continue-reading card, and a compact library preview.

- [ ] **Step 2: Wire navigation**

Change `ScreenContent` so `AppScreen.Home` renders `DashboardScreen`.

- [ ] **Step 3: Preserve compatibility**

Do not modify repository APIs, Markdown rendering, quiz review behavior, backup behavior, or database schema.

## Task 4: Strict Verification

- [ ] **Step 1: Run full unit tests**

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest
```

- [ ] **Step 2: Build APK**

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

- [ ] **Step 3: Run Android doctor**

```powershell
.\scripts\android-doctor.ps1
```

- [ ] **Step 4: Final checklist**

Report each acceptance checklist item as pass/fail/untested, with no hand-waving.
