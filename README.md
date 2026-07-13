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

The native Android app works offline for notes, quizzes, review, search, Trash, and backup. AI is optional and only its configured provider needs a network connection.

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

Install `android-app/app/build/outputs/apk/debug/app-debug.apk`. Android build details and checks are in [android-app/README.md](android-app/README.md).

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

The frontend opens at `http://127.0.0.1:5173`; the API runs at `http://127.0.0.1:8000`. See [app/README.md](app/README.md) for frontend-only commands.

The supported `scripts/dev.ps1` launcher binds the API to loopback only. Direct Uvicorn deployment outside the local desktop profile requires a separate authenticated deployment design; it is not a supported way to expose this API on a network.

## Data safety

- Personal data stays local unless you explicitly share a backup or use an optional AI provider.
- Export before restore, cleanup, migration, or changing phones.
- Restore is a full replacement of current local data.
- Trash is reversible. Delete forever is only recoverable from backup.
- `data/`, `var/`, and generated build files are ignored; `content-demo/` is the only tracked sample library.

Recovery steps and backup limits are documented in [docs/data-recovery.md](docs/data-recovery.md).

## Repository map

```text
android-app/   Native Android beta
app/           React desktop frontend
backend/       FastAPI, SQLite, and Markdown ingest
content-demo/  Small tracked sample library
docs/          Product, recovery, and architecture guides
scripts/       Setup, development, and verification commands
```
