---
id: shark-tank-passcode-calling-convention
title: "Shark Tank Passcode: process_code and is_valid_code"
area: cs-fundamentals
status: seed
visibility: practice
difficulty: medium
weight: 1
tags: [assembly, x86-64, calling-convention, leaq, cmp, sete]
linked_nodes: [x86-64-calling-convention, x86-64-addressing-and-leaq, x86-64-cmp-and-jumps, x86-64-registers]
sources:
  - local-exam-screenshot-2026-05-28
summary: "Practice deriving a passcode from x86-64 assembly and explaining register-based argument passing."
---

# Shark Tank Passcode: `process_code` and `is_valid_code`

## Prompt

You are given this C wrapper:

```c
void escape_tank(int code) {
    if (is_valid_code(code)) {
        printf("Escaped 15-xx3!\n");
    } else {
        printf("Try Again.\n");
    }
}
```

But you only have assembly for `process_code` and `is_valid_code`:

```asm
0000000000401132 <process_code>:
401132: 8d 14 3f        lea    (%rdi,%rdi,1), %edx
401135: b8 37 01 00 00  mov    $0x137, %eax
40113a: 83 e8 01        sub    $0x1, %eax
40113d: 75 fb           jne    40113a <process_code+0x8>
40113f: 8d 82 37 01 00 00 lea   0x137(%rdx), %eax
401145: c3              retq

0000000000401146 <is_valid_code>:
401146: e8 e7 ff ff ff  callq  401132 <process_code>
40114b: 3d 69 7a 00 00  cmp    $0x7a69, %eax
401150: 0f 94 c0        sete   %al
401153: 0f b6 c0        movzbl %al, %eax
401156: c3              retq
```

Questions:

- What passcode is required to escape?
- How does `is_valid_code` pass its argument to `process_code`?

## Answer

- Required passcode: `0x3c99`, which is `15513` in decimal.
- `is_valid_code` passes the argument by leaving it in `%rdi` / `%edi`, the first integer argument register, and then calling `process_code`.

## Explanation

First read `process_code`.

```asm
lea    (%rdi,%rdi,1), %edx
```

This computes:

```text
%edx = code + code = 2 * code
```

Then:

```asm
mov    $0x137, %eax
sub    $0x1, %eax
jne    40113a
```

This is a countdown loop. It starts `%eax = 0x137`, subtracts 1 until `%eax` becomes 0, and then falls through. The loop wastes time; it does not change `%edx`.

Finally:

```asm
lea    0x137(%rdx), %eax
retq
```

This computes:

```text
%eax = %edx + 0x137
%eax = 2 * code + 0x137
```

Now read `is_valid_code`:

```asm
callq  process_code
cmp    $0x7a69, %eax
sete   %al
movzbl %al, %eax
retq
```

It checks whether:

```text
process_code(code) == 0x7a69
```

So:

```text
2 * code + 0x137 = 0x7a69
2 * code = 0x7a69 - 0x137
2 * code = 0x7932
code = 0x3c99
```

Decimal check:

```text
0x3c99 = 15513
```

## Plain Explanation

English: The trick is that `process_code` looks busier than it is. The countdown loop is noise. The real formula is just `2 * code + 0x137`.

中文：这题的陷阱是 `process_code` 看起来很忙，其实倒计时循环只是噪音。真正的公式只是 `2 * code + 0x137`。

English: `is_valid_code` does not move the argument before calling `process_code`. That matters: on x86-64, the first integer argument is already in `%edi`, so `process_code` receives the same `code`.

中文：`is_valid_code` 在调用 `process_code` 前没有移动参数。这很关键：x86-64 中第一个整数参数已经在 `%edi`，所以 `process_code` 收到的还是同一个 `code`。

## What This Tests

- `leaq` arithmetic: `(%rdi,%rdi,1)` means `2 * code`.
- Recognizing loop noise that does not affect the final formula.
- Return values in `%eax`.
- `cmp` plus `sete`: turn equality into 1 or 0.
- Calling convention: first integer argument in `%edi`.

## Linked Review

- Review `x86-64-calling-convention` for why the argument is passed through `%edi`.
- Review `x86-64-addressing-and-leaq` for the two `lea` instructions.
- Review `x86-64-cmp-and-jumps` for `cmp` and equality checks.
- Review `x86-64-registers` for `%eax`, `%edx`, `%edi`, and their 64-bit parents.
