---
title: "Binary Search"
area: algorithms
track: search-patterns
order: 10
status: demo
visibility: core
tags: [search, monotonicity, optimization]
prerequisites: []
related: [x86-64-addressing-and-leaq]
sources: []
summary: "Use a monotonic condition to locate a boundary in logarithmic time."
---

# Binary Search

## Why It Matters

Binary search is a pattern for finding a boundary. Sorted arrays are the classic example, but the deeper idea is monotonicity: once a condition becomes true, it stays true.

## Core Idea

Keep a search range. Check the middle. Discard the half that cannot contain the answer.

```python
def binary_search(arr, target):
    left = 0
    right = len(arr) - 1

    while left <= right:
        mid = left + (right - left) // 2
        if arr[mid] == target:
            return mid
        if arr[mid] < target:
            left = mid + 1
        else:
            right = mid - 1

    return -1
```

## Plain Explanation

Imagine guessing a number from 1 to 100. If the guess is too small, everything below it is useless. If it is too large, everything above it is useless.

## Common Mistakes

- Using `(left + right) // 2` in languages where integer overflow matters.
- Forgetting to move past `mid`, which can cause an infinite loop.
- Applying binary search when the condition is not monotonic.
