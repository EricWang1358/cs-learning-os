# Android Assistant Learning Loop

## Goal

The mobile assistant must operate on the user's learning workflow, not exist as an isolated chat screen.

## Closed Loops

- Capture: a capture slip can generate an AI node draft; an assistant reply can be saved into the capture inbox for later organization.
- Library: the assistant searches local nodes for citations. From an open node, the Reader can start a revision session. The working draft stores the original node ID, so applying the AI draft updates that node instead of creating a duplicate.
- Review: the assistant asks one interview question for a selected topic, evaluates the student's answer, saves the canonical answer as a daily review card, and opens Review.

## Safety And Persistence

The assistant may propose Markdown and select an existing Area, but it never mutates local content itself. The user still opens the editor and saves the result. A working draft persists its node ID in assistant conversation JSON so interrupted sessions retain the correct update target.

Existing-node revisions retain the node's current Area even if the model emits another Area directive. Review and revision modes are mutually exclusive at their entry points: starting a node revision clears review state; after a completed review, later messages are ordinary answers rather than an implicit return to an old draft. This prevents cross-workflow writes and avoids discarding an existing draft merely because the user started review.

## Harness

`AssistantActionClaimsTest` verifies node identity is retained when a working draft is revised. `AssistantConversationCodecTest` verifies the node ID survives conversation serialization. The full debug unit suite and `:app:assembleDebug` are required before release.

## Release

APK version `0.1.21` / code `22` includes the conflict-policy correction.
