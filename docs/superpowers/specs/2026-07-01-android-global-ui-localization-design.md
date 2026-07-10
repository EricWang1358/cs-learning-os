# Android Global UI Localization Design

## Purpose

The Android app already exposes a language setting in `More`, but that setting only changes the `More` page copy. The rest of the native UI is still mostly hard-coded English in Compose files, while the learning content itself should remain untouched.

This design upgrades Android language handling from a local settings-only copy model to a real app-wide resource-backed localization layer. The goal is that switching `English / 中文 / Follow system` affects the app shell and major UI text, but does not translate user notes, quiz content, or AI drafts.

## Problem

The current implementation splits language behavior in an unsafe way:

- `SystemLanguage` is saved in app preferences.
- `More` uses hand-written English/Chinese copy in `MoreSettingsModels.kt`.
- major screens such as Home, Capture, Library, Reader, Search, Review, Backup, and shared components still contain hard-coded English strings.
- `strings.xml` currently contains only `app_name`.

That creates two product problems:

1. the language setting feels broken because it only affects one section
2. the codebase is heading toward duplicated bilingual copy models instead of using Android’s resource system

## Product Position

This is not a general content translation feature. It is app-shell localization.

The app should localize:

- navigation labels
- headers
- empty states
- system/help text
- settings text
- common action buttons
- transient app status/notices when they come from the app shell

The app should not localize:

- Markdown node bodies
- quiz prompt/answer/explanation content
- user-entered text
- AI-generated draft content
- taxonomy/content data that is intentionally user-authored

## Recommended Approach

Use Android resources as the source of truth:

- `android-app/app/src/main/res/values/strings.xml`
- `android-app/app/src/main/res/values-zh/strings.xml`

Add a small app-localization architecture layer instead of scattering locale logic across large Compose files:

1. `AppLocalization.kt`
   - maps `SystemLanguage` to an app locale override
   - provides a localized Compose `Context`
   - supports `Follow system` without duplicating copy models

2. `UiText.kt`
   - resource-backed UI text model for status messages and notices that originate in the ViewModel
   - allows `StatusBanner` and notification surfaces to resolve strings in the current locale at render time

3. screen/resource migration
   - move major hard-coded UI strings into resource files
   - replace literal English in Compose code with `stringResource(...)` or `UiText`

This gives us native resources, cleaner boundaries, and less “stone pile” code than expanding `MoreSettingsModels.kt` into a second app-wide copy system.

## Alternatives Considered

### Keep Hand-Written English/Chinese Copy Models

This would extend the current `MoreSettingsModels.kt` style across the whole app.

Trade-off: fast at first, but duplicates Android’s own localization system, spreads bilingual copy logic through Kotlin files, and makes large UI files even larger.

Decision: reject.

### Fully Switch To `strings.xml` Without A Small Architecture Layer

This would replace literals with `stringResource(...)` directly everywhere.

Trade-off: it uses native resources, but it still leaves dynamic ViewModel messages/notices as raw strings and tends to worsen already-large Compose files.

Decision: reject as the only structure.

### Resource-Backed Localization With A Small App Layer

This uses Android resource files as the source of truth, but adds minimal boundaries for locale override and ViewModel-originated UI messages.

Trade-off: slightly more setup now, but much cleaner ongoing structure.

Decision: use this path.

## Architecture

```text
res/values/strings.xml
res/values-zh/strings.xml
  -> source of localized UI strings

AppLocalization.kt
  -> maps SystemLanguage to app locale behavior
  -> provides localized Context to Compose

UiText.kt
  -> resource-backed transient UI messages/notices

screen composables
  -> use stringResource(...) for static UI
  -> resolve UiText for dynamic shell messages
```

## Scope

### In Scope

- true app-wide locale handling for static shell UI
- English and Chinese resource files
- `Follow system`, `English`, `中文` behavior
- navigation, headers, buttons, empty states, help text, settings text
- viewmodel-originated shell status/notices moved to resource-backed UI text where practical
- Chinese wording polish while migrating

### Out Of Scope

- automatic translation of node/quiz/user content
- server-side i18n
- importing translations from external tooling
- adding more languages than English and Chinese
- fully translating domain taxonomy content authored by users

## Structural Rules

- Do not keep expanding `MoreSettingsModels.kt` as a bilingual copy warehouse.
- Do not centralize all localized screen copy into one huge Kotlin file.
- Prefer Android resources for text, and small helper layers only where resources alone are awkward.
- If a file becomes unwieldy during migration, split by responsibility rather than app layer slogans.

## Migration Slices

### Slice 1: Locale And UI Text Foundation

- add `AppLocalization.kt`
- add `UiText.kt`
- keep `SystemLanguage` as the user-facing preference enum
- support localized rendering without changing node content

### Slice 2: Shared Component Localization

- `WorkbenchComponents.kt`
- common headers
- buttons
- empty states
- status banner

### Slice 3: Main Screen Localization

- `LearningOsApp.kt`
- `DashboardScreen.kt`
- `CaptureScreen.kt`
- `MoreScreen.kt`
- shared library labels where clearly app-owned

### Slice 4: Dynamic Message Cleanup

- migrate obvious ViewModel shell messages/notices to `UiText`
- keep user/content text raw

## Chinese Copy Direction

The Chinese should read like a polished product UI, not direct translation notes. Preferred tone:

- concise
- natural
- product-like
- avoids half-English half-Chinese phrasing unless a technical term is clearer in English

Examples of preferred direction:

- `Local-first contract` -> `本地优先原则`
- `Offline safe` -> `离线可用`
- `Task inbox` -> `任务消息`
- `Q to be solved` -> `待解决问题`

Exact phrasing can be refined during implementation, but the bar is “natural product Chinese,” not literal mirroring.

## Acceptance Criteria

- Switching `English / 中文 / Follow system` changes the major app-shell UI, not just `More`.
- Home, Capture, Library, Reader, Search, Review, Backup, and More have localized static UI text.
- Node Markdown content, quiz content, user input, and AI draft content remain unchanged.
- Dynamic shell status/notices introduced by the app use the selected language where migrated.
- English remains the fallback/default copy.
- `testDebugUnitTest` and `assembleDebug` pass after the migration slice.

## Risks

- Migrating too many screens at once can balloon the diff; keep the slice focused on app-shell text first.
- ViewModel message localization can become messy if raw strings and resource strings mix carelessly.
- Directly shoving every string migration into `LearningOsApp.kt` would worsen the existing file size problem.

## Decision

Proceed with a resource-backed Android localization foundation plus a small app-localization layer. Use it to expand the current language setting from `More`-only behavior into app-wide shell localization, while preserving user learning content exactly as written.
