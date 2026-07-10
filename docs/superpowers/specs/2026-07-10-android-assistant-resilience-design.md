# Android Assistant Resilience Design

## Purpose

Make the mobile knowledge assistant readable on a phone and reliable when an OpenAI-compatible provider produces malformed or interrupted streaming output.

## Interaction Design

- The conversation owns the screen: a compact top bar, left/right message bubbles, and one fixed composer.
- Quick prompts only prefill the composer so a generic label is never stored as a conversation turn or capture.
- Conversations persist locally in Room and restore the most recent displayable session after app restart.
- The composer has one placeholder rather than a duplicated label and placeholder.
- Assistant replies render with the existing CommonMark pipeline after streaming completes; partial tokens remain plain text until then.
- A failed reply retains any useful partial text, explains the interruption, and exposes a retry action for the original request.
- The configured cloud model may answer from general knowledge when local search has no match. It does not claim to perform independent live web search.
- For explicit note-creation requests, the model selects only from the supplied existing Areas and sends the selected Area into the editable draft.

## Reliability Rules

- Ignore empty, JSON-null, and literal `"null"` stream tokens.
- Reject draft Area directives that do not exactly match an existing Area, including Areas deleted while a draft is open.
- Keep partial text visible when a stream fails.
- Restore a retry path without granting the assistant any direct data mutation permissions.
- Show feedback when saving a reply or opening a referenced local source fails.
- Scroll the conversation to the newest streamed content while the assistant is replying.

## Acceptance Harness

- `AssistantStreamParserTest` verifies null token filtering and SSE control handling.
- `AssistantActionClaimsTest` verifies retry action lookup and single-claim capture saving.
- `:app:testDebugUnitTest` is the full regression gate.
- `AssistantConversationCodecTest` verifies lossless local display-history encoding.
- `:app:assembleDebug` produces a package whose manifest is `versionCode 15` and `versionName 0.1.14`.
