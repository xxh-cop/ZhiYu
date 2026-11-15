/*
 * check-design-tokens.js —— 设计令牌收口审计（2026-11-15）。
 * 统计「未收口」的样式与图标，给出收口前后的对照数字；默认只报告，--strict 时对
 * emoji/字符图标与冷灰骨架屏报错（这两项必须清零）。
 * 用法：node scripts/check-design-tokens.js [--strict] [--json]
 */
const fs = require('fs')
const path = require('path')

const SRC = path.resolve(__dirname, '../src')
const SKIP = new Set(['node_modules', 'dist', 'unpackage', '.git'])
const files = []
;(function walk(dir) {
  for (const name of fs.readdirSync(dir)) {
    if (SKIP.has(name)) continue
    const full = path.join(dir, name)
    const stat = fs.statSync(full)
    if (stat.isDirectory()) walk(full)
    else if (/\.(vue|css|scss)$/.test(name)) files.push(full)
  }
})(SRC)

const RULES = {
  rawFontSize: { re: /font-size:\s*\d+rpx/g, label: '未用 zy-t-* / --zy-text-* 的裸字号' },
  hardBrandColor: { re: /#(217e76|1a645e|5bc4a8|8adcc6|8a6d1f|d4a017)\b/gi, label: '硬编码品牌色（应为 var(--zy-*)）', scope: 'style' },
  graphicColor: { re: /#(217e76|1a645e|5bc4a8|8adcc6)\b/gi, label: '模板/脚本中的图形色（SVG 与图标色，允许保留）', scope: 'markup' },
  coldGrey: { re: /#(E5E7EB|F3F4F6|9CA3AF|6B7280|4B5563|1F2937|D1D5DB|374151)\b/gi, label: '冷灰色值（与暖纸体系冲突）' },
  emojiIcon: { re: /[🔊▶😊😌😐😕😮💨📝]|>↗<|>↑</g, label: 'emoji / 字符图标（应为 ZyIcon / MoodFace）' },
  textLoading: { re: />(?:正在加载|加载中|正在读取)[^<]*…</g, label: '纯文字加载占位（应改骨架屏或 StateBlock）' },
  typeClass: { re: /zy-t-(display|title-lg|title|body-lg|body|caption|micro)/g, label: '已使用 zy-t-* 字阶（收口证据）' },
}

const report = {}
for (const [key, rule] of Object.entries(RULES)) {
  report[key] = { label: rule.label, total: 0, files: {} }
}
for (const file of files) {
  const text = fs.readFileSync(file, 'utf8')
  const styleText = file.endsWith('.vue')
    ? (text.match(/<style\b[^>]*>[\s\S]*?<\/style>/g) || []).join('\n')
    : text
  const markupText = file.endsWith('.vue') ? text.replace(/<style\b[^>]*>[\s\S]*?<\/style>/g, '') : ''
  for (const [key, rule] of Object.entries(RULES)) {
    const target = (rule.scope === 'style' ? styleText : rule.scope === 'markup' ? markupText : text)
      // var(--zy-*, #hex) 的回退值不算硬编码：令牌缺失时仍需可渲染
      .replace(/var\(--zy-[^,)]+,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--zy-token)')
    const hits = target.match(rule.re)
    if (hits && hits.length) {
      report[key].total += hits.length
      report[key].files[path.relative(SRC, file)] = hits.length
    }
  }
}

if (process.argv.includes('--json')) {
  console.log(JSON.stringify(report, null, 2))
} else {
  console.log('设计令牌收口审计（src/，共 ' + files.length + ' 个样式/Vue 文件）\n')
  for (const entry of Object.values(report)) {
    console.log(`${String(entry.total).padStart(5)}  ${entry.label}`)
    const top = Object.entries(entry.files).sort((a, b) => b[1] - a[1]).slice(0, 5)
    for (const [file, count] of top) console.log(`         ${count}  ${file}`)
  }
}
if (process.argv.includes('--strict')) {
  const bad = ['emojiIcon', 'coldGrey'].filter(k => report[k].total > 0)
  if (bad.length) {
    console.error('\n收口未通过：' + bad.map(k => report[k].label).join('；'))
    process.exit(1)
  }
}
