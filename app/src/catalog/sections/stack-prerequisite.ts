import type { CatalogSection } from '../types'
import { nodeLink, trackLink } from './helpers'

export const stackPrerequisiteSection: CatalogSection = {
  id: 'stack-prerequisite',
  number: '06',
  title: 'Stack prerequisites',
  eyebrow: 'SECURITY FOUNDATION',
  overview: 'A compact prerequisite shelf for stack frames, calling convention, and memory addresses.',
  guide: 'Use this before Attack Lab or any note that reasons about saved return addresses.',
  summary: 'Draw the stack frame before reasoning about a control-flow overwrite.',
  links: [
    trackLink('stack-prerequisite', 'stack-fundamentals', 'Stack fundamentals', 'All stack prerequisite nodes and their display order.'),
    nodeLink('stack-basics', 'Stack basics', 'The stack as a bounded region with a calling convention.'),
    nodeLink('stack-frame', 'Stack frame structure', 'Saved registers, local variables, and return state.'),
    nodeLink('stack-layout', 'Stack layout and address space', 'Map addresses to frame regions before debugging.'),
  ],
  defaultOpen: false,
}
