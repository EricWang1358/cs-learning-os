---
title: "x86-64 mov and Instruction Suffixes / x86-64 mov 与指令后缀"
area: cs-fundamentals
track: x86-64-assembly
order: 15
status: seed
visibility: core
tags: [assembly, x86-64, mov, suffixes, registers, memory]
prerequisites: [x86-64-registers, c-memory-basics]
related: [x86-64-addressing-and-leaq, x86-64-calling-convention, x86-64-instruction-cheatsheet]
sources:
  - https://web.stanford.edu/class/archive/cs/cs107/cs107.1254/guide/x86-64.html
summary: "Understand `movq`, operand sizes, and what kinds of values can be copied in x86-64 assembly."
---

# x86-64 `mov` and Instruction Suffixes / x86-64 `mov` 与指令后缀

## What This Solves / 解决什么问题

English: Many assembly lines are just data copying. If you can read `movq src, dst`, you can track how values move through registers, memory, and function returns.

中文：很多汇编行其实只是在拷贝数据。你能读懂 `movq src, dst`，就能追踪值如何在寄存器、内存、函数返回值之间移动。

## Core Commands or Code / 核心命令或代码

Basic rule in AT&T syntax:

```asm
movq source, destination
```

Meaning:

```text
copy source into destination
```

Important:

```text
mov copies data.
mov does not add, subtract, compare, or multiply.
```

Common suffixes:

```text
movb    move byte       1 byte   8 bits
movw    move word       2 bytes  16 bits
movl    move long       4 bytes  32 bits
movq    move quad-word  8 bytes  64 bits
```

The `q` in `movq` means quad-word:

```text
q = 64-bit = 8 bytes
```

On x86-64, `movq` is common because registers such as `%rax`, `%rbx`, and `%rcx` are 64-bit registers.

## What Can Source And Destination Be? / 源和目标可以是什么？

Source can be an immediate constant:

```asm
movq $0x213, %rcx
```

Meaning:

```text
%rcx = 0x213
```

中文：`$0x213` 是一个立即数，意思是“数字本身 0x213”，不是地址。

Source can be another register:

```asm
movq %rcx, %rax
```

Meaning:

```text
%rax = %rcx
```

中文：把 `%rcx` 里的值复制到 `%rax`。复制后 `%rcx` 原来的值还在，不会被清空。

Source can be memory:

```asm
movq 8(%rsp), %rax
```

Meaning:

```text
%rax = memory[%rsp + 8]
```

中文：从地址 `%rsp + 8` 的内存位置读取 8 字节，放入 `%rax`。

Destination can be a register:

```asm
movq $5, %rax
```

Meaning:

```text
%rax = 5
```

Destination can be memory:

```asm
movq %rax, -8(%rbp)
```

Meaning:

```text
memory[%rbp - 8] = %rax
```

中文：把 `%rax` 的值存到当前栈帧里的某个局部变量位置。

## Common Patterns / 常见模式

Load a constant:

```asm
movq $0x64, %rbx
```

Read as:

```text
%rbx now holds 0x64
```

Copy a return value:

```asm
movq %rcx, %rax
ret
```

Read as:

```text
return %rcx
```

Save a local variable:

```asm
movq %rdi, -8(%rbp)
```

Read as:

```text
store the first argument into a stack slot
```

Load a local variable:

```asm
movq -8(%rbp), %rax
```

Read as:

```text
load that stack slot into %rax
```

Move a 32-bit int:

```asm
movl $1, %eax
```

Read as:

```text
%eax = 1
```

Note: writing `%eax` also clears the upper 32 bits of `%rax` on x86-64.

## Plain Explanation / 通俗解释

English: Think of `mov` like assignment in C, but with AT&T operand order reversed from what many beginners expect.

中文：可以把 `mov` 想成 C 语言里的赋值，但 AT&T 语法的方向很容易让初学者读反。

```asm
movq %rcx, %rax
```

is like:

```c
rax = rcx;
```

not:

```c
rcx = rax;
```

English: `movq` copies 64 bits. `movl` copies 32 bits. The suffix tells you how many bytes are moving.

中文：`movq` 拷贝 64 位，`movl` 拷贝 32 位。后缀告诉你这次搬运的数据宽度。

## Reader Questions / 读者追问

Question: Does `movq` move the value and erase the source?

Answer: No. It copies. The source remains unchanged.

问题：`movq` 是不是把值“搬走”，源寄存器就没了？

回答：不是。它是复制，源位置的值仍然保留。

Question: Is `movq` doing arithmetic?

Answer: No. `movq` only copies. If you see a value change because of arithmetic, look for `add`, `sub`, `imul`, `lea`, shifts, or logic instructions.

问题：`movq` 会做运算吗？

回答：不会。`movq` 只复制。如果值因为运算发生变化，要找 `add`、`sub`、`imul`、`lea`、移位或逻辑指令。

Question: Why is an immediate value written with `$`?

Answer: In AT&T syntax, `$0x213` means the literal number `0x213`. Without `$`, an expression may mean a memory address.

问题：为什么立即数前面有 `$`？

回答：AT&T 语法里，`$0x213` 表示字面数字 `0x213`。没有 `$` 时，表达式可能表示内存地址。

## Common Mistakes / 常见错误

- English: Reading `movq src, dst` backwards.
- 中文：把 `movq src, dst` 的方向读反。
- English: Thinking `movq` performs arithmetic.
- 中文：误以为 `movq` 会做运算。
- English: Forgetting `$0x10` means the number 16, while `0x10` without `$` can mean memory at address 16.
- 中文：忘记 `$0x10` 是数字 16，而没有 `$` 的 `0x10` 可能表示地址 16 处的内存。
- English: Treating `movq 8(%rsp), %rax` and `leaq 8(%rsp), %rax` as the same.
- 中文：把 `movq 8(%rsp), %rax` 和 `leaq 8(%rsp), %rax` 当成一样。前者读内存，后者算地址。
- English: Forgetting `movl` into a 32-bit register clears the upper half of the 64-bit register.
- 中文：忘记写入 32-bit 寄存器如 `%eax` 会清空对应 64-bit 寄存器的高 32 位。

## Quick Recall / 快速记忆

English: `movq src, dst` means "copy 8 bytes from src into dst."

中文：`movq src, dst` 就是“把 src 的 8 字节复制到 dst”。

## Suggested Next / 下一步

Next, learn `x86-64-addressing-and-leaq`, because the biggest beginner trap is confusing memory-copying `mov` with address-computing `lea`.
