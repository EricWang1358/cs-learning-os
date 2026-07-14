# Contributing

CS Learning OS accepts focused issues, documentation corrections, tests, and
small feature changes. Start with an issue when a change affects data format,
backup compatibility, AI-provider boundaries, or Android navigation.

## Local Checks

Android changes require:

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
powershell -ExecutionPolicy Bypass -File ..\scripts\verify-android-architecture.ps1
```

Do not commit API keys, backups, keystores, emulator files, generated APKs, or
local Android Studio files. Keep AI writes user-confirmed and preserve the
local-first data contract.

## Pull Requests

Use one behavior-focused change per pull request. Include the commands run,
update tests for changed behavior, and describe any migration or privacy
impact. Maintainers may ask for a smaller follow-up before accepting a broad
refactor.
