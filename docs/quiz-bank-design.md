# Quiz Bank Design

## Goal

The quiz bank is a separate practice layer on top of the knowledge map.

Knowledge nodes explain concepts. Quiz items test whether the learner can apply those concepts under exam-like pressure.

## Current Shape

- Source files live under `<content-root>/quizzes/<area>/`.
- SQLite stores quiz metadata, body, tags, linked review nodes, sources, and FTS rows.
- React exposes a `Practice / Quiz Bank` mode.
- Quiz items link back to knowledge nodes through `linked_nodes`.

## Why Separate Quizzes From Nodes

Advantages:
- Daily review can sample quiz items without mixing them with concept notes.
- Wrong-answer history and weights can attach to quiz IDs later.
- Knowledge nodes can stay tutorial-like instead of becoming a pile of exercises.
- One concept node can support many quiz items.

Tradeoff:
- There is one extra content type to maintain.
- Some screenshots may require both a quiz item and a concept-node update.

## Future Extension Path

Likely next tables:
- `quiz_attempts`: answer history, correctness, time spent, notes.
- `review_queue`: due date, interval, ease, priority.
- `quiz_variants`: generated variants tied to a fixed parent quiz.

Likely next UI:
- Daily review page.
- Hide/show answer interaction.
- Mark correct, unsure, wrong.
- Weak-topic summary.

## Default Classification

- Specific exam screenshots become Standard Q quiz items.
- Conceptual "why" questions update or create knowledge nodes.
- If a quiz exposes a recurring misunderstanding, create both: a quiz item and a linked explanatory node.
