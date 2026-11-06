/**
 * 构建后闸口：扫描 mp-weixin 产物 .wxss，命中 `*` 通配符选择器即失败。
 *
 * 背景：WXSS 不支持 `*` 通配选择器（微信官方文档仅支持 .class/#id/元素/后代/子代/并集/::before/::after 等），
 * 含 `*` 的规则会让微信开发者工具整包编译失败（错误码 10041，页面显示「编译 .wxss 文件错误」），
 * 属发布阻断级。2026-11-06 曾因此排查一轮（3 处 `.motion-reduced *` / `.growth-page *`）。
 *
 * 用法：
 *   node scripts/check-wxss-wildcard.js           # 扫默认产物目录 dist/build/mp-weixin
 *   node scripts/check-wxss-wildcard.js <目录>     # 扫指定目录
 * 退出码：0 = 通过；1 = 命中违规（逐条打印 文件:行号:列号 + 选择器片段）。
 *
 * 判定口径（宁稳勿漏、避免误报）：
 *   - 去掉 /* ... *​/ 注释与 calc(...) 内容后，在选择器段（{ 之前）出现独立 `*` 才算命中；
 *   - calc() 内的乘法（如 calc(var(--i) * 60ms)）不算；
 *   - 选择器列表（, 分隔）中任何一段含 `*` 都算。
 */
const fs = require('fs');
const path = require('path');

const root = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', 'build', 'mp-weixin'));

if (!fs.existsSync(root)) {
  console.error(`[check-wxss-wildcard] 目录不存在: ${root}（请先构建 mp-weixin）`);
  process.exit(1);
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

const hits = [];
for (const file of files) {
  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  lines.forEach((line, i) => {
    const clean = stripNoise(line);
    // 压缩产物一行多规则：逐个提取「选择器段{」中的选择器段，而不是只看该行第一个 { 之前
    const re = /([^{}]+)\{/g;
    let m;
    while ((m = re.exec(clean)) !== null) {
      for (const part of m[1].split(',')) {
        if (/(^|[\s>+~])\*(?![\w-])/.test(part) || /^\s*\*\s*$/.test(part)) {
          hits.push(`${path.relative(root, file)}:${i + 1}  ${part.trim().slice(0, 80)}`);
          break;
        }
      }
    }
  });
}

if (hits.length) {
  console.error(`[check-wxss-wildcard] 发现 ${hits.length} 处 * 通配符选择器（WXSS 不支持，发布阻断）:`);
  hits.forEach((h) => console.error('  ' + h));
  process.exit(1);
}
console.log(`[check-wxss-wildcard] 通过：${files.length} 个 .wxss 文件无 * 通配符选择器`);
