import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import { buildHeritageGraph, shortTitle } from '../src/utils/heritage-graph-layout.js'

const catalog = JSON.parse(fs.readFileSync(
  new URL('../../zhiyu-backend/src/main/resources/catalog/heritage-catalog.json', import.meta.url),
  'utf8'
))

test('builds four tradition nodes, four source nodes and closed edges', () => {
  const graph = buildHeritageGraph(catalog)
  assert.equal(graph.nodes.filter(node => node.type === 'tradition').length, 4)
  assert.equal(graph.nodes.filter(node => node.type === 'source').length, 4)
  assert.ok(graph.edges.length >= 4)
  const ids = new Set(graph.nodes.map(node => node.id))
  assert.ok(graph.edges.every(edge => ids.has(edge.from) && ids.has(edge.to)))
  assert.equal(graph.groups.length, 4)
  assert.equal(graph.groups.reduce((sum, group) => sum + group.patternCount, 0), 24)
  assert.deepEqual(buildHeritageGraph(catalog), graph)
})

test('shortens long source labels for canvas while keeping a full accessible list', () => {
  assert.ok(shortTitle('中国非物质文化遗产网·中国非物质文化遗产数字博物馆').length <= 8)
  const graph = buildHeritageGraph(catalog)
  assert.deepEqual(graph.nodes.filter(node => node.type === 'source').map(node => node.label),
    ['来源1', '来源2', '来源3', '来源4'])
  assert.ok(graph.groups.every(group => group.sources.length > 0))
  assert.ok(graph.groups.every(group => group.patterns.length > 0))
})
