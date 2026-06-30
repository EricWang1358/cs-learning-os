# CS Learning OS Android

This is the Android migration subproject for CS Learning OS.

The first milestone is intentionally small: an Android-native shell that can host the existing Learning OS web UI while we extract product boundaries. It follows a local-first architecture, but it is not SaaS-hostile. Future sync, account, and hosted API modes should be additive adapters rather than rewrites.

## Architecture Position

```text
Android shell
  -> WebView product shell
  -> existing React UI during migration
  -> existing FastAPI/SQLite backend during development
  -> future native/local data adapters
```

Local-first does not mean local-only:

- Personal content and study state should work without a hosted account.
- A future sync service can be added as an optional transport.
- AI provider configuration belongs to the user/device/account, not hardcoded app infrastructure.
- SaaS mode should be a deployment profile, not the default domain model.

## Current Milestone

The Android app starts as a WebView wrapper.

- Debug builds load `http://10.0.2.2:5173`, which is the Android emulator route to the host machine.
- If the dev server is unavailable, the app falls back to `file:///android_asset/www/index.html`.
- Cleartext traffic is allowed only for local development hosts.

This gives us a runnable Android surface before rewriting the data layer.

## Build Requirements

Use Android Studio or a command-line Android Gradle setup with:

- JDK 17 or newer
- Android SDK with compile SDK 35
- Gradle available through Android Studio or a future checked-in wrapper

This repository currently does not include a Gradle wrapper binary. Open `android-app/` in Android Studio, let it sync the Gradle project, then run the `app` configuration.

## Development Flow

From the repo root, start the existing local web app:

```powershell
.\scripts\dev.ps1 -Detached -NoBrowser
```

Then run the Android app in an emulator. The WebView should connect to:

```text
http://10.0.2.2:5173
```

For a physical device, override the URL in `app/build.gradle` with your machine LAN address.

## Migration Phases

1. Android shell: WebView, navigation/back behavior, local dev connectivity.
2. Mobile UI hardening: responsive focus reading, Q Queue, quiz bank, graph fallback.
3. Storage boundary: define native interfaces for content, reading activity, questions, and quiz attempts.
4. Offline package: bundle demo content and local SQLite or migrate to native SQLite/Room.
5. Sync-ready model: add device id, content ids, revision ids, conflict rules, and export/import.
6. Productization: signed builds, backup/restore, health diagnostics, privacy policy, release checklist.

## Decision Log

Recommended current stance:

- Keep the project local-first.
- Do not embed the Python FastAPI backend in Android yet.
- Do not rewrite the whole React UI immediately.
- Use the Android shell to force product boundaries and discover mobile-specific state bugs.
- Treat AI as an optional provider adapter. Users may later provide their own API key or use a hosted provider, but core reading/practice should not depend on AI.
