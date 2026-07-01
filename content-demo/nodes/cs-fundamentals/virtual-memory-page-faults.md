---
title: "Virtual Memory and Page Faults"
area: cs-fundamentals
track: memory-hierarchy
order: 20
status: demo
visibility: core
tags: [virtual-memory, page-table, tlb, page-fault]
prerequisites: []
related: [x86-64-addressing-and-leaq]
sources: []
summary: "Connect virtual addresses, page tables, TLB misses, and page faults into one reviewable story."
---

# Virtual Memory and Page Faults

## Core Idea

Virtual memory lets each process act as if it owns a large private address space. The hardware and operating system translate virtual addresses into physical frames.

## Address Translation

The CPU first checks the TLB. If the translation is cached, the memory access can continue quickly. If the TLB misses, hardware or the OS walks the page table to find the mapping.

## Page Fault

A page fault happens when the page table says the requested virtual page is not currently usable. The operating system may load it from disk, allocate a fresh page, or terminate the program if the access is invalid.

## Common Mistake

A TLB miss is not automatically a page fault. A TLB miss means the translation cache missed; a page fault means the page table entry does not permit the access.

