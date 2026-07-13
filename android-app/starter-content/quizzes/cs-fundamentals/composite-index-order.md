---
title: "Composite Index Order"
area: cs-fundamentals
track: databases
status: demo
visibility: practice
difficulty: medium
tags: [database, sql, index]
linked_nodes: [database-indexes]
sources: []
summary: "Choose composite index columns from filtering and ordering behavior."
---

# Composite Index Order

## Prompt

A frequently used query filters messages by `user_id` and lists them ordered by newest `created_at`. What composite index is a strong starting point, and why is it not free?

## Answer

A strong starting point is an index beginning with `user_id` and then `created_at`, matching the equality filter before the ordered range. It is not free because the index consumes storage and every insert, update, or delete affecting those columns must maintain it. Confirm the choice with the actual query plan and workload.

## Explanation

The leading index column determines which prefix a B-tree can seek efficiently. Once rows for one user are grouped, the secondary time order can often satisfy the requested sort without an additional pass. Index design remains workload-specific: a rarely used query may not justify slower writes or another large on-disk structure.
