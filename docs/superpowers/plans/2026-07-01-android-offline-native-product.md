# Android Offline Native Product Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Android WebView shell with a native offline learning product that supports local Markdown nodes, search, quiz, review, and backup.

**Architecture:** Use the existing `android-app` Gradle project. Kotlin/Compose owns UI, Room/SQLite owns local durable state, and domain classes own deterministic Markdown quiz parsing, review scheduling, and backup serialization.

**Tech Stack:** Android Gradle Plugin 8.7.3, Kotlin, Jetpack Compose, Room, KSP, SQLite FTS, JUnit.

---

## File Structure

- Modify `android-app/build.gradle`: add Kotlin and KSP plugins.
- Modify `android-app/app/build.gradle`: enable Kotlin, Compose, Room, JUnit, and Java 17.
- Delete `android-app/app/src/main/java/com/cslearningos/mobile/MainActivity.java`: remove WebView product entry.
- Create `android-app/app/src/main/java/com/cslearningos/mobile/MainActivity.kt`: Compose entry point.
- Create `android-app/app/src/main/java/com/cslearningos/mobile/data/*`: Room entities, DAOs, database, repository, backup codec.
- Create `android-app/app/src/main/java/com/cslearningos/mobile/domain/*`: review scheduler and Markdown quiz parser.
- Create `android-app/app/src/main/java/com/cslearningos/mobile/ui/*`: ViewModel, navigation state, Compose screens, Markdown renderer.
- Create `android-app/app/src/test/java/com/cslearningos/mobile/domain/*`: pure JVM tests for parser and scheduler.
- Modify `android-app/app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`: prevent accidental automatic backup of local learning data.

## Task 1: Build System And Native Entry

**Files:**

- Modify: `android-app/build.gradle`
- Modify: `android-app/app/build.gradle`
- Delete: `android-app/app/src/main/java/com/cslearningos/mobile/MainActivity.java`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/MainActivity.kt`

- [ ] **Step 1: Add Kotlin, Compose, and KSP plugins**

Use Kotlin `2.0.21`, Compose compiler plugin `2.0.21`, and KSP `2.0.21-1.0.25` in the root Gradle file.

- [ ] **Step 2: Add app dependencies**

Add Compose BOM, Activity Compose, Lifecycle ViewModel Compose, Navigation Compose, Room runtime/ktx/compiler, and JUnit.

- [ ] **Step 3: Replace WebView activity with Compose**

`MainActivity.kt` should call `setContent { LearningOsApp() }`.

- [ ] **Step 4: Verify build**

Run:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd android-app
.\gradlew.bat assembleDebug
```

Expected: build succeeds after `LearningOsApp` exists as a minimal placeholder Composable in this task.

## Task 2: Domain Tests First

**Files:**

- Create: `android-app/app/src/test/java/com/cslearningos/mobile/domain/ReviewSchedulerTest.kt`
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/domain/MarkdownQuizParserTest.kt`
- Create in Task 3: `android-app/app/src/main/java/com/cslearningos/mobile/domain/ReviewScheduler.kt`
- Create in Task 3: `android-app/app/src/main/java/com/cslearningos/mobile/domain/MarkdownQuizParser.kt`

- [ ] **Step 1: Write failing scheduler tests**

Test that `again` schedules today, `hard` schedules one day out, and `good` grows interval/ease.

- [ ] **Step 2: Write failing parser tests**

Test that a `:::quiz` block produces prompt, answer, explanation, and stable source anchor.

- [ ] **Step 3: Run tests and confirm RED**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.domain.*"
```

Expected: tests fail because production classes do not exist.

## Task 3: Domain Implementation

**Files:**

- Create: `android-app/app/src/main/java/com/cslearningos/mobile/domain/ReviewScheduler.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/domain/MarkdownQuizParser.kt`

- [ ] **Step 1: Implement review scheduler**

Create `ReviewRating`, `ReviewScheduleInput`, `ReviewScheduleResult`, and `ReviewScheduler.next(...)`.

- [ ] **Step 2: Implement Markdown quiz parser**

Parse deterministic fenced blocks:

```markdown
:::quiz id=anchor
question: ...
answer: ...
explanation: ...
:::
```

- [ ] **Step 3: Run domain tests and confirm GREEN**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.domain.*"
```

Expected: domain tests pass.

## Task 4: Room Data Core

**Files:**

- Create: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningEntities.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningDao.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningDatabase.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt`

- [ ] **Step 1: Create sync-ready entities**

Entities: `LearningNodeEntity`, `QuizItemEntity`, `ReviewStateEntity`, `ReviewAttemptEntity`, `NodeFtsEntity`, and `QuizFtsEntity`.

- [ ] **Step 2: Create DAO**

DAO must support node CRUD, quiz CRUD, review queue, attempts, FTS upsert/delete/search, and transaction helpers.

- [ ] **Step 3: Create repository**

Repository must expose Flows and suspend methods for node save/read/search, manual quiz save, Markdown quiz sync, review answer, export, and restore.

- [ ] **Step 4: Verify build**

Run:

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

Expected: Room code generation succeeds.

## Task 5: Backup Codec

**Files:**

- Create: `android-app/app/src/main/java/com/cslearningos/mobile/data/BackupModels.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/data/BackupCodec.kt`

- [ ] **Step 1: Add backup models**

Backup contains schema version, export timestamp, nodes, quizzes, review states, and attempts.

- [ ] **Step 2: Add JSON export/import**

Use Android `org.json` to avoid adding another serialization dependency.

- [ ] **Step 3: Integrate with repository restore**

Restore validates first, overwrites transactionally, and rebuilds FTS.

## Task 6: Compose UI Loop

**Files:**

- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MarkdownRenderer.kt`

- [ ] **Step 1: Add ViewModel state**

State includes nodes, selected node, editor draft, search query/results, quiz editor state, due review cards, current screen, and backup text.

- [ ] **Step 2: Add Compose screens**

Screens: home, reader, editor, search, quiz editor, review, settings backup/restore.

- [ ] **Step 3: Add Markdown rendering**

Render headings, lists, block quotes, inline code markers as readable Compose text blocks.

- [ ] **Step 4: Verify build**

Run:

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

Expected: debug APK builds.

## Task 7: Privacy And Offline Defaults

**Files:**

- Modify: `android-app/app/src/main/AndroidManifest.xml`
- Modify: `android-app/app/src/main/res/xml/backup_rules.xml`
- Modify: `android-app/app/src/main/res/xml/data_extraction_rules.xml`

- [ ] **Step 1: Remove WebView/network product assumptions**

Remove broad cleartext/development network config from the product manifest unless still required by build-only legacy files.

- [ ] **Step 2: Disable accidental local data backup**

Set backup rules so Room databases and local preferences are excluded until an explicit backup policy exists.

## Task 8: Final Verification

**Files:**

- Verify only.

- [ ] **Step 1: Run doctor**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\scripts\android-doctor.ps1
```

- [ ] **Step 2: Run unit tests**

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest
```

- [ ] **Step 3: Run Android build**

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

- [ ] **Step 4: Commit**

```powershell
git add android-app docs/superpowers/plans/2026-07-01-android-offline-native-product.md
git commit -m "feat: build offline native android product"
```

## Self-Review

- Spec coverage: this plan covers native UI, Room local state, Markdown edit/read, FTS search, quiz manual/Markdown sources, review scheduling, backup/restore, privacy defaults, and no backend dependency.
- Placeholder scan: no `TBD`, `TODO`, or open-ended implementation slots are left.
- Scope note: this is a large implementation. If time runs short, preserve buildability after each task and commit the completed slice rather than leaving the APK broken.
