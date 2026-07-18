import { chromium } from 'playwright'

const baseUrl = process.env.FRONTEND_BASE_URL ?? 'http://127.0.0.1:5173'

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

try {
  await page.goto(`${baseUrl}/`, { waitUntil: 'networkidle' })
  const url = new URL(page.url())
  if (url.pathname !== '/') {
    throw new Error(`Home should remain at /, got ${url.pathname}${url.search}`)
  }

  await page.getByLabel('Home dashboard').waitFor()
  await page.getByRole('heading', { name: 'Continue learning' }).waitFor()
  await page.getByLabel('Knowledge graph', { exact: true }).getByRole('heading', { name: 'Knowledge graph' }).waitFor()
  await page.getByRole('heading', { name: 'Workspaces' }).waitFor()
  await page.goto(`${baseUrl}/health`, { waitUntil: 'networkidle' })
  await page.getByLabel('Knowledge Workbench home').click()
  if (new URL(page.url()).pathname !== '/') {
    throw new Error('Knowledge Workbench should return to the home dashboard.')
  }
  await page.locator('.home-dashboard-header').getByRole('link', { name: 'Open library' }).click()
  if (new URL(page.url()).pathname !== '/nodes') {
    throw new Error('Open library should navigate to /nodes.')
  }

  console.log('Home dashboard regression check passed')
} finally {
  await browser.close()
}
