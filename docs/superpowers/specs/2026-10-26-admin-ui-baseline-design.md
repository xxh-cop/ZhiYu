# 织愈管理端 UI 基线设计

- 日期：2026-10-26
- 状态：待评审
- 范围：`zhiyu-admin-vben/apps/web-ele`（Vue Vben Admin 5.8.0 + Element Plus）
- 上游决策：管理端保留靛蓝品牌（双品牌，不与学生端薄荷青统一）；品牌标志已重绘并转曲，见 `design/logo/`
- 非范围：学生端、后端接口、权限模型、路由结构、组件库替换

## 0. 版本事实

| 项 | 值 | 出处 |
|---|---|---|
| 包版本 | `5.8.0`（根 package.json 标 5.7.0） | `zhiyu-admin-vben/package.json:4`、`apps/web-ele/package.json:3` |
| 偏好默认值 | `defaultPreferences` | `packages/@core/preferences/src/config.ts:3-172` |
| 偏好类型 | `ThemePreferences` / `AppPreferences` | `packages/@core/preferences/src/types.ts:328-351`、`:98-171` |
| EP 变量派生 | `useElementPlusDesignTokens` | `packages/effects/hooks/src/use-design-tokens.ts:163-321`，挂载于 `apps/web-ele/src/app.vue:10` |
| 偏好抽屉开关 | `app.enablePreferences` 默认 `true` | `packages/@core/preferences/src/config.ts:24` |

## 1. 问题（逐条核实）

| # | 现象 | 证据 | 影响 |
|---|---|---|---|
| P-1 | 三套品牌色并存，抽屉换色无效 | `variables.scss:10` 定义 `--zy-admin-primary:#4F46E5`；`business.scss:5` 在 `.zy-business-page` 上把它赋给 `--el-color-primary`（元素级声明优先于继承的 `:root`）；`business.scss:112` 用 `html:not(.dark)`（特异性 0-1-1 > `:root` 0-1-0）赋 `#2f7566` | 业务页靛蓝、侧栏/顶栏/登录薄荷青；Vben 运行时注入的 `:root` 值被两处覆盖夹杀 |
| P-2 | 手写 EP 色阶与运行时重复 | `variables.scss:67-77` 手写 `--el-color-primary-*`；运行时 `<style id="__vben_design_styles__">` 后注入并胜出 | 死代码，误导维护者以为改这里有效 |
| P-3 | 请求失败伪装成空态 | `:empty-text="errorMsg \|\| '…'"` 共 8 处：`audit.vue:178`、`audit-logs.vue:175`、`care-reminders.vue:132`、`groups.vue:168`、`inheritors.vue:328`、`my-projects.vue:156`、`users.vue:393`、`works.vue:221` | 运营无法区分"没有数据"和"加载失败"，且无重试入口 |
| P-4 | 确认交互两套 | `programs.vue:27` 用 `window.confirm`，同文件 `:33`、`:35` 用 `ElMessageBox` | 无焦点管理、不可样式化、可被浏览器拦截 |
| P-5 | 分页不可调且不一致 | `const pageSize = 10` 为模块级常量（8 个列表页）；`campus.vue:27` 硬编码 `:page-size="20"`；`layout="total,prev,pager,next"` 无 `sizes` | 密度无法调整，跨页行为不一致 |
| P-6 | 抽屉宽度不一致 | `audit-logs.vue:220` `size="50%"`；`care-reminders.vue:180` `size="min(560px, 100vw)"` | 窄屏下 50% 抽屉不可用 |
| P-7 | 图表完全脱离主题 | `OperationsChart.vue:11` 第 5 套绿色 `#287d6d…`；`emotion-analysis.vue:16-26` 独立靛蓝 JS 常量；`use-echarts.ts:77-85` 只切 dark/light，从不读 `colorPrimary` | 换主题后图表不变色；空数据仍占 268px 画布；仅靠颜色区分系列 |
| P-8 | 壳层残留模板内容 | `layouts/basic.vue:27-60` 5 条演示通知（vercel 头像、"收到了 14 份新周报"）；`:8,:10` 引入 `VBEN_DOC_URL`、`VBEN_GITHUB_URL`、`SvgGithubIcon` | 交付物看起来像 demo，且外链指向 Vben 官方 |
| P-9 | 登录页第四套绿色 | `views/_core/authentication/login.vue:33` 18 处硬编码（`#2d624b`、`#588a6c`、`#dce3d9`…） | 与管理端品牌无关，且不随深色模式变化 |
| P-10 | 硬编码色债总量 | `apps/web-ele/src` 共 182 处 hex / 15 文件：`views/zhiyu` 71、`variables.scss` 41、`views/_core` 22、`business.scss` 15、`layouts/auth.vue` 14 | 任何主题调整都无法保证全局一致 |
| P-11 | 辅助色对比度不达标 | `variables.scss:17` `--zy-admin-muted:#9CA3AF`（白底约 2.54:1）、`:20` danger `#EF4444`（约 3.76:1）、`:21` success `#10B981`（约 2.54:1）、`:19` accent `#F59E0B`（约 2.15:1） | 正文/状态文字低于 WCAG AA 4.5:1 |
| P-12 | 零处使用 Vben 原生 token | `apps/web-ele/src` 内 `var(--primary)` / `hsl(var(--…))` / `bg-primary` 均无匹配 | 主题引擎的能力完全没用上 |

**正确范例（照此改）**：`comments.vue:15` 用 `--el-color-primary` + `--el-color-primary-light-9` + `--el-border-color`；`BusinessPageHeader.vue:16` 用 `--el-text-color-primary/secondary`；`GuidanceMessages.vue:14` 用主色描边表达选中态。

## 2. 目标与非目标

**目标**

1. 主题只有一个引擎：Vben preferences。改一个值，全站（含 Element Plus 与深色模式）一致响应。
2. "偏好设置可调"从**未验证的假设**变成**被测试过的能力**：所有保留预设下试点页面不破版、对比度达标。
3. 加载/空/错误/离线四态分离，错误可重试且不丢已加载数据。
4. 表格、筛选、分页、抽屉、确认形成可复制契约。
5. 硬编码 hex 从 182 降到 ≤ 20，且只允许存在于令牌层与图表桥。

**非目标**

- 不改后端接口、OpenAPI 契约、角色模型与路由结构。
- 不替换组件库、不引入新 UI 框架、不搭 Figma 设计交付流程。
- 不在本轮做像素级视觉稿还原；本轮要的是**一致性与可维护性**。
- 不改学生端（另立 spec）。

## 3. 设计决策

### D1 · Vben preferences 是唯一主题引擎

品牌色入口只有一个：`apps/web-ele/src/preferences.ts` 的 `theme`。

```ts
theme: {
  mode: 'light',
  builtinType: 'custom',          // 否则抽屉里高亮的是 default 色块，与实际色值不符
  colorPrimary: '#4F46E5',        // 管理端靛蓝
  colorSuccess: '#047857',        // 替代 #10B981，白底约 5.5:1
  colorWarning: '#B45309',        // 替代 #F59E0B，白底约 5.0:1
  colorDestructive: '#DC2626',    // 替代 #EF4444，白底约 4.8:1
  radius: '0.5',                  // 8px，Vben 单值旋钮
}
```

理由：`update-css-variables.ts:88-119` 直接读 `theme.colorPrimary` 并经 `generatorColorVariables` 产出 `--primary-50…900`，再由 `use-design-tokens.ts:239-257` 派生整套 `--el-color-primary-*` 并在深色模式下反转。手写色阶是在复制引擎已经做的事。

**修订记录**：上一轮讨论曾提议由 token 生成器为管理端输出 Element Plus 变量——作废，会与运行时双写打架。

### D2 · 圆角与字号收敛到 Vben 单旋钮

- 圆角统一 `radius: '0.5'`（8px）。放弃此前"卡片 12px / 控件 8px"双档——Vben 只有一个全局 `--radius`，强行分档需要再写覆盖层，与 D1 冲突。
- 字号不覆盖，保留 Vben 默认 `theme.fontSize: 16`（可调范围 15–22）。放弃旧规范里"管理端正文 14px"。密度靠行高、内边距和列宽控制，不靠缩小字号。
- 验收：试点页在 `fontSize` 15 与 16 两档下均无横向溢出。

### D3 · 删除覆盖层，而不是增加优先级

必须删除（不是提高优先级去赢）：

- `business.scss:5` 的 `--el-color-primary: var(--zy-admin-primary)`
- `business.scss:107`、`:109`、`:112` 整段品牌色/文字色/危险色/圆角覆盖
- `variables.scss:67-77` 手写 EP 色阶、`:77` `--el-border-radius-base`
- `variables.scss:8-23` 中已被 preferences 覆盖的颜色令牌

`variables.scss` 收缩为**只保留 Vben 不提供的项目令牌**：图表辅助色、`--zy-admin-primary-bg` 这类局部装饰底纹。`business.scss` 只保留布局、焦点环与 `prefers-reduced-motion`。

### D4 · 保留偏好设置，但把它当功能测起来

`enablePreferences` 保持 `true`（内部运营工具允许换主题是企业软件常态）。本轮**不 fork** `packages/@core/preferences/src/constants.ts` 去裁剪 16 个预设；改为：

- 默认落在 `custom` + 靛蓝。
- 新增一项回归：任选 `default / violet / deep-blue / green / gray` 五个预设，三个试点页在 1280 / 768 / 375 下不破版、文字对比度 ≥ 4.5:1、图表跟随变色。
- 若某预设不达标，修组件而不是砍预设。

后续如需白名单，改 `constants.ts` 的 `BUILT_IN_THEME_PRESETS`（`packages/@core/preferences/src/constants.ts:10-79`）即可，属独立小改动。

### D5 · 缓存失效是发布步骤，不是可选项

`preferences.ts:144-148` 合并顺序是**缓存优先**，初始值只补齐缺失字段；schema 无 `version` 字段。因此改默认色后老用户看不到变化。

对策：localStorage key 前缀含 `VITE_APP_VERSION`（`main.ts:12-14` → `vben-web-ele-<version>-<env>-preferences`）。**改品牌默认值时必须 bump `apps/web-ele/package.json` 的 version**，旧缓存自然失效。禁止用"让用户手动点重置"作为交付口径。

### D6 · 四态契约

新增 `DataState` 组件，契约：

| 态 | 触发 | 呈现 | 必带 |
|---|---|---|---|
| loading | 请求中 | 表格 `v-loading` / 骨架 | 保留表头与列宽，不跳动 |
| empty | 请求成功且 total=0 | `ElEmpty` + 说明 + 下一步动作 | 引导文案，不留白屏 |
| error | 请求失败 | 错误态 + **重试按钮** | 保留已加载数据；错误文案不含技术栈与敏感字段 |
| offline | 网络不可达 | 全局提示条 | 恢复后可重试 |

硬性规则：`empty-text` 只允许表达"确实没有数据"。`:empty-text="errorMsg || …"` 模式在 `src/views` 下必须为 0 处。

### D7 · 列表页契约

- 筛选：统一 `FilterBar`，字段顺序 = 使用频率；窄屏折叠为"筛选"抽屉；重置按钮存在且真的重置分页与排序。
- 分页：`pageSize` 为 `ref`，默认 20，`page-sizes: [10,20,50,100]`，`layout="total,sizes,prev,pager,next,jumper"`。
- 操作列：主操作 ≤ 2 个直出，其余收进 `更多` dropdown；禁止一行 5 个按钮。
- 表格：`min-width` 而非固定 `width`；375px 下允许横向滚动，但主键列（标题 / 状态 / 操作）必须首屏可见；表头可排序时提供 `aria-sort`。
- 破坏性操作：一律 `ElMessageBox.confirm`，标题动词化（"下架作品"而非"提示"），说明后果，`window.confirm` 在 `src/views` 下必须为 0 处。
- 抽屉：统一 `size="min(<内容宽度>, 100vw)"`；打开即拉数据并显示 loading；关闭清详情态。

### D8 · 图表桥

新增 `useChartTheme()`：从 `getComputedStyle(document.documentElement)` 读 `--primary-500`、`--success`、`--warning`、`--destructive`、`--muted-foreground`、`--border`，产出 `colors / text / grid / border / background`；`watch(() => preferences.theme, …)` 触发重算与 `setOption`。

规则：

- 空数据不渲染画布，走 D6 的 empty 态。
- 折线用 `lineStyle.type` 虚线/点线区分系列；饼图用外部标签直连，不允许"仅靠颜色区分"。
- 图表容器固定最小高度由内容决定，错误态替换画布而非叠加。
- `OperationsChart.vue:11` 与 `emotion-analysis.vue:16-26` 的本地色板删除。

### D9 · 自研组件基线（最小集）

只建试点页真正复用的部分，不预先造库：

| 组件 | 职责 | 替换掉 |
|---|---|---|
| `BusinessPageShell` | 页头 + 筛选区 + 内容区 + 操作区骨架 | 各页重复的 `.zy-business-page` 内联结构 |
| `FilterBar` | 统一筛选/重置/折叠 | audit、works、users 三套重复筛选栏 |
| `DataState` | D6 四态 | 8 处 `:empty-text="errorMsg…"` |
| `ChartCard` | 标题 + 说明 + 四态 + 主题注入 | 各图表外层重复容器 |
| `KpiCard` | 标签 + 数值 + 同比 + 加载/错误 | dashboard 内联卡片 |
| `StatusTag` | 状态→色板与文案映射 | 各页 `:type` 三元表达式 |
| `ConfirmAction` | 二次确认 + busy + 结果反馈 | `window.confirm` 与散落的 busy 逻辑 |

全部使用 Element Plus 变量取色，禁止新硬编码色值。

### D10 · 无障碍与响应式基线

- 文字对比度 ≥ 4.5:1，大字号/图形 ≥ 3:1；状态不只靠颜色（配文字或图标）。
- 所有可交互元素键盘可达，焦点环统一 `outline: 2px solid var(--el-color-primary); outline-offset: 3px`（`business.scss` 已有，保留）。
- 图标按钮必须有 `aria-label`；表格提供可理解的列标题，不用空列头。
- 异步结果（保存/审核/领取）通过 `role="status"` 的 `aria-live` 播报，不只弹 Toast。
- 触控目标 ≥ 36px（主操作 ≥ 40px）。
- 尊重 `prefers-reduced-motion`（`business.scss:98-104`、`:114` 已有，保留）。
- 断点：1280 / 768 / 375 三档均无横向溢出。

### D11 · 合规与文案红线

- 关怀提醒：摘要仅管理员与已领取负责人可见（服务端已实现），前端不得回显 `userId`/`sessionId`；未领取态文案固定为"领取后可见脱敏摘要"。
- 情绪分析、关怀相关页面保留"本系统不能替代专业心理咨询"声明，且对比度 ≥ 4.5:1、字号 ≥ 12px。
- 禁止诊断性措辞出现在状态标签、错误文案、空态引导中。
- 导出/日志类操作不得把真实对话、token 或个人资料渲染到界面。

### D12 · 壳层去模板化

- `layouts/basic.vue:27-60` 演示通知**本轮直接隐藏入口**（后端无对应待办接口），删除假数据；接真实来源另立需求，不在本 spec 验收内。
- `:8,:10` 的 `VBEN_DOC_URL`、`VBEN_GITHUB_URL`、`SvgGithubIcon` 头部入口移除或指向项目自有文档。
- 403 / 404 使用品牌化兜底：说明"为什么进不来"和"下一步找谁"，不保留通用文案。
- 登录页 `login.vue:33` 全部改为主题变量，保留现有 `label`、`aria-invalid`、`role="alert"`、44px 密码切换按钮。
- 接入 `design/logo/` 管理端资产：`public/logo.svg`、`public/logo.png`、`favicon-admin.ico`，`preferences.ts` 的 `logo.source` 指向新文件。

## 4. 主题数据流（目标态）

```
preferences.ts (唯一品牌色入口)
   └─> updateCSSVariables            packages/@core/preferences/src/update-css-variables.ts:12-82
         └─> updateMainColorVariables                          同上 :88-119
               └─> generatorColorVariables  →  --primary-50…900 / --success / --warning / --destructive
                     ├─> <style id="__vben-styles__"> :root
                     └─> useElementPlusDesignTokens → --el-color-primary-* / --el-bg-color* / --el-border-radius-base
                           └─> Element Plus 组件 + 自研组件（只读 --el-* / --primary）
                                 └─> useChartTheme() 读 computed style → ECharts option
```

约束：链路上任何一环都不允许再出现写死的品牌色。深色模式由 `html.dark` + 引擎自动反转，项目侧不再写 `.dark { --el-* }` 覆盖。

## 5. 试点页面

| 页面 | 必须达成 |
|---|---|
| `dashboard.vue` | KPI ≤ 4 张且层级一致；待办与图表分区明确；所有图表走 `ChartCard` + `useChartTheme`；无演示通知 |
| `care-reminders.vue` | 抽屉打开即 loading；逐行 busy（不用全局 busy 让所有"领取"按钮同时转）；摘要脱敏文案；`CLAIM/REVIEWED/CONTACTED/REFERRED/CLOSE/REOPEN` 状态标签走 `StatusTag`；错误与空态分离 |
| `programs.vue` | 走 `BusinessPageShell`；分页可调；`window.confirm` 归零；状态流转校验前置到按钮禁用而非提交后报错；操作列收纳；删除本地硬编码色 |
| 全局壳层 | `basic.vue` 去演示内容；403/404 品牌化；登录页令牌化；logo 接入 |

其余列表页（`works`、`users`、`audit`、`audit-logs`、`groups`、`inheritors`、`my-projects`、`campus`、`operation-logs`、`emotion-analysis`）在试点验收后按同一契约滚动改造，不在本轮验收范围内，但**新增硬编码色一律禁止**。

## 6. 债务清理批次

| 批 | 内容 | 目标 |
|---|---|---|
| B1 | 引擎收口：改 `preferences.ts`、删 `business.scss` 覆盖、删 `variables.scss` EP 色阶、bump version | P-1、P-2、P-11 |
| B2 | 状态与列表契约：`DataState`、分页、确认、抽屉 | P-3、P-4、P-5、P-6 |
| B3 | 三试点页 + 壳层 + logo | P-8、P-9、D12 |
| B4 | 图表桥与色板清理 | P-7 |
| B5 | 剩余页面滚动改造 | P-10 |

## 7. 验收标准

**自动化门禁**

```bash
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
```

```
rg ':empty-text="errorMsg' apps/web-ele/src/views        # 期望 0
rg 'window\.confirm'       apps/web-ele/src/views        # 期望 0
rg --pcre2 '#[0-9a-fA-F]{3,8}\b' apps/web-ele/src -c     # 期望 ≤ 20，命中文件仅限 src/preferences.ts、令牌层与 useChartTheme
rg 'var\(--el-color-primary' apps/web-ele/src/views      # 期望 > 0（证明在用主题变量）
```

**人工/浏览器**

- 切换 `default / violet / deep-blue / green / gray` 五个预设 + 一个自定义取色：三试点页在 1280 / 768 / 375 无横向溢出，图表颜色跟随。
- 深浅模式各走一遍：无一处文字对比度 < 4.5:1。
- 断网触发列表请求：显示错误态与重试，恢复后重试成功，不清空已有数据。
- 键盘完成"领取关怀任务 → 记录跟进 → 完成"全链路，焦点不丢、结果有 `aria-live` 播报。
- 清空 localStorage 后首屏默认色为 `#4F46E5`；改默认缴版本已 bump 且旧缓存失效。

**边界说明**：以上为构建 + 浏览器验证，不等于真实 MySQL 数据、真实角色账号端到端与生产域名下的验收；这些仍需单独环境验证并如实标注。

## 8. 风险与取舍

| 风险 | 取舍 |
|---|---|
| 删覆盖层会让"现在看起来对"的页面变色 | B1 必须在一个提交内同时完成 preferences 默认值 + 删覆盖，不拆开发布 |
| 缓存导致老用户看不到新品牌色 | D5 强制 version bump；发布说明中写明 |
| 保留 16 预设带来组合测试成本 | 只测 5 个代表预设；不达标修组件；后续可另做白名单 |
| 圆角收敛到单值，卡片观感变"方" | 接受；换来的是深色模式与偏好联动零成本。若后续坚持双档，需单独决策并覆盖 EP 卡片 |
| ECharts 无官方主题桥，读 computed style 有成本 | 只在主题变化时重算一次并缓存；实例数量可控 |
| `use-echarts.ts:82` 存在 `||` 与三元优先级缺陷（上游） | 本轮不 fork 上游；在应用层显式传主题名规避，并在文档记录 |

## 9. 未决问题（需产品/设计确认）

1. 是否允许运营在管理端把主题切成学生端薄荷青（跨品牌预设），还是管理端预设池只留冷色系。
2. 角色差异：咨询师与传承人登录后是否给不同默认主题或不同首页密度。

以上两项不阻塞 B1–B5；确认前按 D4（保留全部预设）与"所有角色同一默认主题"执行。

## 10. 交付顺序

B1 引擎收口 → B2 状态与列表契约 → B3 试点页 + 壳层 + logo → B4 图表桥 → B5 滚动改造。每批独立可回滚、独立提交，提交前按仓库约定先给 `git status --short` 与 `git diff --stat`。
