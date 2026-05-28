---
title: "x86-64 Registers / x86-64 寄存器"
area: cs-fundamentals
track: x86-64-assembly
order: 10
status: seed
visibility: core
tags: [assembly, x86-64, registers, gdb, c]
prerequisites: [c-memory-basics]
related: [x86-64-addressing-and-leaq, x86-64-cmp-and-jumps, gdb-stepi]
sources:
  - https://web.stanford.edu/class/archive/cs/cs107/cs107.1254/guide/x86-64.html
summary: "Learn the register names you must recognize before tracing x86-64 assembly."
---

# x86-64 Registers / x86-64 寄存器

## What This Solves / 解决什么问题

English: Assembly quiz questions often ask, "What is `%rax` at the end?" You cannot answer that unless you know which names are registers and how values move between them.

中文：汇编题经常问：“最后 `%rax` 是多少？” 如果你不知道哪些名字是寄存器、值怎么在寄存器之间移动，就没法稳定做题。

## Core Commands or Code / 核心命令或代码

In GDB:

```gdb
info registers
p/x $rax
p/x $rcx
p/x $rip
```

Common general-purpose registers:

```text
%rax  return value, arithmetic scratch
%rbx  callee-saved general register
%rcx  fourth integer argument on Linux x86-64, often scratch
%rdx  third integer argument on Linux x86-64, often scratch
%rsi  second integer argument
%rdi  first integer argument
%rsp  stack pointer
%rbp  old-style frame/base pointer
%rip  instruction pointer, the next instruction address
%r8-%r15 extra general-purpose registers
```

Size variants matter:

```text
%rax  64-bit
%eax  low 32 bits of %rax
%ax   low 16 bits
%al   low 8 bits
```

## Plain Explanation / 通俗解释

English: A register is a tiny, very fast storage slot inside the CPU. In a quiz, treat each register like a variable whose value changes instruction by instruction.

中文：寄存器可以理解为 CPU 里面很小但很快的存储格子。做题时，把每个寄存器当成一个变量，一条指令一条指令更新它的值。

English: `%rax` is special because function return values usually appear there. That is why many tracing questions ask for `%rax` at `ret`.

中文：`%rax` 很特殊，因为函数返回值通常放在这里。所以很多追踪题会问 `ret` 前 `%rax` 是多少。

## Reader Questions / 读者追问

Question: If an instruction writes `%eax`, does `%rax` change?

Answer: Yes. On x86-64, writing to a 32-bit register like `%eax` clears the upper 32 bits of the full 64-bit register `%rax`.

问题：如果一条指令写 `%eax`，`%rax` 会变吗？

回答：会。在 x86-64 中，写 `%eax` 这种 32-bit 寄存器会把完整 `%rax` 的高 32 位清零。

Question: Why does GDB use `$rax` but assembly uses `%rax`?

Answer: In GDB expressions, registers are written with `$`. In AT&T assembly syntax, registers are written with `%`.

问题：为什么 GDB 里写 `$rax`，汇编里写 `%rax`？

回答：GDB 表达式里寄存器用 `$`，AT&T 汇编语法里寄存器用 `%`。

## Common Mistakes / 常见错误

- English: Treating `%rax`, `%eax`, and `%al` as unrelated variables.
- 中文：误以为 `%rax`、`%eax`、`%al` 是互不相关的变量。
- English: Forgetting `%rip` is the current instruction location, not a normal data variable.
- 中文：忘记 `%rip` 是当前指令位置，不是普通数据变量。
- English: Assuming every register always has a meaningful C variable name.
- 中文：误以为每个寄存器都能稳定对应一个 C 变量名。

## Quick Recall / 快速记忆

English: In tracing problems, registers are your working table; `%rax` is usually the final answer.

中文：做汇编追踪题时，寄存器就是草稿表；`%rax` 通常是最终答案。

## Suggested Next / 下一步

Next, learn `x86-64-addressing-and-leaq`, because many register-tracing questions are really address-expression arithmetic questions.
