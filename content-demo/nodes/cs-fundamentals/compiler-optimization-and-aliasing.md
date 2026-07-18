---
slug: compiler-optimization-and-aliasing
title: "Compiler Optimization Limits and Aliasing / 编译器优化边界与别名"
area: cs-fundamentals
track: intro-systems
level: intermediate
status: seed
visibility: core
tags: [compiler, optimization, aliasing, restrict, volatile, strlen]
prerequisites: [c-memory-and-pointers]
related: [linker-symbol-resolution, malloc-utilization-and-block-layout]
sources:
  - https://en.cppreference.com/w/c/language/object.html
  - https://en.cppreference.com/w/c/language/restrict.html
summary: "Recognize when aliasing, opaque calls, volatile accesses, or observable behavior prevent a legal optimization."
---

# Compiler Optimization Limits and Aliasing / 编译器优化边界与别名

## What This Solves / 解决什么问题

Compiler optimization is constrained by the as-if rule: the transformed program
must preserve observable behavior. This note teaches how to spot the evidence
that blocks an optimization instead of memorizing a list of flags。

## Core Idea / 核心概念

Four recurring limits are:

1. **Memory aliasing:** two pointers may designate the same object.
2. **Opaque side effects:** a function call may read or mutate state the compiler cannot see.
3. **Volatile accesses:** each access is observable and must occur as specified.
4. **The as-if rule:** I/O, ordering, and other externally visible behavior must remain equivalent.

For a loop that writes through `s[i]` while also calling `strlen(s)`, the write
may change the terminating byte. Hoisting `strlen` is therefore not automatically legal.

## Plain Explanation / 通俗解释

Ask: **Could another name observe this memory or side effect between these two
operations?** If yes, the compiler must preserve that possibility. When the
programmer can prove output pointers do not overlap, local accumulators and a
`restrict` contract expose more freedom without changing the algorithm. The
[C memory and pointers](c-memory-and-pointers.md) node supplies the pointer and
object vocabulary used here.

## Practice / 应用

```c
void sum_and_product(const long *restrict a, int n,
                     long *restrict sum_out,
                     long *restrict prod_out) {
    long sum = 0, product = 1;
    for (int i = 0; i < n; ++i) {
        sum += a[i];
        product *= a[i];
    }
    *sum_out = sum;
    *prod_out = product;
}
```

The local variables avoid repeated stores, while `restrict` is a promise that
the pointed-to regions do not overlap. Violating that promise is a program bug,
not a request for a safer optimization.

## Common Mistakes / 常见错误

- Calling every loop-invariant-looking expression safe to hoist.
- Adding `restrict` without proving non-overlap for the whole relevant lifetime.
- Treating `volatile` as a general performance or thread-safety primitive.

## Quick Recall / 快速记忆

**No proof of independence, no aggressive reordering.**

## Suggested Next / 下一步

Read [linker symbol resolution](linker-symbol-resolution.md) for the next layer:
legal machine code still needs compatible symbols before a program can run.
