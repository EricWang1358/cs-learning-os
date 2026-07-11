---
id: x86-rax-trace-leaq-jump
title: "Trace %rax through x86-64 instructions"
area: cs-fundamentals
status: demo
visibility: practice
difficulty: medium
weight: 1
tags: [assembly, x86-64, registers, leaq, cmp, jump]
linked_nodes: [x86-64-addressing-and-leaq]
sources: []
summary: "Practice computing a final register value by tracing register updates."
---

# Trace `%rax` through x86-64 instructions

## Prompt

Compute the final value of `%rax`.

```asm
movq $0x10, %rcx
leaq 8(%rcx), %rdx
cmpq $0x20, %rdx
jg .L2
movq %rdx, %rax
ret

.L2:
movq %rcx, %rax
ret
```

## Answer

`%rax = 0x18`. `leaq` computes the address expression without reading memory, so `%rdx` receives `0x10 + 0x8`; the signed comparison does not jump, and the fall-through move copies that value into `%rax`.

## Explanation

`movq $0x10, %rcx` puts `0x10` in `%rcx`.

`leaq 8(%rcx), %rdx` computes `0x10 + 0x8 = 0x18`.

`cmpq $0x20, %rdx` compares `%rdx` with `0x20`. Since `0x18` is not greater than `0x20`, `jg .L2` is not taken.

Execution continues to `movq %rdx, %rax`, so `%rax` becomes `0x18`.

## Linked Review

Review `x86-64-addressing-and-leaq` for the address expression.
