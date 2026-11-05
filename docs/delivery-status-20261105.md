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
| 审核 AI 辅助（moderation 档位） | 档位与熔断 key 已预留，无 LLM 调用点；触发条件待拍板 | gap-audit-20261104 §1-4 / P2-3；model-routing-20261104 §2。**→ 更新：`54949d7` 已落地调用点（`AdminModerationService` + `moderation-suggest.st`）与管理端 `AiSuggestPanel.vue`（`6655c70`），features/65 与单测覆盖；真实 LLM 调用验收未做（见 §D），触发条件决策不变** |
| token usage 采集 | 流式与 chat 链路均不返回 usage，成本核算无数据；采集通道与费率配置未做 | eval-baseline-20261104 §4.3；gap-audit-20261104 P2-4。**→ 更新：`54949d7` 已落地 `AiCallMetrics` 埋点与 `ai_trace` 表（含 input_tokens/output_tokens），`/api/admin/ai-metrics` 第二轮冒烟 code=0（见 §B）；费率配置与成本核算口径仍待决** |
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
