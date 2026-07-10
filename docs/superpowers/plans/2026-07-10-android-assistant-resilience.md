# Android Assistant Resilience Plan

## Completed Work

- [x] Add a failing parser regression for literal `"null"` stream tokens and filter it at the service boundary.
- [x] Replace full-width labelled message cards and the double-labelled composer with compact chat chrome.
- [x] Reuse the established Markdown renderer without nested card chrome for completed assistant replies.
- [x] Preserve partial replies on failure and add a retry message action.
- [x] Add visible feedback for failed capture saves and unavailable citations.
- [x] Change quick prompts into editable input prefills instead of automatic generic requests.
- [x] Allow configured cloud-model knowledge when local search has no match.
- [x] Carry a validated existing Area choice into AI-generated editable drafts.
- [x] Persist the most recent local assistant conversation through a Room migration.
- [x] Keep one persistent working draft per conversation and route follow-up requests into full-draft revisions.
- [x] Let the model separate unrelated capture suggestions from the working draft.
- [x] Add retry action model coverage and run the full unit-test and debug-build gates.

## Release Gate

- [x] Debug APK builds successfully.
- [x] APK manifest reports `versionCode 16` and `versionName 0.1.15`.
- [x] User-visible streaming-null regression is covered by a unit test.
