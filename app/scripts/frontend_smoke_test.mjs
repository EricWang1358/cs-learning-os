import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const baseUrl = process.env.FRONTEND_BASE_URL ?? 'http://127.0.0.1:5173'
const apiBaseUrl = process.env.API_BASE_URL ?? 'http://127.0.0.1:8000'
const outputDir = new URL('../../generated/qa/', import.meta.url)
const screenshotPath = (name) => fileURLToPath(new URL(name, outputDir))

const currentUrl = (page) => new URL(page.url())

await mkdir(outputDir, { recursive: true })

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

await page.goto(baseUrl, { waitUntil: 'networkidle' })
await page.getByText('Knowledge Workbench').waitFor()
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
await page.locator('.area-nav').getByRole('button', { name: /^CS fundamentals\s+\d+$/ }).click()
await page.getByText('Reading tracks').waitFor()
await page.getByRole('button', { name: /x86-64 Addressing and leaq/ }).click()
await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: 'x86-64 Addressing and leaq' }).waitFor()
if (!currentUrl(page).pathname.endsWith('/nodes/x86-64-addressing-and-leaq')) {
  throw new Error('Node card navigation should update the URL path.')
}
await page.locator('.markdown-body').first().waitFor()
const rawBoldSyntaxCount = await page.locator('.markdown-body').evaluate((body) => (body.textContent?.match(/\*\*/g) ?? []).length)
if (rawBoldSyntaxCount > 0) {
  throw new Error('Markdown bold syntax leaked into rendered text.')
}
await page.locator('.code-block').first().waitFor()

await page.getByRole('button', { name: 'Focus reading' }).click()
if (currentUrl(page).searchParams.get('focus') !== '1') {
  throw new Error('Focus mode should be reflected in the URL.')
}
await page.getByLabel('Markdown table of contents').locator('a').first().click()
if (!currentUrl(page).hash.startsWith('#section-')) {
  throw new Error('TOC navigation should use a section hash.')
}
await page.getByRole('button', { name: 'Show map' }).click()
await page.screenshot({ path: screenshotPath('desktop-markdown-bold.png'), fullPage: false })

await page.getByRole('button', { name: 'Practice / Quiz Bank' }).click()
if (currentUrl(page).pathname !== '/quizzes') {
  throw new Error('Quiz bank navigation should use /quizzes.')
}
await page.getByText(/indexed quizzes/).waitFor()
await page.getByRole('button', { name: /Trace %rax through x86-64 instructions/ }).click()
await page.getByText('Quiz body').waitFor()
if (!currentUrl(page).pathname.endsWith('/quizzes/x86-rax-trace-leaq-jump')) {
  throw new Error('Quiz selection should update the URL path.')
}
await page.locator('.markdown-body').first().waitFor()
await page.locator('.code-block').first().waitFor()
await page.getByRole('button', { name: /tests: x86-64 Addressing and leaq/ }).waitFor()
await page.screenshot({ path: screenshotPath('desktop-quiz-bank.png'), fullPage: false })

await page.getByRole('button', { name: /tests: x86-64 Addressing and leaq/ }).click()
await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: 'x86-64 Addressing and leaq' }).waitFor()
if (!currentUrl(page).pathname.endsWith('/nodes/x86-64-addressing-and-leaq')) {
  throw new Error('Linked review should navigate to a node URL.')
}
await page.getByRole('button', { name: 'Back' }).click()
await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: 'Trace %rax' }).waitFor()
if (!currentUrl(page).pathname.endsWith('/quizzes/x86-rax-trace-leaq-jump')) {
  throw new Error('Back should restore the previous quiz URL.')
}

await page.getByRole('button', { name: 'Knowledge navigator' }).click()
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
await page.locator('.graph-child-card').first().getByRole('button').first().click()
if (!currentUrl(page).pathname.startsWith('/graph/track/algorithms/')) {
  throw new Error('Graph track click should use a layered graph URL.')
}
await page.locator('.graph-child-card').first().getByRole('button').first().click()
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
await page.getByText('Total local footprint').waitFor()
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
await page.getByRole('button', { name: 'Edit mode' }).click()
await page.getByLabel('Markdown editor').waitFor()
await page.getByLabel('Reader questions').waitFor({ state: 'hidden' })
await page.getByLabel('Markdown table of contents').waitFor({ state: 'hidden' })
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
const crudSmokeQuestion = `Frontend fake Codex CRUD smoke question ${Date.now()}.`
await page.getByLabel('Reader questions').getByPlaceholder(/This explanation skips/).fill(crudSmokeQuestion)
await page.getByRole('button', { name: 'Save question' }).click()
await page.getByText(/Q to be solved:/).waitFor()
await page.goto(`${baseUrl}/queue`, { waitUntil: 'networkidle' })
const crudQuestionCard = page.locator('.question-card', { hasText: crudSmokeQuestion }).first()
await crudQuestionCard.waitFor()
await crudQuestionCard.locator('button.text-link', { hasText: /^Draft$/ }).click()
await crudQuestionCard.locator('button.text-link', { hasText: /^Review draft$/ }).waitFor({ timeout: 30000 })
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

await page.getByRole('button', { name: 'Show map' }).click()
await page.getByRole('button', { name: 'Focus reading' }).waitFor()
await page.getByLabel('Global search').fill('graph.*(')
await page.getByText(/visible of .* indexed nodes/).waitFor()

const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true })
await mobile.goto(baseUrl, { waitUntil: 'networkidle' })
await mobile.getByText('Knowledge Workbench').waitFor()
await mobile.screenshot({ path: screenshotPath('mobile-home.png'), fullPage: false })

await browser.close()
console.log('Frontend smoke test passed')
