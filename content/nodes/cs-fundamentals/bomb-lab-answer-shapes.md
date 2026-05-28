---
title: "Bomb Lab Answer Shapes / Bomb Lab 答案形态"
area: cs-fundamentals
track: bomb-lab
order: 20
status: seed
visibility: support
tags: [bomb-lab, answers, patterns, x86-64, reverse-engineering]
prerequisites: [bomb-lab-debugging-workflow, x86-64-instruction-cheatsheet, x86-64-cmp-and-jumps]
related: [x86-64-addressing-and-leaq, x86-64-registers, gdb-examine-memory]
sources:
  - https://csapp.cs.cmu.edu/3e/bomblab.pdf
  - https://www.cs.cmu.edu/afs/cs.cmu.edu/academic/class/15213-f18/www/activities/bomblab-rec/recitation03-bomblab.pdf
  - https://gist.github.com/meiji163/08cdbacb082c026ab985486867547ff5
  - https://cedricxu.xyz/bomblab
summary: "Recognize what Bomb Lab answers usually look like without assuming one answer key fits every bomb."
---

# Bomb Lab Answer Shapes / Bomb Lab 答案形态

## What This Solves / 解决什么问题

English: The workflow tells you how to solve a phase. This page shows what a solved answer often looks like, so you have a target shape in mind while reversing.

中文：工作流告诉你怎么解 phase。这个页面告诉你“解出来的答案通常长什么样”，这样你逆向时心里有一个目标形态。

## Important Warning / 重要提醒

English: Bomb Lab binaries can differ by course, semester, architecture, and personalization. The examples below are answer shapes and synthetic examples, not guaranteed answers for your bomb.

中文：Bomb Lab 的二进制可能因为课程、学期、架构、个人化版本而不同。下面是答案形态和示例，不保证是你的 bomb 的答案。

## Common Answer Shapes / 常见答案形态

### Phase 1: Exact String / 固定字符串

Typical answer shape:

```text
a full sentence copied exactly from the binary
```

Example:

```text
Border relations with Canada have never been better.
```

What the assembly looks like:

```asm
mov    $0x402400,%esi
call   strings_not_equal
test   %eax,%eax
je     ok_path
call   explode_bomb
```

How to find it:

```gdb
x/s 0x402400
```

中文：这种 phase 本质是字符串相等。答案经常是一整句英文，必须大小写、空格、标点完全一致。

### Phase 2: Number Sequence / 数列

Typical answer shape:

```text
six integers separated by spaces
```

Common examples:

```text
1 2 4 8 16 32
0 1 1 2 3 5
1 2 6 24 120 720
```

What the assembly looks like:

```asm
call   read_six_numbers
cmp    $0x1,(%rsp)
jne    explode
add    previous,current
cmp    expected,current
jne    explode
```

中文：这种 phase 常见是六个整数，后一个数由前面的数推出。你要读循环里的加法、乘法或递推关系。

### Phase 3: Switch or Jump Table / switch 或跳转表

Typical answer shape:

```text
index value
```

Example:

```text
3 256
```

What the assembly looks like:

```asm
cmp    $0x7, first_input
ja     explode
jmp    *jump_table(,first_input,8)
mov    $case_value,%eax
cmp    second_input,%eax
jne    explode
```

中文：这种 phase 通常输入两个数。第一个数选择 case，第二个数必须等于该 case 算出的值。

### Phase 4: Recursive Function / 递归函数

Typical answer shape:

```text
number target_value
```

or:

```text
number 0
```

Example:

```text
7 0
```

What the assembly looks like:

```asm
call   func4
cmp    $target,%eax
jne    explode
cmp    $required_second,input2
jne    explode
```

中文：这种 phase 的关键不是盲猜数字，而是读 `func4`。它经常像二分搜索、递归求和或路径编码。

### Phase 5: Character Mapping / 字符映射

Typical answer shape:

```text
six-character string
```

Example:

```text
flyers
```

What the assembly looks like:

```asm
and    $0xf,%edx
movzbl table(%rdx),%edx
mov    %dl,output(%rax)
call   strings_not_equal
```

中文：这种 phase 常用输入字符的低 4 位作为索引，从表里查字符，最后拼成目标字符串。

### Phase 6: Linked List Reordering / 链表重排

Typical answer shape:

```text
six unique integers from 1 to 6
```

Example:

```text
4 3 2 1 6 5
```

What the assembly looks like:

```asm
read_six_numbers
check each number is 1..6
check no duplicates
map numbers to linked-list nodes
relink nodes in input order
check node values are sorted
```

中文：这种 phase 经常要求输入 1 到 6 的一个排列。真正的答案来自链表节点值的排序顺序。

### Secret Phase: Binary Tree Path / 隐藏关：二叉树路径

Typical answer shape:

```text
one integer
```

Example:

```text
22
```

What the assembly looks like:

```asm
call   strtol
call   fun7
cmp    $path_code,%eax
jne    explode
```

中文：secret phase 常见是二叉搜索树。输入一个数，递归搜索返回路径编码，返回值必须等于目标 path code。

## How To Use These Shapes / 如何使用这些形态

English:

1. Identify the phase type first.
2. Predict the answer shape.
3. Use GDB to extract constants, strings, tables, node values, or path codes.
4. Derive the actual answer for your binary.
5. Test it with a breakpoint on `explode_bomb`.

中文：

1. 先判断 phase 类型。
2. 预测答案形态。
3. 用 GDB 提取常量、字符串、表、节点值或路径编码。
4. 为你的二进制推出真实答案。
5. 在 `explode_bomb` 上设断点，安全测试。

## Common Mistakes / 常见错误

- English: Copying an online answer into a different bomb binary.
- 中文：把网上某个答案直接塞进另一个不同的 bomb。
- English: Knowing the answer shape but not extracting the constants from your binary.
- 中文：知道答案形态，却没有从自己的二进制里提取常量。
- English: Treating phase numbers as universal. Some courses reorder or modify phases.
- 中文：以为 phase 编号永远对应同一种题。有些课程会重排或修改 phase。

## Quick Recall / 快速记忆

English: Bomb answers usually look like: exact string, six-number sequence, index-value pair, recursive target, six-character mapping, linked-list permutation, or tree-path integer.

中文：Bomb 答案常见形态：固定字符串、六个数的数列、索引和值、递归目标、六字符映射、链表排列、树路径整数。

## Suggested Next / 下一步

Use `bomb-lab-debugging-workflow` to solve safely, and use this page only as a shape checklist.
