# Android High-Fidelity Web Migration Design

## Goal

Migrate the mature desktop Web experience into a native Android product without downgrading the visual system, interaction density, or learning workflow. The Android app must feel like the same Knowledge Workbench, not a generic Material sample.

## Web UI Deep Audit

### Color Palette

The Web product uses a dark workbench palette with a yellow operational accent:

| Token | Web Value | Android Role |
| --- | --- | --- |
| `surface` | `#0b0e11` | App background and grid base |
| `surfaceSoft` | `#181a20` | Screen panels and nav surfaces |
| `surfaceCard` | `#1e2329` | Cards, text fields, action blocks |
| `surfaceElevated` | `#2b3139` | Pressed/selected cards |
| `ink` | `#eaecef` | Primary body text |
| `inkStrong` | `#ffffff` | Headings and key values |
| `muted` | `#929aa5` | Metadata, helper copy |
| `line` | `#2b3139` | Default borders |
| `lineStrong` | `#3f4854` | Input borders |
| `accent` | `#fcd535` | Primary action, selection, counts |
| `accentStrong` | `#f0b90b` | Pressed primary action |
| `success` | `#0ecb81` | Good review state |
| `danger` | `#f6465d` | Delete/destructive state |

Android must implement these as explicit Compose tokens, not rely on default Material colors.

### Typography

Web stack: `BinanceNova`, `IBM Plex Sans`, `Aptos`, `Segoe UI`, sans-serif. Android should use the platform sans fallback but preserve Web proportions:

- Brand title: 28-32sp, extra bold, tight line height.
- Page title: 24-28sp portrait, 30-36sp landscape.
- Card title: 18-20sp, bold.
- Body: 15-16sp, medium line height.
- Eyebrow/meta: 11-12sp, uppercase, extra bold, wide tracking.
- Numeric counts and timestamps: mono fallback, 12-14sp, bold.

### Spacing System

Web uses compact but breathable operational spacing:

- Outer panels: 18-28px.
- Cards: 14-18px internal padding.
- Card gaps: 10-14px.
- Buttons/chips: 8-12px vertical and 10-14px horizontal.
- Touch migration rule: every tappable element must be at least 48dp high even when the visual content is compact.

### Component States

Web states:

- Hover: border shifts to accent, card elevates, slight translate.
- Active/selected: accent border, left inset accent line, yellow-soft gradient.
- Disabled: muted opacity, no accent inversion.
- Danger: red border/text.

Android mapping:

- Hover becomes press feedback through `MutableInteractionSource`, tonal darkening, and accent border persistence.
- Active cards keep the Web accent border/left rail.
- Long press is reserved for secondary/destructive actions only; core actions must remain visible buttons.
- Disabled buttons retain custom dark styling with reduced alpha.

## Mobile Interaction Mapping

| Web Interaction | Android Native Mapping |
| --- | --- |
| Sidebar area nav | Portrait: top workbench header + compact filter chips. Landscape: persistent left rail. |
| Node card hover | Press ripple/tonal elevation + selected accent rail. |
| Detail panel | Portrait: full-screen reader/editor with sticky action row. Landscape: list/detail split. |
| Focus reading | Portrait: immersive reader with top actions. Landscape: reading desk with optional side info. |
| Search result card | Tappable card that opens node or quiz. |
| Quiz review | Reveal answer first, then rating buttons. |
| Trash/archive action | Visible danger button, no hidden gesture-only path. |
| Backup/restore | Explicit copy/paste JSON panel with warning copy and large touch targets. |

## Layout Strategy

### Portrait

Use a single-column workbench:

1. Brand header with Knowledge Workbench identity, stats, and offline safety copy.
2. Compact tab/navigation row for Library, Search, Review, Backup.
3. Screen content as stacked dark cards.
4. Primary creation action remains visible as a full-width yellow action.
5. Reader/editor screens use a top toolbar with Back/Edit/Add quiz/Delete where appropriate.

This avoids copying the Web sidebar as an excessively long mobile column while preserving the same hierarchy and visual language.

### Landscape And Large Screens

Use available width instead of stretching:

- >= 840dp: two-pane mode. Left pane contains library/search/review queues. Right pane contains reader/editor/detail.
- >= 1100dp or foldable/tablet: allow a workbench-like three-zone rhythm where navigation, list, and detail are visually distinct.
- Panels keep Web borders, dark surfaces, and accent selection state.

No screen may rely on fixed portrait-only dimensions.

## Implementation Scope

1. Create explicit Compose visual tokens and reusable workbench components.
2. Replace default Material-looking screens with Web-aligned workbench layout.
3. Add adaptive portrait/landscape branching via `BoxWithConstraints`.
4. Improve interaction flow: clickable search results, reveal-before-review, validation, soft delete, backup safety copy.
5. Preserve offline/native constraints: no network permission, Room local data, explicit JSON backup.

## Acceptance Checklist

- Visual system uses Web palette, dark grid background, yellow accent, bordered cards, uppercase meta, and compact operational density.
- Portrait layout is a polished single-column workbench, not a collapsed desktop accident.
- Landscape layout uses at least two panes where width permits.
- All tap targets are 48dp or larger.
- Search result cards open their targets.
- Review does not show answers before reveal.
- Destructive action is visible and styled as danger.
- Backup/restore copy explains local-first data ownership.
- `android-doctor.ps1`, `testDebugUnitTest`, and `assembleDebug` pass before delivery.

## Self-Review

- No placeholder requirements remain.
- The design explicitly maps Web hover/active states to Android press/selected states.
- The design covers portrait, landscape, and larger/foldable screens.
- The scope is limited to high-fidelity migration of the existing offline Android MVP, not adding backend sync or AI.
