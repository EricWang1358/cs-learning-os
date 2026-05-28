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
await page.getByRole('button', { name: /Binary Search/ }).waitFor()
await page.screenshot({ path: screenshotPath('desktop-home.png'), fullPage: false })

await page.getByLabel('Global search').fill('binary')
await page.getByRole('button', { name: /Binary Search/ }).waitFor()
await page.screenshot({ path: screenshotPath('desktop-search-binary.png'), fullPage: false })

await page.getByRole('button', { name: /CS fundamentals/ }).click()
await page.getByText('Reading tracks').waitFor()
await page.getByRole('button', { name: /x86-64 Addressing and leaq/ }).click()
await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: 'x86-64 Addressing and leaq' }).waitFor()
await page.locator('.markdown-body h2 strong', { hasText: '作用' }).waitFor()
const rawBoldSyntaxCount = await page
  .locator('.markdown-body')
  .evaluate((body) => (body.textContent?.match(/\*\*作用\*\*/g) ?? []).length)
if (rawBoldSyntaxCount > 0) {
  throw new Error('Markdown bold syntax leaked into rendered text.')
}
await page.locator('.code-block').first().waitFor()
await page.getByRole('button', { name: 'Focus reading' }).click()
await page.getByLabel('Markdown table of contents').getByText('作用').waitFor()
await page.getByRole('button', { name: 'Show map' }).click()
await page.screenshot({ path: screenshotPath('desktop-markdown-bold.png'), fullPage: false })

await page.getByRole('button', { name: 'Practice / Quiz Bank' }).click()
await page.getByText(/indexed quizzes/).waitFor()
await page.getByRole('button', { name: /Trace %rax through x86-64 instructions/ }).click()
await page.getByText('Quiz body').waitFor()
await page.locator('.markdown-body').getByText('Compute the final value of').waitFor()
await page.locator('.code-block').first().waitFor()
await page.getByRole('button', { name: /tests: x86-64 Addressing and leaq/ }).waitFor()
await page.screenshot({ path: screenshotPath('desktop-quiz-bank.png'), fullPage: false })

await page.getByRole('button', { name: /tests: x86-64 Addressing and leaq/ }).click()
await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: 'x86-64 Addressing and leaq' }).waitFor()
await page.getByRole('button', { name: 'Back' }).click()
await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: 'Trace %rax' }).waitFor()

await page.goto(baseUrl, { waitUntil: 'networkidle' })
await page.getByRole('button', { name: /Binary Search/ }).waitFor()
await page.getByRole('button', { name: /Binary Search/ }).click()
await page.getByRole('button', { name: 'Focus reading' }).click()
await page.locator('.markdown-body h1', { hasText: 'Binary Search' }).waitFor()
await page.getByLabel('Markdown table of contents').getByText('Binary Search').waitFor()
await page.getByLabel('Markdown table of contents').getByText('Why It Matters').click()
await page.locator('.markdown-body h2', { hasText: 'Why It Matters' }).waitFor()
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
const smokeQuestions = await page.request.get(
  `${apiBaseUrl}/api/reader-questions?target_type=node&target_id=binary-search&status=open`,
)
const smokePayload = await smokeQuestions.json()
const smokeQuestion = smokePayload.questions.find((item) => item.question.includes('Smoke test temporary reader question.'))
if (smokeQuestion) {
  await page.request.post(`${apiBaseUrl}/api/reader-questions/${smokeQuestion.id}/resolve`, {
    data: { resolution_note: 'Frontend smoke test cleanup' },
  })
}
await page.screenshot({ path: screenshotPath('desktop-focus-reading.png'), fullPage: false })
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
