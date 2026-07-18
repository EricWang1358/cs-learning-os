# CS Learning OS

CS Learning OS is a local-first study app for Markdown notes, quiz cards, search, and review. Choose the build that matches what you want to do.

## Desktop beta

The desktop beta runs the React app and FastAPI service on Windows. It keeps personal content under the ignored `data/` directory.

```powershell
.\scripts\bootstrap-beta.ps1
.\scripts\start-beta.ps1
```

For a five-minute walkthrough, read [docs/first-run.md](docs/first-run.md).

## Android beta

The Android beta is a native Jetpack Compose app. Its local Room database is the source of truth, so the core note, quiz, review, search, Trash, export, and restore workflows work offline without an account or backend.

AI is optional. A user-configured provider is the only network boundary: cleartext traffic is disabled, provider endpoints must use HTTPS, and the API key is encrypted with Android Keystore protection. If a stored key cannot be protected or recovered, the app fails closed and asks for it again rather than retaining plaintext. Backup restore validates bounded input before replacing local data.

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

Install `android-app/app/build/outputs/apk/debug/app-debug.apk`. Android build details and checks are in [android-app/README.md](android-app/README.md).

The first public Android prerelease is distributed through GitHub Releases. It
is a beta APK, not a Play Store listing. Verify the published SHA-256 before
installing; configure an external AI provider only if you choose to use AI.
Local notes, review data, backups, and provider settings stay on the device by
default.

The Android beta currently mirrors the desktop Library and sync flows. The
next phase narrows mobile scope to review drills and AI Q&A (see
[Next phase](#next-phase-mobile-first-review-loop) above).

Android documentation:

- [User guide (Chinese)](android-app/docs/android-app-usage.md)
- [Architecture](android-app/docs/architecture.md)
- [Recovery and backup contract](android-app/docs/data-recovery.md)
- [Client Android-parity plan](docs/client-android-parity-plan.md)
- [Android documentation status and index](docs/android-migration.md)

## Developer setup

Desktop development requires Python, Node.js, and npm:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r backend\requirements.txt
cd app
npm install
cd ..
.\scripts\dev.ps1
```

The frontend opens at `http://127.0.0.1:5173`; the API runs at `http://127.0.0.1:8000`. See [app/README.md](app/README.md) for frontend-only commands. Cross-platform setup and trouble-shooting are in [docs/SETUP.md](docs/SETUP.md).

The supported `scripts/dev.ps1` launcher binds the API to loopback only. Direct Uvicorn deployment outside the local desktop profile requires a separate authenticated deployment design; it is not a supported way to expose this API on a network.

## Data safety

- Personal data stays local unless you explicitly share a backup or use an optional AI provider.
- Export before restore, cleanup, migration, or changing phones.
- Restore is a full replacement of current local data.
- Trash is reversible. Delete forever is only recoverable from backup.
- `data/`, `var/`, and generated build files are ignored; `content-demo/` is the only tracked sample library.

Recovery steps and backup limits are documented in [docs/data-recovery.md](docs/data-recovery.md).

## Next phase: mobile-first review loop

The current desktop app excels at authoring and knowledge-graph building. The
next phase shifts the mobile experience from a general client into a focused
review companion:

**Desktop (authoring → generation)**
- Build and maintain tutorial nodes, quiz banks, and 3D knowledge trees.
- Generate Daily-Bite-compatible review questions plus answer explanations
  from the quiz Markdown already stored under `data/content/quizzes/`.
- Package generated review sets into a mobile-ready bundle (sync protocol
  already exists). The desktop does the heavy content work so the phone
  never has to.

**Phone (review → Q&A)**
- Pull review bundles from the desktop via the existing sync gateway.
- Focus on fill-in-blank and short-answer recall drills — not reading
  full tutorials.
- AI assistant provides on-device explanations when you get a question
  wrong, pulling context from linked knowledge nodes without transferring
  every tutorial file.

**Why not mirror full tutorials to the phone?**
- Tutorial content is best authored and consumed on a large screen.
- Phone storage and network for a complete course mirror are unnecessary
  when only review cards and answer keys matter on the go.
- The sync protocol already supports scoped pulls; review bundles are a
  natural filter.

The goal: desktop builds the map and writes the tests; phone drills recall
and answers "why was I wrong" — each doing what their form factor does best.

## Community and Security

- [License](LICENSE)
- [Contributing](CONTRIBUTING.md)
- [Security reporting](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Changelog](CHANGELOG.md)

## Repository map

```text
android-app/   Native Android beta
app/           React desktop frontend
backend/       FastAPI, SQLite, and Markdown ingest
content-demo/  Small tracked sample library
docs/          Product, recovery, and architecture guides
scripts/       Setup, development, and verification commands
experimental/  Non-production prototypes kept for reference and extraction
```

The repository layout contract, runtime-data boundaries, and rules for adding
new modules are documented in [docs/repository-layout.md](docs/repository-layout.md).
Run `.scripts\verify-repository-layout.ps1` before opening a structural pull
request.
