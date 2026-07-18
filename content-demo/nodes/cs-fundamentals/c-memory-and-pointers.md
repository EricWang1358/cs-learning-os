---
title: "C Memory and Pointers"
slug: c-memory-and-pointers
area: cs-fundamentals
track: systems-memory
order: 40
status: demo
visibility: core
tags: [c, pointers, stack, heap, memory]
prerequisites: []
related: [virtual-memory-page-faults, x86-64-addressing-and-leaq]
sources: []
summary: "Separate addresses, values, stack lifetime, heap lifetime, and ownership before debugging C code."
---

# C Memory and Pointers

## The Mental Model

A pointer stores an address. Dereferencing a pointer reads or writes the object at that address. Keep the pointer's value separate from the pointed-to value: printing an address does not prove the memory is still valid.

## Stack and Heap

Local variables normally live in a stack frame and cease to exist when the function returns. Dynamically allocated objects live on the heap until `free` releases them. Returning the address of a local variable creates a dangling pointer; using memory after `free` creates another one.

```c
int *make_value(void) {
    int *value = malloc(sizeof(*value));
    if (value == NULL) return NULL;
    *value = 42;
    return value;
}
```

The caller owns the returned allocation and must eventually call `free`. A function should document whether it borrows a pointer, takes ownership, or returns newly owned memory.

## Debugging Checklist

- Is the pointer initialized before dereference?
- Does the target object still have a valid lifetime?
- Is the allocated size correct for the element type and count?
- Is there exactly one owner responsible for releasing the allocation?

## Common Mistake

`sizeof(pointer)` is the size of the address itself, not the size of the array it points to. Pass array lengths explicitly when a function needs bounds.
