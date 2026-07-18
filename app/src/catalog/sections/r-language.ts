import type { CatalogSection } from '../types'
import { nodeLink, trackLink } from './helpers'

export const rLanguageSection: CatalogSection = {
  id: 'r-language',
  number: '05',
  title: 'R language and statistical computing',
  eyebrow: 'DATA WORK',
  overview: 'A staged R route from data structures through manipulation, modeling, visualization, and packages.',
  guide: 'Learn the data model first; only then layer on vectorized manipulation and statistical modeling.',
  summary: 'The useful abstraction is a reproducible data transformation, not a collection of isolated functions.',
  links: [
    trackLink('r-language', 'r-basics', 'R basics', 'Vectors, matrices, lists, data frames, indexing, and iteration.'),
    trackLink('r-language', 'r-data-manipulation', 'Data manipulation', 'purrr, dplyr, tidyr, factors, and apply-style workflows.'),
    trackLink('r-language', 'r-functions', 'Functions in R', 'Parameters, return values, scoping, and validation.'),
    trackLink('r-language', 'r-statistical-modeling', 'Statistical modeling', 'Regression, fitting, simulation, bootstrap, and inference.'),
    trackLink('r-language', 'r-visualization', 'Visualization', 'Plotting tools and communication of model results.'),
    trackLink('r-language', 'r-advanced', 'Advanced R', 'Package development, testing, debugging, and advanced topics.'),
    nodeLink('basic-data-structures', 'Basic Data Structures', 'Choose the right R container before transforming data.'),
    nodeLink('dplyr-pipes-tidyr', 'dplyr, pipes, and tidyr', 'A composable data-wrangling workflow.'),
    nodeLink('fitting-models-to-data', 'Fitting Models to Data', 'Model fit, assumptions, and interpretation.'),
  ],
  defaultOpen: false,
}
