# Content Standards

Before adding learning content, ask which standard to use unless the user explicitly names one.

## Private Library Path Rule

For real local tutorials and quiz-bank items in this project, write into the active content root:

```text
data/content/
```

If `CS_LEARNING_CONTENT` is set, use that configured content root instead.

Do not write real study material into:
- `content-demo/`, which is only for small demo and smoke-test fixtures.
- root-level `data/nodes/` or `data/quizzes/`, which are invalid orphan locations.
- legacy ignored `content/`, unless the user explicitly selected it for migration.

## Standard A: Bilingual Practical Exam Note

Use Standard A for coursework, exam questions, debugging commands, systems/C topics, and concepts that benefit from concrete command or code examples.

Tone target:
- Write like a patient tutorial, not a glossary.
- Start from the learner's likely confusion.
- Use a small runnable example before abstract explanation when possible.
- Keep English and Chinese aligned: the Chinese should explain the same idea, not become a loose unrelated translation.
- Prefer "what you type", "what you see", and "how to read it" over only definitions.
- Match the depth of `Shark Tank Passcode: process_code and is_valid_code`: no skipped reasoning steps, no unexplained prerequisite vocabulary, and no bare definitions without examples.
- For C/GDB/assembly, explain roles of each command, operand, register, variable, memory location, or branch condition that the learner needs to solve the related quiz.

Required path:

```text
<active-content-root>/nodes/<area>/<slug>.md
```

Required structure:

```markdown
# English Title / 中文标题

## What This Solves / 解决什么问题
English explanation.

中文解释。

## Core Commands or Code / 核心命令或代码
Use short, runnable examples.

## Plain Explanation / 通俗解释

Explain the idea like teaching a busy student before an exam.

## Reader Questions / 读者追问
Answer likely confusions exposed by the learner.

## Common Mistakes / 常见错误

- English + 中文 paired bullets.

## Quick Recall / 快速记忆
Add a compact exam-oriented memory hook.

## Suggested Next / 下一步
Link to related nodes by slug in frontmatter and mention the learning path in text.
```

Rules:
- Keep frontmatter concise and searchable.
- Include both English and Chinese in the body.
- Prefer exact commands over vague descriptions.
- Include a small C or GDB example when relevant.
- Build links deliberately: prerequisites before related/suggested ideas.
- If a command depends on architecture, state the portable form and the common x86-64 form.
- If a question only clarifies the current node, update that node; if it reveals a reusable prerequisite or cross-topic bridge, create a new linked node.
- If the note introduces a term such as accumulator, general-purpose register, quad-word, stack pointer, immediate, displacement, or zero-extension, define it in plain language or link to a prerequisite node.
- Do not add a shallow node just to satisfy a quiz link. If the knowledge node would be too thin, fold the answer into the current node or create a fuller prerequisite.

## Placement Gate: `cs-fundamentals`

`CS fundamentals` is broad but not a dumping ground. New nodes may enter this area only when they are intro-level prerequisites or foundational bridges.

Allowed:
- Intro C basics, pointers, arrays, strings, structs, stack/heap, memory layout, integer representation.
- Intro GDB commands and debugging workflow.
- Intro x86-64 concepts for CSAPP/Bomb Lab: registers, calling convention, addressing, simple arithmetic, `cmp`/jumps, stack inspection.
- Binary representation and machine-level concepts repeatedly reused by quizzes.

Not allowed by default:
- Advanced OS/compiler/security/architecture topics.
- Project-specific implementation notes.
- Tool-only workflow notes that belong in `tools`.
- Rare tricks that should be `archive` or a more specific area.

Recommended metadata:

```yaml
area: cs-fundamentals
track: c-and-memory | gdb-debugging | x86-64-assembly | bomb-lab | binary-representation | intro-systems
level: intro
```

## Standard Q: Quiz Bank Item

Use Standard Q for fixed practice questions, exam screenshots, daily review candidates, and anything where the learner should answer before reading the explanation.

Data rule:
- Store quiz items under `<active-content-root>/quizzes/<area>/`, usually `data/content/quizzes/<area>/` for real local data.
- Keep quiz items separate from knowledge nodes.
- Link quiz items to knowledge nodes with `linked_nodes`.
- Use `weight` as a future scheduling hint, but do not overfit the first version.

Required frontmatter:

```yaml
id: stable-quiz-id
title: "Question title"
area: cs-fundamentals
status: seed
visibility: practice
difficulty: easy | medium | hard
weight: 1
tags: [topic, skill]
linked_nodes: [node-slug]
sources:
  - source-or-screenshot-note
summary: "What this quiz trains."
```

Required body:

```markdown
# Title

## Prompt
The question exactly enough to practice from.

## Answer
Final answer first.

## Explanation
Step-by-step reasoning.

## Plain Explanation
Explain the trick or mental model.

## What This Tests
- Skill bullets.

## Linked Review
Mention linked nodes and why to review them.
```

Quality rules:
- Prefer one core skill per quiz item.
- Include tempting wrong answers when they teach a useful distinction.
- If an answer is uncertain from the screenshot, mark the quiz as needing confirmation instead of inventing certainty.
- Keep the prompt reproducible without needing the original image.
- Do not jump steps in explanations. For assembly quizzes, include line-by-line state updates, operand-direction notes, branch decisions, and hex arithmetic when relevant.
- Add a "How To Think" or equivalent walkthrough when the solution depends on recognizing noise, calling convention, or a non-obvious instruction pattern.
- Use `Shark Tank Passcode: process_code and is_valid_code` as the quality bar: final answer first, then exhaustive but readable reasoning, then plain explanation, then linked review.
- If the quiz requires a concept that is not already explained deeply enough, update or create the linked Standard A node before claiming the quiz is complete.
