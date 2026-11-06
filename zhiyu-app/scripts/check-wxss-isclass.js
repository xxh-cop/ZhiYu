/**
 * 构建前闸口：检查源码中「is-* 前缀状态类」的样式与模板注入点一致性。
 *
 * 背景：WXSS 不支持 [disabled] 等属性选择器，批次一统一改为 class 挂钩（is-disabled 等）。
 * 风险是「样式里有 .is-x，但模板没有任何节点注入这个类」→ 规则永远命中不到，视觉静默失效。
 *
 * 判定口径（按「样式选择器 → 承载节点」对齐，而非整文件粗判）：
 * 对样式里的每条 `X.is-x`（X 为前置类，可空）：
 *   通过条件（满足其一）：
 *     a) 模板存在某节点同时带 X 与 is-x 的注入（:class 对象/数组/静态 class 字面量含 is-x，
 *        且该节点静态 class 含 X）——本地注入；
 *     b) X 为空（裸 .is-x，如基座自身）且模板有 is-x 字面量注入——基座自注入；
 *     c) X 出现在某个「会注入 is-x 的共享组件」标签的 class 上（如 <AccessibleButton class="X">），
 *        该类由该组件根节点透传注入——透传注入。
 *   否则报错。
 *
 * 只查 KNOWN_IS_CLASSES 清单内的 is-* 类，不泛化到所有 class，避免 scoped 传递/动态 class 误报。
 * 纯样式文件（无模板）在 SKIP_STYLE_FILES 登记后跳过。
 *
 * 用法： node scripts/check-wxss-isclass.js [src目录]
 * 退出码：0 = 通过；1 = 存在样式有类但无承载节点的文件。
 */
const fs = require('fs');
const path = require('path');

const srcRoot = path.resolve(process.argv[2] || path.join(__dirname, '..', 'src'));

const KNOWN_IS_CLASSES = ['is-disabled', 'is-checked', 'is-off', 'is-pressed', 'is-focused'];

// 会注入 is-* 类的共享组件（其根节点自动带这些类）。
const INJECTOR_COMPONENTS = {
  AccessibleButton: ['is-disabled'],
};

// 纯样式文件（无对应 .vue 模板）无法判定承载节点，登记后跳过。
const SKIP_STYLE_FILES = ['styles/growth.css', 'pages/work/work-detail.css'];

// 从样式段提取「前置类 + is类」对。捕获 X.is-x，X 为紧邻的类名（可空）。
function extractPairs(styleText) {
  const pairs = [];
  // 匹配选择器段（{ 之前）里的 X.is-x；X 为 .xxx 形式，允许前置为空（裸 .is-x）
  const selRe = /([^{}@]+)\{/g;
  let m;
  while ((m = selRe.exec(styleText)) !== null) {
    const sel = m[1];
    for (const part of sel.split(',')) {
      // 找出该选择器里每个 is 类，取其「紧邻前置 token」判断是自定义类还是元素。
      const isRe = /\.(is-[a-z][\w-]*)/g;
      let im;
      while ((im = isRe.exec(part)) !== null) {
        const isCls = im[1];
        if (!KNOWN_IS_CLASSES.includes(isCls)) continue;
        // is 类之前的紧邻 token（可能贴着类名 .x、元素 button，或空白/开头）
        const before = part.slice(0, im.index);
        // 紧邻前置：取 before 末尾的「.类名」或「元素名」，忽略空白与组合器
        const adjMatch = before.match(/(\.[\w-]+|[a-zA-Z][\w-]*)\s*$/);
        let preCls = '';
        let preIsElement = false;
        if (adjMatch) {
          const tok = adjMatch[1];
          if (tok.startsWith('.')) preCls = tok.slice(1);
          else preIsElement = true; // button / input / view 等元素选择器
        }
        pairs.push({ preCls, preIsElement, isCls });
      }
    }
  }
  return pairs;
}

function escapeRe(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

// 模板里某节点是否「同时承载 preCls（若非空）与 isCls 注入」。
// 实现：找出所有含 preCls 的 class 承载位置（静态 class 属性），再看该节点是否注入 isCls。
// 为降低复杂度，采用两阶段：
//   1) 若模板有任何 isCls 字面量注入点（:class 对象键/数组项/静态 class），
//      且（preCls 为空 或 模板任意 class 属性含 preCls），视为本地注入通过。
//      —— 这允许「同一节点 :class 对象里同时写 X 与 is-x」以及「X 静态 + is-x 动态」。
//   2) 否则若 preCls 出现在某个注入组件的 class 上，视为透传注入通过。
function templateHasLocalInjection(template, preCls, isCls) {
  const t = template.replace(/\s+/g, ' ');
  const escIs = escapeRe(isCls);
  // isCls 字面量注入点三种写法
  const hasIs =
    new RegExp(`['"]${escIs}['"]\\s*:`).test(t) || // 对象键 'is-x':
    new RegExp(`:class\\s*=\\s*"[^"]*['"]${escIs}['"]`).test(t) || // :class="[...'is-x'...]"
    new RegExp(`:class\\s*=\\s*'[^']*['"]${escIs}['"]`).test(t) ||
    new RegExp(`\\bclass\\s*=\\s*"[^"]*\\b${escIs}\\b`).test(t) || // 静态 class="..is-x.."
    new RegExp(`\\bclass\\s*=\\s*'[^']*\\b${escIs}\\b`).test(t);
  if (!hasIs) return false;
  if (!preCls) return true; // 裸 .is-x：有注入即可（基座自身情形）
  // preCls 需在模板某 class 属性出现（静态或绑定字面量）
  const escPre = escapeRe(preCls);
  return (
    new RegExp(`\\bclass\\s*=\\s*"[^"]*\\b${escPre}\\b`).test(t) ||
    new RegExp(`\\bclass\\s*=\\s*'[^']*\\b${escPre}\\b`).test(t) ||
    new RegExp(`['"]${escPre}['"]`).test(t) // 绑定对象/数组里的 'X'
  );
}

// 模板是否使用了会注入 isCls 的共享组件（如 AccessibleButton）。
// preCls 非空时，要求该组件标签的 class 上带 preCls（选择器才对得上）；
// preCls 为空（is 类直接挂元素选择器，如 button.is-x）时，组件出现即视为透传。
function templateHasTransitiveInjection(template, preCls, isCls) {
  for (const [comp, classes] of Object.entries(INJECTOR_COMPONENTS)) {
    if (!classes.includes(isCls)) continue;
    const kebab = comp.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
    if (!preCls) {
      // is 类挂元素：组件出现即透传（基座根是 button，命中 button.is-x）
      if (new RegExp(`<\\s*(?:${comp}|${kebab})\\b`).test(template)) return true;
      continue;
    }
    const escPre = escapeRe(preCls);
    const tagRe = new RegExp(`<\\s*(?:${comp}|${kebab})\\b[^>]*?\\bclass\\s*=\\s*"[^"]*\\b${escPre}\\b`);
    const tagRe2 = new RegExp(`<\\s*(?:${comp}|${kebab})\\b[^>]*?\\bclass\\s*=\\s*'[^']*\\b${escPre}\\b`);
    const tagRe3 = new RegExp(`<\\s*(?:${comp}|${kebab})\\b[^>]*?:class\\s*=\\s*"[^"]*['"]${escPre}['"]`);
    if (tagRe.test(template) || tagRe2.test(template) || tagRe3.test(template)) return true;
  }
  return false;
}

const vueFiles = [];
(function walk(d) {
  for (const f of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, f.name);
    if (f.isDirectory()) walk(p);
    else if (f.name.endsWith('.vue')) vueFiles.push(p);
  }
})(srcRoot);

const failures = [];
for (const file of vueFiles) {
  const text = fs.readFileSync(file, 'utf8');
  const styleIdx = text.indexOf('<style');
  if (styleIdx === -1) continue;
  const styleText = text.slice(styleIdx);
  const templateText = text.slice(0, styleIdx);
  const pairs = extractPairs(styleText);
  if (!pairs.length) continue;
  const seen = new Set();
  for (const { preCls, preIsElement, isCls } of pairs) {
    // is 类挂元素选择器（如 button.is-x）时，按「无自定义前置类」处理（走透传判定）
    const effectivePre = preIsElement ? '' : preCls;
    const key = effectivePre + '|' + isCls;
    if (seen.has(key)) continue;
    seen.add(key);
    if (templateHasLocalInjection(templateText, effectivePre, isCls)) continue;
    if (templateHasTransitiveInjection(templateText, effectivePre, isCls)) continue;
    failures.push({ file: path.relative(srcRoot, file), sel: (effectivePre ? '.' + effectivePre : '') + '.' + isCls });
  }
}

if (failures.length) {
  console.error(`[check-wxss-isclass] 发现 ${failures.length} 处 is-* 类样式无承载节点：`);
  for (const f of failures) console.error(`  ${f.file}  ${f.sel}`);
  console.error('说明：样式声明了该选择器，但模板里没有节点同时承载前置类与该 is-* 类的注入（含共享组件透传）。');
  process.exit(1);
}
console.log(`[check-wxss-isclass] 通过：${vueFiles.length} 个 .vue 文件的 is-* 类样式均有承载节点（本地注入或共享组件透传）`);
