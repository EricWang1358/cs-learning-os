# Android Library Folder Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the Android Library into a folder-style browser with real `Area` entities, direct node file lists, and persistent lightweight node marking.

**Architecture:** Add a first-class `AreaEntity` plus node fields for `areaId` and `isChecked`, migrate existing data forward, then rebuild the Library state/models/UI around a root-area list and an area-detail node list. Keep the current app navigation shell, but move folder-specific models and composables into focused files so the screen does not grow back into a stone-pile implementation.

**Tech Stack:** Kotlin, Room, Jetpack Compose, Android ViewModel/StateFlow, JUnit local unit tests, Gradle Android app.

---

## Acceptance Checklist

- [ ] Library root opens to an `Area` folder list.
- [ ] Entering an `Area` opens a direct node list.
- [ ] `track` is reduced to lightweight metadata.
- [ ] Android can create, rename, and delete empty `Area` folders.
- [ ] Android can move a node to another `Area`.
- [ ] Empty `Area` folders render correctly.
- [ ] Node checked state persists.
- [ ] Library can filter between `all` and `checked`.
- [ ] Focused unit tests pass.
- [ ] `:app:compileDebugKotlin` passes.
- [ ] `assembleDebug` passes.

## File Structure

- Create: `android-app/app/src/main/java/com/cslearningos/mobile/data/AreaEntity.kt`
  - Defines the first-class Area Room entity.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningEntities.kt`
  - Adds `areaId` and `isChecked` to nodes and quizzes as needed for snapshot behavior.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningDatabase.kt`
  - Adds schema migration for areas and node checked state.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningDao.kt`
  - Adds queries and mutations for areas and node movement/check state.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt`
  - Adds area lifecycle and library node move/check behavior.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/BackupCodec.kt`
  - Keeps backup import/export backward-compatible while including areas and checked state.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
  - Adds library root/detail state, area management actions, and checked filtering.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryModels.kt`
  - Rebuilds library models around folder rows and file rows.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt`
  - Replaces the current stacked-card library with folder and file-list screens.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
  - Wires any new dialogs or screen chrome hooks needed by the redesigned Library.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/UiLabelResources.kt`
  - Keeps fallback labels working for migrated or imported areas.
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/values-zh/strings.xml`
  - Adds area-management and folder-style library copy.
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/LibraryModelsTest.kt`
  - Verifies root folders, area detail rows, and checked filtering.
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/data/BackupCodecAreaCompatibilityTest.kt`
  - Verifies old backups still decode and new area payloads round-trip.

## Task 1: Model The Folder-Oriented Library In Tests

**Files:**
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/LibraryModelsTest.kt`
- Modify in Task 2: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryModels.kt`

- [ ] **Step 1: Write the failing tests**
- [ ] **Step 2: Run the focused test and watch it fail**
- [ ] **Step 3: Implement the minimal folder-model helpers**
- [ ] **Step 4: Re-run the focused test and watch it pass**

## Task 2: Add First-Class Areas And Node Check State

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/data/AreaEntity.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningEntities.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningDatabase.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningDao.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt`

- [ ] **Step 1: Add a failing compatibility test around area/check persistence**
- [ ] **Step 2: Run the focused test and verify the failure is real**
- [ ] **Step 3: Add the Area schema, migration, DAO operations, and repository methods**
- [ ] **Step 4: Re-run the focused tests and verify green**

## Task 3: Rebuild Library State And Folder Management Actions

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryModels.kt`

- [ ] **Step 1: Add a failing view-model/model test for root/detail navigation and checked filtering**
- [ ] **Step 2: Run the focused tests and verify failure**
- [ ] **Step 3: Implement root/detail library state, create/rename/delete-empty/move/check actions**
- [ ] **Step 4: Re-run the focused tests and verify green**

## Task 4: Replace The Library UI With Folder And File Lists

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/values-zh/strings.xml`

- [ ] **Step 1: Add a failing compile-oriented expectation for the new Library screen contract**
- [ ] **Step 2: Run `:app:compileDebugKotlin` and confirm the contract gap**
- [ ] **Step 3: Implement the folder list, area detail list, dialogs, and low-contrast secondary sections**
- [ ] **Step 4: Re-run `:app:compileDebugKotlin` and verify green**

## Task 5: Preserve Backup Compatibility

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/BackupCodec.kt`
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/data/BackupCodecAreaCompatibilityTest.kt`

- [ ] **Step 1: Write failing tests for old-backup decode and new-backup round-trip**
- [ ] **Step 2: Run the focused tests and watch them fail**
- [ ] **Step 3: Implement backward-compatible encode/decode for areas and checked state**
- [ ] **Step 4: Re-run the focused tests and watch them pass**

## Task 6: Full Verification

**Files:**
- All files touched in Tasks 1-5

- [ ] **Step 1: Run focused library and backup tests**
- [ ] **Step 2: Run `.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`**
- [ ] **Step 3: Run `.\gradlew.bat assembleDebug --no-daemon --console=plain`**
- [ ] **Step 4: Check the acceptance checklist against the implementation and record the result**
