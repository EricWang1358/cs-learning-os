export type CatalogLink = {
  title: string
  href: string
  description: string
  kind?: 'node' | 'quiz' | 'route' | 'external'
}

export type CatalogSection = {
  id: string
  number: string
  title: string
  eyebrow: string
  overview: string
  guide: string
  summary: string
  links: CatalogLink[]
  defaultOpen?: boolean
}
