---
title: "x86-64 cmp and Conditional Jumps / x86-64 比较与条件跳转"
area: cs-fundamentals
track: x86-64-assembly
order: 30
status: seed
visibility: core
tags: [assembly, x86-64, cmp, jumps, condition-codes, bomb-lab]
prerequisites: [x86-64-registers, x86-64-addressing-and-leaq]
related: [gdb-stepi, gdb-disassemble, bomb-lab-debugging-workflow]
sources:
  - https://web.stanford.edu/class/archive/cs/cs107/cs107.1254/guide/x86-64.html
summary: "Read `cmp`, flags, and conditional jumps without reversing the comparison."
---

# x86-64 `cmp` and Conditional Jumps / x86-64 比较与条件跳转

## What This Solves / 解决什么问题

English: A lot of assembly tracing mistakes happen because `cmpq A, B` is read backwards. This node teaches the exam-safe way to read compare-and-jump pairs.

中文：很多汇编追踪题错在把 `cmpq A, B` 读反。这个节点教你一种适合考试的读法，专门处理比较和条件跳转。

## Core Commands or Code / 核心命令或代码

AT&T syntax:

```asm
cmpq A, B
```

Think:

```text
set flags as if computing B - A
```

Then read the jump:

```asm
cmpq $0x430, %rdx
jg .L2
```

Exam-safe translation:

```text
if %rdx > 0x430 as a signed comparison, jump to .L2
```

Common jumps:

```text
je / jz      jump if equal / zero
jne / jnz    jump if not equal / not zero
jg / jnle    signed >
jge / jnl    signed >=
jl / jnge    signed <
jle / jng    signed <=
ja / jnbe    unsigned >
jae / jnb    unsigned >=
jb / jnae    unsigned <
jbe / jna    unsigned <=
```

Common boolean-result pattern:

```asm
cmp    $0x7a69, %eax
sete   %al
movzbl %al, %eax
```

Read it as:

```c
return %eax == 0x7a69;
```

## Plain Explanation / 通俗解释

English: `cmp` does not store a visible result like `%rax = ...`. It updates CPU flags. The next conditional jump reads those flags and decides whether to jump.

中文：`cmp` 不会像 `%rax = ...` 那样存一个看得见的结果。它更新 CPU 的 flags。后面的条件跳转读取这些 flags，决定要不要跳。

English: Sometimes the next instruction is not a jump. `sete %al` means "set the low byte to 1 if equal, otherwise 0." `movzbl %al, %eax` then expands that byte into an integer return value.

中文：有时下一条不是跳转。`sete %al` 表示“如果相等，就把低 8 位设为 1，否则设为 0”。`movzbl %al, %eax` 再把这个 byte 扩展成整数返回值。

English: For most course problems, translate `cmpq A, B; jg target` as "jump if B is greater than A", not "jump if A is greater than B".

中文：对大多数课程题，把 `cmpq A, B; jg target` 翻译成“如果 B 大于 A 就跳”，不要读成“如果 A 大于 B 就跳”。

## Reader Questions / 读者追问

Question: Why do we care about signed versus unsigned jumps?

Answer: The same bit pattern can mean different numbers under signed and unsigned interpretation. `jg` and `jl` are signed. `ja` and `jb` are unsigned.

问题：为什么要区分 signed 和 unsigned 跳转？

回答：同一串 bit 在 signed 和 unsigned 解释下可能代表不同数字。`jg`、`jl` 是 signed；`ja`、`jb` 是 unsigned。

Question: Does `cmpq $0x430, %rdx` change `%rdx`?

Answer: No. It only changes flags. `%rdx` keeps its value.

问题：`cmpq $0x430, %rdx` 会改变 `%rdx` 吗？

回答：不会。它只改变 flags，`%rdx` 的值不变。

## Common Mistakes / 常见错误

- English: Reversing `cmpq A, B` and checking whether `A > B`.
- 中文：把 `cmpq A, B` 读反，去判断 `A > B`。
- English: Treating `cmp` as subtraction that stores into the destination register.
- 中文：误以为 `cmp` 会把减法结果存回目标寄存器。
- English: Mixing signed jumps like `jg` with unsigned jumps like `ja`.
- 中文：混淆 `jg` 这种 signed 跳转和 `ja` 这种 unsigned 跳转。
- English: Missing `setcc` instructions such as `sete`, which convert flags into 0 or 1.
- 中文：漏看 `sete` 这类 `setcc` 指令；它们会把 flags 转成 0 或 1。

## Quick Recall / 快速记忆

English: In AT&T syntax, `cmp A, B; jg` means "jump if B > A" for signed values.

中文：AT&T 语法里，`cmp A, B; jg` 表示 signed 情况下“如果 B > A 就跳”。

## Suggested Next / 下一步

Pair this with `gdb-stepi`: step through `cmp`, then inspect whether the next jump is taken.
