# 学生端 WXSS 方言兼容治理方案（2026-11-06）

> 背景：小程序端 WXSS 官方仅承诺支持 `.class` / `#id` / 元素 / 后代 / 并集 / `::before` / `::after`
> 等基础选择器，其余写法**多数不报错、只是静默失效**。`*` 通配符属发布阻断，已于 2026-11-06
> 修掉（3 处）并新增闸口 [check-wxss-wildcard.js](file:///d:/develop/Zhi_Yu/zhiyu-app/scripts/check-wxss-wildcard.js)
> （`pnpm check:wxss`，扫产物 `dist/build/mp-weixin/**/*.wxss`）。
> 本文针对剩余方言命中给出治理方案：**只出方案，不含任何业务代码改动**。
> §1 复核清单（含与任务口径差异的诚实声明）；§2 三类划分与判据；§3 统一改法与风险；
> §4 分批排期；§5 验证方式；§6 闸口扩展建议；§7 禁令；§8 附带发现与不做项。

## 1. 复核后的命中清单（2026-11-06 全量重扫 `zhiyu-app/src`）

| # | 方言类别 | 数量（复核口径） | 位置 | 任务口径差异 |
|---|---|---|---|---|
| A1 | `[disabled]` | **14 处 / 14 文件** | [AccessibleButton.vue:36](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/AccessibleButton.vue#L36)、[ActionSheet.vue:187](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/ActionSheet.vue#L187)、[CompanionPanel.vue:363](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/CompanionPanel.vue#L363)、[PracticeSessionBar.vue:107](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/PracticeSessionBar.vue#L107)、[SensoryPractice.vue](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/SensoryPractice.vue)（压缩样式段 1 处）、[StateBlock.vue:118](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/StateBlock.vue#L118)、[StuckGuideBar.vue:96](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/StuckGuideBar.vue#L96)、[craft/session.vue:977](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/craft/session.vue#L977)、[emotion/chat.vue:610](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/emotion/chat.vue#L610)、[journey/index.vue:46](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/journey/index.vue#L46)、[mindfulness/index.vue:150](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/mindfulness/index.vue#L150)、[practice/short.vue:356](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/practice/short.vue#L356)、[work-detail.css:48](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/work/work-detail.css#L48)、[growth.css:3](file:///d:/develop/Zhi_Yu/zhiyu-app/src/styles/growth.css#L3) | 任务记 13 + 单列 SensoryPractice 1，合并口径一致 |
| A2 | `:not([disabled])`（A1 同族） | 2 处 | [growth.css:14](file:///d:/develop/Zhi_Yu/zhiyu-app/src/styles/growth.css#L14)、[growth.css:22](file:///d:/develop/Zhi_Yu/zhiyu-app/src/styles/growth.css#L22) | 任务未单列，随 A1 同批处理 |
| A3 | `[aria-checked="true"]` | 3 处 / 3 文件 | [ListeningSummaryCard.vue:210](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/ListeningSummaryCard.vue#L210)、[settings/memory.vue:358](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/settings/memory.vue#L358)、[heritage/studio.vue:785](file:///d:/develop/Zhi_Yu/zhiyu-app/src/sub-heritage/pages/heritage/studio.vue#L785) | 一致 |
| A4 | `[aria-pressed]` | 2 处 / 2 文件 | [emotion/chat.vue:605](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/emotion/chat.vue#L605)（`='true'`）、[settings/index.vue:809](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/settings/index.vue#L809)（**`="false"` 否定态，改法易错，见 §3.4**） | 一致 |
| B1 | `:focus-visible` | **62 行 / 41 文件**；同行选择器段 56 个，**逗号列表混入普通选择器 0 条** | 全仓组件/页面（pages/work、group、mindfulness 等密度最高） | 一致 |
| B2 | `:hover` | 3 行 / 6 选择器 + 1 条 `@media (hover:hover)` | [growth.css:12](file:///d:/develop/Zhi_Yu/zhiyu-app/src/styles/growth.css#L12)、[:25](file:///d:/develop/Zhi_Yu/zhiyu-app/src/styles/growth.css#L25)、[:26](file:///d:/develop/Zhi_Yu/zhiyu-app/src/styles/growth.css#L26) | 一致 |
| B3 | `:root` | 1 处 | [theme.scss:13](file:///d:/develop/Zhi_Yu/zhiyu-app/src/styles/theme.scss#L13)（`:root, page {`，`page` 并列兜底，实测变量生效） | 任务记行号 3，实际 13 |
| C1 | `:nth-child`（无 `:nth-of-type`） | **9 处 / 1 文件** | [StarterDiagram.vue](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/StarterDiagram.vue)（L4/6/7/8/11/12/13，纹样装饰元素） | 任务记 13，复核为 9；差异推测是把 `:last-child` 等同族算入（见 §8 附带发现 9 处） |
| C2 | `:focus` | 2 处 | [settings/index.vue:671](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/settings/index.vue#L671)、[:691](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/settings/index.vue#L691)（`.form-input:focus` / `.form-textarea:focus`） | 一致 |
| C3 | `:focus-within` | 3 处 / 3 文件 | [AccountField.vue:30](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/AccountField.vue#L30)、[inheritor/list.vue:312](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/inheritor/list.vue#L312)、[campus/index.vue:33](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/campus/index.vue#L33) | 任务未列，复核新发现，与 C2 同族同批 |
| C4 | `var()` 嵌套回退链 | 2 行 / 4 处嵌套 | [StateBlock.vue:99](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/StateBlock.vue#L99)（1 处）、[materials/index.vue:16](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/materials/index.vue#L16)（压缩行内含 3 处） | 行口径一致 |

另：[ActionSheet.vue:70](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/ActionSheet.vue#L70) 的
`querySelectorAll('button:not([disabled]),[tabindex]')` 是 **JS（H5 端 DOM 查询），不是 WXSS**，
不属本治理范围；它是闸口扩展时必须排除的典型误报样本（见 §6）。

## 2. 三类划分与判据

判据全部可执行：先答「小程序端是否存在该交互场景」，再答「失效后果是什么」，按决策表归类。

### 2.1 必须换写（A1+A2、A3、A4、C2、C3、C4，共 26 处 / 21 文件）

判据（须**同时满足**）：
1. 小程序端存在该交互：按钮禁用展示、同意勾选/按压选中、输入框聚焦，均为触摸端真实场景；
2. 失效后**用户无法区分状态**（按钮看似可点实际被 `blocked` 拦截、勾选框看似未选、输入框聚焦无高亮）——属功能性可访问性缺陷，不是纯装饰；
3. 该选择器是状态样式的**唯一视觉挂钩**（模板里无并行 class 承担同一状态）。

逐类套用：A1/A2（禁用置灰，含 journey 日历禁用日期 `opacity:0`——失效后禁用日期可见，误导用户以为可选）；A3/A4（同意/选中高亮）；C2/C3（输入聚焦高亮；小程序 `input`/`textarea` 聚焦是真实事件）；C4（`var()` 嵌套在 WXSS 变量实现上行为未文档化，一旦失效整条声明作废、颜色回落继承值——修复成本极低，直接换写消除不确定，不必实测）。

### 2.2 可判定忽略（B1、B2、B3，共 66 行 / 42 文件，0 代码改动）

判据（满足**任一**即可判定忽略，且判定依据必须是「场景不存在」而非「看起来没坏」）：
1. **小程序端根本无此交互**：
   - B1 `:focus-visible`：微信小程序端无 Tab 键焦点导航、无键盘焦点管理（触摸交互），该伪类永不命中；H5 桌面浏览器有真实键盘用户（无障碍合规），必须保留。已复核 **0 条逗号列表混入**（56 个同行选择器段全部纯 `:focus-visible`），小程序端整条丢弃**不会连带误伤任何其他样式**——这是「可以安全忽略」的关键证据。
   - B2 `:hover`：触摸端无指针悬停；`@media (hover:hover)` 在小程序恒不命中，等于自带条件编译；`.motion-reduced .entry:hover{transform:none}` 这类降级兜底规则在「hover 永不触发」的前提下本就无需生效。
2. **存在并列合法选择器兜底且已实测生效**：
   - B3 `:root`：`:root, page {` 中 `page` 是 WXSS 官方支持的页面根选择器，当前实测变量生效。登记为观察项：**今后改动 theme.scss 必须在小程序端实测变量仍生效**（见 §4 批次三的可选收敛项）。

### 2.3 需要实测确认后决定（C1，9 处 / 1 文件）

判据：失效后果需**人眼实测**才能定级，且两种处置（换写 / 接受失效）成本都低。
- C1 `:nth-child` 9 处全在 StarterDiagram.vue 的纹样示意图（`aria-hidden="true"` 装饰元素）。
  失效形态分两种：`2n` 交替换色丢失（仅色彩单调，图形仍在）与 `n+6`/`n+4` 的
  `display:none` 丢失（leaf/hills/rings 会**多渲染**几片，图形可能走样）。
  **执行判据**：在微信开发者工具里逐个 kind 查看，若 `display:none` 失效导致纹样结构误导
  （weave/braid 交织关系走形）→ 换写（§3.5，显式 class）；若仅交替色丢失、结构正确 →
  可登记接受失效。实测前**默认按换写准备**（成本仅 1 文件，见 §4 批次三）。

## 3. 统一改法与风险

### 3.1 属性选择器（A1–A4）：改为 class 状态绑定

统一原则：**行为属性（`:disabled` / `aria-*`）原样保留**——它们承担交互拦截与无障碍语义；
只把「样式挂钩」从属性选择器换成与之并行的 class（`:class="{ 'is-xxx': 状态 }"`），
两端同时生效、小程序端不再依赖方言。

**示例 1：[AccessibleButton.vue](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/AccessibleButton.vue)（A1，全站共用基座，改它收益最大）**

改前：

```html
<button ref="root" role="button" :tabindex="blocked ? -1 : 0"
  :disabled="blocked" :loading="loading" :aria-disabled="blocked" :aria-busy="loading"
  :aria-label="ariaLabel" :aria-pressed="ariaPressed" @click="activate">
```

```css
button[disabled]{opacity:.55;cursor:not-allowed}
```

改后（模板加 `:class`，样式换挂钩，其余一行不动）：

```html
<button ref="root" role="button" :tabindex="blocked ? -1 : 0" :class="{ 'is-disabled': blocked }"
  :disabled="blocked" :loading="loading" :aria-disabled="blocked" :aria-busy="loading"
  :aria-label="ariaLabel" :aria-pressed="ariaPressed" @click="activate">
```

```css
button.is-disabled{opacity:.55;cursor:not-allowed}
```

可行性依据：该组件已声明 `virtualHost: true, styleIsolation: 'shared'`，父组件传入的 class
与本组件根元素 class 在小程序端均能正确落到节点上（wot-design-uni 同款机制，现网已有先例）。

**示例 2：[ListeningSummaryCard.vue:210](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/ListeningSummaryCard.vue#L210)（A3）**

改前：

```css
.summary-consent[aria-checked="true"] { border-color: var(--zy-primary, #217e76); color: var(--zy-primary, #217e76); }
```

改后（模板 `:aria-checked` 旁加 `:class="{ 'is-checked': consent }"`）：

```css
.summary-consent.is-checked { border-color: var(--zy-primary, #217e76); color: var(--zy-primary, #217e76); }
```

`.summary-consent` 落在 AccessibleButton 根元素上，父组件 scoped 样式经 shared 隔离穿透，class 方案与属性选择器方案的命中路径完全一致。

**代价与风险**：每处 2–3 行改动，14+2+3+2=21 处共约 50 行。风险有二：
（1）同名 class 冲突——统一用 `is-disabled` / `is-checked` / `is-pressed` / `is-off` 前缀词，
且 scoped 样式天然隔离，冲突面可控；
（2）模板状态表达式抄错（如把 `blocked` 写成 `disabled`，loading 态丢置灰）——
**必须逐文件核对 diff + 视觉对照**，禁止批量替换（§7）。

### 3.2 否定态警示（A4 的 `settings/index.vue:809`）

`.nudge-toggle[aria-pressed="false"]` 选中的是**关闭态**。改 class 时必须写成
`:class="{ 'is-off': !enabled }"` 配 `.nudge-toggle.is-off{...}`，
**禁止顺手按 `="true"` 的思维改成选中态**——逻辑颠倒是这类改造最高发的事故，
review 时对每处否定态选择器单独点名确认。

### 3.3 `:focus-visible`（B1）：H5 保留 / 小程序端策略性容忍

判据已述（§2.2）：小程序无键盘场景、0 混入、整条丢弃无误伤。给两个方案：

- **方案 A（推荐，0 改动）**：接受小程序端静默失效，规范登记为「`:focus-visible` 是 H5-only
  渐进增强，新代码照写不误」；闸口将其列入白名单（§6）。依据：用户影响为零，41 文件
  逐改的收益只有「心理确定性」，代价是 62 行无关 diff 与回归风险。
- **方案 B（可选，仅在顺手触碰对应文件时做）**：用 uni-app 条件编译把规则裁出小程序产物：

```css
/* #ifdef H5 */
button:focus-visible{outline:3px solid var(--zy-brand-700,#1a645e);outline-offset:3px}
/* #endif */
```

风险：注释位置写错会把 H5 也裁掉；故**不组织专项批量改造**。

### 3.4 `:nth-child`（C1）：显式 class 替代（最省事且视觉完全一致）

[StarterDiagram.vue](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/StarterDiagram.vue) 模板已是
`v-for="n in 9"` 且注入了 `--i`，最省事的替代是补序号 class：

```html
<view v-for="n in 9" :key="n" class="piece"
  :class="{ 'piece-even': n % 2 === 0, 'piece-ge4': n >= 4, 'piece-ge5': n >= 5, 'piece-ge6': n >= 6, 'piece-le4': n <= 4 }"
  :style="{'--i':n}"></view>
```

选择器映射：`:nth-child(2n)` → `.piece-even`；`:nth-child(n+4)` → `.piece-ge4`；
`:nth-child(n+5)` → `.piece-ge5`；`:nth-child(n+6)` → `.piece-ge6`；`:nth-child(-n+4)` → `.piece-le4`。
`display:none` 类规则也可改用 `v-if` 直接不渲染（更省节点）。
备选：若实测仅色彩单调（§2.3），可登记接受失效，0 改动。

### 3.5 其余小类

- **C2/C3（`:focus` / `:focus-within`）**：输入聚焦态改为 class 挂钩——uni-app 的 `input`/`textarea`
  有 `@focus` / `@blur` 事件，维护 `focused` 状态加 `:class="{ 'is-focused': focused }"`；
  `:focus-within`（容器高亮）同样用事件状态落到容器 class。代价：每处多一个响应式字段 + 两个事件绑定。
- **C4（`var()` 嵌套）**：拍平为单层回退，取内层最终兜底值。例：
  [StateBlock.vue:99](file:///d:/develop/Zhi_Yu/zhiyu-app/src/components/StateBlock.vue#L99)
  `color: var(--zy-text-secondary, var(--zy-text, #4a5f54))` → `color: var(--zy-text-secondary, #4a5f54)`。
  无语义损失（内层回退与最终兜底同值），每处 1 行。
- **B3（`:root`，可选收敛）**：保留 `page` 为小程序端唯一承载，`:root` 用 `/* #ifdef H5 */`
  条件编译补回（变量组用 scss `@mixin` 维护一份，避免双份漂移）。改后必须实测小程序变量仍生效。

## 4. 分批排期（用户可感知程度 × 改动风险）

| 批次 | 范围 | 数量 | 文件数 | 排序理由 | 验收方式 |
|---|---|---|---|---|---|
| **批次一** | A1 `[disabled]` + A2 `:not([disabled])` | 16 处 | **14**（§1 A1 清单 + growth.css） | 禁用置灰是用户判断「能不能点」的**唯一视觉信号**，失效=功能性可访问性缺陷；含全站基座 AccessibleButton，改一处收益全站；journey 日历禁用日期误导风险 | 小程序端逐页触发禁用条件对照（§5.2 清单）；H5 改前/改后截图基线对比 |
| **批次二** | A3 `[aria-checked]` + A4 `[aria-pressed]` + C2 `:focus` + C3 `:focus-within` | 10 处 | **8**（ListeningSummaryCard、studio、settings/memory、settings/index、AccountField、inheritor/list、campus/index、emotion/chat——chat 批次一已碰，可同批顺手改） | 选中/聚焦态可见性，用户可感知但后果次于禁用（勾选框本身有 ✓ 字符兜底）；否定态 `="false"` 需单独 review 点名 | 同上；重点核对「同意勾选后高亮出现」「输入框聚焦边框变色」 |
| **批次三** | C1 `:nth-child` + C4 `var()` 嵌套 + B3 `:root` 可选收敛 | 9 + 4 + 1 处 | **4**（StarterDiagram、StateBlock、materials/index、theme.scss） | 装饰与健壮性，用户可感知低；C1 先实测再定换写/接受（§2.3） | StarterDiagram 逐 kind 视觉对照；theme.scss 改后实测变量生效 |
| **不改代码** | B1 `:focus-visible`（62 行/41 文件）、B2 `:hover`（3 行） | — | 0 | §2.2 判据：小程序无此交互场景；B1 已验证 0 混入无误伤 | 闸口白名单登记（§6）；规范文档化 |

每批次完成后执行 `pnpm build:mp-weixin && pnpm check:wxss`（含 §6 扩展规则）防回潮。

## 5. 验证方式

### 5.1 小程序端（微信开发者工具）

1. **必须使用全新 IDE 实例**：完全关闭微信开发者工具再重开。旧实例的编译缓存与
   「热重载劣化」会让样式表现与真实冷启动不一致，**用旧实例验证会误判**。
2. 菜单「工具 → 清除缓存 → 全部清除」，然后重新编译。**冷编译首次需 90 秒以上**，
   编译完成前不要操作页面，否则看到的是半旧半新的混合态。
3. 按批次逐页视觉对照，禁用/选中/聚焦态需主动构造触发条件：
   - 批次一触发点举例：emotion/chat 输入为空时的语音/发送钮、practice/short 进行中的
     `.primary-action`、mindfulness 未选时长的开始钮、journey 日历的禁用日期、
     craft/session 的 `.more-btn`、work 详情页互动钮（`interactionBusy` 时）。
   - 批次二触发点：ListeningSummaryCard 同意勾选、chat 语音按住态、settings 开关关闭态、
     campus/inheritor 搜索框聚焦。
4. 结果记录：每页「改前基线截图 / 改后截图 / 结论」三件套，存 `output/playwright/`（H5）与
   开发者工具截图目录（小程序），附在批次交付说明里。

### 5.2 H5 端

`pnpm dev:h5` 起本地服务，Chrome DevTools 对照；改前先对涉及页面截基线图（H5 端这些选择器
本来就生效，H5 是回归基线而非验收目标）。重点确认：换 class 后选择器特异性没有变化
（原 `[disabled]` 与 `.is-disabled` 特异性同为 0-1-1 对 0-1-1… 属性选择器与 class 同级，
并列书写顺序不变则无级联漂移）。

### 5.3 构建闸口

每批次收尾：`pnpm build:mp-weixin && pnpm check:wxss` 全绿；扩展后的方言规则
（§6）对当批完成的类别从 warn 升 error。

## 6. 闸口扩展建议

### 6.1 可行性

现有 [check-wxss-wildcard.js](file:///d:/develop/Zhi_Yu/zhiyu-app/scripts/check-wxss-wildcard.js) 已是
「扫产物 `.wxss` → 去注释/calc 降噪 → 逐段提取选择器」的框架，扩展只是加规则表，
建议新脚本 `scripts/check-wxss-dialect.js`（或并入原脚本加 `--rules`），**继续扫产物而非源码**——
产物里天然不含模板 JS（`steps[index]`、`querySelectorAll` 这类误报源进不了 `.wxss`），
且 scss 嵌套已被编译平铺，无需处理预处理器语法。

### 6.2 规则表（分级）

| 规则 | 级别 | 说明 |
|---|---|---|
| `*` 通配选择器 | **error**（现状保留） | 发布阻断 |
| 选择器段含 `[disabled]` / `[aria-*]` / `[data-*]` / `[type=]` | warn → 批次一/二完成后升 **error** | 静默失效 |
| `:nth-child(` / `:nth-of-type(` | warn → 批次三完成后升 **error** | 同上 |
| `:focus`（负向断言排除 `-visible`）/ `:focus-within` | warn → 批次二完成后升 **error** | 同上 |
| `:root` | warn 常驻 | 仅 theme.scss 一处，收敛后可升 error |
| `:hover` | **warn 常驻，永不升 error** | 小程序无场景、H5 必需；含 `@media (hover:hover)` |
| `:focus-visible` | **白名单，不告警** | H5-only 渐进增强（§3.3 方案 A） |

### 6.3 误报风险与对策

1. **`@media (hover:hover){` 误伤**：媒体查询条件也以 `{` 结尾、会被「选择器段」正则捕获——
   提取规则时**跳过 `@` 开头的段**（现脚本未排除，扩展时必须加）。
2. **`:focus` 与 `:focus-visible` 边界**：用 `:focus(?!-visible)` 负向断言，不要裸匹配 `:focus`。
3. **`::before` / `::after` 属官方支持**：黑名单只列单冒号伪类的**精确串**
   （`:hover`、`:focus`、`:focus-within`、`:nth-child(`、`:nth-of-type(`、`:root`），
   禁止泛化成 `:.*` 匹配——否则必然误伤伪元素。
4. **`calc()` 运算符**：现脚本已把 `calc(...)` 内容降噪，新规则不受影响；不要顺手把
   `+`/`-`/`/` 纳入检查（calc 合法且官方支持）。
5. **属性选择器正则限定已知清单**（`disabled` / `aria-` / `data-` / `type=`），不泛化 `\[.*?\]`；
   `keyframes` 的 `from`/`to`/`%`、媒体查询、`@supports` 均不含上述形态，无碰撞。
6. **试运行**：新规则先全部以 warn 级别跑满一个迭代（至少覆盖一次完整发版），
   确认产物层零意外命中后，再按 §6.2 的节点升 error。

## 7. 禁令

- **禁止用全局正则批量替换源码样式**。历史事故：批量替换误伤模板里的 `color` HTML 属性。
  必须按组件逐个改：改前截图基线（H5 + 小程序各一）→ 改 → 逐文件核对 diff → 视觉对照。
- **禁止改动 `:disabled` / `aria-*` 等行为属性本身**——它们承担交互拦截与无障碍语义，
  本次只换样式挂钩。
- **禁止顺手「优化」无关样式**（遵守 AGENTS.md「半成品接续、不整体重写」纪律）；
  每批次 diff 必须只含本方案列出的选择器改造。

## 8. 附带发现与不做项

- **附带发现**：`:first-child` / `:last-child`（含 `:not(:last-child)`）共 **9 处 / 9 文件**
  （BreathingPractice、home、inheritor/list、journey、materials/kits、practice/short、privacy、
  privacy/settings、profile），全是列表分隔线/末项间距。同属未文档化伪类，但失效后果仅为
  视觉细节。处置：批次三时在开发者工具抽查 3 个代表页面，若分隔线确实失效 → 按 §3.1
  同款 class 方案补；若实测生效（可能性存在，结构性伪类在部分基础库版本表现不一）→
  登记忽略。**不扩大本方案范围，仅登记**。
- **不做项**：不改任何业务代码（本文档为唯一产出）；不改 `openapi.yaml`、后端与 SQL；
  不组织 `:focus-visible` / `:hover` 的批量条件编译（§3.3 方案 A）；
  不对 `:first-child/:last-child` 做超出「批次三抽查」的动作。
