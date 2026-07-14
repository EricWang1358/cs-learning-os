# Security Baseline Hardening Design

## Goal

Close the confirmed medium-risk local attack paths without changing the offline-first product model or introducing an account system.

## Scope

The release hardening slice covers five boundaries:

1. AI provider credentials at rest and in transit.
2. External backup and Markdown input resource limits.
3. Markdown links opened from untrusted imported or model-generated content.
4. Cross-site triggering of local model preflight work.
5. Accidental exposure of the desktop-local backend beyond loopback.

It does not add users, remote synchronization authentication, or a general server authorization model. Those require a separate product and deployment design.

## Threat Model

The Android user deliberately configures an OpenAI-compatible provider and deliberately selects backup files. The app must nevertheless avoid preserving API keys as plaintext, transmitting credentials over HTTP, following an authenticated redirect to a different origin, exhausting memory on hostile selected content, or opening privileged URI schemes from Markdown.

The Python API is a local desktop companion. CORS is not an access-control boundary: a browser can issue a cross-origin GET to loopback even when it cannot read the response. Expensive model work must therefore never be exposed through a side-effecting GET endpoint.

## Design

### Credential Storage And Provider Transport

`SettingsPreferencesStore` will store only an authenticated-encrypted API-key envelope in preferences. The envelope is encrypted using an AES-GCM key held by Android Keystore. A successful read of the legacy plaintext preference migrates it to the encrypted slot and removes the plaintext slot in the same preference edit. A missing, malformed, or undecryptable envelope produces an empty API key and never falls back to plaintext.

AI settings validation will require an absolute `https` base URL with a nonempty host. Both model-list/draft requests and the streaming gateway will set `instanceFollowRedirects = false`, so the Authorization header is never silently forwarded by a redirect. The manifest will explicitly disable cleartext traffic as defense in depth for API 26/27.

Provider body readers will enforce a shared bounded response size. A response exceeding the limit fails as a transport/protocol error instead of being accumulated in memory.

### Untrusted Backup And Markdown Content

Backup import will stream through a byte-counting reader and reject inputs larger than the documented import limit before JSON parsing. The codec will reject collections, strings, and Markdown bodies that exceed bounded import limits. Limits are owned by the backup domain boundary rather than Compose UI, so every import entry point receives the same protection.

Markdown parsing will accept a bounded input length and maximum structural nesting depth. Exceeding a limit produces a plain, safe fallback block rather than recursive traversal. The renderer will permit only `https` URI links; all other destinations are displayed as text and are not handed to `LocalUriHandler`.

### Local Backend Model Controls

`run_model` will be removed from the GET preflight query contract. GET preflight remains side-effect free. A separately named POST operation will require an explicit same-origin/local control header before launching Codex work, reject concurrent runs, and apply a short per-process cooldown. Existing local UI callers will be updated to use the POST action.

The supported server launcher will keep the loopback bind. Startup will reject non-loopback binding unless an explicit opt-in environment variable is set, making network exposure a deliberate deployment decision rather than an accidental command-line change.

## Error Handling

- Invalid or migrated-unavailable secrets surface as an unconfigured provider and require re-entry; no secret value is displayed in errors or logs.
- HTTPS, redirect, response-size, import-size, and unsafe-link failures receive user-safe errors without exposing provider responses or local paths.
- Backup validation rejects the full import atomically before any Room write.
- Backend rejected model-run requests return a stable 4xx response; failures never invoke the model process.

## Test Strategy

- Unit tests prove plaintext migration, malformed ciphertext rejection, and encrypted-only persistence through a fake key protector.
- Settings and gateway tests prove HTTP base URLs and redirects are rejected before credentials are sent.
- Backup tests prove oversized streams, oversized collections, and oversized Markdown are rejected without partial import.
- Markdown renderer tests prove only `https` destinations become openable links and deeply nested input does not recurse unboundedly.
- Backend tests prove GET preflight is side-effect free, POST requires the explicit control header, concurrent/cooldown calls are rejected, and non-loopback startup requires opt-in.
- Run Android unit tests, backend tests, architecture gates, debug/release manifest processing, and debug APK assembly.

## Non-Goals And Follow-Up

Backend user authentication, per-user authorization, public remote deployment, TLS termination, distributed rate limits, and a fully sandboxed Markdown language are deferred. Before enabling any non-loopback deployment, a separate authentication and operational-security design is mandatory.
