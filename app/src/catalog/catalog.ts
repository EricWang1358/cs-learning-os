import { abilitiesSection } from './sections/abilities'
import { algorithmsSection } from './sections/algorithms'
import { attackLabSection } from './sections/attacklab'
import { csFundamentalsSection } from './sections/cs-fundamentals'
import { knowledgeGraphSection } from './sections/knowledge-graph'
import { maintenanceSection } from './sections/maintenance'
import { networkProgrammingSection } from './sections/networkprogramming'
import { orientationSection } from './sections/orientation'
import { projectsSection } from './sections/projects'
import { rLanguageSection } from './sections/r-language'
import { stackPrerequisiteSection } from './sections/stack-prerequisite'
import { toolsSection } from './sections/tools'
import type { CatalogSection } from './types'

export type { CatalogLink, CatalogSection } from './types'

// Keep this assembly file intentionally small. Agent edits should normally be
// limited to one file under catalog/sections/.
export const catalogSections: CatalogSection[] = [
  orientationSection,
  csFundamentalsSection,
  algorithmsSection,
  attackLabSection,
  networkProgrammingSection,
  rLanguageSection,
  stackPrerequisiteSection,
  projectsSection,
  abilitiesSection,
  knowledgeGraphSection,
  toolsSection,
  maintenanceSection,
]
