import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import sharp from 'sharp'
import { XIAOZHI_FRAMES, xiaozhiFrame } from '../src/utils/xiaozhi.js'

test('existing callers and conversation events resolve to the new character', () => {
  for (const [face, expected] of Object.entries({ calm: 'standard', listen: 'listening', think: 'thinking', happy: 'complete', care: 'listening', sleepy: 'rest', welcome: 'welcome', craft: 'crafting' })) {
    assert.equal(xiaozhiFrame(face), expected)
  }
  assert.equal(xiaozhiFrame('', 'thinking'), 'thinking')
  assert.equal(xiaozhiFrame('', 'talking'), 'listening')
  assert.equal(xiaozhiFrame('rest', 'thinking'), 'rest')
  assert.equal(xiaozhiFrame('unknown'), 'standard')
  assert.equal(xiaozhiFrame('toString'), 'standard')
})

test('small portraits use the separate avatar master, including existing 30px bubbles', () => {
  for (const size of [28, 30, 40, 42, 44, 48]) assert.equal(xiaozhiFrame('', 'thinking', size), 'avatar')
  assert.equal(xiaozhiFrame('', 'thinking', 49), 'thinking')
})

test('all shipped states have equal canvases, alpha, clear margins and a bounded package cost', async () => {
  let bytes = 0
  for (const [key, src] of Object.entries(XIAOZHI_FRAMES)) {
    const file = new URL('../src' + src, import.meta.url)
    const metadata = await sharp(fileURLToPath(file)).metadata()
    assert.equal(metadata.hasAlpha, true, key)
    assert.equal(metadata.width, key === 'avatar' ? 144 : 384, key)
    assert.equal(metadata.width, metadata.height, key)
    bytes += fs.statSync(file).size
    const { data, info } = await sharp(fileURLToPath(file)).ensureAlpha().raw().toBuffer({ resolveWithObject: true })
    // Entire outer pixel border must be transparent, so no loop or tail is cropped.
    for (let n = 0; n < info.width; n++) {
      for (const [x, y] of [[n, 0], [n, info.height - 1], [0, n], [info.width - 1, n]]) {
        assert.equal(data[(y * info.width + x) * 4 + 3], 0, key + ' clipped edge')
      }
    }
  }
  assert.ok(bytes < 400 * 1024, 'mascot assets exceed 400 KiB')
  assert.ok(!fs.readdirSync(new URL('../src/static/mascot/', import.meta.url)).some(name => name.startsWith('xiaotuan-')), 'old mascot still shipped')
})
