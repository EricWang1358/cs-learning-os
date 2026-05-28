---
title: "GDB stepi / GDB 单条机器指令执行"
area: cs-fundamentals
track: gdb-debugging
order: 40
status: seed
visibility: core
tags: [gdb, stepi, assembly, debugging]
prerequisites: [gdb-basics, gdb-disassemble]
related: [gdb-disassemble, gdb-examine-memory, debugging-levels-vscode-python-vs-gdb]
sources:
  - https://sourceware.org/gdb/current/onlinedocs/gdb.html/Continuing-and-Stepping.html
summary: "Use `stepi` to execute exactly one machine instruction at the current program counter."
---

# GDB stepi / GDB 单条机器指令执行

## What This Solves / 解决什么问题

English: `stepi` is for the moment when you are looking at assembly and want to execute the next machine instruction only.

中文：当你正在看汇编，并且只想让程序执行下一条机器指令时，就用 `stepi`。

## Core Commands or Code / 核心命令或代码

```gdb
disassemble
stepi
stepi 5
si
si 5
```

Meaning:

```text
stepi      execute one machine instruction
stepi 5    execute five machine instructions
si         short form of stepi
```

## Plain Explanation / 通俗解释

English: The CPU has an instruction pointer, often called the program counter. It points at the next machine instruction. `stepi` executes the instruction at that address, then moves to the next instruction.

中文：CPU 有一个“指令指针”，也可以叫 program counter。它指向下一条将要执行的机器指令。`stepi` 会执行这个地址上的那一条指令，然后移动到下一条。

English: So "one small step" does not mean one C line, one Python statement, or one clock cycle. It means one decoded CPU instruction such as `mov`, `add`, `cmp`, `call`, or `ret`.

中文：所以“一小步”不是一行 C 代码、不是一条 Python 语句，也不是一个 CPU 时钟周期。它指的是一条 CPU 指令，例如 `mov`、`add`、`cmp`、`call`、`ret`。

## Reader Questions / 读者追问

Question: How big is one `stepi` step?

Answer: One `stepi` step is one machine instruction at the current instruction pointer. On x86-64, instructions can have different byte lengths. A `mov` instruction might occupy a few bytes, while another instruction may occupy more. GDB steps by instruction boundary, not by byte count.

问题：`stepi` 的“一步”到底多大？

回答：`stepi` 的一步是当前指令指针指向的一条机器指令。在 x86-64 上，不同指令的字节长度不一定一样。某条 `mov` 可能占几个字节，另一条指令可能更长。GDB 按“指令边界”走，不是按固定字节数走。

Question: Why can one C line need many `stepi` operations?

Answer: One C line can compile into several instructions. For example, `total = a + b` may require loading values, adding them, and storing the result.

问题：为什么一行 C 代码可能要按很多次 `stepi`？

回答：因为一行 C 代码可能编译成多条机器指令。例如 `total = a + b` 可能需要加载值、执行加法、再保存结果。

## Common Mistakes / 常见错误

- English: Using `stepi` before looking at `disassemble`; then you do not know what instruction you are stepping through.
- 中文：没先看 `disassemble` 就用 `stepi`，结果不知道自己正在走哪条指令。
- English: Thinking `stepi 5` means five C lines.
- 中文：误以为 `stepi 5` 是执行五行 C 代码。
- English: Thinking one instruction always equals one clock cycle.
- 中文：误以为一条指令总是等于一个时钟周期。

## Quick Recall / 快速记忆

English: `stepi` = execute one assembly instruction, not one source line.

中文：`stepi` = 执行一条汇编/机器指令，不是一行源码。

## Suggested Next / 下一步

Pair `gdb-stepi` with `gdb-disassemble`: first see the instruction, then step through it.
