# Android GFM Table Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render assistant-produced GFM tables as aligned, readable phone tables with safe normalization, horizontal scrolling, and a full-screen reader for large tables.

**Architecture:** Preserve CommonMark plus `TablesExtension`. Normalize only structurally complete assistant tables before parsing. `MarkdownRenderer` owns compact/full-screen presentation state and never mutates Markdown or Room data.

**Tech Stack:** Kotlin, CommonMark 0.27, Jetpack Compose Foundation/Material3, Robolectric, JUnit 4, Android emulator.

---

## Task 1: Normalize Complete Assistant Tables

**Files:**
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/markdown/AssistantMarkdownNormalizerTest.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/markdown/AssistantMarkdownNormalizer.kt`

- [ ] Write failing tests for no-leading-pipe tables, prose immediately before a table, and a fenced `a | b` sample that remains unchanged.
- [ ] Run `cd android-app; .\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.markdown.AssistantMarkdownNormalizerTest --console=plain`; expect failure because missing boundary pipes are retained.
- [ ] Outside code and quiz fences, recognize only `header -> TableDelimiterLine -> data row+`. Normalize recognized rows with `"|${line.trim().trim('|').trim()}|"`; insert one blank line before a table if prior output is nonblank/non-table. Leave incomplete candidates and ordinary pipe text untouched.
- [ ] Re-run the focused test; expect pass. Commit the normalizer and tests as `fix(android): normalize assistant gfm tables`.

## Task 2: Prove The Existing GFM AST Receives The Table

**Files:**
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/markdown/QuizAwareMarkdownDocumentTest.kt`
- Modify only for a demonstrated mapping defect: `android-app/app/src/main/java/com/cslearningos/mobile/ui/markdown/StandardMarkdownDocument.kt`

- [ ] Add a failing test that parses a no-leading-pipe table through `QuizAwareMarkdownDocument.parse` and asserts one `MarkdownTableBlock`, two headers, and one row.
- [ ] Run `cd android-app; .\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.ui.markdown.QuizAwareMarkdownDocumentTest --console=plain`; expect failure before normalization reaches the parser.
- [ ] Retain `Parser.builder().extensions(listOf(TablesExtension.create()))`; allow normalized Markdown through the existing `TableBlock` mapper and change AST code only if the new test proves a mapping fault.
- [ ] Run both Task 1 and Task 2 test classes; expect pass. Commit as `test(android): cover assistant table ast`.

## Task 3: Render Scrollable Compact And Full-Screen Tables

**Files:**
- Modify: `android-app/app/build.gradle`
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/MarkdownRendererTableTest.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MarkdownRenderer.kt`

- [ ] Add `testImplementation "androidx.compose.ui:ui-test-junit4"` if missing. Write a failing Compose semantics test: a three-column table exposes `Expand table`; clicking it exposes `Exit table view`; clicking exit restores compact semantics.
- [ ] Run `cd android-app; .\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.ui.MarkdownRendererTableTest --console=plain`; expect failure because no expand/exit semantics exist.
- [ ] Keep `MarkdownTableBlock` unchanged. Replace equal-width table rows with a horizontally scrollable surface using `widthIn(min = 144.dp)` cells, so prose wraps within a stable column and columns cannot collapse. Set `expandable = columnCount > 2 || rows.size > 4`. Add an icon-only `Expand table` control and a full-width Dialog with horizontal/vertical scroll plus icon-only `Exit table view`.
- [ ] Run the renderer, AST, and normalizer test classes; expect pass. Commit as `feat(android): render expandable gfm tables`.

## Task 4: Device Verification

- [ ] Run `cd android-app; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain --rerun-tasks`; expect BUILD SUCCESSFUL.
- [ ] Install the debug APK on the emulator. Verify a no-leading-pipe assistant table wraps cells with aligned columns, opens full screen, pans horizontally/vertically, and exits. Capture compact and expanded `1080x2400` screenshots without committing temporary artifacts.
