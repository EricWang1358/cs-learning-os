import type { CatalogSection } from '../types'
import { nodeLink, trackLink } from './helpers'

export const attackLabSection: CatalogSection = {
  id: 'attacklab',
  number: '03',
  title: 'Attack Lab',
  eyebrow: 'SECURITY PRACTICE',
  overview: 'A phase-by-phase exploit reasoning route with separate core and analysis notes.',
  guide: 'Read the first-steps and debugging workflow before opening a phase-specific note.',
  summary: 'Every phase is a verified control-flow argument, not a payload-copying exercise.',
  links: [
    trackLink('attacklab', 'buffer-overflow', 'Buffer overflow phases', 'Target setup, stack layout, code injection, and phase 1-3 analysis.'),
    trackLink('attacklab', 'rop-exploitation', 'ROP exploitation phases', 'Gadget selection, runtime pointers, and phase 4-5 reasoning.'),
    nodeLink('attacklab-first-steps-from-target-tar', 'First steps from target tar', 'Prepare the target and record the evidence before debugging.'),
    nodeLink('attacklab-debugging-workflow', 'Attack Lab debugging workflow', 'A disciplined workflow for input, stack, and return-address checks.'),
    nodeLink('attacklab-phase4-core', 'Phase 4 core', 'The verified ROP version of the touch2 goal.'),
    nodeLink('attacklab-phase5-core', 'Phase 5 core', 'Rebuild the touch3 goal with a runtime cookie-string pointer.'),
  ],
  defaultOpen: false,
}
