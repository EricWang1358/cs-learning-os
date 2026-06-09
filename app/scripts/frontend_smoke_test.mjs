import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const baseUrl = process.env.FRONTEND_BASE_URL ?? 'http://127.0.0.1:5173'
const apiBaseUrl = process.env.API_BASE_URL ?? 'http://127.0.0.1:8000'
const readMarkDelayMs = Number(process.env.SMOKE_READ_MARK_DELAY_MS ?? '21000')
const readMarkMinIntervalMs = 300000
const outputDir = new URL('../../generated/qa/', import.meta.url)
const screenshotPath = (name) => fileURLToPath(new URL(name, outputDir))

const currentUrl = (page) => new URL(page.url())

async function waitForFakeCodexDraft(questionCard) {
  const reviewDraftButton = questionCard.locator('button.text-link', { hasText: /^Review draft$/ })
  const failedStatus = questionCard.getByText(/AI job needs attention|Codex CLI|OpenAI API|fake Codex failure|timed out|not configured/i)

  try {
    await Promise.race([
      reviewDraftButton.waitFor({ timeout: 30000 }),
      failedStatus.waitFor({ timeout: 30000 }).then(async () => {
        const failureText = (await questionCard.textContent())?.replace(/\s+/g, ' ').trim()
        throw new Error(
          [
            'Frontend smoke expected deterministic fake Codex mode, but the AI draft job failed.',
            'Start or restart the backend with CS_LEARNING_AI_PROVIDER=codex-cli and CS_LEARNING_CODEX_FAKE=success before running npm run test:smoke.',
            failureText ? `Job card: ${failureText}` : '',
          ].filter(Boolean).join(' '),
        )
      }),
    ])
  } catch (error) {
    if (error instanceof Error && error.message.includes('Frontend smoke expected deterministic fake Codex mode')) {
      throw error
    }
    throw new Error(
      [
        'Timed out waiting for the fake Codex draft used by frontend smoke.',
        'This usually means the backend was not started with CS_LEARNING_AI_PROVIDER=codex-cli and CS_LEARNING_CODEX_FAKE=success, or the AI worker is stuck on a real provider call.',
      ].join(' '),
    )
  }
}

await mkdir(outputDir, { recursive: true })

const quizzesResponse = await fetch(`${apiBaseUrl}/api/quizzes`)
if (!quizzesResponse.ok) {
  throw new Error(`Unable to load quizzes for smoke setup: ${quizzesResponse.status}`)
}
const quizzesPayload = await quizzesResponse.json()
const smokeQuiz = quizzesPayload.quizzes?.[0]
if (!smokeQuiz) {
  throw new Error('Frontend smoke requires at least one indexed quiz.')
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

await page.goto(baseUrl, { waitUntil: 'networkidle' })
await page.getByText('Knowledge Workbench').waitFor()
await page.getByLabel('World clock').getByText('Beijing').waitFor()
await page.getByLabel('World clock').getByText('US East').waitFor()
await page.getByRole('button', { name: /Binary Search/ }).waitFor()
if (!currentUrl(page).pathname.startsWith('/nodes/')) {
  throw new Error('Home should redirect to a node URL.')
}
await page.screenshot({ path: screenshotPath('desktop-home.png'), fullPage: false })

await page.getByLabel('Global search').fill('binary')
await page.getByRole('button', { name: /Binary Search/ }).waitFor()
if (currentUrl(page).searchParams.get('q') !== 'binary') {
  throw new Error('Search query should be reflected in the URL.')
}
await page.screenshot({ path: screenshotPath('desktop-search-binary.png'), fullPage: false })

await page.getByLabel('Global search').fill('')
await page.getByRole('button', { name: '+ New node' }).click()
const newNodeTitle = `Frontend Smoke Node ${Date.now()}`
await page.getByLabel('New node title').fill(newNodeTitle)
await page.getByLabel('New node area').fill('Network Programming')
await page.getByLabel('New node track').fill('Network Programming')
await page.getByLabel('New node summary').fill('Temporary frontend-created node.')
await page.getByLabel('New node tags').fill('smoke, Frontend Basic')
await page.getByRole('button', { name: 'Create and edit' }).click()
await page.getByLabel('Markdown editor').waitFor()
const createdSlug = currentUrl(page).pathname.split('/').pop()
if (!createdSlug || !currentUrl(page).pathname.startsWith('/nodes/')) {
  throw new Error('Creating a node should navigate to the new node URL.')
}
await page.getByRole('button', { name: 'Exit edit mode' }).click()
await page.locator('.toolbar-actions').getByRole('button', { name: 'Show map' }).click()
await page.locator('.area-nav').getByRole('button', { name: /^Network Programming\s+\d+$/ }).waitFor()
const createdDetail = await page.request.get(`${apiBaseUrl}/api/nodes/${createdSlug}`)
const createdNode = (await createdDetail.json()).node
if (createdNode.area !== 'network-programming' || createdNode.track !== 'network-programming' || !createdNode.tags.includes('frontend-basic')) {
  throw new Error('New node form should slugify natural-language area, track, and tag input before saving.')
}
const areaMetadata = await page.request.get(`${apiBaseUrl}/api/areas`)
const areaPayload = await areaMetadata.json()
if (!areaPayload.areas.some((area) => area.area === 'network-programming')) {
  throw new Error('New content areas should be exposed through /api/areas metadata.')
}
page.once('dialog', (dialog) => dialog.accept())
await page.getByRole('button', { name: 'Move to trash' }).click()
await page.getByRole('button', { name: 'Undo move to trash' }).waitFor()
await page.getByRole('button', { name: 'Undo move to trash' }).click()
await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: newNodeTitle }).waitFor()
await page.request.post(`${apiBaseUrl}/api/nodes/${createdSlug}/trash`, { data: {} })
await page.request.delete(`${apiBaseUrl}/api/nodes/${createdSlug}`)
await page.goto(`${baseUrl}/nodes/binary-search`, { waitUntil: 'networkidle' })

await page.locator('.area-nav').getByRole('button', { name: /^CS fundamentals\s+\d+$/ }).click()
await page.getByText('Reading tracks').waitFor()
await page.getByRole('button', { name: /x86-64 Addressing and leaq/ }).click()
await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: 'x86-64 Addressing and leaq' }).waitFor()
if (!currentUrl(page).pathname.endsWith('/nodes/x86-64-addressing-and-leaq')) {
  throw new Error('Node card navigation should update the URL path.')
}
await page.locator('.markdown-body').first().waitFor()
await page.getByLabel('Node reading trace').getByText('Last read').waitFor()
await page.getByLabel('Node reading trace').getByText('Last edit').waitFor()
let x86TraceDetail = await page.request.get(`${apiBaseUrl}/api/nodes/x86-64-addressing-and-leaq`)
const x86TraceBeforeFocus = (await x86TraceDetail.json()).node
const rawBoldSyntaxCount = await page.locator('.markdown-body').evaluate((body) => (body.textContent?.match(/\*\*/g) ?? []).length)
if (rawBoldSyntaxCount > 0) {
  throw new Error('Markdown bold syntax leaked into rendered text.')
}
await page.locator('.markdown-body pre code').first().waitFor()

await page.locator('.toolbar-actions').getByRole('button', { name: 'Focus reading' }).click()
if (currentUrl(page).searchParams.get('focus') !== '1') {
  throw new Error('Focus mode should be reflected in the URL.')
}
await page.waitForTimeout(readMarkDelayMs)
x86TraceDetail = await page.request.get(`${apiBaseUrl}/api/nodes/x86-64-addressing-and-leaq`)
const x86TraceAfterFocus = (await x86TraceDetail.json()).node
const previousReadAtMs = Date.parse(x86TraceBeforeFocus.last_read_at || '')
const previousReadWasRecent = Number.isFinite(previousReadAtMs) && Date.now() - previousReadAtMs < readMarkMinIntervalMs
if (
  x86TraceAfterFocus.read_count <= x86TraceBeforeFocus.read_count &&
  x86TraceAfterFocus.last_read_at === x86TraceBeforeFocus.last_read_at &&
  !previousReadWasRecent
) {
  throw new Error('Focus reading dwell should persist last_read_at or read_count in the backend.')
}
await page.getByLabel('Markdown table of contents').locator('a').first().click()
if (!currentUrl(page).hash.startsWith('#section-')) {
  throw new Error('TOC navigation should use a section hash.')
}
await page.locator('.toolbar-actions').getByRole('button', { name: 'Show map' }).click()
await page.screenshot({ path: screenshotPath('desktop-markdown-bold.png'), fullPage: false })

await page.getByRole('button', { name: 'Practice / Quiz Bank' }).click()
if (currentUrl(page).pathname !== '/quizzes') {
  throw new Error('Quiz bank navigation should use /quizzes.')
}
await page.getByText(/indexed quizzes/).waitFor()
await page.getByLabel('Knowledge nodes').locator('.node-card').filter({ hasText: smokeQuiz.title }).first().click()
await page.getByText('Quiz body').waitFor()
if (!currentUrl(page).pathname.endsWith(`/quizzes/${smokeQuiz.id}`)) {
  throw new Error('Quiz selection should update the URL path.')
}
await page.locator('.markdown-body').first().waitFor()
await page.locator('.markdown-body pre code').first().waitFor()
await page.screenshot({ path: screenshotPath('desktop-quiz-bank.png'), fullPage: false })

const linkedReviewButton = page.getByRole('button', { name: /^tests:/ }).first()
if (await linkedReviewButton.count()) {
  await linkedReviewButton.click()
  await page.getByLabel('Node detail').locator('.markdown-body h1').waitFor()
  if (!currentUrl(page).pathname.startsWith('/nodes/')) {
    throw new Error('Linked review should navigate to a node URL.')
  }
} else {
  await page.getByRole('button', { name: 'Knowledge navigator' }).click()
  await page.getByLabel('Knowledge graph navigator').waitFor()
  if (currentUrl(page).pathname !== '/graph') {
    throw new Error('Fallback navigation should use the /graph route.')
  }
}
await page.getByRole('button', { name: 'Back' }).click()
await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: smokeQuiz.title }).waitFor()
if (!currentUrl(page).pathname.endsWith(`/quizzes/${smokeQuiz.id}`)) {
  throw new Error('Back should restore the previous quiz URL.')
}

if (currentUrl(page).pathname !== '/graph') {
  await page.getByRole('button', { name: 'Knowledge navigator' }).click()
}
await page.getByLabel('Knowledge graph navigator').waitFor()
await page.getByText('Workbench').first().waitFor()
if (currentUrl(page).pathname !== '/graph') {
  throw new Error('Knowledge navigator should use the /graph route.')
}
await page.locator('.graph-child-card').first().waitFor()
await page.locator('.graph-child-card').filter({ hasText: /Algorithms/ }).first().getByRole('button').first().click()
if (currentUrl(page).pathname !== '/graph/area/algorithms') {
  throw new Error('Graph area click should use a layered graph URL.')
}
await page.getByLabel('Knowledge graph navigator').getByText('Algorithms').first().waitFor()
await page.locator('.graph-child-card').filter({ hasText: /General/i }).first().getByRole('button').first().click()
if (!currentUrl(page).pathname.startsWith('/graph/track/algorithms/')) {
  throw new Error('Graph track click should use a layered graph URL.')
}
await page.locator('.graph-child-card').filter({ hasText: /Binary Search/ }).first().getByRole('button').first().click()
if (!currentUrl(page).pathname.startsWith('/graph/node/')) {
  throw new Error('Graph node click should open the headings layer.')
}
await page.locator('.graph-action-row').getByRole('button', { name: 'Focus reading' }).waitFor()
await page.screenshot({ path: screenshotPath('desktop-knowledge-navigator.png'), fullPage: false })

await page.getByRole('button', { name: 'System health' }).click()
await page.getByRole('heading', { name: 'System Health' }).waitFor()
if (currentUrl(page).pathname !== '/health') {
  throw new Error('System health should use the /health route.')
}
await page.locator('.health-card').filter({ hasText: 'Project related files' }).first().waitFor()
await page.locator('.health-card').filter({ hasText: 'Git upload estimate' }).first().waitFor()
await page.screenshot({ path: screenshotPath('desktop-system-health.png'), fullPage: false })

await page.goto(`${baseUrl}/nodes/binary-search?focus=1`, { waitUntil: 'networkidle' })
await page.locator('.markdown-body h1', { hasText: 'Binary Search' }).waitFor()
await page.getByLabel('Markdown table of contents').getByRole('link', { name: 'Binary Search', exact: true }).waitFor()
await page.getByLabel('Markdown table of contents').getByText('Why It Matters').click()
if (!currentUrl(page).hash.startsWith('#section-')) {
  throw new Error('TOC section should be represented as a URL hash.')
}
await page.locator('.detail-panel').evaluate((panel) => {
  panel.scrollTop = panel.scrollHeight
})
const firstRelatedLink = page.locator('.detail-section').getByRole('button', { name: /^related:/ }).first()
await firstRelatedLink.waitFor()
await firstRelatedLink.click()
const relatedPath = currentUrl(page).pathname
await page.locator('.markdown-body h1').first().waitFor()
if (!relatedPath.startsWith('/nodes/') || relatedPath.endsWith('/nodes/binary-search')) {
  throw new Error('Related link should move to the linked node URL.')
}
const linkedScrollTop = await page.locator('.detail-panel').evaluate((panel) => panel.scrollTop)
if (linkedScrollTop > 8) {
  throw new Error('Linked node navigation should reset the detail scroll position.')
}
await page.goBack()
await page.locator('.markdown-body h1', { hasText: 'Binary Search' }).waitFor()
if (!currentUrl(page).pathname.endsWith('/nodes/binary-search') || !currentUrl(page).hash.startsWith('#section-')) {
  throw new Error('Browser back should restore the previous node URL and section hash.')
}

await page.goto(`${baseUrl}/nodes/binary-search?focus=1`, { waitUntil: 'networkidle' })
await page.locator('.markdown-body h1', { hasText: 'Binary Search' }).waitFor()
const binaryDetail = await page.request.get(`${apiBaseUrl}/api/nodes/binary-search`)
const binaryOriginalBody = (await binaryDetail.json()).node.body
await page.request.put(`${apiBaseUrl}/api/nodes/binary-search/body`, {
  data: {
    body: `${binaryOriginalBody}\n\n## Code Fence Heading Smoke\n\n\`\`\`python\n# This is a Python comment, not a Markdown heading\nprint("ok")\n\`\`\``,
  },
})
await page.goto(`${baseUrl}/nodes/binary-search?focus=1`, { waitUntil: 'networkidle' })
await page.locator('.markdown-body h2', { hasText: 'Code Fence Heading Smoke' }).waitFor()
await page.getByLabel('Markdown table of contents').getByRole('link', { name: 'Code Fence Heading Smoke' }).waitFor()
if (await page.getByLabel('Markdown table of contents').getByRole('link', { name: 'This is a Python comment, not a Markdown heading' }).count()) {
  throw new Error('Code comments inside fenced blocks should not become TOC headings.')
}
if (await page.getByRole('button', { name: 'Edit section: This is a Python comment, not a Markdown heading' }).count()) {
  throw new Error('Code comments inside fenced blocks should not become editable sections.')
}
await page.request.put(`${apiBaseUrl}/api/nodes/binary-search/body`, {
  data: { body: binaryOriginalBody },
})
await page.goto(`${baseUrl}/nodes/binary-search?focus=1`, { waitUntil: 'networkidle' })
await page.locator('.markdown-body h1', { hasText: 'Binary Search' }).waitFor()
await page.getByRole('button', { name: 'Edit section: Binary Search' }).click()
await page.getByLabel('Edit Markdown section: Binary Search').waitFor()
await page.getByLabel('Edit Markdown section: Binary Search').fill(
  binaryOriginalBody.replace('# Binary Search', 'Binary Search'),
)
await page.getByRole('button', { name: 'Save section' }).click()
await page.locator('.markdown-section-editor').getByText(/Keep the opening heading line/).waitFor()
await page.getByLabel('Edit Markdown section: Binary Search').fill(`${binaryOriginalBody}\n\nUnsaved section edit.`)
page.once('dialog', (dialog) => dialog.dismiss())
await page.getByRole('button', { name: /related:/ }).first().click()
await page.locator('.markdown-section-editor').getByText('Navigation stayed here because section edits are unsaved.').waitFor()
await page.getByLabel('Edit Markdown section: Binary Search').waitFor()
await page.getByLabel('Edit Markdown section: Binary Search').fill(
  `${binaryOriginalBody}\n\n## Section Edit Smoke\nThis temporary line verifies section editing.`,
)
page.once('dialog', (dialog) => dialog.accept())
await page.getByRole('button', { name: 'Save section' }).click()
await page.locator('.markdown-body').getByText('Section Edit Smoke').waitFor()
await page.request.put(`${apiBaseUrl}/api/nodes/binary-search/body`, {
  data: { body: binaryOriginalBody },
})
await page.goto(`${baseUrl}/nodes/binary-search?focus=1`, { waitUntil: 'networkidle' })
await page.locator('.markdown-body h1', { hasText: 'Binary Search' }).waitFor()
await page.getByRole('button', { name: 'Edit mode' }).click()
await page.getByLabel('Markdown editor').waitFor()
await page.getByLabel('Reader questions').waitFor({ state: 'hidden' })
await page.getByLabel('Markdown table of contents').waitFor({ state: 'hidden' })
await page.getByLabel('Markdown editor').fill('   ')
await page.getByRole('button', { name: 'Save Markdown' }).click()
await page.getByText('Markdown cannot be empty.').waitFor()
await page.getByLabel('Markdown editor').fill(binaryOriginalBody)
page.once('dialog', (dialog) => dialog.accept())
await page.getByRole('button', { name: 'Exit edit mode' }).click()
await page.locator('.markdown-body h1', { hasText: 'Binary Search' }).waitFor()

await page.getByLabel('Reader questions').getByPlaceholder(/This explanation skips/).fill('Smoke test temporary reader question.')
await page.getByRole('button', { name: 'Save question' }).click()
await page.getByText(/Q to be solved:/).waitFor()
await page.goto(`${baseUrl}/queue`, { waitUntil: 'networkidle' })
await page.getByLabel('Question queue').waitFor()
await page.getByText('Smoke test temporary reader question.').first().waitFor()
await page.screenshot({ path: screenshotPath('desktop-q-queue.png'), fullPage: false })
const smokeQuestions = await page.request.get(
  `${apiBaseUrl}/api/reader-questions?target_type=node&target_id=binary-search&status=open`,
)
const smokePayload = await smokeQuestions.json()
const smokeQuestionCleanup = smokePayload.questions.filter((item) =>
  item.question.includes('Smoke test temporary reader question.'),
)
for (const smokeQuestion of smokeQuestionCleanup) {
  await page.request.post(`${apiBaseUrl}/api/reader-questions/${smokeQuestion.id}/resolve`, {
    data: { resolution_note: 'Frontend smoke test cleanup' },
  })
}
await page.goto(`${baseUrl}/nodes/binary-search?focus=1`, { waitUntil: 'networkidle' })
await page.locator('.markdown-body h1', { hasText: 'Binary Search' }).waitFor()
await page.screenshot({ path: screenshotPath('desktop-focus-reading.png'), fullPage: false })

await page.goto(`${baseUrl}/nodes/project-crud-app?focus=1`, { waitUntil: 'networkidle' })
await page.locator('.markdown-body h1', { hasText: 'Project Pattern: CRUD' }).waitFor()
const crudDetail = await page.request.get(`${apiBaseUrl}/api/nodes/project-crud-app`)
const crudOriginalBody = (await crudDetail.json()).node.body
await page.request.put(`${apiBaseUrl}/api/nodes/project-crud-app/body`, {
  data: {
    body: `${crudOriginalBody}\n\n## Markdown Table Smoke\n| Command | Meaning |\n| --- | --- |\n| \`stepi\` | Step one CPU instruction. |`,
  },
})
await page.goto(`${baseUrl}/nodes/project-crud-app?focus=1`, { waitUntil: 'networkidle' })
await page.locator('.markdown-body table').waitFor()
await page.locator('.markdown-body table').getByText('stepi').waitFor()
await page.request.put(`${apiBaseUrl}/api/nodes/project-crud-app/body`, {
  data: { body: crudOriginalBody },
})
const crudSmokeQuestion = `Frontend fake Codex CRUD smoke question ${Date.now()}.`
await page.getByLabel('Reader questions').getByPlaceholder(/This explanation skips/).fill(crudSmokeQuestion)
await page.getByRole('button', { name: 'Save question' }).click()
await page.getByText(/Q to be solved:/).waitFor()
await page.goto(`${baseUrl}/queue`, { waitUntil: 'networkidle' })
const crudQuestionCard = page.locator('.question-card', { hasText: crudSmokeQuestion }).first()
await crudQuestionCard.waitFor()
await crudQuestionCard.locator('button.text-link', { hasText: /^Draft$/ }).click()
await waitForFakeCodexDraft(crudQuestionCard)
await crudQuestionCard.locator('button.text-link', { hasText: /^Review draft$/ }).click()
await page.getByLabel('Markdown editor').waitFor()
await page.getByText('Patch ops: 1').waitFor()
await page.getByLabel('AI draft line diff').waitFor()
await page.request.put(`${apiBaseUrl}/api/nodes/project-crud-app/body`, {
  data: { body: `${crudOriginalBody}\n\n## External Edit\nThis simulates another save before applying the AI draft.` },
})
page.once('dialog', (dialog) => dialog.accept())
await page.getByRole('button', { name: 'Save Markdown' }).click()
await page.getByLabel('AI draft conflict').waitFor()
await page.getByRole('button', { name: 'Return to Q Queue' }).click()
await page.getByLabel('Question queue').waitFor()
await page.request.put(`${apiBaseUrl}/api/nodes/project-crud-app/body`, {
  data: { body: crudOriginalBody },
})
await crudQuestionCard.locator('button.text-link', { hasText: /^Review draft$/ }).click()
await page.getByLabel('Markdown editor').waitFor()
page.once('dialog', (dialog) => dialog.accept())
await page.getByRole('button', { name: 'Save Markdown' }).click()
await page.locator('.markdown-body').getByText('AI Draft Smoke Note').waitFor()
const crudQuestions = await page.request.get(
  `${apiBaseUrl}/api/reader-questions?target_type=node&target_id=project-crud-app&status=resolved`,
)
const crudQuestionPayload = await crudQuestions.json()
if (!crudQuestionPayload.questions.some((item) => item.question === crudSmokeQuestion)) {
  throw new Error('Frontend fake Codex flow should resolve the linked CRUD question after apply.')
}
await page.request.put(`${apiBaseUrl}/api/nodes/project-crud-app/body`, {
  data: { body: crudOriginalBody },
})

await page.locator('.toolbar-actions').getByRole('button', { name: 'Show map' }).click()
await page.locator('.toolbar-actions').getByRole('button', { name: 'Focus reading' }).waitFor()
await page.getByLabel('Global search').fill('graph.*(')
await page.getByText(/visible of .* indexed nodes/).waitFor()

const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true })
await mobile.goto(baseUrl, { waitUntil: 'networkidle' })
await mobile.getByText('Knowledge Workbench').waitFor()
await mobile.screenshot({ path: screenshotPath('mobile-home.png'), fullPage: false })

await browser.close()
console.log('Frontend smoke test passed')
