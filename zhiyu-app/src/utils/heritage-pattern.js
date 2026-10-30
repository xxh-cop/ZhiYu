// Deterministic geometry for the original heritage study patterns.
// The renderer receives plain normalized data only: no markup, DOM, randomness or clock.

const FAMILY_VARIANTS = {
  stitch: ['sprig', 'petals', 'arcs', 'grid', 'seed', 'ribbon'],
  weave: ['plain', 'twill', 'basket', 'chevron', 'diamond', 'window'],
  cutout: ['bloom', 'leaves', 'window', 'fan', 'stars', 'double'],
  porcelain: ['medallion', 'border', 'sprig', 'wave', 'lattice', 'petal'],
}

const MIN_DETAIL = 4
const MAX_DETAIL = 12
const TAU = Math.PI * 2
const MAX_POINTS = 100
const WEAVE_MARGIN = 0.1
const WEAVE_SPAN = 0.8

export function normalizeHeritagePlan(value) {
  try {
    if (value === null || typeof value !== 'object' || Array.isArray(value)) return null
    const family = value.family
    if (typeof family !== 'string' || !Object.prototype.hasOwnProperty.call(FAMILY_VARIANTS, family)) return null
    const variant = value.variant
    if (typeof variant !== 'string' || FAMILY_VARIANTS[family].indexOf(variant) < 0) return null
    const detail = value.detail
    if (!Number.isInteger(detail) || detail < MIN_DETAIL || detail > MAX_DETAIL) return null
    return { family, variant, detail }
  } catch (error) {
    return null
  }
}

export function buildHeritageGeometry(value) {
  const plan = normalizeHeritagePlan(value)
  if (!plan) return []
  const layers = []
  BUILDERS[plan.family][plan.variant](layers, plan.detail)
  return layers
}

// --- shared finite primitives -------------------------------------------------

function clampInt(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function clamp01(value) {
  return value < 0 ? 0 : value > 1 ? 1 : value
}

function round4(value) {
  const rounded = Math.round(value * 10000) / 10000
  return rounded === 0 ? 0 : rounded
}

function point(x, y) {
  return [round4(clamp01(x)), round4(clamp01(y))]
}

function colorIndex(value) {
  return Number.isInteger(value) && value >= 0 && value <= 3 ? value : null
}

function addLayer(layers, points, options) {
  if (!Array.isArray(points) || points.length < 2 || points.length > MAX_POINTS) return
  const settings = options || {}
  layers.push({
    points,
    closed: settings.closed === true,
    fill: colorIndex(settings.fill),
    stroke: colorIndex(settings.stroke),
    width: Math.min(0.05, Math.max(0.002, round4(settings.width === undefined ? 0.01 : settings.width))),
  })
}

function ellipse(cx, cy, rx, ry, count, rotation) {
  const points = []
  const start = rotation || 0
  for (let index = 0; index < count; index++) {
    const angle = start + (index / count) * TAU
    points.push(point(cx + Math.cos(angle) * rx, cy + Math.sin(angle) * ry))
  }
  return points
}

function arc(cx, cy, rx, ry, start, end, count) {
  const points = []
  for (let index = 0; index <= count; index++) {
    const angle = start + (end - start) * (index / count)
    points.push(point(cx + Math.cos(angle) * rx, cy + Math.sin(angle) * ry))
  }
  return points
}

function rect(x0, y0, x1, y1) {
  return [point(x0, y0), point(x1, y0), point(x1, y1), point(x0, y1)]
}

// A pointed leaf/petal grown from base to tip; the sides bulge by `width`.
function leaf(bx, by, tx, ty, width, half) {
  const dx = tx - bx
  const dy = ty - by
  const length = Math.sqrt(dx * dx + dy * dy) || 0.0001
  const nx = -dy / length
  const ny = dx / length
  const points = []
  for (let index = 0; index <= half; index++) {
    const turn = index / half
    const bulge = Math.sin(Math.PI * turn) * width
    points.push(point(bx + dx * turn + nx * bulge, by + dy * turn + ny * bulge))
  }
  for (let index = half - 1; index > 0; index--) {
    const turn = index / half
    const bulge = Math.sin(Math.PI * turn) * width
    points.push(point(bx + dx * turn - nx * bulge, by + dy * turn - ny * bulge))
  }
  return points
}

function sprigX(turn) {
  return 0.5 + 0.045 * Math.sin(turn * Math.PI * 0.9) - 0.02 * turn
}

function sprigY(turn) {
  return 0.85 - 0.7 * turn
}

function star(cx, cy, outer, inner, tips, rotation) {
  const points = []
  for (let index = 0; index < tips * 2; index++) {
    const radius = index % 2 === 0 ? outer : inner
    const angle = rotation + (index / (tips * 2)) * TAU
    points.push(point(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius))
  }
  return points
}

// --- stitch: thin stitched lines, short marks and sparse points --------------

function buildStitchSprig(layers, detail) {
  const pairs = clampInt(Math.floor(detail / 2), 2, 5)
  const stem = []
  for (let index = 0; index <= 10; index++) stem.push(point(sprigX(index / 10), sprigY(index / 10)))
  addLayer(layers, stem, { stroke: 1, width: 0.012 })
  for (let pair = 0; pair < pairs; pair++) {
    const y = 0.72 - (pair * 0.46) / Math.max(1, pairs - 1)
    const baseX = sprigX((0.85 - y) / 0.7)
    addLayer(layers, leaf(baseX, y, baseX - 0.135, y - 0.1, 0.02, 11), {
      closed: true, fill: null, stroke: 2, width: 0.011,
    })
    addLayer(layers, leaf(baseX, y - 0.005, baseX + 0.135, y - 0.105, 0.02, 11), {
      closed: true, fill: null, stroke: 3, width: 0.011,
    })
  }
  addLayer(layers, ellipse(sprigX(1), 0.145, 0.024, 0.036, 16), {
    closed: true, fill: 2, stroke: 1, width: 0.008,
  })
}

function buildStitchPetals(layers, detail) {
  const count = detail
  for (let index = 0; index < count; index++) {
    const angle = -Math.PI / 2 + (index / count) * TAU
    addLayer(layers, leaf(
      0.5 + Math.cos(angle) * 0.065,
      0.5 + Math.sin(angle) * 0.065,
      0.5 + Math.cos(angle) * 0.335,
      0.5 + Math.sin(angle) * 0.335,
      0.029,
      11,
    ), { closed: true, fill: null, stroke: index % 2 === 0 ? 2 : 3, width: 0.012 })
  }
  addLayer(layers, ellipse(0.5, 0.5, 0.048, 0.048, 20), {
    closed: true, fill: 2, stroke: 1, width: 0.008,
  })
  addLayer(layers, ellipse(0.5, 0.5, 0.085, 0.085, 28), {
    closed: true, fill: null, stroke: 1, width: 0.006,
  })
}

function buildStitchArcs(layers, detail) {
  const bands = clampInt(Math.round(detail / 3), 2, 4)
  const start = Math.PI * 1.1
  const end = Math.PI * 1.9
  const dashSpan = ((end - start) / detail) * 0.6
  for (let band = 0; band < bands; band++) {
    const radius = 0.15 + band * (0.2 / Math.max(1, bands - 1))
    for (let dash = 0; dash < detail; dash++) {
      const from = start + (dash / detail) * (end - start)
      const points = []
      for (let step = 0; step <= 3; step++) {
        const angle = from + dashSpan * (step / 3)
        points.push(point(0.5 + Math.cos(angle) * radius, 0.62 + Math.sin(angle) * radius * 0.88))
      }
      addLayer(layers, points, { stroke: band % 2 === 0 ? 2 : 3, width: 0.011 })
    }
  }
}

function buildStitchGrid(layers, detail) {
  const count = clampInt(Math.ceil(detail / 3), 2, 4)
  const low = 0.17
  const high = 0.83
  const step = (high - low) / count
  for (let index = 0; index <= count; index++) {
    addLayer(layers, [point(low + index * step, low), point(low + index * step, high)], {
      stroke: 1, width: 0.006,
    })
    addLayer(layers, [point(low, low + index * step), point(high, low + index * step)], {
      stroke: 1, width: 0.006,
    })
  }
  for (let row = 0; row < count; row++) {
    for (let column = 0; column < count; column++) {
      const left = low + column * step + step * 0.28
      const top = low + row * step + step * 0.28
      const right = low + column * step + step * 0.72
      const bottom = low + row * step + step * 0.72
      const points = (row + column) % 2 === 0
        ? [point(left, top), point(right, bottom)]
        : [point(right, top), point(left, bottom)]
      addLayer(layers, points, { stroke: (row + column) % 2 === 0 ? 2 : 3, width: 0.014 })
    }
  }
}

const SEED_ANCHORS = [
  [0.30, 0.24], [0.68, 0.28], [0.36, 0.64], [0.72, 0.70], [0.50, 0.46],
  [0.24, 0.48], [0.80, 0.48], [0.48, 0.80], [0.52, 0.18], [0.20, 0.72],
]
const SEED_RADII = [0.018, 0.024, 0.015, 0.021]

function buildStitchSeed(layers, detail) {
  const count = 6 + detail
  for (let index = 0; index < count; index++) {
    const anchor = SEED_ANCHORS[index % SEED_ANCHORS.length]
    const ring = Math.floor(index / SEED_ANCHORS.length)
    const angle = index * 2.399963229728653
    const offset = ring === 0 ? 0.018 : 0.032
    const radius = SEED_RADII[index % SEED_RADII.length]
    addLayer(layers, ellipse(
      anchor[0] + Math.cos(angle) * offset,
      anchor[1] + Math.sin(angle) * offset,
      radius,
      radius,
      8,
      angle,
    ), { closed: true, fill: index % 3 === 0 ? 3 : 2, stroke: 1, width: 0.006 })
  }
}

function buildStitchRibbon(layers, detail) {
  const loops = clampInt(Math.floor(detail / 4) + 1, 2, 4)
  const steps = 90
  const points = []
  for (let index = 0; index < steps; index++) {
    const turn = index / (steps - 1)
    const angle = turn * loops * TAU
    const radius = 0.05 + 0.24 * turn
    points.push(point(0.5 + Math.cos(angle) * radius, 0.5 + Math.sin(angle) * radius * 0.88))
  }
  addLayer(layers, points, { stroke: 2, width: 0.013 })
  const end = points[points.length - 1]
  addLayer(layers, [end, point(0.84, 0.58), point(0.88, 0.64)], { stroke: 3, width: 0.012 })
  addLayer(layers, ellipse(0.88, 0.64, 0.018, 0.018, 10), {
    closed: true, fill: 3, stroke: 1, width: 0.006,
  })
}

// --- weave: crossing bands, over/under rules and folded strips ----------------

function uniformBands(count) {
  const cell = WEAVE_SPAN / count
  return Array.from({ length: count }, (_, index) => ({
    center: WEAVE_MARGIN + cell * (index + 0.5),
    half: cell * 0.24,
  }))
}

// The band that is on top stays continuous; the hidden band is split at the crossing.
function weaveGrid(layers, columns, rows, over, horizontalFill, verticalFill) {
  const right = WEAVE_MARGIN + WEAVE_SPAN
  for (let rowIndex = 0; rowIndex < rows.length; rowIndex++) {
    const row = rows[rowIndex]
    let start = WEAVE_MARGIN
    for (let columnIndex = 0; columnIndex < columns.length; columnIndex++) {
      if (!over(columnIndex, rowIndex)) continue
      const column = columns[columnIndex]
      const left = column.center - column.half
      if (left - start > 0.0005) {
        addLayer(layers, rect(start, row.center - row.half, left, row.center + row.half), {
          closed: true, fill: horizontalFill, stroke: 1, width: 0.006,
        })
      }
      start = column.center + column.half
    }
    if (right - start > 0.0005) {
      addLayer(layers, rect(start, row.center - row.half, right, row.center + row.half), {
        closed: true, fill: horizontalFill, stroke: 1, width: 0.006,
      })
    }
  }
  for (let columnIndex = 0; columnIndex < columns.length; columnIndex++) {
    const column = columns[columnIndex]
    let start = WEAVE_MARGIN
    for (let rowIndex = 0; rowIndex < rows.length; rowIndex++) {
      if (over(columnIndex, rowIndex)) continue
      const row = rows[rowIndex]
      const top = row.center - row.half
      if (top - start > 0.0005) {
        addLayer(layers, rect(column.center - column.half, start, column.center + column.half, top), {
          closed: true, fill: verticalFill, stroke: 1, width: 0.006,
        })
      }
      start = row.center + row.half
    }
    if (right - start > 0.0005) {
      addLayer(layers, rect(column.center - column.half, start, column.center + column.half, right), {
        closed: true, fill: verticalFill, stroke: 1, width: 0.006,
      })
    }
  }
}

function buildWeavePlain(layers, detail) {
  const count = clampInt(3 + Math.floor((detail - 4) / 4), 3, 5)
  const bands = uniformBands(count)
  weaveGrid(layers, bands, bands, (column, row) => (column + row) % 2 === 0, 3, 2)
}

function buildWeaveTwill(layers, detail) {
  const count = clampInt(3 + Math.floor((detail - 4) / 4), 3, 5)
  const bands = uniformBands(count)
  weaveGrid(layers, bands, bands, (column, row) => ((column - row) % 3 + 3) % 3 === 0, 2, 3)
}

function buildWeaveBasket(layers, detail) {
  const groups = clampInt(Math.round(detail / 3), 2, 4)
  const groupSpan = WEAVE_SPAN / groups
  const bands = []
  for (let group = 0; group < groups; group++) {
    const start = WEAVE_MARGIN + group * groupSpan
    const half = groupSpan * 0.15
    bands.push(
      { center: start + groupSpan * 0.32, half },
      { center: start + groupSpan * 0.68, half },
    )
  }
  weaveGrid(layers, bands, bands, (column, row) => (Math.floor(column / 2) + Math.floor(row / 2)) % 2 === 0, 2, 3)
}

function buildWeaveChevron(layers, detail) {
  const count = clampInt(Math.round(detail / 3), 2, 4)
  for (const x of [0.3, 0.7]) {
    addLayer(layers, [point(x, 0.13), point(x, 0.87)], { stroke: 3, width: 0.016 })
  }
  const step = 0.52 / Math.max(1, count - 1)
  for (let index = 0; index < count; index++) {
    const base = 0.24 + index * step
    addLayer(layers, [point(0.13, base), point(0.5, base - 0.09), point(0.87, base)], {
      stroke: index % 2 === 0 ? 2 : 3, width: 0.032,
    })
  }
}

function buildWeaveDiamond(layers, detail) {
  const count = clampInt(Math.round(detail / 2.5), 2, 5)
  const low = 0.14
  const high = 0.86
  const spacing = 0.72 / (count + 1)
  for (let index = 0; index < count; index++) {
    const offset = -0.36 + (index + 1) * spacing
    let ax = low
    let ay = low + offset
    let bx = high
    let by = high + offset
    if (ay < low) {
      ax = low - offset
      ay = low
    }
    if (by > high) {
      bx = high - offset
      by = high
    }
    addLayer(layers, [point(ax, ay), point(bx, by)], { stroke: 2, width: 0.022 })

    const intercept = 1 + offset
    let cx = low
    let cy = intercept - low
    let dx = high
    let dy = intercept - high
    if (cy > high) {
      cx = intercept - high
      cy = high
    }
    if (dy < low) {
      dx = intercept - low
      dy = low
    }
    addLayer(layers, [point(cx, cy), point(dx, dy)], { stroke: 3, width: 0.022 })
  }
}

function buildWeaveWindow(layers, detail) {
  const units = clampInt(Math.ceil(detail / 3), 2, 4)
  const outerLow = 0.1
  const outerHigh = 0.9
  const innerLow = 0.26
  const innerHigh = 0.74
  addLayer(layers, rect(outerLow, outerLow, outerHigh, innerLow), { closed: true, fill: 3, stroke: 1, width: 0.008 })
  addLayer(layers, rect(outerLow, innerHigh, outerHigh, outerHigh), { closed: true, fill: 3, stroke: 1, width: 0.008 })
  addLayer(layers, rect(outerLow, innerLow, innerLow, innerHigh), { closed: true, fill: 2, stroke: 1, width: 0.008 })
  addLayer(layers, rect(innerHigh, innerLow, outerHigh, innerHigh), { closed: true, fill: 2, stroke: 1, width: 0.008 })
  for (let unit = 0; unit < units; unit++) {
    const at = (unit + 1) / (units + 1)
    const x = innerLow + at * (innerHigh - innerLow)
    const y = innerLow + at * (innerHigh - innerLow)
    addLayer(layers, rect(x - 0.015, outerLow, x + 0.015, innerLow), { closed: true, fill: 2, stroke: 1, width: 0.005 })
    addLayer(layers, rect(x - 0.015, innerHigh, x + 0.015, outerHigh), { closed: true, fill: 2, stroke: 1, width: 0.005 })
    addLayer(layers, rect(outerLow, y - 0.015, innerLow, y + 0.015), { closed: true, fill: 3, stroke: 1, width: 0.005 })
    addLayer(layers, rect(innerHigh, y - 0.015, outerHigh, y + 0.015), { closed: true, fill: 3, stroke: 1, width: 0.005 })
  }
  addLayer(layers, rect(innerLow, innerLow, innerHigh, innerHigh), { closed: true, fill: null, stroke: 1, width: 0.008 })
}

// --- cutout: solid silhouettes, holes and negative space ---------------------

function buildCutoutBloom(layers, detail) {
  const count = detail
  for (let index = 0; index < count; index++) {
    const angle = -Math.PI / 2 + (index / count) * TAU
    addLayer(layers, leaf(
      0.5 + Math.cos(angle) * 0.05,
      0.5 + Math.sin(angle) * 0.05,
      0.5 + Math.cos(angle) * 0.36,
      0.5 + Math.sin(angle) * 0.36,
      0.042,
      12,
    ), { closed: true, fill: 2, stroke: 1, width: 0.008 })
  }
  addLayer(layers, ellipse(0.5, 0.5, 0.1, 0.1, 28), { closed: true, fill: 0, stroke: 1, width: 0.008 })
  addLayer(layers, ellipse(0.5, 0.5, 0.045, 0.045, 16), { closed: true, fill: 3, stroke: 1, width: 0.006 })
}

function buildCutoutLeaves(layers, detail) {
  const pairs = clampInt(Math.floor(detail / 2), 2, 6)
  addLayer(layers, [point(0.5, 0.88), point(0.5, 0.12)], { stroke: 2, width: 0.014 })
  for (let pair = 0; pair < pairs; pair++) {
    const y = 0.74 - (pair * 0.48) / Math.max(1, pairs - 1)
    addLayer(layers, leaf(0.5, y, 0.36, y - 0.095, 0.022, 11), {
      closed: true, fill: 3, stroke: 1, width: 0.008,
    })
    addLayer(layers, leaf(0.5, y - 0.004, 0.64, y - 0.099, 0.022, 11), {
      closed: true, fill: 2, stroke: 1, width: 0.008,
    })
  }
  addLayer(layers, ellipse(0.5, 0.105, 0.035, 0.045, 16), {
    closed: true, fill: 3, stroke: 1, width: 0.008,
  })
}

function buildCutoutWindow(layers, detail) {
  const muntins = clampInt(Math.ceil(detail / 4), 1, 3)
  addLayer(layers, rect(0.16, 0.16, 0.84, 0.84), { closed: true, fill: 2, stroke: 1, width: 0.01 })
  addLayer(layers, rect(0.28, 0.28, 0.72, 0.72), { closed: true, fill: 0, stroke: 1, width: 0.008 })
  for (let index = 0; index < muntins; index++) {
    const at = (index + 1) / (muntins + 1)
    const x = 0.28 + at * 0.44
    const y = 0.28 + at * 0.44
    addLayer(layers, rect(x - 0.012, 0.28, x + 0.012, 0.72), { closed: true, fill: 3, stroke: 1, width: 0.005 })
    addLayer(layers, rect(0.28, y - 0.012, 0.72, y + 0.012), { closed: true, fill: 3, stroke: 1, width: 0.005 })
  }
}

function buildCutoutFan(layers, detail) {
  const slats = clampInt(Math.round(detail / 2), 2, 6)
  const cx = 0.5
  const cy = 0.82
  const radius = 0.33
  addLayer(layers, rect(0.18, 0.79, 0.82, 0.84), { closed: true, fill: 3, stroke: 1, width: 0.008 })
  for (let index = 0; index < slats; index++) {
    const step = Math.PI / slats
    const from = Math.PI + index * step + step * 0.16
    const to = Math.PI + (index + 1) * step - step * 0.16
    const points = [point(cx, cy)]
    for (let part = 0; part <= 10; part++) {
      const angle = from + (to - from) * (part / 10)
      points.push(point(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius))
    }
    points.push(point(cx, cy))
    addLayer(layers, points, { closed: true, fill: 2, stroke: 1, width: 0.008 })
  }
  addLayer(layers, arc(cx, cy, radius, radius, Math.PI, TAU, 28), { stroke: 1, width: 0.008 })
}

const STAR_ANCHORS = [
  [0.28, 0.24], [0.70, 0.28], [0.34, 0.68], [0.72, 0.70], [0.50, 0.46],
  [0.22, 0.50], [0.78, 0.50], [0.48, 0.80], [0.50, 0.16], [0.64, 0.80],
]
const STAR_RADII = [0.06, 0.048, 0.068, 0.044]

function buildCutoutStars(layers, detail) {
  const count = clampInt(Math.round(detail * 0.8), 4, 10)
  for (let index = 0; index < count; index++) {
    const anchor = STAR_ANCHORS[index % STAR_ANCHORS.length]
    const radius = STAR_RADII[index % STAR_RADII.length]
    addLayer(layers, star(anchor[0], anchor[1], radius, radius * 0.44, 5, -Math.PI / 2 + index * 0.31), {
      closed: true, fill: index % 2 === 0 ? 2 : 3, stroke: 1, width: 0.006,
    })
  }
}

function buildCutoutDouble(layers, detail) {
  const radius = 0.12 + (detail - 4) * 0.006
  const hole = radius * 0.55
  for (const cx of [0.33, 0.67]) {
    addLayer(layers, ellipse(cx, 0.5, radius, radius, 36), { closed: true, fill: 2, stroke: 1, width: 0.01 })
  }
  addLayer(layers, rect(0.4, 0.5 - 0.045, 0.6, 0.5 + 0.045), { closed: true, fill: 2, stroke: 1, width: 0.006 })
  for (const cx of [0.33, 0.67]) {
    addLayer(layers, ellipse(cx, 0.5, hole, hole, 28), { closed: true, fill: 0, stroke: 1, width: 0.006 })
  }
}

// --- porcelain: vessel boundary and round surface motifs ---------------------

function addPlate(layers, withRim) {
  addLayer(layers, ellipse(0.5, 0.5, 0.39, 0.39, 48), { closed: true, fill: null, stroke: 1, width: 0.012 })
  if (withRim !== false) {
    addLayer(layers, ellipse(0.5, 0.5, 0.355, 0.355, 48), { closed: true, fill: null, stroke: 3, width: 0.005 })
  }
}

function buildPorcelainMedallion(layers, detail) {
  addPlate(layers)
  addLayer(layers, ellipse(0.5, 0.5, 0.2, 0.2, 40), { closed: true, fill: 2, stroke: 1, width: 0.008 })
  addLayer(layers, ellipse(0.5, 0.5, 0.145, 0.145, 32), { closed: true, fill: 0, stroke: 1, width: 0.006 })
  addLayer(layers, ellipse(0.5, 0.5, 0.072, 0.072, 24), { closed: true, fill: 3, stroke: 1, width: 0.008 })
  for (let index = 0; index < detail; index++) {
    const angle = -Math.PI / 2 + (index / detail) * TAU
    addLayer(layers, [
      point(0.5 + Math.cos(angle) * 0.215, 0.5 + Math.sin(angle) * 0.215),
      point(0.5 + Math.cos(angle) * 0.258, 0.5 + Math.sin(angle) * 0.258),
    ], { stroke: 2, width: 0.012 })
  }
}

function buildPorcelainBorder(layers, detail) {
  addPlate(layers, false)
  addLayer(layers, ellipse(0.5, 0.5, 0.378, 0.378, 48), { closed: true, fill: 2, stroke: 1, width: 0.006 })
  addLayer(layers, ellipse(0.5, 0.5, 0.312, 0.312, 44), { closed: true, fill: 0, stroke: 1, width: 0.005 })
  const count = detail + 4
  for (let index = 0; index < count; index++) {
    const angle = (index / count) * TAU
    const cx = 0.5 + Math.cos(angle) * 0.345
    const cy = 0.5 + Math.sin(angle) * 0.345
    addLayer(layers, [
      point(cx + Math.cos(angle) * 0.024, cy + Math.sin(angle) * 0.024),
      point(cx - Math.sin(angle) * 0.016, cy + Math.cos(angle) * 0.016),
      point(cx - Math.cos(angle) * 0.024, cy - Math.sin(angle) * 0.024),
      point(cx + Math.sin(angle) * 0.016, cy - Math.cos(angle) * 0.016),
    ], { closed: true, fill: 3, stroke: 1, width: 0.004 })
  }
}

function buildPorcelainSprig(layers, detail) {
  addPlate(layers)
  const pairs = clampInt(Math.floor(detail / 2), 2, 5)
  const stem = []
  for (let index = 0; index <= 10; index++) {
    const turn = index / 10
    stem.push(point(0.4 + 0.2 * turn + 0.04 * Math.sin(turn * Math.PI), 0.72 - 0.44 * turn))
  }
  addLayer(layers, stem, { stroke: 2, width: 0.014 })
  for (let pair = 0; pair < pairs; pair++) {
    const turn = 0.2 + (pair * 0.6) / Math.max(1, pairs - 1)
    const x = 0.4 + 0.2 * turn + 0.04 * Math.sin(turn * Math.PI)
    const y = 0.72 - 0.44 * turn
    addLayer(layers, leaf(x, y, x - 0.125, y - 0.1, 0.021, 11), {
      closed: true, fill: 3, stroke: 1, width: 0.007,
    })
    addLayer(layers, leaf(x, y - 0.004, x + 0.125, y - 0.104, 0.021, 11), {
      closed: true, fill: 2, stroke: 1, width: 0.007,
    })
  }
  addLayer(layers, ellipse(0.6, 0.28, 0.028, 0.038, 16), {
    closed: true, fill: 3, stroke: 1, width: 0.007,
  })
}

function buildPorcelainWave(layers, detail) {
  addPlate(layers)
  const bands = clampInt(Math.round(detail / 3), 2, 4)
  for (let band = 0; band < bands; band++) {
    const baseY = 0.36 + (band * 0.28) / (bands - 1)
    const halfChord = Math.sqrt(Math.max(0, 0.34 * 0.34 - (baseY - 0.5) * (baseY - 0.5))) * 0.82
    const points = []
    for (let step = 0; step <= 24; step++) {
      const turn = step / 24
      points.push(point(
        0.5 - halfChord + 2 * halfChord * turn,
        baseY + Math.sin(turn * TAU * 2) * 0.032,
      ))
    }
    addLayer(layers, points, { stroke: band % 2 === 0 ? 2 : 3, width: 0.014 })
  }
}

function buildPorcelainLattice(layers, detail) {
  addPlate(layers)
  const lines = clampInt(Math.ceil(detail / 3), 2, 4)
  const spacing = 0.56 / lines
  for (let index = 0; index < lines; index++) {
    const offset = (index - (lines - 1) / 2) * spacing
    const half = Math.sqrt(Math.max(0, 0.34 * 0.34 - offset * offset)) * 0.92
    addLayer(layers, [point(0.5 + offset, 0.5 - half), point(0.5 + offset, 0.5 + half)], {
      stroke: 2, width: 0.012,
    })
    addLayer(layers, [point(0.5 - half, 0.5 + offset), point(0.5 + half, 0.5 + offset)], {
      stroke: 3, width: 0.012,
    })
  }
}

function buildPorcelainPetal(layers, detail) {
  addPlate(layers)
  for (let index = 0; index < detail; index++) {
    const angle = -Math.PI / 2 + (index / detail) * TAU
    addLayer(layers, leaf(
      0.5 + Math.cos(angle) * 0.08,
      0.5 + Math.sin(angle) * 0.08,
      0.5 + Math.cos(angle) * 0.36,
      0.5 + Math.sin(angle) * 0.36,
      0.03,
      11,
    ), { closed: true, fill: index % 2 === 0 ? 2 : 3, stroke: 1, width: 0.007 })
  }
}

// --- dispatch ----------------------------------------------------------------

const BUILDERS = {
  stitch: {
    sprig: buildStitchSprig,
    petals: buildStitchPetals,
    arcs: buildStitchArcs,
    grid: buildStitchGrid,
    seed: buildStitchSeed,
    ribbon: buildStitchRibbon,
  },
  weave: {
    plain: buildWeavePlain,
    twill: buildWeaveTwill,
    basket: buildWeaveBasket,
    chevron: buildWeaveChevron,
    diamond: buildWeaveDiamond,
    window: buildWeaveWindow,
  },
  cutout: {
    bloom: buildCutoutBloom,
    leaves: buildCutoutLeaves,
    window: buildCutoutWindow,
    fan: buildCutoutFan,
    stars: buildCutoutStars,
    double: buildCutoutDouble,
  },
  porcelain: {
    medallion: buildPorcelainMedallion,
    border: buildPorcelainBorder,
    sprig: buildPorcelainSprig,
    wave: buildPorcelainWave,
    lattice: buildPorcelainLattice,
    petal: buildPorcelainPetal,
  },
}
