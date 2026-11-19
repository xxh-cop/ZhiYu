/**
 * 令牌对比度闸口：读取 design/tokens.json（单一事实源），核对「云白（默认）/ 暖纸（可选）/
 * 玄墨（深色）」三套主题下关键前景/背景组合满足 WCAG 下限；任一主题任一组合退化即失败退出。
 *
 * 背景：2026-11-08 暖纸青玉 v6 换底时实测发现两组不达标并修复——
 *   brand-600 文字/暖纸底 4.33:1（<4.5，故文字级品牌色改用 brand-700）
 *   accent #8A6D1F/暖金 tint 4.05:1（<4.5，加深为 #7A5C12 → 5.16:1）
 * 2026-11-08 页面配色双主题化：云白设为默认主题后全部组合按白底重算，
 * 不许沿用暖底结论；暖纸主题切回也必须全绿，不留半成品。
 * 2026-11-19 玄墨深色纳入：深色覆盖取自 tokens.json 的 dark 段（与 styles/theme.scss
 * 的 .zy-theme-dark 对齐）。纳入即暴露「白字压品牌填充 4.83:1」贴线问题，dark.brand600
 * 加深为 #2C776C（5.30:1）后三主题全绿。深色朱砂实心按钮底改用 semantic.careFill
 * （#A64842），与 care 文字色分离——否则 #D4736A 实底配白字仅 3.25:1。
 *
 * 用法：node scripts/check-contrast.js
 * 退出码：0 = 三主题全部达标；1 = 存在不达标组合（逐条打印主题、组合名、实测值、要求值）。
 */
const fs = require('fs');
const path = require('path');

const tokensPath = path.join(__dirname, '..', '..', 'design', 'tokens.json');
const tokens = JSON.parse(fs.readFileSync(tokensPath, 'utf8'));

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

/** 只取参与对比度计算的十六进制面级令牌，避免把 $comment 等字段并进主题面 */
const DARK_SURFACE_KEYS = ['bg', 'surface', 'sunken', 'tint', 'tintWarm', 'text', 'textSecondary', 'faint'];
function pick(src, keys) {
  const out = {};
  for (const k of keys) out[k] = src[k];
  return out;
}

/**
 * 三套主题的取值上下文：N=中性面级，B=品牌，A=强调，S=语义。
 * white 直接取 neutral/brand/accent/semantic 原值；
 * warm 仅覆盖中性面级（品牌/语义与 white 共用）；
 * dark 同时覆盖中性面级、品牌、强调与语义（见 .zy-theme-dark）。
 */
const themeContexts = {
  white: {
    label: '云白（默认）',
    N: tokens.neutral,
    B: tokens.brand,
    A: tokens.accent,
    S: tokens.semantic,
  },
  warm: {
    label: '暖纸（可选）',
    N: { ...tokens.neutral, ...tokens.themes.warm },
    B: tokens.brand,
    A: tokens.accent,
    S: tokens.semantic,
  },
  dark: {
    label: '玄墨（深色）',
    N: { ...tokens.neutral, ...pick(tokens.dark, DARK_SURFACE_KEYS) },
    B: { ...tokens.brand, 600: tokens.dark.brand600, 700: tokens.dark.brand700 },
    A: { ...tokens.accent, goldText: tokens.dark.goldText },
    S: {
      ...tokens.semantic,
      warningText: tokens.dark.warningText,
      care: tokens.dark.care,
      careFill: tokens.dark.careFill,
    },
  },
};

/** [组合名, 前景取值 fn(C), 背景取值 fn(C), 最低要求] */
const pairDefs = [
  ['title / 页面底', (C) => C.N.text, (C) => C.N.bg, 4.5],
  ['title / 卡片', (C) => C.N.text, (C) => C.N.surface, 4.5],
  ['text 正文 / 页面底', (C) => C.N.textSecondary, (C) => C.N.bg, 4.5],
  ['text 正文 / 卡片', (C) => C.N.textSecondary, (C) => C.N.surface, 4.5],
  ['text 正文 / 薄荷 tint', (C) => C.N.textSecondary, (C) => C.N.tint, 4.5],
  ['text 正文 / 暖金 tint', (C) => C.N.textSecondary, (C) => C.N.tintWarm, 4.5],
  ['text 正文 / 下沉衬底 sunken', (C) => C.N.textSecondary, (C) => C.N.sunken, 4.5],
  ['白字 / brand-600 实心按钮', () => '#FFFFFF', (C) => C.B['600'], 4.5],
  ['brand-700 文字 / 页面底（文字级品牌色）', (C) => C.B['700'], (C) => C.N.bg, 4.5],
  ['brand-700 / 薄荷 tint（secondary 按钮字）', (C) => C.B['700'], (C) => C.N.tint, 4.5],
  ['accent 深金文字 / 暖金 tint', (C) => C.A.goldText, (C) => C.N.tintWarm, 4.5],
  ['warningText 正文级警示 / 页面底', (C) => C.S.warningText, (C) => C.N.bg, 4.5],
  ['care 绛红 / 页面底', (C) => C.S.care, (C) => C.N.bg, 4.5],
  ['白字 / care 实心按钮', () => '#FFFFFF', (C) => C.S.careFill, 4.5],
  ['faint 装饰灰 / 页面底（仅装饰 eyebrow，图形级）', (C) => C.N.faint, (C) => C.N.bg, 3.0],
];

let fail = 0;
let total = 0;
for (const [themeName, C] of Object.entries(themeContexts)) {
  console.log(`── 主题：${C.label} ──`);
  for (const [name, fgFn, bgFn, min] of pairDefs) {
    total++;
    const fg = fgFn(C), bg = bgFn(C);
    if (!fg || !bg) {
      console.error(`[check-contrast][${themeName}] 令牌缺失: ${name}（fg=${fg} bg=${bg}）`);
      fail++;
      continue;
    }
    const r = contrast(fg, bg);
    if (r < min) {
      console.error(`[check-contrast][${themeName}] 不达标: ${name}  实测 ${r.toFixed(2)}:1，要求 ≥${min}:1（fg ${fg} / bg ${bg}）`);
      fail++;
    } else {
      console.log(`[check-contrast][${themeName}] 达标 ${r.toFixed(2)}:1（≥${min}）  ${name}`);
    }
  }
}
if (fail) {
  console.error(`\n[check-contrast] 失败：${fail}/${total} 组不达标。令牌改动不得降低可访问性，请回退或调整用途边界。`);
  process.exit(1);
}
console.log(`\n[check-contrast] 通过：三主题 ${total} 组全部达标`);
