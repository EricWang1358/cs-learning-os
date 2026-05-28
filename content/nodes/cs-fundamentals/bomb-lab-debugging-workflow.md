---
title: "Bomb Lab Debugging Workflow / Bomb Lab 调试工作流"
area: cs-fundamentals
track: bomb-lab
order: 10
status: seed
visibility: core
tags: [bomb-lab, gdb, assembly, x86-64, csapp, debugging]
prerequisites: [gdb-basics, gdb-disassemble, gdb-stepi, gdb-examine-memory, x86-64-instruction-cheatsheet]
related: [bomb-lab-answer-shapes, x86-64-cmp-and-jumps, x86-64-addressing-and-leaq, x86-64-registers]
sources:
  - https://csapp.cs.cmu.edu/3e/bomblab.pdf
  - https://sourceware.org/gdb/current/onlinedocs/gdb.html/Machine-Code.html
  - https://sourceware.org/gdb/current/onlinedocs/gdb.html/Memory.html
summary: "A safe repeatable workflow for reading Bomb Lab phases with GDB and x86-64 assembly."
---

# Bomb Lab Debugging Workflow / Bomb Lab 调试工作流

## What This Solves / 解决什么问题

English: Bomb Lab feels scary because one wrong input can explode. The real skill is not guessing answers; it is building a repeatable loop for reading each phase safely.

中文：Bomb Lab 吓人的地方在于输错就爆。但真正要练的不是猜答案，而是建立一个稳定流程，安全地读懂每个 phase。

## Core Commands or Code / 核心命令或代码

Start with static inspection:

```bash
objdump -d ./bomb > bomb.asm
strings ./bomb | less
```

Then use GDB:

```gdb
gdb ./bomb
break phase_1
break explode_bomb
run
disassemble phase_1
layout asm
info registers
x/s $rdi
x/20gx $rsp
stepi
nexti
continue
```

Useful pattern:

```gdb
break phase_3
run answers.txt
disassemble phase_3
```

## Plain Explanation / 通俗解释

English: For each phase, your job is to recover the input rule. The assembly usually says: read input, transform or compare values, and call `explode_bomb` if a check fails.

中文：每个 phase 的目标都是还原“输入规则”。汇编通常在做三件事：读输入、转换或比较值、检查失败就调用 `explode_bomb`。

English: Do not start by stepping randomly. First sketch the control flow: where are the comparisons, where are the jumps, and which path reaches `explode_bomb`?

中文：不要一上来乱 `stepi`。先画控制流：比较在哪里？跳转在哪里？哪条路径会到 `explode_bomb`？

## Reader Questions / 读者追问

Question: How do I avoid exploding the bomb while exploring?

Answer: Set a breakpoint on `explode_bomb`. If execution stops there, inspect the path and restart with a better input. You can also avoid continuing into it once you hit the breakpoint.

问题：探索时怎么避免真的爆？

回答：给 `explode_bomb` 设置断点。如果程序停在那里，说明当前路径失败了；这时检查路径，然后用更好的输入重跑。停在断点后不要继续执行进去。

Question: Should I use online Bomb Lab answers?

Answer: Use official handouts and instruction references for method. Avoid answer dumps unless you are comparing after solving, because copied answers do not train the reverse-engineering skill.

问题：要不要直接看网上 Bomb Lab 答案？

回答：方法上用官方 handout 和指令资料；不要一开始看答案流。直接抄答案不会训练逆向阅读能力。

## Phase Reading Checklist / Phase 阅读清单

- Find the phase function with `disassemble phase_N`.
- Mark calls to parsing helpers such as `sscanf`, `read_six_numbers`, or string comparison helpers.
- Identify all `cmp`/`test` instructions.
- Translate each conditional jump into a plain-language condition.
- Mark any edge that reaches `explode_bomb` as a failure path.
- Track input-derived values in registers and stack slots.
- Convert the surviving path into an input rule.
- Test with GDB before trusting the answer.

## Common Patterns / 常见套路

- String equality: compare your input with a hidden string.
- Number sequence: read several integers, then enforce a recurrence.
- Switch/jump table: one input chooses a case; another must match the case result.
- Recursion/search: input must produce a specific return value.
- Linked list/tree: input reorders or searches data structures stored in memory.

## Common Mistakes / 常见错误

- English: Treating `explode_bomb` as mysterious instead of just a failure branch.
- 中文：把 `explode_bomb` 神秘化，而不是把它看成失败分支。
- English: Reading `cmp` backwards and inverting the required input rule.
- 中文：把 `cmp` 读反，导致输入规则整体反过来。
- English: Ignoring helper calls like `sscanf`, then not knowing where inputs enter registers or stack.
- 中文：忽略 `sscanf` 这类辅助调用，导致不知道输入值进入了哪些寄存器或栈位置。
- English: Stepping instruction by instruction before drawing the rough control flow.
- 中文：还没画粗略控制流就开始逐条指令乱走。

## Quick Recall / 快速记忆

English: Bomb Lab loop = disassemble, mark failure paths, track input values, derive rule, test safely.

中文：Bomb Lab 循环 = 反汇编、标失败路径、追踪输入值、推出规则、安全测试。

## Suggested Next / 下一步

Use `bomb-lab-answer-shapes` to see what solved answers commonly look like. Use `x86-64-instruction-cheatsheet` while reading phases, then turn each phase-style prompt into a Standard Q quiz item.
