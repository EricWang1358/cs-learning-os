---
title: "Sliding Window Invariant"
area: algorithms
track: sequence-patterns
status: demo
visibility: practice
difficulty: medium
tags: [sliding-window, string, invariant]
linked_nodes: [two-pointers]
sources: []
summary: "Explain how an invariant turns a substring scan into a linear algorithm."
---

# Sliding Window Invariant

## Prompt

For the longest substring without repeated characters, what invariant should the window maintain, and when should the left pointer move?

## Answer

Maintain that every character inside the current window is unique. Extend the right boundary for each new character. If that character already appears inside the window, move the left boundary just past its previous occurrence, then update the stored last-seen position and the best window length.

## Explanation

The left boundary never moves backward, so every character enters and leaves the active interval at most once. The map of last-seen positions tells us exactly how far to repair the window instead of removing characters one by one. This invariant explains both correctness and the linear time bound.
