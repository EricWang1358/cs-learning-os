---
title: "Binary Search"
area: algorithms
status: seed
visibility: core
tags: [search, monotonicity, optimization]
prerequisites: []
related: [graph-traversal]
sources:
  - https://cp-algorithms.com/num_methods/binary_search.html
summary: "Use a monotonic condition to locate a boundary in logarithmic time."
---

# Binary Search

## Why It Matters

Binary search is not only for sorted arrays. It is a general method for finding a boundary when the answer space has a monotonic true/false structure.

## Core Idea

If a predicate changes from false to true only once, repeatedly test the middle and discard half of the search space.

## When To Use It

- Searching in a sorted list.
- Finding the minimum feasible answer.
- Optimizing over an integer or real-valued answer space.

## Common Confusions

- The hard part is often designing the predicate, not writing the loop.
- Boundary conventions matter more than they first appear.

## Suggested Next

- Practice "binary search on answer".
- Compare with two pointers when the condition is not globally monotonic.
