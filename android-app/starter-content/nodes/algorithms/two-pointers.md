---
title: "Two Pointers and Sliding Windows"
area: algorithms
track: sequence-patterns
order: 30
status: demo
visibility: core
tags: [array, string, two-pointers, sliding-window]
prerequisites: []
related: [binary-search, graph-traversal]
sources: []
summary: "Maintain a moving interval so each element is processed a small, predictable number of times."
---

# Two Pointers and Sliding Windows

## Core Idea

Use two indexes to maintain a useful range instead of restarting a nested loop for every position. The pattern is usually linear because each pointer only moves forward through the input.

## Sorted Arrays

For a sorted array, put one pointer at each end. Compare the current sum with the target: move the left pointer to increase the sum and the right pointer to decrease it. This removes a whole set of impossible pairs at each step.

## Sliding Window

For a contiguous subarray or substring, expand the right boundary to include new data. When the window violates an invariant, advance the left boundary until it is valid again.

```python
def longest_unique(text):
    last_seen = {}
    left = 0
    best = 0
    for right, char in enumerate(text):
        if char in last_seen and last_seen[char] >= left:
            left = last_seen[char] + 1
        last_seen[char] = right
        best = max(best, right - left + 1)
    return best
```

## Interview Check

State the invariant aloud: for example, "the current window contains no duplicate characters." Then explain which operation can break it and why moving the left boundary restores it. This is the proof that the algorithm is linear rather than a disguised quadratic loop.
