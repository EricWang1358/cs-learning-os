---
id: x86-rax-trace-leaq-jump
title: "Trace %rax through x86-64 instructions"
area: cs-fundamentals
status: seed
visibility: practice
difficulty: medium
weight: 1
tags: [assembly, x86-64, registers, leaq, cmp, jump]
linked_nodes: [gdb-disassemble, gdb-stepi, c-memory-basics]
sources:
  - local-exam-screenshot-2026-05-28
summary: "Practice computing the final value of `%rax` by tracing x86-64 register updates."
---

# Trace `%rax` through x86-64 instructions

## Prompt

Identify the value of `%rax` at the end of each function and briefly justify how you got that answer.

```asm
Function 1:
    movq $0x213, %rcx
    leaq (%rcx,%rcx), %rdx
    cmpq $0x430, %rdx
    jg .L2
    leaq 8(%rcx), %rcx

.L2:
    movq %rcx, %rax
    ret
```

```asm
Function 2:
    movq $0x33, %rcx
    leaq 1(,%rcx,2), %rdx
    movq $0x64, %rbx
    leaq (%rdx,%rbx,4), %r12
    leaq (%r12), %rax
    ret
```

## Answer

- Function 1: `%rax = 0x21b`.
- Function 2: `%rax = 0x1f7`.

## Explanation

Function 1:

```text
%rcx = 0x213
%rdx = %rcx + %rcx = 0x426
compare %rdx with 0x430
0x426 is not greater than 0x430, so jg is not taken
%rcx = %rcx + 8 = 0x21b
%rax = %rcx = 0x21b
```

Careful: the tempting wrong answer is `%rax = 0x213`. That happens if you assume `jg .L2` is taken and skip `leaq 8(%rcx), %rcx`. Under standard AT&T syntax, `cmpq $0x430, %rdx` checks the signed relation between `%rdx` and `0x430`; since `0x426` is not greater than `0x430`, the jump is not taken.

Function 2:

```text
%rcx = 0x33
%rdx = 1 + %rcx * 2 = 1 + 0x66 = 0x67
%rbx = 0x64
%r12 = %rdx + %rbx * 4 = 0x67 + 0x190 = 0x1f7
%rax = %r12 = 0x1f7
```

The tempting wrong move is to treat `leaq` as loading memory. Here it does not read memory; it only computes the expression and writes the numeric result.

## Plain Explanation

English: `leaq` often looks like memory access, but in these questions it is usually arithmetic. It computes an address-shaped expression and stores the number in the destination register.

中文：`leaq` 看起来像是在访问内存，但这类题里它通常是在做算术。它把“地址表达式”算出来，然后把这个数字放进目标寄存器。

English: For AT&T syntax, `cmpq A, B` sets flags as if it computed `B - A`. Then `jg` jumps when `B` is greater than `A` as a signed comparison.

中文：在 AT&T 语法里，`cmpq A, B` 设置标志位时相当于计算 `B - A`。后面的 `jg` 表示 signed comparison 下 `B > A` 才跳转。

## What This Tests

- Register tracing: update one register at a time.
- `leaq` arithmetic: read `D(base,index,scale)` as `D + base + index * scale`.
- Conditional jumps: do not skip the comparison semantics.
- Hex arithmetic: keep values in hex or convert carefully.

## Linked Review

- Review `gdb-disassemble` if instruction syntax feels unfamiliar.
- Review `gdb-stepi` if you want to simulate this one machine instruction at a time.
- Review `c-memory-basics` if register and memory roles are blending together.
