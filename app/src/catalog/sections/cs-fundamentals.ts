import type { CatalogSection } from '../types'
import { nodeLink, quizLink, trackLink } from './helpers'

export const csFundamentalsSection: CatalogSection = {
  id: 'cs-fundamentals',
  number: '01',
  title: 'CS fundamentals',
  eyebrow: 'SYSTEMS FOUNDATION',
  overview: 'The broad systems shelf, separated into tracks so the area remains searchable.',
  guide: 'Choose a track first. Open individual nodes only when you need the detailed explanation or quiz.',
  summary: 'This area is intentionally a directory of tracks, not one giant reading list.',
  links: [
    trackLink('cs-fundamentals', 'c-and-memory', 'C and memory', 'Language characteristics, objects, pointers, and memory vocabulary.'),
    trackLink('cs-fundamentals', 'systems-memory', 'Systems memory', 'Reusable pointer and object reasoning used by optimization and allocation.'),
    trackLink('cs-fundamentals', 'memory-hierarchy', 'Memory hierarchy', 'Virtual memory, page faults, and the boundary between memory and control flow.'),
    trackLink('cs-fundamentals', 'binary-representation', 'Binary representation', 'Two\'s complement, signed overflow, and integer truncation.'),
    trackLink('cs-fundamentals', 'x86-64-assembly', 'x86-64 assembly', 'Registers, addressing, calling convention, and instruction reading.'),
    trackLink('cs-fundamentals', 'gdb-debugging', 'GDB debugging', 'Command reference, stepping, memory inspection, and debugging workflow.'),
    trackLink('cs-fundamentals', 'cache-lab', 'Cache Lab', 'Lecture, roadmap, implementation notes, traces, and testing checklists.'),
    trackLink('cs-fundamentals', 'intro-systems', 'Intro systems', 'Compiler legality, linker symbols, and allocator block accounting.'),
    trackLink('cs-fundamentals', '15-213-written-assignments', '15-213 written assignments', 'WA1–WA9 written assignment guides with worked examples and quizzes.'),
    nodeLink('wa1-binary-representation-and-data-lab', 'WA1: Binary Representation and Data Lab', 'Two\'s complement, signed overflow detection, bitwise C expressions, and integer truncation.'),
    nodeLink('wa2-x86-64-assembly-reading', 'WA2: x86-64 Assembly Reading and Tracing', 'Register-by-register tracing, leaq arithmetic, mov patterns, and conditional jump evaluation.'),
    nodeLink('wa3-control-flow-and-condition-codes', 'WA3: Control Flow and Condition Codes', 'cmp/test flags, conditional jumps, loops in assembly, and switch jump tables.'),
    nodeLink('wa4-stack-frames-and-buffer-overflow', 'WA4: Stack Frames and Buffer Overflow', 'Stack frame layout, buffer overflow exploits, and stack canary protection.'),
    nodeLink('wa5-take-home-midterm', 'WA5: Take-Home Midterm', 'Bitwise parity, assembly recursion, stack layout, and cache analysis.'),
    nodeLink('wa6-virtual-memory', 'WA6: Virtual Memory and Address Translation', 'VM benefits, page faults, and TLB address translation walkthrough.'),
    nodeLink('wa7-dynamic-memory-allocation', 'WA7: Dynamic Memory Allocation', 'Malloc trace tables, coalescing, and free-list implementation comparison.'),
    nodeLink('wa8-systems-optimization-and-linking', 'WA8: Optimization, Linking, and Allocation', 'Compiler, linker, and malloc concepts in one assignment guide.'),
    nodeLink('wa9-exceptional-control-flow', 'WA9: Exceptional Control Flow', 'Processes, waitpid, signals, and asynchronous control flow.'),
    quizLink('gdb-disassemble-stepi-stack-examine', 'GDB disassemble / stepi quiz', 'Practice reading machine state instead of only rereading notes.'),
  ],
  defaultOpen: false,
}
