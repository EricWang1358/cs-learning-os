---
title: "x86-64 Instruction Cheatsheet / x86-64 指令速查"
area: cs-fundamentals
track: x86-64-assembly
order: 40
status: seed
visibility: core
tags: [assembly, x86-64, instructions, cheatsheet, bomb-lab]
prerequisites: [x86-64-registers, x86-64-mov-and-suffixes, x86-64-addressing-and-leaq, x86-64-cmp-and-jumps]
related: [gdb-disassemble, gdb-stepi, bomb-lab-debugging-workflow]
sources:
  - https://web.stanford.edu/class/archive/cs/cs107/cs107.1254/guide/x86-64.html
summary: "A compact guide to the x86-64 instructions most often needed for quizzes and Bomb Lab."
---

# x86-64 Instruction Cheatsheet / x86-64 指令速查

## What This Solves / 解决什么问题

English: When you read disassembly, you need a small working vocabulary before you need a full ISA manual. This page gives the core instructions for quizzes and Bomb Lab.

中文：读反汇编时，你先需要一个小而够用的词汇表，不是一上来就啃完整指令手册。这个页面覆盖 quiz 和 Bomb Lab 最常见的指令。

## Core Commands or Code / 核心命令或代码

Data movement:

```asm
movq src, dst      # copy value
leaq addr, dst     # compute address expression
pushq src          # push onto stack
popq dst           # pop from stack
```

Arithmetic and logic:

```asm
addq src, dst      # dst = dst + src
subq src, dst      # dst = dst - src
imulq src, dst     # dst = dst * src
andq src, dst      # bitwise and
orq src, dst       # bitwise or
xorq src, dst      # bitwise xor
shlq n, dst        # left shift
sarq n, dst        # arithmetic right shift
```

Compare and branch:

```asm
cmpq a, b          # set flags as if b - a
testq a, b         # set flags as if a & b
sete dst8          # dst8 = 1 if equal, else 0
setne dst8         # dst8 = 1 if not equal, else 0
movzbl src8, dst32 # zero-extend byte to 32-bit integer
je target          # jump if equal
jne target         # jump if not equal
jg target          # signed greater
jl target          # signed less
ja target          # unsigned above
jb target          # unsigned below
```

Function flow:

```asm
call target        # call function, return address goes on stack
ret                # return to caller
nop                # do nothing
```

## Plain Explanation / 通俗解释

English: Do not memorize every instruction at once. Start by classifying each line: move, compute, compare, jump, call, or return. Once you know the category, the details become much easier.

中文：不要一开始背全部指令。先给每一行分类：搬运、计算、比较、跳转、调用、返回。类别看懂之后，细节会容易很多。

English: Bomb Lab phases often combine only a few patterns: parse input, compare values, jump to explode on failure, otherwise continue.

中文：Bomb Lab 的 phase 经常只是组合几个套路：解析输入、比较值、失败就跳到 explode，成功就继续。

## Reader Questions / 读者追问

Question: What is `testq %rax, %rax` doing?

Answer: It checks whether `%rax` is zero without changing `%rax`. If `%rax & %rax` is zero, the zero flag is set.

问题：`testq %rax, %rax` 在干什么？

回答：它在不改变 `%rax` 的情况下检查 `%rax` 是否为 0。如果 `%rax & %rax` 是 0，就会设置 zero flag。

Question: Why does `sete %al; movzbl %al, %eax` often mean return true or false?

Answer: `sete` writes 1 to `%al` if the previous comparison was equal, otherwise 0. `movzbl` turns that byte into a clean 32-bit integer in `%eax`.

问题：为什么 `sete %al; movzbl %al, %eax` 经常表示返回 true 或 false？

回答：如果前面的比较相等，`sete` 会把 `%al` 写成 1，否则写成 0。`movzbl` 再把这个 byte 变成干净的 32-bit 整数放进 `%eax`。

Question: Why does `xor %eax, %eax` often mean `%eax = 0`?

Answer: Any value xor itself equals zero. Compilers often use this as a compact way to clear a register.

问题：为什么 `xor %eax, %eax` 经常表示 `%eax = 0`？

回答：任何值和自己异或都会得到 0。编译器经常用这种方式清空寄存器。

## Common Mistakes / 常见错误

- English: Trying to understand every instruction before identifying the control flow.
- 中文：还没看控制流，就试图逐字理解每条指令。
- English: Forgetting AT&T syntax writes the destination on the right.
- 中文：忘记 AT&T 语法通常把目标操作数写在右边。
- English: Treating `call explode_bomb` as just another call instead of a failure edge.
- 中文：把 `call explode_bomb` 当普通调用看，而不是识别成失败路径。

## Quick Recall / 快速记忆

English: Classify first: move, compute, compare, jump, call, return.

中文：先分类：搬运、计算、比较、跳转、调用、返回。

## Suggested Next / 下一步

Use this page beside `bomb-lab-debugging-workflow` as a working checklist while reading phases.

If `movq`, operand suffixes, or immediate/register/memory operands are still unclear, review `x86-64-mov-and-suffixes` before using this cheatsheet.
