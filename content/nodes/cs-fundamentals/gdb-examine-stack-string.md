---
title: "GDB examine stack string / GDB 查看栈上字符串"
area: cs-fundamentals
track: gdb-debugging
order: 60
status: seed
visibility: core
tags: [gdb, stack, string, memory, c]
prerequisites: [c-memory-basics, gdb-examine-memory]
related: [gdb-examine-memory, gdb-basics]
sources:
  - https://sourceware.org/gdb/current/onlinedocs/gdb.html/Memory.html
summary: "Use `x/s` with pointer arithmetic to examine a C string stored at an offset from the stack pointer."
---

# GDB examine stack string / GDB 查看栈上字符串

## What This Solves / 解决什么问题

English: When a question says a string is stored 8 bytes after the stack pointer, use `x/s` at `$sp + 8`.

中文：当题目说字符串存在栈指针之后 8 字节的位置，就从 `$sp + 8` 开始用 `x/s` 看字符串。

## Core Commands or Code / 核心命令或代码

Portable-style answer:

```gdb
x/s $sp + 8
```

Common x86-64 answer:

```gdb
x/s $rsp + 8
```

Small C example:

```c
char message[] = "hello";
```

GDB displays a string by reading bytes until it finds `\0`.

## Plain Explanation / 通俗解释

English: `x/s` means "examine memory as a string." `$sp + 8` means "start 8 bytes after the stack pointer."

中文：`x/s` 的意思是“把内存当字符串看”。`$sp + 8` 的意思是“从栈指针往后 8 字节的位置开始”。

## Common Mistakes / 常见错误

- English: Using `x/8s`; that means eight strings, not one string eight bytes away.
- 中文：误写成 `x/8s`。这表示看 8 个字符串，不是偏移 8 字节。
- English: Forgetting that a C string must be null-terminated.
- 中文：忘记 C 字符串必须以 `\0` 结束。

## Quick Recall / 快速记忆

English: Offset belongs to the address: `x/s $sp + 8`.

中文：偏移量写在地址上：`x/s $sp + 8`。

## Suggested Next / 下一步

Review `gdb-examine-memory` to understand `x/nfu address`.
