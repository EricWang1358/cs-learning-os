---
title: "Virtual Memory: TLB miss versus page fault"
area: cs-fundamentals
track: memory-hierarchy
status: demo
visibility: practice
difficulty: easy
weight: 1
tags: [virtual-memory, tlb, page-fault]
linked_nodes: [virtual-memory-page-faults]
sources: []
summary: "Practice distinguishing a translation-cache miss from an invalid or unavailable page."
---

# Virtual Memory: TLB miss versus page fault

## Prompt

What is the difference between a TLB miss and a page fault?

## Answer

A TLB miss means the CPU did not find a cached virtual-to-physical translation in the TLB, so it must consult the page table. A page fault means the page table entry says the requested access cannot proceed directly, so the operating system must handle it.

## Explanation

The two events are related but not the same. A TLB miss can still find a valid page-table entry and continue normally after filling the TLB. A page fault is stronger: the page may be absent, protected, swapped out, or invalid, so control transfers to the OS page-fault handler.

