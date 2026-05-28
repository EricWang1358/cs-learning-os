---
title: "C Memory Basics / C 语言内存基础"
area: cs-fundamentals
track: c-and-memory
order: 20
status: seed
visibility: core
tags: [c, memory, pointer, stack, gdb]
prerequisites: [c-language-characteristics]
related: [gdb-basics, gdb-examine-memory, gdb-examine-stack-string, debugging-levels-vscode-python-vs-gdb]
sources: []
summary: "Understand addresses, pointers, stack memory, and byte-level layout before using GDB memory commands."
---

# C Memory Basics / C 语言内存基础

## What This Solves / 解决什么问题

English: GDB commands such as `x`, `disassemble`, and stack inspection only make sense when you understand that C variables live at addresses and memory is just bytes interpreted in different formats.

中文：如果不知道“变量有地址、内存是一串字节、不同格式只是不同解释方式”，GDB 的 `x`、反汇编、栈查看都会像魔法。

## Core Commands or Code / 核心命令或代码

```c
#include <stdio.h>

int main(void) {
    int x = 0x41424344;
    char s[] = "hello";

    printf("&x = %p\n", (void *)&x);
    printf("s  = %p\n", (void *)s);
    return 0;
}
```

Compile with debug symbols:

```bash
gcc -g -O0 demo.c -o demo
gdb ./demo
```

## Plain Explanation / 通俗解释

English: A pointer is an address. GDB often asks you two questions: where should I look, and how should I display the bytes there?

中文：指针就是地址。GDB 经常问的其实是两件事：从哪里开始看？用什么格式解释那里的字节？

## Common Mistakes / 常见错误

- English: Confusing a value with its address.
- 中文：把变量的值和变量的地址混在一起。
- English: Forgetting that strings are bytes ending with `\0`.
- 中文：忘记 C 字符串本质是以 `\0` 结尾的一串字节。

## Quick Recall / 快速记忆

English: Address first, format second.

中文：先确定地址，再确定显示格式。

## Suggested Next / 下一步

Learn `gdb-examine-memory` next, then connect it to `gdb-examine-stack-string`.
