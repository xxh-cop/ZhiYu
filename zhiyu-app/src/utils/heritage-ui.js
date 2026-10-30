const FAMILY_LABELS = {
  stitch: '针线',
  weave: '编织',
  cutout: '剪刻',
  porcelain: '器面',
}

const VARIANT_LABELS = {
  'stitch:sprig': '细叶',
  'stitch:petals': '花瓣',
  'stitch:arcs': '弧线',
  'stitch:grid': '方格',
  'stitch:seed': '疏点',
  'stitch:ribbon': '回环',
  'weave:plain': '平纹',
  'weave:twill': '斜纹',
  'weave:basket': '双带',
  'weave:chevron': '山形',
  'weave:diamond': '菱格',
  'weave:window': '开窗',
  'cutout:bloom': '花形',
  'cutout:leaves': '对叶',
  'cutout:window': '方窗',
  'cutout:fan': '半扇',
  'cutout:stars': '星点',
  'cutout:double': '双环',
  'porcelain:medallion': '器心',
  'porcelain:border': '沿口',
  'porcelain:sprig': '一枝',
  'porcelain:wave': '水线',
  'porcelain:lattice': '方格',
  'porcelain:petal': '瓣影',
}

export function familyLabel(family) {
  return FAMILY_LABELS[family] || family || ''
}

export function variantLabel(family, variant) {
  return VARIANT_LABELS[`${family}:${variant}`] || variant || ''
}

export function patternImage(id) {
  return `/static/heritage/${String(id || '')}.png`
}

export function decoratePatterns(catalog) {
  const traditions = Array.isArray(catalog && catalog.traditions) ? catalog.traditions : []
  const byId = new Map(traditions.map(tradition => [tradition.id, tradition]))
  return (Array.isArray(catalog && catalog.patterns) ? catalog.patterns : []).map(pattern => {
    const tradition = byId.get(pattern.traditionId)
    return {
      ...pattern,
      traditionName: tradition ? tradition.name : '',
      traditionShortName: tradition ? tradition.shortName : '',
      region: tradition ? tradition.region : '',
      familyLabel: familyLabel(pattern.family),
    }
  })
}

export function filterHeritagePatterns(patterns, options = {}) {
  const keyword = String(options.keyword || '').trim().toLowerCase()
  const traditionId = String(options.traditionId || '')
  const seen = new Set()
  return (Array.isArray(patterns) ? patterns : []).filter(pattern => {
    if (!pattern || typeof pattern !== 'object' || !pattern.id) return false
    if (traditionId && pattern.traditionId !== traditionId) return false
    if (!keyword) return true
    return [pattern.name, pattern.region, pattern.traditionName, pattern.traditionShortName, pattern.learningFocus]
      .filter(Boolean)
      .some(value => String(value).toLowerCase().includes(keyword))
  }).filter(pattern => {
    if (seen.has(pattern.id)) return false
    seen.add(pattern.id)
    return true
  })
}

export function findHeritagePattern(patterns, id) {
  return (Array.isArray(patterns) ? patterns : []).find(pattern => pattern && pattern.id === String(id)) || null
}

export function findTradition(traditions, id) {
  return (Array.isArray(traditions) ? traditions : []).find(tradition => tradition && tradition.id === String(id)) || null
}

export function sourcesForPattern(catalog, pattern) {
  const sourceIds = new Set((pattern && pattern.sourceIds) || [])
  return (Array.isArray(catalog && catalog.sources) ? catalog.sources : [])
    .filter(source => sourceIds.has(source.id))
}
