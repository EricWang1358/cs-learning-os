# Android Library Tree Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Android Library cards and the separate Area screen with a compact inline folder tree and a terminal, recoverable Trash row.

**Architecture:** Keep Room, backup, Reader and repository contracts unchanged. `LibraryModels.kt` builds Area and Trash presentation rows; `LearningViewModel` owns the single expanded row; `LibraryScreen.kt` renders a single list where Areas disclose inline and Nodes route straight to Reader.

**Tech Stack:** Kotlin, Jetpack Compose, Room, JUnit4, Android Gradle Plugin.

---

### Task 1: Define Tree Presentation and Expansion State

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryModels.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/LibraryModelsTest.kt`

- [ ] **Step 1: Write failing model tests**

Add tests asserting sorted Area rows are followed by one `Trash` system row, Trash carries its recoverable-node count and original Area metadata, and normal Area actions omit direct deletion.

- [ ] **Step 2: Run focused test and confirm failure**

Run: `./gradlew testDebugUnitTest --tests com.cslearningos.mobile.ui.LibraryModelsTest`

Expected: FAIL because tree row and Trash types do not exist.

- [ ] **Step 3: Implement minimal model and ViewModel state**

Introduce `LibraryTreeRow` (Area or Trash), make `buildLibraryTreeModel(areas, nodes, trashNodes, dueQuizzes, context)` append the terminal Trash row, and add `toggleLibraryArea(areaId)` which replaces the expanded Area ID or clears it when tapped again. Retain `LibraryNodeRow` fields and `buildLibraryAreaDetail` filtering.

- [ ] **Step 4: Run focused test and confirm pass**

Run: `./gradlew testDebugUnitTest --tests com.cslearningos.mobile.ui.LibraryModelsTest`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryModels.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt android-app/app/src/test/java/com/cslearningos/mobile/ui/LibraryModelsTest.kt; git commit -m "feat: model Android library tree rows"`

### Task 2: Render the Inline Area and Node Tree

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningUiModels.kt`
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/values-zh/strings.xml`

- [ ] **Step 1: Write failing UI policy tests**

Extend model/UI tests to verify one expanded Area at a time and that an expanded Node row has the Reader navigation callback rather than an inline disclosure callback.

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./gradlew testDebugUnitTest --tests com.cslearningos.mobile.ui.LibraryModelsTest`

Expected: FAIL until the tree interaction policy is represented.

- [ ] **Step 3: Replace root/detail navigation with the tree list**

Render Areas as full-width clickable rows with folder, title, track preview, count and localized expanded semantics. Render expanded content inline with frameless `TextButton` actions for New node, Edit, More and checked filtering. Render Node rows with title, track, checked/due/recency metadata and route `onClick` to `viewModel.openNode(node)`; do not render Read or expand controls. Keep dialogs and mutation flows for rename, create, move and empty-Area delete in secondary actions.

- [ ] **Step 4: Add localized semantic and Trash labels**

Add only strings used by the new Area/Trash row and ensure all copy has Chinese translations.

- [ ] **Step 5: Run focused tests and assemble**

Run: `./gradlew testDebugUnitTest --tests com.cslearningos.mobile.ui.LibraryModelsTest assembleDebug`

Expected: PASS and an APK under `android-app/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 6: Commit**

Run: `git add android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningUiModels.kt android-app/app/src/main/res/values/strings.xml android-app/app/src/main/res/values-zh/strings.xml android-app/app/src/test/java/com/cslearningos/mobile/ui/LibraryModelsTest.kt; git commit -m "feat: render inline Android library tree"`

### Task 3: Move Recoverable Trash into Library

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/library/data/LearningRepositoryPolicyTest.kt`

- [ ] **Step 1: Verify existing restore policy test**

Run: `./gradlew testDebugUnitTest --tests '*LearningRepositoryPolicyTest*'`

Expected: PASS, including restore to the stored Area and permanent-delete policy.

- [ ] **Step 2: Add inline Trash detail**

Append an always-visible, final Trash row to the Library list. Its full row toggles inline content; each item shows title, original Area name and deletion recency, with frameless Restore and Delete forever actions. Reuse `ConfirmDestructiveDialog` before `viewModel.permanentlyDeleteNode(node)` and call `viewModel.restoreNode(node)` for Restore.

- [ ] **Step 3: Remove duplicate More Trash presentation**

Remove the More-screen Trash section and its local destructive-dialog state while retaining unrelated More sections. Do not change repository delete, restore, search, or backup code.

- [ ] **Step 4: Run Trash policy tests**

Run: `./gradlew testDebugUnitTest --tests '*LearningRepositoryPolicyTest*'`

Expected: PASS; no migration or persistence contract changes.

- [ ] **Step 5: Commit**

Run: `git add android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt android-app/app/src/test/java/com/cslearningos/mobile/feature/library/data/LearningRepositoryPolicyTest.kt; git commit -m "feat: place recoverable trash in library"`

### Task 4: Verify the Feature and Release Artifact

**Files:**
- Modify: `docs/superpowers/specs/2026-07-11-android-library-tree-design.md` only if implementation deviates from approved behavior.

- [ ] **Step 1: Run full verification**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: all unit tests PASS and debug APK generated at `android-app/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Run architecture checks**

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-android-architecture.ps1`

Expected: script exits 0 with no architecture violations.

- [ ] **Step 3: Inspect final diff and status**

Run: `git diff --check; git status --short; git log --oneline -4`

Expected: no whitespace errors; only intentional tracked changes and unrelated `.playwright-cli/` remains untracked in the original worktree.

- [ ] **Step 4: Commit final documentation only if changed**

Run: `git add docs/superpowers/specs/2026-07-11-android-library-tree-design.md; git commit -m "docs: record library tree verification"`

