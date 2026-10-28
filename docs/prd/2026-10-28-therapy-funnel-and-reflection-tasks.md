# 疗愈闭环与 AI 创作回顾 · 前后端任务清单

- 对应 PRD：`docs/prd/2026-10-28-therapy-funnel-and-reflection.md`
- 日期：2026-10-28
- 状态：Phase 1（BE-0..BE-3、FE-0..FE-2）与 Phase 2（BE-4、BE-5、FE-3、FE-4）均已实施
- 原则：契约先行（OpenAPI → 迁移/测试 → 服务 → 前端 → 浏览器验收）；不新增错误码；不 push。

## Phase 1 执行记录（2026-10-28）

- 后端：推荐 ID 字符串化、`craft_recommend_event` 表/迁移/兼容检查、点击接口、漏斗聚合接口；新增测试通过。
- 学生端：首页/推荐页静默上报 HOME/RECOMMEND 点击；`node --test tests/*.mjs` 8 项通过；H5 与微信小程序构建通过。
- 管理端：FunnelChart 注册、TherapyFunnelCard、Dashboard 7/30 天接入；84 文件 / 601 测试通过；typecheck/build 通过。
- 浏览器：点击接口返回 200/code 0；漏斗 6/3/2/2/1/1，空态、样本不足、错误重试、深色均通过；`pageErrors=0`。
- 本地 MySQL 已执行 `20261028_craft_recommend_event.sql` 两次（可重复执行）。
- 合成验收数据已按 `990001..990006` 清理；截图在 `output/playwright/p1-*`。
- Phase 2：`reflection.st`、`AiClient.reflectionSummary`、`ReflectionSummaryService`、反思 summary 保存与发布预填完成；AI 可用/降级、未同意、非本人、未完成路径测试通过；H5/小程序构建通过；浏览器生成回顾、保存、发布预填通过，`pageErrors=0`。
- 未做：Gherkin 文件、正式 demo seed 入库、Phase 3 演示彩排。

---

## 0. 依赖与交付顺序

```
BE-0 契约 + ID 字符串化
  ├─ BE-1 schema / migration / H2 / 兼容检查
  │    └─ BE-2 推荐点击接口
  │         └─ BE-3 漏斗聚合接口
  │              └─ FE-1 管理端漏斗组件 ─ FE-2 Dashboard 集成
  └─ BE-4 AI 回顾接口 + prompt
       ├─ BE-5 反思保存 summary
       │    └─ FE-3 反思页 AI 回顾
       │         └─ FE-4 发布页预填
       └─ BE-6 Gherkin / OpenAPI / 文档
BE-7 演示数据 + FE-5 验收（依赖 BE-2/BE-3/BE-5/FE-2/FE-4）
```

- **Phase 1（A）**：BE-0 → BE-1 → BE-2 → BE-3 → FE-1 → FE-2。
- **Phase 2（B）**：BE-4 → BE-5 → FE-3 → FE-4。
- **Phase 3**：BE-6、BE-7、FE-5，回归与演示彩排。
- 前端可在契约冻结后先用 fixture 并行开发，但合并前必须接真实接口。

---

## 1. 后端任务

> 所有新增接口必须先改 `openapi/openapi.yaml`，再改代码；错误码只用现有 1001/1002/1003/1004/3001/5000。

### BE-0 契约与雪花 ID 字符串化

**优先级**：P0
**估时**：S（约 0.5 天）
**依赖**：无

**文件**

- `openapi/openapi.yaml`
- `zhiyu-backend/src/main/java/com/zhiyu/modules/craft/vo/RecommendVO.java`
- `zhiyu-backend/src/main/java/com/zhiyu/modules/craft/vo/RecommendItemVO.java`
- 相关 `CraftControllerTest` / `CraftRecommendServiceTest`（如有断言调整）

**内容**

- [ ] 在 OpenAPI 增加：
  - `POST /api/craft/recommend/click`（operationId `craftRecommendClick`）
  - `GET /api/admin/dashboard/therapy-funnel`（operationId `adminDashboardTherapyFunnel`）
  - `POST /api/growth/reflection/summary`（operationId `growthReflectionSummary`）
  - `POST /api/growth/reflection` 请求体增加可选 `summary`（maxLength 300）
  - `RecommendVO.recommendationId`、`RecommendItemVO.projectId` 改为 `string` + `pattern: '^[0-9]+$'`
- [ ] `RecommendVO` / `RecommendItemVO` 的 Long 字段加 `@JsonSerialize(using = ToStringSerializer.class)`，Java 内部类型保持 Long，JSON 输出字符串。
- [ ] 检查前端/测试是否仍按数字处理这些 ID；URL 拼接、Vue key 均可直接使用字符串。
- [ ] OpenAPI YAML 解析通过；新增 operationId 唯一；错误响应统一 `ErrorResult`。

**Done**

- 推荐接口实测 JSON 中 `recommendationId`、`items[].projectId` 是字符串。
- 现有推荐测试通过；OpenAPI 可导入/解析。

---

### BE-1 点击事件表、迁移、H2 与兼容检查

**优先级**：P0
**估时**：S（约 0.5 天）
**依赖**：BE-0

**文件**

- `sql/schema.sql`
- `sql/migrations/20261028_craft_recommend_event.sql`（新增）
- `zhiyu-backend/src/test/resources/schema-h2.sql`
- `zhiyu-backend/src/main/java/com/zhiyu/config/SchemaCompatibilityCheck.java`
- `zhiyu-backend/src/main/java/com/zhiyu/modules/craft/entity/CraftRecommendEvent.java`（新增）
- `zhiyu-backend/src/main/java/com/zhiyu/modules/craft/mapper/CraftRecommendEventMapper.java`（新增）

**内容**

- [ ] 在 `sql/schema.sql` 增加 PRD §7.1 的 `craft_recommend_event`，字段完整（id/user_id/recommendation_id/project_id/event_type/source/create_time/update_time/deleted）。
- [ ] 新建 MySQL 迁移脚本：
  - 备份说明；
  - `CREATE TABLE IF NOT EXISTS` 可重复执行；
  - 回滚 `DROP TABLE` 说明；
  - 不修改既有表。
- [ ] H2 `schema-h2.sql` 增加同结构表与索引（H2 语法）。
- [ ] `SchemaCompatibilityCheck` 增加 `craft_recommend_event` 必需字段检查；错误提示补充该迁移文件名。
- [ ] 实体使用 `@TableName("craft_recommend_event")`，主键 `ASSIGN_ID`；Mapper 继承 `BaseMapper`。
- [ ] 迁移在空库、旧库各执行一次，再重复执行一次；H2 测试启动通过。

**Done**

- 空库/旧库迁移均成功；重复执行无报错。
- 缺少新表时应用启动给出明确迁移提示。
- H2 测试可插入/查询事件。

---

### BE-2 推荐点击接口

**优先级**：P0
**估时**：S/M（约 1 天）
**依赖**：BE-0、BE-1

**文件**

- `zhiyu-backend/src/main/java/com/zhiyu/modules/craft/controller/CraftRecommendEventController.java`（新增）
- `zhiyu-backend/src/main/java/com/zhiyu/modules/craft/service/RecommendEventService.java`（新增）
- `zhiyu-backend/src/main/java/com/zhiyu/modules/craft/dto/RecommendClickDTO.java`（新增）
- 测试：`RecommendEventServiceTest.java` / `CraftRecommendEventControllerTest.java`

**接口**

`POST /api/craft/recommend/click`

```json
{ "recommendationId": "930000000000000001", "projectId": "940001", "source": "HOME" }
```

**内容**

- [ ] DTO：
  - `recommendationId`、`projectId` 字符串，`@Pattern("^[0-9]+$")`；
  - `source` 仅允许 `HOME` / `RECOMMEND`。
- [ ] 校验当前用户对 `craft_recommendation` 的所有权；校验项目属于该推荐（查 `craft_recommendation_item`）。
- [ ] 不存在/不属于本人统一返回 1004，不泄露他人数据；source 非法返回 1001。
- [ ] 写入 `CLICK` 事件；重复点击幂等返回成功；依赖唯一键兜底。
- [ ] 点击失败不抛系统异常；客户端会静默忽略，但服务端保留可诊断日志（不含隐私正文）。
- [ ] 测试：成功、重复、非本人推荐、错误 source、项目不匹配。

**Done**

- 每个用户/推荐/项目最多一条 CLICK。
- 非本人推荐返回 1004；不暴露推荐是否存在。
- OpenAPI 与实现字段一致。

---

### BE-3 疗愈漏斗聚合接口

**优先级**：P0
**估时**：M（约 1–1.5 天）
**依赖**：BE-1、BE-2（点击阶段）

**文件**

- `zhiyu-backend/src/main/java/com/zhiyu/modules/admin/controller/TherapyFunnelController.java`（新增）
- `zhiyu-backend/src/main/java/com/zhiyu/modules/admin/service/TherapyFunnelService.java`（新增）
- `zhiyu-backend/src/main/java/com/zhiyu/modules/admin/vo/TherapyFunnelVO.java`（新增）
- 测试：`TherapyFunnelServiceTest.java`

**接口**

`GET /api/admin/dashboard/therapy-funnel?days=7|30`

**内容**

- [ ] 仅 `ROLE_3`；非管理员 1003；`days` 非 7/30 返回 1001。
- [ ] 以 `Asia/Shanghai` 计算 `[start,end)`；排除 `deleted=1`。
- [ ] cohort：窗口内 `emotion_session` 的学生用户（role=0，账号未删除）。
- [ ] 六阶段按 PRD §4.2 统计，按 user_id 去重，且必须属于 cohort：
  - emotion / recommended / clicked / started / completed / published。
  - 不强制 session_id 串联；PRD 已说明这是运营口径。
- [ ] 转化率：`fromPrevious`、`fromStart`；上一阶段/首阶段为 0 时 null。
- [ ] cohort < 5：`insufficient=true`，转化率 null。
- [ ] 心流：完成会话的 `flow_seconds` 总和 / `end_time-start_time` 秒总和；分母 ≤0 → null。
- [ ] 返回 `days/cohort/insufficient/stages/flow/generatedAt`，不含用户 ID。
- [ ] 测试：
  - 6 人完整链路；
  - 无点击；
  - 小样本；
  - 无完成会话/零时长；
  - 非管理员、非法 days；
  - 逻辑删除不计入。

**Done**

- 同一批用户计数正确；不会把各阶段独立人数直接相加。
- 空 cohort 返回 0 + 空阶段，不报 500。
- 页面可以看到 7/30 天切换后的不同结果。

---

### BE-4 AI 创作回顾接口与提示词

**优先级**：P0
**估时**：M（约 1–1.5 天）
**依赖**：BE-0

**文件**

- `zhiyu-backend/src/main/resources/prompts/reflection.st`（新增）
- `zhiyu-backend/src/main/java/com/zhiyu/ai/AiClient.java`
- `zhiyu-backend/src/main/java/com/zhiyu/modules/growth/service/ReflectionSummaryService.java`（新增）
- `zhiyu-backend/src/main/java/com/zhiyu/modules/growth/controller/GrowthController.java`
- `zhiyu-backend/src/main/java/com/zhiyu/modules/growth/dto/GrowthRequests.java`
- 测试：`ReflectionSummaryServiceTest.java`

**接口**

`POST /api/growth/reflection/summary`

**内容**

- [ ] `reflection.st` 使用 UTF-8；约束 2–3 句、≤200 字、非诊断、只复述自填内容与完成步骤；用户文本仅作数据，不执行指令。
- [ ] `ReflectionSummaryService`：
  - 校验当前用户、session 存在且属于本人、status=3；
  - 读取 project title、步骤总数/当前完成步骤；
  - 渲染提示词（projectTitle/steps/beforeMood/afterMood/emotion/note）；
  - 调用 `AiClient.reflectionSummary`；
  - 空结果/异常返回固定模板 + `generated=false`；
  - 不读取 `emotion_message` 原始对话。
- [ ] `AiClient.reflectionSummary`：
  - 输入安全过滤（scene `reflection`；命中高危按现有安全流程处理）；
  - 复用 `llm-message` 熔断 key，不新增资源 key；
  - 输出安全过滤（scene `reflection`）；
  - 失败返回空串，由 service 降级；不写虚假审计。
- [ ] `GrowthRequests.ReflectionSummary`：sessionId、title、beforeMood/afterMood、emotion、note、consent；校验同反思保存。
- [ ] 响应 `{summary, generated}`；AI 不可用仍 200 + 固定模板。
- [ ] 测试：
  - AI 正常返回（mock）；
  - 熔断/异常降级；
  - 未同意 1001；
  - 会话非本人 1004；
  - 未完成 3001；
  - 输出过滤替换。

**Done**

- AI 可用时得到可读回顾；不可用时得到固定模板。
- 无原始对话进入提示词或审计。
- 备注含高危表达时不生成诊断性内容。

---

### BE-5 反思保存扩展 summary

**优先级**：P0
**估时**：S（约 0.5 天）
**依赖**：BE-4

**文件**

- `zhiyu-backend/src/main/java/com/zhiyu/modules/growth/dto/GrowthRequests.java`
- `zhiyu-backend/src/main/java/com/zhiyu/modules/growth/service/GrowthService.java`
- `openapi/openapi.yaml`
- 测试：`GrowthServiceReflectionTest.java`（或现有 growth 测试）

**内容**

- [ ] `Reflection` 增加 `summary`（≤300，可空）。
- [ ] 保存时把 summary 放入 payload，随现有加密逻辑落库；为空时不写空字段或保持兼容。
- [ ] 现有校验（本人、完成、consent）不变。
- [ ] 测试：有/无 summary 均保存成功；超过 300 返回 1001；不泄露到其他接口。

**Done**

- `wellbeing_record.payload` 可解密出 summary。
- 旧客户端不传 summary 仍正常保存。

---

### BE-6 OpenAPI、Gherkin 与文档收尾

**优先级**：P1
**估时**：S（约 0.5 天）
**依赖**：BE-0..BE-5

**文件**

- `openapi/openapi.yaml`
- `features/19-therapy-funnel.feature`（新增）
- `features/20-ai-reflection.feature`（新增）
- `docs/prd/2026-10-28-therapy-funnel-and-reflection.md`（如口径调整）
- `docs/requirements-audit-20261021.md`（更新 FR-60-03 状态；只改必要行）

**内容**

- [ ] OpenAPI 与实现逐项对照：路径、operationId、字段、错误码、ID 字符串。
- [ ] 两个 `.feature` 覆盖 happy path + 异常路径（见 PRD §8）。
- [ ] 更新审计文档：FR-60-03 从“部分实现”改为“已有漏斗，口径见 PRD”，但不宣称疗效。
- [ ] 记录未做：EXPOSURE/CTR、严格 session-linked 漏斗、用户级归因。

**Done**

- Gherkin 可解析；OpenAPI YAML 可解析；文档口径与实现一致。

---

### BE-7 演示数据包（合成数据）

**优先级**：P0（演示前）
**估时**：S（约 0.5 天）
**依赖**：BE-1..BE-3

**文件**

- `sql/demo/20261028-demo-funnel.sql`（新增，不并入 `init-data.sql`）
- `sql/demo/20261028-demo-funnel-cleanup.sql`（新增）
- 可选 `.ai-tmp/` 一次性脚本

**内容**

- [ ] 至少 20 名合成学生、近 30 天情绪会话。
- [ ] 15 人推荐、8–10 人点击、6–8 人开始创作、4–5 人完成、2–3 人提交作品。
- [ ] 至少 1 个 cohort<5 的 7 天小样本场景。
- [ ] 使用 99xx 高位 ID；不覆盖真实账号/数据；密码哈希只复用本地测试账号。
- [ ] 清理脚本按 ID 范围删除；不删除真实用户。
- [ ] 在本地 MySQL 执行一次，确认漏斗和样本不足场景都可演示。

**Done**

- Dashboard 7/30 天漏斗均有可展示数据；清理后恢复原状。
- 数据全部为合成文本，无真实个人信息。

---

## 2. 前端任务

### FE-0 推荐 ID 字符串与点击埋点

**优先级**：P0
**估时**：S/M（约 1 天）
**依赖**：BE-0、BE-2

**文件**

- `zhiyu-app/src/pages/home/index.vue`
- `zhiyu-app/src/pages/craft/recommend.vue`
- `zhiyu-app/src/utils/recommend-click.js`（新增）
- `zhiyu-app/tests/recommend-click.test.mjs`（新增）

**内容**

- [ ] 首页/推荐页保存 `recommendationId`（来自 `/api/craft/recommend`）。
- [ ] 新增 `trackRecommendationClick({recommendationId, projectId, source})`：
  - 任一 ID 缺失直接返回；
  - 使用 `uni.request`/统一封装但**不弹错误、不触发登录跳转**；
  - 失败静默，只允许开发日志；
  - 不等待结果，不阻塞导航。
- [ ] 首页点击卡片：调用 `source='HOME'`，随后进入详情。
- [ ] 推荐页点击卡片：调用 `source='RECOMMEND'`，随后进入创作会话。
- [ ] 单元测试：缺失 ID 不发请求、payload 正确、失败不抛错。
- [ ] 确认推荐接口字符串 ID 在 URL、key、埋点中均不被 `Number()` 转换。

**Done**

- 点击卡片进入详情/创作不受埋点失败影响。
- 后端能收到一次 CLICK；重复点击不重复计数。
- `node --test tests/recommend-click.test.mjs` 通过。

---

### FE-1 管理端漏斗图表组件

**优先级**：P0
**估时**：M（约 1–1.5 天）
**依赖**：BE-3

**文件**

- `zhiyu-admin-vben/packages/effects/plugins/src/echarts/echarts.ts`
- `zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/components/TherapyFunnelCard.vue`（新增）
- `zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/components/therapy-funnel.ts`（新增，格式化纯函数）
- `zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/components/therapy-funnel.test.ts`（新增）
- 复用：`ChartCard.vue`、`useChartTheme.ts`、`EchartsUI`、`useEcharts`

**内容**

- [ ] 在 ECharts 插件注册 `FunnelChart`，不影响现有图表。
- [ ] `TherapyFunnelCard.vue`：
  - props `days`；
  - 请求 `/admin/dashboard/therapy-funnel`；
  - 使用 `ChartCard` 显示标题、说明、loading/error/empty；
  - cohort=0：空态；
  - insufficient：显示人数 + 样本不足提示，不显示百分比；
  - ready：FunnelChart + 阶段转化率列表 + 心流占比；
  - 使用 `useChartTheme`，深浅模式/主题色跟随；
  - 图表 `aria.label.description` 和 ChartCard `role=img` 名称。
- [ ] `therapy-funnel.ts`：`toFunnelChartData`、`formatRate`、`isInsufficient` 等纯函数；测试覆盖 null 转化率、样本不足、空数据。
- [ ] 错误重试：重新请求，保留旧图？漏斗没有历史数据 retention 需求；错误时 ChartCard 错误态即可。

**Done**

- 6 阶段漏斗在 light/dark/主题色下可读。
- 样本不足时没有虚假百分比；空数据不渲染 canvas。
- 纯函数测试通过；管理端 typecheck/build 通过。

---

### FE-2 Dashboard 集成

**优先级**：P0
**估时**：S（约 0.5 天）
**依赖**：FE-1

**文件**

- `zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/components/OperationsBoard.vue`
- 相关样式（scoped）

**内容**

- [ ] 在 Dashboard 的运营概览区域加入 `<TherapyFunnelCard :days="days" />`。
- [ ] 仅在 `!analytics`（工作台）展示；数据看板保持现有 6 图，避免拥挤。
- [ ] 7/30 天切换时漏斗同步刷新（props watch）。
- [ ] 布局：位于 KPI/待办之后、趋势图之前或之后，保持 1280/768/375 无横向溢出。
- [ ] 不改变现有 `DataErrorBanner`、KPI、图表逻辑。

**Done**

- Dashboard 首屏加载、7/30 切换、空态、错误态正常。
- 375px 无横向溢出；无重复错误横幅。

---

### FE-3 反思页 AI 创作回顾

**优先级**：P0
**估时**：M（约 1–1.5 天）
**依赖**：BE-4、BE-5

**文件**

- `zhiyu-app/src/pages/craft/reflection.vue`

**内容**

- [ ] 新增状态：`aiConsent`、`summary`、`summaryBusy`、`summaryError`、`summaryGenerated`。
- [ ] 新增卡片：
  - 说明「只发送作品名、步骤和自填感受，不发送对话」；
  - `AI 同意` 复选框（与保存授权分开）；
  - 「生成小织回顾」按钮，loading/disabled；
  - `summary` 可编辑文本，显示 `generated=false` 时「使用固定回顾」；
  - 「重新生成」；
  - 错误 `role=alert` + 重试；
  - 失败不隐藏保存/发布按钮。
- [ ] `generateSummary` 调 `POST /api/growth/reflection/summary`；成功后填充 summary；失败展示错误和固定/重试提示。
- [ ] `save` 请求带 `summary`（如有）；保持原保存后跳转成长页的行为。
- [ ] `publish()` 跳发布页时携带 `message`（编码后的 summary），并保留 `sessionId/projectId`。
- [ ] 未生成 summary 或未同意 AI 时，原流程不变。
- [ ] 文案：不诊断、不承诺疗效；摘要默认仅本人可见。
- [ ] 可访问性：可见 label、焦点、`aria-live`/`role=status`、触控目标 ≥36px。

**Done**

- AI 可用：生成 → 编辑 → 保存 → 成长记录可见。
- AI 不可用：固定模板 + 可保存/发布。
- 未勾选 AI 同意：按钮禁用/1001，不调用模型。
- 页面无横向溢出，键盘可操作。

---

### FE-4 发布页预填作品故事

**优先级**：P0
**估时**：S（约 0.5 天）
**依赖**：FE-3

**文件**

- `zhiyu-app/src/pages/work/publish.vue`

**内容**

- [ ] `onLoad` 读取 `message` 查询参数，写入 `form.message`（解码、截断到 500 字）。
- [ ] 保留用户可编辑/清空；不自动提交。
- [ ] 与现有 `sessionId`、`projectId`、`projectTitle` 预填兼容。
- [ ] `message` 为空/非法/超长时安全降级，不报错。
- [ ] 页面显示 AI 生成提示（如「已带入小织回顾，可修改」），但不强制标记。

**Done**

- 反思页「带入作品故事」→ 发布页故事已填、可修改、可删除。
- 预览、发布、审核流程不受影响。

---

### FE-5 跨端构建、浏览器验收与演示彩排

**优先级**：P0（演示前）
**估时**：M（约 1–1.5 天）
**依赖**：FE-0..FE-4、BE-3..BE-5

**内容**

- [ ] 学生端：`pnpm build:h5`、`pnpm build:mp-weixin`；H5 浏览器跑通点击埋点、AI 回顾、发布预填。
- [ ] 管理端：`pnpm --filter @vben/web-ele typecheck`、`build`；Dashboard 漏斗 7/30、空态、小样本、错误重试、深浅模式。
- [ ] 后端：迁移空库/旧库/重复执行；点击幂等；漏斗数字与 SQL 对账；AI 回顾降级；反思 summary 加密落库。
- [ ] 浏览器脚本：学生 H5 + 管理端 5777 + 后端 8080；用合成数据，不写入真实数据。
- [ ] 截图：`output/playwright/funnel-*.png`、`reflection-*.png`、`publish-prefill-*.png`。
- [ ] 演示彩排：按 `docs/demo/demo-script-and-pages-20261028.md` 的 8 分钟脚本走查，只修阻断问题。

**Done**

- 两端构建通过；浏览器 0 pageerror。
- 漏斗数字与数据库查询一致；点击埋点只记一次。
- AI 回顾在 AI 可用/不可用两种情况下都能完成保存/发布。
- 演示数据清理脚本可用。

---

## 3. 建议提交切片

| 顺序 | 提交 | 内容 |
|---|---|---|
| 1 | `feat(api): 增加推荐点击与疗愈漏斗契约` | OpenAPI、ID 字符串化、DTO/schema 设计 |
| 2 | `feat(db): 增加推荐点击事件表` | schema、migration、H2、兼容检查 |
| 3 | `feat(craft): 记录推荐点击` | 点击服务/控制器/测试 |
| 4 | `feat(admin): 增加疗愈漏斗聚合接口` | 漏斗服务/控制器/测试 |
| 5 | `feat(admin): 接入疗愈漏斗图表` | FunnelChart、TherapyFunnelCard、Dashboard、测试 |
| 6 | `feat(growth): 增加 AI 创作回顾接口` | prompt、AiClient、summary service、测试 |
| 7 | `feat(growth): 保存反思回顾并预填发布` | Reflection summary、学生端反思页、发布页 |
| 8 | `test(demo): 增加漏斗演示数据与验收` | Gherkin、demo seed、截图/脚本 |

> 每批提交前展示 `git status --short` 与 `git diff --stat`；不 push；不把用户既有 docx、`.ai-tmp/`、截图或演示数据清理脚本误提交到生产初始化数据。

---

## 4. 总体验收清单

- [ ] 推荐接口 ID 全部为字符串，点击事件 payload 正确。
- [ ] `POST /api/craft/recommend/click` 幂等；非本人/不存在不泄露。
- [ ] 漏斗 6 阶段同人群、同窗口、按 user 去重；样本不足不显示百分比。
- [ ] 心流占比分母/空值处理正确。
- [ ] 管理端漏斗 light/dark/主题色、7/30、375/768/1280、空/错/样本不足通过。
- [ ] AI 回顾正常、降级、未同意、非本人、未完成五类路径通过。
- [ ] 回顾只存本人加密 payload；不进关怀提醒/管理端。
- [ ] 反思页 → 发布页预填可编辑；未生成回顾时原流程不回归。
- [ ] 迁移空库/旧库/重复执行；H2 测试通过。
- [ ] 学生 H5 + 小程序构建；管理端 typecheck/build。
- [ ] Gherkin 可解析；OpenAPI 可解析；文档与实现一致。
- [ ] 演示数据为合成数据，清理脚本可用。

---

## 5. 明确不做

- 推荐 EXPOSURE/CTR、严格 session-linked 因果漏斗、用户级归因。
- AI 回顾自动生成/自动公开/进入管理员或关怀视图。
- 新错误码、新 AI 资源 key、真实语音/学校 SSO/直播。
- B5 剩余页面统一、重安全加固、压测与生产部署。
