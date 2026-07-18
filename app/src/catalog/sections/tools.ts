import type { CatalogSection } from '../types'
import { areaLink } from './helpers'

export const toolsSection: CatalogSection = {
  id: 'tools',
  number: '10',
  title: 'Tools and maintenance',
  eyebrow: 'OPERATIONS',
  overview: 'Small operational notes for keeping the local-first learning environment healthy.',
  guide: 'Use these only when a workflow or environment issue blocks learning work.',
  summary: 'Maintenance is a support lane, not a replacement for the learning tracks.',
  links: [
    areaLink('tools', 'All tool notes', 'Browse operational notes and environment helpers.'),
    { title: 'System health', href: '/health', description: 'Inspect storage, database, generated files, and service readiness.', kind: 'route' },
    { title: 'Desktop / mobile sync', href: '/sync', description: 'Pair devices and inspect LAN sync status.', kind: 'route' },
  ],
  defaultOpen: false,
}
