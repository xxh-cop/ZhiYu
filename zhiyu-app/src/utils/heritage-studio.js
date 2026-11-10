const HEX_COLOR = /^#[0-9a-f]{6}$/i

export const STUDIO_COLORS = [
  { id: 'original', name: '初色', value: null },
  { id: 'terracotta', name: '陶红', value: '#B45F4B' },
  { id: 'indigo', name: '深蓝', value: '#315A7A' },
  { id: 'ochre', name: '赭色', value: '#A46B3E' },
]

export function createStudioState(pattern) {
  const palette = pattern && Array.isArray(pattern.palette) ? pattern.palette.slice(0, 4) : []
  if (palette.length !== 4 || palette.some(color => !HEX_COLOR.test(color))) {
    throw new TypeError('pattern.palette must contain four hex colors')
  }
  return {
    step: 1,
    running: false,
    started: false,
    completed: false,
    palette,
    originalPalette: palette.slice(),
    primaryColor: palette[2],
    history: [],
  }
}

export function applyPalette(state, palette) {
  const next = Array.isArray(palette) ? palette.slice(0, 4) : []
  if (next.length !== 4 || next.some(color => !HEX_COLOR.test(String(color || '')))) return state
  return {
    ...state,
    palette: next,
    originalPalette: next.slice(),
    primaryColor: next[2],
    history: [],
  }
}

export function setPrimaryColor(state, color) {
  if (!HEX_COLOR.test(String(color || '')) || color === state.primaryColor) return state
  const palette = state.palette.slice()
  palette[2] = color
  return {
    ...state,
    palette,
    primaryColor: color,
    history: [...state.history, state.palette.slice()],
  }
}

export function undoPrimaryColor(state) {
  if (!state.history.length) return state
  const history = state.history.slice(0, -1)
  const palette = state.history[state.history.length - 1].slice()
  return {
    ...state,
    palette,
    primaryColor: palette[2],
    history,
  }
}

export function startStudio(state) {
  if (state.completed) {
    const palette = state.originalPalette.slice()
    return {
      ...state,
      step: 1,
      running: true,
      started: true,
      completed: false,
      palette,
      primaryColor: palette[2],
      history: [],
    }
  }
  return state.running ? state : { ...state, running: true, started: true }
}

export function pauseStudio(state) {
  return state.running ? { ...state, running: false } : state
}

export function resumeStudio(state) {
  return !state.completed && !state.running ? { ...state, running: true, started: true } : state
}

export function advanceStudio(state) {
  if (!state.running || state.completed || state.step >= 4) return state
  return { ...state, step: state.step + 1 }
}

export function completeStudio(state) {
  if (!state.running || state.step < 4) return state
  return { ...state, running: false, completed: true }
}

export function drawHeritagePattern(ctx, layers, size, palette, options = {}) {
  if (!ctx || !Array.isArray(layers) || !Array.isArray(palette) || palette.length !== 4) return
  ctx.clearRect(0, 0, size, size)
  if (options.background !== false) {
    ctx.setFillStyle(palette[0])
    ctx.fillRect(0, 0, size, size)
  }
  for (const layer of layers) {
    if (!layer || !Array.isArray(layer.points) || layer.points.length < 2) continue
    ctx.beginPath()
    layer.points.forEach(([x, y], index) => {
      if (index === 0) ctx.moveTo(x * size, y * size)
      else ctx.lineTo(x * size, y * size)
    })
    if (layer.closed) ctx.closePath()
    if (layer.fill !== null && layer.fill >= 0 && layer.fill <= 3) {
      ctx.setFillStyle(palette[layer.fill])
      ctx.fill()
    }
    if (layer.stroke !== null && layer.stroke >= 0 && layer.stroke <= 3) {
      ctx.setStrokeStyle(palette[layer.stroke])
      ctx.setLineWidth(Math.max(1, layer.width * size))
      ctx.stroke()
    }
  }
}
