# Android Architecture

The Android product is local-first and independently buildable. Room-backed local data is the application read source of truth. UI sends typed actions to screen state holders; application commands validate domain rules and commit canonical rows, projections, and future replication outbox records atomically.

The migration target and dependency rules are defined in the repository architecture specification. During Phase 1 the existing `:app` remains a compatibility shell while pure domain contracts and replaceable adapters move into Gradle modules.

No required build input may resolve outside this Android repository root.
