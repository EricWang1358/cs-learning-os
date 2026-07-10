---
title: "Dynamic Programming Basics"
area: algorithms
track: optimization-patterns
order: 40
status: demo
visibility: core
tags: [dynamic-programming, recurrence, optimization]
prerequisites: []
related: [binary-search, two-pointers]
sources: []
summary: "Turn overlapping subproblems into a recurrence, base cases, and a compact state table."
---

# Dynamic Programming Basics

## When To Reach For It

Dynamic programming helps when a problem has overlapping subproblems and an optimal solution can be assembled from optimal smaller solutions. Common signals are "best", "number of ways", "minimum cost", and a choice repeated over a sequence.

## A Reliable Construction Order

1. Define the state in one sentence, including exactly what information it represents.
2. Write the transition from smaller states to the current state.
3. Set base cases that make the first transition valid.
4. Choose an evaluation order so required states already exist.
5. Reduce memory only after the full table is correct.

## Example: House Robber

Let `best[i]` be the maximum value obtainable from the first `i` houses. At each house, either skip it or take it and combine it with the best valid prefix.

```python
def rob(values):
    previous_two = 0
    previous_one = 0
    for value in values:
        previous_two, previous_one = previous_one, max(previous_one, previous_two + value)
    return previous_one
```

## Common Mistakes

Do not begin with a table shape. Begin with the meaning of one cell. A recurrence that sounds vague usually hides an off-by-one error, an invalid overlap, or a missing base case.
