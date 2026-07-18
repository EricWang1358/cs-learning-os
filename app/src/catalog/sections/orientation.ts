import type { CatalogSection } from '../types'

export const orientationSection: CatalogSection = {
  id: 'orientation',
  number: '00',
  title: 'Reader orientation',
  eyebrow: 'START HERE',
  overview: 'A short route through the workbench before you commit to a topic.',
  guide: 'Use Library for live content and this catalog for the human-curated order of study.',
  summary: 'The catalog is a map, not a second database.',
  links: [
    { title: 'Home dashboard', href: '/', description: 'Resume reading, review, and open the main tools.', kind: 'route' },
    { title: 'Library', href: '/nodes', description: 'Search and manage the complete live node collection.', kind: 'route' },
    { title: 'Knowledge graph', href: '/knowledge-graph', description: 'Inspect prerequisite direction and graph structure.', kind: 'route' },
    { title: 'Review queue', href: '/review', description: 'Convert quiz answers into a spaced review loop.', kind: 'route' },
    { title: 'Daily Bite', href: '/bite', description: 'Run a small recall exercise when time is limited.', kind: 'route' },
  ],
  defaultOpen: true,
}
