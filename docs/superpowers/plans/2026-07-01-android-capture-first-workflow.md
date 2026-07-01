# Android Capture-First Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the first capture-first Android workflow slice: fixed mobile bottom navigation, a More system screen, and durable Capture Slip inbox.

**Architecture:** Keep the current Room-backed local-first architecture. Add Capture Slip as a first-class data entity, expose it through repository/ViewModel state, and render it through dedicated Compose screens rather than growing `LearningOsApp.kt` further.

**Tech Stack:** Kotlin, Jetpack Compose, Room, JUnit local tests, existing Workbench visual components.

---

## Scope

This plan implements A1 and the first half of A2 from `docs/superpowers/specs/2026-07-01-android-mobile-learning-workflow-design.md`.

Included:

- Portrait bottom navigation: Home, Capture, Library, Review, More.
- More screen: Settings, AI Provider, Backup, Export, Import, Desktop Sync, Support entries.
- Capture Slip data model and Room migration.
- Capture Slip create, archive, observe, backup/restore.
- Capture screen with fast input and inbox.
- Home shows recent capture slips.

Deferred:

- OutlineBlock editor.
- Markdown/TXT export implementation.
- AI provider networking or API calls.
- Desktop sync transport.
- PDF generation.

## File Structure

- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningEntities.kt`
  - Add `CaptureSlipEntity`, `CaptureSlipType`, `CaptureSlipStatus`.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningDao.kt`
  - Add capture slip queries and upserts.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningDatabase.kt`
  - Bump schema from 2 to 3 and add migration creating `capture_slips`.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/BackupModels.kt`
  - Include capture slips in backup envelope.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/BackupCodec.kt`
  - Encode/decode capture slips.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt`
  - Add observe/create/archive capture slip behavior.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
  - Add `Capture` and `More` navigation, capture form state, and actions.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
  - Replace portrait top nav with bottom navigation and route Capture/More screens.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardModels.kt`
  - Add recent capture slip summary.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardScreen.kt`
  - Show quick capture and recent slips on Home.
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/CaptureScreen.kt`
  - Dedicated capture input and inbox UI.
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
  - Dedicated system tools UI.
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`
  - Add capture slip repository and backup tests.
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/DashboardModelsTest.kt`
  - Add dashboard recent capture assertions.
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/MobileWorkflowModelsTest.kt`
  - Test bottom tab model and More entries.

## Task 1: Capture Slip Data Contract

**Files:**

- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningEntities.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningDao.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningDatabase.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/BackupModels.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/BackupCodec.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`

- [ ] **Step 1: Write failing repository and backup tests**

Add tests:

```kotlin
@Test
fun saveCaptureSlipCreatesInboxSlip() = runTest {
    val dao = FakeLearningDao()
    val repository = LearningRepository(dao)

    val slip = repository.saveCaptureSlip(
        body = "I do not understand why TLB miss triggers a page table walk.",
        type = CaptureSlipType.unclear,
        topicHint = "Virtual Memory",
        sourceLabel = "video",
        now = 1_000L
    )

    assertEquals(CaptureSlipStatus.inbox, slip.status)
    assertEquals("Virtual Memory", slip.topicHint)
    assertEquals(slip, dao.captureSlips.getValue(slip.id))
}

@Test
fun archiveCaptureSlipKeepsDurableTombstone() = runTest {
    val dao = FakeLearningDao()
    val repository = LearningRepository(dao)
    val slip = repository.saveCaptureSlip("What is a page fault?", now = 1_000L)

    repository.archiveCaptureSlip(slip.id, now = 2_000L)

    assertEquals(CaptureSlipStatus.archived, dao.captureSlips.getValue(slip.id).status)
    assertEquals(2_000L, dao.captureSlips.getValue(slip.id).updatedAt)
}

@Test
fun backupRoundTripPreservesCaptureSlips() {
    val backup = LearningBackup(
        schemaVersion = BackupCodec.SchemaVersion,
        exportedAt = 1_000L,
        nodes = emptyList(),
        quizzes = emptyList(),
        reviewStates = emptyList(),
        attempts = emptyList(),
        readerQuestions = emptyList(),
        captureSlips = listOf(
            CaptureSlipEntity(
                id = "capture-1",
                body = "Why does page replacement matter?",
                type = CaptureSlipType.question,
                topicHint = "Virtual Memory",
                sourceLabel = "lecture",
                linkedNodeId = null,
                linkedOutlineBlockId = null,
                status = CaptureSlipStatus.inbox,
                createdAt = 1L,
                updatedAt = 1L,
                revision = 1L,
                syncStatus = SyncStatus.dirty,
                deletedAt = null
            )
        )
    )

    val decoded = BackupCodec.decode(BackupCodec.encode(backup))

    assertEquals(1, decoded.captureSlips.size)
    assertEquals("capture-1", decoded.captureSlips.single().id)
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.data.LearningRepositoryPolicyTest"
```

Expected: compile fails because capture slip types and repository methods do not exist.

- [ ] **Step 3: Implement data model**

Add:

```kotlin
enum class CaptureSlipType {
    unclear,
    mistake,
    video_note,
    concept_seed,
    question
}

enum class CaptureSlipStatus {
    inbox,
    linked,
    converted,
    archived
}

@Entity(tableName = "capture_slips")
data class CaptureSlipEntity(
    @PrimaryKey val id: String,
    val body: String,
    val type: CaptureSlipType,
    @ColumnInfo(name = "topic_hint") val topicHint: String?,
    @ColumnInfo(name = "source_label") val sourceLabel: String?,
    @ColumnInfo(name = "linked_node_id") val linkedNodeId: String?,
    @ColumnInfo(name = "linked_outline_block_id") val linkedOutlineBlockId: String?,
    val status: CaptureSlipStatus,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val revision: Long,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?
)
```

- [ ] **Step 4: Implement DAO/database/backup/repository support**

Add DAO methods:

```kotlin
@Query("SELECT * FROM capture_slips WHERE deleted_at IS NULL ORDER BY created_at DESC")
fun observeCaptureSlips(): Flow<List<CaptureSlipEntity>>

@Query("SELECT * FROM capture_slips")
suspend fun getAllCaptureSlips(): List<CaptureSlipEntity>

@Query("SELECT * FROM capture_slips WHERE id = :id LIMIT 1")
suspend fun getCaptureSlip(id: String): CaptureSlipEntity?

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertCaptureSlip(slip: CaptureSlipEntity)

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertCaptureSlips(slips: List<CaptureSlipEntity>)

@Query("DELETE FROM capture_slips")
suspend fun deleteAllCaptureSlips()
```

Add repository methods:

```kotlin
val captureSlips: Flow<List<CaptureSlipEntity>> = dao.observeCaptureSlips()

suspend fun saveCaptureSlip(
    body: String,
    type: CaptureSlipType = CaptureSlipType.unclear,
    topicHint: String? = null,
    sourceLabel: String? = null,
    now: Long = System.currentTimeMillis()
): CaptureSlipEntity

suspend fun archiveCaptureSlip(id: String, now: Long = System.currentTimeMillis())
```

- [ ] **Step 5: Run tests to verify GREEN**

Run the same command. Expected: all `LearningRepositoryPolicyTest` tests pass.

## Task 2: Mobile Workflow Models

**Files:**

- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MobileWorkflowModels.kt`
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/MobileWorkflowModelsTest.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardModels.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/DashboardModelsTest.kt`

- [ ] **Step 1: Write failing model tests**

Add:

```kotlin
@Test
fun bottomTabsMatchCaptureFirstWorkflow() {
    assertEquals(
        listOf("Home", "Capture", "Library", "Review", "More"),
        MobileBottomTab.entries.map { it.label }
    )
}

@Test
fun moreEntriesExposeSystemTools() {
    assertEquals(
        listOf("Settings", "AI Provider", "Backup", "Export", "Import", "Desktop Sync", "Support"),
        moreSystemEntries().map { it.title }
    )
}
```

Update dashboard tests to assert recent slips:

```kotlin
assertEquals("What is a page fault?", summary.recentCaptureSlips.single().body)
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.MobileWorkflowModelsTest" --tests "com.cslearningos.mobile.ui.DashboardModelsTest"
```

Expected: compile fails because `MobileBottomTab`, `moreSystemEntries`, and dashboard capture summary do not exist.

- [ ] **Step 3: Implement pure UI models**

Add:

```kotlin
enum class MobileBottomTab(val label: String) {
    Home("Home"),
    Capture("Capture"),
    Library("Library"),
    Review("Review"),
    More("More")
}

data class MoreSystemEntry(
    val title: String,
    val body: String
)

fun moreSystemEntries(): List<MoreSystemEntry> = listOf(
    MoreSystemEntry("Settings", "Local app preferences and privacy defaults."),
    MoreSystemEntry("AI Provider", "Optional user-managed provider configuration."),
    MoreSystemEntry("Backup", "Full JSON restore data for this app."),
    MoreSystemEntry("Export", "Readable Markdown/TXT study materials."),
    MoreSystemEntry("Import", "Bring learning packages into the local library."),
    MoreSystemEntry("Desktop Sync", "Future local computer transport, not a cloud requirement."),
    MoreSystemEntry("Support", "Diagnostics, product notes, and recovery guidance.")
)
```

Update `DashboardSummary` with:

```kotlin
val recentCaptureSlips: List<CaptureSlipEntity>
```

- [ ] **Step 4: Run tests to verify GREEN**

Run the same targeted UI model tests. Expected: pass.

## Task 3: ViewModel Capture And More State

**Files:**

- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/MobileWorkflowModelsTest.kt`

- [ ] **Step 1: Write failing tests for screen mapping helpers**

Add pure helper tests if direct ViewModel tests require Android runtime:

```kotlin
@Test
fun bottomTabMapsToAppScreens() {
    assertEquals(AppScreen.Home, MobileBottomTab.Home.targetScreen())
    assertEquals(AppScreen.Capture, MobileBottomTab.Capture.targetScreen())
    assertEquals(AppScreen.Library, MobileBottomTab.Library.targetScreen())
    assertEquals(AppScreen.Review, MobileBottomTab.Review.targetScreen())
    assertEquals(AppScreen.More, MobileBottomTab.More.targetScreen())
}
```

- [ ] **Step 2: Run test to verify RED**

Expected: compile fails because `AppScreen.Capture`, `AppScreen.More`, or `targetScreen` do not exist.

- [ ] **Step 3: Implement ViewModel state and actions**

Add to `AppScreen`:

```kotlin
Capture,
More
```

Add to `LearningUiState`:

```kotlin
val captureSlips: List<CaptureSlipEntity> = emptyList(),
val captureBody: String = "",
val captureTopicHint: String = "",
val captureSourceLabel: String = "",
val captureType: CaptureSlipType = CaptureSlipType.unclear
```

Add ViewModel actions:

```kotlin
fun showCapture()
fun showMore()
fun setCaptureBody(value: String)
fun setCaptureTopicHint(value: String)
fun setCaptureSourceLabel(value: String)
fun saveCaptureSlip()
fun archiveCaptureSlip(slip: CaptureSlipEntity)
```

- [ ] **Step 4: Run targeted tests**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.MobileWorkflowModelsTest"
```

Expected: pass.

## Task 4: Bottom Navigation And More UI

**Files:**

- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt`

- [ ] **Step 1: Add bottom navigation composables**

In portrait mode, render content above a bottom nav row:

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BrandBlock(state) }
        item { StatusBanner(state.message) }
        item { ScreenContent(state, viewModel, isDetailPane = true) }
    }
    MobileBottomNav(state = state, viewModel = viewModel)
}
```

`MobileBottomNav` uses custom workbench styling and 56dp minimum height per tab.

- [ ] **Step 2: Route More screen**

Update `ScreenContent`:

```kotlin
AppScreen.More -> MoreScreen(state, viewModel)
```

Create `MoreScreen` using `moreSystemEntries()`.

- [ ] **Step 3: Compile**

Run:

```powershell
cd android-app
.\gradlew.bat compileDebugKotlin
```

Expected: success.

## Task 5: Capture Screen And Home Integration

**Files:**

- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/CaptureScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`

- [ ] **Step 1: Route Capture screen**

Update `ScreenContent`:

```kotlin
AppScreen.Capture -> CaptureScreen(state, viewModel)
```

- [ ] **Step 2: Implement Capture screen**

Capture screen includes:

- Section header: "Capture slip".
- Body text field.
- Topic hint text field.
- Source label text field.
- Save button.
- Inbox list with archive buttons.

- [ ] **Step 3: Update Home**

Home should show:

- Quick capture button routing to Capture.
- Recent capture slips.
- Existing continue reading and library preview.

- [ ] **Step 4: Compile and targeted tests**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.DashboardModelsTest" --tests "com.cslearningos.mobile.ui.MobileWorkflowModelsTest"
```

Expected: pass.

## Task 6: Strict Verification

**Files:**

- No new files.

- [ ] **Step 1: Run full unit tests**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest
```

Expected: build successful.

- [ ] **Step 2: Build APK**

Run:

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

Expected: build successful and APK at `android-app/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Run Android doctor**

Run:

```powershell
.\scripts\android-doctor.ps1
```

Expected: all checks OK.

- [ ] **Step 4: Manual acceptance checklist**

Report:

- Bottom nav visible in portrait.
- Home is a command center, not a raw node feed.
- Capture slip can be saved in fewer than three taps from launch.
- Capture slip survives repository persistence and backup roundtrip tests.
- More screen exposes Settings, AI Provider, Backup, Export, Import, Desktop Sync, Support.
- JSON backup remains app restore data.
- Markdown/TXT export is visible as a system entry but not implemented in this slice.

## Self-Review

- Spec coverage: A1 and first half of A2 are covered; A3/A4/A5 are intentionally deferred.
- Red-flag scan: No task asks for an unspecified future implementation.
- Type consistency: `CaptureSlipEntity`, `CaptureSlipType`, `CaptureSlipStatus`, `MobileBottomTab`, and `MoreSystemEntry` are introduced before use.
- Risk: Existing Android worktree is dirty. Implementation must avoid reverting unrelated uncommitted changes and should commit only cohesive Android workflow files when verification passes.
