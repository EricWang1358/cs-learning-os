# Security Baseline Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent local credential disclosure, unsafe provider traffic, hostile imported-content resource exhaustion, unsafe Markdown links, and cross-site model preflight execution.

**Architecture:** Android owns credential encryption and untrusted-content limits at the settings, backup, and Markdown boundaries. Both OpenAI-compatible transports enforce the same HTTPS/no-redirect/bounded-body policy. The Python API retains its local profile, but all model work becomes an explicit guarded POST operation rather than a side-effecting GET.

**Tech Stack:** Kotlin, Android Keystore AES-GCM, SharedPreferences, CommonMark, Jetpack Compose, JUnit/Robolectric, Python FastAPI, pytest.

---

## File Structure

- `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/data/ApiKeyProtector.kt`: Android Keystore-backed API-key envelope encryption and decryption.
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/data/SettingsPreferencesStore.kt`: encrypted-only persistence and legacy plaintext migration.
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/domain/ValidateAiSettingsUseCase.kt`: HTTPS endpoint validation.
- `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/data/AiDraftService.kt`: no redirects and bounded response handling.
- `android-app/adapter/model-openai/src/main/kotlin/com/cslearningos/mobile/assistant/openai/OpenAiModelGateway.kt`: streaming transport no-redirect and bounded fallback body handling.
- `android-app/app/src/main/AndroidManifest.xml`: explicit cleartext denial.
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/backup/BackupTransferCoordinator.kt`: streamed, capped import reader.
- `android-app/app/src/main/java/com/cslearningos/mobile/data/BackupCodec.kt`: bounded collection/string decoding before persistence.
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/markdown/StandardMarkdownDocument.kt`: bounded parser input/depth behavior.
- `android-app/app/src/main/java/com/cslearningos/mobile/ui/MarkdownRenderer.kt`: HTTPS-only link activation.
- `backend/system_router.py`: side-effect-free GET preflight and guarded POST model preflight.
- `scripts/dev.ps1`: fixed loopback launch path retained and regression-checked.

### Task 1: Encrypt API Keys And Reject Unsafe Provider Endpoints

**Files:**
- Create: `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/data/ApiKeyProtector.kt`
- Create: `android-app/app/src/test/java/com/cslearningos/mobile/feature/settings/data/SettingsPreferencesStoreTest.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/data/SettingsPreferencesStore.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/domain/ValidateAiSettingsUseCase.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/feature/settings/ValidateAiSettingsUseCaseTest.kt`
- Modify: `android-app/app/src/main/AndroidManifest.xml`

- [ ] Write tests with an in-memory `SharedPreferences` and deterministic `ApiKeyProtector` fake that assert: saving `"sk-secret"` does not store it under `apiKey`; loading an encrypted value returns the original secret; a legacy plaintext value migrates to `apiKeyEncrypted` and removes `apiKey`; malformed encrypted data returns an empty key rather than legacy plaintext.
- [ ] Run `cd android-app; .\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.feature.settings.data.SettingsPreferencesStoreTest --console=plain`; expect failure because `ApiKeyProtector` and encrypted preference behavior do not exist.
- [ ] Add `ApiKeyProtector` with `encrypt(plainText: String): String` and `decrypt(envelope: String): String?`. Its platform implementation creates/reuses an `AndroidKeyStore` AES-GCM key, serializes version + 12-byte IV + ciphertext as Base64, and returns null for malformed/unauthentic envelopes. Inject this protector into `SettingsPreferencesStore` so tests use the fake.
- [ ] Store encrypted data at `apiKeyEncrypted`; on a successful legacy read, encrypt, write the encrypted value, and remove `apiKey` atomically. Never write plaintext after the migration path.
- [ ] Add failing validation cases for `http://provider.test/v1`, `https:///v1`, and `not-a-url`; keep `https://provider.test/v1` valid. Implement `URI`-based validation that requires exactly HTTPS and a nonblank host.
- [ ] Set `android:usesCleartextTraffic="false"` on the application element.
- [ ] Re-run the settings store and validation tests; then run `:app:processReleaseMainManifest`; expect all pass and the merged manifest to contain the explicit cleartext denial.
- [ ] Commit as `fix(android): harden provider credentials`.

### Task 2: Bound Authenticated Provider Transport

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/feature/settings/data/AiDraftService.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/feature/settings/AiDraftServiceTest.kt`
- Modify: `android-app/adapter/model-openai/src/main/kotlin/com/cslearningos/mobile/assistant/openai/OpenAiModelGateway.kt`
- Modify: `android-app/adapter/model-openai/src/test/kotlin/com/cslearningos/mobile/assistant/openai/OpenAiModelGatewayTest.kt`

- [ ] Add failing fake-connection tests that assert both GET and POST disable `instanceFollowRedirects`, and that a response larger than `1_048_576` bytes fails without returning or accumulating its body.
- [ ] Run the two focused Gradle test classes; expect failure because connections currently follow redirects and use `readText()`/unbounded `StringBuilder`.
- [ ] Set `instanceFollowRedirects = false` on every API-key-bearing `HttpURLConnection`. Replace `readText()` with a `readBoundedText(maxBytes: Int = 1_048_576)` helper that counts UTF-8 bytes while reading and throws `IOException("Provider response exceeds 1048576 bytes.")` before an oversized body is retained.
- [ ] Apply the same 1 MiB bound to the gateway's non-SSE fallback accumulator; stop and emit `ModelFailure.Protocol("Provider response exceeds 1048576 bytes.")` on overflow.
- [ ] Re-run focused tests and `:adapter:model-openai:test`; expect pass.
- [ ] Commit as `fix(android): bound model transport responses`.

### Task 3: Reject Oversized Imports And Unsafe Markdown Actions

**Files:**
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/backup/BackupTransferCoordinator.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/backup/BackupTransferCoordinatorTest.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/data/BackupCodec.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/data/BackupCodecTest.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/markdown/StandardMarkdownDocument.kt`
- Modify: `android-app/app/src/main/java/com/cslearningos/mobile/ui/MarkdownRenderer.kt`
- Modify: `android-app/app/src/test/java/com/cslearningos/mobile/ui/MarkdownRendererTableTest.kt`

- [ ] Add a failing backup-reader test using a generated stream larger than `8_388_608` bytes and assert `readImportedText` throws a stable `IOException` before returning text. Add codec tests proving more than `2_000` imported records or a Markdown body over `1_000_000` characters is rejected before any mapped item is returned.
- [ ] Run the focused backup tests; expect failure because import uses unbounded `readText()` and codec arrays have no limits.
- [ ] Stream imported bytes into a bounded buffer, enforcing an 8 MiB maximum. Define codec constants for maximum records, titles, fields, and Markdown body length; validate every JSON collection and string before building backup entities.
- [ ] Add Markdown tests proving an `https://example.test/path` destination remains actionable while `intent:`, `tel:`, `sms:`, `market:`, `file:`, and `javascript:` destinations render as plain text. Add a deep nesting fixture exceeding the selected parser depth limit and assert it becomes a safe fallback block rather than recursing.
- [ ] Run focused Markdown tests; expect failure because all URI schemes are currently passed to `LocalUriHandler` and parsing depth is unbounded.
- [ ] Add an HTTPS destination predicate before `LocalUriHandler.openUri`; preserve link text for rejected URLs. Add document input/depth guards at the parser boundary with a safe plain-text fallback for over-limit input.
- [ ] Re-run focused backup and Markdown tests; expect pass.
- [ ] Commit as `fix(android): constrain imported content`.

### Task 4: Make Model Preflight Explicit And Local-Only

**Files:**
- Modify: `backend/system_router.py`
- Modify: `scripts/dev.ps1`
- Modify: `backend/smoke_test.py`
- Create: `backend/test_system_router_security.py`

- [ ] Add FastAPI tests that invoke `GET /api/ai/preflight?run_model=true` and assert no `codex_preflight` call occurs; invoke the new POST model-preflight route without `X-CS-Local-Action: 1` and assert HTTP 403; then invoke it with the header and assert exactly one model preflight call. Add a second guarded POST while one is active and assert HTTP 429.
- [ ] Run `python -m pytest backend/test_system_router_security.py -q`; expect failure because GET currently invokes model work and no guarded POST route exists.
- [ ] Remove `run_model` behavior from the GET handler. Add `POST /api/ai/model-preflight` requiring `X-CS-Local-Action: 1`, a process-local in-flight guard, and a monotonic cooldown; return 403 for a missing/incorrect header and 429 for an active/cooldown request before calling Codex.
- [ ] Keep the supported development launcher fixed at `--host 127.0.0.1`; add a regression check that prevents this command from widening its bind address. Document that direct manual Uvicorn deployment is outside the local profile and requires a separate authenticated deployment design.
- [ ] Re-run backend security tests and `python -m pytest backend -q`; expect pass.
- [ ] Commit as `fix(backend): guard local model preflight`.

### Task 5: Integrate And Verify

**Files:**
- Modify only if a preceding test demonstrates an integration defect.

- [ ] Run `cd android-app; .\gradlew.bat '-Dorg.gradle.java.installations.paths=C:\Program Files\Java\jdk-21' :app:testDebugUnitTest :app:assembleDebug --console=plain --rerun-tasks`.
- [ ] Run from repository root: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test-verify-android-architecture.ps1` and `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-android-architecture.ps1`.
- [ ] Run `python -m pytest backend -q` and `git diff --check main...HEAD`.
- [ ] Inspect the release merged manifest to confirm no new exported components, `allowBackup="false"`, and `usesCleartextTraffic="false"`.
- [ ] Commit any integration-only correction as `fix: complete security hardening verification`.
