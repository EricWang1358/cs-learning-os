---
title: "Pointer Lifetime and Ownership"
area: cs-fundamentals
track: systems-memory
status: demo
visibility: practice
difficulty: easy
tags: [c, pointers, memory, ownership]
linked_nodes: [c-memory-and-pointers]
sources: []
summary: "Distinguish an address from a valid object with a defined lifetime."
---

# Pointer Lifetime and Ownership

## Prompt

Why is returning the address of a local C variable unsafe, and what ownership rule should a function document when it returns heap memory?

## Answer

A local variable normally lives in the function's stack frame, which stops being valid when the function returns; its address becomes dangling even if it still appears to contain the old value. If a function returns heap memory, it should document that the caller owns the returned allocation and is responsible for releasing it exactly once.

## Explanation

Memory bugs are often lifetime bugs rather than arithmetic bugs. A pointer can hold a numerically plausible address while the object it once named no longer exists. Clear ownership prevents two common failures: nobody frees a live allocation, causing a leak, or two components both free it, causing use-after-free behavior.
