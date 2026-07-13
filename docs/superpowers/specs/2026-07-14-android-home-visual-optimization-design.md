# Android Home Visual Optimization Design

## Goal

Make the Android Home screen faster to scan and act on at phone size without changing routes, learning data, command handling, or sync behavior.

## Scope

This change is limited to the Home composition and its focused UI tests.

- Keep the existing warm Workbench palette and bottom navigation.
- Retain the existing Nodes and Due counts.
- Replace the two-column primary card grid with one full-width continue-learning card followed by a compact three-action row.
- Preserve the existing Capture, Create, and Search destinations and callbacks.
- Leave Library, Review, More, persistence, and application/content modules unchanged.

## Layout

The phone Home screen has three visual bands above the persistent bottom navigation:

1. A compact brand and statistics header.
2. A full-width continue-learning card. It shows the current node title, a readable summary, and the existing continue affordance. The title wraps rather than being artificially truncated when space permits.
3. A single horizontal row of equal-width Capture, Create, and Search actions. Each action keeps its current destination and labels.

The layout intentionally leaves the next content section visible below the action row when the viewport permits, indicating that the screen scrolls. Fixed UI controls retain stable dimensions so labels cannot resize the layout.

## Behavior

The optimization is presentation-only:

- Continue opens the current node exactly as before.
- Capture, Create, and Search invoke their existing callbacks.
- Nodes and Due values remain derived from current UI state.
- Bottom navigation and accessibility descriptions remain unchanged.

## Verification

- Add or update focused Compose UI tests for the Home content structure and action callbacks before changing production layout.
- Run the affected app unit tests and assemble the debug APK.
- Install the APK on the existing Android emulator and capture Home screenshots at `1080x2400` before and after the change.
- Confirm the post-change Home screenshot is nonblank, shows the full-width continue card and the three action row, and preserves a functioning Library navigation transition.

## Non-Goals

- No redesign of Library, Review, Capture, More, or editor screens.
- No color-system replacement, animation redesign, new data fields, or network synchronization work.
- No changes to Phase 2A content-command transaction semantics.
