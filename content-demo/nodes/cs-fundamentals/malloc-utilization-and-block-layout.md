---
slug: malloc-utilization-and-block-layout
title: "Malloc Utilization and Block Layout / Malloc 利用率与块布局"
area: cs-fundamentals
track: intro-systems
level: intermediate
status: seed
visibility: core
tags: [malloc, allocator, alignment, headers, footers, coalescing, utilization]
prerequisites: [c-memory-and-pointers]
related: [compiler-optimization-and-aliasing]
sources:
  - https://man7.org/linux/man-pages/man3/malloc.3.html
  - https://gee.cs.oswego.edu/dl/html/malloc.html
summary: "Compute allocator utilization by separating live payload bytes from alignment, metadata, minimum-block, and heap-extension overhead."
---

# Malloc Utilization and Block Layout / Malloc 利用率与块布局

## What This Solves / 解决什么问题

Allocator questions become mechanical when every block is decomposed into
payload, header/footer metadata, alignment padding, and free remainder. The
headline metric is usually:

```text
utilization = live allocated payload bytes / total heap bytes obtained
```

The denominator includes bytes requested from the underlying heap, not only
currently allocated payloads. The [C memory and pointers](c-memory-and-pointers.md)
node explains the pointer/object terms used by this accounting.

## Core Idea / 核心概念

For each allocation:

1. Round the request up to satisfy alignment and metadata requirements.
2. Enforce the minimum block size; tiny requests can still consume a full block.
3. Apply the allocator's placement policy to the current free list. WA8 uses
   **first-fit**: choose the first free block in list order that is large enough.
4. When freeing, account for immediate coalescing and whether a footer remains.
5. Add a new chunk only when no usable block exists, then recompute live payload and heap totals.

Removing footers from allocated blocks or supporting smaller mini-blocks can
improve utilization, but it also changes how boundary information and free
blocks are represented. The trade-off is a layout invariant, not a free win.

## Plain Explanation / 通俗解释

`malloc(1)` asks for one useful byte, but the allocator must still buy enough
space to find the block later and return an aligned pointer. Measure the bytes
that the caller can use separately from the bytes the allocator needs to manage
the heap.

## Practice / 应用

Make a trace table with columns `operation`, `chosen block`, `split/coalesce`,
`heap bytes`, and `live payload`. This exposes whether a low utilization result
comes from internal fragmentation, external fragmentation, or an oversized
chunk extension.

## Common Mistakes / 常见错误

- Using the sum of all historical `malloc` requests as live payload.
- Forgetting alignment padding or the minimum block size.
- Counting a freed block as live payload after it has been coalesced.
- Comparing layouts without keeping the same chunk-extension policy.

## Quick Recall / 快速记忆

**Payload is numerator; obtained heap is denominator; layout rules explain both.**

## Suggested Next / 下一步

Connect this accounting method to [compiler optimization limits and aliasing](compiler-optimization-and-aliasing.md)
when reasoning about performance: fewer instructions do not automatically mean
a smaller heap footprint.
