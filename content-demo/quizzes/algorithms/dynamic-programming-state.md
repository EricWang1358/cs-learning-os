---
title: "Dynamic Programming State"
area: algorithms
track: optimization-patterns
status: demo
visibility: practice
difficulty: medium
tags: [dynamic-programming, recurrence]
linked_nodes: [dynamic-programming-basics]
sources: []
summary: "Define a state before writing a recurrence or optimizing space."
---

# Dynamic Programming State

## Prompt

Before coding a dynamic-programming solution, what must a state definition say, and why should memory optimization wait until after a full recurrence works?

## Answer

A state definition must say exactly which subproblem one table entry represents, including its input prefix, position, or constraint. Memory optimization should wait because a complete table makes dependencies and base cases visible. Once the recurrence is correct, you can keep only the earlier states that the next transition actually reads.

## Explanation

Many DP bugs come from a state whose meaning changes halfway through the solution. A full table is a reasoning tool, not wasted work: it exposes evaluation order and off-by-one cases. Compressing it too early can overwrite a value that the recurrence still needs, producing subtle but plausible wrong answers.
