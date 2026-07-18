import type { CatalogSection } from '../types'
import { areaLink, nodeLink } from './helpers'

export const projectsSection: CatalogSection = {
  id: 'projects',
  number: '07',
  title: 'Projects and reusable patterns',
  eyebrow: 'BUILD',
  overview: 'Convert implementation work into durable patterns that can be reused in future projects.',
  guide: 'Open the project area for the complete live collection, then promote recurring design decisions into nodes.',
  summary: 'A project note should preserve decisions, trade-offs, and verification evidence.',
  links: [
    areaLink('projects', 'All project nodes', 'Browse every project pattern in Library.'),
    nodeLink('project-crud-app', 'CRUD application pattern', 'A reusable application structure and workflow.'),
  ],
  defaultOpen: false,
}
