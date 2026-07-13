---
title: "Database Indexes"
area: cs-fundamentals
track: databases
order: 60
status: demo
visibility: core
tags: [database, sql, index, query-plan]
prerequisites: []
related: [http-request-lifecycle, project-crud-app]
sources: []
summary: "Use indexes to reduce row scans while accounting for write cost, selectivity, and query shape."
---

# Database Indexes

## What An Index Does

An index is an auxiliary data structure that lets the database locate rows without reading every row in a table. A B-tree index is effective for equality lookups, ordered ranges, and many sorts because it keeps keys in searchable order.

## Design From Queries

Start with the query, not the table. For a query filtering by `user_id` and ordering by `created_at`, an index beginning with `user_id` and then `created_at` can help both operations. Use the database query plan to confirm the optimizer actually uses it.

```sql
CREATE INDEX messages_user_created_at
ON messages (user_id, created_at DESC);
```

## Trade-offs

Indexes consume storage and make inserts, updates, and deletes more expensive because each affected index must also change. Low-selectivity columns such as a boolean often provide little value alone, but may be useful after a selective leading column in a composite index.

## Common Mistakes

An index is not a blanket performance fix. Wrapping the indexed column in a function, applying a leading wildcard search, or filtering only on a later column of a composite index can prevent the intended lookup strategy.
