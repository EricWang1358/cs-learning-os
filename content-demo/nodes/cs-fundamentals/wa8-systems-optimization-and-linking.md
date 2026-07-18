---
slug: wa8-systems-optimization-and-linking
title: "WA8: Optimization, Linking, and Allocation / 优化、链接与分配"
area: cs-fundamentals
track: 15-213-written-assignments
level: intermediate
status: seed
visibility: support
tags: [csapp, compiler-optimization, linking, malloc, utilization]
prerequisites: [compiler-optimization-and-aliasing, linker-symbol-resolution, malloc-utilization-and-block-layout]
related: []
sources:
  - https://en.cppreference.com/w/c/language/object.html
  - https://refspecs.linuxfoundation.org/elf/gabi4+/contents.html
  - https://man7.org/linux/man-pages/man3/malloc.3.html
summary: "A compact systems-performance map connecting compiler legality, linker symbol rules, and allocator space overhead."
---

# WA8: Optimization, Linking, and Allocation / 优化、链接与分配

## What This Solves / 解决什么问题

WA8 mixes three layers that are easy to conflate: what a compiler is allowed to
change, how object-file symbols become one program, and how an allocator turns
requested payloads into heap blocks. This root keeps those layers separate while
showing their shared theme: preserve observable behavior while accounting for
hidden cost。

## Core Idea / 核心概念

Use a layer-first diagnosis:

- **Compiler:** Is a transformation legal under aliasing and side-effect rules?
- **Linker:** Which definition wins, and is a duplicate strong definition fatal?
- **Allocator:** How many bytes are metadata, padding, free space, or payload?

## Concept Map / 概念导航

- [Compiler optimization limits and aliasing](compiler-optimization-and-aliasing.md) covers the as-if rule, `restrict`, and opaque calls.
- [Linker symbol resolution](linker-symbol-resolution.md) covers strong, weak, static, and extern symbols.
- [Malloc utilization and block layout](malloc-utilization-and-block-layout.md) turns heap traces into a utilization ratio.

## Common Confusions / 常见混淆

- A compiler optimization cannot repair an ABI or linker type mismatch.
- A linker resolving a symbol does not prove that two declarations have the same type or layout.
- Utilization is a space ratio, not the number of successful `malloc` calls.

## Suggested Next / 下一步

Study the three child nodes independently: optimization legality, symbol
resolution, then allocator block accounting.
