# 管理端企业级 UI 对标与行动计划（2026-11-03）

适用范围：`zhiyu-admin-vben/apps/web-ele`。目标：从「能用的后台」到「企业级、有质感、交互编排到位」。
设计基座已定（不推翻）：青玉案主色 `brand-600 #217E76`、珍珠玻璃令牌（`theme.scss`，HSL 派生）、ZyListPage + useListPage 封装。

## 1. 对标来源与结论

调研四个来源，只抄思路不抄代码：

| 来源 | 核心可借鉴点 |
|---|---|
| Vben Admin 官方（doc.vben.pro） | 列表 = 「查询表单 + 表格」一个单元；远程数据契约固定；**适配层集中注册**默认行为与单元格渲染器；查询表单不触发校验、只发查询事件；`codec: {encode, decode}` 把「表单值 ↔ 提交参数」收进纯函数边界；封装不满足就回退原生组件（逃生舱原则） |
| scaffold-master（参考仓库） | 珍珠玻璃视觉语言（已移植）；材质分五级、**玻璃只用于浮层、绝不牺牲表格可读性**；动效集中在 `composables/motion.ts`：共享 EASE/DUR 常量、`revealCascade` 70–90ms 级联入场、常驻动画只动 transform/opacity；reduced-motion 直接渲染终态、帧循环在隐藏/离屏时暂停 |
| ant-design-pro（ProTable） | 单 `request` API → `{data,total}`；**列配置同时生成搜索项**（`valueEnum` 字典渲染），插槽仅作逃生舱 |
| shadcn-admin / vue-pure-admin | shadcn 证明**无 JS 动效库也能达标**（Radix CSS 过渡天然尊重 reduced-motion）；@pureadmin/table 证明 el-table 零侵入封装 + 内置分页/加载是可行形态 |

## 2. 现状差距（诊断）

1. **列表页两套写法并存**：users/works/comments 三页已迁 ZyListPage；其余 9 个列表页各自复制「fetch + 竞态守卫 + 分页」逻辑（groups/inheritors/audit-logs/care-reminders/audit/my-projects）或干脆压缩成单行难维护（programs/operation-logs/campus）。
2. **表单校验散落**：仅 inheritors 新建弹窗有 FormRules；users 编辑、audit 驳回等弹窗无就地校验，错误只靠 toast（Vben/shadcn 共识：错误就地留在字段）。
3. **动效无编排**：玻璃 hover（zy-elevate）与掠光（zy-sheen）已有，但列表进出场、卡片交错、弹窗过渡全靠 Element 默认，无统一时长/缓动常量，无交错入场。
4. **图表无下钻**：OperationsBoard 系列 ECharts 只展示，点击柱/点不能带筛选跳明细；空数据时图表区域无解释性文案。
5. **细节不一致**：筛选区有的可折叠有的不可；页头主操作有的在筛选行（inheritors）、有的在页头（my-projects）；`prefers-reduced-motion` 只有零星页面处理。

## 3. 行动计划（按优先级）

| # | 事项 | 来源思路 | 状态 |
|---|---|---|---|
| P0 | ZyListPage 铺开 9 个列表页（groups/operation-logs/campus/my-projects/inheritors/audit-logs/care-reminders/audit/programs） | pure-admin 零侵入封装 | ✅ 本日完成（3 批，逐页可回退） |
| P0 | ZyListPage 补 `headerActions` 插槽：页头主操作归位（my-projects/programs），筛选行只放筛选 | scaffold 页面结构固化 | ✅ 本日完成 |
| P1 | 表单层：`#/utils/form-rules` 共享规则组 + `ZyFormField`（统一标签/错误就地/hint 位），两个新建弹窗试点并去重 | Vben schema + shadcn 就地错误 | ✅ 本日完成 |
| P1 | 动效：复用既有 `animations.scss` 类，ZyListPage 页头/横幅/筛选卡/列表卡错峰入场（60ms 级联）；animations.scss 补全局 reduced-motion 直出终态；不引 JS 动效库 | scaffold 动效编排 | ✅ 本日完成 |
| P2 | 图表下钻：既有 OperationsChart `click`/`chart-links` → `router.push` 带 query 链路本轮回归验证通过（看板柱图「纸艺」→ 内容审核 `status=1&category=纸艺`，筛选态落盘）；空态解释文案既有 ChartCard 已覆盖 | ECharts 社区标准做法 | ✅ 本日验证 |
| P2 | useListPage 支持从 route query 初始化筛选态（下钻落点配套） | Vben codec 思路的轻量版 | 暂缓：audit/programs 已各自从 query 初始化，模式够用，抽象延后 |
| P3 | 列字典渲染（`valueEnum` 思路）：角色码/状态码 tag 映射收敛为共享工具 | ProTable | ✅ 2026-11-04 完成（D1：`#/constants/status-dict` 17 份字典 + `ZyStatusTag`；定稿两处不一致：craft_project.status 按 DDL 统一「待审/上架/下架」，level=3 统一「高阶」对齐 DDL/契约/学生端） |

## 3.1 第二轮增量（2026-11-04，D1–D4）

- **D1 状态字典**：`src/constants/status-dict.ts` + `src/components/zy-status-tag/ZyStatusTag.vue`；替换 12 列表页 + ai-governance + system-health + project-edit 的全部散落映射；发现并按后端 DDL/契约定稿两处文案不一致。
- **D2 非列表页形态**：heritage（工作流条吸顶 + 编辑弹窗脏检查拦截关弹窗/跳路由）；runtime-config / speech-config（分组卡片 + 作用与默认值 + 生效时机说明，默认值源自 RuntimeSettings.defaults）；监控三页（时间窗口收敛为 `ZyRangeSelect`，ai-governance/system-health 字典化）；project-edit（表单三段分组 + rules + scroll-to-error + 焦点落首个错误字段）；看板壳沿用共享组件无需再改。
- **D3 可达性**：行内操作键盘 Tab 可达（实测）、弹窗焦点进出/Esc/归还（EP focus-trap 实测）、`#/utils/form-a11y` 的 focusFirstError 接入三个表单、focus-visible 静态覆盖图 + 浏览器抽测。
- **D4**：抽屉/预览加载统一 el-skeleton；暗色 19 条业务路由程序化扫描零硬编码亮底。

## 4. 不做的事（明确排除）

- 不引入 vxe-table、TanStack Form、Zod、GSAP——现有 Element Plus + CSS 已覆盖需求，引入即依赖与体积负债。
- 不把玻璃材质铺到表格行/阅读面（scaffold 材质红线）。
- 不改动 heritage（目录编辑器）、speech-config / runtime-config（配置页）、ai-governance / system-health / emotion-analysis（图表与监控页）的页面类型归属；它们走一致性/动效/表单Pass，不套 ZyListPage。
- 不动后端接口；不改主色与珍珠玻璃令牌体系。

## 5. 风险与注意

### 坑位记录

- **el-form-item 的显式 `required` prop 会覆盖 rules**：包装组件（如 ZyFormField）若给 el-form-item 传显式 `required`（含 `false` 默认值），element-plus `normalizedRules` 会把规则里的 `required: true` 改写为 false，空值被 async-validator 跳过——客户端校验静默失效。必填一律只写进 rules。
- **`scroll-to-error` 只滚动、不聚焦**（EP 2.14.5 实测）：校验失败后键盘焦点仍在提交按钮上，屏幕阅读器用户不知道错在哪。需配合 `#/utils/form-a11y` 的 `focusFirstError()`（已接入 project-edit / CreateUserDialog / inheritors 新建弹窗）。
- **Vben 布局的真实滚动容器是 `#__vben_layout_scroll`**（不是 `#__vben_main_content`）：sticky/滚动监听以它为准。
- **后端分页参数不一致**：`operation-logs`/`campus-verifications`/`care-reminders`/`project-catalog` 只收 page 且页大小固定（10 或 20）；comments 固定 20；admin users/works/groups/inheritors/audit-logs 收 pageSize 且封顶 100。写新列表页前先核对接口签名。
- **审核类接口无批量端点**：评论/作品/项目审核全部单条；评论带 expectedStatus 乐观锁、项目带 3001 状态守卫、作品无任何并发保护。循环打点伪装批量会半成功半失败，禁止。
- **驳回理由落库**（2026-11-04 后端 I1 已闭合）：评论 review 与作品 status 均接受 `reason`，驳回/下架/隐藏时服务端强制必填（缺失 1001），落库 `audit_reason` 并随通知送达作者。前端三处调用点（review-center / works / comments）已同步接 reason。

### 既有注意（2026-11-03）

- `operation-logs`/`campus-verifications`/`care-reminders`/`project-catalog` 后端**固定每页条数、不收 pageSize** → 对应页面 `show-sizes=false`，fetcher 不透传 pageSize，避免「UI 切 50 条而后端仍返固定条数」的分页错乱。
- `programs` 为服务端全量返回 + 前端筛选本地分页，**不套 useListPage**（其语义是服务端分页），只复用 ZyListPage 结构。
- audit 页原有「服务端 keyword + 前端再过滤标题」双重过滤，前端过滤冗余（MySQL LIKE 已覆盖），迁移时去掉，行为等价。

### 后端缺口（M 批实测，2026-11-04）

通知中心深链与作品详情抽屉落地时确认的三项接口缺口；前端均已按「不伪造、不循环拉全量」原则就位。

1. **commentId → 所属作品解析** ✅ 已闭合（2026-11-04 后端 O2）
   - 后端交付：`GET /admin/comments/{id}`（脱敏摘要含 workId/status）+ `GET /admin/comments` 支持 `workId` 过滤。
   - 前端已消费：comments.vue 消费 `?commentId=`（详情拿 workId → 状态页签+作品过滤 → 行高亮滚动；1004 降级提示）；通知中心 comment 落点保持 `/zhiyu/comments?commentId=`。
   - （原记录）前端已就位：通知 `refType=comment` 落点为评论审核页 `?commentId=<id>`（query 透传）。

2. **WorkDetailVO 缺 `auditReason` 字段**
   - 前端已就位：作品详情抽屉展示状态与基本信息；驳回理由在驳回时随通知送达作者。
   - 受限：`GET /admin/works/{id}` 不返回已落库的 `work.audit_reason`，抽屉无法向审核员回显历史驳回理由（表已有列，I1 起写入）。
   - 后端补完后前端改动点：WorkDetailDrawer.vue 的详情接口类型加 `auditReason`，在基本信息区条件展示（仅管理员视角，注意对学生端不外泄）。

3. **按 workId + status 查评论（管理端审核视角）**
   - 前端已就位：抽屉评论区仅对 `status=1` 作品调用学生端口径 `GET /works/{id}/comments`（该口径管理员无豁免，待审/下架作品整体 404）；其余状态显示缺口说明，不循环拉全量筛。
   - 受限：待审/下架作品的评论上下文、以及「该作品下的待审评论」均无管理端可读路径。
   - 后端补完后前端改动点：WorkDetailDrawer.vue 评论区改走管理端接口（如 `GET /admin/comments?workId=&status=`），支持分态 tab；同时解除「仅已发布作品可读」的前置判断。
