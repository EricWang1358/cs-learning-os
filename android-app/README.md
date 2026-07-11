# CS Learning OS Android

The Android beta is a native, local-first app for Markdown notes, quiz cards, review, search, Trash, and explicit backup and restore. Core study flows work without an account, desktop service, or AI.

## Build and run

Requirements:

- JDK 17 or newer
- Android SDK with compile SDK 35
- The checked-in Gradle wrapper

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd android-app
.\gradlew.bat assembleDebug
```

You can instead set `sdk.dir` in the ignored `android-app/local.properties`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Data and recovery

All notes and study state are stored in Room on the device. Export a backup before restore, cleanup, migration, or changing phones. Restore is a full replacement: current local data is deleted and replaced by the selected backup. Trash can be restored; Delete forever is only recoverable from backup.

See [../docs/first-run.md](../docs/first-run.md) and [../docs/data-recovery.md](../docs/data-recovery.md).

## Network permission policy

The manifest includes `android.permission.INTERNET` only for the user-configured, optional AI provider. Reading, editing, search, review, Trash, export, and restore do not require network access. Do not add networking for analytics, automatic sync, or a mandatory backend. Any new network feature must be explicit, feature-scoped, documented, and keep the offline core usable.

Android automatic system backup remains disabled so app data is not copied through an implicit channel. Users control transfer through explicit export and restore.

## Advanced commands

Run these from `android-app/` unless noted:

```powershell
# Full Android unit suite
.\gradlew.bat :app:testDebugUnitTest

# Debug APK
.\gradlew.bat :app:assembleDebug

# Environment diagnosis, from the repository root
.\scripts\android-doctor.ps1
.\scripts\android-doctor.ps1 -Json

# Package-boundary and compatibility-size checks, from the repository root
.\scripts\verify-android-architecture.ps1
```

`verify-android-beta.ps1` is reserved for versioned beta distribution because it also checks release metadata and tags. Routine feature work can use unit tests, architecture verification, and `assembleDebug` independently.

## Architecture

```text
Jetpack Compose UI
  -> feature and application services
  -> Room/SQLite local storage
  -> optional provider or future sync adapters
```

Keep local data and deterministic study behavior independent from optional providers. Stable ids, revisions, tombstones, and explicit conflict rules are required before adding sync.

## Versioning

`app/build.gradle` owns `versionCode` and `versionName`. Change them only for a requested phone-testable beta release, then update `docs/release-notes.md` and its matching tag. A task that explicitly says no version bump must leave all three unchanged.
