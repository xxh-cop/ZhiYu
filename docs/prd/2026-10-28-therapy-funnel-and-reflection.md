# 疗愈闭环驾驶舱与 AI 创作回顾 PRD

- 版本：1.0
- 日期：2026-10-28
- 状态：Phase 1（点击埋点 + 漏斗）已实施；Phase 2（AI 创作回顾）待实施
- 范围：学生端 UniApp、管理端 Vben、Spring Boot 后端与 OpenAPI/数据库契约
- 上游决策：`docs/prd/2026-10-28-therapy-funnel-and-reflection-tasks.md`
- 相关背景：`docs/requirements-audit-20261021.md`（FR-60-03 部分实现）、`docs/superpowers/specs/2026-10-26-admin-ui-baseline-design.md`（B4 图表桥）

---

## 1. 背景与问题

已有能力：

- 学生端已具备情绪会话、推荐、创作、发布、成长记录等闭环入口。
- 管理端已有真实运营汇总（趋势、分布、KPI），但没有「同一批用户从情绪会话到作品发布」的漏斗。
- 推荐只记录「生成」，没有记录「点击」，无法判断推荐是否真的被打开。
- 创作完成后的反思页已能收集作品名、前后感受和备注，但没有一段收束性的回顾文本。

当前两个业务缺口：

1. **管理端看不到完整闭环**：FR-60-03 只做到趋势/分布，无法回答「情绪会话之后，有多少人获得推荐、点开推荐、开始创作、完成并发布」。
2. **学生端缺少创作收束**：完成创作后的情绪体验停在表单填写，AI 陪伴没有形成「完成—回看—带走一句话」的闭环。

---

## 2. 目标与非目标

### 目标

- 建立**同一批用户、同一时间窗**的六阶段疗愈漏斗：情绪会话 → 获得推荐 → 点击推荐 → 开始创作 → 完成创作 → 发布作品。
- 记录推荐点击事件，区分首页推荐与推荐页来源；点击失败不阻塞导航。
- 在管理端 Dashboard 展示漏斗、阶段转化率和完成创作的心流时长占比；空态与样本不足可解释。
- 在学生端「创作后的片刻」增加 AI 创作回顾：手动生成、可编辑、可保存、可带入作品故事；AI 不可用时返回固定模板。
- 全链路复用现有 `AiClient`、内容安全过滤、审计、熔断与主题系统；不新增医疗结论。

### 非目标

- 不把漏斗写成因果推断或疗效证明；它只是运营行为指标。
- 不记录推荐渲染曝光（EXPOSURE）；本轮只记录点击。推荐生成表作为「获得推荐」阶段。
- 不做用户级下钻、个人归因、推荐算法效果评估或 A/B 实验。
- 不自动生成 AI 回顾，不自动公开摘要，不把摘要写入关怀提醒。
- 不引入实时推荐、实时音视频、学校 SSO、真实语音服务或 B5 页面改造。
- 不改变现有推荐选品规则、作品审核流程和关怀授权模型。

---

## 3. 角色与场景

| 角色 | 场景 |
|---|---|
| 学生 | 完成创作后，填写感受，生成一段可编辑的 AI 回顾，保存到成长记录或带入作品故事 |
| 学生 | 从首页或推荐页点击推荐卡片，系统记录点击；点击失败不影响进入项目详情 |
| 管理员 | 在 Dashboard 切换近 7/30 天，查看同一批用户的六阶段漏斗、转化率与心流时长占比 |
| 管理员 | 数据不足时看到「样本不足」说明，而不是虚假百分比 |
| 咨询师 | 不新增可见数据；关怀提醒仍走原有脱敏与授权流程 |

---

## 4. 功能 A：疗愈闭环驾驶舱

### 4.1 用户故事

- 作为管理员，我希望看到近 7/30 天内，从情绪会话到作品发布的阶段人数和转化率，以判断推荐与创作链路是否顺畅。
- 作为管理员，我希望漏斗只统计同一批用户，避免把各阶段独立人数相加造成虚高。
- 作为管理员，我希望在样本不足时明确看到「样本不足」，而不是把极小样本包装成增长。
- 作为管理员，我希望漏斗图与现有 Dashboard 图表一样支持主题色和深浅模式。

### 4.2 口径定义

**报表定位：同人群、同时间窗的运营漏斗，不是因果归因。**

- **时间窗**：`Asia/Shanghai` 自然日，`[start, end)`；仅统计 `deleted=0`。
- **cohort**：在窗口内创建过 `emotion_session` 的学生用户（`user.role=0`，账号未删除）。
- **用户去重**：每个阶段按 `user_id` 去重；用户必须属于 cohort。
- **同人群不强制 session_id 串联**：首页推荐调用 `/api/craft/recommend` 时可能不带 `sessionId`，强制按 `craft_recommendation.session_id` 连接会漏掉首页入口。v1 采用「同一用户 + 同一时间窗」口径；严格因果链路另立报表，不在本轮。

| 阶段 | key | 数据来源 | 窗口内条件 |
|---|---|---|---|
| 情绪会话 | `emotion` | `emotion_session` | `create_time` 在窗口 |
| 获得推荐 | `recommended` | `craft_recommendation` | `create_time` 在窗口，且 user 属于 cohort |
| 点击推荐 | `clicked` | `craft_recommend_event` | `event_type='CLICK'`，`create_time` 在窗口，且 user 属于 cohort |
| 开始创作 | `started` | `craft_session` | `start_time` 在窗口，且 user 属于 cohort |
| 完成创作 | `completed` | `craft_session` | `status=3`，`end_time` 在窗口，且 user 属于 cohort |
| 发布作品 | `published` | `work` | `create_time` 在窗口，`status IN (0,1)`（提交即算，含待审核/私密），user 属于 cohort |

> 说明：`published` 统计“提交作品”而非“审核通过”；否则审核延迟会让漏斗失真。前端文案写「提交作品」。

### 4.3 转化率与辅助指标

- `fromPrevious` = 当前阶段用户数 / 上一阶段用户数；上一阶段为 0 时返回 `null`。
- `fromStart` = 当前阶段用户数 / cohort；cohort 为 0 时返回 `null`。
- **样本不足**：`cohort < 5` 时 `insufficient=true`，所有转化率返回 `null`，阶段人数仍返回；前端显示「样本不足，暂不计算转化率」。
- **心流时长占比**：
  - 对 cohort 内、窗口内完成的 `craft_session` 求和：`SUM(flow_seconds)` / `SUM(TIMESTAMPDIFF(SECOND, start_time, end_time))`。
  - 分母 ≤0 或没有完成会话时返回 `null`；前端显示「—」。
  - 这是行为信号，不是传感器或疗效指标。

### 4.4 推荐点击事件

新增 `craft_recommend_event`，v1 仅写入 `CLICK`；表结构预留 `event_type` 以支持未来 `EXPOSURE`，但本轮不写曝光。

- 点击入口：学生端首页推荐卡片、`pages/craft/recommend` 推荐卡片。
- 点击时调用 `POST /api/craft/recommend/click`，成功与否都不阻塞跳转；失败静默并记录客户端日志（不弹错误 Toast）。
- 同一用户、同一推荐、同一项目、同一事件只记一次（唯一键 + 服务端幂等）。
- `source`：`HOME` / `RECOMMEND`；用于区分入口，不参与漏斗去重。

### 4.5 管理端展示

- 位置：`/zhiyu/dashboard` 的「运营概览」区域，位于 KPI/待办之后、现有趋势图之前或之后（实现时按布局确定）。
- 组件：`TherapyFunnelCard.vue`，使用 B4 的 `ChartCard` + `useChartTheme`。
- 图表：ECharts `FunnelChart`；阶段标签直接显示名称和人数，不靠颜色区分。
- 数据范围切换：复用 Dashboard 的 7/30 天切换；切换时同时刷新漏斗。
- 状态：
  - loading：保留卡片高度，不跳动；
  - error：错误文案 + 重试；不把错误显示成空漏斗；
  - empty（cohort=0）：空态「近 N 天还没有情绪会话」；
  - insufficient（0<cohort<5）：显示阶段人数 + 说明，不显示百分比；
  - ready：漏斗 + 转化率 + 心流时长占比。
- 权限：仅 `ROLE_3`；前端路由已在管理员区域，后端仍强制校验。

### 4.6 隐私与合规

- 接口只返回聚合数字，不返回用户 ID、昵称、会话内容或作品内容。
- 不做个人下钻；导出/日志不含心理原文。
- 页面文案明确「运营漏斗，不代表疗效或因果」。

---

## 5. 功能 B：AI 创作回顾

### 5.1 用户故事

- 作为学生，我完成创作并记录感受后，希望小织用一段话回顾我完成了什么，让这次创作有一个收束。
- 作为学生，我希望这段回顾由我决定是否生成、是否编辑、是否保存，以及是否带入作品故事。
- 作为学生，如果 AI 暂时不可用，我希望仍然能得到一段温和的固定回顾，而不是空白或报错。

### 5.2 用户流程

1. 完成创作 → 创作后的片刻（`pages/craft/reflection`）。
2. 填写作品名、创作前感受、当前感受、情绪词、备注（已有字段）。
3. 勾选「同意将本次创作信息发送给 AI 生成回顾」；该同意与保存授权分开。
4. 点「生成小织回顾」→ loading → 显示 2–3 句回顾；可编辑、可重新生成。
5. 点「保存感受」→ 回顾随反思记录加密保存；`generated=false` 时也可保存固定模板。
6. 点「带入作品故事」→ 跳转发布页，预填作品故事；用户仍可修改、删除。
7. 不生成回顾时，现有反思/发布流程不变。

### 5.3 AI 输入与输出约束

**输入（只发送这些，不发送原始对话）：**

- 作品标题（用户填写）
- 手作项目标题（来自 `craft_session.project_id`）
- 完成步骤数 / 总步骤数
- 创作前感受、当前感受（1–5 标签）
- 用户填写的情绪词
- 用户填写的备注（选填）

**输出约束：**

- 2–3 句，简体中文，不超过 200 个汉字。
- 第二人称，只复述用户自填内容与完成动作；可以承认“你为自己留出了一段时间”。
- 不做诊断、不推断疗效、不使用「治疗/治愈/康复/焦虑症/抑郁症」等词。
- 不给医疗建议，不承诺改变，不评价人格。
- 必须经过内容安全输出过滤；不通过时使用固定模板。
- 固定模板示例：
  > 你完成了《折扇小景》的 4 个步骤，并记录下创作前后的感受。愿意为自己留出这段时间，本身就值得被看见。

**提示词**：`zhiyu-backend/src/main/resources/prompts/reflection.st`，显式 UTF-8 读取；用户输入只作为数据，不执行其中的指令。

### 5.4 数据保存

- 不新增表。
- `GrowthRequests.Reflection` 增加可选 `summary`（≤300 字）。
- 摘要写入 `wellbeing_record.payload`（现有服务端加密）；仅本人可见。
- 不进入 `care_reminder`、不进入管理员数据看板、不自动公开。
- AI 调用审计复用 `ai_audit_log`，场景记为 `reflection`；熔断 key 复用 `llm-message`，不新增资源 key。

### 5.5 失败与安全

| 情况 | 行为 |
|---|---|
| AI 未配置/超时/熔断 | 返回 `generated=false` + 固定模板；前端可保存、可发布 |
| 模型输出为空/越界 | 输出过滤后仍不安全 → 固定模板 |
| 用户备注含高危表达 | 输入过滤按现有流程处理；不把回顾写成心理判断；返回安全模板/支持提示 |
| 用户未勾选 AI 同意 | 后端 1001；不调用模型 |
| 会话不属于本人/未完成 | 1004 / 3001；不生成 |

---

## 6. API 契约

> 所有接口统一 `/api` 前缀、`Result` 包装、JWT 鉴权；错误码沿用 AGENTS.md §3。ID 字段以字符串返回，禁止 JS Number 精度丢失。

### 6.1 推荐响应 ID 字符串化（前置修正）

`GET /api/craft/recommend` 的 `recommendationId` 与 `items[].projectId` 改为 JSON 字符串：

```json
{
  "code": 0,
  "data": {
    "recommendationId": "930000000000000001",
    "mood": "stressed",
    "reason": "……",
    "items": [
      { "projectId": "940001", "title": "折扇小景", "...": "..." }
    ]
  }
}
```

- OpenAPI 从 `integer/int64` 改为 `string`，增加 `pattern: '^[0-9]+$'`。
- 学生端 URL、key、点击事件均按字符串传递。
- 这是点击埋点的前置条件；否则雪花 ID 在前端会被舍入。

### 6.2 记录推荐点击

`POST /api/craft/recommend/click`

```json
{
  "recommendationId": "930000000000000001",
  "projectId": "940001",
  "source": "HOME"
}
```

- `source`: `HOME | RECOMMEND`。
- 校验：推荐属于当前用户、项目属于该推荐、source 合法；否则 1004/1001，不泄露他人资源是否存在。
- 幂等：重复点击返回 `code=0`；唯一键兜底。
- 成功：`{ "code": 0, "message": "操作成功", "data": null }`。
- 失败不阻塞学生端跳转。

### 6.3 查询疗愈漏斗

`GET /api/admin/dashboard/therapy-funnel?days=7|30`

```json
{
  "code": 0,
  "data": {
    "days": 30,
    "cohort": 120,
    "insufficient": false,
    "stages": [
      { "key": "emotion", "label": "情绪会话", "users": 120, "fromPrevious": null, "fromStart": 1.0 },
      { "key": "recommended", "label": "获得推荐", "users": 96, "fromPrevious": 0.8, "fromStart": 0.8 },
      { "key": "clicked", "label": "点击推荐", "users": 54, "fromPrevious": 0.5625, "fromStart": 0.45 },
      { "key": "started", "label": "开始创作", "users": 41, "fromPrevious": 0.7593, "fromStart": 0.3417 },
      { "key": "completed", "label": "完成创作", "users": 28, "fromPrevious": 0.6829, "fromStart": 0.2333 },
      { "key": "published", "label": "提交作品", "users": 19, "fromPrevious": 0.6786, "fromStart": 0.1583 }
    ],
    "flow": { "completedSessions": 28, "flowSeconds": 18600, "focusRate": 0.62 },
    "generatedAt": "2026-10-28T10:00:00+08:00"
  }
}
```

- `days` 仅 7/30；其他 1001。
- 非管理员 1003。
- `focusRate`、`fromPrevious`、`fromStart` 无数据时为 `null`；`insufficient` 时转化率为 `null`。
- 只返回聚合数字。

### 6.4 生成 AI 创作回顾

`POST /api/growth/reflection/summary`

```json
{
  "sessionId": "920000000000000001",
  "title": "期末周的一片叶子",
  "beforeMood": 3,
  "afterMood": 4,
  "emotion": "安定",
  "note": "剪纸的时候一直在数折线，慢慢安静下来。",
  "consent": true
}
```

响应：

```json
{
  "code": 0,
  "data": {
    "summary": "你完成了《折扇小景》的 4 个步骤，并记录下创作前后的感受。愿意为自己留出这段时间，本身就值得被看见。",
    "generated": false
  }
}
```

- `consent=false` → 1001。
- 会话不属于本人 → 1004；未完成 → 3001。
- AI 不可用/越界 → 200 + `generated=false` + 固定模板；不返回 4001，避免前端把可用的兜底当成失败。
- 只发送 5.3 列出的字段；不发送对话原文。

### 6.5 保存反思（扩展现有接口）

`POST /api/growth/reflection` 增加可选字段：

```json
{
  "sessionId": "920000000000000001",
  "title": "期末周的一片叶子",
  "beforeMood": 3,
  "afterMood": 4,
  "emotion": "安定",
  "note": "……",
  "summary": "你完成了……",
  "consent": true
}
```

- `summary` 可空，≤300；非空时随 payload 加密保存。
- 现有校验保持不变：本人、已完成、consent=true。

---

## 7. 数据模型与迁移

### 7.1 新表

```sql
CREATE TABLE IF NOT EXISTS craft_recommend_event (
  id                BIGINT      NOT NULL,
  user_id           BIGINT      NOT NULL COMMENT '点击用户',
  recommendation_id BIGINT      NOT NULL COMMENT 'craft_recommendation.id',
  project_id        BIGINT      NOT NULL COMMENT '被点击项目',
  event_type        VARCHAR(16) NOT NULL DEFAULT 'CLICK' COMMENT 'CLICK；预留 EXPOSURE',
  source            VARCHAR(16) NOT NULL COMMENT 'HOME / RECOMMEND',
  create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_craft_recommend_event (user_id, recommendation_id, project_id, event_type),
  KEY idx_craft_recommend_event_time (event_type, create_time),
  KEY idx_craft_recommend_event_rec (recommendation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐点击事件';
```

- 主键由应用侧 `ASSIGN_ID` 生成，不设 `AUTO_INCREMENT`。
- 迁移：`sql/migrations/20261028_craft_recommend_event.sql`，包含备份说明、可重复执行检查、回滚说明。
- 同步：`sql/schema.sql`、`zhiyu-backend/src/test/resources/schema-h2.sql`、`SchemaCompatibilityCheck`（缺表/缺列时提示执行该迁移）。
- 不改 `wellbeing_record` 表；AI 摘要走 payload。

### 7.2 索引与查询

- 漏斗按 user + 时间窗查询，建议 `emotion_session(user_id, create_time)`、`craft_recommendation(user_id, create_time)`、`craft_session(user_id, start_time/end_time)`、`work(user_id, create_time)` 视实际执行计划补充；不盲目新增重复索引。
- 点击查询使用事件表唯一键与 `(event_type, create_time)`。

---

## 8. 验收标准与 Gherkin

### 8.1 管理端漏斗

```gherkin
Feature: 疗愈闭环漏斗

Scenario: 同一批用户完成情绪到作品的闭环
  Given 近 30 天内有 6 名学生创建过情绪会话
  And 其中 3 人获得推荐并有 2 人点击
  And 其中 2 人开始创作、1 人完成并提交作品
  When 管理员查看疗愈漏斗
  Then 六个阶段人数按同一批用户去重
  And 转化率以首阶段 cohort 为分母
  And 提交作品包含待审核与私密作品

Scenario: 样本不足不计算转化率
  Given cohort 少于 5 人
  When 管理员查看漏斗
  Then 返回 insufficient=true
  And 阶段人数仍可见
  And 转化率为 null

Scenario: 普通用户不能读取漏斗
  Given 当前账号角色不是管理员
  When 请求 /api/admin/dashboard/therapy-funnel
  Then 返回 1003
```

### 8.2 AI 创作回顾

```gherkin
Feature: AI 创作回顾

Scenario: 学生生成、编辑并保存回顾
  Given 学生已完成一次创作
  And 已填写作品名、前后感受和备注
  And 勾选 AI 生成同意
  When 点击生成小织回顾
  Then 返回 2-3 句非诊断回顾
  And 学生可以编辑并保存反思
  And 摘要随记录加密保存

Scenario: AI 不可用时降级
  Given AI 服务超时或熔断
  When 学生生成回顾
  Then 返回固定模板且 generated=false
  And 页面不白屏，仍可保存和发布

Scenario: 未同意不调用模型
  Given 学生未勾选 AI 同意
  When 请求生成回顾
  Then 返回 1001
  And 不产生 AI 审计记录
```

### 8.3 自动化门禁

```bash
# 后端
cd zhiyu-backend
mvn test "-Dmaven.repo.local=../.ai-tmp/m2"

# 管理端
cd zhiyu-admin-vben
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build

# 学生端
cd zhiyu-app
pnpm build:h5
pnpm build:mp-weixin
```

- OpenAPI YAML 可解析，新增 operationId 唯一，路径与字段与实现一致。
- 迁移在空库与旧库各执行一次、重复执行一次；H2 测试通过。
- 浏览器验收见任务清单 FE-6。

---

## 9. 演示数据要求

漏斗不能空着上台。演示前准备一套合成数据（不改真实用户）：

- 至少 20 名合成学生、近 30 天情绪会话。
- 其中约 15 人获得推荐，8–10 人产生点击事件。
- 6–8 人开始创作，4–5 人完成，2–3 人提交作品。
- 至少 1 个 7 天内的小样本场景，用于演示 insufficient。
- 所有作品/评论/关怀数据使用合成文本；演示后按 ID 清理。

> 合成数据只用于演示和验收，不写入真实学生数据；不得用演示数据宣称疗效。

---

## 10. 风险与开放问题

| 风险 | 处理 |
|---|---|
| 首页推荐没有 sessionId，严格 session 链路会漏计 | 采用同用户 + 同窗口口径；页面/文档写明不是因果归因 |
| 点击事件失败影响跳转 | 客户端 fire-and-forget；失败只记日志，不阻塞 |
| 雪花 ID 在 JS 精度丢失 | 推荐响应 ID 字符串化；点击接口只接受字符串 |
| 小样本被误读为转化率 | cohort<5 显示样本不足；不显示百分比 |
| AI 回顾越界或过度解释 | 提示词约束 + 输入/输出过滤 + 固定模板；不做诊断 |
| 回顾摘要泄露隐私 | 只存本人加密 payload；不进关怀提醒/管理端 |
| 演示数据与真实数据混淆 | 合成 ID + 演示后清理；截图不包含真实个人信息 |

**开放问题（不阻塞本轮）：**

- 是否需要后续增加 `EXPOSURE` 事件与推荐 CTR；本轮只做 CLICK。
- 是否需要严格 session-linked 漏斗；本轮用同用户窗口口径。
- AI 回顾是否需要在作品详情公开显示；本轮只在发布页预填，用户可删改。

---

## 11. 交付顺序

1. 契约与数据模型（OpenAPI、schema、migration、H2、ID 字符串化）。
2. 推荐点击接口 + 学生端埋点。
3. 管理端漏斗聚合与图表。
4. AI 回顾接口、提示词、降级与反思保存扩展。
5. 学生端回顾 UI + 发布页预填。
6. Gherkin、类型检查、构建、迁移与浏览器验收。
7. 合成演示数据与彩排。

具体前后端任务、依赖与 Done 标准见 `docs/prd/2026-10-28-therapy-funnel-and-reflection-tasks.md`。
