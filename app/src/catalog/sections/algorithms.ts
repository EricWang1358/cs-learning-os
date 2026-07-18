import type { CatalogSection } from '../types'
import { nodeLink, trackLink } from './helpers'

export const algorithmsSection: CatalogSection = {
  id: 'algorithms',
  number: '02',
  title: 'Algorithms and data structures',
  eyebrow: 'PROBLEM SOLVING',
  overview: 'From reusable problem patterns to graph algorithms, hashing, and complexity theory.',
  guide: 'Start with a pattern track, then use the graph or complexity tracks when the problem demands a stronger model.',
  summary: 'The algorithm is the invariant and proof, not just the implementation template.',
  links: [
    trackLink('algorithms', 'general', 'Core patterns', 'Binary search, graph traversal, and general problem-solving habits.'),
    trackLink('algorithms', 'analysis', 'Analysis', 'Amortized reasoning and performance accounting.'),
    trackLink('algorithms', 'data-structures', 'Data structures', 'Hashing, Bloom filters, and data-structure trade-offs.'),
    trackLink('algorithms', 'graph-algorithms', 'Graph algorithms', 'Representations, BFS, DFS, and graph exploration.'),
    trackLink('algorithms', 'graphs', 'Advanced graph topics', 'Strongly connected components and graph condensation.'),
    trackLink('algorithms', 'complexity-theory', 'Complexity theory', 'P, NP, NP-completeness, approximation, and coping strategies.'),
    nodeLink('binary-search', 'Binary Search', 'A monotonic condition and a precise boundary invariant.'),
    nodeLink('graph-traversal', 'Graph Traversal', 'A compact entry point for BFS and DFS reasoning.'),
    nodeLink('strongly-connected-components', 'Strongly Connected Components', 'Condense a directed graph into a DAG of components.'),
  ],
  defaultOpen: false,
}
