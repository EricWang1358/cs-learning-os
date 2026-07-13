---
title: "x86-64 Addressing and leaq"
area: cs-fundamentals
track: x86-64-assembly
order: 30
status: demo
visibility: core
tags: [assembly, x86-64, addressing, leaq]
prerequisites: []
related: [binary-search]
sources: []
summary: "Read address expressions and understand why `leaq` often behaves like arithmetic."
---

# x86-64 Addressing and `leaq`

## **作用**

`leaq` helps demonstrate bold Markdown headings in the demo content while also explaining a real assembly idea.

## Core Idea

AT&T address expressions use this shape:

```text
D(base,index,scale) = D + base + index * scale
```

## Examples

```asm
leaq (%rcx,%rcx), %rdx       # rdx = rcx + rcx
leaq 8(%rcx), %rcx           # rcx = rcx + 8
leaq 1(,%rcx,2), %rdx        # rdx = 1 + rcx * 2
movq 8(%rsp), %rax           # rax = memory at rsp + 8
```

## `leaq` vs `movq`

`leaq` computes the address-shaped expression and stores the number.

`movq` with parentheses usually reads or writes memory at that address.

## Common Mistake

Parentheses do not automatically mean memory access. With `leaq`, the expression is calculated but memory is not read.
