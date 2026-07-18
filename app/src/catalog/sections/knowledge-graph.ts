import type { CatalogSection } from '../types'
import { areaLink } from './helpers'

export const knowledgeGraphSection: CatalogSection = {
  id: 'knowledge-graph',
  number: '09',
  title: 'Knowledge graph demonstrations',
  eyebrow: 'GRAPH LAB',
  overview: 'Graph-backed examples that make prerequisites, mastery, and rerooting visible.',
  guide: 'Use the 3D graph for structure and the ordinary node links for reading the source material.',
  summary: 'The graph is a navigation and reasoning surface; Markdown remains the source of truth.',
  links: [
    { title: 'Open the 3D knowledge graph', href: '/knowledge-graph', description: 'Explore layers, reroot a node, and inspect edge direction.', kind: 'route' },
    { title: 'Open graph navigator', href: '/graph', description: 'Browse the same knowledge structure as a paginated hierarchy.', kind: 'route' },
    areaLink('knowledge-graph', 'Graph demonstration nodes', 'Browse the graph-specific demo nodes in Library.'),
  ],
  defaultOpen: false,
}
