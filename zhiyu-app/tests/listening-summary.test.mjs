import test from 'node:test'
import assert from 'node:assert/strict'
import { summaryDisplay, summaryKindLabel } from '../src/utils/listening-summary.js'

test('labels saved summaries and falls back for generated guidance', () => {
  assert.equal(summaryKindLabel('LISTENING_SUMMARY'), '倾听小结')
  assert.equal(summaryDisplay({ summary: '  ', generated: false }), '这段倾听还没有留下文字。')
  assert.equal(summaryDisplay({ summary: '  一句小结  ', generated: false }), '一句小结')
  assert.equal(summaryDisplay({ summary: 'a'.repeat(320) }).length, 300)
})
