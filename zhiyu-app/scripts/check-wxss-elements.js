/**
 * 构建后闸口（warn 级，不阻断）：扫描 mp-weixin 产物 .wxss，
 * 命中「小程序端不存在的元素选择器」时打印清单，但 exit 0。
 *
 * 背景：`.motion-reduced ... br` 这类规则里的 `br`/`svg`/`g`/`path`/... 是 H5 DOM 元素，
 * 在小程序端根本不存在（uni-app 把 <br/> 编译为 <view/> 占位，svg 系无原生组件，用 image/canvas）。
 * 实测依据：.ai-tmp 验证脚本扫全部 89 个产物 .wxml，这 11 个元素标签 0 命中，
 * 而 view/text/image/button/input/textarea/scroll-view/checkbox 均真实存在。
 * 因此含这些元素选择器的规则是「永远命中不到」的死规则。
 *
 * 与 check-wxss-wildcard.js 的关系：职责独立。`*` 是发布阻断（error/exit 1），
 * 元素选择器是死规则（warn/exit 0）。分开避免在一个脚本里堆规则分级框架。
 * 复用其「stripNoise 降噪 + 逐段提取选择器」的口径，避免「只看每行第一个 {」的漏报坑。
 *
 * 判定口径（误报必须为 0）：
 *   - 只在选择器段（{ 之前）判定，模板/属性值/注释里的词不算；
 *   - 选择器列表（, 分隔）逐段看，段的「首 token」是裸元素名才算命中；
 *   - 元素名后允许跟 .class / ::before 等（如 `svg.icon`），但段首是 `.circle-badge` 这种类名不算；
 *   - calc() 内容、注释已被 stripNoise 降噪，不参与判定。
 *
 * 用法： node scripts/check-wxss-elements.js [目录]   # 默认 dist/build/mp-weixin
 * 退出码：恒 0（warn 级）；命中仅打印清单。
 */
const fs = require('fs');
const path = require('path');

const root = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', 'build', 'mp-weixin'));

// 小程序端不存在的元素（实测产物 .wxml 零命中；依据见文件头注释）
const DEAD_ELEMENTS = ['br', 'svg', 'g', 'path', 'ellipse', 'circle', 'defs', 'clippath', 'lineargradient', 'radialgradient', 'stop'];

if (!fs.existsSync(root)) {
  console.warn(`[check-wxss-elements] 目录不存在: ${root}（请先构建 mp-weixin）`);
  process.exit(0); // warn 级：目录缺失也不阻断
}

const files = [];
(function walk(dir) {
  for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, f.name);
    if (f.isDirectory()) walk(p);
    else if (f.name.endsWith('.wxss')) files.push(p);
  }
})(root);

function stripNoise(line) {
  return line.replace(/\/\*[\s\S]*?\*\//g, '').replace(/calc\([^()]*\)/g, 'calc(_)');
}

// 判断单个选择器段是否含「裸死元素」作关键/结构选择器。
// 命中情形：段首（br{...}）或组合器后（.foo svg、.a > path）出现裸元素名。
// 不命中：类名/ID/属性里的词（.circle-badge、#path、[data-path]），
// 因为这些词前面紧跟 . # [ 等，而非段首或组合器。
function matchDeadElement(part) {
  const t = part.trim();
  if (!t) return null;
  for (const el of DEAD_ELEMENTS) {
    // 元素名前：段首 或 组合器（空白 > + ~）；前面不能是 . # [ 或单词字符（防 .circle/#path/brand）。
    // 元素名后：组合器 / .class / #id / :pseudo / [attr] / { / 段尾；不能是单词字符或 -（防 brand/circle-badge）。
    // 用正则字面量避免字符串转义歧义。
    const re = new RegExp(`(?:^|[\\s>+~])${el}(?=[\\s>+~.:#\\[{]|$)`, 'i');
    // 先粗筛位置，再核验前一个字符不是 . # [ 或单词字符
    let m;
    while ((m = re.exec(t)) !== null) {
      const elStart = m.index + m[0].length - el.length;
      const prev = elStart > 0 ? t[elStart - 1] : '';
      if (prev && /[.#[\w-]/.test(prev)) continue; // 紧跟 . # [ 或字母 => 类名/属性/长词，跳过
      const next = t[elStart + el.length] || '';
      if (/[\w-]/.test(next)) continue; // 后跟字母/连字符 => 长词一部分，跳过
      return el;
    }
  }
  return null;
}

const hits = [];
for (const file of files) {
  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  lines.forEach((line, i) => {
    const clean = stripNoise(line);
    // 压缩产物一行多规则：逐段提取「选择器段{」，不只看行首第一个 {
    const re = /([^{}@]+)\{/g;
    let m;
    while ((m = re.exec(clean)) !== null) {
      for (const part of m[1].split(',')) {
        const el = matchDeadElement(part);
        if (el) {
          hits.push({ file: path.relative(root, file), line: i + 1, el, sel: part.trim().slice(0, 80) });
        }
      }
    }
  });
}

if (hits.length) {
  console.warn(`[check-wxss-elements] 警告：发现 ${hits.length} 处「小程序端不存在的元素选择器」（死规则，不阻断构建）:`);
  for (const h of hits) console.warn(`  ${h.file}:${h.line}  [${h.el}]  ${h.sel}`);
  console.warn('说明：这些元素（br/svg/g/path/...）在小程序端不存在，对应规则永远命中不到，属死代码。');
} else {
  console.log(`[check-wxss-elements] 通过：${files.length} 个 .wxss 文件无死元素选择器`);
}
process.exit(0); // warn 级：恒 0
