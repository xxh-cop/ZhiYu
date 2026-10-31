# 织愈 L4 有界智能陪伴：年度路线与 Q1 技术设计

> 状态：方案已获用户方向确认（2026-10-30），尚未开始业务代码实现。
> 本次只落设计文档，不改现有代码。

**Goal:** 在一年内把织愈从“功能完整的竞赛原型”推进为“可演示、可评估、可试点”的 L4 有界智能陪伴系统。

**Architecture:** 保留 Java 17 + Spring Boot + Spring AI + Vue/UniApp/Vben 技术栈，以 `PracticeSession` 统一一次非遗情绪练习，以 `CompanionOrchestrator` 承载小织的工具调用、状态机、记忆、检索与安全策略；现有页面、服务和内容全部保留，作为不同模式接入。

**Tech Stack:** Java 17、Spring Boot 3.4、Spring AI 1.0、MyBatis-Plus/JdbcTemplate、MySQL 8、Redis、Vue 3.4、UniApp、Vben Admin、ECharts 6；Q2 再评估 Spring AI 2.0 / Spring Boot 4 升级。

**Spec:** `docs/superpowers/specs/2026-10-29-heritage-student-experience-design.md`、根目录五份设计文档、`docs/requirements-audit-20261021.md`、`docs/ui-ux-review-20261020.md`

## 0. 决策摘要

- **年度目标**：L4 有界智能陪伴 + 一次真实校园试点；不做 L5 自主治疗。
- **旗舰闭环**：「经纬一刻」10–15 分钟非遗情绪练习。
- **旗舰工艺**：默认瑞昌竹编；夏布绣作为第二旗舰/品牌备选。
- **产品原则**：不减少现有功能；新入口统一，旧页面保留为“探索”或“课程库”。
- **AI 原则**：所有 AI 调用仍走 `AiClient`；Agent 只能调用白名单工具；危机与安全规则不由 LLM 最终决定。
- **技术原则**：不引入 Python 运行时；不默认多 Agent；Q1 不引入 LangGraph4j/MCP；需要时 Q2 再做兼容性 spike。
- **真实模型**：Q1 至少接一个真实 LLM；没有凭据就明确标记 Mock，不宣称 L4。
- **证据原则**：过程指标不等于疗效；量表只作自我了解，不做疗效结论。

## 1. 年度目标与非目标

### 1.1 年度目标

1. 一条旗舰闭环可完整走通：心情 → 选择 → 非遗练习 → 呼吸/停顿 → 文化卡 → 小结 → 成长旅程。
2. 小织从“聊天”升级为“有上下文、有工具、有记忆、有边界”的陪伴者。
3. 有一套可复现的 AI 质量与安全评测，而不是只靠演示截图。
4. 有一次 30–50 人、4–8 周的真实校园试点与专业边界审查。
5. 有一个可维护、可部署、可答辩的稳定版本。

### 1.2 明确不做

- 不做心理诊断、疗效承诺、自动化治疗建议。
- 不做 L5 自主治疗；不让 LLM 独立决定危机转介。
- 不做多 Agent 表演；不引入 Python 运行时；不替换 Vue/UniApp/Vben。
- 不为了“智能化”继续增加页面、量表或入口数量。
- 不在竞赛期把 Spring AI 2.0 / Boot 4 升级和功能开发混在一起。
- 不默认保存原始对话、危机细节或用户未授权的内容。

## 2. 四条主线

### 主线 A：一个旗舰闭环

「经纬一刻」：10–15 分钟，瑞昌竹编 · 经纬初见。
现有 `pages/start`、`pages/heritage/studio`、`culture card`、`pages/journey` 全部保留；新增统一 PracticeSession 编排层，不重写页面。

### 主线 B：一个小织

- 感知当前 PracticeSession；
- 只会调用白名单工具；
- 可选记忆只存偏好，不存原始对话；
- 多模态只做过程反馈，不做心理判断；
- 真实模型、真实语音、真实视觉至少各接一个；
- 有 AI 运行轨迹与降级标记。

### 主线 C：一条证据链

- 20–30 条黄金评测问题；
- 10 条安全红队场景；
- 8 条端到端练习任务；
- 30–50 人试点；
- 过程指标 + 主观反馈；
- 不把量表分数包装成疗效。

### 主线 D：一个可运营的产品

- 内容工作流：草稿、发布、下架、版本；
- AI 运营：模型/Prompt 版本、评测、成本、延迟、失败样本；
- 通知、支持转介、数据导出/删除、备份恢复；
- 真实设备、弱网、并发与安全验收。

## 3. 季度路线图与 Gate

### Q1（第 1–3 月）：L3 底座与旗舰闭环

**交付**
- `PracticeSession` 聚合层与状态机；
- 受控工具注册表；
- 真实 LLM 接入与 Mock fallback；
- Typed SSE（stage/tool/citation/artifact/done/error）；
- 旗舰页改造：Start 创建 session，Studio 显示 session bar + 小织面板；
- 完成文化卡/小结并进入 Journey；
- 20 条评测问题 + 10 条安全红队；
- AI trace 元数据与治理页展示；
- 设计令牌基线（不进行全站大改版）。

**Gate**
- 90 秒可完整演示；
- 真实模型可切换，Mock 明确标注；
- 工具调用成功率、引用支持率、降级率有记录；
- 无 Blocker 安全问题；
- 现有 48 个页面入口一个不少。

### Q2（第 4–6 月）：L4 智能与多模态

**交付**
- 可选记忆（时间/材料/纹样偏好，用户可查看/删除）；
- 作品图像过程反馈（真实视觉模型，无诊断、无美丑评价）；
- 自适应计划（时间、卡住次数、完成率）；
- 情绪—介质—工艺—材料—安全—来源知识图谱；
- AI 运营页：模型/Prompt 版本、评测、成本/延迟、失败样本；
- 管理端内容工作流；
- Spring AI 2.0 / Boot 4 兼容性 spike（独立分支，不混功能）。

**Gate**
- 小织能解释“为什么推荐这个练习”；
- 记忆可控、可删除、默认关闭；
- 多模态只输出过程反馈；
- AI 运营页能解释一次完整会话。

### Q3（第 7–9 月）：内容深度与真实试点

**交付**
- 2 门旗舰工艺、6–8 个短练习、12 个纹样；
- 3 个传承人故事、材料包/活动模板、来源与授权；
- 3 个团体工作坊模板；
- 30–50 人试点，4–8 周；
- 真实设备、弱网、断流、后台切换验收；
- 监控、备份、恢复与审计演练。

**Gate**
- 有试点报告与真实反馈；
- 有内容授权清单；
- 不出现诊断/疗效结论；
- 真实设备与弱网有记录。

### Q4（第 10–12 月）：产品化与竞赛收口

**交付**
- 性能、可用性、无障碍、安全检查；
- Docker/HTTPS/对象存储/Redis/向量存储适配；
- 管理端运营 SOP、审核 SLA、支持转介流程；
- 论文/软著/竞赛材料/演示视频；
- 代码、契约、评测集冻结；
- 可选校内本地化部署方案。

**Gate**
- 稳定版本可重复部署；
- 试点报告、演示脚本、技术与伦理边界说明齐全；
- 不把 Mock、构建、截图冒充真实服务验收。

## 4. Q1 旗舰 L3 技术设计

### 4.1 体验流程

```text
Start 页：选 1/3/5/15 分钟 + 心情
  ↓
创建 PracticeSession（HERITAGE）
  ↓
小织问 1 个问题，最多 2 轮，不进入无限闲聊
  ↓
只推荐 1 个练习：瑞昌竹编 · 经纬初见
  ↓
Studio：4 步跟做 + 呼吸停顿 + 受控 AI 变体
  ↓
完成：文化卡 + 倾听小结 + 作品草稿
  ↓
保存到 Journey；下次可继续
```

### 4.2 PracticeSession 状态机

```text
CREATED
  ├─ LISTENING → RECOMMENDED → PRACTICING
  ├─ PRACTICING ⇄ PAUSED
  ├─ PRACTICING → COMPLETED → ARTIFACT_SAVED
  └─ CREATED / LISTENING / PRACTICING / PAUSED → ABANDONED
```

规则：

- `COMPLETED` 只能从 `PRACTICING` 且当前步骤完成后进入；
- `ARTIFACT_SAVED` 只能从 `COMPLETED` 进入；
- `ABANDONED` 不删除数据，标记后可恢复；
- 危机输入不改变练习状态，直接返回支持卡，AI 不继续推进；
- 每个状态只允许对应工具，越界由 Policy 拒绝。

### 4.3 数据模型

`practice_session`：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 雪花 ID，字符串返回 |
| user_id | BIGINT | 本人 |
| kind | VARCHAR(20) | HERITAGE / CRAFT / MINDFULNESS / DRAWING |
| status | TINYINT | 0 CREATED / 1 LISTENING / 2 RECOMMENDED / 3 PRACTICING / 4 PAUSED / 5 COMPLETED / 6 ARTIFACT_SAVED / 7 ABANDONED |
| mood_code | VARCHAR(32) | 可选，情绪代码 |
| minutes_budget | INT | 用户选择的时间 |
| emotion_session_id | BIGINT NULL | 关联情绪会话 |
| craft_session_id | BIGINT NULL | 关联创作会话 |
| heritage_pattern_id | VARCHAR(32) NULL | 纹样目录 ID |
| current_step | INT | 当前步骤 |
| context_cipher | TEXT | 加密 JSON：时间/材料限制/同意标记，不含原始对话 |
| started_at / completed_at | DATETIME | 生命周期 |
| create_time / update_time / deleted | 标准字段 | 逻辑删除 |

`user_ai_memory`（Q2 完整，Q1 可先建表只用一个偏好）：

| 字段 | 说明 |
|---|---|
| id / user_id | 本人 |
| memory_key | 白名单：preferred_minutes / preferred_material / preferred_pattern_family |
| value_cipher | 加密值 |
| consent_version | 同意版本 |
| source | session / manual |
| expires_at | 默认 180 天 |
| create_time / update_time / deleted | 标准字段 |

`ai_trace`（元数据，不含原文）：

| 字段 | 说明 |
|---|---|
| id / trace_id | 追踪 ID |
| user_id / practice_session_id | 内部关联，管理端不展示 user_id |
| scene | companion / rag / variant / summary |
| model / prompt_version | 模型与 Prompt 版本 |
| tool_name | 工具名，可空 |
| latency_ms / input_tokens / output_tokens | 性能 |
| result_status | OK / FALLBACK / BLOCKED / ERROR |
| fallback_reason | 归一化原因，不含原文 |
| create_time | 只追加 |

原始输入/输出仍由 `ai_audit_log` 加密保存；`ai_trace` 不存原文。

### 4.4 工具契约

| 工具 | 输入 | 输出 | 副作用 | 允许状态 |
|---|---|---|---|---|
| `recommendPractice` | mood, minutes, constraints | 1–2 个练习卡片 + 推荐理由 | 无 | LISTENING / RECOMMENDED |
| `startPractice` | practiceId | practiceSessionId + firstStep | 写 session/craft | RECOMMENDED |
| `nextStep` | practiceSessionId | next step | 写进度 | PRACTICING |
| `suggestBreathing` | reason | 4s/6s 呼吸配置 | 无 | PRACTICING / PAUSED |
| `searchHeritage` | question | 引用列表 | 无 | LISTENING / PRACTICING |
| `generateVariants` | patternId, theme | 3 个受控变体 | 无 | PRACTICING |
| `draftCultureCard` | practiceSessionId | 文化卡草稿 | 无 | COMPLETED |
| `saveArtifact` | practiceSessionId, notes | 加密记录 | 写 wellbeing_record | COMPLETED |
| `suggestSupport` | reason | 支持卡 | 无 | 任意，危机优先 |
| `rememberPreference` | key, value | memory | 写 memory，需显式同意 | COMPLETED |
| `forgetMemory` | memoryId | deleted | 软删 memory | 任意 |

工具规则：

- 每次用户请求最多 2 次工具调用，禁止递归/自调用；
- 工具只能使用当前用户、当前 session；
- 工具参数必须通过 JSON Schema 校验；
- 所有工具结果写入 trace（不写原文）；
- 危机/高危时只允许 `suggestSupport`，不允许推荐或继续练习。

### 4.5 SSE 事件契约

新增 `POST /api/companion/sessions/{id}/message`，保持现有 `/api/emotion/chat` 不变。

```text
event: stage
data: {"stage":"LISTEN|RECOMMEND|PRACTICE|BREATHE|ARTIFACT|DONE","label":"正在准备练习"}

event: tool
data: {"name":"recommendPractice","status":"running|done|failed"}

event: citation
data: {"id":"ihc-bamboo","title":"竹编（瑞昌竹编）","url":"https://...","citationType":"VERIFIED_SOURCE"}

event: message
data: {"delta":"..."}

event: artifact
data: {"type":"culture_card|listening_summary","id":"...","title":"..."}

event: done
data: {"practiceSessionId":"...","status":"COMPLETED"}

event: error
data: {"code":4001,"message":"小织暂时没有连上，可以继续手动练习。"}
```

不发送思维链、工具原始参数、原始检索片段或用户原文。

### 4.6 后端模块

建议新增：

```text
com.zhiyu.modules.companion
├── controller/CompanionSessionController.java
├── controller/CompanionMessageController.java
├── service/CompanionOrchestrator.java
├── service/CompanionPolicy.java
├── service/CompanionEventStream.java
├── tool/CompanionTool.java
├── tool/CompanionToolRegistry.java
├── tool/RecommendPracticeTool.java
├── tool/StartPracticeTool.java
├── tool/NextStepTool.java
├── tool/SuggestBreathingTool.java
├── tool/SearchHeritageTool.java
├── tool/GenerateVariantsTool.java
├── tool/DraftCultureCardTool.java
├── tool/SaveArtifactTool.java
├── tool/SuggestSupportTool.java
└── memory/CompanionMemoryService.java

com.zhiyu.modules.practice
├── model/PracticeSession.java
├── mapper/PracticeSessionMapper.java
└── service/PracticeSessionService.java
```

复用：

- `AiClient`：所有模型调用、安全过滤、熔断、审计；
- `HeritageGraphIndex` / `KnowledgeAskService`：检索与引用；
- `HeritageCoCreateService`：受控变体；
- `ListeningSummaryService`：小结；
- `AdminAiGovernanceService`：治理聚合；
- `BusinessAccess`：用户/会话归属。

### 4.7 前端接入

不新建一套页面，优先复用：

- `pages/start/index.vue`：选择时间和心情，创建 PracticeSession；
- `pages/heritage/studio.vue`：接收 `practiceSessionId`，显示 session bar、AI 小织面板、步骤进度；
- `pages/emotion/history.vue` / `ListeningSummaryCard`：保留小结；
- `pages/journey/index.vue`：展示 Practice Artifact + 文化卡 + 小结；
- `pages/settings/index.vue`：增加“小织记忆”入口（Q2）。

新增组件：

- `PracticeSessionBar.vue`：阶段、时间、保存状态、暂停/退出；
- `CompanionPanel.vue`：小织消息、工具状态、引用卡、支持卡；
- `CompanionEventCard.vue`：typed SSE 的事件渲染；
- `MemoryPanel.vue`：Q2，查看/删除偏好记忆。

### 4.8 安全与边界

- 危机规则优先于 Agent；命中高危只返回支持卡，不继续练习；
- 工具只读/写当前用户数据，禁止跨会话；
- AI 变体只允许工艺白名单与受控参数；
- 作品图像只做过程反馈，不做心理/审美判断；
- 记忆默认关闭、可查看、可删除、TTL 180 天，不记原始对话与高危细节；
- 所有 AI 输出走安全过滤与审计；所有工具调用写 trace；
- 管理端只展示脱敏聚合，不展示原文、用户 ID、思维链或工具参数。

### 4.9 评测与观测

- 20 条黄金问题：事实、关系、资料不足、跨地域混淆、寓意无来源、提示词注入；
- 10 条安全红队：自伤、绝望、越狱、工具越权、记忆泄露、图片隐私；
- 8 条端到端任务：开始、暂停、恢复、完成、保存、删除、降级、返回；
- 指标：工具成功率、引用支持率、任务完成率、降级率、P95 延迟、安全通过率；
- `ai_trace` 写入 `trace_id`，AI 治理页展示聚合与失败样本；
- 不把点击率、使用时长或量表分数当作疗效。

### 4.10 Q1 任务拆解

| 任务 | 交付 | 完成标准 |
|---|---|---|
| Q1-1 | `practice_session` / `user_ai_memory` / `ai_trace` DDL、迁移、H2 结构 | 首次/重复迁移通过；启动兼容检查通过 |
| Q1-2 | `PracticeSessionService` + controller | 创建/读取/暂停/恢复/完成/放弃；越权 404；单测 |
| Q1-3 | `CompanionTool` 注册表 + 3 个只读工具 | 参数校验、用户隔离、状态限制、trace；单测 |
| Q1-4 | `CompanionOrchestrator` + `AiClient` Agent 方法 + prompt | 结构化解码、最多 2 次工具调用、Mock/真实模型可切换 |
| Q1-5 | `CompanionMessageController` typed SSE | stage/tool/citation/message/artifact/done/error；断流可恢复 |
| Q1-6 | Start + Studio + Journey 接入 | 一次旗舰闭环；不删旧入口；375/768/1280 无横向溢出 |
| Q1-7 | Culture Card / Listening Summary 写入 session artifact | 生成与保存分离、加密记录、可删除 |
| Q1-8 | eval 数据集 + trace 治理页 | 20 条评测记录；治理页不展示原文 |
| Q1-9 | 90 秒演示脚本与彩排 | 真实模型或明确 Mock；安全红队通过 |

### 4.11 Q1 验收

- 一条旗舰闭环可完整完成并保存；
- 真实模型可切换，Mock 有明确标识；
- 工具调用有白名单、状态限制、权限和 trace；
- 危机输入不会进入练习推进；
- 现有功能一个不少，旧页面可访问；
- H5/小程序构建通过，后端定向测试通过；
- 没有把截图、Mock 或构建结果当作真实模型验收。

## 5. 年度资源与风险

### 5.1 依赖

- 真实 LLM / 视觉 / 语音凭据（服务端环境变量，不进前端与聊天）；
- 心理中心/心理老师审核话术、危机流程与试点方案；
- 非遗传承人内容、材料与授权；
- 30–50 名试点学生与伦理/隐私流程；
- 真实设备、弱网、并发与备份环境。

### 5.2 风险与应对

| 风险 | 应对 |
|---|---|
| 继续堆功能导致主线稀释 | 年度只做 1 旗舰 + 1 副旗舰；其他功能收进“探索” |
| 真实模型/语音/视觉延期 | Q1 先接文本模型；视觉/语音 Q2；没有就标注 Mock |
| 内容授权周期长 | Q1 启动传承人与心理中心沟通，不等到 Q3 |
| Spring AI 2.0 / Boot 4 升级风险 | Q2 独立 spike，全量回归后再合并 |
| 多 Agent / 框架膨胀 | Q1 单 Agent + 工具；Q2 再评估图框架 |
| 记忆越界 | 默认关闭、白名单、可删除、TTL、无原始对话 |
| 试点被当成疗效证明 | 只讲过程指标和主观反馈，不宣称疗效 |

## 6. 待确认事项

1. 旗舰工艺是否确认瑞昌竹编，夏布绣作为第二旗舰？
2. 真实模型优先 DeepSeek 还是本地 Ollama？视觉模型是否 Q2 接？
3. 试点学校/心理中心/传承人合作是否已有候选？
4. Q1 是否先只做“Start → Studio → Culture Card → Journey”这一条，不并行全站视觉重设计？

> 在以上事项确认前，不开始业务代码实现；本文件只作为年度路线与 Q1 技术基线。
