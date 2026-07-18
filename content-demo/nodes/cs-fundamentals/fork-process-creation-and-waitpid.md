---
slug: fork-process-creation-and-waitpid
title: "fork, Process Lifecycles, and waitpid / 进程创建与等待"
area: cs-fundamentals
track: intro-systems
level: intermediate
status: seed
visibility: core
tags: [c, unix, fork, waitpid, process-lifecycle, zombie, orphan]
prerequisites: []
related: [unix-signals-and-sigprocmask]
sources:
  - https://man7.org/linux/man-pages/man2/fork.2.html
  - https://man7.org/linux/man-pages/man2/waitpid.2.html
summary: "Reason about process trees, child ownership, waitpid errors, and zombie or orphan outcomes without guessing output order."
---

# fork, Process Lifecycles, and waitpid / 进程创建与等待

## What This Solves / 解决什么问题

Use this node when a C program calls `fork()` and the question asks about the
process graph, possible interleavings, or whether a child is reaped correctly。
它关注可复用的进程生命周期规则，而不是某一道作业的固定输出。

## Core Commands or Code / 核心接口

```c
pid_t child = fork();
if (child == 0) {
    /* child path */
} else if (child > 0) {
    /* parent path; child is the returned PID */
    int status;
    if (waitpid(child, &status, 0) == -1) {
        /* inspect errno: ECHILD, EINTR, or another documented error */
    }
}
```

`fork` returns once in each process: zero in the child and the child's PID in
the parent. Only the process that executes a later `fork` creates that branch.
`waitpid` waits for a matching child state change and lets the parent inspect
`WIFEXITED`, `WEXITSTATUS`, or `WIFSIGNALED`.

## Plain Explanation / 通俗解释

Treat every `fork` as a graph split, not as a duplicated line in one timeline.
Then mark the ownership of each child. A parent waiting for a direct child does
not automatically wait for a grandchild. If a child exits before its parent
reaps it, it can remain a zombie; if its parent exits first, it can be
reparented as an orphan.

## Details / 细节

- `waitpid(pid, ...)` is both an ownership check and a synchronization point.
- `ECHILD` means no matching unwaited child exists; `EINTR` means a caught signal interrupted a blocking wait; `EINVAL` indicates invalid option flags.
- Save the PID returned by `fork` when a later wait must target that exact child.
- Check every system-call return value. A child with no children should not call `waitpid(-1, ...)` and silently assume it synchronized with a descendant.

## Common Mistakes / 常见错误

- Treating `fork() != 0` as “the original process” after another nested fork; identify each process by its own return value.
- Assuming `printf` order is deterministic when no synchronization edge exists.
- Waiting for one child and claiming that all descendants have finished.

## Quick Recall / 快速记忆

**Fork creates; waitpid owns and orders; reaping prevents zombies.**

## Suggested Next / 下一步

Review [Unix signals and sigprocmask](unix-signals-and-sigprocmask.md) to see how
asynchronous events interact with a process blocked in `waitpid`.
