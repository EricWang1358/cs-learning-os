# Frontend guide

This folder contains the React and Vite desktop interface for CS Learning OS. It reads data from the local FastAPI service; personal Markdown and SQLite files do not belong in this folder.

## Run with the full app

From the repository root, the preferred command starts ingest, API, and frontend together:

```powershell
.\scripts\dev.ps1
```

The UI opens at `http://127.0.0.1:5173` and expects the API at `http://127.0.0.1:8000`.

## Frontend-only development

```powershell
cd app
npm install
npm run dev
```

Use frontend-only mode when an API is already running. Source files are under `src/`; static assets are under `public/`.

## Checks

```powershell
npm run lint
npm run build
npm run test:smoke
npm run test:navigation-focus
```

The smoke commands may require the local API and browser dependencies. Keep route and focus behavior consistent with `docs/state-machine.md`. `DESIGN.md` is a historical design-analysis artifact, not current product guidance or onboarding.
