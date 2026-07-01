# CS Learning OS Android

This is the Android migration subproject for CS Learning OS.

The current milestone is a small Android-native offline product. It supports local Markdown nodes, local quiz/review state, local search, and explicit JSON backup/restore without requiring a desktop backend or hosted account. The architecture remains SaaS-ready: future sync, account, and hosted API modes should be additive adapters rather than rewrites.

## Architecture Position

```text
Native Android app
  -> Jetpack Compose UI
  -> Room/SQLite local data
  -> deterministic domain services
  -> future optional sync/API adapters
```

Local-first does not mean local-only:

- Personal content and study state should work without a hosted account.
- A future sync service can be added as an optional transport.
- AI provider configuration belongs to the user/device/account, not hardcoded app infrastructure.
- SaaS mode should be a deployment profile, not the default domain model.

## Current Milestone

The Android app is a native offline minimum product.

- Create and edit Markdown learning nodes inside the app.
- Read saved nodes without a backend.
- Add manual quizzes or derive quizzes from `:::quiz` Markdown blocks.
- Review due quiz cards using a deterministic local scheduler.
- Search local nodes and quizzes through Room FTS.
- Export and restore an explicit JSON backup.
- Use a compact `More` settings center for AI service config, local data tools, language, and day/night display mode.
- Avoid network permissions and automatic system backup of local learning data by default.

## Build Requirements

Use Android Studio or a command-line Android Gradle setup with:

- JDK 17 or newer
- Android SDK with compile SDK 35
- The checked-in Gradle wrapper at `android-app/gradlew.bat`

The wrapper installs the pinned Gradle distribution on first use. Open `android-app/` in Android Studio and run the `app` configuration, or build from the command line:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd android-app
.\gradlew.bat assembleDebug
```

If you prefer not to set `ANDROID_HOME`, create an ignored `android-app/local.properties` file with `sdk.dir=<your Android SDK path>`.

## Development Flow

Run the Android prerequisite check from the repo root:

```powershell
.\scripts\android-doctor.ps1
```

For worker handoffs or release scripts, use JSON mode:

```powershell
.\scripts\android-doctor.ps1 -Json
```

The APK is self-contained for this milestone. Install it on an emulator or phone, open the app, and create a Markdown node from the Home screen.

## Beta Versioning

The test APK version is defined in `app/build.gradle`:

- `versionCode` must increase for every Android implementation commit that produces a phone-testable APK.
- `versionName` should stay human-readable in the current beta line, such as `0.1.3`.
- Docs-only/spec-only commits do not need a version bump unless a new APK is distributed.

Build outputs such as `.apk` and `.aab` remain ignored by Git. Share or install the APK from `app/build/outputs/apk/debug/app-debug.apk`, but track which beta it is through Gradle and the commit message/handoff.

## Migration Phases

1. Native offline MVP: Compose UI, Room data, Markdown CRUD, quiz/review, local search, explicit backup.
2. Mobile UX hardening: responsive reading, better editor affordances, quiz queue polish, graph fallback.
3. Sync-ready model: device id, content ids, revision ids, conflict rules, and adapter interfaces.
4. Desktop sync: local computer transport before hosted sync.
5. Productization: signed builds, backup/restore UX, health diagnostics, privacy policy, release checklist.

## Decision Log

Recommended current stance:

- Keep the project local-first.
- Do not embed the Python FastAPI backend in Android yet.
- Do not depend on the React/FastAPI stack for the Android MVP.
- Use native Android implementation to force product boundaries and discover mobile-specific state bugs.
- Treat AI as an optional provider adapter. Users may later provide their own API key or use a hosted provider, but core reading/practice should not depend on AI.
