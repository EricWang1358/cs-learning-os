---
title: "GDB examine memory / GDB 查看内存"
area: cs-fundamentals
track: gdb-debugging
order: 50
status: seed
visibility: core
tags: [gdb, memory, x-command, c, debugging]
prerequisites: [c-memory-basics, gdb-basics]
related: [gdb-examine-stack-string, gdb-disassemble, gdb-stepi]
sources:
  - https://sourceware.org/gdb/current/onlinedocs/gdb.html/Memory.html
summary: "Use GDB's `x/nfu address` command to examine memory with a count, format, unit size, and start address."
---

# GDB examine memory / GDB 查看内存

## What This Solves / 解决什么问题

English: The `x` command examines raw memory. It is the command you use when a question asks for words, bytes, strings, or values starting at an address.

中文：`x` 命令用来查看原始内存。题目要求从某个地址开始看 word、byte、字符串或数值时，就用它。

## Core Commands or Code / 核心命令或代码

General form:

```gdb
x/nfu address
```

Where:

```text
n = count       how many units
f = format      x hex, d decimal, s string, i instruction
u = unit size   b byte, h halfword, w word, g giant word
```

Exam command for the first 20 four-byte words starting at the current stack address:

```gdb
x/20xw $sp
```

Common x86-64 spelling:

```gdb
x/20xw $rsp
```

## Plain Explanation / 通俗解释

English: Read `x/20xw $sp` as: examine 20 units, display in hex, each unit is a word, starting at the stack pointer.

中文：把 `x/20xw $sp` 读成：从栈指针开始，看 20 个单位，用十六进制显示，每个单位是一个 word。

## Common Mistakes / 常见错误

- English: Forgetting that `w` means a 4-byte word in GDB memory display.
- 中文：忘记 GDB 里的 `w` 通常表示 4 字节 word。
- English: Using `$sp` in a course that expects x86-64 `$rsp`, or vice versa.
- 中文：课程要 x86-64 时写 `$sp`，或者题目用通用写法时不知道 `$rsp` 是具体架构寄存器。

## Quick Recall / 快速记忆

English: `x/20xw $sp` = 20 hex words from stack pointer.

中文：`x/20xw $sp` = 从栈指针开始看 20 个十六进制 word。

## Suggested Next / 下一步

Use `gdb-examine-stack-string` for string-specific stack questions.
