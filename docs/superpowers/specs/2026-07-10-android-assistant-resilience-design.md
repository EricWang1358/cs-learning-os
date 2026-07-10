# Android Assistant Resilience Design

## Purpose

Make the mobile knowledge assistant readable on a phone and reliable when an OpenAI-compatible provider produces malformed or interrupted streaming output.

## Interaction Design

- The conversation owns the screen: a compact top bar, left/right message bubbles, and one fixed composer.
- The composer has one placeholder rather than a duplicated label and placeholder.
- Assistant replies render with the existing CommonMark pipeline after streaming completes; partial tokens remain plain text until then.
- A failed reply retains any useful partial text, explains the interruption, and exposes a retry action for the original request.

## Reliability Rules

- Ignore empty, JSON-null, and literal `"null"` stream tokens.
- Keep partial text visible when a stream fails.
- Restore a retry path without granting the assistant any direct data mutation permissions.
- Show feedback when saving a reply or opening a referenced local source fails.
- Scroll the conversation to the newest streamed content while the assistant is replying.

## Acceptance Harness

- `AssistantStreamParserTest` verifies null token filtering and SSE control handling.
- `AssistantActionClaimsTest` verifies retry action lookup and single-claim capture saving.
- `:app:testDebugUnitTest` is the full regression gate.
- `:app:assembleDebug` produces a package whose manifest is `versionCode 14` and `versionName 0.1.13`.
