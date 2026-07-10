# Android Adaptive Action Layout

## Goal

Make every Workbench action control compact, readable, and visually aligned with the mobile navigation language. Action labels must not produce accidental fixed-width stacks or uneven wrapping.

## Shared Contract

`WorkbenchButton` is the single visual primitive for normal, primary, and destructive actions. It uses a compact 44 dp minimum height, content-aware width, readable 13 sp typography, and wrapping text instead of single-line truncation.

`ToolbarRow` is the shared layout primitive for action groups:

- If all labels fit, actions remain on one compact, content-width row.
- If they overflow, readable actions are arranged in two equal-width columns.
- An action wider than a readable column takes its own full-width row instead of being ellipsized.
- Groups can still use a full-width button deliberately for a sole primary action such as Search or Reveal Answer.

Option sets and low-frequency secondary actions do not remain as unbounded button grids. `WorkbenchMenuButton` keeps the same trigger style while presenting model choices, capture types, and Reader secondary actions in a readable menu. This keeps high-frequency action groups within two rows on a phone.

## Scope

The change applies to capture, library, reader, review, backup, More, and assistant action groups because they already use `ToolbarRow`. Remaining Library rows that combined metadata with buttons now separate metadata from actions before using the same layout.

## Regression Harness

`ToolbarLayoutPolicyTest` verifies the three decision paths: natural single row, balanced overflow columns, and long-label promotion. The full debug unit suite and `:app:assembleDebug` remain the release gate. Toolbar children must not supply `fillMaxWidth()` unless the group contains one deliberate full-width action, because it overrides the adaptive measurement policy.

## Release

APK version `0.1.19` / code `20` contains this action-layout behavior and the post-audit optimization pass.
