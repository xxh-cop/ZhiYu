/*
 * apply-design-tokens.js —— 一次性 codemod（2026-11-15 字阶/颜色收口）。
 * 只处理 <style> 块与 css/scss 文件，不动模板、脚本、SVG 属性与 theme.scss 令牌定义：
 *   1. font-size:NNrpx → var(--zy-text-*, 映射值)，全站收敛到 7 档（22/24/28/30/32/40/56）。
 *   2. 品牌色 / 冷灰 hex → var(--zy-*)，与暖纸青玉 v6 对齐。
 * 可重复执行（幂等）。执行后请跑 node scripts/check-design-tokens.js 复核。
 */
const fs = require('fs')
const path = require('path')

const SRC = path.resolve(__dirname, '../src')
const files = []
;(function walk(dir) {
  for (const name of fs.readdirSync(dir)) {
    if (['node_modules', 'dist', 'unpackage', '.git'].includes(name)) continue
    const full = path.join(dir, name)
    if (fs.statSync(full).isDirectory()) walk(full)
    else if (/\.(vue|css|scss)$/.test(name) && !/theme\.scss$/.test(name)) files.push(full)
  }
})(SRC)

const TYPE_SCALE = [
  { max: 22, token: 'micro', value: 22 },
  { max: 26, token: 'caption', value: 24 },
  { max: 28, token: 'body', value: 28 },
  { max: 30, token: 'body-lg', value: 30 },
  { max: 34, token: 'title', value: 32 },
  { max: 44, token: 'title-lg', value: 40 },
  { max: Infinity, token: 'display', value: 56 },
]

const COLORS = {
  '#217e76': 'var(--zy-brand-600, #217e76)',
  '#1a645e': 'var(--zy-brand-700, #1a645e)',
  '#2d9b8f': 'var(--zy-brand-500, #2d9b8f)',
  '#5bc4a8': 'var(--zy-brand-300, #5bc4a8)',
  '#8adcc6': 'var(--zy-brand-200, #8adcc6)',
  '#c9e4dd': 'var(--zy-brand-100, #c9e4dd)',
  '#eaf4f1': 'var(--zy-brand-50, #eaf4f1)',
  '#0f3d38': 'var(--zy-brand-900, #0f3d38)',
  '#8a6d1f': 'var(--zy-accent, #7a5c12)',
  '#7a5c12': 'var(--zy-accent, #7a5c12)',
  '#a64842': 'var(--zy-care, #a64842)',
  '#1f2937': 'var(--zy-title, #33302a)',
  '#374151': 'var(--zy-title, #33302a)',
  '#4b5563': 'var(--zy-text, #5c5648)',
  '#6b7280': 'var(--zy-text, #5c5648)',
  '#9ca3af': 'var(--zy-text, #5c5648)',
  '#d1d5db': 'var(--zy-line-soft, #e9e2d5)',
  '#e5e7eb': 'var(--zy-border, #e4dccb)',
  '#f3f4f6': 'var(--zy-bg, #f6f1e7)',
}

function transform(css) {
  let out = css.replace(/font-size:\s*(\d+)rpx/g, (m, n) => {
    const size = Number(n)
    if (!size) return m
    const step = TYPE_SCALE.find(s => size <= s.max)
    return `font-size:var(--zy-text-${step.token}, ${step.value}rpx)`
  })
  out = out.replace(/#[0-9a-fA-F]{6}\b/g, hex => COLORS[hex.toLowerCase()] || hex)
  return out
}

let changed = 0
for (const file of files) {
  const raw = fs.readFileSync(file, 'utf8')
  let next = raw
  if (file.endsWith('.vue')) {
    next = raw.replace(/(<style\b[^>]*>)([\s\S]*?)(<\/style>)/g, (m, open, css, close) => open + transform(css) + close)
  } else {
    next = transform(raw)
  }
  if (next !== raw) {
    fs.writeFileSync(file, next, 'utf8')
    changed++
  }
}
console.log(`apply-design-tokens: ${changed} / ${files.length} 个文件已收口`)
