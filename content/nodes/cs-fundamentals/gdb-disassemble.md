---
title: "GDB disassemble / GDB 反汇编"
area: cs-fundamentals
status: seed
visibility: core
tags: [gdb, disassembly, assembly, c, debugging]
prerequisites: [gdb-basics, c-language-characteristics, c-memory-basics]
related: [gdb-stepi, gdb-examine-memory, debugging-levels-vscode-python-vs-gdb]
sources:
  - https://sourceware.org/gdb/current/onlinedocs/gdb.html/Machine-Code.html
summary: "Use `disassemble` to view the CPU instructions generated from compiled C code."
---

# GDB disassemble / GDB 反汇编

## What This Solves / 解决什么问题

English: `disassemble` helps you answer: "After this C function is compiled, what exact CPU instructions will run?"

中文：`disassemble` 帮你回答：“这个 C 函数编译之后，CPU 实际会执行哪些指令？”

## Core Commands or Code / 核心命令或代码

Try this tiny C program:

```c
int add(int a, int b) {
    int total = a + b;
    return total;
}

int main(void) {
    return add(2, 3);
}
```

Compile and open it in GDB:

```bash
gcc -g -O0 demo.c -o demo
gdb ./demo
```

Useful GDB commands:

```gdb
disassemble
disassemble main
disassemble add
disassemble /m add
disassemble 0x401126, 0x401160
```

How to read them:

```text
disassemble          show instructions for the current function or frame
disassemble add      show instructions for the function add
disassemble /m add   mix C source and assembly when debug info exists
disassemble A, B     show instructions from address A to address B
```

## Plain Explanation / 通俗解释

English: C source code is what you wrote. Assembly is closer to what the CPU actually receives. `disassemble` translates machine code bytes back into readable assembly instructions.

中文：C 源码是你写的东西；汇编更接近 CPU 实际收到的东西。`disassemble` 会把机器码字节反过来显示成人能读的汇编指令。

English: It does not run the program. It only shows the instructions already stored in the program's code section.

中文：它不会执行程序，只是把程序代码区域里已经存在的指令显示出来。

## Reader Questions / 读者追问

Question: You said "`disassemble` shows CPU instructions." What kinds of instructions are those?

Answer: Common instruction categories include data movement, arithmetic, comparison, jumps, calls, returns, stack operations, and memory access.

问题：你说 `disassemble` 显示 CPU 实际执行的指令，那“指令”都有哪些？

回答：常见指令类型包括：数据搬运、算术运算、比较、跳转、函数调用、返回、栈操作、内存访问。

Examples you may see on x86-64:

```asm
mov    %edi,-0x14(%rbp)   ; move data / 搬运数据
add    %edx,%eax          ; add numbers / 做加法
cmp    $0x0,-0x4(%rbp)    ; compare / 比较
je     0x401150           ; jump if equal / 条件跳转
call   0x401126 <add>     ; call function / 调用函数
ret                       ; return / 返回
push   %rbp               ; push to stack / 压栈
pop    %rbp               ; pop from stack / 出栈
```

Question: Does one C line equal one assembly instruction?

Answer: Usually no. One C line can become many instructions, and optimization can remove, merge, or reorder instructions.

问题：一行 C 代码是不是等于一条汇编指令？

回答：通常不是。一行 C 代码可能变成很多条指令；如果开了优化，编译器还可能删除、合并或重排指令。

## Common Mistakes / 常见错误

- English: Thinking `disassemble` changes program state.
- 中文：误以为 `disassemble` 会改变程序状态。
- English: Reading assembly without checking which architecture you are on.
- 中文：看汇编时不确认当前 CPU 架构，例如 x86-64、ARM。
- English: Expecting optimized builds to match source code neatly.
- 中文：期待优化后的程序还和源码一行一行整齐对应。

## Quick Recall / 快速记忆

English: `disassemble` means "show me the compiled instructions."

中文：`disassemble` 就是“给我看编译后的指令”。

## Suggested Next / 下一步

Use `gdb-stepi` next: after you can see the instructions, learn how to execute them one at a time.
