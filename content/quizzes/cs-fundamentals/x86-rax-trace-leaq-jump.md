---
id: x86-rax-trace-leaq-jump
title: "Trace %rax through x86-64 instructions"
area: cs-fundamentals
status: seed
visibility: practice
difficulty: medium
weight: 1
tags: [assembly, x86-64, registers, leaq, cmp, jump]
linked_nodes: [x86-64-registers, x86-64-mov-and-suffixes, x86-64-addressing-and-leaq, x86-64-cmp-and-jumps, x86-64-instruction-cheatsheet, gdb-stepi]
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

### How To Think

Before calculating, translate the instruction vocabulary:

```text
movq src, dst        copy src into dst
leaq expr, dst       compute expr as arithmetic, store it in dst
cmpq A, B            set flags as if computing B - A
jg label             jump if B > A, signed, after the cmp
ret                  return; for these questions, we care what %rax holds
```

The safest habit is to keep a small register table and update only the register touched by the current instruction.

### Function 1 Walkthrough

```text
Start:
%rcx = unknown, %rdx = unknown, %rax = unknown
```

Step 1:

```asm
movq $0x213, %rcx
```

Mental translation:

```text
Put the immediate constant 0x213 into %rcx.
```

State:

```text
%rcx = 0x213
```

Step 2:

```asm
leaq (%rcx,%rcx), %rdx
```

Address-expression rule:

```text
D(base,index,scale) = D + base + index * scale
```

Here there is no displacement and no explicit scale, so scale defaults to 1:

```text
(%rcx,%rcx) = %rcx + %rcx = 0x213 + 0x213
```

Hex arithmetic:

```text
0x213 + 0x213 = 0x426
```

State:

```text
%rcx = 0x213
%rdx = 0x426
```

Step 3:

```asm
cmpq $0x430, %rdx
```

AT&T comparison rule:

```text
cmpq A, B sets flags as if computing B - A.
```

So this compares:

```text
%rdx ? 0x430
0x426 ? 0x430
```

Since:

```text
0x426 < 0x430
```

the relation `%rdx > 0x430` is false.

Step 4:

```asm
jg .L2
```

`jg` means signed "jump if greater" based on the previous `cmp`.

Because `%rdx` is not greater than `0x430`, the jump is not taken. Execution continues to the next line instead of skipping to `.L2`.

Step 5:

```asm
leaq 8(%rcx), %rcx
```

Translate:

```text
%rcx = 8 + %rcx
```

Hex arithmetic:

```text
0x213 + 0x8 = 0x21b
```

State:

```text
%rcx = 0x21b
%rdx = 0x426
```

Step 6:

```asm
movq %rcx, %rax
```

Copy `%rcx` into `%rax`:

```text
%rax = 0x21b
```

So Function 1 returns:

```text
%rax = 0x21b
```

Careful: the tempting wrong answer is `%rax = 0x213`. That happens if you assume `jg .L2` is taken and skip `leaq 8(%rcx), %rcx`.

### Function 2 Walkthrough

```text
Start:
%rcx = unknown, %rdx = unknown, %rbx = unknown, %r12 = unknown, %rax = unknown
```

Step 1:

```asm
movq $0x33, %rcx
```

State:

```text
%rcx = 0x33
```

Step 2:

```asm
leaq 1(,%rcx,2), %rdx
```

Read the expression carefully:

```text
D(base,index,scale)
```

Here:

```text
D = 1
base = omitted
index = %rcx
scale = 2
```

So:

```text
%rdx = 1 + %rcx * 2
```

Hex arithmetic:

```text
%rcx = 0x33
0x33 * 2 = 0x66
1 + 0x66 = 0x67
```

State:

```text
%rcx = 0x33
%rdx = 0x67
```

Step 3:

```asm
movq $0x64, %rbx
```

State:

```text
%rbx = 0x64
```

Step 4:

```asm
leaq (%rdx,%rbx,4), %r12
```

Again use:

```text
D + base + index * scale
```

Here:

```text
D = 0
base = %rdx = 0x67
index = %rbx = 0x64
scale = 4
```

Hex arithmetic:

```text
0x64 * 4 = 0x190
0x67 + 0x190 = 0x1f7
```

State:

```text
%r12 = 0x1f7
```

Step 5:

```asm
leaq (%r12), %rax
```

This can look suspicious because of the parentheses, but `leaq` still does not read memory. It computes the expression `%r12` and stores that value in `%rax`.

State:

```text
%rax = %r12 = 0x1f7
```

So Function 2 returns:

```text
%rax = 0x1f7
```

The tempting wrong move is to treat `leaq` as loading memory. In these instructions, `leaq` only computes the expression and writes the numeric result.

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

- Review `x86-64-registers` if `%rax`, `%rcx`, `%rdx`, and `%r12` are not automatic yet.
- Review `x86-64-mov-and-suffixes` if `movq $0x213, %rcx` does not immediately read as "copy this 64-bit value into `%rcx`."
- Review `x86-64-addressing-and-leaq` if `1(,%rcx,2)` or `(%rdx,%rbx,4)` feels weird.
- Review `x86-64-cmp-and-jumps` if `cmpq $0x430, %rdx; jg .L2` is easy to read backwards.
- Review `x86-64-instruction-cheatsheet` for the minimum instruction vocabulary.
- Review `gdb-stepi` if you want to simulate this one machine instruction at a time.
