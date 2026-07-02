# Android Feature Modularization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the Android app into feature-first boundaries inside the existing `:app` module while reducing god files, moving side effects out of UI controllers, and adding an architecture verification harness.

**Architecture:** Keep one Android Gradle module, but split the app into `appshell`, `core`, and `feature/*` boundaries. Extract settings/AI/backup adapters first, then split repositories by feature, then split the giant `LearningViewModel` into feature-owned state/controllers and thin the app shell. Update the Android modularization spec and add a deterministic architecture verification harness before calling the work complete.

**Tech Stack:** Kotlin, Jetpack Compose, Android ViewModel, Room, SharedPreferences, PowerShell harness scripts, Gradle unit tests

---

## File Structure

### New files expected

- `android-app/app/src/main/java/com/cslearningos/mobile/appshell/navigation/AppRoute.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/appshell/state/AppShellState.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/appshell/state/AppShellViewModel.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/core/common/AndroidArchitectureConstants.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/data/SettingsPreferencesStore.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/data/AiDraftService.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/domain/ValidateAiSettingsUseCase.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/ui/SettingsUiState.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/ui/SettingsViewModel.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/backup/data/BackupRepository.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/backup/domain/ExportBackupUseCase.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/backup/domain/RestoreBackupUseCase.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/backup/ui/BackupUiState.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/backup/ui/BackupViewModel.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/data/LibraryRepository.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/domain/SaveNodeUseCase.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/domain/RestoreNodeFromTrashUseCase.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/ui/LibraryUiState.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/ui/LibraryViewModel.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/capture/data/CaptureRepository.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/capture/domain/GenerateCaptureDraftUseCase.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/capture/ui/CaptureUiState.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/capture/ui/CaptureViewModel.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/data/ReviewRepository.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/domain/AnswerQuizUseCase.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/ui/ReviewUiState.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/ui/ReviewViewModel.kt`
- `scripts/verify-android-architecture.ps1`

### Existing files expected to shrink or be replaced

- `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/BackupScreen.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt`
- `docs/android-workflow.md`
- `android-app/README.md`
- `scripts/verify-android-beta.ps1`
- `scripts/test-verify-android-beta.ps1`
- `docs/superpowers/specs/2026-07-02-android-feature-modularization-design.md`

### Existing tests expected to grow

- `android-app/app/src/test/java/com/cslearningos/mobile/ui/MoreSettingsModelsTest.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/ui/*`

### New tests expected

- `android-app/app/src/test/java/com/cslearningos/mobile/feature/settings/ValidateAiSettingsUseCaseTest.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/feature/backup/BackupViewModelTest.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/feature/library/LibraryViewModelTest.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/feature/capture/GenerateCaptureDraftUseCaseTest.kt`
- `android-app/app/src/test/java/com/cslearningos/mobile/feature/review/AnswerQuizUseCaseTest.kt`

## Task 1: Add architecture harness and shared constants

**Files:**
- Create: `scripts/verify-android-architecture.ps1`
- Modify: `scripts/verify-android-beta.ps1`
- Modify: `scripts/test-verify-android-beta.ps1`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/core/common/AndroidArchitectureConstants.kt`
- Modify: `docs/android-workflow.md`
- Modify: `android-app/README.md`

- [ ] **Step 1: Write the failing harness regression test**

```powershell
function Test-ArchitectureHarnessRequiresFeatureFolders {
    param(
        [string]$VerifyScript
    )

    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("verify-android-architecture-" + [System.Guid]::NewGuid().ToString("N"))

    try {
        New-TestProject -Root $root
        New-Item -ItemType Directory -Force -Path (Join-Path $root "android-app\app\src\main\java\com\cslearningos\mobile\feature\settings\ui") | Out-Null

        $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $VerifyScript -ProjectRoot $root 2>&1 | Out-String
        Assert-True ($LASTEXITCODE -ne 0) "Expected architecture harness to fail when required feature folders are missing."
        Assert-True ($output -match "feature structure") "Expected architecture harness to report missing feature structure."
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}
```

- [ ] **Step 2: Run the regression test to verify it fails**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-verify-android-beta.ps1`

Expected: FAIL because `verify-android-architecture.ps1` does not exist yet and the new regression check cannot pass.

- [ ] **Step 3: Create the shared Android architecture constants file**

```kotlin
package com.cslearningos.mobile.core.common

/**
 * Shared constants for Android architecture cleanup work.
 */
object AndroidArchitectureConstants {
    const val DueReviewRefreshIntervalMillis: Long = 60_000L
    const val AiConnectTimeoutMillis: Int = 15_000
    const val AiReadTimeoutMillis: Int = 45_000
    const val AppNoticeLimit: Int = 6
    const val AreaOrderStep: Int = 10
}
```

- [ ] **Step 4: Implement the architecture harness and wire it into beta verification**

```powershell
param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$requiredFeaturePaths = @(
    "android-app/app/src/main/java/com/cslearningos/mobile/appshell",
    "android-app/app/src/main/java/com/cslearningos/mobile/core",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/settings",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/backup",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/library",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/capture",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/review"
)

$missing = $requiredFeaturePaths | Where-Object {
    -not (Test-Path (Join-Path $ProjectRoot $_))
}

if ($missing.Count -gt 0) {
    Write-Error ("Android feature structure missing: " + ($missing -join ", "))
}

Write-Host "Android architecture verification passed."
```

```powershell
$architectureRun = Invoke-ExternalCommand -FilePath "powershell" -Arguments @(
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    (Join-Path $ProjectRoot "scripts\verify-android-architecture.ps1"),
    "-ProjectRoot",
    $ProjectRoot
) -WorkingDirectory $ProjectRoot

Add-Check -Name "android architecture harness" -Ok ($architectureRun.exitCode -eq 0) -Detail $architectureRun.output
```

- [ ] **Step 5: Run the harness tests and commit**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-verify-android-beta.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-android-architecture.ps1
```

Expected:

- `verify-android-beta regression tests passed.`
- `Android architecture verification passed.`

Commit:

```bash
git add scripts/verify-android-architecture.ps1 scripts/verify-android-beta.ps1 scripts/test-verify-android-beta.ps1 docs/android-workflow.md android-app/README.md android-app/app/src/main/java/com/cslearningos/mobile/core/common/AndroidArchitectureConstants.kt
git commit -m "chore: add android architecture harness"
```

## Task 2: Extract settings adapters and AI service from the app-wide controller

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/data/SettingsPreferencesStore.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/data/AiDraftService.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/domain/ValidateAiSettingsUseCase.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/ui/SettingsUiState.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/ui/SettingsViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/settings/ValidateAiSettingsUseCaseTest.kt`

- [ ] **Step 1: Write the failing settings validation test**

```kotlin
@Test
fun validateAiSettingsReportsMissingFieldsInOrder() {
    val useCase = ValidateAiSettingsUseCase()

    val result = useCase(
        provider = "",
        apiKey = "",
        baseUrl = "https://api.deepseek.com/v1",
        model = ""
    )

    assertEquals(listOf("provider", "apiKey", "model"), result.missingFields)
    assertFalse(result.isValid)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd android-app; .\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.feature.settings.ValidateAiSettingsUseCaseTest" --no-daemon --console=plain`

Expected: FAIL because `ValidateAiSettingsUseCase` and the feature package do not exist yet.

- [ ] **Step 3: Create the settings store, AI service shell, and feature use case**

```kotlin
package com.cslearningos.mobile.feature.settings.domain

data class AiSettingsValidationResult(
    val missingFields: List<String>
) {
    val isValid: Boolean
        get() = missingFields.isEmpty()
}

class ValidateAiSettingsUseCase {
    operator fun invoke(
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String
    ): AiSettingsValidationResult =
        AiSettingsValidationResult(
            missingFields = buildList {
                if (provider.isBlank()) add("provider")
                if (apiKey.isBlank()) add("apiKey")
                if (baseUrl.isBlank()) add("baseUrl")
                if (model.isBlank()) add("model")
            }
        )
}
```

```kotlin
package com.cslearningos.mobile.feature.settings.data

class SettingsPreferencesStore(
    private val aiPrefs: android.content.SharedPreferences,
    private val appPrefs: android.content.SharedPreferences
)
```

```kotlin
package com.cslearningos.mobile.feature.settings.data

interface AiDraftService {
    suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String>
    suspend fun requestDraft(baseUrl: String, apiKey: String, model: String, prompt: String): String
}
```

- [ ] **Step 4: Add the feature view-model and delegate settings behavior**

```kotlin
package com.cslearningos.mobile.feature.settings.ui

data class SettingsUiState(
    val provider: String = "DeepSeek",
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com/v1",
    val model: String = "deepseek-v4-flash",
    val thinkingEnabled: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val busy: Boolean = false
)
```

```kotlin
class SettingsViewModel(
    private val store: SettingsPreferencesStore,
    private val aiDraftService: AiDraftService,
    private val validateAiSettings: ValidateAiSettingsUseCase
) : ViewModel()
```

```kotlin
// In LearningViewModel.kt, replace inline validation/storage with delegation:
private val settingsViewModel = SettingsViewModel(
    store = SettingsPreferencesStore(aiPrefs, appPrefs),
    aiDraftService = realAiDraftService,
    validateAiSettings = ValidateAiSettingsUseCase()
)
```

- [ ] **Step 5: Run focused tests and commit**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.feature.settings.ValidateAiSettingsUseCaseTest" --no-daemon --console=plain
```

Expected: PASS

Commit:

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/feature/settings android-app/app/src/test/java/com/cslearningos/mobile/feature/settings android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt
git commit -m "refactor: extract android settings adapters"
```

## Task 3: Extract backup feature repository and view-model

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/backup/data/BackupRepository.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/backup/domain/ExportBackupUseCase.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/backup/domain/RestoreBackupUseCase.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/backup/ui/BackupUiState.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/backup/ui/BackupViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/BackupScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/backup/BackupViewModelTest.kt`

- [ ] **Step 1: Write the failing backup view-model test**

```kotlin
@Test
fun noteBackupSavedToDeviceEmitsSuccessMessage() = runTest {
    val repository = FakeBackupRepository()
    val viewModel = BackupViewModel(repository)

    viewModel.noteBackupSavedToDevice()

    assertEquals("backup_saved", viewModel.state.value.messageKey)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd android-app; .\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.feature.backup.BackupViewModelTest" --no-daemon --console=plain`

Expected: FAIL because `BackupViewModel` and `FakeBackupRepository` do not exist yet.

- [ ] **Step 3: Create backup repository and use cases**

```kotlin
package com.cslearningos.mobile.feature.backup.data

class BackupRepository(
    private val learningDao: com.cslearningos.mobile.data.LearningDao
)
```

```kotlin
package com.cslearningos.mobile.feature.backup.domain

class ExportBackupUseCase(
    private val repository: com.cslearningos.mobile.feature.backup.data.BackupRepository
)

class RestoreBackupUseCase(
    private val repository: com.cslearningos.mobile.feature.backup.data.BackupRepository
)
```

```kotlin
package com.cslearningos.mobile.feature.backup.ui

data class BackupUiState(
    val busy: Boolean = false,
    val messageKey: String? = null
)
```

- [ ] **Step 4: Add `BackupViewModel` and update `BackupScreen` to consume feature state**

```kotlin
class BackupViewModel(
    private val repository: BackupRepository
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state

    fun noteBackupSavedToDevice() {
        _state.update { it.copy(messageKey = "backup_saved") }
    }
}
```

```kotlin
@Composable
fun BackupScreen(
    state: BackupUiState,
    onShareBackup: () -> Unit,
    onSaveLocalBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    // Keep the existing UI, but replace direct LearningViewModel dependencies with feature actions.
}
```

- [ ] **Step 5: Run focused tests and commit**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.feature.backup.BackupViewModelTest" --no-daemon --console=plain
```

Expected: PASS

Commit:

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/feature/backup android-app/app/src/test/java/com/cslearningos/mobile/feature/backup android-app/app/src/main/java/com/cslearningos/mobile/ui/BackupScreen.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt
git commit -m "refactor: extract android backup feature"
```

## Task 4: Split library, capture, and review repositories from `LearningRepository`

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/data/LibraryRepository.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/domain/SaveNodeUseCase.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/domain/RestoreNodeFromTrashUseCase.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/capture/data/CaptureRepository.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/capture/domain/GenerateCaptureDraftUseCase.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/data/ReviewRepository.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/domain/AnswerQuizUseCase.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/capture/GenerateCaptureDraftUseCaseTest.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/review/AnswerQuizUseCaseTest.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt`

- [ ] **Step 1: Write the failing capture draft and review use-case tests**

```kotlin
@Test
fun generateCaptureDraftBuildsPromptFromSlipAndNodeTitles() {
    val service = FakeAiDraftService(response = "# Draft")
    val useCase = GenerateCaptureDraftUseCase(service)
    val settings = AiProviderSettings(
        provider = "DeepSeek",
        apiKey = "sk-test",
        baseUrl = "https://api.deepseek.com/v1",
        model = "deepseek-v4-flash"
    )
    val slip = CaptureSlipEntity(
        id = "slip-1",
        body = "TLB miss",
        type = CaptureSlipType.unclear,
        topicHint = null,
        sourceLabel = null,
        linkedNodeId = null,
        status = CaptureSlipStatus.ai_queued,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        revision = 1L,
        syncStatus = SyncStatus.clean,
        deletedAt = null
    )

    val draft = runTest {
        useCase(
            settings = settings,
            slip = slip,
            existingNodeTitles = listOf("Paging", "Virtual Memory")
        )
    }

    assertTrue(draft.contains("# Draft"))
}
```

```kotlin
@Test
fun answerQuizDelegatesRatingToReviewRepository() = runTest {
    val repository = FakeReviewRepository()
    val useCase = AnswerQuizUseCase(repository)

    useCase("quiz-1", ReviewRating.Good)

    assertEquals(listOf("quiz-1:Good"), repository.answers)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.feature.capture.GenerateCaptureDraftUseCaseTest" --tests "com.cslearningos.mobile.feature.review.AnswerQuizUseCaseTest" --no-daemon --console=plain
```

Expected: FAIL because the new feature repositories and use cases do not exist yet.

- [ ] **Step 3: Create the feature repositories and use-case shells**

```kotlin
package com.cslearningos.mobile.feature.library.data

class LibraryRepository(
    private val dao: com.cslearningos.mobile.data.LearningDao
)
```

```kotlin
package com.cslearningos.mobile.feature.capture.domain

class GenerateCaptureDraftUseCase(
    private val aiDraftService: com.cslearningos.mobile.feature.settings.data.AiDraftService
) {
    suspend operator fun invoke(
        settings: com.cslearningos.mobile.ui.AiProviderSettings,
        slip: com.cslearningos.mobile.data.CaptureSlipEntity,
        existingNodeTitles: List<String>
    ): String = aiDraftService.requestDraft(settings.baseUrl, settings.apiKey, settings.model, slip.body)
}
```

```kotlin
package com.cslearningos.mobile.feature.review.domain

class AnswerQuizUseCase(
    private val repository: com.cslearningos.mobile.feature.review.data.ReviewRepository
) {
    suspend operator fun invoke(quizId: String, rating: com.cslearningos.mobile.domain.ReviewRating) {
        repository.answerQuiz(quizId, rating)
    }
}
```

- [ ] **Step 4: Migrate `LearningRepository` behavior into feature repositories and keep compatibility tests green**

```kotlin
// Move node/area CRUD into LibraryRepository.
// Move capture-slip CRUD into CaptureRepository.
// Move answerQuiz/due queue logic into ReviewRepository.
// Leave a compatibility shim in LearningRepository only long enough to keep call sites compiling during the migration.
class LearningRepository(
    private val libraryRepository: LibraryRepository,
    private val captureRepository: CaptureRepository,
    private val reviewRepository: ReviewRepository
)
```

- [ ] **Step 5: Run focused tests and commit**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.feature.capture.GenerateCaptureDraftUseCaseTest" --tests "com.cslearningos.mobile.feature.review.AnswerQuizUseCaseTest" --tests "com.cslearningos.mobile.data.LearningRepositoryPolicyTest" --no-daemon --console=plain
```

Expected: PASS

Commit:

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/feature/library android-app/app/src/main/java/com/cslearningos/mobile/feature/capture android-app/app/src/main/java/com/cslearningos/mobile/feature/review android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt android-app/app/src/test/java/com/cslearningos/mobile/feature android-app/app/src/test/java/com/cslearningos/mobile/data/LearningRepositoryPolicyTest.kt
git commit -m "refactor: split android feature repositories"
```

## Task 5: Split feature view-models and UI state out of `LearningViewModel`

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/ui/LibraryUiState.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/library/ui/LibraryViewModel.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/capture/ui/CaptureUiState.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/capture/ui/CaptureViewModel.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/ui/ReviewUiState.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/review/ui/ReviewViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Test: `android-app/app/src/test/java/com/cslearningos/mobile/feature/library/LibraryViewModelTest.kt`

- [ ] **Step 1: Write the failing library view-model test**

```kotlin
@Test
fun openLibraryAreaSetsSelectedAreaAndClearsMessage() {
    val viewModel = LibraryViewModel(FakeLibraryRepository())

    viewModel.openLibraryArea("systems")

    assertEquals("systems", viewModel.state.value.selectedAreaId)
    assertNull(viewModel.state.value.messageKey)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd android-app; .\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.feature.library.LibraryViewModelTest" --no-daemon --console=plain`

Expected: FAIL because `LibraryViewModel` and `LibraryUiState` do not exist yet.

- [ ] **Step 3: Create feature-owned UI state and view-model shells**

```kotlin
package com.cslearningos.mobile.feature.library.ui

data class LibraryUiState(
    val areas: List<com.cslearningos.mobile.data.AreaEntity> = emptyList(),
    val nodes: List<com.cslearningos.mobile.data.LearningNodeEntity> = emptyList(),
    val selectedAreaId: String? = null,
    val checkedFilter: com.cslearningos.mobile.ui.LibraryCheckedFilter = com.cslearningos.mobile.ui.LibraryCheckedFilter.All,
    val messageKey: String? = null
)
```

```kotlin
class LibraryViewModel(
    private val repository: com.cslearningos.mobile.feature.library.data.LibraryRepository
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    fun openLibraryArea(areaId: String) {
        _state.update { it.copy(selectedAreaId = areaId, messageKey = null) }
    }
}
```

```kotlin
class CaptureViewModel(
    private val repository: com.cslearningos.mobile.feature.capture.data.CaptureRepository,
    private val generateCaptureDraft: com.cslearningos.mobile.feature.capture.domain.GenerateCaptureDraftUseCase
) : ViewModel()

class ReviewViewModel(
    private val repository: com.cslearningos.mobile.feature.review.data.ReviewRepository,
    private val answerQuiz: com.cslearningos.mobile.feature.review.domain.AnswerQuizUseCase
) : ViewModel()
```

- [ ] **Step 4: Shrink `LearningViewModel` into a compatibility shell**

```kotlin
// Remove feature-owned state from LearningUiState.
// Delegate existing UI events to LibraryViewModel, CaptureViewModel, ReviewViewModel, BackupViewModel, and SettingsViewModel.
class LearningViewModel(application: Application) : AndroidViewModel(application) {
    val library = LibraryViewModel(libraryRepository)
    val capture = CaptureViewModel(captureRepository, generateCaptureDraftUseCase)
    val review = ReviewViewModel(reviewRepository, answerQuizUseCase)
    val backup = BackupViewModel(backupRepository)
    val settings = SettingsViewModel(settingsStore, aiDraftService, validateAiSettingsUseCase)
}
```

- [ ] **Step 5: Run focused tests and commit**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.feature.library.LibraryViewModelTest" --no-daemon --console=plain
```

Expected: PASS

Commit:

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/feature/library/ui android-app/app/src/main/java/com/cslearningos/mobile/feature/capture/ui android-app/app/src/main/java/com/cslearningos/mobile/feature/review/ui android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt android-app/app/src/test/java/com/cslearningos/mobile/feature/library
git commit -m "refactor: split android feature viewmodels"
```

## Task 6: Thin the app shell and rewire screens to feature-scoped state

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/appshell/navigation/AppRoute.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/appshell/state/AppShellState.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/appshell/state/AppShellViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/CaptureScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardScreen.kt`

- [ ] **Step 1: Write the failing app-shell routing test**

```kotlin
@Test
fun selectedBottomTabMapsReaderAndEditorToLibrary() {
    assertEquals(AppRoute.Library, selectedBottomTabFor(AppRoute.Reader))
    assertEquals(AppRoute.Library, selectedBottomTabFor(AppRoute.Editor))
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd android-app; .\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.MobileNavigationModelsTest" --no-daemon --console=plain`

Expected: FAIL because `AppRoute` and `selectedBottomTabFor(AppRoute)` do not exist yet.

- [ ] **Step 3: Create app-shell route and state types**

```kotlin
package com.cslearningos.mobile.appshell.navigation

enum class AppRoute {
    Home,
    Capture,
    Library,
    Reader,
    Editor,
    Search,
    QuizEditor,
    Review,
    Backup,
    More
}
```

```kotlin
package com.cslearningos.mobile.appshell.state

data class AppShellState(
    val route: com.cslearningos.mobile.appshell.navigation.AppRoute = com.cslearningos.mobile.appshell.navigation.AppRoute.Home,
    val message: com.cslearningos.mobile.ui.UiText? = null
)
```

```kotlin
class AppShellViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppShellState())
    val state: StateFlow<AppShellState> = _state

    fun navigate(route: AppRoute) {
        _state.update { it.copy(route = route) }
    }
}
```

- [ ] **Step 4: Rewire `LearningOsApp` and screens to consume feature state**

```kotlin
@Composable
fun LearningOsApp(
    shellViewModel: AppShellViewModel = viewModel(),
    learningViewModel: LearningViewModel = viewModel()
) {
    val shellState by shellViewModel.state.collectAsStateWithLifecycle()
    val libraryState by learningViewModel.library.state.collectAsStateWithLifecycle()
    val captureState by learningViewModel.capture.state.collectAsStateWithLifecycle()
    val reviewState by learningViewModel.review.state.collectAsStateWithLifecycle()
    val backupState by learningViewModel.backup.state.collectAsStateWithLifecycle()
    val settingsState by learningViewModel.settings.state.collectAsStateWithLifecycle()
}
```

```kotlin
@Composable
fun LibraryScreen(
    state: com.cslearningos.mobile.feature.library.ui.LibraryUiState,
    onOpenArea: (String) -> Unit,
    onOpenNode: (String) -> Unit,
    onCreateArea: (String) -> Unit,
    onToggleCheckedFilter: (com.cslearningos.mobile.ui.LibraryCheckedFilter) -> Unit
) {
    // Move all node/area rendering to feature-scoped inputs:
    // - area rows come from state.areas
    // - node rows come from state.nodes
    // - actions call the passed lambdas rather than a shared LearningViewModel
}
```

- [ ] **Step 5: Run targeted tests and commit**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.MobileNavigationModelsTest" --no-daemon --console=plain
```

Expected: PASS

Commit:

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/appshell android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryScreen.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/CaptureScreen.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardScreen.kt
git commit -m "refactor: thin android app shell"
```

## Task 7: Remove legacy god-file behavior, update docs/spec, and run full verification

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt`
- Modify: `docs/superpowers/specs/2026-07-02-android-feature-modularization-design.md`
- Modify: `docs/android-workflow.md`
- Modify: `android-app/README.md`
- Modify: `scripts/verify-android-architecture.ps1`
- Modify: `scripts/verify-android-beta.ps1`

- [ ] **Step 1: Write the failing final architecture check**

```powershell
$legacyFiles = @(
    "android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt",
    "android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt"
)

$legacySizeViolations = $legacyFiles | Where-Object {
    (Get-Item (Join-Path $ProjectRoot $_)).Length -gt 30000
}

if ($legacySizeViolations.Count -gt 0) {
    Write-Error ("Legacy architecture files still too large: " + ($legacySizeViolations -join ", "))
}
```

- [ ] **Step 2: Run the architecture harness to verify it fails**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-android-architecture.ps1`

Expected: FAIL until the oversized legacy files have been reduced beneath the harness threshold.

- [ ] **Step 3: Finish cleanup and update the docs**

```markdown
| Android architecture verification | `.\scripts\verify-android-architecture.ps1` |
```

```kotlin
// Delete feature methods from LearningViewModel once all call sites use
// learningViewModel.library / capture / review / backup / settings directly.
// Delete migrated logic from LearningRepository once feature repositories own it.
```

- [ ] **Step 4: Run the full verification stack**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-android-architecture.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-android-beta.ps1 -SkipDoctor
cd android-app
.\gradlew.bat testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Expected:

- `Android architecture verification passed.`
- `verify-android-beta` returns success
- `BUILD SUCCESSFUL` for unit tests
- `BUILD SUCCESSFUL` for debug assemble

- [ ] **Step 5: Commit the final modularization pass**

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile docs/android-workflow.md android-app/README.md scripts/verify-android-architecture.ps1 scripts/verify-android-beta.ps1 docs/superpowers/specs/2026-07-02-android-feature-modularization-design.md docs/superpowers/plans/2026-07-02-android-feature-modularization.md
git commit -m "refactor: modularize android feature architecture"
```

## Self-Review Notes

- Spec coverage: app shell, feature boundaries, repository splitting, view-model splitting, constants cleanup, spec updates, and harness updates are each mapped to a task.
- Placeholder scan: no `TODO`, `TBD`, or "implement later" text remains in the plan steps.
- Type consistency: `AppRoute`, `AppShellState`, `SettingsUiState`, `BackupUiState`, `LibraryUiState`, `CaptureUiState`, `ReviewUiState`, and the feature use-case names are used consistently across the tasks.
