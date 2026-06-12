import type { ReactNode } from 'react'
import bash from 'highlight.js/lib/languages/bash'
import c from 'highlight.js/lib/languages/c'
import cpp from 'highlight.js/lib/languages/cpp'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import markdown from 'highlight.js/lib/languages/markdown'
import powershell from 'highlight.js/lib/languages/powershell'
import python from 'highlight.js/lib/languages/python'
import typescript from 'highlight.js/lib/languages/typescript'
import x86asm from 'highlight.js/lib/languages/x86asm'
import hljs from 'highlight.js/lib/core'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { API_BASE } from '../lib/apiClient'
import {
  headingId,
  parseMarkdownHeadings,
  plainMarkdownText,
  splitMarkdownSections,
  type MarkdownSection,
  type SectionEditDraft,
} from '../lib/markdown'

const codeHighlightLanguages = {
  asm: x86asm,
  bash,
  c,
  cpp,
  javascript,
  json,
  markdown,
  powershell,
  python,
  sh: bash,
  shell: bash,
  ts: typescript,
  typescript,
  x86asm,
}

Object.entries(codeHighlightLanguages).forEach(([name, language]) => {
  if (!hljs.getLanguage(name)) {
    hljs.registerLanguage(name, language)
  }
})

function reactNodeText(value: ReactNode): string {
  if (value === null || value === undefined || typeof value === 'boolean') return ''
  if (typeof value === 'string' || typeof value === 'number') return String(value)
  if (Array.isArray(value)) return value.map(reactNodeText).join('')
  if (typeof value === 'object' && 'props' in value) {
    const props = value.props as { children?: ReactNode }
    return reactNodeText(props.children)
  }
  return ''
}

function markdownNodeLine(node: unknown) {
  const positionedNode = node as { position?: { start?: { line?: number } } }
  return positionedNode.position?.start?.line
}

export function MarkdownView({
  body,
  editingSection,
  isSectionSaving,
  onCancelSectionEdit,
  onChangeSectionDraft,
  onSaveSectionEdit,
  onStartSectionEdit,
  sectionEditError,
}: {
  body: string
  editingSection?: SectionEditDraft | null
  isSectionSaving?: boolean
  onCancelSectionEdit?: () => void
  onChangeSectionDraft?: (draft: string) => void
  onSaveSectionEdit?: () => void
  onStartSectionEdit?: (section: MarkdownSection) => void
  sectionEditError?: string
}) {
  const sections = splitMarkdownSections(body)
  const shouldRenderBySection = Boolean(editingSection)
  const headingIdsByLine = new Map(parseMarkdownHeadings(body).map((heading) => [heading.lineNumber, heading.id]))
  const sectionsByStartLine = new Map(sections.map((section) => [section.startLine, section]))

  const renderMarkdown = (markdownBody: string, lineOffset = 1) => {
    return (
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          code({ children, className }) {
            const language = /language-([a-zA-Z0-9_-]+)/.exec(className ?? '')?.[1]
            const code = reactNodeText(children).replace(/\n$/, '')
            if (!language || !hljs.getLanguage(language)) {
              return <code className={className}>{children}</code>
            }
            const highlighted = hljs.highlight(code, { language, ignoreIllegals: true }).value
            return (
              <code
                className={`hljs language-${language}`}
                dangerouslySetInnerHTML={{ __html: highlighted }}
              />
            )
          },
          h1({ children, node }) {
            const text = plainMarkdownText(reactNodeText(children))
            const localLineNumber = markdownNodeLine(node) ?? -1
            const lineNumber = localLineNumber + lineOffset - 1
            const id = headingIdsByLine.get(lineNumber) ?? headingId(text, 0)
            const section = sectionsByStartLine.get(lineNumber)
            return (
              <h1 className={section?.isEditable ? 'editable-heading' : undefined} id={id}>
                <span>{children}</span>
                {section?.isEditable && onStartSectionEdit && (
                  <button
                    type="button"
                    className="section-edit-button"
                    aria-label={`Edit section: ${section.title}`}
                    title="Edit this section"
                    onClick={() => onStartSectionEdit(section)}
                  >
                    Edit
                  </button>
                )}
              </h1>
            )
          },
          h2({ children, node }) {
            const text = plainMarkdownText(reactNodeText(children))
            const localLineNumber = markdownNodeLine(node) ?? -1
            const lineNumber = localLineNumber + lineOffset - 1
            const id = headingIdsByLine.get(lineNumber) ?? headingId(text, 0)
            const section = sectionsByStartLine.get(lineNumber)
            return (
              <h2 className={section?.isEditable ? 'editable-heading' : undefined} id={id}>
                <span>{children}</span>
                {section?.isEditable && onStartSectionEdit && (
                  <button
                    type="button"
                    className="section-edit-button"
                    aria-label={`Edit section: ${section.title}`}
                    title="Edit this section"
                    onClick={() => onStartSectionEdit(section)}
                  >
                    Edit
                  </button>
                )}
              </h2>
            )
          },
          h3({ children, node }) {
            const text = plainMarkdownText(reactNodeText(children))
            const localLineNumber = markdownNodeLine(node) ?? -1
            const lineNumber = localLineNumber + lineOffset - 1
            const id = headingIdsByLine.get(lineNumber) ?? headingId(text, 0)
            return <h3 id={id}>{children}</h3>
          },
          img({ alt, src, title }) {
            const rawSrc = typeof src === 'string' ? src : ''
            const imageSrc = rawSrc.startsWith('/content-assets/') ? `${API_BASE}${rawSrc}` : rawSrc
            return (
              <img
                alt={alt ?? ''}
                decoding="async"
                loading="lazy"
                src={imageSrc}
                title={typeof title === 'string' ? title : undefined}
              />
            )
          },
        }}
      >
        {markdownBody}
      </ReactMarkdown>
    )
  }

  const renderSectionEditor = (section: SectionEditDraft) => (
    <div className="markdown-section-editor" id={headingIdsByLine.get(section.startLine)} key={`edit-${section.startLine}`}>
      <div className="section-editor-header">
        <p className="eyebrow">Editing section</p>
        <strong>{section.title}</strong>
      </div>
      <textarea
        aria-label={`Edit Markdown section: ${section.title}`}
        value={section.draft}
        onChange={(event) => onChangeSectionDraft?.(event.target.value)}
      />
      {sectionEditError && <p className="section-editor-error">{sectionEditError}</p>}
      <div className="section-editor-actions">
        <button
          type="button"
          className="focus-toggle"
          disabled={isSectionSaving}
          onClick={onSaveSectionEdit}
        >
          {isSectionSaving ? 'Saving...' : 'Save section'}
        </button>
        <button
          type="button"
          className="focus-toggle"
          disabled={isSectionSaving}
          onClick={onCancelSectionEdit}
        >
          Cancel
        </button>
      </div>
    </div>
  )

  return (
    <div className="markdown-body">
      {shouldRenderBySection
        ? sections.map((section) =>
            editingSection?.startLine === section.startLine
              ? renderSectionEditor(editingSection)
              : <div className="markdown-section" key={section.startLine}>{renderMarkdown(section.text, section.startLine)}</div>,
          )
        : renderMarkdown(body)}
    </div>
  )
}
