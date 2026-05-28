# Navigation Design

## Goal

The UI should not hard-code learning paths. Content metadata should decide how nodes appear in the portal.

## Current Model

Nodes use three levels:

- `area`: broad domain, such as `cs-fundamentals`.
- `track`: subdomain or learning path, such as `x86-64-assembly`.
- `order`: human-maintained reading order inside a track.

Example:

```yaml
area: cs-fundamentals
track: x86-64-assembly
order: 20
```

## Why This Exists

Flat areas become noisy once they pass roughly 10 nodes. `CS fundamentals` already contains C, GDB, x86-64, Bomb Lab, memory, and networking. These are related, but not one linear list.

## Rules

- Use `track` for reader-facing path grouping.
- Use `order` for stable recommended reading sequence.
- Use `prerequisites` and `related` for graph reasoning and recommendations.
- Do not encode topic-specific paths directly in React.

## Future Extension

A future `content/portals/*.yaml` file can override labels, descriptions, featured paths, and pinned nodes. If no portal file exists, the app should infer navigation from `area`, `track`, and `order`.
