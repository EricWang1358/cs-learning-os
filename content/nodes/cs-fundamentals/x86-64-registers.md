---
title: "x86-64 Registers / x86-64 寄存器"
area: cs-fundamentals
track: x86-64-assembly
order: 10
status: seed
visibility: core
tags: [assembly, x86-64, registers, gdb, c]
prerequisites: [c-memory-basics]
related: [x86-64-mov-and-suffixes, x86-64-addressing-and-leaq, x86-64-cmp-and-jumps, gdb-stepi]
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
%rax  accumulator; return value; arithmetic scratch
%rbx  general-purpose register; often preserved across calls
%rcx  general-purpose register; 4th integer argument; often loop/count scratch
%rdx  general-purpose register; 3rd integer argument; arithmetic/helper scratch
%rsi  2nd integer/pointer argument; often source pointer
%rdi  1st integer/pointer argument; often destination or input pointer
%rsp  stack pointer
%rbp  old-style frame/base pointer
%r8   5th integer/pointer argument
%r9   6th integer/pointer argument
%r10  general-purpose scratch
%r11  general-purpose scratch
%r12  general-purpose register; often preserved across calls
%r13  general-purpose register; often preserved across calls
%r14  general-purpose register; often preserved across calls
%r15  general-purpose register; often preserved across calls
%rip  instruction pointer, the next instruction address
```

Size variants matter:

```text
%rax  64-bit
%eax  low 32 bits of %rax
%ax   low 16 bits
%al   low 8 bits
```

Register families:

```text
64-bit   32-bit   16-bit   8-bit
%rax     %eax     %ax      %al
%rbx     %ebx     %bx      %bl
%rcx     %ecx     %cx      %cl
%rdx     %edx     %dx      %dl
%rsi     %esi     %si      %sil
%rdi     %edi     %di      %dil
%rsp     %esp     %sp      %spl
%rbp     %ebp     %bp      %bpl
%r8      %r8d     %r8w     %r8b
%r9      %r9d     %r9w     %r9b
```

## Plain Explanation / 通俗解释

English: A register is a tiny, very fast storage slot inside the CPU. In a quiz, treat each register like a variable whose value changes instruction by instruction.

中文：寄存器可以理解为 CPU 里面很小但很快的存储格子。做题时，把每个寄存器当成一个变量，一条指令一条指令更新它的值。

English: `%rax` is special because function return values usually appear there. That is why many tracing questions ask for `%rax` at `ret`.

中文：`%rax` 很特殊，因为函数返回值通常放在这里。所以很多追踪题会问 `ret` 前 `%rax` 是多少。

English: Historically, `%rax` is the accumulator. In beginner terms, it is the CPU's favorite "answer/result" register. Compilers still use it heavily for return values and temporary arithmetic results.

中文：历史上 `%rax` 是 accumulator（累加器）。通俗说，它很像 CPU 最喜欢放“答案/结果”的寄存器。现代编译器仍然经常用它保存返回值和临时运算结果。

## What Can Each Register Represent? / 每个寄存器可能代表什么变量？

The same register can represent different things in different functions. Do not memorize one fixed meaning. Instead, infer meaning from nearby instructions.

同一个寄存器在不同函数里可以代表不同变量。不要死记“某寄存器永远是什么”，要从附近指令推断。

Examples:

```asm
movq $0x213, %rcx
```

Possible meaning:

```text
%rcx is a temporary variable holding 0x213.
```

```asm
leaq (%rcx,%rcx), %rdx
```

Possible meaning:

```text
%rdx is a computed temporary: 2 * %rcx.
```

```asm
call process_code
```

Before this call:

```text
%edi may be the first int argument to process_code.
```

After this call:

```text
%eax may be process_code's int return value.
```

Stack-related examples:

```asm
movq 8(%rsp), %rax
```

Possible meaning:

```text
Read a saved value or local stack slot into %rax.
```

```asm
movq %rax, -8(%rbp)
```

Possible meaning:

```text
Store %rax into a local variable slot.
```

## Register Categories / 寄存器分类

Accumulator / 累加器:

```text
%rax family: %rax, %eax, %ax, %al
```

Use:

```text
return values, arithmetic results, temporary results
```

通俗解释：看到 `%rax/%eax`，先问：“这里是不是函数返回值？是不是刚算出来的结果？”

Argument registers / 参数寄存器:

```text
1st: %rdi / %edi
2nd: %rsi / %esi
3rd: %rdx / %edx
4th: %rcx / %ecx
5th: %r8  / %r8d
6th: %r9  / %r9d
```

Use:

```text
passing function arguments before call
```

通俗解释：`call` 前先看这些寄存器，它们常常就是即将传给函数的参数。

Stack registers / 栈相关寄存器:

```text
%rsp stack pointer
%rbp frame/base pointer
```

Use:

```text
finding stack variables, saved return addresses, saved registers
```

通俗解释：`%rsp` 像当前栈顶指针；`%rbp` 在没优化的代码里常像“本函数栈帧的参考点”。

Instruction pointer / 指令指针:

```text
%rip
```

Use:

```text
address of the next instruction
```

通俗解释：`%rip` 告诉你 CPU 下一步要执行哪里。它不是普通变量。

## Reader Questions / 读者追问

Question: If an instruction writes `%eax`, does `%rax` change?

Answer: Yes. On x86-64, writing to a 32-bit register like `%eax` clears the upper 32 bits of the full 64-bit register `%rax`.

问题：如果一条指令写 `%eax`，`%rax` 会变吗？

回答：会。在 x86-64 中，写 `%eax` 这种 32-bit 寄存器会把完整 `%rax` 的高 32 位清零。

Question: Why does GDB use `$rax` but assembly uses `%rax`?

Answer: In GDB expressions, registers are written with `$`. In AT&T assembly syntax, registers are written with `%`.

问题：为什么 GDB 里写 `$rax`，汇编里写 `%rax`？

回答：GDB 表达式里寄存器用 `$`，AT&T 汇编语法里寄存器用 `%`。

Question: Are `%rax`, `%eax`, `%ax`, and `%al` four different variables?

Answer: No. They are different-sized views of the same register family. `%eax` is the low 32 bits of `%rax`; `%al` is the low 8 bits.

问题：`%rax`、`%eax`、`%ax`、`%al` 是四个不同变量吗？

回答：不是。它们是同一个寄存器家族的不同大小视图。`%eax` 是 `%rax` 的低 32 位，`%al` 是低 8 位。

## Common Mistakes / 常见错误

- English: Treating `%rax`, `%eax`, and `%al` as unrelated variables.
- 中文：误以为 `%rax`、`%eax`、`%al` 是互不相关的变量。
- English: Forgetting `%rip` is the current instruction location, not a normal data variable.
- 中文：忘记 `%rip` 是当前指令位置，不是普通数据变量。
- English: Assuming every register always has a meaningful C variable name.
- 中文：误以为每个寄存器都能稳定对应一个 C 变量名。
- English: Seeing `%rax` and forgetting it may be the function return value.
- 中文：看到 `%rax` 却忘记它可能是函数返回值。
- English: Looking for the first function argument on the stack instead of `%rdi/%edi`.
- 中文：第一个函数参数明明在 `%rdi/%edi`，却只去栈上找。

## Quick Recall / 快速记忆

English: In tracing problems, registers are your working table; `%rax` is usually the final answer.

中文：做汇编追踪题时，寄存器就是草稿表；`%rax` 通常是最终答案。

## Suggested Next / 下一步

Next, learn `x86-64-mov-and-suffixes` to understand how values are copied into and out of these registers.
