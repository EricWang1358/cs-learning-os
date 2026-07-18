import { chromium } from 'playwright'

const baseUrl = process.env.FRONTEND_BASE_URL ?? 'http://127.0.0.1:5173'
const apiBaseUrl = process.env.API_BASE_URL ?? 'http://127.0.0.1:8000'

const currentUrl = (page) => new URL(page.url())

async function assertApiReady(page) {
  const response = await page.request.get(`${apiBaseUrl}/api/nodes/binary-search`)
  if (!response.ok()) {
    throw new Error(`Expected Binary Search API to be ready, got HTTP ${response.status()}.`)
  }
  const payload = await response.json()
  const relatedSlug = payload.node?.links?.find((link) => link.kind === 'related')?.target
  if (!relatedSlug) {
    throw new Error('Expected Binary Search to have a related node for Back restoration coverage.')
  }
  const relatedResponse = await page.request.get(`${apiBaseUrl}/api/nodes/${relatedSlug}`)
  if (!relatedResponse.ok()) {
    throw new Error(`Expected related node ${relatedSlug} to be readable, got HTTP ${relatedResponse.status()}.`)
  }
  const relatedPayload = await relatedResponse.json()
  return { relatedSlug, relatedTitle: relatedPayload.node.title }
}

async function expectFocusedBinarySearch(page) {
  await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: 'Binary Search' }).waitFor()
  await page.getByLabel('Markdown table of contents').getByRole('link', { name: 'Binary Search', exact: true }).waitFor()
  await page.locator('.toolbar-actions').getByRole('button', { name: 'Show map' }).waitFor()
  const url = currentUrl(page)
  if (!url.pathname.endsWith('/nodes/binary-search') || url.searchParams.get('focus') !== '1') {
    throw new Error(`Expected focused Binary Search URL, got ${url.pathname}${url.search}.`)
  }
}

async function expectMappedBinarySearch(page) {
  await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: 'Binary Search' }).waitFor()
  await page.getByLabel('Global search').waitFor()
  await page.locator('.toolbar-actions').getByRole('button', { name: 'Focus reading' }).waitFor()
  const url = currentUrl(page)
  if (!url.pathname.endsWith('/nodes/binary-search') || url.searchParams.has('focus')) {
    throw new Error(`Expected mapped Binary Search URL, got ${url.pathname}${url.search}.`)
  }
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1280, height: 820 } })

try {
  const { relatedSlug, relatedTitle } = await assertApiReady(page)

  await page.goto(`${baseUrl}/nodes/binary-search`, { waitUntil: 'networkidle' })
  await expectMappedBinarySearch(page)
  await page.getByLabel('Global search').fill('binary')
  await page.waitForURL((url) => url.searchParams.get('q') === 'binary')
  if (currentUrl(page).searchParams.get('q') !== 'binary') {
    throw new Error('Search query should stay encoded in the route before sort navigation.')
  }

  await page.getByLabel('Sort').selectOption('alphabet')
  await page.waitForURL((url) => url.pathname.startsWith('/nodes') && url.searchParams.get('sort') === 'alphabet')
  await page.getByLabel('Node detail').locator('.markdown-body h1').first().waitFor()
  const sortedUrl = currentUrl(page)
  if (sortedUrl.pathname !== '/nodes' || sortedUrl.searchParams.has('focus') || sortedUrl.searchParams.get('q') !== 'binary') {
    throw new Error(`Sort navigation should preserve map-mode query params, got ${sortedUrl.pathname}${sortedUrl.search}.`)
  }
  await page.getByLabel('Knowledge nodes').waitFor()

  await page.goBack()
  await expectMappedBinarySearch(page)

  await page.goto(`${baseUrl}/nodes/binary-search?focus=1`, { waitUntil: 'networkidle' })
  await expectFocusedBinarySearch(page)
  await page.getByLabel('Markdown table of contents').getByText('Why It Matters').click()
  if (!currentUrl(page).hash.startsWith('#section-')) {
    throw new Error('Focused TOC navigation should encode the section hash before leaving the page.')
  }

  await page.locator('.detail-section').getByRole('button', { name: /^related:/ }).first().click()
  await page.getByLabel('Node detail').locator('.markdown-body h1', { hasText: relatedTitle }).waitFor()
  const relatedUrl = currentUrl(page)
  if (!relatedUrl.pathname.endsWith(`/nodes/${relatedSlug}`) || relatedUrl.searchParams.get('focus') !== '1') {
    throw new Error(`Related-node navigation should preserve focus mode, got ${relatedUrl.pathname}${relatedUrl.search}.`)
  }

  await page.goBack()
  await expectFocusedBinarySearch(page)
  if (!currentUrl(page).hash.startsWith('#section-')) {
    throw new Error('Browser Back should restore the focused Binary Search section hash.')
  }

  console.log('Navigation/focus regression check passed')
} finally {
  await browser.close()
}
