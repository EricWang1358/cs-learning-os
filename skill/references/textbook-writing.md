---
name: textbook-writing
description: "Guidelines for writing English CS textbook-grade content, modeled on CS:APP, TCP/IP Illustrated, and other authoritative CS textbooks."
---

# Textbook-Grade CS Writing / 教科书级计算机写作

This reference defines what "textbook-grade" means for CS learning content and
how to achieve it. The canonical model is *Computer Systems: A Programmer's
Perspective* (CS:APP, Bryant & O'Hallaron) and similar authoritative textbooks.

## What Makes Textbook-Grade Content

Textbook-grade content is **not** about being long or formal. It is about being
**complete**, **precise**, and **pedagogically structured** — every concept is
motivated, defined, exemplified, and practiced.

| Dimension | Textbook | Typical Tutorial |
|-----------|----------|-----------------|
| Motivation | "Why this matters" before how | Often skips to how |
| Definitions | Every term defined on first use | Assumes reader already knows |
| Examples | Worked step-by-step with reasoning | One-liner code snippets |
| Practice | Scaffolded: check → exercise → problem | None or one "try it" |
| Pitfalls | Common mistakes named and explained | Mentioned only in passing |
| Depth | Builds from first principles | "Just enough to be dangerous" |

## Template: Textbook-Grade CS Node

In addition to the Standard A frontmatter sections (What This Solves, Core
Commands, Plain Explanation, Common Mistakes, Quick Recall), a textbook-grade
node adds these pedagogical sections:

```markdown
## Learning Objectives / 学习目标

By the end of this section you will be able to:
- Define [key terms] and state the [core rule].
- Trace [process/algorithm] step-by-step for a given input.
- Predict [outcome] given [conditions].
- Debug [common mistake] in a code snippet.

## Motivation / 为什么重要

A real scenario that this concept solves. "Without [concept], you would
have to [painful alternative]. With it, [benefit]."

## Core Idea / 核心概念

The single invariant, rule, or mental model. One paragraph.
If the reader remembers only one thing, this is it.

## Terminology / 术语表

**Term:** One-sentence definition. No circular definitions.
**Related:** [cross-reference]

---

## Worked Example / 示例分析

A concrete problem with step-by-step reasoning. Each step:
1. States what happens.
2. Shows the state change (register, memory, variable, or diagram).
3. Cites the rule that justifies it.

## Practice Problems / 练习

### Check (recall)
- Factual: state the rule, define the term.

### Apply (single-step)
- Given input X, what is output Y?

### Analyze (multi-step)
- Trace this code/protocol/algorithm and explain why.

### Evaluate / Debug (deep)
- Find the bug. Why is it wrong? How would you fix it?

## Reader Questions / 读者追问

**Q: [A question a confused reader would actually ask.]**

**A:** [Answer that addresses the confusion directly, not just rephrasing
  the section title.]

## Conceptual Model / 概念模型

A diagram or mental metaphor for the abstract idea:
- "Think of the linker as a name resolver, not a type checker."
- "A page table is like a phone book: you look up the virtual page to find the physical frame number."

## Spiral Review / 螺旋回顾

Connect to content from earlier nodes:
- "Recall how [previous concept] works. Now see how [current concept] extends it."
```

## Pedagogical Patterns

### 1. Concrete → Abstract → Concrete

Always start with a concrete example, extract the principle, then apply it to
a new concrete case.

```
❌ "A page fault occurs when the referenced page is not in memory."
   (Abstract definition first — reader has no foundation.)

✅ "Your program accesses address 0x7ffee123. The TLB misses, the page table
   walk shows the PTE has its valid bit cleared. The OS loads the page from
   disk. That sequence is a page fault. More precisely: a page fault occurs..."
   (Concrete trace first, then the definition.)
```

### 2. Worked Example with State Tracking

For every operation that changes state (assembly instruction, malloc trace,
protocol message), show the state **before** and **after**:

```text
Before:     rax = 0x1000, rbx = 0x2000, [0x1000] = 42
leaq (%rax, %rbx, 2), %rcx
After:      rcx = 0x1000 + 2*0x2000 = 0x5000
```

Rules:
- Label every value with its meaning ("rax holds the array base"), not just
  the hex number.
- Show intermediate calculations in full before the final answer.
- Use plain arithmetic before hex where possible.

### 3. The "Why Not" Pattern

After stating a rule, immediately address the natural counter-question:

> "The linker selects the strong definition over the weak one."
>
> *Wait — does that mean a weak definition's type doesn't matter?*
>
> **Correct.** The linker resolves names, not types. A weak `double x` and a
> strong `int x` link silently; using `x` as a double where the strong
> definition has `int` is undefined behavior.

This pattern anticipates the reader's skepticism and addresses it before they
move on frustrated.

### 4. Problem Scaffolding

Problems should be scaffolded by difficulty, not dumped as one block:

| Level | Skill | Example |
|-------|-------|---------|
| **Check** | Recall a definition | "What does `restrict` guarantee?" |
| **Apply** | Use a rule once | "Given this expression and precedence, what is the result?" |
| **Trace** | Follow a multi-step process | "Draw the process tree for this fork program." |
| **Debug** | Find an error in code | "Why does this linker command fail?" |
| **Design** | Create a solution | "Design a block layout that improves utilization for 8-byte requests." |

### 5. Spiral Re-entry

When a new concept depends on an earlier one, **explicitly recall** the earlier
concept before building on it:

> "Recall from [virtual memory and page faults](...): a TLB miss does not
> automatically mean a page fault. The same distinction applies here: a signal
> being pending does not mean it has been delivered."

### 6. Progression Gauge

Label difficulty so readers can self-select:

```
[Basic]  — core definition, required for what follows
[Core]   — essential reasoning pattern
[ deeper ] — optional depth; skip on first read
[Advanced] — builds on multiple prior concepts
```

## Quality Checklist

Before marking a node as complete (status: active), verify:

### Completeness
- [ ] Every term in the body is defined (locally if short, via link if needs its own node).
- [ ] Every code snippet runs and produces the stated output.
- [ ] Every prerequisite slug in frontmatter resolves to an existing node.
- [ ] Every inline link works: `[text](slug.md)` matches a real file.
- [ ] At least one worked example with step-by-step reasoning.
- [ ] At least one practice problem per difficulty level (check, apply, trace).
- [ ] Reader Questions section with 2+ questions a real beginner would ask.

### Depth
- [ ] The "why it works" is explained, not just "what to type."
- [ ] The "why not" is addressed for any rule that seems arbitrary.
- [ ] Edge cases and limits are named (not hidden).
- [ ] Common mistakes include the reasoning behind the mistake, not just the correction.

### Textbook Style
- [ ] English is precise, active voice, technical terms used correctly.
- [ ] Examples are minimal — they teach one new idea, not ten.
- [ ] Each paragraph adds one point; no filler.
- [ ] Diagrams or mental models for abstract concepts (cited as `![Alt](/content-assets/...)`).

### Standards Alignment
- [ ] Matches Standard A (tutorial node) or Standard Q (quiz) structure from content-standards.md.
- [ ] Frontmatter `prerequisites` and `related` are verified against the knowledge graph.
- [ ] No mastery data in Markdown (mastery is dynamic, not frontmatter).
- [ ] Summary field captures the one-sentence value proposition.

## Anti-Slop: Removing AI Tells / 去机器味

*Adapted from [stop-slop](https://github.com/hardikpandya/stop-slop) (MIT, 14k★)
with modifications for CS textbook writing.*

AI-generated text has predictable patterns — filler phrases, cliché structures,
and a "narrator from a distance" voice that undermines authority. Textbook-grade
writing must sound like a human expert, not an LLM. This section defines what to
remove and what to write instead.

### Prose Quality Scoring / 行文质量评分

Before publishing, rate each node 1–10 on five dimensions. Below **35/50**:
revise.

| Dimension | Question | CS Textbook Lens |
|-----------|----------|-----------------|
| **Directness** | Statements or announcements? | No "Here's the thing:", no "It turns out". State the fact. |
| **Precision** | Concrete or vague? | Name the specific instruction, register, flag, error code. No "The implications are significant." |
| **Trust** | Respects reader intelligence? | No "Think about it", no "obviously", no "Let that sink in." |
| **Density** | Anything cuttable? | Every paragraph adds one idea. No filler sentences. |
| **Authenticity** | Sounds human? | Active voice. Named actors. No LLM rhythmic tics. |

### Banned Phrases / 禁用短语

These add zero information. Delete them on sight.

**Throat-clearing openers:**
- "Here's the thing:", "It turns out", "The truth is", "Let me be clear"
- "Here's what/why/how [X]" → state X directly
- "I'm going to be honest", "Can we talk about"
- "The uncomfortable truth is"

**Emphasis crutches:**
- "Full stop.", "Period.", "Let that sink in."
- "Make no mistake", "This matters because", "Here's why that matters"

**Filler and hedges (kill all):**
- "really", "just", "literally", "genuinely", "honestly", "simply", "actually"
- "deeply", "truly", "fundamentally", "inherently", "inevitably"
- "interestingly", "importantly", "crucially"
- "It's worth noting", "At the end of the day", "When it comes to"
- "In a world where", "The reality is", "At its core"

**Business jargon → plain language:**

| Avoid | Use |
|-------|-----|
| Navigate (challenges) | Handle, address |
| Unpack (analysis) | Explain, examine |
| Lean into | Accept |
| Landscape | Field, situation |
| Game-changer | Significant |
| Double down | Commit |
| Deep dive | Detailed analysis |
| Moving forward | Next, from now on |
| Circle back | Return to |

**Meta-commentary (in expository prose):**
- "Hint:", "Plot twist:", "Spoiler:"
- "You already know this, but"
- "The rest of this section explains..."
- "Let me walk you through..."
- "I want to explore..."

*Exception for CS tutorials:* "In this section, we trace the malloc call"
is acceptable once per section to orient the reader. Use sparingly — one
orientation sentence, not a running commentary.

**Vague declaratives — name the specific thing or cut:**
- ❌ "The implications are significant." → ✅ "If `restrict` is violated, the
  compiler may reorder stores across the aliased region."
- ❌ "The stakes are high." → ✅ "A missed `ECHILD` check leaves zombies."
- ❌ "This is the deepest problem." → Cut entirely. Describe the problem.

**Performative emphasis:**
- "I promise", "They exist, I promise"
- "This is what X actually looks like" → Show X instead of announcing it.

### Structural Anti-Patterns / 结构反模式

**Binary contrast for manufactured drama → use only for genuine technical distinctions:**

| ❌ Slop | ✅ CS Textbook |
|---------|---------------|
| "Not because X. Because Y." | "A TLB miss is not a page fault. A TLB miss means the translation cache missed; a page fault means the PTE is invalid." |
| "The answer isn't X. It's Y." | Direct statement: "`waitpid(-1, ...)` returns -1 with ECHILD when no children exist." |
| "It's not just X but also Y." | "X AND Y." |

Rule: use contrast when it teaches a real distinction students must memorize
(TLB miss vs. page fault, blocked vs. pending). Never use it as a rhetorical
device.

**Negative listing → state directly:**
- ❌ "It wasn't a compiler bug. It wasn't a linker error. It was aliasing."
- ✅ "Aliasing caused the unexpected output."

**Dramatic fragmentation → complete sentences:**
- ❌ "Speed. Quality. Cost. Pick two. That's it. That's the tradeoff."
- ✅ Not used in CS textbooks. Write complete sentences that explain why the
  trade-off exists.

**Rhetorical setups → deliver the insight directly:**
- ❌ "What if processes could share memory without copying?" (Socratic posturing)
- ✅ "Virtual memory lets processes share physical pages through copy-on-write."

**Three-item staccato lists → vary rhythm:**
- ❌ "The compiler optimizes. The linker resolves. The allocator accounts."
- ✅ "The compiler transforms code. The linker resolves symbols into addresses.
  The allocator tracks heap space." (varying sentence length reads human.)

### False Agency / 假能动

AI loves giving inanimate things human verbs because it avoids naming the actor.
In CS textbooks, software components *do* perform actions, so the rule is more
nuanced:

| ✅ OK (real actor) | ❌ Slop (vague actor) |
|-------------------|----------------------|
| "The linker resolves symbols." | "The decision emerges from the specification." |
| "The kernel delivers the signal." | "The culture shifts toward correctness." |
| "`waitpid` blocks until the child exits." | "The conversation moves toward formal verification." |
| "The compiler hoists the invariant." | "The data tells us to use a different approach." |

**Rule:** Programs, kernels, and compilers are real actors with defined behavior.
Abstract nouns (decisions, cultures, conversations) are not. If the subject is a
concrete system component, active voice is correct. If the subject is an
abstraction, find the human or program that actually does the thing.

### Sentence-Level Rules / 句级规则

| Rule | Rationale |
|------|-----------|
| **No Wh- sentence starters in expository prose** | "What makes this hard is..." → "The constraint is..." Name the specific constraint. *Exception: Wh- questions are correct in Reader Questions sections and What This Solves headers — those are genuine questions the reader asks.* |
| **No em dashes in formal paragraphs** | Use commas, periods, or a colon. Em dashes create a breathless, bloggy rhythm. *OK in code comments and asides.* |
| **No lazy extremes** | "Every", "always", "never", "everyone", "nobody" — these are false authority signals. Replace with specifics: "every call to `malloc` that returns non-NULL" beats "every allocation." |
| **Active voice** | "The child is reaped by init" → "Init reaps the child." Every sentence has a subject doing something. |
| **Vary sentence length** | AI defaults to 18–22 word sentences in a metronomic rhythm. Mix: 4-word punch, 15-word explanation, 30-word walkthrough. |
| **One idea per paragraph** | A paragraph that starts about linker symbols and drifts to compiler flags needs splitting. |

### Before/After: Applying Anti-Slop to CS Content

**❌ Before (slop):**
> Here's the thing: understanding virtual memory is actually crucial. Not
> because the concept is complex. Because it fundamentally changes how you think
> about addresses. Let that sink in. In this section, we'll unpack the landscape
> of address translation and navigate the implications. The stakes are high.

**✅ After (textbook-grade):**
> Virtual memory gives each process its own address space. When your program
> loads from address `0x7fff`, that address is virtual — the hardware and OS
> translate it to a physical frame. The translation uses a page table, cached by
> the TLB. A TLB miss walks the page table; a page fault means the page is not
> in memory at all.

**What changed:**
- Throat-clearing gone ("Here's the thing", "It turns out").
- Emphasis crutches gone ("Let that sink in", "The stakes are high").
- Business jargon gone ("unpack the landscape", "navigate the implications").
- Hedges gone ("actually", "fundamentally").
- Binary contrast gone ("Not because X. Because Y.").
- False drama gone — the content is concrete and instructional.
- Concrete trace appears: address → TLB → page table → page fault.

**❌ Before (slop):**
> The linker resolves names. It's not a type checker, but actually a name
> resolver. Here's what I mean: two strong definitions of the same symbol are
> a link error. The implications are significant.

**✅ After (textbook-grade):**
> The linker resolves names, not types. Two strong definitions of the same
> symbol produce a link error. A strong `int x` and a weak `double x` link
> silently, but reading `x` as a `double` where the strong definition has
> `int` is undefined behavior.

## Phrasebook: Precise Technical English / 精确技术英文速查

Quick lookup. For detailed rules, see the Anti-Slop section above.

| Vague / Informal | Textbook-Grade |
|------------------|----------------|
| "basically" | Remove. State the rule directly. |
| "a bunch of" | "several", "multiple", or specify the count. |
| "things" | Name the thing: "objects", "symbols", "processes", "blocks". |
| "does something" | "transforms", "resolves", "translates", "allocates". |
| "it's like" | "The conceptual model is:" — then the model. |
| "just" | Remove ("just add restrict", "just call fork"). Undermines precision. |
| "obviously", "of course" | Remove. If it's obvious, the reader who doesn't get it feels stupid. |
| "magic" | Never. Everything has an explanation. |
| "somehow" | Never. Trace the path. |
| "in order to" | "To" — shorter, clearer. |
| "basically what happens is" | State what happens. |
| "a lot of" | Quantify or remove. |
| Every paragraph ending with the same rhythm | Vary sentence length. See Sentence-Level Rules above. |
| "navigate", "unpack", "lean into", "deep dive" | "handle", "explain", "accept", "detailed analysis". See business jargon table above. |

## Phrasebook: Chinese ↔ English Alignment

When writing bilingual content (Standard A pattern):

| Good Practice | Bad Practice |
|---------------|--------------|
| Chinese explains the same idea with equivalent depth. | Chinese is a loose unrelated summary. |
| Chinese follows the English structure but uses natural Chinese expression. | Chinese is a word-for-word translation that reads unnaturally. |
| Technical terms in Chinese are consistent within one node. | Chinese uses different translations for the same term. |
| English comes first (the primary teaching language), Chinese follows. | Mixed-language paragraphs with no primary voice. |

## When to Create a New Node vs. Expand

```
A concept needs its own dedicated node when:
- It has its own runnable example that would clutter the current node.
- It will be reused as a prerequisite by multiple other nodes.
- It requires its own Practice Problems section.
- It is referenced by name in 2+ other nodes' prerequisites or related.

Otherwise, define the term locally and link to the nearest existing node.
```

## References

- *Computer Systems: A Programmer's Perspective*, 3rd ed. (Bryant & O'Hallaron)
  — the structural model for this reference.
- *The C Programming Language*, 2nd ed. (Kernighan & Ritchie) — model for
  concise, precise technical prose.
- *TCP/IP Illustrated*, Vol. 1 (Stevens) — model for protocol trace analysis.
- *Structure and Interpretation of Computer Programs* (Abelson & Sussman) —
  model for building from first principles.
- [stop-slop](https://github.com/hardikpandya/stop-slop) (Hardik Pandya, MIT,
  14k★) — the Anti-Slop section is adapted from this skill. It defines the
  phrase and structure patterns that make AI prose sound machine-generated and
  the scoring system for prose quality.
- [awesome-technical-writing](https://github.com/BolajiAyodeji/awesome-technical-writing)
  (Bolaji Ayodeji, 2.3k★) — curated index of technical writing courses, style
  guides, books, and tools. Use when looking for external writing resources.
- [content-standards.md](content-standards.md) — the app's Standard A and Q
  structural requirements. This reference supplements those standards with
  textbook-grade depth and pedagogical patterns.
- [node-schema.md](node-schema.md) — frontmatter schema and slug rules.
