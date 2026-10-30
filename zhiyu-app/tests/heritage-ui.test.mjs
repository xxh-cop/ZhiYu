import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import {
  decoratePatterns,
  familyLabel,
  filterHeritagePatterns,
  findHeritagePattern,
  findTradition,
  patternImage,
  sourcesForPattern,
  variantLabel,
} from '../src/utils/heritage-ui.js'

const catalogUrl = new URL('../../zhiyu-backend/src/main/resources/catalog/heritage-catalog.json', import.meta.url)
const catalog = JSON.parse(fs.readFileSync(catalogUrl, 'utf8'))
const patterns = decoratePatterns(catalog)

test('decorates all 24 patterns with their tradition and family label', () => {
  assert.equal(patterns.length, 24)
  assert.equal(patterns.filter(pattern => pattern.traditionId === '970102').length, 6)
  const weave = patterns.find(pattern => pattern.id === '971201')
  assert.equal(weave.traditionShortName, '瑞昌竹编')
  assert.equal(weave.traditionName, '竹编（瑞昌竹编）')
  assert.equal(weave.region, '瑞昌')
  assert.equal(weave.familyLabel, '编织')
  assert.equal(familyLabel('unknown'), 'unknown')
  assert.equal(variantLabel('weave', 'twill'), '斜纹')
  assert.equal(variantLabel('cutout', 'window'), '方窗')
  assert.equal(variantLabel('cutout', 'double'), '双环')
  assert.equal(variantLabel('weave', 'unknown'), 'unknown')
})

test('filters by tradition, keyword and combined criteria without duplicates', () => {
  assert.equal(filterHeritagePatterns(patterns).length, 24)
  assert.equal(filterHeritagePatterns(patterns, { traditionId: '970102' }).length, 6)
  assert.equal(filterHeritagePatterns(patterns, { keyword: '竹编' }).length, 6)
  assert.equal(filterHeritagePatterns(patterns, { keyword: '新余' }).length, 6)
  assert.equal(filterHeritagePatterns(patterns, { keyword: '不存在' }).length, 0)
  assert.equal(filterHeritagePatterns(patterns, { traditionId: '970103', keyword: '剪纸' }).length, 6)
  const duplicated = filterHeritagePatterns([...patterns, patterns[0]], { traditionId: '970101' })
  assert.equal(duplicated.length, 6)
})

test('looks up patterns, traditions and only the pattern source citations', () => {
  const pattern = findHeritagePattern(patterns, '971201')
  assert.equal(pattern.name, '经纬初见')
  assert.equal(findHeritagePattern(patterns, 'missing'), null)
  const tradition = findTradition(catalog.traditions, pattern.traditionId)
  assert.equal(tradition.shortName, '瑞昌竹编')
  const sources = sourcesForPattern(catalog, pattern)
  assert.equal(sources.length, 1)
  assert.equal(sources[0].id, 'ihc-bamboo')
  assert.equal(sources[0].mediaReuse, 'NOT_GRANTED')
})

test('maps pattern ids to the generated local image paths', () => {
  assert.equal(patternImage('971201'), '/static/heritage/971201.png')
  assert.ok(fs.existsSync(new URL('../src/static/heritage/971201.png', import.meta.url)))
})
