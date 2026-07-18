import { useMemo, useState } from 'react'
import { catalogSections, type CatalogLink, type CatalogSection } from '../catalog/catalog'

function matchesSection(section: CatalogSection, query: string) {
  if (!query) return section
  const normalized = query.toLocaleLowerCase()
  const sectionMatches = [section.title, section.eyebrow, section.overview, section.guide, section.summary]
    .some((value) => value.toLocaleLowerCase().includes(normalized))
  const links = section.links.filter((link) =>
    [link.title, link.description].some((value) => value.toLocaleLowerCase().includes(normalized)),
  )
  return sectionMatches ? section : { ...section, links }
}

function CatalogLinkRow({ link }: { link: CatalogLink }) {
  const isExternal = link.href.startsWith('http://') || link.href.startsWith('https://')
  return (
    <li className="catalog-link-row">
      <a
        href={link.href}
        className="catalog-link"
        target={isExternal ? '_blank' : undefined}
        rel={isExternal ? 'noreferrer' : undefined}
      >
        <span className="catalog-link-title">{link.title}</span>
        <span className="catalog-link-description">{link.description}</span>
        <span className="catalog-link-arrow" aria-hidden="true">-&gt;</span>
      </a>
    </li>
  )
}

function CatalogSectionBlock({ section }: { section: CatalogSection }) {
  return (
    <details className="catalog-section" open={section.defaultOpen}>
      <summary className="catalog-section-summary">
        <span className="catalog-section-number">{section.number}</span>
        <span className="catalog-section-heading">
          <span className="eyebrow">{section.eyebrow}</span>
          <strong>{section.title}</strong>
          <span>{section.overview}</span>
        </span>
        <span className="catalog-section-toggle" aria-hidden="true" />
      </summary>
      <div className="catalog-section-body">
        <div className="catalog-reading-note">
          <span className="catalog-note-label">Guide</span>
          <p>{section.guide}</p>
        </div>
        <div className="catalog-reading-note catalog-reading-note-summary">
          <span className="catalog-note-label">Takeaway</span>
          <p>{section.summary}</p>
        </div>
        <ol className="catalog-links" aria-label={`${section.title} links`}>
          {section.links.map((link) => <CatalogLinkRow key={link.href} link={link} />)}
        </ol>
      </div>
    </details>
  )
}

export function CatalogPage() {
  const [query, setQuery] = useState('')
  const [isOutlineOpen, setIsOutlineOpen] = useState(true)
  const visibleSections = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase()
    return catalogSections
      .map((section) => matchesSection(section, normalized))
      .filter((section) => !normalized || section.links.length > 0 || section.title.toLocaleLowerCase().includes(normalized))
  }, [query])

  return (
    <section className="catalog-page" aria-label="Learning Catalog">
      <header className="catalog-header">
        <div>
          <p className="eyebrow">CURATED NAVIGATION / AGENT-MAINTAINED</p>
          <h2>Learning Catalog</h2>
          <p className="catalog-lede">A readable study route with chapter summaries and direct links into the live workbench.</p>
        </div>
        <div className="catalog-header-meta" aria-label="Catalog metadata">
          <span>{visibleSections.length} sections</span>
          <span>{catalogSections.reduce((count, section) => count + section.links.length, 0)} links</span>
        </div>
      </header>

      <div className="catalog-toolbar">
        <label className="catalog-search">
          <span>Find in catalog</span>
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search chapters or nodes"
            aria-label="Find in catalog"
          />
        </label>
        <button
          type="button"
          className="catalog-outline-toggle"
          aria-expanded={isOutlineOpen}
          onClick={() => setIsOutlineOpen((current) => !current)}
        >
          {isOutlineOpen ? 'Hide outline' : 'Show outline'}
        </button>
      </div>

      <div className={`catalog-layout ${isOutlineOpen ? '' : 'catalog-layout-outline-hidden'}`}>
        {isOutlineOpen && (
          <nav className="catalog-outline" aria-label="Catalog outline">
            <p className="eyebrow">CONTENTS</p>
            {catalogSections.map((section) => (
              <a key={section.id} href={`#catalog-${section.id}`}>
                <span>{section.number}</span>
                <strong>{section.title}</strong>
              </a>
            ))}
          </nav>
        )}
        <div className="catalog-sections">
          {visibleSections.length > 0 ? visibleSections.map((section) => (
            <div key={section.id} id={`catalog-${section.id}`}>
              <CatalogSectionBlock section={section} />
            </div>
          )) : (
            <p className="catalog-empty">No catalog entry matches “{query}”.</p>
          )}
        </div>
      </div>
    </section>
  )
}
