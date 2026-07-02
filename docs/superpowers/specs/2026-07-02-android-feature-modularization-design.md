# Android Feature Modularization Design

## Purpose

Refactor the Android app from a small-but-growing stone-pile implementation into a feature-modular structure that is easier to understand, test, and extend.

The goal is not to split Gradle modules yet. The goal is to create real feature boundaries inside the existing `:app` module so future Android work can stay decoupled and consistent with the project's local-first product rules.

This pass must also raise the baseline code standard:

- reduce or remove god files
- move side effects out of UI state controllers
- remove repeated logic and dead code
- replace meaningful magic numbers with named constants
- keep naming, comments, headers, and formatting consistent

## Confirmed Direction

- Use feature-first modularization, not a pure layer-only reorganization
- Keep a single Android Gradle module for now
- Replace the overloaded app-wide `LearningViewModel` pattern with a light app shell plus feature-owned state and actions
- Break the catch-all `LearningRepository` into feature repositories, gateways, and use cases
- Update the corresponding architecture spec and verification harness as part of this work

## Problems To Solve

### Architecture Pressure

`LearningViewModel.kt` currently owns navigation, feature state, AI requests, backup actions, settings persistence, global notices, and many screen-specific workflows.

`LearningRepository.kt` currently owns node editing, area management, review flows, capture flows, backup serialization, and restore logic in one class.

This makes the Android app harder to reason about and encourages new work to pile onto the same files.

### Weak Feature Boundaries

The current package structure still centers around broad `ui` and `data` buckets. Several screens depend on the same app-wide state object and controller, even when they only need one narrow slice of behavior.

This increases coupling between unrelated product areas such as:

- library management
- capture and AI drafting
- review scheduling
- backup and restore
- settings and AI provider configuration

### Style Drift

The Android codebase already has better structure than the first migration pass, but several issues still need a formal cleanup target:

- meaningful numeric values still live inline
- constants are inconsistent across files
- some file and function responsibilities are too broad
- naming and comments are not yet governed by one explicit Android architecture standard

## Recommended Approach

Implement the refactor in four coordinated tracks.

### Track 1: App Shell Extraction

Reduce the app-wide controller layer to a thin shell that owns only:

- navigation
- top-level screen routing
- app-wide message delivery
- shared dependency assembly

The shell should not own every feature's full UI state.

### Track 2: Feature-Owned State And Actions

Create explicit feature domains inside `:app`:

- `library`
- `capture`
- `review`
- `backup`
- `settings`

Each feature should own:

- its UI state
- its state transitions or actions
- its use cases
- its feature-specific repository interfaces

### Track 3: Data And Side-Effect Decoupling

Move IO and framework side effects away from screen controllers:

- Room access stays behind repositories or gateways
- SharedPreferences access moves into settings-oriented adapters
- OpenAI-compatible HTTP logic moves into a dedicated AI service
- backup encoding and restore workflows move into backup-specific data/domain units

### Track 4: Architecture Standardization

Use this pass to formalize Android coding rules that future slices must follow:

- no meaningful magic numbers inline
- shared constants live in named constant files close to their feature
- file and function responsibilities stay narrow
- comments explain non-obvious intent or constraints
- dead code and debug prints are removed before handoff

## Target Package Structure

Inside `android-app/app/src/main/java/com/cslearningos/mobile/`, move toward this structure:

```text
appshell/
  navigation/
  state/
  ui/

core/
  common/
  data/
  model/
  ui/

feature/
  backup/
    data/
    domain/
    ui/
  capture/
    data/
    domain/
    ui/
  library/
    data/
    domain/
    ui/
  review/
    data/
    domain/
    ui/
  settings/
    data/
    domain/
    ui/
```

This is a logical architecture target, not a demand to move every Android file in one giant rename commit. Migration can happen in slices as long as the dependency direction stays consistent.

## Dependency Rules

The refactor should enforce these rules:

### App Shell

`appshell` may depend on feature entry points, route models, and shared UI infrastructure. It must not reach into feature-internal repositories or raw Room operations.

### Feature UI

`feature/*/ui` may depend on:

- feature state
- feature actions or view-models
- `core/ui`
- light shared model types

It must not directly call:

- DAO methods
- SharedPreferences
- HTTP clients
- unrelated feature repositories

### Feature Domain

`feature/*/domain` holds use cases and business rules. It should remain deterministic and as Android-free as practical.

### Feature Data

`feature/*/data` adapts Room, prefs, codecs, and network services into interfaces that the feature domain can use.

### Core

`core/data` is allowed to host shared low-level implementations such as Room database setup, shared codecs, and infrastructure adapters, but it must not become a second catch-all business layer.

## ViewModel Decomposition

Replace the current all-in-one app controller with:

- `AppShellViewModel`
- `LibraryViewModel`
- `CaptureViewModel`
- `ReviewViewModel`
- `BackupViewModel`
- `SettingsViewModel`

### AppShellViewModel

Owns:

- current route or screen
- cross-feature navigation requests
- app-wide message channel or notice surface
- wiring between feature entry points

Does not own:

- full library state
- full review queue state
- full capture drafting state
- direct AI HTTP logic
- direct backup import/export logic

### LibraryViewModel

Owns:

- areas
- active node lists
- selected area
- selected node or reader target
- checked filter
- area management dialogs or commands

### CaptureViewModel

Owns:

- capture draft fields
- AI preflight state
- capture notices
- queue or draft-generation status

### ReviewViewModel

Owns:

- due review list
- selected quiz
- answer visibility
- rating actions

### BackupViewModel

Owns:

- backup/export/import action state
- restore error presentation
- file-share status

### SettingsViewModel

Owns:

- language settings
- appearance settings
- AI provider settings
- provider validation and model-list pull status

## UI State Decomposition

Replace the single `LearningUiState` pattern with feature-scoped state:

- `AppShellState`
- `LibraryUiState`
- `CaptureUiState`
- `ReviewUiState`
- `BackupUiState`
- `SettingsUiState`

Screens should accept only the state and actions they actually need. A screen should not receive a giant app-wide state object just because it is convenient.

## Repository And Service Decomposition

Retire `LearningRepository` as the main all-purpose business class. Replace it with smaller feature-aligned units.

### Repositories

- `LibraryRepository`
- `CaptureRepository`
- `ReviewRepository`
- `BackupRepository`
- `SettingsRepository`

### Services Or Gateways

- `AiDraftService`
- `StarterContentService`
- small persistence gateways where needed

### Use Cases

Introduce narrow use cases for multi-step flows such as:

- `SaveNodeUseCase`
- `RestoreNodeFromTrashUseCase`
- `GenerateCaptureDraftUseCase`
- `ValidateAiSettingsUseCase`
- `ExportBackupUseCase`
- `RestoreBackupUseCase`
- `AnswerQuizUseCase`

These use cases should encapsulate cross-repository workflows so feature controllers do not rebuild orchestration logic inline.

## Code Standard Rules

This refactor is also the Android code standard pass. The following rules are in scope and should be enforced in touched files.

### Constants And Magic Numbers

Meaningful numeric values must move to named constants. Examples include:

- AI connect timeout
- AI read timeout
- due review refresh interval
- area ordering step
- notice retention count
- shared spacing, alpha, and radius values repeated across screens

Values that only express the nature of a trivial operation, such as incrementing by one or boolean-like branch counts, do not need forced constant extraction.

### Naming

- Kotlin code uses `camelCase` for values and functions
- Kotlin types use `PascalCase`
- file names match their primary purpose
- abbreviations should be rare and obvious
- names should be descriptive without becoming essay-length

### Comments And Headers

Add file and function headers to new core architecture files where they clarify ownership and responsibility.

Use comments for:

- non-obvious constraints
- tricky transformation logic
- adapter boundaries
- compatibility rules

Do not add filler comments that merely restate the code.

### Cleanup

- remove dead code
- remove stray debug prints
- remove duplicated logic that should be a helper or use case
- avoid replacing one giant file with many giant files

## Migration Strategy

This should happen in controlled slices, not as one massive rename-only rewrite.

### Phase 1: Extract External Adapters

First extract the most obvious side-effect boundaries:

- AI settings persistence
- AI HTTP service
- backup export/import service layer

This lowers risk in `LearningViewModel` quickly without changing every screen at once.

### Phase 2: Split Feature Repositories

Split the business-heavy `LearningRepository` into feature-specific repositories and use cases while preserving current behavior.

### Phase 3: Split Feature ViewModels And States

Move feature state ownership out of `LearningViewModel` into feature-specific controllers and state classes.

### Phase 4: Thin App Shell

Shrink `LearningOsApp.kt` and app-level wiring so the shell becomes a composition layer rather than a screen-logic bucket.

## Spec And Harness Updates

This refactor must not ship as code-only cleanup. The corresponding design and verification assets must move with it.

### Spec Requirements

- add this new Android modularization design spec
- update any directly affected Android architecture or polish specs if their described ownership model changes materially
- keep the implementation plan aligned with this design's dependency rules and phased migration order

### Harness Requirements

Add or extend an Android architecture verification harness so the new boundaries remain testable.

The first practical version should verify at least:

- required Android unit tests still pass
- `assembleDebug` still passes
- the feature package structure exists for the migrated slices
- the legacy god-file pattern does not continue growing unchecked
- architecture verification can run from a repo-level PowerShell entry point

The most practical first harness shape is:

- new `scripts/verify-android-architecture.ps1`
- optional integration from `scripts/verify-android-beta.ps1` once stable

The harness does not need to be a perfect static architecture linter in the first pass. It does need to create a deterministic, repeatable architecture check that can fail when the refactor contract is violated.

## Out Of Scope

- immediate conversion to multiple Gradle modules
- replacing Room with another persistence model
- replacing Compose navigation patterns with a new framework
- broad redesign of product behavior unrelated to modularization
- speculative abstraction for features that do not exist yet

## Acceptance Criteria

- Android code is reorganized around feature-first boundaries inside the existing `:app` module
- `LearningViewModel.kt` is replaced or reduced so it no longer owns unrelated feature workflows
- `LearningRepository.kt` is replaced or reduced so it no longer acts as the single business bucket for library, capture, review, backup, and settings
- `LearningOsApp.kt` becomes a lighter composition shell
- migrated screens receive feature-scoped state and actions instead of one giant app-wide state object
- AI HTTP logic no longer lives inside a general UI controller
- settings persistence no longer lives inline inside a general UI controller
- touched code removes meaningful magic numbers through named constants
- touched code removes dead code and debug prints
- touched files follow consistent naming, comments, and formatting rules
- this spec exists and the corresponding implementation plan references it
- an Android architecture verification harness exists or the current beta harness is extended to cover architecture checks
- focused unit tests pass
- `testDebugUnitTest` passes
- `assembleDebug` passes

## Risks And Mitigations

- The refactor may become a rename storm with little behavioral value
  - Mitigation: require each slice to move real responsibility, not only package names

- Splitting view-models can break shared workflows between capture, editor, reader, and library
  - Mitigation: centralize those handoffs in explicit use cases and app-shell navigation actions

- Repository splitting can create duplicated data queries
  - Mitigation: keep shared low-level Room infrastructure in `core/data` and share small gateways where justified

- The harness can become too ambitious and block delivery
  - Mitigation: start with deterministic, high-value checks rather than full custom static analysis

## Decision

Proceed with a feature-first Android modularization pass inside the existing `:app` module. Focus first on extracting external adapters, then split repositories, then split feature state/controllers, and finally thin the app shell. Treat spec updates and architecture harness updates as part of the definition of done, not optional follow-up work.
