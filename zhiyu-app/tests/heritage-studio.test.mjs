import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import {
  STUDIO_COLORS,
  advanceStudio,
  applyPalette,
  completeStudio,
  createStudioState,
  drawHeritagePattern,
  pauseStudio,
  resumeStudio,
  setPrimaryColor,
  startStudio,
  undoPrimaryColor,
} from '../src/utils/heritage-studio.js'
import { buildHeritageGeometry } from '../src/utils/heritage-pattern.js'

const catalog = JSON.parse(fs.readFileSync(new URL('../../zhiyu-backend/src/main/resources/catalog/heritage-catalog.json', import.meta.url), 'utf8'))
const pattern = catalog.patterns.find(item => item.id === '971201')
const geometry = buildHeritageGeometry(pattern)

test('studio starts at step one with the original palette and no history', () => {
  const state = createStudioState(pattern)
  assert.equal(state.step, 1)
  assert.equal(state.running, false)
  assert.equal(state.started, false)
  assert.equal(state.completed, false)
  assert.deepEqual(state.palette, pattern.palette)
  assert.deepEqual(state.originalPalette, pattern.palette)
  assert.equal(state.primaryColor, pattern.palette[2])
  assert.deepEqual(state.history, [])
  const next = setPrimaryColor(state, '#B45F4B')
  assert.notEqual(next, state)
  assert.equal(next.palette[2], '#B45F4B')
  assert.equal(next.primaryColor, '#B45F4B')
  assert.deepEqual(next.history[0], pattern.palette)
  assert.deepEqual(state.palette, pattern.palette)
})

test('color changes can be undone and invalid choices are ignored', () => {
  const state = createStudioState(pattern)
  const changed = setPrimaryColor(state, '#315A7A')
  const undone = undoPrimaryColor(changed)
  assert.deepEqual(undone.palette, pattern.palette)
  assert.equal(undone.primaryColor, pattern.palette[2])
  assert.deepEqual(undone.history, [])
  assert.deepEqual(setPrimaryColor(state, '#315A7A').palette, setPrimaryColor(state, '#315A7A').palette)
  assert.equal(setPrimaryColor(state, 'not-a-color'), state)
  assert.equal(setPrimaryColor(state, pattern.palette[2]), state)
})

test('steps only advance while running and completion needs step four', () => {
  const idle = createStudioState(pattern)
  const started = startStudio(idle)
  assert.equal(started.running, true)
  assert.equal(started.started, true)
  assert.equal(advanceStudio(idle), idle)
  const step2 = advanceStudio(started)
  assert.equal(step2.step, 2)
  const paused = pauseStudio(step2)
  assert.equal(paused.running, false)
  assert.equal(paused.started, true)
  assert.equal(advanceStudio(paused), paused)
  const resumed = resumeStudio(paused)
  assert.equal(resumed.started, true)
  const step3 = advanceStudio(resumed)
  const step4 = advanceStudio(step3)
  assert.equal(step4.step, 4)
  assert.equal(completeStudio(step3), step3)
  const done = completeStudio(step4)
  assert.equal(done.completed, true)
  assert.equal(done.running, false)
})

test('restarting a completed studio returns to the original palette', () => {
  let state = createStudioState(pattern)
  state = setPrimaryColor(state, '#315A7A')
  state = startStudio(state)
  state = advanceStudio(advanceStudio(advanceStudio(state)))
  state = completeStudio(state)
  const restarted = startStudio(state)
  assert.equal(restarted.step, 1)
  assert.equal(restarted.running, true)
  assert.equal(restarted.completed, false)
  assert.deepEqual(restarted.palette, pattern.palette)
  assert.deepEqual(restarted.history, [])
})

test('applying a suggested palette keeps the exercise progress and resets color history', () => {
  let state = createStudioState(pattern)
  state = startStudio(state)
  state = advanceStudio(advanceStudio(state))
  state = setPrimaryColor(state, '#315A7A')
  const suggestion = ['#EFE6D1', '#3F4A3B', '#8C9A66', '#C9A66B']
  const applied = applyPalette(state, suggestion)
  assert.equal(applied.step, 3)
  assert.equal(applied.running, true)
  assert.deepEqual(applied.palette, suggestion)
  assert.deepEqual(applied.originalPalette, suggestion)
  assert.equal(applied.primaryColor, suggestion[2])
  assert.deepEqual(applied.history, [])
  assert.equal(applyPalette(state, ['#fff']), state)
  assert.equal(applyPalette(state, ['#EFE6D1', '#3F4A3B', '#8C9A66', 'nope']), state)
})

test('draw helper paints a background and finite geometry layers', () => {
  const calls = []
  const ctx = {
    clearRect: (...args) => calls.push(['clearRect', ...args]),
    fillRect: (...args) => calls.push(['fillRect', ...args]),
    setFillStyle: color => calls.push(['setFillStyle', color]),
    setStrokeStyle: color => calls.push(['setStrokeStyle', color]),
    setLineWidth: width => calls.push(['setLineWidth', width]),
    beginPath: () => calls.push(['beginPath']),
    moveTo: (x, y) => calls.push(['moveTo', x, y]),
    lineTo: (x, y) => calls.push(['lineTo', x, y]),
    closePath: () => calls.push(['closePath']),
    fill: () => calls.push(['fill']),
    stroke: () => calls.push(['stroke']),
  }
  drawHeritagePattern(ctx, geometry, 240, pattern.palette)
  assert.deepEqual(calls[0], ['clearRect', 0, 0, 240, 240])
  assert.ok(calls.some(call => call[0] === 'setFillStyle' && call[1] === pattern.palette[0]))
  assert.ok(calls.some(call => call[0] === 'fillRect' && call[1] === 0 && call[4] === 240))
  assert.ok(calls.filter(call => call[0] === 'beginPath').length > 3)
  assert.ok(calls.some(call => call[0] === 'fill'))
  assert.ok(calls.some(call => call[0] === 'stroke'))
})

test('studio color presets expose four named choices including the original', () => {
  assert.equal(STUDIO_COLORS.length, 4)
  assert.equal(STUDIO_COLORS[0].id, 'original')
  assert.equal(new Set(STUDIO_COLORS.map(option => option.id)).size, 4)
  for (const option of STUDIO_COLORS) {
    assert.ok(option.name)
    if (option.value !== null) assert.match(option.value, /^#[0-9a-f]{6}$/i)
  }
})
