---
title: "x86-64 Addressing and leaq / x86-64 寻址与 leaq"
area: cs-fundamentals
track: x86-64-assembly
order: 20
status: seed
visibility: core
tags: [assembly, x86-64, addressing, leaq, registers]
prerequisites: [x86-64-registers, c-memory-basics]
related: [x86-64-cmp-and-jumps, gdb-disassemble, gdb-stepi]
sources:
  - https://web.stanford.edu/class/archive/cs/cs107/cs107.1254/guide/x86-64.html
summary: "Read x86-64 address expressions and understand why `leaq` often means arithmetic."
---

# x86-64 Addressing and `leaq` / x86-64 寻址与 `leaq`

## What This Solves / 解决什么问题

English: Many exam questions hide arithmetic inside expressions like `8(%rcx)` or `1(,%rcx,2)`. This node teaches you to decode those expressions.

中文：很多考试题会把算术藏在 `8(%rcx)` 或 `1(,%rcx,2)` 这种表达式里。这个节点教你把它们拆开读。

## Core Commands or Code / 核心命令或代码

AT&T memory/address form:

```text
D(base,index,scale) = D + base + index * scale
```

Examples:

```asm
leaq (%rcx,%rcx), %rdx       # %rdx = %rcx + %rcx
leaq 8(%rcx), %rcx           # %rcx = 8 + %rcx
leaq 1(,%rcx,2), %rdx        # %rdx = 1 + %rcx * 2
leaq (%rdx,%rbx,4), %r12     # %r12 = %rdx + %rbx * 4
movq (%rsp), %rax            # %rax = memory at address %rsp
movq 8(%rsp), %rax           # %rax = memory at address %rsp + 8
```

Important difference:

```text
leaq expression, dst    compute the expression number
movq expression, dst    read memory at that address
```

## Plain Explanation / 通俗解释

English: `leaq` means "load effective address", but in tracing questions it often behaves like a free arithmetic instruction. It calculates the address-shaped expression and writes the number into the destination register.

中文：`leaq` 全称是 “load effective address”，但在追踪题里它经常像一个免费的算术指令。它把地址形式的表达式算出来，把这个数字写进目标寄存器。

English: The dangerous part is that the same-looking expression can mean "compute this number" with `leaq`, but "go to memory and read from this address" with `movq`.

中文：危险点在于：同样长相的表达式，配 `leaq` 时是“算出这个数字”，配 `movq` 时可能是“去这个内存地址读值”。

## Reader Questions / 读者追问

Question: In `leaq 1(,%rcx,2), %rdx`, why is there an empty spot before the first comma?

Answer: The base register is omitted. The expression is `1 + no_base + %rcx * 2`.

问题：`leaq 1(,%rcx,2), %rdx` 里第一个逗号前为什么是空的？

回答：因为 base register 被省略了。这个表达式就是 `1 + 没有 base + %rcx * 2`。

Question: Does `leaq (%r12), %rax` read memory?

Answer: No. It just computes the expression `%r12` and copies that numeric address/value into `%rax`. `movq (%r12), %rax` would read memory.

问题：`leaq (%r12), %rax` 会读内存吗？

回答：不会。它只是计算表达式 `%r12`，把这个数字放进 `%rax`。`movq (%r12), %rax` 才会读内存。

## Common Mistakes / 常见错误

- English: Treating every parenthesized expression as memory access.
- 中文：看到括号就以为一定是在访问内存。
- English: Forgetting the scale can only be 1, 2, 4, or 8 in normal x86-64 addressing.
- 中文：忘记常规 x86-64 寻址里的 scale 通常只能是 1、2、4、8。
- English: Reading AT&T operands backwards; the destination is usually on the right.
- 中文：把 AT&T 操作数读反；目标操作数通常在右边。

## Quick Recall / 快速记忆

English: `leaq D(base,index,scale), dst` means `dst = D + base + index * scale`.

中文：`leaq D(base,index,scale), dst` 就是 `dst = D + base + index * scale`。

## Suggested Next / 下一步

Study `x86-64-cmp-and-jumps` next, because after arithmetic, most quiz mistakes happen at conditional branches.
