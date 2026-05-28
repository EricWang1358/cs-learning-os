# Curation Rules

The map should be selective at the surface and generous underneath.

## Visibility

### core

Use for ideas that are fundamental, reused often, or form prerequisites for many other nodes.

Examples:
- binary search
- graph traversal
- dynamic programming state design
- memory hierarchy
- HTTP request lifecycle

### support

Use for ideas that are useful but not central every week.

Examples:
- Fenwick tree variants
- OAuth refresh token flow
- amortized analysis examples

### archive

Use for rare, niche, low-confidence, or low-frequency material that should still be searchable.

Examples:
- contest trick used in one problem family
- one-off library configuration
- tutorial with unclear long-term value

## Promotion

Promote a node when:
- it appears in multiple projects or problems
- the learner repeatedly asks about it
- it blocks progress in a core area
- it explains several other nodes

## Splitting

Split a node when:
- it mixes multiple unrelated ideas
- it has more than three substantial sections
- its tags point to different top-level areas
- it cannot produce a clear one-sentence summary

## Reader Questions

When the learner asks a question while reading, decide whether to update the source node or create a new node.

Update the source node when:
- the question clarifies one sentence, command, or metaphor already in that node
- the answer is only useful in that node's local context
- the question can become a short "Reader Question" or "Plain Explanation" subsection

Create a new node when:
- the question reveals a reusable prerequisite
- the answer would be useful from several other nodes
- the question compares two domains or tools
- the explanation needs its own examples, links, or practice path

Default to updating the source node first. Create a new node only when the concept will likely be linked again.

## Suggested Next

Use reasoning over tags. Good suggestions include:
- a prerequisite the learner lacks
- a sibling concept often confused with the current one
- a project where the idea becomes real
- a harder extension that naturally follows

Keep suggestions short. Three to five links are enough.

## Quiz Items

Create or update a quiz item when:
- the user provides a concrete exam-style question
- the content is best practiced before reading the explanation
- the item can become part of fixed review or daily review later

Do not replace knowledge nodes with quiz items. Use both layers:
- knowledge node: teaches the concept
- quiz item: tests whether the learner can apply it

Create or update a knowledge node alongside the quiz when:
- the solution needs a concept that is not yet explained clearly
- the same misunderstanding will appear in many quiz items
- the quiz exposes a missing prerequisite

Default classification:
- screenshots of specific questions become Standard Q quiz items
- conceptual questions about why something works become Standard A knowledge nodes or reader-question updates
