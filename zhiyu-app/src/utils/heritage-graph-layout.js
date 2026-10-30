const MAX_LABEL = 8

export function shortTitle(value) {
  const text = String(value || '').trim()
  return text.length <= MAX_LABEL ? text : text.slice(0, MAX_LABEL)
}

export function buildHeritageGraph(catalog) {
  const traditions = Array.isArray(catalog && catalog.traditions) ? catalog.traditions.slice(0, 4) : []
  const sources = Array.isArray(catalog && catalog.sources) ? catalog.sources.slice(0, 4) : []
  const patterns = Array.isArray(catalog && catalog.patterns) ? catalog.patterns : []
  const nodes = []
  const edges = []

  traditions.forEach((tradition, index) => {
    const angle = -Math.PI / 2 + (index * Math.PI) / 2
    nodes.push({
      id: tradition.id,
      type: 'tradition',
      label: shortTitle(tradition.shortName || tradition.name),
      x: 50 + Math.cos(angle) * 26,
      y: 50 + Math.sin(angle) * 26,
    })
  })
  sources.forEach((source, index) => {
    const angle = -Math.PI / 4 + (index * Math.PI) / 2
    nodes.push({
      id: source.id,
      type: 'source',
      label: `来源${index + 1}`,
      x: 50 + Math.cos(angle) * 42,
      y: 50 + Math.sin(angle) * 42,
    })
  })

  const nodeIds = new Set(nodes.map(node => node.id))
  const seen = new Set()
  traditions.forEach(tradition => {
    ;(tradition.sourceIds || []).forEach(sourceId => {
      const key = `${tradition.id}>${sourceId}`
      if (!nodeIds.has(tradition.id) || !nodeIds.has(sourceId) || seen.has(key)) return
      seen.add(key)
      edges.push({ from: tradition.id, to: sourceId })
    })
  })

  const groups = traditions.map(tradition => {
    const ownPatterns = patterns.filter(pattern => pattern.traditionId === tradition.id)
    return {
      id: tradition.id,
      title: tradition.name,
      region: tradition.region,
      patternCount: ownPatterns.length,
      patterns: ownPatterns.slice(0, 6).map(pattern => pattern.name),
      sources: sources
        .filter(source => (tradition.sourceIds || []).includes(source.id))
        .map(source => source.title),
    }
  })

  return { nodes, edges, groups }
}
