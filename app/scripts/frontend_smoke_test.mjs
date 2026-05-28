import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const baseUrl = process.env.FRONTEND_BASE_URL ?? 'http://127.0.0.1:5173'
const apiBaseUrl = process.env.API_BASE_URL ?? 'http://127.0.0.1:8000'
const outputDir = new URL('../../generated/qa/', import.meta.url)
const screenshotPath = (name) => fileURLToPath(new URL(name, outputDir))

await mkdir(outputDir, { recursive: true })

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

await page.goto(baseUrl, { waitUntil: 'networkidle' })
await page.getByText('Knowledge Workbench').waitFor()
await page.getByText('Binary Search').waitFor()
await page.screenshot({ path: screenshotPath('desktop-home.png'), fullPage: false })

await page.getByLabel('Global search').fill('graph')
await page.getByText('Graph Traversal').waitFor()
await page.screenshot({ path: screenshotPath('desktop-search-graph.png'), fullPage: false })

await page.getByRole('button', { name: /Projects/ }).click()
await page.getByText('Project Pattern: CRUD App').waitFor()
await page.screenshot({ path: screenshotPath('desktop-projects.png'), fullPage: false })

await page.getByRole('button', { name: /CS fundamentals/ }).click()
await page.getByText('Reading tracks').waitFor()
await page.getByRole('button', { name: /x86-64 assembly/ }).click()
await page.getByRole('button', { name: /x86-64 Registers/ }).waitFor()
await page.getByRole('button', { name: /x86-64 Addressing and leaq/ }).waitFor()
await page.screenshot({ path: screenshotPath('desktop-cs-tracks.png'), fullPage: false })

await page.getByRole('button', { name: /Archive/ }).click()
await page.getByText(/visible of .* indexed nodes/).waitFor()

await page.getByRole('button', { name: 'Q Queue' }).click()
await page.getByText(/indexed open questions/).waitFor()
await page.getByText(/Open target and resolve this question/).first().waitFor()

await page.getByRole('button', { name: 'Practice / Quiz Bank' }).click()
await page.getByText(/indexed quizzes/).waitFor()
await page.getByRole('button', { name: /Trace %rax through x86-64 instructions/ }).click()
await page.getByText('Quiz body').waitFor()
await page.locator('.markdown-body').getByText('Function 1:').waitFor()
await page.locator('.code-block').first().waitFor()
await page.getByRole('button', { name: /tests: x86-64 Registers/ }).waitFor()
await page.screenshot({ path: screenshotPath('desktop-quiz-bank.png'), fullPage: false })
await page.getByRole('button', { name: /tests: x86-64 Registers/ }).click()
await page.getByLabel('Node detail').getByRole('heading', { name: /x86-64 Registers/ }).waitFor()
await page.getByRole('button', { name: 'Back' }).click()
await page.getByLabel('Node detail').getByRole('heading', { name: /Trace %rax/ }).waitFor()

await page.getByRole('button', { name: /All nodes/ }).click()
await page.getByRole('button', { name: /Debugging Loop/ }).click()
await page.getByRole('button', { name: 'Focus reading' }).click()
await page.locator('.markdown-body h1', { hasText: 'Debugging Loop' }).waitFor()
await page.locator('.markdown-body h2', { hasText: 'Why It Matters' }).waitFor()
await page.getByRole('button', { name: 'Edit mode' }).click()
await page.getByLabel('Markdown editor').waitFor()
await page.getByLabel('Reader questions').waitFor({ state: 'hidden' })
page.once('dialog', (dialog) => dialog.accept())
await page.getByRole('button', { name: 'Exit edit mode' }).click()
await page.locator('.markdown-body h2', { hasText: 'Why It Matters' }).waitFor()
await page.getByRole('button', { name: 'Edit mode' }).click()
await page.getByLabel('Markdown editor').waitFor()
page.once('dialog', (dialog) => dialog.accept())
await page.getByRole('button', { name: 'Cancel' }).click()
await page.locator('.markdown-body h2', { hasText: 'Why It Matters' }).waitFor()
await page.getByRole('button', { name: 'Edit mode' }).click()
await page.getByLabel('Markdown editor').waitFor()
page.once('dialog', (dialog) => dialog.accept())
await page.getByRole('button', { name: /related: Project Crud App/ }).click()
await page.getByLabel('Markdown editor').waitFor({ state: 'hidden' })
await page.getByLabel('Node detail').getByRole('heading', { name: /Project Pattern: CRUD App/ }).waitFor()
const malformedHeadingCount = await page.locator('.markdown-body h1, .markdown-body h2, .markdown-body h3').evaluateAll((headings) =>
  headings.filter((heading) => heading.textContent?.includes('##') || heading.textContent?.includes('- ')).length,
)
if (malformedHeadingCount > 0) {
  throw new Error('Markdown headings include raw markdown syntax; block parsing regressed.')
}
await page.screenshot({ path: screenshotPath('desktop-debugging-loop-markdown.png'), fullPage: false })
await page.getByRole('button', { name: 'Show map' }).click()

await page.getByLabel('Global search').fill('graph.*(')
await page.getByText(/visible of .* indexed nodes/).waitFor()

await page.getByLabel('Global search').fill('')
await page.getByRole('button', { name: /Graph Traversal/ }).click()
await page.getByRole('button', { name: /related: Binary Search/ }).click()
await page
  .getByLabel('Node detail')
  .getByText('Use a monotonic condition to locate a boundary in logarithmic time.')
  .waitFor()

await page.getByRole('button', { name: 'Show map' }).click()
await page.getByLabel('Global search').fill('stepi')
await page.getByRole('button', { name: /GDB stepi/ }).click()

const focusButton = page.getByRole('button', { name: 'Focus reading' })
const showMapButton = page.getByRole('button', { name: 'Show map' })
if (await focusButton.isVisible()) {
  await focusButton.click()
} else {
  await showMapButton.waitFor()
}
await page.getByRole('button', { name: 'Show map' }).waitFor()
await page.locator('.code-block').first().waitFor()
await page.getByLabel('Reader questions').getByPlaceholder(/This explanation skips/).fill('Why does stepi stop after one machine instruction?')
await page.getByRole('button', { name: 'Save question' }).click()
await page.getByText(/Q to be solved:/).waitFor()
const smokeQuestions = await page.request.get(
  `${apiBaseUrl}/api/reader-questions?target_type=node&target_id=gdb-stepi&status=open`,
)
const smokePayload = await smokeQuestions.json()
const smokeQuestion = smokePayload.questions.find((item) =>
  item.question.includes('Why does stepi stop after one machine instruction?'),
)
if (smokeQuestion) {
  await page.request.post(`${apiBaseUrl}/api/reader-questions/${smokeQuestion.id}/resolve`, {
    data: { resolution_note: 'Frontend smoke test cleanup' },
  })
}
await page.screenshot({ path: screenshotPath('desktop-focus-reading.png'), fullPage: false })
await page.getByRole('button', { name: 'Show map' }).click()
await page.getByRole('button', { name: 'Focus reading' }).waitFor()

const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true })
await mobile.goto(baseUrl, { waitUntil: 'networkidle' })
await mobile.getByText('Knowledge Workbench').waitFor()
await mobile.screenshot({ path: screenshotPath('mobile-home.png'), fullPage: false })

await browser.close()
console.log('Frontend smoke test passed')
