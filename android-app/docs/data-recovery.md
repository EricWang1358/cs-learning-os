# Android Data Recovery

Backup schema v1 remains readable throughout the modular migration. Restore is an explicit, previewed operation; malformed or unsupported input must leave Room unchanged. Incremental device synchronization is not a backup replacement and is not implemented in Phase 1.

Before any future destructive migration, export a backup through the in-app Backup screen and verify that the exported file can be selected for restore.
