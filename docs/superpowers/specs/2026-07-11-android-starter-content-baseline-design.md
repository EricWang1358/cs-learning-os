# Android Starter Content Baseline

## Goal

Make a fresh Android installation useful without requiring a desktop import. The starter pack is a compact CS study baseline, not a list of placeholder titles.

## Content Contract

The repository-owned `content-demo` directory remains the only starter-content source. Gradle packages `nodes/**/*.md` and `quizzes/**/*.md` into APK assets; the Android importer retains Markdown bodies and derives Room rows from front matter.

The baseline must contain at least:

- 12 nodes across algorithms, CS fundamentals, projects, and engineering abilities.
- 10 standalone review prompts with non-trivial answers and explanations.
- Four algorithm patterns: binary search, graph traversal, two pointers/sliding windows, and dynamic programming.
- Five systems fundamentals: virtual memory, x86 addressing, C memory/pointers, HTTP lifecycle, and database indexes.
- One project pattern, one capture workflow, and one debugging workflow.

The source material is adapted from the desktop Learning OS corpus where it is relevant. Mobile notes are intentionally concise, readable on a phone, and independently reviewable.

## Upgrade Behavior

`StarterContentSeedVersion` is incremented when the packaged baseline gains new entries. During seeding, a starter node or quiz is inserted only when its stable starter ID is absent. Existing user edits and previously deleted starter items are not overwritten or recreated.

Version 3 introduces the complete baseline. APK version `0.1.17` / code `18` carries this seed version.

## Harness

`StarterContentCatalogTest` loads the actual repository Markdown through `StarterContentImporter`. It fails when the content count, required area coverage, canonical topics, review-state count, or answer/explanation quality drop below this baseline.

Run the focused harness with:

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest --tests com.cslearningos.mobile.data.StarterContentCatalogTest
```

The release gate also runs the full debug unit suite and `:app:assembleDebug` so generated assets and the packaged APK are validated together.
