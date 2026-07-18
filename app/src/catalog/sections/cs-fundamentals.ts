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
    trackLink('cs-fundamentals', '15-213-written-assignments', '15-213 written assignments', 'WA8 and WA9 roots with their reusable prerequisite concepts.'),
    nodeLink('wa8-systems-optimization-and-linking', 'WA8: Optimization, Linking, and Allocation', 'Compiler, linker, and malloc concepts in one assignment guide.'),
    nodeLink('wa9-exceptional-control-flow', 'WA9: Exceptional Control Flow', 'Processes, waitpid, signals, and asynchronous control flow.'),
    quizLink('gdb-disassemble-stepi-stack-examine', 'GDB disassemble / stepi quiz', 'Practice reading machine state instead of only rereading notes.'),
  ],
  defaultOpen: false,
}
