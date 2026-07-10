---
title: "Debugging Loop"
area: abilities
track: engineering-practice
order: 10
status: demo
visibility: core
tags: [debugging, hypothesis, testing, logs]
prerequisites: []
related: [c-memory-and-pointers, http-request-lifecycle]
sources: []
summary: "Debug by reproducing, measuring, testing one hypothesis, and preserving the regression case."
---

# Debugging Loop

## A Repeatable Loop

1. Make the failure reproducible and record the smallest input that shows it.
2. State one falsifiable hypothesis about where the expected and actual behavior diverge.
3. Collect the cheapest decisive evidence: a focused test, log, debugger breakpoint, or query plan.
4. Change the smallest responsible code path, then rerun the reproduction and nearby regression tests.
5. Keep the test or documented reproduction so the same defect cannot silently return.

## Why This Works

Random edits can hide symptoms and create new failures. A hypothesis gives every observation a purpose. The goal is not to accumulate logs; it is to find the first point at which the system violates an invariant.

## Practical Questions

- What input and state are required to reproduce the failure?
- What should be true immediately before the failure?
- Which boundary converts correct data into incorrect data?
- What evidence would prove the current theory wrong?

## Common Mistake

Do not call a bug fixed because the UI happened to look correct once. Verify the original reproduction, edge cases around it, and the automated tests that protect the contract.
