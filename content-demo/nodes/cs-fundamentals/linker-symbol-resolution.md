---
slug: linker-symbol-resolution
title: "Linker Symbol Resolution / 链接器符号解析"
area: cs-fundamentals
track: intro-systems
level: intermediate
status: seed
visibility: core
tags: [linker, object-files, symbols, strong, weak, static, extern]
prerequisites: []
related: [compiler-optimization-and-aliasing]
sources:
  - https://refspecs.linuxfoundation.org/elf/gabi4+/contents.html
  - https://sourceware.org/binutils/docs/ld/
summary: "Apply strong, weak, and local linkage rules to predict duplicate-definition errors and silent type mismatches."
---

# Linker Symbol Resolution / 链接器符号解析

## What This Solves / 解决什么问题

Compilation produces per-module objects; linking combines their symbols into a
single address space. The key is to classify each declaration before asking
which definition wins。

## Core Idea / 核心概念

- An initialized global object or non-inline function is normally a **strong** definition.
- An uninitialized tentative global definition is commonly treated as **weak** under the traditional C toolchain model; modern compiler flags can change this, so check the ABI and toolchain.
- `static` gives a symbol internal linkage, so it is local to its object file.
- `extern` declares a definition elsewhere; it does not create a new definition.

Two strong definitions are a link error. One strong and one weak definition
usually selects the strong symbol. Multiple weak definitions may be accepted,
but the selected type and layout are not made safe by that choice.

## Plain Explanation / 通俗解释

Think of the linker as resolving names, not validating your design. If one file
declares `x` as an `int` and another provides a `double`, a successful link does
not make accesses compatible. Use one authoritative declaration in a header and
compile all users against it。

## Practice / 应用

```c
/* api.h */
extern int counter;
void step(void);

/* module.c */
int counter = 0;
void step(void) { ++counter; }
```

The header shares declarations; exactly one module owns each external
definition. A file-scope `static int counter` would be a different private
symbol, even if its spelling matched.

## Common Mistakes / 常见错误

- Counting a block-scope local variable as a multiply-defined global symbol.
- Assuming `extern` allocates storage.
- Treating successful weak-symbol resolution as proof of type compatibility.

## Quick Recall / 快速记忆

**Strong + strong: error; strong + weak: strong wins; static: private name.**

## Suggested Next / 下一步

Read [malloc utilization and block layout](malloc-utilization-and-block-layout.md)
to move from name/link costs to runtime space costs.
