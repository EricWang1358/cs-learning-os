---
id: shark-tank-passcode-calling-convention
title: "Shark Tank Passcode: process_code and is_valid_code"
area: cs-fundamentals
status: seed
visibility: practice
difficulty: medium
weight: 1
tags: [assembly, x86-64, calling-convention, leaq, cmp, sete]
linked_nodes: [x86-64-calling-convention, x86-64-registers, x86-64-mov-and-suffixes, x86-64-addressing-and-leaq, x86-64-cmp-and-jumps]
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

### Step 0: Decide What The C Code Wants

The wrapper says:

```c
if (is_valid_code(code)) {
    printf("Escaped 15-xx3!\n");
}
```

So the passcode is whatever makes `is_valid_code(code)` return true.

That means we need to answer two smaller questions:

```text
1. What does process_code(code) compute?
2. What value does is_valid_code require process_code(code) to equal?
```

### Step 1: How The Argument Enters `process_code`

`escape_tank` calls:

```c
is_valid_code(code)
```

On x86-64 System V, the first integer argument is passed in `%edi` / `%rdi`.

So when `is_valid_code` begins:

```text
%edi contains code
%rdi contains the same value in the 64-bit register family
```

The first instruction in `is_valid_code` is:

```asm
callq  401132 <process_code>
```

There is no instruction before the call that changes `%edi` or `%rdi`. Therefore `process_code` receives the same first argument:

```text
process_code(code)
```

This answers part b: `is_valid_code` passes its argument by leaving it in the first argument register, `%edi` / `%rdi`, before calling `process_code`.

### Step 2: Read `process_code` Line By Line

```asm
lea    (%rdi,%rdi,1), %edx
```

`lea` computes the address-shaped expression as arithmetic.

The expression format is:

```text
D(base,index,scale) = D + base + index * scale
```

Here:

```text
D = 0
base = %rdi
index = %rdi
scale = 1
```

So:

```text
%edx = code + code = 2 * code
```

State after this line:

```text
%edx = 2 * code
```

Then:

```asm
mov    $0x137, %eax
```

This initializes a counter:

```text
%eax = 0x137
```

Then the loop starts:

```asm
sub    $0x1, %eax
jne    40113a
```

`sub $0x1, %eax` means:

```text
%eax = %eax - 1
```

`jne 40113a` means:

```text
if the previous subtraction did not produce zero, jump back and subtract again
```

So this loop counts down:

```text
0x137, 0x136, 0x135, ..., 0x2, 0x1, 0x0
```

When `%eax` finally becomes `0`, `jne` is not taken, and execution falls through.

Important observation:

```text
The loop changes %eax.
The loop does not change %edx.
```

Since `%edx` still holds `2 * code`, the loop is noise for the final formula. It makes the code look busier, but it does not change the input-derived value.

Finally:

```asm
lea    0x137(%rdx), %eax
retq
```

Again use the `lea` expression rule:

```text
0x137(%rdx) = 0x137 + %rdx
```

Since `%rdx` / `%edx` holds `2 * code`, this computes:

```text
%eax = %edx + 0x137
%eax = 2 * code + 0x137
```

Then `retq` returns with `%eax` as the integer return value.

So:

```c
process_code(code) returns 2 * code + 0x137
```

### Step 3: Read `is_valid_code` After The Call

```asm
callq  process_code
cmp    $0x7a69, %eax
sete   %al
movzbl %al, %eax
retq
```

After the `callq`, `%eax` contains the return value from `process_code`.

So this line:

```asm
cmp    $0x7a69, %eax
```

compares:

```text
%eax ? 0x7a69
```

Because AT&T `cmp A, B` sets flags as if computing `B - A`, this is checking whether:

```text
process_code(code) == 0x7a69
```

Then:

```asm
sete %al
```

means:

```text
if the comparison was equal:
    %al = 1
else:
    %al = 0
```

Then:

```asm
movzbl %al, %eax
```

means:

```text
zero-extend the 8-bit value in %al into the 32-bit register %eax
```

So the function returns a clean C-style boolean:

```text
return 1 if process_code(code) == 0x7a69
return 0 otherwise
```

### Step 4: Solve The Equation

We need:

```text
2 * code + 0x137 = 0x7a69
```

Move `0x137` to the other side:

```text
2 * code = 0x7a69 - 0x137
```

Do the hex subtraction carefully:

```text
  0x7a69
- 0x0137
= 0x7932
```

So:

```text
2 * code = 0x7932
```

Divide by 2:

```text
code = 0x3c99
```

Decimal check, if the input expects decimal:

```text
0x3c99 = 15513
```

Final passcode:

```text
0x3c99
```

or decimal:

```text
15513
```

### Why Not `0x7a69`?

`0x7a69` is not the input code. It is the required output of `process_code(code)`.

The program checks:

```text
process_code(code) == 0x7a69
```

not:

```text
code == 0x7a69
```

That distinction is the whole puzzle.

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
- Review `x86-64-mov-and-suffixes` for `mov`, instruction suffixes, and source/destination operand types.
- Review `x86-64-addressing-and-leaq` for the two `lea` instructions.
- Review `x86-64-cmp-and-jumps` for `cmp` and equality checks.
- Review `x86-64-registers` for `%eax`, `%edx`, `%edi`, and their 64-bit parents.
