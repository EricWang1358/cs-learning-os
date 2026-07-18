import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const baseUrl = process.env.FRONTEND_BASE_URL ?? 'http://127.0.0.1:5173'
const apiBaseUrl = process.env.API_BASE_URL ?? 'http://127.0.0.1:8000'
const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const outputDirectory = process.env.QA_OUTPUT_DIR
  ? path.resolve(process.env.QA_OUTPUT_DIR)
  : path.resolve(scriptDirectory, '../../generated/qa')

function fail(message) {
  throw new Error(message)
}

function currentUrl(page) {
  return new URL(page.url())
}

async function assertApiReady(page) {
  let response
  try {
    response = await page.request.get(`${apiBaseUrl}/api/nodes`, { timeout: 10000 })
  } catch (error) {
    fail(`Knowledge node API is unavailable at ${apiBaseUrl}: ${error instanceof Error ? error.message : String(error)}`)
  }
  if (!response.ok()) {
    fail(`Knowledge node API returned HTTP ${response.status()} at ${apiBaseUrl}/api/nodes.`)
  }

  let payload
  try {
    payload = await response.json()
  } catch {
    fail('Knowledge node API returned invalid JSON.')
  }
  if (!payload || !Array.isArray(payload.nodes)) {
    fail('Knowledge node API response does not contain a nodes array.')
  }
  const activeNodes = payload.nodes.filter((node) => !['archive', 'trash'].includes(node.visibility))
  if (activeNodes.length === 0) {
    fail('Knowledge node API has no active nodes; Library Workbench data is unavailable.')
  }
  if (!activeNodes.some((node) => typeof node.updated_at === 'string' && node.updated_at.length > 0)) {
    fail('Knowledge node API has no usable updated_at values for date grouping.')
  }
  return {
    count: activeNodes.length,
    nodesBySlug: new Map(activeNodes.map((node) => [node.slug, node])),
  }
}

async function openLibrary(page, viewport) {
  await page.setViewportSize(viewport)
  try {
    await page.goto(`${baseUrl}/nodes`, { waitUntil: 'domcontentloaded', timeout: 15000 })
  } catch (error) {
    fail(`Frontend is unavailable at ${baseUrl}/nodes: ${error instanceof Error ? error.message : String(error)}`)
  }

  try {
    await page.getByLabel('Knowledge nodes').waitFor({ state: 'visible', timeout: 10000 })
    await page.getByLabel('Global search').waitFor({ state: 'visible', timeout: 10000 })
    await page.locator('[data-testid^="library-date-group-"]').first().waitFor({ state: 'visible', timeout: 15000 })
  } catch (error) {
    const bodyText = await page.locator('body').innerText().catch(() => '')
    fail(`Library Workbench did not load usable node data: ${error instanceof Error ? error.message : String(error)}${bodyText ? `\nPage text: ${bodyText.slice(0, 300)}` : ''}`)
  }
}

async function assertNoHorizontalOverflow(page, label) {
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth,
  }))
  const widest = Math.max(metrics.documentWidth, metrics.bodyWidth)
  if (widest > metrics.viewport + 1) {
    fail(`${label} has horizontal overflow (${widest}px content in ${metrics.viewport}px viewport).`)
  }
}

async function assertGroups(page, { checkDefaultState = true } = {}) {
  const groups = page.locator('[data-testid^="library-date-group-"]')
  const count = await groups.count()
  if (count === 0) fail('Library rendered no non-empty date groups.')

  const states = await groups.evaluateAll((elements) => elements.map((element) => {
    const bucket = element.getAttribute('data-testid')?.replace('library-date-group-', '') ?? ''
    const toggle = element.querySelector('.library-date-group-toggle')
    const content = element.querySelector('.library-date-group-content')
    return {
      bucket,
      expanded: toggle?.getAttribute('aria-expanded') === 'true',
      rowCount: element.querySelectorAll('[data-testid="library-node-row"]').length,
      hidden: content?.hasAttribute('hidden') ?? false,
    }
  }))
  const validBuckets = new Set(['today', 'two-days', 'week', 'older'])
  for (const state of states) {
    if (!validBuckets.has(state.bucket)) fail(`Unexpected Library date bucket: ${state.bucket}`)
    if (state.rowCount < 1) fail(`Empty Library date group was rendered: ${state.bucket}`)
    if (state.expanded === state.hidden) fail(`Date group visibility disagrees with aria-expanded: ${state.bucket}`)
    if (checkDefaultState) {
      const shouldOpen = state.bucket === 'today' || state.bucket === 'two-days'
      if (state.expanded !== shouldOpen) {
        fail(`Unexpected default state for ${state.bucket}: expected ${shouldOpen ? 'expanded' : 'collapsed'}.`)
      }
    }
  }
  return states
}

async function assertToolbarAndGroupControls(page) {
  const toolbar = page.locator('header.search-header.library-workbench-toolbar')
  if (await toolbar.count() !== 1) fail('Library Workbench search toolbar is missing.')
  const position = await toolbar.evaluate((element) => getComputedStyle(element).position)
  if (position === 'sticky' || position === 'fixed') fail(`Library Workbench toolbar must not be sticky; computed position is ${position}.`)

  const expand = page.getByRole('button', { name: 'Expand all', exact: true })
  const collapse = page.getByRole('button', { name: 'Collapse all', exact: true })
  await expand.waitFor({ state: 'visible' })
  await collapse.waitFor({ state: 'visible' })

  await collapse.click()
  const collapsedStates = await assertGroups(page, { checkDefaultState: false })
  if (collapsedStates.some((state) => state.expanded)) fail('Collapse all left at least one date group expanded.')

  await expand.click()
  const expandedStates = await assertGroups(page, { checkDefaultState: false })
  if (expandedStates.some((state) => !state.expanded)) fail('Expand all left at least one date group collapsed.')
}

async function assertSequenceEditing(page, nodesBySlug) {
  const row = page.locator('[data-testid="library-node-row"]').first()
  await row.waitFor({ state: 'visible' })
  const rowLabel = await row.getAttribute('aria-label')
  const title = rowLabel?.replace(/^Library node\s+/, '')
  const sourceNode = [...nodesBySlug.values()].find((node) => node.title === title)
  if (!sourceNode) fail(`Could not map visible Library row to API node: ${rowLabel ?? '(missing label)'}`)

  const order = row.locator('[data-testid="library-node-order"]')
  await order.dblclick()
  const cancelledInput = row.locator('input.library-node-order-input')
  await cancelledInput.waitFor({ state: 'visible' })
  await cancelledInput.press('Escape')
  await cancelledInput.waitFor({ state: 'hidden' })

  let patchCalls = 0
  await page.route('**/api/nodes/*/display-order', async (route) => {
    const request = route.request()
    const pathParts = new URL(request.url()).pathname.split('/').filter(Boolean)
    const slug = decodeURIComponent(pathParts[pathParts.length - 2] ?? '')
    const node = nodesBySlug.get(slug)
    if (!node) {
      await route.abort()
      return
    }
    const payload = JSON.parse(request.postData() ?? '{}')
    patchCalls += 1
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ node: { ...node, display_order: payload.display_order } }),
    })
  })

  try {
    await order.dblclick()
    const input = row.locator('input.library-node-order-input')
    await input.waitFor({ state: 'visible' })
    const nextOrder = 900000 + (Number.isInteger(sourceNode.display_order) ? sourceNode.display_order : 1)
    await input.fill(String(nextOrder))
    await input.press('Enter')
    await input.waitFor({ state: 'hidden', timeout: 10000 })
    if (patchCalls !== 1) fail(`Expected one intercepted sequence PATCH after Enter, got ${patchCalls}.`)
    const displayedOrder = await order.textContent()
    if (displayedOrder?.trim() !== String(nextOrder)) {
      fail(`Sequence display did not update after Enter: expected ${nextOrder}, got ${displayedOrder?.trim() ?? '(empty)'}.`)
    }
  } finally {
    await page.unroute('**/api/nodes/*/display-order')
  }
}

async function assertSearchAndQueryPreservation(page) {
  const search = page.getByLabel('Global search')
  await search.fill('binary')
  await page.waitForURL((url) => url.searchParams.get('q') === 'binary', { timeout: 10000 })
  if (currentUrl(page).searchParams.get('q') !== 'binary') fail('Search query was not written to the Library URL.')

  // Library hides the legacy sort select, but route-level sort navigation must
  // still preserve q for links/bookmarks and older callers.
  await page.goto(`${baseUrl}/nodes?sort=alphabet&q=binary`, { waitUntil: 'domcontentloaded', timeout: 15000 })
  await page.getByLabel('Global search').waitFor({ state: 'visible', timeout: 10000 })
  const sortedUrl = currentUrl(page)
  if (sortedUrl.searchParams.get('sort') !== 'alphabet' || sortedUrl.searchParams.get('q') !== 'binary') {
    fail(`Sort navigation dropped query parameters: ${sortedUrl.pathname}${sortedUrl.search}`)
  }
}

async function main() {
  await mkdir(outputDirectory, { recursive: true })
  const browser = await chromium.launch()
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
    const { count: activeNodeCount, nodesBySlug } = await assertApiReady(page)

    await openLibrary(page, { width: 1440, height: 900 })
    await assertNoHorizontalOverflow(page, 'Desktop Library Workbench')
    await assertGroups(page)
    await page.screenshot({ path: path.join(outputDirectory, 'library-workbench-desktop.png'), fullPage: true })
    await assertToolbarAndGroupControls(page)
    await assertSequenceEditing(page, nodesBySlug)
    await assertSearchAndQueryPreservation(page)

    await openLibrary(page, { width: 390, height: 844 })
    await assertNoHorizontalOverflow(page, 'Mobile Library Workbench')
    await assertGroups(page)
    await page.screenshot({ path: path.join(outputDirectory, 'library-workbench-mobile.png'), fullPage: true })

    console.log(`Library Workbench regression check passed (${activeNodeCount} active API nodes).`)
    console.log(`Screenshots written to ${outputDirectory}`)
  } finally {
    await browser.close()
  }
}

try {
  await main()
} catch (error) {
  console.error(`Library Workbench regression check failed: ${error instanceof Error ? error.message : String(error)}`)
  process.exitCode = 1
}
