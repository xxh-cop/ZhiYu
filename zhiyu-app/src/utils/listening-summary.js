export function summaryKindLabel(kind) {
  return kind === 'LISTENING_SUMMARY' ? '倾听小结' : ''
}

export function summaryDisplay(record) {
  const text = String((record && record.summary) || '').trim()
  if (!text) return '这段倾听还没有留下文字。'
  return text.length > 300 ? text.slice(0, 300) : text
}
