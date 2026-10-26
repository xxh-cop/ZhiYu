// Only an allowlisted internal destination can survive the login boundary.
export function safeWorkReturn(value) {
  if (typeof value !== 'string') return ''
  let decoded = value
  try { decoded = decodeURIComponent(value) } catch { return '' }
  return /^\/pages\/(?:work\/detail|craft\/detail|programs\/index)\?id=[1-9]\d{0,18}$/.test(decoded) ? decoded : ''
}
export function workPath(id) {
  return safeWorkReturn(`/pages/work/detail?id=${id}`)
}
export function workLoginUrl(id) {
  const next = workPath(id)
  return '/pages/login/index' + (next ? `?next=${encodeURIComponent(next)}` : '')
}
export function workShareUrl(id, location) {
  const path = workPath(id)
  if (!path) return ''
  return `${location.origin}${location.pathname}#${path}`
}
