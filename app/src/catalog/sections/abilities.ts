import type { CatalogSection } from '../types'
import { areaLink, nodeLink } from './helpers'

export const abilitiesSection: CatalogSection = {
  id: 'abilities',
  number: '08',
  title: 'Learning abilities',
  eyebrow: 'META SKILLS',
  overview: 'Operational habits for debugging, asking better questions, and turning uncertainty into evidence.',
  guide: 'Return here whenever a technical problem feels vague or a fix is becoming a sequence of guesses.',
  summary: 'The debugging loop is a transferable skill across every area in the catalog.',
  links: [
    areaLink('abilities', 'All ability nodes', 'Browse the complete ability and workflow shelf.'),
    nodeLink('debugging-loop', 'Debugging Loop', 'Reproduce, isolate, hypothesize, test, and verify.'),
  ],
  defaultOpen: false,
}
