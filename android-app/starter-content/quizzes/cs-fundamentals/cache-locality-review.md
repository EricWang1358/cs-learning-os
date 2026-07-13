---
title: "Cache Locality Review"
area: cs-fundamentals
track: memory-hierarchy
---

# Cache Locality Review

## Prompt

Why does accessing nearby memory often run faster than jumping around randomly?

## Answer

Nearby accesses reuse cache lines, so the CPU can serve more loads from cache instead of slower main memory.

## Explanation

Spatial locality means one fetched cache line contains neighboring bytes that later operations may need.
