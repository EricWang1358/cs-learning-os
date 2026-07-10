# Android Assistant Resilience Plan

## Completed Work

- [x] Add a failing parser regression for literal `"null"` stream tokens and filter it at the service boundary.
- [x] Replace full-width labelled message cards and the double-labelled composer with compact chat chrome.
- [x] Reuse the established Markdown renderer without nested card chrome for completed assistant replies.
- [x] Preserve partial replies on failure and add a retry message action.
- [x] Add visible feedback for failed capture saves and unavailable citations.
- [x] Add retry action model coverage and run the full unit-test and debug-build gates.

## Release Gate

- [x] Debug APK builds successfully.
- [x] APK manifest reports `versionCode 14` and `versionName 0.1.13`.
- [x] User-visible streaming-null regression is covered by a unit test.
