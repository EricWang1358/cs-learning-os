import type { CatalogLink } from '../types'

export function trackLink(area: string, track: string, title: string, description: string): CatalogLink {
  return {
    title,
    href: `/nodes?area=${encodeURIComponent(area)}&track=${encodeURIComponent(track)}`,
    description,
    kind: 'route',
  }
}

export function areaLink(area: string, title: string, description: string): CatalogLink {
  return {
    title,
    href: `/nodes?area=${encodeURIComponent(area)}`,
    description,
    kind: 'route',
  }
}

export function nodeLink(slug: string, title: string, description: string): CatalogLink {
  return { title, href: `/nodes/${encodeURIComponent(slug)}`, description, kind: 'node' }
}

export function quizLink(id: string, title: string, description: string): CatalogLink {
  return { title, href: `/quizzes/${encodeURIComponent(id)}`, description, kind: 'quiz' }
}
