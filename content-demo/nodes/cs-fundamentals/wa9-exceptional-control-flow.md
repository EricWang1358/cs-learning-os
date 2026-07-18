---
slug: wa9-exceptional-control-flow
title: "WA9: Exceptional Control Flow / 异常控制流"
area: cs-fundamentals
track: 15-213-written-assignments
level: intermediate
status: seed
visibility: support
tags: [csapp, exceptional-control-flow, processes, signals, synchronization]
prerequisites: [fork-process-creation-and-waitpid, unix-signals-and-sigprocmask]
related: []
sources:
  - https://man7.org/linux/man-pages/man2/fork.2.html
  - https://man7.org/linux/man-pages/man2/waitpid.2.html
  - https://man7.org/linux/man-pages/man7/signal.7.html
summary: "A reusable map of how processes, waits, and signals change the normal sequential control-flow story."
---

# WA9: Exceptional Control Flow / 异常控制流

## What This Solves / 解决什么问题

Exceptional control flow (ECF) explains why a program can stop following one
straight-line instruction stream. A process can create another process, wait
for a state change, or receive a signal that transfers control to a handler or
the default action。它把进程、同步和信号放在同一张可检查的图上。

## Core Idea / 核心概念

The durable reasoning order is:

1. Draw process-creation edges and record which branch each process executes.
2. Add synchronization edges such as [`waitpid`](https://man7.org/linux/man-pages/man2/waitpid.2.html) before inferring output order.
3. Add signal delivery and default dispositions only after the process graph is clear.

An output ordering is possible only when it respects every synchronization edge;
concurrent branches may otherwise interleave. Do not treat a process tree as a
total order.

## Concept Map / 概念导航

- [fork, process lifecycles, and waitpid](fork-process-creation-and-waitpid.md) explains process ownership, reaping, and wait errors.
- [Unix signals and sigprocmask](unix-signals-and-sigprocmask.md) explains masks, pending delivery, and dispositions.
- A TLB miss or page fault is a memory event, not automatically a process-control event; compare [virtual memory and page faults](virtual-memory-page-faults.md).

## Study Checklist / 学习检查

- Can I distinguish a child PID return value of `0` from the parent's positive child PID?
- Can I state which process owns a child before calling `waitpid`?
- Can I separate a blocked signal from a pending signal and from a delivered signal?
- Can I name which termination information a parent can observe?

## Common Confusions / 常见混淆

- `waitpid` synchronizes a parent with a matching child; it does not wait for arbitrary grandchildren.
- A TLB miss, a signal, and a page fault are different events. This node is about control-flow transfer, not every kernel event.
- A signal's default action is not the same as a user-installed handler.

## Suggested Next / 下一步

Read [fork, process lifecycles, and waitpid](fork-process-creation-and-waitpid.md) first, then
[Unix signals and sigprocmask](unix-signals-and-sigprocmask.md) for asynchronous delivery and masks.
