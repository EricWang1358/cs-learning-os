import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const outputDir = new URL('../../generated/qa/', import.meta.url)
await mkdir(outputDir, { recursive: true })
const shot = (name) => fileURLToPath(new URL(name, outputDir))

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
page.on('pageerror', (err) => console.log('[pageerror]', err.stack || String(err)))

await page.goto('http://127.0.0.1:5173/knowledge-graph', { waitUntil: 'networkidle' })
await page.waitForTimeout(3000)

// Reroot to "GDB Basics" via the bottleneck rail (real node with markdown headings)
await page.locator('.kgraph-bottleneck-list button').first().click()
await page.waitForTimeout(4500)
await page.screenshot({ path: shot('kgraph-heading-satellites.png'), fullPage: false })

// Report what the scene received (via the header root label) and heading API payload
const info = await page.evaluate(async () => {
  const header = document.querySelector('.kgraph-header p:last-child')?.textContent
  const resp = await fetch('http://127.0.0.1:8000/api/graph/node/gdb-basics?page=1')
  const nav = await resp.json()
  return {
    header,
    headingChildren: (nav.children || []).filter((c) => c.type === 'heading').map((c) => c.label),
  }
})
console.log(JSON.stringify(info, null, 2))
await browser.close()
