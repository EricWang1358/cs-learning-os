---
title: "x86-64 Calling Convention / x86-64 函数调用约定"
area: cs-fundamentals
track: x86-64-assembly
order: 50
status: seed
visibility: core
tags: [assembly, x86-64, calling-convention, registers, functions]
prerequisites: [x86-64-registers]
related: [x86-64-instruction-cheatsheet, gdb-stepi, gdb-disassemble]
sources:
  - https://web.stanford.edu/class/archive/cs/cs107/cs107.1254/guide/x86-64.html
summary: "Understand how x86-64 passes function arguments and returns values through registers."
---

# x86-64 Calling Convention / x86-64 函数调用约定

## What This Solves / 解决什么问题

English: When assembly calls a function, the arguments are usually not shown as `process_code(code)`. They are placed in registers first. This node teaches how to spot that.

中文：汇编调用函数时，参数通常不会写成 `process_code(code)`。参数会先放进寄存器。这个节点教你看出参数是怎么传进去的。

## Core Commands or Code / 核心命令或代码

On common Linux x86-64 System V calling convention:

```text
1st integer/pointer argument -> %rdi / %edi
2nd integer/pointer argument -> %rsi / %esi
3rd integer/pointer argument -> %rdx / %edx
4th integer/pointer argument -> %rcx / %ecx
5th integer/pointer argument -> %r8  / %r8d
6th integer/pointer argument -> %r9  / %r9d
integer return value         -> %rax / %eax
```

Example:

```asm
mov    $0x1234, %edi
call   process_code
cmp    $0x7a69, %eax
```

Read it as:

```c
int result = process_code(0x1234);
result == 0x7a69;
```

## Plain Explanation / 通俗解释

English: Before `call`, look at argument registers. After `call`, look at `%rax` or `%eax` for the return value.

中文：`call` 之前看参数寄存器；`call` 之后看 `%rax` 或 `%eax`，它通常保存返回值。

English: If the caller receives `code` as its first argument, then `code` is already in `%edi` at the start of the function. Calling another one-argument function without changing `%edi` passes the same argument through.

中文：如果当前函数的第一个参数是 `code`，那函数开始时 `code` 已经在 `%edi`。如果没有改 `%edi` 就调用另一个单参数函数，那就是把同一个参数继续传过去。

## Reader Questions / 读者追问

Question: Why do some registers use `%edi` instead of `%rdi`?

Answer: `%edi` is the low 32-bit part of `%rdi`. For an `int` argument, the compiler often uses the 32-bit register name.

问题：为什么有时用 `%edi`，不是 `%rdi`？

回答：`%edi` 是 `%rdi` 的低 32 位。对于 `int` 参数，编译器经常使用 32-bit 寄存器名。

Question: Does `call process_code` automatically show its argument?

Answer: No. You infer the argument from the calling convention and the register values immediately before the call.

问题：`call process_code` 会自动显示参数吗？

回答：不会。你要根据调用约定和 call 之前的寄存器值推断参数。

## Common Mistakes / 常见错误

- English: Looking for arguments on the stack even when the first six integer arguments are in registers.
- 中文：明明前六个整数参数在寄存器里，却只去栈上找参数。
- English: Forgetting `%eax` is the 32-bit return value register.
- 中文：忘记 `%eax` 是 32-bit 返回值寄存器。
- English: Missing pass-through calls where the caller does not modify `%edi` before `call`.
- 中文：漏看“透传参数”的调用：调用前没有改 `%edi`，所以参数原样传下去。

## Quick Recall / 快速记忆

English: Before `call`, check `%rdi/%rsi/%rdx...`; after `call`, check `%rax`.

中文：`call` 前看 `%rdi/%rsi/%rdx...`；`call` 后看 `%rax`。

## Suggested Next / 下一步

Use this with `x86-64-cmp-and-jumps` to read validation functions that call a helper and then compare its return value.
