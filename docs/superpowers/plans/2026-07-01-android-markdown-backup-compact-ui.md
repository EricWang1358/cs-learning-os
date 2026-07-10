# Android Markdown, Backup, and Compact UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize Android Markdown rendering, replace raw backup JSON editing with file/share flows, and make non-home pages more compact.

**Architecture:** Parse normal Markdown with `commonmark-java` plus GFM tables into an app-owned render model, keep `:::quiz` as a wrapper-level custom block, move backup transfer mechanics into a dedicated coordinator, and reduce non-home header weight through smaller shared UI helpers and collapsible sections.

**Tech Stack:** Kotlin, Jetpack Compose, Android Activity Result APIs, `FileProvider`, Room-backed repository, `commonmark-java`, JUnit local unit tests, Gradle Android app.

---

## Acceptance Checklist

- [ ] Standard Markdown features render correctly for the approved syntax set.
- [ ] `:::quiz` remains supported.
- [ ] Backup export shares a `.txt` file whose contents are JSON.
- [ ] Backup export saves locally through the system document picker.
- [ ] Backup import restores from `.txt` or `.json` by parsing file contents.
- [ ] The raw backup text field is removed.
- [ ] Non-home page headers are less visually heavy.
- [ ] Library and backup flows use more collapsible space-saving layout.
- [ ] `testDebugUnitTest` passes.
- [ ] `assembleDebug` passes.

## File Structure

- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/markdown/StandardMarkdownDocument.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/markdown/QuizAwareMarkdownDocument.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/backup/BackupTransferCoordinator.kt`
- Create: `android-app/app/src/main/res/xml/file_paths.xml`
- Modify: `android-app/app/build.gradle`
- Modify: `android-app/app/src/main/AndroidManifest.xml`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MarkdownRenderer.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt`
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/values-zh/strings.xml`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/markdown/QuizAwareMarkdownDocumentTest.kt`
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/backup/BackupTransferCoordinatorTest.kt`

## Task 1: Write Failing Tests For Markdown And Backup Helpers

**Files:**
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/markdown/QuizAwareMarkdownDocumentTest.kt`
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/backup/BackupTransferCoordinatorTest.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`

- [ ] **Step 1: Add Markdown adapter tests**

Cover:

- headings, emphasis, inline code, links, blockquotes, ordered/unordered lists, horizontal rules, code blocks, and tables
- preserving `:::quiz` as a separate custom block

- [ ] **Step 2: Add backup transfer tests**

Cover:

- generated filename prefix `cs-learning-os-backup-`
- `.txt` suffix
- JSON content passthrough

- [ ] **Step 3: Add regression test for readable repository behavior if touched**

Keep current repository backup behavior stable while new transfer helpers are added.

- [ ] **Step 4: Verify RED**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.markdown.*" --tests "com.cslearningos.mobile.ui.backup.*"
```

Expected: FAIL because the new adapter/coordinator types do not exist yet.

## Task 2: Implement Standard Markdown Adapter

**Files:**
- Modify: `android-app/app/build.gradle`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/markdown/StandardMarkdownDocument.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/markdown/QuizAwareMarkdownDocument.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MarkdownRenderer.kt`

- [ ] **Step 1: Add Markdown parser dependencies**

Add `org.commonmark:commonmark` and `org.commonmark:commonmark-ext-gfm-tables`.

- [ ] **Step 2: Build app-owned Markdown block/span models**

Parse standard Markdown into focused render models instead of raw line heuristics.

- [ ] **Step 3: Keep `:::quiz` support in a wrapper layer**

Split quiz fences first, then parse the remaining Markdown with the standard parser.

- [ ] **Step 4: Render the new model in Compose**

Keep the current visual language where possible while expanding syntax support.

- [ ] **Step 5: Verify GREEN**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.markdown.*"
```

Expected: PASS.

## Task 3: Implement File-Based Backup Transfer

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/backup/BackupTransferCoordinator.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Modify: `android-app/app/src/main/AndroidManifest.xml`
- Create: `android-app/app/src/main/res/xml/file_paths.xml`

- [ ] **Step 1: Add backup document helper**

Create a coordinator that:

- formats the export filename
- writes a shareable cache file
- writes export text to a picked document URI
- reads imported file text back into the app

- [ ] **Step 2: Refactor view-model backup APIs**

Expose export/restore methods that deal in raw JSON payloads and stop depending on editable `backupText` UI state.

- [ ] **Step 3: Add Android share/import plumbing**

Use `rememberLauncherForActivityResult` for save/import and `ACTION_SEND` plus `FileProvider` for sharing.

- [ ] **Step 4: Replace backup text editor UI**

Swap the editor for action tiles/buttons and compact helper copy.

- [ ] **Step 5: Verify backup helper tests**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.backup.*"
```

Expected: PASS.

## Task 4: Compact Non-Home Headers And Add Collapsible Sections

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/values-zh/strings.xml`

- [ ] **Step 1: Lower header visual weight**

Shrink non-home title blocks and tone down their card treatment.

- [ ] **Step 2: Add shared collapsible helper UI**

Use it first for backup help and library overview/map context.

- [ ] **Step 3: Move explanatory copy lower**

Keep primary task actions/content higher than descriptive text on non-home pages.

- [ ] **Step 4: Verify touched flows build**

Run:

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

Expected: PASS.

## Task 5: Full Verification

**Files:**
- All touched files above

- [ ] **Step 1: Run full unit tests**

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest
```

- [ ] **Step 2: Build debug APK**

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

- [ ] **Step 3: Check acceptance items one by one**

Report each checklist item as pass/fail/untested from fresh evidence.
