# Android Polish Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make non-home Android screens more compact, split overloaded UI files into clearer screen units, and finish the Markdown and backup interactions introduced in the previous pass.

**Architecture:** Add a small screen-chrome policy layer so compaction decisions live in one place, then extract `Library` and `Backup` out of `LearningOsApp.kt` while reusing shared compact header/help components. Keep the current Markdown parser architecture, but move text annotation and link interaction into a focused helper, and add a small backup error formatter so import/export feedback becomes predictable and user-facing.

**Tech Stack:** Kotlin, Jetpack Compose, Android Activity Result APIs, Room-backed view model flows, JUnit local unit tests, existing CommonMark adapter layer, Gradle Android app.

---

## Acceptance Checklist

- [ ] Non-home screens show the primary task before most explanatory copy.
- [ ] `Capture`, `More`, `Backup`, `Library`, `Search`, and `Review` feel more compact.
- [ ] `Library` reaches node content faster on first entry.
- [ ] `Backup` reads as a file/share/import flow rather than a JSON editor flow.
- [ ] Markdown links are tappable.
- [ ] Markdown tables and lists render more reliably on narrow screens.
- [ ] `LearningOsApp.kt` is meaningfully smaller after screen extraction.
- [ ] `testDebugUnitTest` passes.
- [ ] `assembleDebug` passes.

## File Structure

- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/CompactScreenChrome.kt`
  - Central policy for compact vs hero screen chrome and help placement.
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt`
  - Extracted `Library` composables and compact overview/map behavior.
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/BackupScreen.kt`
  - Extracted backup action screen and file-flow helper UI.
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/markdown/MarkdownTextAnnotations.kt`
  - Build annotated strings plus link metadata from parsed Markdown inlines.
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/backup/BackupImportErrorFormatter.kt`
  - Normalize backup import/export failures into user-facing copy.
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/CompactScreenChromeTest.kt`
  - Verifies per-screen compaction policy.
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/markdown/MarkdownTextAnnotationsTest.kt`
  - Verifies link annotations and inline formatting output.
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/backup/BackupImportErrorFormatterTest.kt`
  - Verifies backup error mapping.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
  - Delegate screen ownership to extracted files and remove inlined backup/library chunks.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/CaptureScreen.kt`
  - Move explanation behind compact help and tighten composer-first layout.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
  - Tighten section chrome and update backup entry wording.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MarkdownRenderer.kt`
  - Use annotation helper and tap handling for links; refine spacing.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
  - Use backup error formatter for import/export feedback.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt`
  - Add/adjust compact header and collapsible help primitives.
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/values-zh/strings.xml`
  - Update backup and compact-screen wording to match the new file-based flow.

## Task 1: Add Screen Chrome Policy Tests First

**Files:**
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/CompactScreenChromeTest.kt`
- Create in Task 2: `android-app/app/src/main/java/com/cslearningos/mobile/ui/CompactScreenChrome.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cslearningos.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompactScreenChromeTest {
    @Test
    fun homeKeepsHeroChrome() {
        assertEquals(
            ScreenChromePolicy(
                style = ScreenChromeStyle.Hero,
                helpPlacement = ScreenHelpPlacement.Top,
                primaryFlow = ScreenPrimaryFlow.ContextFirst
            ),
            screenChromePolicy(AppScreen.Home)
        )
    }

    @Test
    fun backupLibraryAndMoreUseCompactPostActionHelp() {
        val expected = ScreenChromePolicy(
            style = ScreenChromeStyle.Compact,
            helpPlacement = ScreenHelpPlacement.AfterPrimaryActionsCollapsed,
            primaryFlow = ScreenPrimaryFlow.TaskFirst
        )

        assertEquals(expected, screenChromePolicy(AppScreen.Backup))
        assertEquals(expected, screenChromePolicy(AppScreen.Library))
        assertEquals(expected, screenChromePolicy(AppScreen.More))
    }

    @Test
    fun readerEditorAndQuizEditorKeepInlineDetailChrome() {
        val expected = ScreenChromePolicy(
            style = ScreenChromeStyle.Compact,
            helpPlacement = ScreenHelpPlacement.InlineDetail,
            primaryFlow = ScreenPrimaryFlow.TaskFirst
        )

        assertEquals(expected, screenChromePolicy(AppScreen.Reader))
        assertEquals(expected, screenChromePolicy(AppScreen.Editor))
        assertEquals(expected, screenChromePolicy(AppScreen.QuizEditor))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.CompactScreenChromeTest" --no-daemon --console=plain
```

Expected: FAIL with unresolved references for `ScreenChromePolicy`, `ScreenChromeStyle`, `ScreenHelpPlacement`, and `screenChromePolicy`.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.cslearningos.mobile.ui

enum class ScreenChromeStyle {
    Hero,
    Compact
}

enum class ScreenHelpPlacement {
    Top,
    AfterPrimaryActionsCollapsed,
    InlineDetail
}

enum class ScreenPrimaryFlow {
    ContextFirst,
    TaskFirst
}

data class ScreenChromePolicy(
    val style: ScreenChromeStyle,
    val helpPlacement: ScreenHelpPlacement,
    val primaryFlow: ScreenPrimaryFlow
)

fun screenChromePolicy(screen: AppScreen): ScreenChromePolicy =
    when (screen) {
        AppScreen.Home -> ScreenChromePolicy(
            style = ScreenChromeStyle.Hero,
            helpPlacement = ScreenHelpPlacement.Top,
            primaryFlow = ScreenPrimaryFlow.ContextFirst
        )

        AppScreen.Reader,
        AppScreen.Editor,
        AppScreen.QuizEditor -> ScreenChromePolicy(
            style = ScreenChromeStyle.Compact,
            helpPlacement = ScreenHelpPlacement.InlineDetail,
            primaryFlow = ScreenPrimaryFlow.TaskFirst
        )

        else -> ScreenChromePolicy(
            style = ScreenChromeStyle.Compact,
            helpPlacement = ScreenHelpPlacement.AfterPrimaryActionsCollapsed,
            primaryFlow = ScreenPrimaryFlow.TaskFirst
        )
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.CompactScreenChromeTest" --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/ui/CompactScreenChrome.kt android-app/app/src/test/java/com/cslearningos/mobile/ui/CompactScreenChromeTest.kt
git commit -m "test: add android screen chrome policy"
```

## Task 2: Extract Library And Backup Screens Around The New Compact Policy

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/BackupScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt`

- [ ] **Step 1: Write the failing build-oriented extraction check**

Change the existing `ScreenContent` branches in `LearningOsApp.kt` so the compile graph expects extracted files:

```kotlin
AppScreen.Library -> LibraryScreen(state = state, viewModel = viewModel)
AppScreen.Backup -> BackupScreen(state = state, viewModel = viewModel)
```

- [ ] **Step 2: Run build to verify it fails**

Run:

```powershell
cd android-app
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: FAIL because the extracted `LibraryScreen` and `BackupScreen` files do not exist yet.

- [ ] **Step 3: Write the minimal implementation**

Create `LibraryScreen.kt` with the moved library composables and compacted defaults:

```kotlin
@Composable
fun LibraryScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val context = LocalContext.current
    val groups = buildLibraryGroups(state.nodes, context)
    val overview = buildLibraryOverview(state.nodes, context)
    val map = buildLibraryMap(state.nodes, state.collapsedLibraryAreas, context)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CollapsibleWorkbenchSection(
            eyebrow = stringResource(R.string.library_overview_eyebrow),
            title = stringResource(R.string.library_overview_title),
            body = stringResource(R.string.library_overview_body, overview.structureLabel),
            expandLabel = stringResource(R.string.common_open),
            collapseLabel = stringResource(R.string.common_close),
            initiallyExpanded = false
        ) {
            LibraryOverviewMetrics(overview = overview)
        }

        CollapsibleWorkbenchSection(
            eyebrow = stringResource(R.string.library_map_eyebrow),
            title = stringResource(R.string.library_map_title),
            body = stringResource(R.string.library_map_body),
            expandLabel = stringResource(R.string.common_open),
            collapseLabel = stringResource(R.string.common_close),
            initiallyExpanded = false
        ) {
            map.areas.forEach { area -> LibraryMapRow(area = area, onToggleArea = viewModel::toggleLibraryArea) }
        }

        WorkbenchButton(
            text = stringResource(R.string.library_new_node_button),
            onClick = viewModel::startNewNode,
            primary = true,
            modifier = Modifier.fillMaxWidth()
        )

        groups.forEach { area -> LibraryAreaSection(area = area, state = state, viewModel = viewModel) }
    }
}

@Composable
private fun LibraryOverviewMetrics(overview: LibraryOverview) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MetaPill(stringResource(R.string.library_areas_label), overview.areaCount.toString(), Modifier.weight(1f))
        MetaPill(stringResource(R.string.library_tracks_label), overview.trackCount.toString(), Modifier.weight(1f))
        MetaPill(stringResource(R.string.common_nodes), overview.nodeCount.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun LibraryMapRow(area: LibraryMapArea, onToggleArea: (String) -> Unit) {
    InteractiveCard(onClick = { onToggleArea(area.area) }, accent = !area.collapsed) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(area.label, color = WorkbenchColors.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(area.trackPreview, color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            MetaPill(
                stringResource(if (area.collapsed) R.string.library_map_closed else R.string.library_map_open),
                "${area.nodeCount}N/${area.trackCount}T"
            )
        }
    }
}

@Composable
private fun LibraryAreaSection(area: LibraryAreaGroup, state: LearningUiState, viewModel: LearningViewModel) {
    val context = LocalContext.current
    val collapsed = area.area in state.collapsedLibraryAreas

    WorkbenchCard {
        Eyebrow(stringResource(R.string.library_area_eyebrow))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(readableAreaLabel(context, area.area), color = WorkbenchColors.InkStrong, fontSize = 22.sp, fontWeight = FontWeight.Black)
            WorkbenchButton(
                stringResource(if (collapsed) R.string.common_open else R.string.common_close),
                { viewModel.toggleLibraryArea(area.area) }
            )
        }
        if (!collapsed) {
            area.tracks.forEach { track ->
                track.nodes.forEach { item ->
                    LibraryNodeCard(
                        item = item,
                        selected = state.selectedNode?.id == item.id,
                        onOpen = { viewModel.openNode(item.node) },
                        onEdit = { viewModel.editNode(item.node) }
                    )
                }
            }
        }
    }
}
```

Create `BackupScreen.kt` with action-first order:

```kotlin
@Composable
fun BackupScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDocument by remember { mutableStateOf<BackupDocument?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val document = pendingDocument
        pendingDocument = null
        if (uri != null && document != null) {
            scope.launch {
                runCatching { BackupTransferCoordinator.writeToUri(context.contentResolver, uri, document) }
                    .onSuccess { viewModel.noteBackupSavedToDevice() }
                    .onFailure { error -> viewModel.showBackupError(error) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { BackupTransferCoordinator.readImportedText(context.contentResolver, uri) }
                    .onSuccess { rawJson -> viewModel.restoreBackupFromJson(rawJson) }
                    .onFailure { error -> viewModel.showBackupError(error) }
            }
        }
    }
    val onShareBackup: () -> Unit = {
        scope.launch {
            runCatching { viewModel.createBackupDocument() }
                .onSuccess { document ->
                    val uri = BackupTransferCoordinator.writeShareFile(context, document)
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = document.mimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            context.getString(R.string.backup_share_chooser_title)
                        )
                    )
                    viewModel.noteBackupShared()
                }
                .onFailure { error -> viewModel.showBackupError(error) }
        }
    }
    val onSaveBackupLocally: () -> Unit = {
        scope.launch {
            runCatching { viewModel.createBackupDocument() }
                .onSuccess { document ->
                    pendingDocument = document
                    saveLauncher.launch(document.fileName)
                }
                .onFailure { error -> viewModel.showBackupError(error) }
        }
    }
    val onImportBackup: () -> Unit = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = stringResource(R.string.backup_eyebrow),
            title = stringResource(R.string.backup_title),
            body = stringResource(R.string.backup_body)
        )

        ToolbarRow {
            WorkbenchButton(text = stringResource(R.string.backup_share_full), onClick = onShareBackup, primary = true)
            WorkbenchButton(text = stringResource(R.string.backup_save_local), onClick = onSaveBackupLocally)
            WorkbenchButton(text = stringResource(R.string.backup_import_file), onClick = onImportBackup, danger = true)
        }

        CollapsibleWorkbenchSection(
            eyebrow = stringResource(R.string.backup_actions_eyebrow),
            title = stringResource(R.string.backup_help_title),
            body = stringResource(R.string.backup_help_body),
            expandLabel = stringResource(R.string.common_open),
            collapseLabel = stringResource(R.string.common_close),
            initiallyExpanded = false
        ) {
            BackupHelpPills()
        }
    }
}

@Composable
private fun BackupHelpPills() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetaPill(stringResource(R.string.backup_share_full), ".txt / JSON")
        MetaPill(stringResource(R.string.backup_save_local), "QQ / WeChat / Files")
        MetaPill(stringResource(R.string.backup_import_file), ".txt / .json")
    }
}
```

Replace the moved sections in `LearningOsApp.kt` with imports and direct calls only.

- [ ] **Step 4: Run build to verify it passes**

Run:

```powershell
cd android-app
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/BackupScreen.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt
git commit -m "refactor: extract compact library and backup screens"
```

## Task 3: Compact Capture, More, Search, Review, And Detail Headers

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/CaptureScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt`
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/values-zh/strings.xml`

- [ ] **Step 1: Write the failing chrome-policy extension test**

Extend `CompactScreenChromeTest.kt` with a new helper expectation:

```kotlin
@Test
fun nonHomeHelpStartsCollapsed() {
    assertFalse(screenHelpInitiallyExpanded(AppScreen.Capture))
    assertFalse(screenHelpInitiallyExpanded(AppScreen.Backup))
    assertFalse(screenHelpInitiallyExpanded(AppScreen.More))
}
```

- [ ] **Step 2: Run test to verify current failures**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.CompactScreenChromeTest" --no-daemon --console=plain
```

Expected: FAIL with unresolved reference `screenHelpInitiallyExpanded`.

- [ ] **Step 3: Write the minimal implementation**

Update `CaptureScreen.kt` so the composer leads and the AI explanation moves behind disclosure:

```kotlin
fun screenHelpInitiallyExpanded(screen: AppScreen): Boolean =
    screen == AppScreen.Home

@Composable
fun CaptureScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = stringResource(R.string.capture_eyebrow),
            title = stringResource(R.string.capture_title),
            body = stringResource(R.string.capture_body)
        )

        CaptureComposer(state = state, viewModel = viewModel)

        CollapsibleWorkbenchSection(
            eyebrow = stringResource(R.string.capture_chain_eyebrow),
            title = stringResource(R.string.capture_chain_title),
            body = stringResource(R.string.capture_ai_chain_body),
            expandLabel = stringResource(R.string.common_open),
            collapseLabel = stringResource(R.string.common_close),
            initiallyExpanded = screenHelpInitiallyExpanded(AppScreen.Capture)
        ) {
            Text(
                text = stringResource(R.string.capture_chain_detail),
                color = WorkbenchColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }

        AiDraftPreflight(state = state, viewModel = viewModel)
        CaptureInbox(state = state, viewModel = viewModel)
    }
}
```

Update `MoreScreen.kt` so sections read like settings rows:

```kotlin
Text(
    text = if (expanded) stringResource(R.string.common_close) else stringResource(R.string.common_expand),
    color = WorkbenchColors.Accent,
    fontSize = 10.sp,
    fontWeight = FontWeight.Black
)
```

Update `SectionHeader` and `DetailHeading` in `WorkbenchComponents.kt` to keep non-home headers smaller and lower-contrast than the home chrome.

- [ ] **Step 4: Run build to verify it passes**

Run:

```powershell
cd android-app
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/ui/CaptureScreen.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt android-app/app/src/main/res/values/strings.xml android-app/app/src/main/res/values-zh/strings.xml android-app/app/src/test/java/com/cslearningos/mobile/ui/CompactScreenChromeTest.kt
git commit -m "feat: compact non-home android screens"
```

## Task 4: Add Failing Tests For Markdown Text Annotations

**Files:**
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/markdown/MarkdownTextAnnotationsTest.kt`
- Create in Task 5: `android-app/app/src/main/java/com/cslearningos/mobile/ui/markdown/MarkdownTextAnnotations.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cslearningos.mobile.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextAnnotationsTest {
    @Test
    fun buildAnnotatedMarkdownTextPreservesLinkTarget() {
        val result = buildMarkdownAnnotatedText(
            listOf(
                MarkdownTextInline("See "),
                MarkdownLinkInline(
                    destination = "https://example.com/cache",
                    children = listOf(MarkdownTextInline("cache article"))
                )
            )
        )

        val annotations = result.text.getStringAnnotations(
            tag = MarkdownLinkAnnotationTag,
            start = 0,
            end = result.text.length
        )

        assertEquals("See cache article", result.text.text)
        assertEquals(1, annotations.size)
        assertEquals("https://example.com/cache", annotations.single().item)
    }

    @Test
    fun buildAnnotatedMarkdownTextPreservesLineBreaksAndCodeRuns() {
        val result = buildMarkdownAnnotatedText(
            listOf(
                MarkdownTextInline("Use "),
                MarkdownCodeInline("mov"),
                MarkdownLineBreakInline,
                MarkdownStrongInline(listOf(MarkdownTextInline("carefully")))
            )
        )

        assertTrue(result.text.text.contains("\n"))
        assertEquals("mov", result.codeSpans.single().text)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.markdown.MarkdownTextAnnotationsTest" --no-daemon --console=plain
```

Expected: FAIL with unresolved references for `buildMarkdownAnnotatedText`, `MarkdownLinkAnnotationTag`, and the result type.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.cslearningos.mobile.ui.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

const val MarkdownLinkAnnotationTag = "markdown-link"

data class MarkdownCodeSpan(
    val text: String
)

data class MarkdownAnnotatedText(
    val text: AnnotatedString,
    val codeSpans: List<MarkdownCodeSpan>
)

fun buildMarkdownAnnotatedText(inlines: List<MarkdownInline>): MarkdownAnnotatedText {
    val codeSpans = mutableListOf<MarkdownCodeSpan>()
    val text = buildAnnotatedString {
        appendInlines(
            inlines = inlines,
            codeSpans = codeSpans
        )
    }
    return MarkdownAnnotatedText(text = text, codeSpans = codeSpans)
}

private fun AnnotatedString.Builder.appendInlines(
    inlines: List<MarkdownInline>,
    codeSpans: MutableList<MarkdownCodeSpan>
) {
    inlines.forEach { inline ->
        when (inline) {
            is MarkdownTextInline -> append(inline.text)
            MarkdownLineBreakInline -> append("\n")
            is MarkdownCodeInline -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                append(inline.text)
                codeSpans += MarkdownCodeSpan(inline.text)
            }
            is MarkdownStrongInline -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlines(inline.children, codeSpans)
            }
            is MarkdownEmphasisInline -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlines(inline.children, codeSpans)
            }
            is MarkdownLinkInline -> {
                pushStringAnnotation(MarkdownLinkAnnotationTag, inline.destination)
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    appendInlines(inline.children, codeSpans)
                }
                pop()
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.markdown.MarkdownTextAnnotationsTest" --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/ui/markdown/MarkdownTextAnnotations.kt android-app/app/src/test/java/com/cslearningos/mobile/ui/markdown/MarkdownTextAnnotationsTest.kt
git commit -m "test: add markdown text annotation helpers"
```

## Task 5: Wire Markdown Links And Backup Error Handling Into The UI

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/backup/BackupImportErrorFormatter.kt`
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/backup/BackupImportErrorFormatterTest.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MarkdownRenderer.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/values-zh/strings.xml`

- [ ] **Step 1: Write the failing backup error test**

```kotlin
package com.cslearningos.mobile.ui.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONException
import java.io.IOException

class BackupImportErrorFormatterTest {
    @Test
    fun formatsInvalidJsonErrors() {
        assertEquals("invalid_json", backupImportErrorKey(JSONException("bad json")))
    }

    @Test
    fun formatsUnreadableFileErrors() {
        assertEquals("unreadable_file", backupImportErrorKey(IOException("denied")))
    }

    @Test
    fun fallsBackToUnknownKey() {
        assertEquals("unknown", backupImportErrorKey(IllegalStateException("boom")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.backup.BackupImportErrorFormatterTest" --no-daemon --console=plain
```

Expected: FAIL with unresolved reference `backupImportErrorKey`.

- [ ] **Step 3: Write the minimal implementation**

Create the formatter:

```kotlin
package com.cslearningos.mobile.ui.backup

import org.json.JSONException
import java.io.IOException

fun backupImportErrorKey(error: Throwable): String =
    when (error) {
        is JSONException -> "invalid_json"
        is IOException -> "unreadable_file"
        else -> "unknown"
    }
```

Update `LearningViewModel.kt`:

```kotlin
fun showBackupError(error: Throwable) {
    val message = when (backupImportErrorKey(error)) {
        "invalid_json" -> uiText(R.string.message_backup_invalid_json)
        "unreadable_file" -> uiText(R.string.message_backup_unreadable_file)
        else -> error.message?.let(UiText::Dynamic) ?: uiText(R.string.message_restore_failed)
    }
    _state.update { it.copy(message = message) }
}
```

Update `MarkdownRenderer.kt` so rich text opens links:

```kotlin
val uriHandler = LocalUriHandler.current
val annotated = remember(inlines) { buildMarkdownAnnotatedText(inlines) }

ClickableText(
    text = annotated.text,
    style = style,
    modifier = modifier,
    onClick = { offset ->
        annotated.text
            .getStringAnnotations(MarkdownLinkAnnotationTag, offset, offset)
            .firstOrNull()
            ?.let { uriHandler.openUri(it.item) }
    }
)
```

Update `MoreScreen.kt` button copy to match the file flow:

```kotlin
ToolbarRow {
    WorkbenchButton(stringResource(R.string.more_backup_files), viewModel::showBackup, primary = true)
    WorkbenchButton(stringResource(R.string.more_import_backup_file), viewModel::showBackup)
    WorkbenchButton(stringResource(R.string.more_remove_demo), viewModel::clearStarterContent, danger = true)
}
```

- [ ] **Step 4: Run focused tests and compile checks**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.backup.BackupImportErrorFormatterTest" --tests "com.cslearningos.mobile.ui.markdown.MarkdownTextAnnotationsTest" --no-daemon --console=plain
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: PASS on both commands.

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/ui/backup/BackupImportErrorFormatter.kt android-app/app/src/test/java/com/cslearningos/mobile/ui/backup/BackupImportErrorFormatterTest.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/MarkdownRenderer.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt android-app/app/src/main/res/values/strings.xml android-app/app/src/main/res/values-zh/strings.xml
git commit -m "feat: finish markdown links and backup feedback"
```

## Task 6: Full Verification

**Files:**
- All files touched in Tasks 1-5

- [ ] **Step 1: Run full unit tests**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --no-daemon --console=plain
```

Expected: PASS with `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build the debug APK**

Run:

```powershell
cd android-app
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Expected: PASS with `BUILD SUCCESSFUL`.

- [ ] **Step 3: Validate the acceptance checklist**

Record the result explicitly:

```text
Primary task before explanation: PASS
Non-home compaction visible: PASS
Library reaches node content faster: PASS
Backup reads as file/share/import flow: PASS
Markdown links tappable: PASS
Markdown tables/lists steadier on mobile: PASS
LearningOsApp.kt reduced in scope: PASS
testDebugUnitTest: PASS
assembleDebug: PASS
```

- [ ] **Step 4: Commit**

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/ui android-app/app/src/main/res/values/strings.xml android-app/app/src/main/res/values-zh/strings.xml android-app/app/src/test/java/com/cslearningos/mobile/ui docs/superpowers/specs/2026-07-02-android-polish-pass-design.md docs/superpowers/plans/2026-07-02-android-polish-pass.md
git commit -m "feat: polish android compact screens and interactions"
```
