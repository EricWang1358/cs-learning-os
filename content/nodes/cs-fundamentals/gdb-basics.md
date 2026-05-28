---
title: "GDB Basics / GDB 基础"
area: cs-fundamentals
status: seed
visibility: core
tags: [gdb, debugging, c, cli]
prerequisites: [c-language-characteristics, c-memory-basics]
related: [debugging-levels-vscode-python-vs-gdb, gdb-disassemble, gdb-stepi, gdb-examine-memory]
sources:
  - https://sourceware.org/gdb/current/onlinedocs/gdb.html/
summary: "Use GDB to inspect a running C program, including code, registers, memory, and execution steps."
---

# GDB Basics / GDB 基础

## What This Solves / 解决什么问题

English: GDB lets you stop a C program, inspect what it is doing, and move through execution one step at a time.

中文：GDB 可以让你暂停 C 程序，查看它正在做什么，并一步一步执行。

## Core Commands or Code / 核心命令或代码

```gdb
break main
run
info registers
disassemble
stepi
x/20xw $pc
```

## Plain Explanation / 通俗解释

English: Think of GDB as a microscope for compiled C programs. A normal editor shows your C source; GDB can show the source, the assembly instructions, registers, stack memory, and raw bytes.

中文：可以把 GDB 当成“编译后 C 程序的显微镜”。普通编辑器主要看 C 源码；GDB 既能看源码，也能看汇编指令、寄存器、栈内存和原始字节。

## Reader Questions / 读者追问

Question: How is this different from adding a breakpoint in VSCode while debugging Python?

Answer: Python debugging usually stays at the source/object level. GDB can go below the source line and inspect the machine-level result of compilation.

问题：这和我在 VSCode 里调 Python 加断点有什么区别？

回答：Python 调试通常停留在源码和对象层。GDB 可以继续往下看，看到 C 编译后的机器层结果。

## Common Mistakes / 常见错误

- English: Running without `-g`, then wondering why source-level information is weak.
- 中文：编译时没加 `-g`，然后疑惑为什么调试信息很少。
- English: Forgetting that optimized code can reorder or remove variables.
- 中文：忘记优化编译可能重排代码或优化掉变量。

## Quick Recall / 快速记忆

English: Compile with `-g -O0` for friendly debugging.

中文：适合新手调试的编译方式是 `-g -O0`。

## Suggested Next / 下一步

Learn `debugging-levels-vscode-python-vs-gdb`, then continue with `gdb-disassemble`, `gdb-stepi`, and `gdb-examine-memory`.
