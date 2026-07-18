import { chromium } from 'playwright'

const baseUrl = process.env.FRONTEND_BASE_URL ?? 'http://127.0.0.1:5173'

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

try {
  await page.goto(`${baseUrl}/graph`, { waitUntil: 'networkidle' })
  await page.getByLabel('Knowledge graph navigator').waitFor()
  await page.locator('.graph-canvas').waitFor()

  const geometry = await page.locator('.workspace-shell').evaluate((workspace) => {
    const shell = workspace.querySelector('.graph-navigator-shell')
    const canvas = workspace.querySelector('.graph-canvas')
    if (!shell || !canvas) throw new Error('Graph shell or canvas is missing.')
    const shellRect = shell.getBoundingClientRect()
    const canvasRect = canvas.getBoundingClientRect()
    const childRects = Array.from(canvas.querySelectorAll('.graph-child-card')).map((child) => {
      const rect = child.getBoundingClientRect()
      return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom }
    })
    return {
      workspaceWidth: workspace.getBoundingClientRect().width,
      shellWidth: shellRect.width,
      canvasWidth: canvasRect.width,
      canvasLeft: canvasRect.left,
      canvasRight: canvasRect.right,
      childRects,
    }
  })

  if (geometry.shellWidth < geometry.workspaceWidth - 340) {
    throw new Error(`Graph shell collapsed to ${geometry.shellWidth}px inside a ${geometry.workspaceWidth}px workspace.`)
  }
  if (geometry.canvasWidth < 700) {
    throw new Error(`Graph canvas is too narrow: ${geometry.canvasWidth}px.`)
  }
  if (geometry.childRects.some((rect) => rect.left < geometry.canvasLeft - 1 || rect.right > geometry.canvasRight + 1)) {
    throw new Error('Graph child card is rendered outside the canvas bounds.')
  }

  console.log('Graph layout regression check passed')
} finally {
  await browser.close()
}
