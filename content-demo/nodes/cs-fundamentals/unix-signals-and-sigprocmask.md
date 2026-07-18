---
slug: unix-signals-and-sigprocmask
title: "Unix Signals and sigprocmask / Unix 信号与信号屏蔽"
area: cs-fundamentals
track: intro-systems
level: intermediate
status: seed
visibility: core
tags: [unix, signals, sigprocmask, sigsegv, sigkill, sigstop]
prerequisites: [c-memory-and-pointers]
related: [fork-process-creation-and-waitpid]
sources:
  - https://man7.org/linux/man-pages/man7/signal.7.html
  - https://man7.org/linux/man-pages/man2/sigprocmask.2.html
summary: "Separate signal masks, pending delivery, handlers, and default actions while reasoning about asynchronous control flow."
---

# Unix Signals and sigprocmask / Unix 信号与信号屏蔽

## What This Solves / 解决什么问题

Signals are small asynchronous notifications delivered by the kernel or
another process. This note gives a precise vocabulary for masks, pending
signals, handlers, and default dispositions, including the fault path for
`SIGSEGV`。

## Core Commands or Code / 核心接口

```c
sigset_t blocked;
sigemptyset(&blocked);
sigaddset(&blocked, SIGINT);
sigprocmask(SIG_BLOCK, &blocked, NULL);
/* critical section: SIGINT may be pending but is not delivered yet */
sigprocmask(SIG_UNBLOCK, &blocked, NULL);
```

`SIG_BLOCK` unions the set with the current mask, `SIG_UNBLOCK` removes it, and
`SIG_SETMASK` replaces the mask. In portable multithreaded code,
[`pthread_sigmask`](https://man7.org/linux/man-pages/man3/pthread_sigmask.3.html)
is preferred.

## Plain Explanation / 通俗解释

Blocking a signal does not erase it. The signal can become pending and is
eligible for delivery when unblocked. A handler is one possible disposition;
the default disposition is another. Keep the three states separate when
tracing control flow.

## Details / 细节

- With the default disposition, an invalid memory access normally raises `SIGSEGV`, terminates the process abnormally, and may produce a core dump subject to system limits. See [C memory and pointers](c-memory-and-pointers.md) for the memory vocabulary behind this fault.
- A parent can observe signal termination through `WIFSIGNALED(status)` and `WTERMSIG(status)` after reaping the child.
- `SIGKILL` and `SIGSTOP` cannot be caught, blocked, or ignored. `SIGKILL` terminates; `SIGSTOP` suspends until an action such as `SIGCONT`.
- `SIGINT` (often Ctrl+C) and `SIGALRM` are ordinary examples whose default termination actions can be changed by a handler.

## Common Mistakes / 常见错误

- Calling a blocked signal “handled”: blocking only delays delivery.
- Assuming all signals can be ignored: `SIGKILL` and `SIGSTOP` are exceptions.
- Treating `SIGSEGV` as a normal recoverable branch without documenting the handler and invalid access that caused it.

## Quick Recall / 快速记忆

**Mask delays delivery; pending records intent; disposition decides the action.**

## Suggested Next / 下一步

Connect this note to [fork, process lifecycles, and waitpid](fork-process-creation-and-waitpid.md)
and trace what a parent observes when a child exits normally versus from a signal.
