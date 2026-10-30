import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import { buildHeritageGeometry, normalizeHeritagePlan } from '../src/utils/heritage-pattern.js'

const catalogUrl = new URL('../../zhiyu-backend/src/main/resources/catalog/heritage-catalog.json', import.meta.url)

// These identities protect the agreed curation records, not the wording of their descriptions.
const curation = [
  {
    id: '970101', sourceId: 'ihc-xiabu', name: '民间绣活（夏布绣）', region: '新余',
    registryCode: 'Ⅶ-77', listedYear: 2014, family: 'stitch',
    studies: [
      ['971101', 'sprig', '细叶留白'], ['971102', 'petals', '花瓣小记'],
      ['971103', 'arcs', '弧线针影'], ['971104', 'grid', '格间针语'],
      ['971105', 'seed', '疏点成星'], ['971106', 'ribbon', '线的回环'],
    ],
  },
  {
    id: '970102', sourceId: 'ihc-bamboo', name: '竹编（瑞昌竹编）', region: '瑞昌',
    registryCode: 'Ⅶ-51', listedYear: 2008, family: 'weave',
    studies: [
      ['971201', 'plain', '经纬初见'], ['971202', 'twill', '斜纹节奏'],
      ['971203', 'basket', '双带交织'], ['971204', 'chevron', '折线山形'],
      ['971205', 'diamond', '菱格留光'], ['971206', 'window', '编一扇窗'],
    ],
  },
  {
    id: '970103', sourceId: 'ihc-paper', name: '剪纸（瑞昌剪纸）', region: '瑞昌',
    registryCode: 'Ⅶ-16', listedYear: 2008, family: 'cutout',
    studies: [
      ['971301', 'bloom', '花开纸上'], ['971302', 'leaves', '对叶相望'],
      ['971303', 'window', '方窗有光'], ['971304', 'fan', '展开半扇'],
      ['971305', 'stars', '纸上星点'], ['971306', 'double', '双环相连'],
    ],
  },
  {
    id: '970104', sourceId: 'ihc-porcelain', name: '景德镇手工制瓷技艺', region: '景德镇',
    registryCode: 'Ⅷ-7', listedYear: 2006, family: 'porcelain',
    studies: [
      ['971401', 'medallion', '器心一圈'], ['971402', 'border', '沿口小景'],
      ['971403', 'sprig', '一枝入画'], ['971404', 'wave', '回环水线'],
      ['971405', 'lattice', '方格有序'], ['971406', 'petal', '瓣影成环'],
    ],
  },
]
const sourceUrls = {
  'ihc-xiabu': 'https://www.ihchina.cn/project_details/14188.html',
  'ihc-bamboo': 'https://www.ihchina.cn/art/detail/id/14076.html',
  'ihc-paper': 'https://www.ihchina.cn/project_details/13952.html',
  'ihc-porcelain': 'https://www.ihchina.cn/project_details/14270.html',
}

function readCatalog() {
  assert.ok(fs.existsSync(catalogUrl), `缺少真实非遗目录文件：${catalogUrl.pathname}`)
  return JSON.parse(fs.readFileSync(catalogUrl, 'utf8'))
}

function nonEmptyText(value, label) {
  assert.equal(typeof value, 'string', `${label} must be text`)
  assert.ok(value.trim().length > 0, `${label} must not be blank`)
}

function textList(value, label) {
  assert.ok(Array.isArray(value), `${label} must be an array`)
  assert.ok(value.length > 0, `${label} must not be empty`)
  value.forEach((entry, index) => nonEmptyText(entry, `${label}[${index}]`))
}

test('catalog loads four sources, four traditions and twenty-four original studies', () => {
  const data = readCatalog()
  nonEmptyText(data.version, 'version')
  nonEmptyText(data.disclaimer, 'disclaimer')
  for (const [key, count] of [['sources', 4], ['traditions', 4], ['patterns', 24]]) {
    assert.ok(Array.isArray(data[key]), `${key} must be an array`)
    assert.equal(data[key].length, count, `${key} must retain the curated records`)
  }
})

test('all record IDs remain unique strings without numeric coercion', () => {
  const data = readCatalog()
  const records = [...data.sources, ...data.traditions, ...data.patterns]
  for (const record of records) nonEmptyText(record.id, 'record.id')
  assert.equal(new Set(records.map(record => record.id)).size, records.length)
  for (const record of [...data.traditions, ...data.patterns]) {
    assert.match(record.id, /^\d+$/, `${record.id} must be a decimal string`)
  }
})

test('each tradition retains its six distinct named family variants', () => {
  const { traditions, patterns } = readCatalog()
  assert.deepEqual(traditions.map(item => item.id).sort(), curation.map(item => item.id).sort())
  for (const expected of curation) {
    const tradition = traditions.find(item => item.id === expected.id)
    for (const field of ['name', 'region', 'registryCode', 'listedYear']) {
      assert.equal(tradition[field], expected[field], `${expected.id}.${field}`)
    }
    assert.ok(tradition.sourceIds.includes(expected.sourceId))
    const studies = patterns.filter(item => item.traditionId === expected.id)
    assert.equal(studies.length, 6)
    assert.equal(new Set(studies.map(item => item.variant)).size, 6)
    assert.ok(studies.every(item => item.family === expected.family))
    assert.deepEqual(
      studies.map(item => [item.id, item.variant, item.name]).sort(([a], [b]) => a.localeCompare(b)),
      expected.studies,
      `${expected.id} must retain the agreed ID / variant / name mapping`,
    )
  }
})

test('source and tradition references close and every source is actually used', () => {
  const { sources, traditions, patterns } = readCatalog()
  const sourceIds = new Set(sources.map(item => item.id))
  const traditionById = new Map(traditions.map(item => [item.id, item]))
  const citedByTraditions = new Set()
  const citedByPatterns = new Set()
  for (const [records, cited] of [[traditions, citedByTraditions], [patterns, citedByPatterns]]) {
    for (const record of records) {
      textList(record.sourceIds, `${record.id}.sourceIds`)
      assert.equal(new Set(record.sourceIds).size, record.sourceIds.length)
      for (const id of record.sourceIds) {
        assert.ok(sourceIds.has(id), `${record.id} has dangling source ${id}`)
        cited.add(id)
      }
    }
  }
  for (const pattern of patterns) {
    nonEmptyText(pattern.traditionId, `${pattern.id}.traditionId`)
    const tradition = traditionById.get(pattern.traditionId)
    assert.ok(tradition, `${pattern.id} has a dangling tradition`)
    assert.ok(pattern.sourceIds.every(id => tradition.sourceIds.includes(id)), `${pattern.id} cites unrelated background`)
  }
  assert.deepEqual(citedByTraditions, sourceIds)
  assert.deepEqual(citedByPatterns, sourceIds)
})

test('sources expose dated official references without granting external media reuse', () => {
  const { sources } = readCatalog()
  assert.deepEqual(sources.map(item => item.id).sort(), Object.keys(sourceUrls).sort())
  for (const source of sources) {
    for (const field of ['title', 'organization', 'url', 'accessedAt']) nonEmptyText(source[field], `${source.id}.${field}`)
    assert.equal(source.url, sourceUrls[source.id])
    const url = new URL(source.url)
    assert.equal(url.protocol, 'https:')
    assert.equal(url.hostname, 'www.ihchina.cn')
    assert.equal(url.username + url.password + url.search + url.hash, '')
    assert.match(source.accessedAt, /^\d{4}-\d{2}-\d{2}$/)
    const date = new Date(`${source.accessedAt}T00:00:00Z`)
    assert.ok(Number.isFinite(date.getTime()), `${source.id} has an invalid access date`)
    assert.equal(date.toISOString().slice(0, 10), source.accessedAt, 'reject normalized invalid calendar dates')
    assert.ok(source.accessedAt <= new Date().toISOString().slice(0, 10), 'access date must not be in the future')
    assert.equal(source.mediaReuse, 'NOT_GRANTED')
    textList(source.facts, `${source.id}.facts`)
  }
})

test('tradition detail consumers receive descriptions, materials, process and safety boundaries', () => {
  const { traditions } = readCatalog()
  for (const tradition of traditions) {
    for (const field of ['name', 'shortName', 'region', 'category', 'registryCode', 'summary', 'practiceBoundary']) {
      nonEmptyText(tradition[field], `${tradition.id}.${field}`)
    }
    assert.ok(['传统美术', '传统技艺'].includes(tradition.category))
    assert.ok(Number.isInteger(tradition.listedYear))
    for (const field of ['materials', 'process', 'safety']) textList(tradition[field], `${tradition.id}.${field}`)
    assert.ok(tradition.practiceBoundary.trim().length > 15, `${tradition.id} needs an explicit practice boundary`)
  }
})

test('pattern metadata stays inside the finite rendering and practice input boundaries', () => {
  const { patterns } = readCatalog()
  const allowedFamilies = new Set(['stitch', 'weave', 'cutout', 'porcelain'])
  const allowedLevels = new Set(['入门', '进阶'])
  for (const pattern of patterns) {
    assert.ok(allowedFamilies.has(pattern.family), `${pattern.id}.family`)
    assert.equal(pattern.origin, 'ORIGINAL_STUDY')
    assert.ok(Number.isInteger(pattern.detail), `${pattern.id}.detail must be an integer`)
    assert.ok(pattern.detail >= 4 && pattern.detail <= 12, `${pattern.id}.detail must fit the renderer contract`)
    assert.ok(pattern.detail >= 6 && pattern.detail <= 10, `${pattern.id}.detail must use a curated initial value`)
    assert.ok(Array.isArray(pattern.palette))
    assert.equal(pattern.palette.length, 4, `${pattern.id} needs background / ink / primary / secondary colors`)
    for (const color of pattern.palette) {
      assert.equal(typeof color, 'string')
      assert.match(color, /^#[0-9a-f]{6}$/i)
    }
    assert.ok(Number.isSafeInteger(pattern.minutes) && pattern.minutes > 0, `${pattern.id}.minutes must be a positive estimate`)
    assert.ok(allowedLevels.has(pattern.level), `${pattern.id}.level`)
  }
})

test('original study copy is present, differentiated and carries the non-clinical disclaimer', () => {
  const { disclaimer, traditions, patterns } = readCatalog()
  // The mandated safety notice is stable; editorial descriptions are deliberately not snapshot-tested.
  assert.ok(disclaimer.includes('本系统不能替代专业心理咨询'))
  assert.ok(disclaimer.includes('原创教学再设计'))
  assert.doesNotMatch(JSON.stringify({ disclaimer, traditions, patterns }), /抑郁症|焦虑症|你患有/)
  for (const pattern of patterns) {
    for (const field of ['name', 'variant', 'designNote', 'learningFocus']) nonEmptyText(pattern[field], `${pattern.id}.${field}`)
  }
  for (const tradition of traditions) {
    const studies = patterns.filter(item => item.traditionId === tradition.id)
    assert.equal(new Set(studies.map(item => item.designNote.trim())).size, 6, `${tradition.id} needs six differentiated design notes`)
  }
})

// ---------------------------------------------------------------------------
// Geometry core: finite, deterministic, data-only compositions.
// ---------------------------------------------------------------------------

const geometryFamilies = {
  stitch: ['sprig', 'petals', 'arcs', 'grid', 'seed', 'ribbon'],
  weave: ['plain', 'twill', 'basket', 'chevron', 'diamond', 'window'],
  cutout: ['bloom', 'leaves', 'window', 'fan', 'stars', 'double'],
  porcelain: ['medallion', 'border', 'sprig', 'wave', 'lattice', 'petal'],
}
const familyNames = Object.keys(geometryFamilies)
const studyPlans = familyNames.flatMap(family => geometryFamilies[family].map(variant => ({ family, variant })))

const invalidPlans = [
  undefined, null, [], ['stitch', 'sprig', 6], 'stitch', 6, NaN, Infinity, true,
  {}, { family: 'stitch' }, { variant: 'sprig' }, { family: 'stitch', variant: 'sprig' },
  { family: 'stitch', variant: 'sprig', detail: '6' },
  { family: 'stitch', variant: 'sprig', detail: NaN },
  { family: 'stitch', variant: 'sprig', detail: Infinity },
  { family: 'stitch', variant: 'sprig', detail: -Infinity },
  { family: 'stitch', variant: 'sprig', detail: 6.5 },
  { family: 'stitch', variant: 'sprig', detail: 4.0001 },
  { family: 'stitch', variant: 'sprig', detail: 3 },
  { family: 'stitch', variant: 'sprig', detail: 13 },
  { family: 'stitch', variant: 'sprig', detail: -0 },
  { family: 'stitch', variant: 'sprig', detail: true },
  { family: 'embroidery', variant: 'sprig', detail: 6 },
  { family: '__proto__', variant: 'sprig', detail: 6 },
  { family: 'stitch', variant: 'plain', detail: 6 },
  { family: 'weave', variant: 'bloom', detail: 6 },
  { family: 'cutout', variant: 'sprig', detail: 6 },
  { family: 'porcelain', variant: 'window', detail: 6 },
  { family: 'stitch', variant: 'sprig', detail: Number.MAX_SAFE_INTEGER },
  Object.defineProperty({ variant: 'sprig', detail: 6 }, 'family', {
    get() { throw new Error('unexpected property access') },
  }),
]

function assertPlainData(value, label, stack = []) {
  if (value === null || typeof value === 'number' || typeof value === 'boolean') return
  assert.notEqual(typeof value, 'string', `${label} must not contain text or markup`)
  assert.equal(typeof value, 'object', `${label} must be plain data`)
  assert.ok(!stack.includes(value), `${label} must not contain cycles`)
  const next = [...stack, value]
  if (Array.isArray(value)) value.forEach((entry, index) => assertPlainData(entry, `${label}[${index}]`, next))
  else for (const [key, entry] of Object.entries(value)) assertPlainData(entry, `${label}.${key}`, next)
}

function assertGeometry(geometry, label) {
  assert.ok(Array.isArray(geometry), `${label} must return an array`)
  assert.ok(geometry.length >= 1 && geometry.length <= 512, `${label} has ${geometry.length} layers`)
  assertPlainData(geometry, label)
  geometry.forEach((layer, index) => {
    const at = `${label} layer ${index}`
    assert.ok(layer && typeof layer === 'object' && !Array.isArray(layer), `${at} must be an object`)
    assert.deepEqual(Object.keys(layer).sort(), ['closed', 'fill', 'points', 'stroke', 'width'], `${at} keys`)
    assert.equal(typeof layer.closed, 'boolean', `${at}.closed`)
    for (const key of ['fill', 'stroke']) {
      if (layer[key] !== null) assert.ok(Number.isInteger(layer[key]) && layer[key] >= 0 && layer[key] <= 3, `${at}.${key}`)
    }
    assert.equal(typeof layer.width, 'number', `${at}.width must be numeric`)
    assert.ok(Number.isFinite(layer.width) && layer.width > 0 && layer.width <= 0.05, `${at}.width out of range`)
    assert.ok(Array.isArray(layer.points), `${at}.points must be an array`)
    assert.ok(layer.points.length >= 2 && layer.points.length <= 100, `${at} has ${layer.points.length} points`)
    layer.points.forEach((point, pointIndex) => {
      const pointAt = `${at}.points[${pointIndex}]`
      assert.ok(Array.isArray(point) && point.length === 2, `${pointAt} must be [x, y]`)
      point.forEach((coordinate, axis) => {
        const axisAt = `${pointAt}[${axis}]`
        assert.equal(typeof coordinate, 'number', `${axisAt} must be numeric`)
        assert.ok(Number.isFinite(coordinate), `${axisAt} must be finite`)
        assert.ok(coordinate >= 0 && coordinate <= 1, `${axisAt} must stay normalized`)
        assert.equal(Number(coordinate.toFixed(4)), coordinate, `${axisAt} must keep at most four decimals`)
      })
    })
  })
}

test('normalization accepts only the 24 finite family / variant pairs and strips unknown fields', () => {
  assert.equal(studyPlans.length, 24)
  for (const { family, variant } of studyPlans) {
    for (const detail of [4, 8, 12]) {
      const plan = normalizeHeritagePlan({ family, variant, detail, palette: ['#000000'], extra: { deep: true } })
      assert.deepEqual(plan, { family, variant, detail })
      assert.deepEqual(Object.keys(plan), ['family', 'variant', 'detail'])
    }
  }
})

test('normalization rejects malformed plans without throwing or falling back', () => {
  for (const value of invalidPlans) {
    let plan
    assert.doesNotThrow(() => { plan = normalizeHeritagePlan(value) }, `input ${String(value)}`)
    assert.equal(plan, null, `input ${String(value)}`)
    assert.deepEqual(buildHeritageGeometry(value), [])
  }
})

test('all twenty-four curated studies yield legal, distinct geometry', () => {
  const { patterns } = readCatalog()
  assert.equal(patterns.length, 24)
  const fullSignatures = new Set()
  const shapeSignatures = new Set()
  for (const pattern of patterns) {
    assert.deepEqual(normalizeHeritagePlan(pattern), {
      family: pattern.family,
      variant: pattern.variant,
      detail: pattern.detail,
    }, pattern.id)
    const geometry = buildHeritageGeometry(pattern)
    assertGeometry(geometry, pattern.id)
    fullSignatures.add(JSON.stringify(geometry))
    shapeSignatures.add(JSON.stringify(geometry.map(layer => layer.points)))
  }
  assert.equal(fullSignatures.size, 24, 'every catalog study needs its own composition')
  assert.equal(shapeSignatures.size, 24, 'compositions must differ in shape, not only in colour')
})

test('each family keeps six structurally different variants', () => {
  for (const family of familyNames) {
    const signatures = geometryFamilies[family].map(variant => {
      const geometry = buildHeritageGeometry({ family, variant, detail: 8 })
      assertGeometry(geometry, `${family}/${variant}`)
      return JSON.stringify(geometry.map(layer => layer.points))
    })
    assert.equal(new Set(signatures).size, 6, `${family} variants must not be recolours or copies`)
  }
})

test('shared variant names still differ between families', () => {
  for (const variant of ['sprig', 'window']) {
    const sharing = familyNames.filter(family => geometryFamilies[family].includes(variant))
    assert.equal(sharing.length, 2)
    const signatures = sharing.map(family => JSON.stringify(buildHeritageGeometry({ family, variant, detail: 8 })))
    assert.equal(new Set(signatures).size, sharing.length, `${variant} must respect its family structure`)
  }
})

test('detail 4 / 8 / 12 changes every composition', () => {
  for (const { family, variant } of studyPlans) {
    const signatures = [4, 8, 12].map(detail => JSON.stringify(buildHeritageGeometry({ family, variant, detail })))
    assert.equal(new Set(signatures).size, 3, `${family}/${variant} must respond to detail`)
  }
})

test('every detail level from 4 through 12 stays inside the geometry contract', () => {
  for (const { family, variant } of studyPlans) {
    for (let detail = 4; detail <= 12; detail++) {
      assertGeometry(buildHeritageGeometry({ family, variant, detail }), `${family}/${variant}/${detail}`)
    }
  }
})

test('builders ignore unknown fields and keep the caller object untouched', () => {
  const input = { family: 'stitch', variant: 'seed', detail: 7, name: 'ignored', palette: ['#ffffff'], nested: { count: 2 } }
  const snapshot = JSON.parse(JSON.stringify(input))
  const geometry = buildHeritageGeometry(input)
  assertGeometry(geometry, 'unknown-field plan')
  assert.deepEqual(input, snapshot)
  const frozen = Object.freeze({ family: 'weave', variant: 'window', detail: 9 })
  assert.deepEqual(normalizeHeritagePlan(frozen), { family: 'weave', variant: 'window', detail: 9 })
  assertGeometry(buildHeritageGeometry(frozen), 'frozen plan')
})

test('repeated builds are deterministic and independent of call order', () => {
  for (const pattern of readCatalog().patterns) {
    const first = buildHeritageGeometry(pattern)
    const second = buildHeritageGeometry({ ...pattern })
    assert.deepEqual(second, first, `${pattern.id} must be deterministic`)
    assert.equal(JSON.stringify(second), JSON.stringify(first))
  }
})

test('geometry stays pure data without browser globals', () => {
  assert.equal(typeof globalThis.window, 'undefined')
  assert.equal(typeof globalThis.document, 'undefined')
  for (const { family, variant } of studyPlans) {
    assertGeometry(buildHeritageGeometry({ family, variant, detail: 8 }), `${family}/${variant}`)
  }
})
