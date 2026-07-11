# Android Home And Review Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Home and Review to match the approved reference hierarchy while preserving free-form CS content, Area scoping, AI editing, and existing review scheduling.

**Architecture:** Home metrics move into the existing portrait `BrandBlock`, eliminating the duplicate Dashboard title. Review presentation moves into focused composables for Area selection, prompt recall, concise-answer rating, and post-rating explanation, while `LearningViewModel` continues to own session state and repository writes.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4, Android Gradle Plugin.

---

### Task 1: Lock Presentation Policy In Models

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardModels.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/ReviewQueueModels.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/DashboardModelsTest.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/ReviewQueueModelsTest.kt`

- [ ] Add failing tests that Dashboard content exposes only Capture, Create, Search after metrics move to BrandBlock, and that Review maps state to Setup, Prompt, Rating, or Explanation without showing multiple layers together.
- [ ] Run `./gradlew testDebugUnitTest --tests '*DashboardModelsTest' --tests '*ReviewQueueModelsTest'` and confirm failure from missing presentation policy.
- [ ] Implement `DashboardSummary.compactActions` and `ReviewStage`/`reviewStage(...)` with the minimum logic needed by the tests.
- [ ] Re-run the targeted tests and confirm success.

### Task 2: Rebuild Home Hierarchy

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardScreen.kt`

- [ ] Put two stacked compact metric actions to the right of the existing multiline brand title on Home only; their combined height aligns with the title block.
- [ ] Remove the duplicate Dashboard title and render Capture, Create, Search as one restrained command row above Continue Reading.
- [ ] Compile with `./gradlew compileDebugKotlin` and confirm success.

### Task 3: Rebuild Review Screens

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/values-zh/strings.xml`

- [ ] Render Area setup as a clean selection surface with due/total counts and an explicit Start review command.
- [ ] Render Prompt as progress plus one free-form question, optional Hint, and a user-triggered concise-answer reveal; do not show the answer on entry.
- [ ] Render Rating with only the concise answer, AI/manual edit menu, and Again/Hard/Good.
- [ ] Render Explanation only after rating, show the full explanation, then Retry/Previous/Next; Previous/Next remain within the chosen Area and return to Prompt.
- [ ] Preserve long-text wrapping and scrolling above the bottom navigation.

### Task 4: Verify And Release

**Files:**
- Modify: `android-app/app/build.gradle`

- [ ] Run `./gradlew testDebugUnitTest assembleDebug` and the Android architecture verification script.
- [ ] Increment versionCode and patch versionName, rebuild, and commit the release.
- [ ] Confirm APK at `android-app/app/build/outputs/apk/debug/app-debug.apk`.
- [ ] Record that screenshot verification is blocked when `emulator -list-avds` and `adb devices` are empty.
