---
title: "Binary Search Boundary"
area: algorithms
track: search-patterns
status: demo
visibility: practice
difficulty: easy
tags: [binary-search, monotonicity]
linked_nodes: [binary-search]
sources: []
summary: "Explain the invariant that makes a binary search find a first valid answer."
---

# Binary Search Boundary

## Prompt

You need the first index in a sorted array whose value is greater than or equal to a target. What invariant should the search maintain, and why must the successful branch continue searching left?

## Answer

Maintain a candidate range that can still contain the first valid index. When `array[mid]` is less than the target, every index through `mid` is invalid, so move the left boundary right. When it is greater than or equal to the target, record `mid` as a candidate and continue left, because an earlier valid index may exist.

## Explanation

The key is not finding any valid value, but finding the boundary between invalid and valid values. The predicate `array[index] >= target` is false and then true, so it is monotonic. Moving left after a valid midpoint preserves the possibility of a better answer; returning immediately would incorrectly return a later duplicate.
