// Only bounded data reaches the renderer; model text is never inserted as markup.
export function normalizePlan(value = {}) {
 return { motif: ['flower', 'leaf', 'ripple'].includes(value.motif) ? value.motif : 'flower',
  petals: Number.isInteger(value.petals) && value.petals >= 6 && value.petals <= 16 ? value.petals : 12,
  rings: Number.isInteger(value.rings) && value.rings >= 1 && value.rings <= 3 ? value.rings : 2 }
}
export function makeShapes(value) {
 const plan = normalizePlan(value), shapes = []
 const step = .36 / plan.rings
 for (let ring = 1; ring <= plan.rings; ring++) {
  const radius = ring * step, half = Math.min(step * .38, radius * Math.sin(Math.PI / plan.petals) * .8)
  for (let i = 0; i < plan.petals; i++) {
   const angle = i * 2 * Math.PI / plan.petals + (ring % 2 ? 0 : Math.PI / plan.petals)
   const cx = .5 + Math.cos(angle) * radius, cy = .5 + Math.sin(angle) * radius
   const points = Array.from({ length: 32 }, (_, j) => {
    const t = j * Math.PI / 16
    const x = Math.cos(t) * half * (plan.motif === 'leaf' ? 1.15 : 1)
    const y = Math.sin(t) * half * (plan.motif === 'flower' ? .72 : plan.motif === 'leaf' ? .45 : 1)
    return [cx + x * Math.cos(angle) - y * Math.sin(angle), cy + x * Math.sin(angle) + y * Math.cos(angle)]
   })
   shapes.push(points)
  }
 }
 shapes.push(Array.from({ length: 32 }, (_, i) => [.5 + Math.cos(i * Math.PI / 16) * step * .36, .5 + Math.sin(i * Math.PI / 16) * step * .36]))
 return shapes
}
export function hitShape(shapes, x, y) {
 return shapes.findIndex(points => {
  let inside = false
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
   const [xi, yi] = points[i], [xj, yj] = points[j]
   if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
  }
  return inside
 })
}
export function drawShapes(ctx, shapes, size, colors = [], outline = false, callback) {
 ctx.clearRect(0, 0, size, size)
 if (!outline) { ctx.setFillStyle('#fafcfb'); ctx.fillRect(0, 0, size, size) }
 shapes.forEach((points, i) => {
  ctx.beginPath(); ctx.moveTo(points[0][0] * size, points[0][1] * size)
  points.slice(1).forEach(p => ctx.lineTo(p[0] * size, p[1] * size)); ctx.closePath()
  if (!outline) { ctx.setFillStyle(colors[i] || '#ffffff'); ctx.fill() }
  ctx.setStrokeStyle(outline ? '#193f38' : '#5a7b70'); ctx.setLineWidth(outline ? 2 : 1.4); ctx.stroke()
 })
 ctx.draw(false, callback)
}
