# Android Global UI Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the Android language setting into a real app-wide shell localization system using native Android resources, without translating user learning content.

**Architecture:** Keep `SystemLanguage` as the user-facing setting, but stop using Kotlin-only bilingual copy as the app-wide mechanism. Add a small locale/app-text foundation, move shell UI text into `strings.xml` / `values-zh/strings.xml`, and migrate the main screens plus shared components to resource-backed text while leaving node/quiz/user content untouched.

**Tech Stack:** Kotlin, Jetpack Compose, Android `stringResource`, Android `Context`/locale override, JUnit local unit tests, existing Android Gradle app.

---

## Acceptance Checklist

- [ ] `English / 中文 / Follow system` changes the major shell UI, not only `More`.
- [ ] Home, Capture, Library, Reader, Search, Review, Backup, and More use localized static UI text.
- [ ] Learning content itself is not translated.
- [ ] Dynamic shell messages/notices introduced by the app are localized where migrated.
- [ ] Existing Android unit tests still pass.
- [ ] `assembleDebug` still passes.
- [ ] The localization structure reduces future stone-pile code rather than adding a giant bilingual Kotlin file.

## File Structure

- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/AppLocalization.kt`
  - Locale override and localized Compose context helpers.
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/UiText.kt`
  - Resource-backed transient UI message model.
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/AppLocalizationTest.kt`
  - Tests for language-to-locale mapping and fallback behavior.
- Modify: `android-app/app/src/main/res/values/strings.xml`
  - English shell UI strings.
- Create: `android-app/app/src/main/res/values-zh/strings.xml`
  - Chinese shell UI strings.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreSettingsModels.kt`
  - Remove app-wide bilingual copy responsibility; keep only small setting/domain helpers that still belong here.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
  - Switch to resources for settings UI text.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt`
  - Resource-aware status/empty-state helpers where needed.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`
  - Apply localized context and migrate top-level shell strings.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardScreen.kt`
  - Migrate dashboard shell strings.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/CaptureScreen.kt`
  - Migrate capture shell strings.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryModels.kt`
  - Localize app-owned area/track labels where appropriate.
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
  - Replace shell-originated raw strings with resource-backed `UiText` where necessary.
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/MoreSettingsModelsTest.kt`
  - Update tests away from hard-coded bilingual copy assumptions.

## Task 1: Locale Foundation Tests First

**Files:**
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/ui/AppLocalizationTest.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/MoreSettingsModelsTest.kt`
- Create in Task 2: `android-app/app/src/main/java/com/cslearningos/mobile/ui/AppLocalization.kt`

- [ ] **Step 1: Write failing locale tests**

Add tests that prove:

- `SystemLanguage.English` maps to English app locale
- `SystemLanguage.Chinese` maps to Chinese app locale
- `FollowSystem` resolves `zh-*` to Chinese and non-`zh` to English
- the old `MoreSettingsModels` tests no longer require app-wide bilingual copy objects

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.AppLocalizationTest" --tests "com.cslearningos.mobile.ui.MoreSettingsModelsTest"
```

Expected: FAIL because `AppLocalization.kt` and its helpers do not exist yet, and tests still reflect the old copy shape.

## Task 2: Implement Locale Foundation

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/AppLocalization.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreSettingsModels.kt`

- [ ] **Step 1: Add locale mapping helpers**

Implement:

- a function that maps `SystemLanguage` plus system locale tag to the effective app language
- a small localized-context helper for Compose/app use

Keep `SystemLanguage` in `MoreSettingsModels.kt`, but move actual app-localization logic into `AppLocalization.kt`.

- [ ] **Step 2: Trim `MoreSettingsModels.kt` responsibility**

Keep only setting-domain helpers that still belong there. Do not let it remain the app-wide bilingual text source.

- [ ] **Step 3: Verify GREEN**

Run the same targeted tests and expect success.

## Task 3: Resource Files And Shared UI Text

**Files:**
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Create: `android-app/app/src/main/res/values-zh/strings.xml`
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/ui/UiText.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt`

- [ ] **Step 1: Add English and Chinese string resources**

Create resource keys for shared shell text:

- nav labels
- common buttons
- section headers
- empty-state labels
- common notes/help text

- [ ] **Step 2: Add `UiText` for transient shell messages**

Implement a tiny resource-backed text model for status/notices from ViewModel, such as:

- string resource id
- optional format args
- resolver function from `Context`

- [ ] **Step 3: Make shared components resource-friendly**

Update shared components only where needed so they can consume `UiText` or resource strings without bloating the file.

- [ ] **Step 4: Run targeted compile/test**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest --tests "com.cslearningos.mobile.ui.AppLocalizationTest" --tests "com.cslearningos.mobile.ui.MoreSettingsModelsTest"
```

Expected: PASS.

## Task 4: Localize More And Top-Level Shell

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MoreScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`

- [ ] **Step 1: Replace `More` hard-coded copy with resources**

Migrate `More` UI labels, notes, and section text to `stringResource(...)`.

- [ ] **Step 2: Apply localized context at the app shell**

Make the selected `SystemLanguage` affect the app shell’s resource resolution.

- [ ] **Step 3: Migrate top-level shell strings**

Replace hard-coded shell text in `LearningOsApp.kt` with resource-backed strings without making the file even more monolithic.

- [ ] **Step 4: Verify compile**

Run:

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

Expected: PASS.

## Task 5: Localize Main Screens

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/DashboardScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/CaptureScreen.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LibraryModels.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningOsApp.kt`

- [ ] **Step 1: Migrate Dashboard shell strings**

Localize dashboard headings, action text, helper text, metrics, and library preview labels.

- [ ] **Step 2: Migrate Capture shell strings**

Localize capture headers, prompts, action labels, preflight helper copy, inbox copy, and workflow labels.

- [ ] **Step 3: Localize app-owned library labels**

Localize area/track labels that belong to the app shell rather than user-authored content.

- [ ] **Step 4: Keep user content raw**

Do not translate:

- node titles/body text
- quiz text
- user-entered slip content
- AI draft content

- [ ] **Step 5: Run targeted tests/build**

Run:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest
```

Expected: PASS.

## Task 6: Localize Dynamic Shell Messages

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/WorkbenchComponents.kt`

- [ ] **Step 1: Identify shell-only transient strings**

Migrate app-originated shell messages such as:

- save confirmations
- validation prompts
- restore/export status
- AI setup shell status where it is clearly app-owned

- [ ] **Step 2: Keep content-dependent messages raw where needed**

Do not force-fit resource localization onto user content that should remain literal.

- [ ] **Step 3: Verify no shell-only English islands remain in touched flows**

Spot-check Home, Capture, More, Search, Review, Backup, and Reader shell surfaces.

## Task 7: Strict Final Verification

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

- [ ] **Step 3: Final checklist**

Report each acceptance checklist item as pass/fail/untested, with explicit note that user learning content remains untranslated.

- [ ] **Step 4: Commit**

```bash
git add android-app/app/src/main/java/com/cslearningos/mobile/ui android-app/app/src/main/res/values/strings.xml android-app/app/src/main/res/values-zh/strings.xml android-app/app/src/test/java/com/cslearningos/mobile/ui docs/superpowers/specs/2026-07-01-android-global-ui-localization-design.md docs/superpowers/plans/2026-07-01-android-global-ui-localization.md
git commit -m "feat: localize android shell UI"
```
