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
await page.waitForTimeout(4500)
await page.screenshot({ path: shot('kgraph-after-fix.png'), fullPage: false })

const before = await page.evaluate(() => {
  const canvas = document.querySelector('canvas')
  const r = canvas?.getBoundingClientRect()
  return { canvas: r ? { x: r.x, y: r.y, w: r.width, h: r.height } : null }
})
console.log('geometry:', JSON.stringify(before))

// Open the legend popup via the toolbar button
const legendButton = page.getByRole('button', { name: '图例' })
if (await legendButton.count()) {
  await legendButton.first().click()
  await page.waitForTimeout(600)
  await page.screenshot({ path: shot('kgraph-legend-open.png'), fullPage: false })
  const popup = await page.evaluate(() => {
    const el = [...document.querySelectorAll('div')].find(
      (d) => (d.textContent || '').startsWith('掌握度') && d.style.position === 'absolute',
    )
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { x: r.x, y: r.y, w: r.width, h: r.height }
  })
  console.log('legend popup:', JSON.stringify(popup))
} else {
  console.log('图例 button NOT FOUND')
}
await browser.close()
