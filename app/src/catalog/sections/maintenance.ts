import type { CatalogSection } from '../types'
import { areaLink } from './helpers'

export const maintenanceSection: CatalogSection = {
  id: 'maintenance',
  number: '11',
  title: 'Generated and maintenance nodes',
  eyebrow: 'SYSTEM GENERATED',
  overview: 'A clearly separated shelf for generated stubs and temporary graph fixtures.',
  guide: 'Do not use this as a study route. Open it only when auditing generated content or cleanup state.',
  summary: 'Generated content stays visible without polluting the curated learning path.',
  links: [
    areaLink('stubs', 'Generated stubs', 'Browse generated or placeholder nodes in Library.'),
    { title: 'System health', href: '/health', description: 'Use repair and content-root checks before deleting generated material.', kind: 'route' },
  ],
  defaultOpen: false,
}
