# Android Adaptive Action Layout

## Goal

Make every Workbench action control compact, readable, and visually aligned with the mobile navigation language. Action labels must not produce accidental fixed-width stacks or uneven wrapping.

## Shared Contract

`WorkbenchButton` is the single visual primitive for normal, primary, and destructive actions. It uses a compact 40 dp minimum height, content-aware width, lighter button typography, and wrapping text instead of single-line truncation.

`ToolbarRow` is the shared layout primitive for action groups:

- If all labels fit, actions remain on one compact, content-width row.
- If they overflow, readable actions are arranged in two equal-width columns.
- An action wider than a readable column takes its own full-width row instead of being ellipsized.
- Groups can still use a full-width button deliberately for a sole primary action such as Search or Reveal Answer.

## Scope

The change applies to capture, library, reader, review, backup, More, and assistant action groups because they already use `ToolbarRow`. Remaining Library rows that combined metadata with buttons now separate metadata from actions before using the same layout.

## Regression Harness

`ToolbarLayoutPolicyTest` verifies the three decision paths: natural single row, balanced overflow columns, and long-label promotion. The full debug unit suite and `:app:assembleDebug` remain the release gate.

## Release

APK version `0.1.18` / code `19` contains this action-layout behavior.
