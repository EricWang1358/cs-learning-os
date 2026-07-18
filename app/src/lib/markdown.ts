export type TocItem = {
  id: string
  level: 1 | 2 | 3
  text: string
}

export type ParsedHeading = TocItem & {
  lineNumber: number
}

export type MarkdownSection = {
  startLine: number
  endLine: number
  isEditable: boolean
  level: 0 | 1 | 2
  text: string
  title: string
}

export type SectionEditDraft = MarkdownSection & {
  draft: string
}

export type ReadingReturnAnchor = {
  headingId: string
  headingIndex: number
  mode: 'section-end' | 'heading-start'
}

export function plainMarkdownText(text: string) {
  return text.replace(/`([^`]+)`/g, '$1').replace(/\*\*([^*]+?)\*\*/g, '$1')
}

/** Resolve a reader-facing Markdown link to a node slug when it is local. */
export function markdownNodeSlug(href: string) {
  const value = href.trim()
  if (!value || /^(?:https?:|mailto:|tel:|data:|javascript:)/i.test(value)) return null

  const path = value.split(/[?#]/, 1)[0]
  const routeMatch = path.match(/^\/nodes\/([^/]+)$/)
  if (routeMatch) return decodeURIComponent(routeMatch[1])

  const fileMatch = path.match(/(?:^|\/)([a-z0-9][a-z0-9-]*)\.md$/i)
  return fileMatch ? fileMatch[1] : null
}

export function headingId(text: string, index: number) {
  const base = plainMarkdownText(text)
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fff]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return `section-${base || 'heading'}-${index}`
}

export function markdownContentLines(body: string) {
  let isInCodeFence = false
  return body.split('\n').map((line, index) => {
    if (/^\s*```/.test(line)) {
      const result = { isCode: true, line, lineNumber: index + 1 }
      isInCodeFence = !isInCodeFence
      return result
    }
    return { isCode: isInCodeFence, line, lineNumber: index + 1 }
  })
}

export function parseMarkdownHeadings(body: string): ParsedHeading[] {
  let headingIndex = 0
  return markdownContentLines(body).flatMap(({ isCode, line, lineNumber }) => {
    if (isCode) return []
    const heading = line.trim().match(/^(#{1,3})\s+(.+)$/)
    if (!heading) return []
    const text = plainMarkdownText(heading[2].trim())
    const item = {
      id: headingId(text, headingIndex),
      level: heading[1].length as 1 | 2 | 3,
      lineNumber,
      text,
    }
    headingIndex += 1
    return [item]
  })
}

export function splitMarkdownSections(body: string): MarkdownSection[] {
  const lines = body.split('\n')
  const editableHeadings = markdownContentLines(body)
    .filter((item) => !item.isCode)
    .flatMap((item) => {
      const heading = item.line.trim().match(/^(#{1,2})\s+(.+)$/)
      if (!heading) return []
      return [
        {
          ...item,
          level: heading[1].length as 1 | 2,
          title: plainMarkdownText(heading[2].trim()),
        },
      ]
    })

  if (!editableHeadings.length) {
    return [
      {
        startLine: 1,
        endLine: lines.length,
        isEditable: false,
        level: 0,
        text: body,
        title: 'Full note',
      },
    ]
  }

  const sections: MarkdownSection[] = []
  const firstHeading = editableHeadings[0]
  if (firstHeading.lineNumber > 1) {
    sections.push({
      startLine: 1,
      endLine: firstHeading.lineNumber - 1,
      isEditable: false,
      level: 0,
      text: lines.slice(0, firstHeading.lineNumber - 1).join('\n'),
      title: 'Before first heading',
    })
  }

  return [
    ...sections,
    ...editableHeadings.map((heading, index) => {
      const nextHeading = editableHeadings[index + 1]
      const endLine = nextHeading ? nextHeading.lineNumber - 1 : lines.length
      const text = lines.slice(heading.lineNumber - 1, endLine).join('\n')
      return {
        startLine: heading.lineNumber,
        endLine,
        isEditable: true,
        level: heading.level,
        text,
        title: heading.title,
      }
    }),
  ]
}

export function replaceMarkdownLineRange(body: string, startLine: number, endLine: number, replacement: string) {
  const lines = body.split('\n')
  return [
    ...lines.slice(0, startLine - 1),
    ...replacement.split('\n'),
    ...lines.slice(endLine),
  ].join('\n')
}

export function buildTableOfContents(body: string): TocItem[] {
  return parseMarkdownHeadings(body).map(({ id, level, text }) => ({ id, level, text }))
}

export function sectionReadingAnchor(
  body: string,
  section: MarkdownSection,
  mode: ReadingReturnAnchor['mode'],
): ReadingReturnAnchor | null {
  const headings = parseMarkdownHeadings(body)
  const headingIndex = headings.findIndex((heading) => heading.lineNumber === section.startLine)
  if (headingIndex === -1) return null
  return {
    headingId: headings[headingIndex].id,
    headingIndex,
    mode,
  }
}
