# 交付状态台账（2026-11-05）

> **性质：只读验证 + 证据汇总。** 本次未修改任何业务代码、未 commit、未 push。
> 本台账严格区分两类信息：**「本次真实跑出」**（2026-11-05 本机执行，数字见 §1）
> 与**「从已有文档摘引」**（注明原文档与日期，本次未重跑）。
> 静态核对与构建/单测通过不能代替真实环境验收。

## 2026-11-05 部署前整体验证（HEAD 6655c70）

> 本节为当日**第二轮**验证（晚间），基线已前进到 `dev` 的 `6655c70`；下文 §0/§1 为当日第一轮（HEAD `19502bb`）的原始记录，保持原样未改。本轮同样未 commit、未 push、未修改业务代码。

### A. 验证基线

| 核对项 | 结果 |
|---|---|
| 分支 / HEAD | `dev` @ `6655c70`（feat(admin): 项目分层下架适配与审核 AI 建议面板） |
| 上游同步 | `git status -sb` 显示 `## dev...origin/dev`，**无领先落后** |
| 工作区 | **仅剩**根目录《详细设计说明书（上篇）`.docx`（M）与 `output/`（??），未 stage、未改 `.gitignore`；**无任何源码级未提交改动**，HEAD 与工作区一致，验证结果可信 |
| 5 条提交确认 | `1f67172`（契约/结构）→ `0b39378`（下架状态机+事件度量）→ `54949d7`（AI 指标+审核建议）→ `9245549`（学生端）→ `6655c70`（管理端），与任务清单一致；其下为 `1c9d24f`（本台账首轮提交）与 `3508e21`（通知 reftype，首轮的在途改动已入分支） |

### B. 逐项结果（全部在 HEAD 6655c70 上执行）

| 项 | 命令 | 退出码 | 关键数字 / 证据 | 结论 |
|---|---|---|---|---|
| 后端全量测试 | `mvn test "-Dmaven.repo.local=../.ai-tmp/m2"`（zhiyu-backend/） | 0 | **Tests run: 874, Failures: 0, Errors: 0, Skipped: 1**；Total time 01:56 min；唯一跳过仍为 `SpringAiLlmClientTest`（需真实密钥，历史口径一致）；较第一轮 800 项新增 74 项来自本轮 5 条提交 | ✅ |
| 后端启动冒烟（真实 MySQL） | `.env` 注入后 `mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18081"`，**另起 18081 端口** | 启动成功 | `Started ZhiyuApplication in 5.219 seconds`；`SchemaCompatibilityCheck` 通过（该 Runner 仅在缺字段时抛 `IllegalStateException`，应用正常 Started 即通过，[SchemaCompatibilityCheck.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/config/SchemaCompatibilityCheck.java)）；日志落 `.ai-tmp/smoke-18081.log` | ✅ |
| ├ 抽查步骤接口 | `GET /api/craft/projects/101/steps`（student01 登录，code=0） | — | HTTP 200，code=0，**4 步全部 `audioUrl: null`**（id 1001–1004，音频资产治理生效，原 101-*.mp3 死引用已清除） | ✅ |
| ├ 抽查 AI 指标 | `GET /api/admin/ai-metrics`（admin 登录，code=0） | — | HTTP 200，code=0，data 含 `window/generatedAt/scenes/note/latencySampleTruncated` | ✅ |
| └ 实例处置 | StopCommand 停止 | — | 18081 端口确认已释放（fetch 不可达）；用户 8080 实例全程未动（验证前探测其本就未运行） | ✅ |
| 管理端类型检查 | `pnpm -F @vben/web-ele run typecheck` | 0 | vue-tsc 无输出，0 类型错误 | ✅ |
| 管理端生产构建 | `pnpm run build:ele` | 0 | turbo **11/11 tasks successful**，总 2m8.937s；产物 `apps/web-ele/dist.zip`（1,724,827 字节） | ✅ |
| 学生端 H5 构建 | `npm run build:h5` | 0 | `DONE Build complete.`；94 行警告全部仍为历史既有 Sass deprecation（App.vue @import + wot-design-uni 依赖），无新增 | ✅ |
| 学生端小程序构建 | `npm run build:mp-weixin` | 0 | `DONE Build complete.`，产物 `dist/build/mp-weixin`；94 行历史警告 + 1 条既有 `Circular chunk: utils/request ↔ stores/user` 提示（历史提交引入的结构提醒） | ✅ |

### C. 5 条提交 ↔ 验证项对应关系

| 提交 | 内容 | 本轮对应验证 |
|---|---|---|
| `1f67172` feat(sql) | openapi +193 行、schema.sql/schema-h2、3 件 20261105 迁移、SchemaCompatibilityCheck 扩列、init-data 音频治理 | mvn test（含 `SchemaCompatibilityCheckTest`）；冒烟启动 SchemaCompatibilityCheck 通过；steps 抽查 `audioUrl:null` |
| `0b39378` feat(craft) | 分层下架状态机、音频资产治理、会话行为事件度量（features 67/68） | mvn test 874 项含该批服务/状态机测试；冒烟 steps 接口结构化返回 |
| `54949d7` feat(ai) | AiClient 指标埋点、`AiCallMetrics`、`/api/admin/ai-metrics`、审核辅助建议（features 65/66） | mvn test 含 `AiClientMetricsTest`/`AiClientModerationTest`/`AiCallMetricsTest`；冒烟 ai-metrics 抽查 code=0 |
| `9245549` feat(app) | 学生端 `session.vue` 事件上报 + `stores/craft.js` + `utils/session-events.js` | `build:h5` / `build:mp-weixin` 均 exit 0（仅证明可构建，见 §D） |
| `6655c70` feat(admin) | 管理端下架适配 + 新 `AiSuggestPanel.vue` + audit/review-center/my-projects | typecheck 0 错误 + `build:ele` 11/11 成功（仅证明类型与构建，见 §D） |

### D. 本次验证不能证明什么

- **单机验证**：单实例、本机回环；不代表多实例/生产部署形态。
- **数据库口径**：874 项测试跑在 **H2**；冒烟使用**本机开发 MySQL 8**（dev profile + `.env`），非生产 MySQL/Redis 环境的真实验收。
- **AI 路径**：单测为 Mock/规则断言；冒烟实例虽经 `.env` 注入密钥，但抽查的 `ai-metrics` 是聚合读接口，**未实际发起 LLM 调用**；真实模型质量仍以 [eval-baseline-20261104.md](file:///d:/develop/Zhi_Yu/docs/eval-baseline-20261104.md) 历史报告为准，本次未重跑评测。
- **审核 AI 建议链路**（moderation-suggest）本次未以真实 LLM 调用验收，仅有单测与构建证据。
- **微信真机**未覆盖：SSE 分块（enableChunked/fallback）、InnerAudioContext、tabBar、键盘行为均无真机证据。
- **HTTPS 与正式域名白名单**未覆盖。
- **并发压测**未做：>20 并发与 100 人口径仍无证据（历史口径见 perf-baseline-20261102 §5）。
- **`suspected_away` 等会话行为事件依赖前端上报**（9245549 的 `session-events.js`）：本次仅验证其可构建，**尚未产生任何真实上报数据**，事件度量链路未形成闭环证据。
- 冒烟为单点接口抽查，不等于端到端业务流程回归。

### E. 结论

**HEAD 6655c70 整体验证通过**：全部验证项退出码 0，无失败项。通过口径限于 §D 所列边界之内；真实环境验收缺口维持 §2.3（下表）原判不变。

---

## 2026-11-05 导出端点与真实 AI 复验（HEAD bbebbe2 时点登记）

> 本节登记 2026-11-05 复验批次（导出端点 `3d43901` + 学生端入口 `bbebbe2`、真实 AI 复验在 `6951a9b` 上执行）的结论与数字。
> 本节数字为该批次的执行结果登记；本轮在此基础上仅独立复核了管理端文案修改后的 `typecheck`（见本节末），未重跑该批次任何测试。

### F. 下架窗口导出端点已建（`3d43901` + `bbebbe2`）

- `GET /api/craft/session/{id}/export`：窗口内附件下载、`omitted[]` 如实标注、成功先落 `export` 事件。
- 错误码纪律：`3001`（窗口过期/状态不符）/`1003`（非本人）/`1004`（不存在）/`1002`（未登录）按语义返回，**不伪装 404**。
- 验证数字：导出单测 **6/6**；全量 **880 测试 0 失败**；真实 MySQL 冒烟 **840 字节附件下载通过**；学生端「导出我的创作数据」入口已接入只读会话（`bbebbe2`）。
- **未验**：小程序真机下载、正式域名白名单、`3001` 过期场景（时间构造不出来，未构造非法数据强测）。

### G. 真实 AI 复验（6951a9b，deepseek-reasoner 真实调用）

| 指标 | 实测值 |
|---|---|
| 真实调用次数 / 结果 | 2 次，`result_status=OK` |
| 延迟 | 1672ms / 1690ms，p50 = 1690ms |
| `ai_trace` tokens（input/output） | 600/74、613/174（真实值，非估算） |
| `ai-metrics` 聚合 tokens ↔ 逐行求和 | **精确吻合**（3001 / 1186） |

据此：「审核 AI 辅助仅有单测」的状态更新为「**已真实验收（限两次样本）**」——样本量小、单模型单时段，不构成长期稳定性结论；管理端加载态文案已按此实测修正（`AiSuggestPanel.vue`，原「推理模型可能需要数十秒」改为「通常数秒，内容复杂时可能更久」，仅文案、未动行为）。

### H. 新发现的分场景缺口（待修）

- **tokens 上报不是全场景**：`llm-moderation` 场景有 usage 落库，但 `llm-message`（作品寄语）场景 tokens 仍为 **NULL**。
- **影响**：按场景统计成本时，寄语类调用无法计价；**不得按调用次数均匀估算**分摊。
- 状态：**待修**（本台账仅登记事实与影响，修复方案由后续任务定）。

### I. 本轮独立复核

| 项 | 命令 | 退出码 |
|---|---|---|
| 管理端类型检查（文案修改后） | `pnpm -F @vben/web-ele run typecheck` | 0 |

---

## 2026-11-05 心流度量与自评校准（工作区在途批次登记）

> 本节登记 2026-11-05「心流度量与自评校准」批次（四件事）的会话报告结论。
> 该批改动**当前为工作区未提交状态**（`craft/calibration/`、`FlowMetricsService.java`、`flow-metrics.vue`、features 70/71 等，见当时 `git status`）；本节数字均来自该批会话报告，**本台账未重跑任何测试/构建复核**。
> 已完成事项与未验证/缺口严格分列，未闭合项不写成已解决。

### J. 已完成的四件事（按会话报告登记）

**J1. 心流自评校准（后端）**
- 端点：`GET/POST /api/craft/session/{id}/calibration` 与 `GET /api/admin/flow-calibration/stats`。
- 存储：复用 `wellbeing_record`（kind=`FLOW_CAL`），**未建新表**；1~7 李克特量表。
- 抽样与降级：抽样率 `zhiyu.flow.calibration.sample-rate` 默认 0.2；跳过落记录、可补答；Spearman 相关、阈值 0.3、minSamples 30；ρ<0.3 或样本不足时降级为「专注停留占比」口径。
- 验证：单测 6/6、全量 **886 测试 0 失败**；真库冒烟抓到 H2 测不出的 `Timestamp`/`LocalDateTime` 强转 500 并已修复。

**J2. 自评弹窗（学生端）**
- 行为：仅 `handleFinish` 成功后异步查询、`sampled && !answered && !skipped` 才渲染，未抽中零元素；✕ 走 `{skip:true}`；失败静默不卡页面。
- 验证：13 项实测通过；顺手修复两处真实布局 bug（`.success-overlay` 缺 `flex-direction: column` 导致双卡并排溢出；7 个评分钮 `min-width:88rpx` 撑爆卡片）；`build:h5` / `build:mp-weixin` 均 exit 0。

**J3. 专注度量看板（后端 + 管理端）**
- 端点与入口：`GET /api/admin/flow-metrics/dashboard`；管理端「数据中心 › 专注度量」。
- 口径：分母 = Σ `step_leave.dwell_seconds`，分子 = 阈值后深度沉浸秒数；同口径剔除（只读/归档、待审项目、无事件会话），剔除明细 `exclusions[]` 最多 50 条；占比先会话级再「人×周」中位数，只报 p50/p90 与分桶样本数；`byLevel` / `byDurationBucket` / `byTakedownKind` 三组独立分面；标题吃后端 `metricLabel`；页脚常驻「engagement ≠ 疗效」；`unavailable[]` 单列；11-05 口径变更 markLine。
- 验证：单测 8/8、全量 **897 测试 0 失败**、管理端 typecheck/build 均 exit 0；真库冒烟 4 项目 5 会话与手算**精确吻合**；非法参数 400+1001、匿名 401+1002、role=1 403+1003；截图见 `output/playwright/admin-ui-fm/`。

**J4. 提示语真实入口回填 + 导出端点前端接入（`3d43901` / `bbebbe2`，已入分支）**
- `GET /api/craft/session/{id}/export`：窗口内下载 JSON 附件；窗口过期/开关关闭/非本人 → 3001/3001/1003；会话不存在 1004。
- 学生端按钮仅在 `readOnly && canExport && sessionId` 三齐备时渲染；H5 走 fetch+Blob，小程序走 `downloadFile`+`saveFile`。
- 验证：单测 6/6、全量 880 测试 0 失败。
- 实现注意：cross-origin 读不到 `Content-Disposition`，已改为按内容识别（业务拒绝是带 `code` 的 Result、导出载荷无 `code`）。

### K. 单列缺口（未验证/待决，不写成已解决）

| # | 缺口 | 性质 |
|---|---|---|
| K1 | `craft_project.level` 19 个项目中 18 个 =1、1 个 =2：难度分面**数据本身无区分度**，代码正确但维度失效；补分级或下掉分面待产品决定 | 待产品决策 |
| K2 | 「事件流首次 3001」**不存在该事件类型**（心跳闸口只抛异常不落库）：剔除起点实际退化为服务端派生的 `session_archive` 首次时间；要精确到心跳被拒时点需补落事件 | 待修（精度缺口） |
| K3 | 跨周会话按 `start_time` 归周，未做跨周拆分（工程折中，已写入契约） | 已知折中 |
| K4 | 真实样本规模：当前真实数据仅 1 个活跃用户、3 个纳入会话；p50/p90/IQR 的统计行为未经大样本检验；自评 ρ 当前必然为 null | 待真实数据积累 |
| K5 | 真机下载、域名白名单、3001 窗口过期场景（含学生端 toast 原文）、并发 finish 去重、小程序自评弹窗，均未实测 | 未验证 |
| K6 | 暗色逐元素对比度未做数值审计（本批未改设计令牌） | 未验证 |

---

## 2026-11-05 双对话收敛（阶段 2）（工作区在途批次登记）

> 本节登记 2026-11-05「双对话收敛（阶段 2）」批次的会话报告结论。
> 该批改动**当前为工作区未提交状态**（`application.yml` 开关翻动、`EmotionControllerViaCommanderTest.java` 等，与心流批同处一个工作区）；本节数字均来自该批会话报告，**本台账未重跑任何测试/冒烟复核**。

### L. 已登记的六件事（按会话报告）

**L1. 性质澄清：本批是「接续」而非从零写**
`modules/dialogue` 15 个类、6 个测试类、DDL/迁移/erase/契约登记已在 `94b8571` 就位；本批只补三处：开关默认值、委托路径 HTTP 级 SSE 平价测试、文档措辞。

**L2. 开关默认值变化（只翻三个，P3 保持关闭）**
`commander` false→true、`listening-via-commander` false→true、`rolling-summary` false→true；`companion-adapter` 保持 false 不变。回落方式 = 环境变量置 false，旧实现未删。

**L3. 既有测试行为变化为零**
`application-test.yml` 显式钉住四开关 false + guard-store memory，既有测试（含「关闭态零 dialogue-route 记账」断言）仍在关闭态运行，未改任何既有断言。唯一修过的测试是本批自建的 `EmotionControllerViaCommanderTest`：首轮 2 失败系 MockMvc standalone 默认 ISO-8859-1 编码把中文 chunk 写成 `?`，判定为**测试设施编码问题**（非实现错误、非假设过时），以 UTF-8 消息转换器修复。

**L4. 上下文参数**
原文窗口 N=10（`zhiyu.emotion.max-history-turns`）；第 11 条起、每滑出 5 条做增量压缩（`summary-trigger-turn:11` / `summary-increment-turns:5`）；摘要走 standard 档、熔断 key `llm-summary`、AES 落 `dialogue_context_summary`、TTL 90 天、注销级联已覆盖。

**L5. 防循环**
深度上限 1（结构性，无二次路由代码路径，有单测断言）；重复意图规范化哈希 ≥3 次/600s 锁定；振荡 A→B→A 强制留 A；会话 30 轮预算、单轮墙钟 30s（超限只记账不阻断）；`RouteGuard` 双存储，Redis 失败 3 次冷却 30s 降级内存。

**L6. 验证**
- 全量 `mvn test`：**889 通过 / 0 失败 / 1 跳过**（exit 0；跳过为历史 `SpringAiLlmClientTest`）。
- 新增 `EmotionControllerViaCommanderTest` 3/3：live chunk 序列+done 形状、legacy 无名 data 事件、危机零 LLM+不落库+done(crisis,revoked)。
- Mock+真实 MySQL 冒烟（默认开）：`emotion/chat` 返回 2 chunk + done 且无 capability 字段；`/api/dialogue/chat` 的 R2/R4 路由均正常；`ai_trace` 仅元数据无明文。
- 回落模式冒烟：`/api/dialogue/chat` 404+1004、旧实现正常、`ai_trace` 零新增 dialogue-route。

### M. 单列缺口（未验证/待决，不写成已解决）

| # | 缺口 |
|---|---|
| M1 | **默认开启组合只有冒烟覆盖，无测试守门**——全量套件跑在关闭态（application-test.yml 钉住），开启态行为变更若回归无自动化拦截 |
| M2 | 真实模型多轮对话质量未验（全 Mock）；评测基线仍是 2026-11-04 历史报告，未重跑 |
| M3 | 滚动摘要真实 LLM 端到端未产出（Mock 下走 FALLBACK 降级，仅验证不阻断对话） |
| M4 | Redis 版 `RouteGuardStore` 仅单测，冒烟用 memory |
| M5 | 多实例一致性、commander 路径首字延迟（NFR-02）、微信真机、P3 适配器接线与 P4 退役，均未覆盖 |
| M6 | **文档内部张力**：§2.4「心流时长看板口径未定」与上一节 J3 的落地事实存在张力，因「既有一字不改」纪律未动，待统一收口 |

---

## 2026-11-05 HEAD 04099c0 部署前整体验证

> 本节为**本轮真实执行的验证记录**（区别于 §F–§M 的批次结论登记）。只验证、未 commit、未 push、未改源码。
> 五条提交：`3255ba0`（craft 校准+看板后端）→ `ea66bf0`（dialogue 三开关默认开）→ `c5e1fcf`（学生端自评弹窗）→ `00ff4ea`（管理端看板页）→ `04099c0`（台账）。

### A. 基线核对

| 核对项 | 结果 |
|---|---|
| 分支 / HEAD | `dev` @ `04099c0`；`dev...origin/dev` 无领先落后 |
| 工作区 | 仅剩根目录 docx（M）与 `output/`（??），无源码级未提交改动，HEAD 与工作区一致 |
| 五条提交 | 与任务清单一致（见上） |

### B. 逐项结果（全部在 HEAD 04099c0 上执行）

| 项 | 命令 / 方式 | 退出码 | 关键数字 / 证据 | 结论 |
|---|---|---|---|---|
| 后端全量测试 | `mvn test "-Dmaven.repo.local=../.ai-tmp/m2"`（锁机制：`.ai-tmp/backend-mvn.lock` 自建自删，日志确认 lock acquired/released） | 0 | **Tests run: 897, Failures: 0, Errors: 0, Skipped: 1**（`SpringAiLlmClientTest` 需真实密钥，历史口径一致）；Total time 01:27 min | ✅ |
| 冒烟实例启动 | 18093 端口（18082 被并行任务占用，换口未动之）；**未注入 `.env`**（其内无 dialogue 开关覆盖）→ MockLlmClient 接管（日志确认「降级模式：未配置 DEEPSEEK_API_KEY」）+ 真实本机 MySQL + **默认开关（全 true）** | 启动成功 | `Started ZhiyuApplication in 3.71 seconds`；SchemaCompatibilityCheck 通过（设计为失败才抛异常） | ✅ |
| ① 步骤接口 | `GET /api/craft/projects/101/steps`（student01） | — | 200 / code=0 / 4 步 / `audioUrl` 全 null | ✅ |
| ② AI 指标 | `GET /api/admin/ai-metrics`（admin） | — | 200 / code=0 | ✅ |
| ③ 专注度量看板 | `GET /api/admin/flow-metrics/dashboard`（admin） | — | 200 / code=0；**`metricLabel` = 「专注停留占比」**（真实数据 insufficient，符合「不得显示心流占时」预期） | ✅ |
| ④ 校准统计 | `GET /api/admin/flow-calibration/stats`（admin） | — | 200 / code=0；`{method:spearman, threshold:0.3, minSamples:30, sampleSize:0, status:insufficient, rho:null}` | ✅ |
| ⑤ 倾听链委托（重点复核） | `POST /api/emotion/chat`（mode=live，Mock） | — | 200，SSE `chunk→chunk→done`，done 载荷 `{"sessionId":...,"crisis":false,"revoked":false}` **无 capability 字段**（协议零破坏）；`ai_trace` 实测落 `scene=dialogue-route, rule=R4→LISTENING, model=mock`（id `2118254057712902100`）——**实际命中委托路径** | ✅ |
| ⑥ commander 端点（重点复核） | `POST /api/dialogue/chat`（Mock） | — | 200，SSE `chunk→chunk→done`，done 含 `capability=LISTENING, toolUsed=false`；`ai_trace` 落 route 行（id `2118254058434322400`，rule=R4）——**默认配置下端点已注册可用** | ✅ |
| 管理端类型检查 | `pnpm -F @vben/web-ele run typecheck` | 0 | 0 类型错误 | ✅ |
| 管理端生产构建 | `pnpm run build:ele` | 0 | turbo 11/11，总 25.107s，web-ele built in 17.24s；`dist.zip` 1,733,211 字节 | ✅ |
| 学生端 H5 构建 | `npm run build:h5` | 0 | DONE；94 行历史既有 Sass 警告，无新增 | ✅ |
| 学生端小程序构建 | `npm run build:mp-weixin` | 0 | DONE；产物 `dist/build/mp-weixin`；94 历史警告 + 既有 Circular chunk 提示 | ✅ |
| 冒烟实例处置 | StopCommand | — | 18093 端口确认释放；8080 全程未动（验证前后均未运行） | ✅ |
| 冒烟数据清理 | `DELETE /api/emotion/sessions/{id}` 业务接口 | — | 两条冒烟会话（`2118254057641598977`、`2118254058497236993`）已逻辑删除（code=0）；`ai_trace` 两行 route 记账保留（只追加指标表，作为本验证证据） | ✅ |

### C. 三个行为变更 ↔ 提交 ↔ 验证覆盖

| 行为变更 | 引入提交 | 本轮验证覆盖到 | 未覆盖到 |
|---|---|---|---|
| craft 心流度量（自评校准 + 看板口径） | `3255ba0`（后端） | mvn test 897 全绿（含 `FlowCalibrationTest`/`FlowMetricsTest`）；真实 MySQL 冒烟两端点 code=0、`metricLabel=专注停留占比`、calibration `insufficient/rho=null` | 真实大样本统计行为；`flow_seconds` 新口径（阈值后时长）未与历史数据对比 |
| dialogue 三开关默认开 | `ea66bf0` | **默认配置冒烟**：委托路径命中（ai_trace route 行）、commander 端点注册、SSE 协议形状不变 | **全量测试跑在关闭态**（application-test.yml 钉住四开关 false）——默认开启组合仅本次冒烟覆盖，无自动化守门；真实模型多轮质量未验 |
| 管理端看板页 / 学生端自评弹窗 | `00ff4ea` / `c5e1fcf` | typecheck 0 错误 + 三端构建 exit 0 | 浏览器/真机走查（页面渲染、弹窗交互）本轮未做 |

### D. 本次验证不能证明什么

- **全量 897 项测试跑在 dialogue 关闭态**（`application-test.yml` 钉住四开关 false）；默认开启组合仅有本次单点冒烟覆盖，无测试守门，开启态回归不会被 CI 拦截。
- 冒烟 AI 链路全为 **Mock**（`model=mock`）：真实模型多轮对话质量、滚动摘要真实生成均未验证；评测基线仍是 2026-11-04 历史报告。
- 冒烟用**本机 dev MySQL + 内存版熔断/guard store**；生产 MySQL/Redis、多实例一致性未覆盖。
- **微信真机**未覆盖（SSE 分块、自评弹窗、导出下载均无真机证据）。
- **并发压测**未做（含 commander 路径首字延迟 NFR-02 口径）。
- `flow_seconds` 语义已变更为「阈值后时长」，**不可与变更前的历史数值直接比较**；本轮未造创作会话，看板读的是既有数据。
- 看板/弹窗为构建级验证，未做浏览器或真机走查。

### E. 结论

**HEAD 04099c0 部署前整体验证通过**：全部验证项退出码 0、无失败项；三个行为变更均有对应证据（craft 度量到端点级、dialogue 默认开到冒烟级、前端到构建级）。通过口径严格限于 §D 边界；其中「默认开启组合无测试守门」建议作为后续第一优先补强项。

---

## 0. 验证基线（第一轮：HEAD 19502bb）

| 项 | 值 |
|---|---|
| HEAD | `19502bb` feat(admin): 审核中心、通知触达与表单组件体系落地 |
| 最近 6 提交 | 19502bb / 001ac4d / 8bca93c / 94b8571 / a9c4421 / 33a9eaa |
| 工作区 | **非干净**：`openapi/openapi.yaml`、`NotificationController.java`、`NotificationService.java`、`AuditReasonQueueTest.java`、`NotificationDeleteTest.java`、`NotificationServiceTest.java` 已修改未提交；新增未跟踪 `NotificationControllerTest.java`、`features/64-notification-reftype-filter.feature`、`output/`；另有一份根目录 docx 二进制改动。**验证完成后**管理端新增 3 个在途改动文件（见 §1 注） |

**口径声明**：任务要求「已提交的干净基线」，但实际工作区带有一批**在途未提交的「通知 reftype 过滤」改动**（对应 features/64）。按约定本次不 stash、不提交、不回退，因此 §1 的后端测试数字反映的是「**HEAD 19502bb + 通知在途改动**」的实际工作区状态，不是纯 HEAD。前端两个工程目录在验证执行前无未提交改动（`git status --short zhiyu-admin-vben` 当时为空、`zhiyu-app` 无改动），前端构建结果可视为纯 HEAD 基线。

## 1. 本次真实跑出的验证数字（2026-11-05 本机）

| 验证项 | 命令 | 结果 | 数字 |
|---|---|---|---|
| 后端全量测试 | `mvn test "-Dmaven.repo.local=../.ai-tmp/m2"`（zhiyu-backend/） | **BUILD SUCCESS** | **Tests run: 800, Failures: 0, Errors: 0, Skipped: 1**；Total time **01:05 min**；Skipped 1 条位于 `SpringAiLlmClientTest`（需真实密钥类，与历史口径一致） |
| 学生端 H5 构建 | `npm run build:h5`（zhiyu-app/） | **成功**（exit 0，`DONE Build complete.`） | 94 行警告**全部为历史既有 Sass deprecation**（legacy-js-api 15 / @import 37 / global-builtin 35 / 其他 7），源自 `src/App.vue` 的 @import 写法与 `wot-design-uni` 依赖内部，非本次新出现 |
| 学生端小程序构建 | `npm run build:mp-weixin` | **成功**（exit 0，`DONE Build complete.`，产物 `dist/build/mp-weixin`） | 警告同上 94 行（历史既有）；另 1 条 `Circular chunk: utils/request -> stores/user -> utils/request` 提示——两文件最近改动在历史提交（cf23a6a、1e214b9），属既有结构提醒，不阻断构建 |
| 管理端类型检查 | `pnpm -F @vben/web-ele run typecheck`（zhiyu-admin-vben/） | **通过**（exit 0，vue-tsc 无输出） | 0 类型错误 |
| 管理端生产构建 | `pnpm run build:ele` | **成功**（exit 0） | turbo 11/11 tasks successful；web-ele `✓ built in 7.75s`；总耗时 13.154s；产物 `apps/web-ele/dist.zip`（1,720,883 字节） |

> 管理端目录在**验证执行前**检查为干净（`git status --short zhiyu-admin-vben` 为空），故正常纳入构建验证；**验证完成后**该目录出现 3 个在途改动文件（`api/zhiyu/notification.ts`、`constants/status-dict.ts`、`views/zhiyu/notifications.vue`，与通知 reftype 同一条线的管理端部分），**未纳入本次验证数字**。管理端结论的有效时点 = 验证执行时刻。
> 本次未跑：真实 AI 评测（需 DEEPSEEK_API_KEY 手动入口）、压测、浏览器走查、MySQL 联调——这些见 §2.2/§3 的历史证据与缺口。

## 2. 四栏台账

### 2.1 已实现且本次验证（有 2026-11-05 数字支撑）

| 能力 | 本次证据 |
|---|---|
| 后端 20 个模块全量单测/集成测试（H2） | mvn test 800 通过 / 0 失败 / 0 错误（§1）；覆盖 auth/emotion/craft/work/group/inheritor/admin/audit/campus/care/growth/file/speech/companion/crisis/practice/heritage/notification/user/dialogue，模块清单与判定见 [gap-audit-20261104.md](file:///d:/develop/Zhi_Yu/docs/gap-audit-20261104.md) §2 |
| 学生端双端可构建（H5 + 微信小程序） | §1 两条构建均 exit 0 |
| 管理端类型安全与生产构建 | §1 typecheck 0 错误 + build 11/11 成功 |
| 审核中心 / 通知触达 / 对话编排 / 账号安全等 HEAD 四组提交能力 | 包含在 800 项测试中全绿（含在途改动涉及的 `NotificationServiceTest` / `NotificationDeleteTest` / `AuditReasonQueueTest`，均通过） |

### 2.2 已实现，仅历史验证（本次基线未重跑，引原文档）

| 能力 | 历史证据与日期 |
|---|---|
| SSE 句级真流式 + 高危句级中断 | [43-sse-live-stream.feature](file:///d:/develop/Zhi_Yu/features/43-sse-live-stream.feature)；真实 DeepSeek 压测 [perf-baseline-20261102.md](file:///d:/develop/Zhi_Yu/docs/perf-baseline-20261102.md)（2026-11-02，55/55 零错误；线程池「假 max」修复后 20 并发首 chunk P95 1818ms，口径见 [requirements-audit-20261021.md](file:///d:/develop/Zhi_Yu/docs/requirements-audit-20261021.md) L35 NFR 行 2026-11-03 刷新） |
| 评论先审后发（默认待审、公开列表过滤、撤回清正文） | [business-delivery-status.md](file:///d:/develop/Zhi_Yu/docs/business-delivery-status.md)「本次验证」评论批次（2026-10-18/19：22 项测试 + 真实 HTTP/MySQL 审核前后可见性验证）；features/46/47 |
| 危机留存降级口径（不存原文、仅存规则式脱敏摘要、加密存储） | [privacy-policy.md](file:///d:/develop/Zhi_Yu/docs/privacy-policy.md) L46（2026-11-04 起口径）；[dialogue-unification-20261104.md](file:///d:/develop/Zhi_Yu/docs/dialogue-unification-20261104.md) L415；features/06、17 |
| 项目下架问责（驳回/下架理由必填落库、通知含原因、WorkDetailVO.auditReason 回填） | [46-audit-reason-queue.feature](file:///d:/develop/Zhi_Yu/features/46-audit-reason-queue.feature)；gap-audit-20261104 §1「不在缺口清单」段（2026-11-04） |
| 媒体签名鉴权与撤回后重鉴权（视频旧签名失效、作者保留访问） | business-delivery-status.md 作品批次（2026-10-19：27 项测试 + 本机 MySQL 联调）；[final-delivery-checklist.md](file:///d:/develop/Zhi_Yu/docs/final-delivery-checklist.md)「媒体清理」段（2026-10-20） |
| AI 评测基线 L1–L6（红线 15/15、L6 摘要忠实性 13/13、AK gate PASS） | [eval-baseline-20261104.md](file:///d:/develop/Zhi_Yu/docs/eval-baseline-20261104.md)（baseline-final 2026-11-04 08:09；Z 批 L6 首轮 2026-11-04 16:09；gate 实测 2026-11-04 21:48，报告在 docs/eval-runs/） |
| 注销级联清理（35 表覆盖）/ 16 类个人数据导出 / 隐私中心 | final-delivery-checklist.md（2026-10-20，419 项回归 418 通过 1 跳过）；business-delivery-status.md 隐私批次（2026-10-20） |
| 一对一预约 / 小组协作 / 活动反馈 / 成长回看 | business-delivery-status.md「本次验证」各批次（2026-10-18～20，均含真实 HTTP/MySQL 联调与浏览器验证） |

### 2.3 已实现，未做真实环境验收（本次与历史均未覆盖，逐条写缺什么条件）

| 能力 | 缺什么条件 | 证据出处 |
|---|---|---|
| 微信真机：SSE 分块（enableChunked ≥2.20.1 与 fallback）、InnerAudioContext 播放、tabBar、键盘交互 | 需真实 iOS/Android 设备 + 微信开发者工具真机预览；浏览器模拟手机尺寸不等于真机 | requirements-audit-20261021 L36/L81；business-delivery-status 多处「微信真机仍待验收」 |
| HTTPS 与正式域名白名单 | 需正式域名、证书、小程序平台白名单配置 | requirements-audit-20261021 L36（NFR-04～09 行） |
| 真实短信/邮件发送通道（找回密码） | 后端 `/api/auth/password/forgot` 已闭环但发送通道未接入，未配置时按设计返回 3001「该功能暂未开放」；需通道选型决策 + 服务商账号 | gap-audit-20261104 §1-1 |
| 100 人并发压测 / 普通接口 P95 ≤500ms | 压测只到 20 并发（SSE 线程池满配 16）；普通接口 P95 无任何证据；需压测环境 + 明确负载口径 | perf-baseline-20261102 §5；requirements-audit-20261021 L35 |
| 备份恢复演练 | 有操作手册与 `backup-manifest` 端点，但恢复演练未执行；保留期限需运营落实 | [runbook-backup-restore.md](file:///d:/develop/Zhi_Yu/docs/runbook-backup-restore.md)；requirements-audit-20261021 FR-70 行 |
| 生产环境 MySQL/Redis 真实验收 | 历史与本次均为本机 MySQL/H2；需类生产部署环境 | requirements-audit-20261021 L88（P0 验收标准行） |
| 千问语音真实通话质量与成本 | 已接入（provider gated），需真实 key + 真机通话实测 | gap-audit-20261104 P1-6；business-delivery-status 语音条 |

### 2.4 已知缺口与待产品决策（只列事实与待决点，不替产品做决定）

| 事项 | 事实 | 出处 |
|---|---|---|
| 心流时长占比看板 | 口径未定（时间窗/分母/暂停归属未定），实现未启动 | gap-audit-20261104 P1-4；requirements-audit FR-60-03 行 |
| 审核 AI 辅助（moderation 档位） | 档位与熔断 key 已预留，无 LLM 调用点；触发条件待拍板 | gap-audit-20261104 §1-4 / P2-3；model-routing-20261104 §2。**→ 更新：`54949d7` 已落地调用点（`AdminModerationService` + `moderation-suggest.st`）与管理端 `AiSuggestPanel.vue`（`6655c70`），features/65 与单测覆盖；真实 LLM 复验已于 2026-11-05 执行（两次 deepseek-reasoner 真实调用均 OK，tokens 真实落库且聚合精确吻合，见 §G）——**已真实验收（限两次样本）**，不构成长期稳定性结论；触发条件决策不变** |
| token usage 采集 | 流式与 chat 链路均不返回 usage，成本核算无数据；采集通道与费率配置未做 | eval-baseline-20261104 §4.3；gap-audit-20261104 P2-4。**→ 更新：`54949d7` 已落地 `AiCallMetrics` 埋点与 `ai_trace` 表（含 input_tokens/output_tokens），`/api/admin/ai-metrics` 第二轮冒烟 code=0（见 §B）、复验批次聚合与逐行求和精确吻合（见 §G）；**但分场景缺口：`llm-message`（寄语）tokens 仍为 NULL，寄语类成本无法按场景计价，待修（见 §H）**；费率配置与成本核算口径仍待决** |
| 小程序订阅消息（通知二期） | 一期（站内通知）已交付；二期依赖一期真实运行数据，未启动 | gap-audit-20261104 P2-6 |
| PSS-10 / PANAS / GSES 量表 | 中文版本授权与计分依据未核实，未启用；不能自行补写题目 | gap-audit-20261104 P2-1 |
| 种子音频死引用 | [init-data.sql](file:///d:/develop/Zhi_Yu/sql/init-data.sql) L92-95 种了 `/static/audio/101-1..4.mp3`，但 `zhiyu-app/src/static/audio/` 目录**不存在**（第一轮核实）；且后端无音频上传端点，步骤音频仍靠手填 URL | 第一轮 grep/Glob 核实（2026-11-05）；gap-audit-20261104 B1。**→ 更新：已经 `1f67172`/`0b39378` 音频资产治理处理，第二轮冒烟实测 101 项目四步 `audioUrl` 全为 `null`（见 §B）；音频上传端点缺口仍按 B1 待决** |
| 危机留存策略的边界 | 留存口径已定（不存原文、仅存脱敏摘要，privacy-policy L46）；但查询端 `GET /api/admin/crisis-logs` **未登记 openapi、管理端无页面**（契约先行纪律项）；留存期限等运营规则未明确 | gap-audit-20261104 §1-2 / P0-3；requirements-audit FR-70 行 |
| 进行中创作会话在项目下架后是否强制中断 | **第一轮核实当时行为**：`CraftSessionServiceImpl` 仅在 `create` 校验项目上架（L54-56，非上架统一 1004）；`changeStatus`/`heartbeat`/`getProgress` 不再校验——即下架**不中断**进行中会话，只阻断新建 | [CraftSessionServiceImpl.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/modules/craft/service/impl/CraftSessionServiceImpl.java)（2026-11-05 第一轮静态核实）。**→ 更新：`0b39378` 分层下架状态机已定义新行为——公开面即收、进行中/暂停会话转只读、72h 导出窗（`zhiyu.takedown`，application.yml L185-190）、窗口结束转归档；features/67 与单测覆盖（874 项全绿，见 §B）；真实下架流程的端到端演练未做** |
| 忘记密码前端入口 | 后端闭环，两端均无页面入口；发送通道决策（校内可先走管理员人工通道）待拍板 | gap-audit-20261104 §1-1 / P0-2 |
| dialogue P3/P4（companion 并入统一入口、旧端点退役） | P0–P2 已实现但三个开关默认 false；P3/P4 按分期设计未启动；评测集 7 条 cmd-* 用例仍 SKIPPED | gap-audit-20261104 §1-3；eval-baseline-20261104 §Z.3 |
| 情绪基线偏离提醒 / 推荐反馈回灌排序 | 前者零代码证据；后者只有采集侧不回灌排序（依赖真实点击数据积累口径） | gap-audit-20261104 §1-4 / P1-5 / P2-2 |

## 3. 边界声明

- §1 数字为 2026-11-05 本机（Windows）真实执行结果；§2.2 为文档摘引，本次未重跑；§2.3/§2.4 为事实与待决点罗列。
- 本台账不代表生产验收通过；构建、H2 测试不能代替 MySQL/真机/真实外部服务/压测验收（AGENTS §8 DoD 口径）。
- gap-audit-20261104 §0 的「60+ 文件未提交」前提已随 HEAD 四组提交（94b8571→19502bb）消化；当前工作区仅剩 §0 所列的通知 reftype 在途改动与一份 docx 二进制改动。
- 本台账未做代码审查，不改变任何已有文档结论；引用处均给出路径。
