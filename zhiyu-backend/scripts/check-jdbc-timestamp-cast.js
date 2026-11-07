/**
 * 源码闸口：生产代码禁止裸 `(Timestamp)` / `(java.sql.Timestamp)` 强转，命中即失败。
 *
 * 背景：`JdbcTemplate.queryForList` 对 DATETIME 列的返回类型随驱动分叉——
 * MySQL Connector/J 8.0.23+ 按 JDBC 4.2 返回 LocalDateTime，H2 返回 Timestamp；
 * 按其中一种强转在另一种驱动下 ClassCastException（H2 全绿、MySQL 运行期才炸）。
 * 同类事故已三次实证（PersonalWorkService / SoftTierPoolService / ModerationShadowService，
 * 见 docs/moderation-soft-tier-e2e-report-20261107.md F1）。
 * 约定：从 Map 行取时间列一律走 com.zhiyu.common.util.JdbcTimes。
 *
 * 用法：
 *   node zhiyu-backend/scripts/check-jdbc-timestamp-cast.js           # 扫默认范围
 *   node zhiyu-backend/scripts/check-jdbc-timestamp-cast.js <目录>     # 扫指定目录
 * 退出码：0 = 通过；1 = 命中违规（逐条打印 文件:行号 + 代码片段）。
 *
 * 范围与豁免（显式，不放水）：
 *   - 扫描范围按任务定义为 zhiyu-backend/src/main/java（生产代码）；
 *     src/test 只在 H2 上运行、不存在真实驱动分叉，不在本闸口范围。
 *   - 唯一豁免方式：命中行同行注释 `// jdbc-cast-ok: <理由>`（理由 ≥6 个非空白字符），
 *     逐行锚定、随代码漂移，不接受整目录/整文件跳过；被豁免行会逐条打印，不放任静默白名单。
 * 判定口径：剥离块注释（含跨行 javadoc）与行注释后匹配 /\([ \t]*(?:java\.sql\.)?Timestamp[ \t]*\)/。
 *   空白只用 [ \t] 而非 \s——与 Java 版（JdbcCastGuardTest/JdbcCastScan）逐字符同源，
 *   防 JS \s（含 Unicode 空白）与 Java \s（仅 ASCII）的字符集漂移（一致性由 JdbcCastGateConsistencyTest 双跑锁定）。
 *   已知边界：字符串字面量里写 "(Timestamp)" 会误报——用豁免注解并写明理由（当前代码无此情形）。
 */
const fs = require('fs');
const path = require('path');

const SRC = path.resolve(process.argv[2] || path.join(__dirname, '..', 'src', 'main', 'java'));
const CAST = /\([ \t]*(?:java\.sql\.)?Timestamp[ \t]*\)/;
const EXEMPT = /jdbc-cast-ok[ \t]*:[ \t]*(\S.{4,})/; // 同行豁免注解 + ≥6 字符理由

if (!fs.existsSync(SRC)) {
  console.error(`[check-jdbc-timestamp-cast] 目录不存在: ${SRC}`);
  process.exit(1);
}

const files = [];
(function walk(dir) {
  for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, f.name);
    if (f.isDirectory()) walk(p);
    else if (f.name.endsWith('.java')) files.push(p);
  }
})(SRC);

// 逐行剥离注释（跨行块注释用状态机）；返回去掉注释后的代码
function stripper() {
  let inBlock = false;
  return (line) => {
    let out = '';
    for (let i = 0; i < line.length; i++) {
      if (inBlock) {
        if (line[i] === '*' && line[i + 1] === '/') { inBlock = false; i++; }
        continue;
      }
      if (line[i] === '/' && line[i + 1] === '*') { inBlock = true; i++; continue; }
      if (line[i] === '/' && line[i + 1] === '/') break;
      out += line[i];
    }
    return out;
  };
}

const hits = [];
const exempted = [];
for (const file of files) {
  const strip = stripper();
  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  lines.forEach((raw, i) => {
    if (!CAST.test(strip(raw))) return;
    const rel = path.relative(SRC, file).split(path.sep).join('/') + ':' + (i + 1);
    const ex = raw.match(EXEMPT);
    if (ex) exempted.push(`${rel}  （豁免理由：${ex[1].trim()}）`);
    else hits.push(`${rel}  ${raw.trim().slice(0, 100)}`);
  });
}

if (exempted.length) {
  console.log(`[check-jdbc-timestamp-cast] 豁免 ${exempted.length} 处（逐行列示，不放任静默）:`);
  exempted.forEach((h) => console.log('  ' + h));
}
if (hits.length) {
  console.error(`[check-jdbc-timestamp-cast] 发现 ${hits.length} 处裸 (Timestamp) 强转（驱动分叉 ClassCastException 三次实证；请改用 com.zhiyu.common.util.JdbcTimes）:`);
  hits.forEach((h) => console.error('  ' + h));
  process.exit(1);
}
console.log(`[check-jdbc-timestamp-cast] 通过：${files.length} 个 .java 文件无裸 (Timestamp) 强转`);
