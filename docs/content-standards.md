# Content Standards

## Standard A

Name: Bilingual Practical Exam Note

Use for:
- coursework questions
- exam-style command recall
- GDB, C, systems, low-level debugging
- topics where command examples and short code samples matter

Requirements:
- English and Chinese explanations.
- A concrete command or code sample.
- A plain-language explanation.
- A tutorial tone: start from confusion, show what to type, explain how to read the result.
- Common mistakes.
- Quick recall section.
- Frontmatter links to prerequisites and related nodes.
- Reader questions when the learner's confusion reveals a missing explanation.

Workflow rule:
- Before adding future content, ask the user which standard to use unless they explicitly specify one.
- When a reader question appears, update the source node if the answer is local; create a new linked node if the answer is reusable across multiple nodes.

## Standard Q

Name: Quiz Bank Item

Use for:
- fixed practice questions
- exam screenshots
- daily review candidates
- questions where the learner should answer before reading the explanation

Requirements:
- Store under `content/quizzes/<area>/`.
- Keep quiz items separate from knowledge nodes.
- Include `Prompt`, `Answer`, `Explanation`, `Plain Explanation`, `What This Tests`, and `Linked Review`.
- Link to knowledge nodes through `linked_nodes`.
- Use `difficulty` and `weight` now so future daily review can sample by importance and weakness.

Workflow rule:
- If the user sends an exam-style screenshot, classify it as Standard Q by default unless they ask for a concept note.
- If the screenshot reveals a missing concept, add or update the linked knowledge node separately.
