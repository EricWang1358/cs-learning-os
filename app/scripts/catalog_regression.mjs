import { chromium } from 'playwright'

const baseUrl = process.env.FRONTEND_BASE_URL ?? 'http://127.0.0.1:5173'
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1280, height: 820 } })

try {
  await page.goto(`${baseUrl}/catalog`, { waitUntil: 'domcontentloaded', timeout: 10000 })
  await page.getByRole('heading', { name: 'Learning Catalog' }).waitFor()
  await page.getByRole('link', { name: 'Learning Catalog' }).waitFor()
  await page.getByRole('link', { name: 'Library' }).waitFor()
  await page.getByRole('link', { name: /WA8: Optimization/ }).waitFor()
  await page.getByRole('link', { name: /WA9: Exceptional/ }).waitFor()
  await page.getByText('Reader orientation').first().waitFor()

  const wa8Href = await page.getByRole('link', { name: /WA8: Optimization/ }).getAttribute('href')
  const wa9Href = await page.getByRole('link', { name: /WA9: Exceptional/ }).getAttribute('href')
  if (wa8Href !== '/nodes/wa8-systems-optimization-and-linking' || wa9Href !== '/nodes/wa9-exceptional-control-flow') {
    throw new Error(`Unexpected written-assignment links: ${wa8Href}, ${wa9Href}`)
  }

  const fundamentals = page.locator('#catalog-cs-fundamentals .catalog-section')
  await fundamentals.locator('summary').click()
  if (await fundamentals.locator('.catalog-section-body').isVisible()) {
    throw new Error('A catalog chapter should collapse when its summary is clicked.')
  }

  const outlineToggle = page.getByRole('button', { name: 'Hide outline' })
  await outlineToggle.click()
  await page.getByRole('button', { name: 'Show outline' }).waitFor()
  await page.getByRole('button', { name: 'Show outline' }).click()
  await page.getByRole('button', { name: 'Hide outline' }).waitFor()

  const url = new URL(page.url())
  if (url.pathname !== '/catalog') {
    throw new Error(`Expected /catalog, got ${url.pathname}`)
  }

  console.log('Learning Catalog regression check passed')
} finally {
  await browser.close()
}
