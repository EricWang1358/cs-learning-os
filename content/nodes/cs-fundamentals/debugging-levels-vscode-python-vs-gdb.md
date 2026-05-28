---
title: "Debugging Levels: VSCode Python vs GDB / 调试层级：VSCode Python 断点 vs GDB"
area: cs-fundamentals
status: seed
visibility: core
tags: [debugging, python, gdb, vscode, c, abstraction]
prerequisites: [c-language-characteristics]
related: [gdb-basics, gdb-disassemble, gdb-stepi, c-memory-basics]
sources: []
summary: "Compare high-level Python breakpoint debugging with low-level GDB debugging of compiled C programs."
---

# Debugging Levels: VSCode Python vs GDB / 调试层级：VSCode Python 断点 vs GDB

## What This Solves / 解决什么问题

English: This explains why GDB feels different from setting a breakpoint in VSCode while debugging Python.

中文：这个节点解释为什么 GDB 和你在 VSCode 里给 Python 打断点的感觉完全不一样。

## Core Commands or Code / 核心命令或代码

Python breakpoint style:

```python
def add(a, b):
    total = a + b
    return total
```

You stop on a source line and inspect Python objects like `a`, `b`, and `total`.

C/GDB style:

```c
int add(int a, int b) {
    int total = a + b;
    return total;
}
```

```gdb
break add
run
disassemble
info registers
stepi
```

## Plain Explanation / 通俗解释

English: VSCode Python debugging is usually source-level and object-level. You see variables in a friendly runtime.

中文：VSCode 调 Python 通常是源码层、对象层。你看到的是比较友好的运行时变量。

English: GDB can debug source lines too, but its special power is going lower: registers, stack addresses, raw memory, and machine instructions.

中文：GDB 也能按源码行调试，但它真正厉害的地方是能往下看：寄存器、栈地址、原始内存、机器指令。

## Reader Questions / 读者追问

Question: Is `stepi` just a smaller version of clicking "Step Over" in VSCode?

Answer: Not exactly. VSCode Python stepping usually moves by Python source statements. `stepi` moves by one CPU instruction after C has been compiled.

问题：`stepi` 是不是就像 VSCode 里的 Step Over，只是更小？

回答：不完全是。VSCode 调 Python 通常按 Python 源码语句走；`stepi` 是 C 编译后按一条 CPU 指令走。

## Common Mistakes / 常见错误

- English: Expecting every C source line to match exactly one machine instruction.
- 中文：以为每一行 C 代码都刚好对应一条机器指令。
- English: Expecting GDB to hide memory and registers the way a high-level debugger often does.
- 中文：期待 GDB 像高级语言调试器一样把内存和寄存器都藏起来。

## Quick Recall / 快速记忆

English: Python debugger: source/object level. GDB: source plus machine level.

中文：Python 调试器偏源码/对象层；GDB 可以看到源码层，也可以看到机器层。

## Suggested Next / 下一步

Read `gdb-disassemble` to see compiled instructions, then `gdb-stepi` to execute them one by one.
