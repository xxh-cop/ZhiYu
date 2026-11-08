/**
 * 令牌对比度闸口：读取 design/tokens.json（单一事实源），核对暖纸青玉关键前景/背景组合
 * 满足 WCAG 下限；任一组合退化即失败退出。
 *
 * 背景：2026-11-08 暖纸青玉 v6 换底时实测发现两组不达标并修复——
 *   brand-600 文字/暖纸底 4.33:1（<4.5，故文字级品牌色改用 brand-700）
 *   accent #8A6D1F/暖金 tint 4.05:1（<4.5，加深为 #7A5C12 → 5.16:1）
 * 本脚本把该结论固化：改令牌导致对比度退化时当场失败。
 *
 * 基线实录（2026-11-08 实测，脚本复核值应与之接近）：
 *   title/页面底 11.68 · text/页面底 6.48 · 白字/brand-600 4.79
 *   brand-700/页面底 6.16 · accent(#7A5C12)/暖金 tint 5.16 · faint/页面底 3.33（仅装饰）
 *
 * 用法：node scripts/check-contrast.js
 * 退出码：0 = 全部达标；1 = 存在不达标组合（逐条打印组合名、实测值、要求值）。
 */
const fs = require('fs');
const path = require('path');

const tokensPath = path.join(__dirname, '..', '..', 'design', 'tokens.json');
const tokens = JSON.parse(fs.readFileSync(tokensPath, 'utf8'));
const N = tokens.neutral, B = tokens.brand, S = tokens.semantic, A = tokens.accent;

function luminance(hex) {
  const c = hex.replace('#', '').match(/../g)
    .map((v) => parseInt(v, 16) / 255)
    .map((v) => (v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)));
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
}
function contrast(fg, bg) {
  const [a, b] = [luminance(fg), luminance(bg)].sort((x, y) => y - x);
  return (a + 0.05) / (b + 0.05);
}

/** [组合名, 前景 hex, 背景 hex, 最低要求] */
const pairs = [
  ['title / 页面底', N.text, N.bg, 4.5],
  ['title / 卡片', N.text, N.surface, 4.5],
  ['text 正文 / 页面底', N.textSecondary, N.bg, 4.5],
  ['text 正文 / 卡片', N.textSecondary, N.surface, 4.5],
  ['text 正文 / 薄荷 tint', N.textSecondary, N.tint, 4.5],
  ['text 正文 / 暖金 tint', N.textSecondary, N.tintWarm, 4.5],
  ['白字 / brand-600 实心按钮', N.surface, B['600'], 4.5],
  ['brand-700 文字 / 页面底（文字级品牌色）', B['700'], N.bg, 4.5],
  ['brand-700 / 薄荷 tint（secondary 按钮字）', B['700'], N.tint, 4.5],
  ['accent 深金文字 / 暖金 tint', A.goldText, N.tintWarm, 4.5],
  ['warningText 正文级警示 / 页面底', S.warningText, N.bg, 4.5],
  ['care 绛红 / 页面底', S.care, N.bg, 4.5],
  ['白字 / care 实心按钮', N.surface, S.care, 4.5],
  ['faint 装饰灰 / 页面底（仅装饰 eyebrow，图形级）', N.faint, N.bg, 3.0],
];

let fail = 0;
for (const [name, fg, bg, min] of pairs) {
  if (!fg || !bg) {
    console.error(`[check-contrast] 令牌缺失: ${name}（fg=${fg} bg=${bg}）`);
    fail++;
    continue;
  }
  const r = contrast(fg, bg);
  if (r < min) {
    console.error(`[check-contrast] 不达标: ${name}  实测 ${r.toFixed(2)}:1，要求 ≥${min}:1（fg ${fg} / bg ${bg}）`);
    fail++;
  } else {
    console.log(`[check-contrast] 达标 ${r.toFixed(2)}:1（≥${min}）  ${name}`);
  }
}
if (fail) {
  console.error(`\n[check-contrast] 失败：${fail} 组不达标。令牌改动不得降低可访问性，请回退或调整用途边界。`);
  process.exit(1);
}
console.log(`\n[check-contrast] 通过：${pairs.length} 组全部达标`);
