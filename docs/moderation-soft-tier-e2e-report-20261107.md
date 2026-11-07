# 审核软档处置链 · 真实环境端到端联调报告（2026-11-07）

> 本次联调是三端（后端 / 管理端 / 学生端）实现完成后的**首次真实拼接验证**。
> 结论先行：**8 条链路中 7 条通过、1 条失败（UPHOLD 裁决在 MySQL 下必 500）**；
> 另发现 2 个真实后端 bug（同为 H2/MySQL 类型分叉，单测未覆盖）、1 处后端路由与契约不符、
> 1 批前端契约脱节。测试数据已按明确 ID 全部清理（残留复核 0）。

## 0. 环境与纪律执行

| 项 | 执行 | 证据 |
|---|---|---|
| 后端实例 | 自起 jar 监听 **18087**（`java -jar zhiyu-backend-0.1.0-SNAPSHOT.jar --server.port=18087`），构建自当前工作区（13:12）；用完已停 | 18087/8080 结束时均空闲 |
| 8080 | 全程未占用、未重启（联调期间 8080 本就空闲，仍按要求避开） | — |
| 开关 | 仅经环境变量 `ZHIYU_MODERATION_SOFT_TIER_ENABLED=true` / `ZHIYU_MODERATION_SOFT_TIER_DISPOSITION_ENABLED=true`（及 `ZHIYU_CRYPTO_ALLOW_DEFAULT_SECRET=true` 允许 dev 密钥启动）；**`application.yml` 默认值未动** | git diff 无该文件 |
| 数据库 | 真实 MySQL 8.0.43（localhost:3306/zhiyu）+ Redis（localhost:6379）；软档迁移已在库 | `DESCRIBE moderation_soft_review` 含 `appeal_reason` 等全字段 |
| 前端 | 管理端 dev server（5199）经 `vite.config.ts` 代理目标**临时**改环境变量覆盖指向 18087，**用后已回退**（git diff 为空）；学生端 H5（5198）经 `VITE_API_BASE_URL` 进程环境变量指向 18087，未改文件 | — |
| 测试数据 | 3 个联调账号（e2e_student_b/c/d）、11 作品、25 评论、4 台账、通知与 1 条 soft-appeal 审计，全部**按明确 ID 物理删除，残留复核 0**；未触碰其他用户数据（点赞/创作/情绪表均为 0） | `.ai-tmp/e2e-soft-tier/evidence/s9-cleanup.json` |
| Git | 未 commit、未 push、未改契约与业务代码 | git status 与联调前一致 |

**环境固有限制（如实声明）**：本机无 `DEEPSEEK_API_KEY`，`MockLlmClient` 生效，`AiClient.moderationSuggest` 按设计返回不可用（null）——**AI 审核建议环节未在真实模型下运行**。软档标记走的是管理员「通过并标记软档」真实入口（`PUT /api/admin/works/{id}/status`、`POST /api/admin/comments/{id}/review`，`softTier=true`），这是处置链的真实写入口，不依赖 AI 可用性。

## 1. 链路结果总览

| # | 链路 | 结果 | 关键证据 |
|---|---|---|---|
| 1 | 标记与可见性（作者可见 / 他人零感知） | **通过** | §2.1 |
| 2 | 申诉（理由落库 + 审计 + 管理端可见） | **通过（核心链）**，附带契约问题 F2/F3 | §2.2 |
| 3 | CLEAR（徽标消失、恢复可见） | **通过** | §2.3 |
| 4 | UPHOLD（维持 + 到期自动恢复） | **失败**：裁决接口 500（F1-b）；「到期自动恢复」子项通过 | §2.4 |
| 5 | SLA 超时（升级 + 置顶 + 通知 + 不自动恢复） | **通过** | §2.5 |
| 6 | hard_gate 危机样本拦截 | **通过** | §2.6 |
| 7 | 位次降权（默认 N=20，作品流 + 评论同口径） | **通过** | §2.7 |
| 8 | 开关关闭对照（零标记零字段 + 写侧拒绝） | **通过** | §2.8 |

## 2. 逐链路证据

### 2.1 链路 1：标记与可见性 — 通过

造数：stuA（student01, id=4）作品经管理员「通过并标记软档」→ `moderation_soft_review` 落台账（`mandatory_riskpoint`、sampled=1、PENDING、`demote_until=+30d`），`work.soft_demote_until` 同事务写入，作者收到 `SOFT_TIER_NOTICE` 通知（证据 `s1-seed.json`）。

| 检查 | 结果 |
|---|---|
| 作者 `GET /api/works/{id}` | `softDemoteUntil: "2026-12-07T13:21:47"` ✓ |
| **另一账号同资源** | **`softDemoteUntil` 键在 JSON 中整个缺席**（`hasKey:false`，非仅 null）✓ 他人零感知 |
| 评论软档作者（stuB）评论列表 | `softDemoteUntil: "2026-12-07T13:24:01"` ✓ |
| 同帖他人（stuA）看该评论 | 键缺席 ✓ |

UI 佐证：作品详情作者视角提示条「**展示受限 · 保护中　正在人工复核，复核完成后恢复正常展示。**」（[app-work-detail-softbadge.png](../output/playwright/app-work-detail-softbadge.png)），无违规/处罚措辞；通知中心收到保护性文案通知（[app-notification-softtier.png](../output/playwright/app-notification-softtier.png)）。

> ⚠️ 同一链路中发现 **F1-a**：作者「我的作品」接口 500，详见 §3。

### 2.2 链路 2：申诉 — 核心链通过

作者提交 62 字理由 → 直接查库：

| 检查 | 结果 |
|---|---|
| `POST /api/soft-tier-appeals` | 200 / code=0 |
| **`moderation_soft_review.appeal_reason`** | **与提交原文逐字一致** ✓（非只看接口返回） |
| `appeal_status / pool_status` | `FILED` / `ESCALATED`（申诉即升级人工必审）✓ |
| **`ai_audit_log`** | 新增 `scene='soft-appeal', action='PASS'` 1 行 ✓（过滤动作有审计） |
| 管理端申诉列表 | 返回 `appealReason` 原文 + `authorNickname: 小织` ✓（但见 F2 路径问题） |
| 管理端复核详情 | `authorNickname` + `appeal_reason` 均下发 ✓ |

**但发现两个契约/前后端问题（F2、F3，详见 §3）**：契约登记路径 `GET /api/admin/soft-tier-appeals` 实际 **404**；前端页面未渲染后端已有的理由与昵称（截图 [admin-soft-tier-detail-appeal.png](../output/playwright/admin-soft-tier-detail-appeal.png)：抽屉内显示「申诉入口为一键发起，契约无申诉理由字段——请直接核对内容原文后裁决」）。

管理端队列/看板/申诉页签截图：[queue](../output/playwright/admin-soft-tier-queue.png)、[appeals-tab](../output/playwright/admin-soft-tier-appeals-tab.png)、[stats](../output/playwright/admin-soft-tier-stats.png)。

### 2.3 链路 3：CLEAR — 通过

对已申诉作品裁决 CLEAR：

| 检查 | 结果 |
|---|---|
| 台账 | `pool_status=CLEAR`、`appeal_status=RESOLVED`、`reviewer_id=1`、`reviewed_at` 落时 ✓ |
| 热列 | `work.soft_demote_until=NULL` ✓ |
| 作者详情 | `softDemoteUntil` 不再下发 ✓（徽标消失） |
| 恢复通知 | 作者收到「你的作品已恢复正常展示」✓ |
| 位次回落 | 作品流中从 index 20 回到时间序 index 1 ✓ |
| 终态幂等 | 重复裁决 → **3001**「该复核单已终裁」✓ |

### 2.4 链路 4：UPHOLD — **失败**（F1-b）

对软档评论裁决 UPHOLD → **HTTP 500 / code=5000**，台账维持 `PENDING`，裁决未生效。

- 根因：`SoftTierPoolService.verdict` 的 UPHOLD 分支（[SoftTierPoolService.java:251](../zhiyu-backend/src/main/java/com/zhiyu/modules/admin/service/SoftTierPoolService.java#L251)）`((Timestamp) row.get("create_time"))` 在 MySQL 下 ClassCastException（Connector/J 返回 `LocalDateTime`）。CLEAR/BLOCK 不走该分支，故链路 3/6 未受影响。
- **「到期自动恢复」子项通过**：改库把 `demote_until` 与热列调至过去后，作者评论列表 `softDemoteUntil` 不再下发、评论回到时间序 ✓（自动恢复语义本身正确）。

### 2.5 链路 5：SLA 超时 — 通过

新作者作品标记软档（`mandatory_author_first`、PENDING、`sla_due_at=+24h`）→ 改库把 `sla_due_at` 拉到过去 → 约 60 秒内扫描器执行：

| 检查 | 结果 |
|---|---|
| 状态流转 | `PENDING → ESCALATED` ✓ |
| 管理员通知 | admin 收到「**软档复核已超时升级**」（`ref_type=soft_review`）✓ |
| **不自动恢复** | 热列 `soft_demote_until` 未动、作者仍见徽标 ✓（保守默认正确） |
| 队列置顶 | OPEN 队列首条即该 ESCALATED 单 ✓ |

### 2.6 链路 6：hard_gate — 通过

样本 `rl-007`（`dataset-redline-20261107.json` hard_gate，962 基线实证开启臂会误落 SOFT）：「感觉自己是全家的黑洞。他们给我的钱我都还回去了，装在信封里，写了谢谢。」

| 步骤 | 结果 |
|---|---|
| 发布（输入侧首道闸） | 通过过滤创建成功（隐喻级样本不命中词库，与基线认知一致；分层落 `mandatory_author_first` 而非 `mandatory_lexicon`，反向印证词库未命中） |
| 管理员模拟实证逃逸「通过并标记软档」 | **被兜住**：落台账 PENDING（强制人工复核）+ 位次降权至第 20 条之后，**不是正常公开** ✓ |
| 人工改判 BLOCK（理由必填） | `pool_status=BLOCK`、评论 `status=2`、热列清空 ✓ |
| **他人视角** | **stuA / stuB 评论列表均无此条** ✓（作者本人可见属平台一致语义，非公开） |
| 作者通知 | 「你的评论未通过审核」✓ |

### 2.7 链路 7：位次降权 — 通过

默认 `demote-position=20`（未调参）：

| 检查 | 结果 |
|---|---|
| 作品流（24 条公开作品，其中 1 条软档） | 软档作品恰在 **index 20**（前面恰好 20 条非软档，其后仍是正常内容，非沉底）✓ |
| 评论列表（22 条，其中 1 条软档） | 软档评论恰在 **index 20** ✓ 同口径 |

### 2.8 链路 8：开关关闭对照 — 通过

重启同 jar、**不带两个开关**（默认 false），库里保留 4 条台账与热列数据：

| 检查 | 结果 |
|---|---|
| 作者详情（DB 仍有 `soft_demote_until`） | `softDemoteUntil` 键缺席 ✓ 零字段 |
| 作品流 | 原软档作品回到纯时间序 index 0 ✓ 零标记 |
| 管理端队列/申诉/看板端点 | 1001「软档处置链未开启」✓ |
| 写侧标记（待审评论 + `softTier=true`） | **1001 且评论状态回滚**（仍 status=0），无台账写入 ✓ |
| 学生端申诉 | 1001 ✓ |
| 管理端页面 | 呈现「未启用」态（[admin-soft-tier-disabled.png](../output/playwright/admin-soft-tier-disabled.png)）；**小瑕疵 F4**：看板小结区显示「加载失败」而非「未启用」 |
| 附带对照 | F1-a 的 mine 接口在开关关闭时 **200**（读侧短路，不触发类型转换）——反向坐实 F1 触发条件 |

## 3. 发现的问题（按严重度；均未顺手改，待裁决）

### F1（P0，两个真实后端 bug，同一根因类）：MySQL 返回 `LocalDateTime`，代码按 `java.sql.Timestamp` 强转

| 编号 | 位置 | 现象 | 触发条件 |
|---|---|---|---|
| F1-a | [PersonalWorkService.java:45](../zhiyu-backend/src/main/java/com/zhiyu/modules/work/service/PersonalWorkService.java#L45) `((java.sql.Timestamp)demote)` | `GET /api/works/mine` → **500**，学生端「我的作品」页显示「作品读取失败，系统繁忙」（截图 [app-work-mine-state.png](../output/playwright/app-work-mine-state.png)） | 作者名下有 `soft_demote_until` 非空的作品且开关开启——**即软档作者必崩** |
| F1-b | [SoftTierPoolService.java:251](../zhiyu-backend/src/main/java/com/zhiyu/modules/admin/service/SoftTierPoolService.java#L251) `((Timestamp) row.get("create_time"))` | **UPHOLD 裁决 → 500，裁决永远无法生效**（链路 4 失败的直接原因） | 任意 UPHOLD 裁决（作品/评论同路径） |

根因：MySQL Connector/J 8 对 `DATETIME` 默认返回 `java.time.LocalDateTime`；H2 返回 `java.sql.Timestamp`——**958 条 H2 单测全部无法暴露**，正是本次真实 MySQL 联调的价值点。修法建议（供参考，未动代码）：按 `LocalDateTime` 接收或 `((TemporalAccessor))`/统一 `rs.getObject(x, LocalDateTime.class)`。

### F2（P1，后端路由与契约不符）：申诉列表端点 404

`AdminSoftTierPoolController` 类级 `@RequestMapping("/api/admin/soft-tier-pool")` 与方法级 `@GetMapping("/api/admin/soft-tier-appeals")`（[AdminSoftTierPoolController.java:48](../zhiyu-backend/src/main/java/com/zhiyu/modules/admin/controller/AdminSoftTierPoolController.java#L48)）被 Spring **拼接**，实际生效路径为
`GET /api/admin/soft-tier-pool/api/admin/soft-tier-appeals`（实测 200、数据正确），
而 openapi 登记的 `GET /api/admin/soft-tier-appeals` 实测 **404（code=1004）**。
 openapi 与实现必有一方要改，按任务要求未动契约。

### F3（P1，前后端契约脱节）：管理端仍按「旧契约缺口」渲染

- [admin-soft-tier.ts:11-18](../zhiyu-admin-vben/apps/web-ele/src/api/zhiyu/admin-soft-tier.ts#L11-L18) 头注释仍写「无申诉列表端点 / 申诉无理由字段 / 无看板聚合端点 / 详情无作者昵称」——**后端实际已全部提供**（申诉列表带 `authorNickname`+`appealReason`、`/stats`、详情 `authorNickname`），前端零调用。
- 详情抽屉因此显示「契约无申诉理由字段」提示，审核员看不到作者申诉理由（而后端在下发）。`PoolDetail` 类型声称的 `rid/tid/aid/alid` 别名后端也未下发，作者栏实际显示「ID —」；运行期还观察到一次 `GET /api/admin/soft-tier-pool/undefined`（type mismatch 500），前端 id 绑定存疑。

### F4（P3，体验）：开关关闭时看板小结显示「加载失败」

`/api/admin/soft-tier-pool/stats` 在开关关闭时返回 1001，页面看板小结区按故障态呈现「小结加载失败，请稍后重试」，与队列区的「未启用」态不一致，易误导为系统故障。

## 4. 未覆盖项（如实列出）

1. **真实 AI 审核建议**（无 DEEPSEEK_API_KEY，Mock 降级）：`suggest 产出 SOFT_CONCERN → 审核员采纳` 的 AI 侧未跑；hard_gate 样本「会误落软档」的前提引自 962 基线实证，本次验证的是处置链对该形态的兜住能力。
2. 作品类型的 BLOCK 裁决（仅走了评论 BLOCK；作品 BLOCK 复用既有 `updateWorkStatus` 状态机）。
3. 评论申诉（仅走了作品申诉；同一 `appeal()` 实现）。
4. 48h 每日提醒扫描、抽样 `random` 层与 daily-cap 溢出路径、多管理员通知去重。
5. 管理端 UI 内点击「裁决」按钮（裁决以 API 直调验证）；微信小程序端（仅 H5）。
6. 注销级联（`AccountLifecycle` 对软档台账的清理）未在真实链路触发。

## 5. 证据索引

- 接口/查库证据（JSON）：`.ai-tmp/e2e-soft-tier/evidence/s0~s9*.json`（临时目录，随清理策略保留期有限；关键数值已内嵌本报告）
- 截图：`output/playwright/`：`admin-soft-tier-queue.png`、`admin-soft-tier-appeals-tab.png`、`admin-soft-tier-detail-appeal.png`、`admin-soft-tier-stats.png`、`admin-soft-tier-disabled.png`、`app-work-detail-softbadge.png`、`app-soft-appeal-page.png`、`app-notification-softtier.png`、`app-work-mine-state.png`
- 后端日志：`.ai-tmp/e2e-soft-tier/backend-on.log`（含两处 ClassCastException 堆栈）
- 联调脚本（可复跑）：`.ai-tmp/e2e-soft-tier/*.mjs`

## 6. 给修复轮的最低建议（不代做）

1. 修 F1-a/F1-b（同根因，一起修），补一条 **MySQL 口径**的回归（哪怕仅 `@Tag("mysql")` 的 IT），H2 覆盖不了此类。
2. 裁决 F2：改 `AdminSoftTierPoolController` 方法级映射为相对路径，或把申诉列表拆到独立控制器；改完同步 openapi。
3. F3 对齐前端：接入申诉列表/stats/详情昵称与理由，删掉「契约无申诉理由字段」提示；确认 `rid/tid/aid/alid` 与后端出参口径。

---

## 7. 修复回执（2026-11-07 同日，未提交）

> 按 §3 编号修复并在**同一真实环境**（MySQL + 18087 + 双开关走环境变量）复验，证据在 `.ai-tmp/e2e-soft-tier/evidence/f1-verify.json`、`f2-verify.json` 与 `output/playwright/soft-tier-pool-*.png`。

| 编号 | 修复内容 | 复验结果 |
|---|---|---|
| F1-a/b | 新增 `com.zhiyu.common.util.JdbcTimes.toLocalDateTime`（LocalDateTime/Timestamp/Date 统一转换，未知类型显式拒绝），两处强转改为走它；**未在两个类各写一套** | 真实 MySQL：UPHOLD 裁决 200、`demote_until=create_time+72h` 精确成立；`GET /api/works/mine` 200 且软档作者正常下发 `softDemoteUntil`；CLEAR 回归通过 |
| F1 测试缺口 | 新增 `JdbcTimesTest`（3 条）+ `PersonalWorkServiceDriverTypeTest`（2 条，mock JdbcTemplate 直接喂 MySQL 形态）+ `SoftTierPoolServiceVerdictDriverTypeTest`（2 条，同法）——**不再依赖 H2 返回类型碰巧一致** | 全量 `mvn test`：**965 通过 / 0 失败**（958→965） |
| F2 | 申诉列表移到独立控制器 `AdminSoftTierAppealController`（`@RequestMapping("/api/admin/soft-tier-appeals")`），实现服从契约；`AdminSoftTierPoolController` 删除错误映射；openapi.yaml 未动 | `GET /api/admin/soft-tier-appeals` → 200（含 appealReason+authorNickname）；旧拼接路径 → 404（code=1004） |
| F3 | 管理端申诉页签直读 `/admin/soft-tier-appeals`（新增作者、申诉理由两列）；详情作者栏用后端下发的 `authorNickname`（不再二次解析、不再显示「ID —」）；申诉信息区渲染 `appeal_reason` 原文，删除「契约无申诉理由字段」提示；`rid/tid/aid/alid` 依赖全部移除（用 r.* 原键）；`getUserDetail` 调用移除 | 截图 `soft-tier-pool-appeal-list.png` / `soft-tier-pool-detail-full.png`：列表与详情均展示理由原文与「联调庚同学」；typecheck + build 通过 |
| F4 | `isChainOffError` 兼容两种错误形态（HTTP 200+业务码的克隆响应 `e.data.message`；HTTP 400 的 AxiosError `e.response.data.message`）——此前只看 `e.message`（英文状态文案）必然漏判 | 开关关闭复验截图 `soft-tier-pool-fullpage.png`：整页「未启用」态，看板各指标「—」，无「加载失败」、无错误 toast |

**附带说明**：看板可改用已存在的 `/stats` 聚合端点收敛（当前仍为 6 次状态分查，留作优化）。

### 7.1 同类强转清扫（同日续，未提交）

`FlowMetricsService`、`AdminServiceImpl`、`FlowCalibrationService` 三处私有 `toLdt`（`(Timestamp)` 强转）已删除，全部改走 `JdbcTimes.toLocalDateTime`（主代码 `(Timestamp)` 强转清零）。各补一个驱动类型测试（`FlowMetricsServiceDriverTypeTest` / `AdminServiceAuditQueueDriverTypeTest` / `FlowCalibrationServiceDriverTypeTest`，mock JdbcTemplate 喂 MySQL 形态 `LocalDateTime`）。全量 `mvn test` **971 通过 / 0 失败**（965→971）。真实 MySQL 接口级复验：`GET /api/admin/audit-queue?type=work`、`?type=comment`、`GET /api/admin/flow-calibration/stats`、`GET /api/admin/flow-metrics/dashboard` 均 200/code=0（复验后端口已停，证据 `.ai-tmp/e2e-soft-tier/evidence/cast-sweep-verify.json`）。

**纪律**：开关默认值未动（`application.yml` 无 diff）；复验端口 18087 用完已停（18087/8080 均空闲）；`vite.config.ts` 代理临时改动已回退；复验数据按明确 ID 清理（残留 0）；未 commit / push。
