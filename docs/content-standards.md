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
- Quality target: match the explanatory depth of `Shark Tank Passcode: process_code and is_valid_code`, not a short glossary note.
- Define every prerequisite term that a first-pass learner would reasonably ask about, or link/create the prerequisite node.
- For C/GDB/assembly, include vocabulary, operand or argument roles, a tiny code/command example, line-by-line interpretation, and easy-to-misread traps.
- Common mistakes.
- Quick recall section.
- Frontmatter links to prerequisites and related nodes.
- Reader questions when the learner's confusion reveals a missing explanation.
- Do not use vague filler such as "understand the concept" without showing what changes in code, registers, memory, output, or reasoning.

Workflow rule:
- Before adding future content, ask the user which standard to use unless they explicitly specify one.
- When a reader question appears, update the source node if the answer is local; create a new linked node if the answer is reusable across multiple nodes.
- If a quiz explanation needs a concept that is missing or too shallow, update/create the linked Standard A node instead of bloating the quiz.

## Placement Rules

### `cs-fundamentals`

`CS fundamentals` is intentionally broad, but new nodes must be introductory prerequisites or foundational bridges.

Allowed in `cs-fundamentals`:
- Intro-level C language basics, C memory model basics, pointers, stack/heap, arrays, strings, structs, and integer representation.
- Intro-level GDB commands and debugging workflow.
- Intro-level x86-64 concepts needed for CSAPP, Bomb Lab, calling convention, registers, addressing, and simple control flow.
- Binary representation, machine-level arithmetic, and other prerequisite concepts that many quizzes or systems notes reuse.

Not allowed by default:
- Advanced operating systems, compilers, distributed systems, architecture optimization, security exploitation, or niche one-off tricks.
- Project-specific debugging notes that belong under `projects`.
- Tool usage that is not a CS concept and belongs under `tools`.

If a topic is not intro-level but should remain findable, put it in a more specific area/track or mark it `archive`; do not dump it into `cs-fundamentals`.

Recommended frontmatter for new `cs-fundamentals` nodes:

```yaml
area: cs-fundamentals
track: c-and-memory | gdb-debugging | x86-64-assembly | bomb-lab | binary-representation | intro-systems
level: intro
```

## New Content Checklist

Before saving a new node or quiz, verify:

- The user selected or implied `Standard A` or `Standard Q`.
- The placement decision is explicit: area, track, level, visibility.
- If area is `cs-fundamentals`, `level` is intro or the note is a foundational bridge.
- The explanation would satisfy a reader who asked "why exactly?" three times.
- Required vocabulary is either defined locally or linked as a prerequisite.
- Code/command examples are concrete enough to run or mentally simulate.
- Quiz explanations show the complete reasoning path, not only the final trick.
- The content has deliberate links, not tag-only related guesses.
- Low-frequency or uncertain content is still searchable but marked `archive` or `needs-review`.

## Standard Q

Name: Quiz Bank Item

Use for:
- fixed practice questions
- exam screenshots
- daily review candidates
- questions where the learner should answer before reading the explanation

Requirements:
- Store under `<active-content-root>/quizzes/<area>/`, usually `data/content/quizzes/<area>/` for real local data.
- Keep quiz items separate from knowledge nodes.
- Include `Prompt`, `Answer`, `Explanation`, `Plain Explanation`, `What This Tests`, and `Linked Review`.
- Explanations must not skip reasoning steps; show the line-by-line state change, the mental translation, and the arithmetic.
- Quality target: `Shark Tank Passcode: process_code and is_valid_code`.
- Include "How To Think" when the quiz depends on recognizing noise, calling convention, state tracing, pointer/memory layout, or a non-obvious instruction.
- Explain tempting wrong answers and why they are wrong when they teach a useful distinction.
- Link to knowledge nodes through `linked_nodes`.
- Use `difficulty` and `weight` now so future daily review can sample by importance and weakness.

Workflow rule:
- If the user sends an exam-style screenshot, classify it as Standard Q by default unless they ask for a concept note.
- If the screenshot reveals a missing concept, add or update the linked knowledge node separately.
