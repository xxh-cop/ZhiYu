# 非遗气息深化·阶段一 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让纹样馆 / 纹样详情 / 配色台三页脱离第二代 growth.css，接暖纸青玉组件层，并把静态纹样升级为可逐层绘出的活体渲染与工序演示。

**Architecture:** 复用既有确定性几何 `utils/heritage-pattern.js`（buildHeritageGeometry）与 canvas 绘制 `utils/heritage-studio.js`（drawHeritagePattern），新增一个跨端 PatternLive 组件（canvas 逐层 reveal，失败回落 manifest PNG）；新增四个纯展示组件（名录牌/地域印/工序条/非遗卡）只引用既有 `--zy-*` 令牌；三页换承载样式与组件，数据流与接口不变。

**Tech Stack:** UniApp Vue3（H5 + 微信小程序）、SCSS/CSS 变量、wot-design-uni、既有 check:* 门禁脚本。

**Spec:** docs/superpowers/specs/2026-11-10-heritage-atmosphere-student-design.md

## Global Constraints

- 不新增色值；只引用 `--zy-*`（theme.scss v6）。gold `#D9A441` 仅图形；错误态 care 绛；禁纯红纯黑。
- WXSS 合规：类选择器；无 `*`、无属性选择器、无 `:nth-child`、无 `:focus-visible`；状态用 `is-*`。
- 动效受 `useInteraction().reducedMotion` 约束；reduced 时一次性静态绘制。
- 不编造寓意：工序/观察文案只来自 catalog 的 `process / learningFocus / designNote`。
- 免责声明「本系统不能替代专业心理咨询」每页保留；原创教学标识保留。
- MP 端 canvas 为降级敏感点：列表缩略在 MP 用 manifest PNG（H5 用活体），详情活体失败回落 PNG。
- 提交粒度：阶段一代码 + 规格文档一起一个语义提交（用户已确认）。

---

### Task 1: PatternLive 活体纹样组件

**Files:**
- Create: `zhiyu-app/src/components/PatternLive.vue`

**Interfaces:**
- Consumes: `buildHeritageGeometry(plan)` → `layers[{points,closed,fill,stroke,width}]`；`drawHeritagePattern(ctx,layers,size,palette)`；`patternImage(id)`；`useInteraction().reducedMotion`。
- Produces: `<PatternLive :pattern :palette :canvas-id :animate :reveal-ratio :label />`；props pattern(Object)/palette(Array4)/canvasId(String,required)/animate(Boolean)/revealRatio(Number 0..1,default 1)/label(String)。

- [ ] **Step 1: 写组件**（canvas + 逐层 reveal + PNG 回落），代码见实现文件。
- [ ] **Step 2: H5 构建验证组件可编译**：`npm run build:h5`。
- [ ] **Step 3: 提交点并入 Task 6 统一提交。**

### Task 2: 名录牌 / 地域印 / 工序条

**Files:**
- Create: `zhiyu-app/src/components/HeritageRegistryPlate.vue`
- Create: `zhiyu-app/src/components/RegionSeal.vue`
- Create: `zhiyu-app/src/components/ProcessStrip.vue`

**Interfaces:**
- HeritageRegistryPlate: props tradition(Object)；无 registryCode 且无 listedYear 时不渲染。
- RegionSeal: props region(String)/size('sm'|'md')；region 空不渲染；gold 仅描边。
- ProcessStrip: props steps(Array<String>)/current(Number,-1)/family(String)；role=list，当前步 aria-current="step"。

- [ ] **Step 1: 写三个组件**（纯 CSS/SVG 材质 motif，令牌驱动）。
- [ ] **Step 2: `npm run check:wxss && npm run check:isclass && npm run check:elements`。**

### Task 3: HeritageCard 非遗卡

**Files:**
- Create: `zhiyu-app/src/components/HeritageCard.vue`

**Interfaces:**
- Consumes PatternLive / RegionSeal / AccessibleButton；props pattern(Object)；emit `open(id)`。
- H5 cover 用 PatternLive(animate=false)；MP cover 用 `patternImage` PNG（条件编译）。

- [ ] **Step 1: 写组件**（zy-card + 缝线底边 + 角饰印 + 原创教学标签）。
- [ ] **Step 2: check:* 三脚本。**

### Task 4: gallery 重穿

**Files:**
- Modify: `zhiyu-app/src/sub-heritage/pages/heritage/gallery.vue`（移除 `<style src growth.css>`，换 zy-toolkit + scoped 令牌样式；hero→SectionHead；网格→HeritageCard；筛选/搜索/状态行为不变）。

- [ ] **Step 1: 替换模板与样式**（保留 load/filter/openPattern 逻辑）。
- [ ] **Step 2: `npm run build:h5` + check:* 。**

### Task 5: detail 重穿 + 工序演示

**Files:**
- Modify: `zhiyu-app/src/sub-heritage/pages/heritage/detail.vue`（移除 growth.css；art→PatternLive animate；新增 HeritageRegistryPlate / ProcessStrip / 「看工序」播放器（revealRatio 0.34/0.67/1 同步 current）；designNote 改引言语言；其余卡片/来源/按钮保留）。

- [ ] **Step 1: 替换模板与样式 + 播放器状态**（processTimer 清理 onBeforeUnmount）。
- [ ] **Step 2: build:h5 + check:* 。**

### Task 6: studio 重穿 + 一致性脚本 + 验收 + 提交

**Files:**
- Modify: `zhiyu-app/src/sub-heritage/pages/heritage/studio.vue`（移除 growth.css，scoped 内以令牌重定义所用 growth-* 类；逻辑不动）。
- Create: `zhiyu-app/scripts/check-heritage-svg.js`（node  runnable：读 catalog + manifest，断言 24 id 闭合、buildHeritageGeometry 非空、points∈[0,1]、layer≤MAX_POINTS、palette 长度 4）。
- Modify: `zhiyu-app/package.json`（加 `check:heritage` script）。

- [ ] **Step 1: studio 换承载样式。**
- [ ] **Step 2: 写一致性脚本并跑 `npm run check:heritage`。**
- [ ] **Step 3: `npm run build:h5 && npm run build:mp-weixin` + 四个 check:* + check:heritage。**
- [ ] **Step 4: 浏览器双主题截图（gallery/detail/studio）存 output/playwright/；reduced-motion 截图。**
- [ ] **Step 5: 给用户看 git status/diff stat 后，规格文档 + 阶段一代码一起提交 `feat(app): 非遗气息深化·阶段一`。**

## Self-Review

- 规格 §4 覆盖：gallery(Task4)/detail(Task5)/studio(Task6)/活体+工序(Task1,5)/名录牌+地域印+工序条(Task2)/降级(Task1,3)/状态(保留 StateBlock 语义于各页)/验收(Task6)。§3 的 HeritageCard 在 Task3（阶段二复用）。§5/§6 属阶段二三，不在本计划。
- 类型一致：PatternLive props 与 HeritageCard/detail 调用一致；drawHeritagePattern 签名 (ctx,layers,size,palette) 与既有一致。
- 无占位符。
