# 双对话收敛设计（2026-11-04）

> **性质声明：本文是纯设计文档，不含任何代码改动，未跑构建，未 commit/push。**
> 所有「现状」结论均来自 2026-11-04 对仓库文件的实际读取，逐条附文件与行号证据。
> 架构方向已由用户拍板（单一对话主线 + 指挥官调度层 + 近期原文/滚动摘要混合上下文），
> 本文只做落地设计，不另起方案。
> 上游文档：[ai-intelligence-plan-20261103.md](file:///d:/develop/Zhi_Yu/docs/ai-intelligence-plan-20261103.md) §0-2、
> [eval-baseline-20261104.md](file:///d:/develop/Zhi_Yu/docs/eval-baseline-20261104.md)、
> [doc-conflicts-20261103.md](file:///d:/develop/Zhi_Yu/docs/doc-conflicts-20261103.md) 冲突 #2。

---

## 决策已定（2026-11-04 用户拍板）

以下五项为**已拍板决策**，本文全部章节按此定稿，不再作为开放问题：

| # | 决策 | 状态 |
|---|---|---|
| ① | 统一入口形态：**新增 `POST /api/dialogue/chat`**；`/api/emotion/chat` 与 `/api/companion/sessions/{id}/message` 在兼容期内改薄适配器 | 已定 |
| ② | 路由依据：**规则 + 入口上下文**；模型分类器**不进上线路径**，仅可作离线评测候选，未来若要上线须重新拍板 | 已定 |
| ③ | companion 消息**补落库** `emotion_message`（加密）；tool/citation 事件仍只进 `ai_trace` | 已定 |
| ④ | 滚动摘要用 **standard 档 + 摘要忠实性评测门禁**；掉分再按 `fallback_chain` 升 strong | 已定 |
| ⑤ | 注销清理缺口（`AccountLifecycle.erase` 未覆盖 `user_ai_memory`/`practice_session`）**立即修，不等 P1 其余工作**；执行方式：**单独先行、由用户另行派发**（避免与当前后端 work/导出审计线并行改后端），本设计线不动代码 | 已定 |

各决策的理由保留在 §8 备查。

---

## 0. 结论先行

| 议题 | 结论（一句话） | 详见 |
|---|---|---|
| 调度层边界 | 新建 `DialogueCommander`：**只做「输入安检 → 路由 → 调用子能力 → 汇总出口安检语义」四件事**，不生成文本、不持久化消息、不持有业务状态；子能力（倾听/陪伴/知识/练习）互不调用，跨能力转交必须回到调度层 | §2 |
| 上下文策略 | **最近 10 轮原文**（维持现状，安全过滤准确性优先）+ **第 11 轮起生成滚动摘要**（standard 档、评测门禁、加密存储、90 天滚动过期、随注销清理）；摘要可审计、可随撤回/注销清除 | §3 |
| 会话模型 | 以 `emotion_session` / `emotion_message` 为统一主线（它是唯一有消息落库的链路），加 `kind` 字段区分能力域；`practice_session.emotion_session_id` 早已是现成外键（schema.sql L550），迁移是**加列不是搬表**，可回滚 | §4 |
| 旧接口 | `/api/emotion/chat` 协议（chunk/revoke/done）在兼容期内**零破坏**；新统一入口 `/api/dialogue/chat` 正名，旧端点改薄适配器，**2 个迭代且评测平价后**才评审退役 | §4.3 |
| 分期 | 5 期（P0 门禁与评测用例 → P1 上下文+清理缺口 → P2 调度层接管倾听 → P3 companion 并入 → P4 退役清理），每期独立开关、独立回退 | §7 |
| 决策 | 5 项已于 2026-11-04 全部拍板（见上文「决策已定」；理由备查在 §8） | §8 |

---

## 1. 现状盘点

### 1.1 两条链路逐项对比

| 维度 | emotion 倾听链 | companion 陪伴编排链 | 证据 |
|---|---|---|---|
| 入口 | `POST /api/emotion/chat`（SSE，120s） | `POST /api/companion/sessions/{id}/message`（SSE，120s） | EmotionController.java L62；CompanionMessageController.java L49 |
| 会话表 | `emotion_session`（mood/summary 加密/status 0-1） | `practice_session`（kind/status 8 态/context_cipher 加密，**不存原始对话**） | schema.sql L90-101、L541-563 |
| 消息落库 | `emotion_message`（role=user/assistant，content 加密，每轮双方落库） | **无消息落库**，对话即弃；只有 `ai_trace` 元数据（不存原文） | schema.sql L103-113；CompanionOrchestrator.java 全文无消息写入；schema.sql L582-602 |
| 会话模型互通 | 不互通：practice_session 有 `emotion_session_id` 外键但当前代码未在对话时建立关联 | 同左 | schema.sql L550；两控制器互不引用 |
| SSE 协议 | 旧模式：无名 data 整段 + done；**live 模式：chunk（句级）/revoke（整体替换）/done{sessionId,crisis,revoked}/error** | typed 事件：**stage/message{delta,generated}/tool/citation/done{stage,generated,blocked,toolUsed}/error** | EmotionController.java L142-184、L192-204；CompanionEvent.java L14-20 |
| 流式粒度 | live=句级真流式（SentenceSplitter，句级安检，高危即时中断） | **非流式**：编排完成后一次性发事件序列 | AiClient.java L230+；CompanionMessageController.java L67-71 |
| LLM 调用 | `AiClient.streamChatLive`（流式） | `AiClient.companionPlan`（非流式 JSON 计划） | AiClient.java L230、L516 |
| 熔断资源 key | `llm-chat` | `llm-message` | AiClient.java L171/L239、L341/L525 |
| 安全过滤 | 输入前置（BLOCK 不调 LLM）+ 句级检 + 全文终检 + revoke | 输入前置 + 输出终检（BLOCK→关怀话术；REWRITE→改写文本替换 reply 并丢弃工具） | AiClient.java L162-192/L230+；AiClient.java L535-547 |
| 高危处理 | 过滤器 BLOCK → 预置关怀话术（含热线）+ 写 care_reminder（脱敏）+ done(crisis=true)，**全程不调 LLM** | 同一过滤器同一路径（scene=companion），编排层收到 blocked 后返回关怀话术 simpleTurn | KeywordContentSafetyFilter.java L77-79/L126；AiClient.java L519-521；CompanionOrchestrator.java L97-108 |
| 降级路径 | 熔断/异常 → SSE `event:error`，前端降级缓存图文 | plan 缺失/越界/工具失败/熔断 → **确定性 fallback 话术**（按练习状态分支），trace 记 fallback_reason | EmotionController.java L125-134；CompanionOrchestrator.java L213-255 |
| 历史注入 | 最近 10 轮解密注入（调用点硬编码 10，yaml `max_history_turns: 10`） | **无历史注入**，只有记忆偏好摘要 + 当前练习状态 | EmotionController.java L83；ai-capabilities.yaml L28；CompanionOrchestrator.java L157-174 |
| 记忆 | 无（仅 emotion_session.summary 自动截前 100 字） | `user_ai_memory`：3 个白名单 key、consent 门、AES、TTL、deleteAll 可撤回 | CompanionMemoryService.java L38/L86-153；schema.sql L566-580 |
| 工具 | 无 | 3 个白名单工具（recommendPractice/suggestBreathing/searchHeritage），按练习状态硬白名单 | CompanionPolicy.java L14-23 |
| 可观测 | `ai_audit_log`（加密，只追加） | `ai_audit_log` + `ai_trace`（scene/prompt_version/tool/latency/tokens/status） | CompanionTraceService.java L26-44 |
| 会话收尾产物 | 倾听小结（授权 + 已结束 + 无 care_reminder 才生成，加密存 wellbeing_record，strong 档） | 无 | ListeningSummaryService.java L59-93/L95-124；ai-capabilities.yaml L112 |
| 前端调用点 | stores/emotion.js（**已发 mode:'live'**，兼容旧协议 raw 兜底）、pages/emotion/chat.vue、history.vue | components/CompanionPanel.vue、pages/settings/memory.vue | emotion.js L190-231；CompanionPanel.vue L181 |
| Gherkin | 02-emotion-listening、43-sse-live-stream、18-emotion-note | 28-companion-tools、29-companion-message、32-companion-memory | features/ 目录 |

### 1.2 只有一边有的能力（收敛后**不能丢**清单）

| 能力 | 仅在 | 收敛落点 |
|---|---|---|
| 句级真流式 + revoke 撤回 | emotion | 成为统一主线协议基础（§4.3） |
| 消息持久化 + 历史注入 + 会话列表/删除/恢复 | emotion | 统一会话模型直接继承（§4.1） |
| 倾听小结（wellbeing_record，授权+收尾后） | emotion | 保持独立端点，不搬进调度层 |
| 工具调用框架 + 白名单策略 + citations | companion | 作为「练习陪伴」子能力整体保留 |
| 偏好记忆（consent/TTL/可撤回） | companion | 升格为全局记忆服务，供所有子能力注入 |
| ai_trace 运行元数据 | companion | 升格为调度层统一 trace（scene 扩展） |
| 评测集 + EvalRunner（80 条五层） | companion | 收敛前后的对照评测基础设施（§6） |

### 1.3 已确认的策略漂移点（收敛要解决的真问题）

| # | 漂移 | 现状证据 | 风险 |
|---|---|---|---|
| D1 | 降级语义不一致：emotion 熔断→`event:error` 推给前端；companion 熔断→静默走确定性话术（用户无感知，只有 trace） | EmotionController.java L125-134 vs CompanionOrchestrator.java L117-125 | 同一用户在不同页面遇到 AI 故障得到完全不同的反馈形态 |
| D2 | 回复载体不一致：emotion 是流式逐句；companion 是整段 message 事件 | §1.1「流式粒度」行 | 前端两套渲染与中断逻辑，小织表现不统一 |
| D3 | companion 对话无落库：用户无法在「练习里的对话」与「倾听历史」之间看到连续记录 | §1.1「消息落库」行 | 历史断裂；倾听小结也读不到练习期发言 |
| D4 | 历史窗口「配置与实际分离」：yaml 写 `max_history_turns: 10`，调用点硬编码 `10` | EmotionController.java L83 | 改配置不生效，属隐患 |
| D5 | **注销清理缺口**：`AccountLifecycle.erase` 清 emotion_*、wellbeing_record 等 20+ 处，但**不含 `user_ai_memory` 与 `practice_session`** | AccountLifecycle.java L29-54 全文 | 隐私红线：注销后偏好记忆与练习会话残留 |
| D6 | 危机话术双份硬编码：`AiClient.SAFE_CHAT_REPLY` 与 `CompanionOrchestrator.SAFE_SUPPORT` 措辞不同 | AiClient.java L116-117；CompanionOrchestrator.java L43-44 | 高危时刻话术漂移，红线层评测需分别维护 |

---

## 2. 调度层设计

### 2.1 职责边界

新建 `com.zhiyu.modules.dialogue.DialogueCommander`（新模块 `modules/dialogue`，避免塞进已有 18 个模块中的任何一个造成二次耦合；companion/emotion 改造为其子能力）。

| 做 | 不做 |
|---|---|
| ① 入口输入安检：在任何路由决策**之前**直接调 `ContentSafetyFilter.checkInput`（非 AI 调用，符合 AGENTS §4 例外）；BLOCK → 预置危机话术 + care_reminder + SSE 危机事件，**LLM 与编排完全不启动** | 不生成任何面向用户的文本（子能力负责生成；危机话术是预置文件不是生成） |
| ② 路由决策：按 §2.3 规则决定本轮交给哪个子能力 | 不做业务读写（会话/消息/练习状态全在子能力各自服务里） |
| ③ 调用子能力并转写出口：把子能力产出适配成统一 SSE 事件序列 | 不持久化消息（落库仍在统一会话服务，见 §4） |
| ④ 防循环三道闸的执行与记账（§2.4），全程写 `ai_trace`（scene=dialogue-route） | 不持有跨请求业务状态（闸口状态放 Redis/内存，随 `zhiyu.flow.store` 模式） |
| ⑤ 每一档失败显式降级 + trace + WARN 日志（§2.5），禁止静默 | 不绕过 `AiClient` 门面；自身不产生新的 LLM 调用点，因此**不需要新熔断资源 key**（子能力沿用 llm-chat/llm-message） |

**危机红线在调度层的落法**：checkInput 命中高危 ⇒ 直接返回预置干预话术，跳过路由、跳过子能力、跳过 LLM——与现状两条链路的过滤器 BLOCK 路径语义完全一致（§1.1 高危行），只是把「同一个动作」收敛到调度层入口，消除 D6 双份话术。

### 2.2 输入输出契约（设计稿，不修改 openapi）

```text
输入（调度层内部对象，不是新 REST 契约）：
  DialogueCommand {
    userId, sessionId?,          // 会话为空时由倾听子能力创建（现状语义）
    text,                        // ≤500 字（对齐 CompanionOrchestrator.MAX_USER_TEXT）
    entryContext,                // EMOTION_PAGE | PRACTICE_PAGE | KNOWLEDGE_PAGE | OTHER
    practiceSessionId?,          // 练习页入口必带
    clientProtocol               // "live"（句级）——旧协议只出现在适配器层
  }

输出：
  DialogueOutcome {
    capability,                  // LISTENING | PRACTICE_COMPANION | KNOWLEDGE | CRISIS | FALLBACK
    events: List<CompanionEvent 风格事件>,  // 复用 stage/message/tool/citation/done/error 词汇表
    traceId
  }
```

要点：出口事件词汇表**以 companion 的 typed 事件为骨架**（它已含 stage/tool/citation），再并入 emotion live 协议的 **chunk/revoke**（流式与安全撤回是红线设施，不能丢）。统一后 done 载荷为 `{sessionId, capability, stage?, crisis, revoked, toolUsed}`——全部可选字段向后兼容（§4.3）。

### 2.3 路由决策依据：**规则优先**，模型判定只做离线候选

| 方案 | 延迟成本 | 资金成本 | 正确性 | 可审计性 | 结论 |
|---|---|---|---|---|---|
| A. 规则 + 入口上下文 | ≈0ms | 0 | 高：entryContext 是强先验（练习页进来的就是要练） | 每条路由可在 ai_trace 写规则命中项 | **采用** |
| B. 模型分类器前置 | +1 次 LLM 调用/轮；首字时间最坏翻倍（NFR-02 首字 ≤2s 直接承压） | 对话量 ×2 | 分类本身会错，错了还要兜底回规则 | 黑盒，需额外评测 | 仅离线评测候选，不上线 |

**规则表（初版，落在 `ai-config/ai-capabilities.yaml` 新增 `dialogue.routing_rules` 段，可热改）：**

| 优先级 | 条件 | 路由到 |
|---|---|---|
| 0 | 输入安检 BLOCK | CRISIS（预置话术，不进 LLM） |
| 1 | entryContext=PRACTICE_PAGE 且 practiceSession 非终态 | PRACTICE_COMPANION（现 CompanionOrchestrator 逻辑原样迁入） |
| 2 | entryContext=KNOWLEDGE_PAGE | KNOWLEDGE（现 RagService/KnowledgeAskService 链） |
| 3 | 文本命中显式指令（用户点快捷动作，如「推荐手作」「我想静静呼吸」→ 前端随请求带 `intentHint`） | 对应子能力 |
| 4 | 以上皆不满足 | LISTENING（情绪倾听，**最安全默认**：无工具、无执行副作用） |

取舍说明：自由文本跨域误判（在倾听页问非遗知识）不做「自动转交」，由倾听子能力在系统提示词内保持既有行为；v1 不为边缘场景付分类成本。**模型分类器不进上线路径（决策②）**：规则命中与覆盖情况全部进 ai_trace；若未来要重新评估模型分类，须先以离线评测证明规则误判率超过约定阈值，再单独拍板，本设计不预留线上开关。

### 2.4 防循环三道闸

现状 companion 是**单跳**（无循环可能），循环风险是引入调度层后才出现的，因此三道闸是新增设施：

| 闸 | 机制 | 参数（配置化，进 yaml `dialogue.guards`） | 触发后行为（显式，不静默） |
|---|---|---|---|
| ① 深度上限 | 每轮用户输入：路由 1 次 + 子能力执行 1 次，**子能力返回值不进入二次路由**；子能力间无互调接口（编译期物理隔离：子能力包不互相依赖） | `max_route_depth: 1` | 结构上不可达；防御性代码在深度>1 时抛 IllegalState 并记 ERROR |
| ② 重复意图检测 | 规范化文本哈希（去标点/空白/大小写）进 Redis 滑窗；同哈希 ≥3 次/10 分钟 ⇒ 锁定当前子能力；路由在 3 轮内出现 A→B→A 振荡 ⇒ 本轮强制留在 A | `repeat_intent_times: 3`、`repeat_window_seconds: 600`、`oscillation_lock: true` | 返回确定性安抚话术 + 前端提示「我们先把这件事做完」；trace 记 `fallback_reason=REPEAT_INTENT_LOCK` |
| ③ 预算/超时 | 每会话 LLM 轮次预算（默认 30 轮/会话，可配）；单轮调度层墙钟预算 30s（SSE 总超时 120s 内）；熔断沿用现有 5 资源 key，不新增、不共享 | `session_turn_budget: 30`、`turn_wall_clock_ms: 30000` | 预算耗尽 → 确定性收尾话术 + done；trace 记 `BUDGET_EXHAUSTED` / `TURN_TIMEOUT` |

### 2.5 失败与降级路径矩阵（每一档显式行为）

| 失败点 | 行为 | 用户可见 | 记录 |
|---|---|---|---|
| 输入安检 BLOCK（危机） | 预置干预话术，不调 LLM | chunk(关怀话术) + done(crisis=true) | care_reminder + ai_audit_log + trace(ROUTE_CRISIS) |
| 规则引擎自身异常 | 默认路由 LISTENING | 正常对话（用户无感） | trace(ROUTE_FALLBACK) + WARN 日志 |
| 子能力 LLM 熔断（llm-chat/llm-message） | 沿用现有：倾听→event:error 由前端降级缓存图文；陪伴→确定性话术 | 各子能力保持现状语义 | breaker 计数 + trace |
| 子能力执行异常 | 确定性兜底话术（倾听用缓存图文话术；陪伴用现 fallback 分支话术） | chunk(兜底) + done | trace(CAPABILITY_FAILED) + ERROR |
| 闸②/③触发 | 见 §2.4 | 确定性话术 + done | trace(对应 reason) |
| 全文终检非 PASS | 沿用 live 协议 revoke 机制 | revoke + done(revoked=true) | ai_audit_log(scene=chat-revoked) |

D1 漂移的处置：统一后**两类降级语义都保留但分工明确**——「流式内容失败」走 event:error+前端降级（用户主动等待中，需要知道断了）；「编排/工具失败」走确定性话术（用户无感是因为话术本身可用）。差异写进契约描述，不再各写各的。

---

## 3. 上下文策略

### 3.1 近期原文窗口

| 决策 | 依据 |
|---|---|
| 窗口 = **最近 10 轮原文**，维持现状不变 | ① 安全过滤准确性：高危情绪常是多轮逐步升级（「最近总睡不着」×3 轮后才现自伤措辞），原文窗口是过滤器与人工追溯的证据链，摘要会丢失升级轨迹；② 成本可算：10 轮 ≈ 10×(500+300) 字 ≈ 4–6k tokens 上限，在 deepseek-chat 上下文与 20s 超时预算内（现状已验证）；③ 与 yaml `max_history_turns: 10` 对齐 |
| 顺手修 D4：窗口值改为读取配置，删除调用点硬编码 10 | EmotionController.java L83 |

### 3.2 滚动摘要

| 项 | 设计 |
|---|---|
| 触发时机 | 会话第 11 轮起：每新增 5 轮，把「已滑出原文窗口的最旧 5 轮」压缩进摘要（增量滚动，不是每次全量重算）；会话 END 时强制收尾一次 |
| 生成方式 | 走 `AiClient` 新场景方法 `contextSummary`（注册到既有 key `llm-message`，不新增熔断 key）；prompt 外置 `prompts/context-summary.st`（新建）；模型档 **standard**（拍板点 4，评测门禁见 §6） |
| 摘要性质 | **内部上下文，不对用户展示**（与用户可读的「倾听小结」是两条线，后者不动）；禁存原句、禁存可识别信息（对齐 `care_reminder.summary` 的脱敏语义），prompt 中硬性约束 |
| 注入方式 | system 段注入：`滚动摘要 + 最近 10 轮原文`；摘要超 300 字截断 |
| 可审计 | 每份摘要记录 model/prompt_version/覆盖到的 message_id 区间，随 ai_trace 可查 |
| 是否过评测 | **必须**：新增摘要忠实性用例（摘要不许编造用户没说过的状态、不许出现诊断措辞）进 L3 层，见 §6 |

### 3.3 存储与清理

| 项 | 设计 |
|---|---|
| 新表 `dialogue_context_summary`（DDL 进 schema.sql + migrations 增量脚本，符合 §5.1） | `id, session_id, summary_cipher(AES), covers_until_message_id, model, prompt_version, expires_at(+90 天滚动), create_time, update_time, deleted` |
| 会话删除 | emotion 会话删除端点（DELETE /api/emotion/sessions/{id}）级联逻辑删除摘要 |
| 撤回授权 | memory consent=false ⇒ `promptSummary` 返回空（现状）；滚动摘要属会话连续性而非偏好记忆，但**仍加密 + 可删**，隐私政策口径按「对话内容」对待 |
| 注销 | `AccountLifecycle.erase` **必须补三张表**：`user_ai_memory`、`practice_session`（现状缺口 D5，决策⑤：P1a 单独先行修复）；`dialogue_context_summary` 的 erase 条目随 P1b 建表同批交付 |
| ai_trace / ai_audit_log | 维持只追加不清除（审计豁免，现状注释已声明） |

> **AH2 机器化复检（2026-11-04，AF2 实测删除 500 后追加）**：以 `sql/schema.sql` 全部 **48 张表**为基准与 erase/suspend 机器比对（脚本 `.ai-tmp/ah2-coverage.mjs`，守卫测试 `AccountEraseCoverageTest.noSilentMissOfNewTables`）：
> - **已覆盖 35 张**（用户数据全量）。
> - **保留 13 张**（差集白名单，逐表理由）：审计只追加 `ai_audit_log`、`admin_operation_log`、`ai_trace`、`admin_export_log`；无用户归属列的公共内容/字典/配置 `dict_mood`、`dict_category`、`dict_therapy_tags`、`runtime_config`、`craft_material`、`craft_step`、`craft_safety`（项目内容库）、`course_lesson`（课程内容库）、`heritage_catalog_version`（目录版本快照，actor_id 为管理操作留痕）。
> - **差集 = 0**。AH1 的 500 不是漏表而是列值错误：`campus_verification.review_reason` 为 `NOT NULL DEFAULT ''`，erase 置 NULL 被 MySQL 拒绝（H2 旧测试未插行未触发），已修复为置空串并加回归。

### 3.4 多设备/多端一致性

- 会话状态**服务端唯一权威**：原文消息在 `emotion_message`、摘要在 `dialogue_context_summary`、闸口滑窗在 Redis（与 `zhiyu.flow.store` 配置一致，memory 档仅单实例开发用）。
- 双端（H5/小程序）同时在线：不发锁、不做冲突合并——后到的输入按到达顺序追加，SSE 断线重连从落库消息重建界面（现状 emotion 已如此）。
- 客户端**禁止**本地拼装上下文发回服务端（防篡改 + 防上下文膨胀），请求只带当轮 text + sessionId（现状契约不变）。

---

## 4. 会话模型收敛

### 4.1 目标模型

**以 `emotion_session` / `emotion_message` 为统一主线**（理由：唯一有消息落库、有历史注入、有会话生命周期端点的链路；companion 侧无消息资产需要保护）。

| 改动 | 内容 |
|---|---|
| `emotion_session` 加列 | `kind VARCHAR(20) NOT NULL DEFAULT 'EMOTION'`（EMOTION / PRACTICE / KNOWLEDGE）；`source_context VARCHAR(30) NULL`（入口上下文） |
| 关联 | 沿用现成外键 `practice_session.emotion_session_id`（schema.sql L550）：练习页发起对话时创建/复用一条 kind=PRACTICE 的会话并回填外键 |
| `emotion_message` | 结构不变；companion 的每轮 user/assistant 文本开始落库（拍板点 3）；tool/citation 事件**不作为消息落库**，仍进 ai_trace |
| `practice_session` | 保留为练习域实体（状态机/步骤/预算），不搬不改语义 |
| 不变 | user_ai_memory、ai_trace、wellbeing_record（倾听小结）全部保持 |

### 4.2 历史数据迁移策略（必须可回滚）

| 步骤 | 内容 | 回滚 |
|---|---|---|
| M1 | 增量迁移脚本（随 **P3** 执行）`sql/migrations/<日期>_dialogue_unify.sql`：`ALTER TABLE emotion_session ADD kind/…`（IF NOT EXISTS 幂等模式对齐既有迁移）；**不搬数据**——companion 历史消息本来就不存在，无数据可搬。`dialogue_context_summary` 建表迁移随 **P1b** 先行交付，两者互不等待 | 回滚脚本 drop 新增列（列可空、无索引依赖，安全） |
| M2 | 既有 `practice_session` 行不动；外键回填仅限「迁移后新发生的练习对话」，不做存量批量回填（无对话内容可关联，回填无收益） | 无需回滚 |
| M3 | 迁移纪律：先备份；测试库验证首次执行与重复执行；H2 测试结构与 SchemaCompatibilityCheck 同步核对（AGENTS §5.1） | — |

### 4.3 旧接口兼容期与退役条件（协议不能无声破坏）

| 端点 | 兼容期安排 | 退役条件（全部满足才评审退役） |
|---|---|---|
| `POST /api/emotion/chat` | **协议零破坏**：chunk/revoke/done 事件名与载荷字段只增不改；live 模式新增事件类型（stage/tool/citation）对旧前端天然安全——sse.js 按事件名分发，未知名称不进任何 handler（emotion.js L208-230 证据） | ① H5 + 小程序双端发统一入口版本 ≥2 个迭代；② §6 评测平价达成；③ ai_trace 中旧入口流量占比 <5% |
| 旧非 live 整段模式 | 保留一个迭代周期（现状 doc-conflicts #2 已声明）；前端 raw 兜底逻辑同步保留 | 双端 live 全量后，随 P4 删除旧分支与前端 raw 兜底 |
| `POST /api/companion/sessions/{id}/message` | 改为薄适配器：内部转调度层，事件序列保持 stage/message/tool/citation/done 不变 | CompanionPanel 切统一入口满 2 个迭代；features 28/29/32 全绿 |
| 退役动作本身 | 先契约标注 `deprecated: true` + sunset 说明，一个迭代后才删代码；删除当期必须有前端发版配合 | — |

---

## 5. 契约影响清单（只列不改 openapi.yaml）

| # | 端点 | 变更 |
|---|---|---|
| C1 | `POST /api/dialogue/chat`（**新增**） | 统一入口，SSE；请求 `{sessionId?, text, entryContext, practiceSessionId?, intentHint?}`；事件 = chunk/revoke/stage/message/tool/citation/done/error；done 载荷 `{sessionId, capability, stage?, crisis, revoked, toolUsed}` |
| C2 | `POST /api/emotion/chat` | 标注 `deprecated: true`（兼容期）；description 注明「内部转调度层，协议不变」 |
| C3 | `POST /api/companion/sessions/{id}/message` | 标注 `deprecated: true`；注明适配器语义 |
| C4 | `GET /api/emotion/sessions` / `GET /api/emotion/sessions/{id}` | 响应加 `kind` 字段（统一历史列表按能力域筛选用） |
| C5 | `PUT /api/emotion/sessions/{id}/state` | 不变（END/RESUME 语义沿用） |
| C6 | `/api/companion/memory*` 三个端点 | 不变 |
| C7 | 倾听小结三个端点（summary/generate 等） | 不变 |
| C8 | 错误码 | 不新增；SSE 故障继续走 `event:error`（AGENTS §3 注），危机不新增 code |

---

## 6. 评测与验收

### 6.1 对照评测设计（全部复用既有设施）

- 运行器：`EvalRunnerEngine` + `CompanionEvalMockTest`（CI）+ `CompanionEvalRealIT`（手动，需 DEEPSEEK_API_KEY）。
- 数据集：`eval/companion-eval.json`（80 条五层），收敛前以 **baseline-final（20261104-0809）为对照组**：L1 15/15、L2 22P/2S、L3 15/15、L4 Mock 10/10（真实 5P/5S）、L5 8/8。
- 新增用例（P0 期就先补进数据集，大部分对现系统即应通过）：
  - 路由层：危机输入 ⇒ 断言**零 LLM 调用**（ Mock 模式可断言）、care_reminder 落库、话术为预置文本；
  - 防循环：同哈希重复输入 ×3 ⇒ 锁定响应；A→B→A 振荡 ⇒ 强制留 A；
  - 摘要忠实性（≥5 条）：滚动摘要不含原会话未出现的「事实」、不含诊断措辞；
  - 协议平价：同一用例在 `/api/emotion/chat` 与 `/api/dialogue/chat` 的最终安全文本一致。

### 6.2 可验证通过标准

| 指标 | 阈值 | 判定方式 |
|---|---|---|
| L1 红线层 | **15/15，一票否决，零回退**（含新增危机绕 LLM 用例） | 每期 CI + 真实模式各一遍 |
| L2 检索质量 | ≥22P（不掉于基线）；INSUFFICIENT 拒答率 100% | 真实模式 |
| L3 对话质量 | ≥15/15，且新增摘要忠实性用例全过 | 真实模式 |
| L4 降级路径 | Mock 10/10；新增闸口用例全过 | Mock CI |
| L5 注入防御 | 8/8 | 真实模式 |
| SSE 契约 | features/43-sse-live-stream.feature 全绿；新旧入口双跑 | mvn test |
| 性能 | 首字时间 ≤2s（NFR-02）不因路由层劣化 >10%（路由是规则，预期劣化≈0） | 复用既有压测口径抽测 |
| 漂移消除 | D1–D6 逐项有关闭证据（代码位置或评测用例） | 评审核对 |

### 6.3 回滚判定

触发任一即回滚当期：① L1 任何一条 FAIL；② L3 连续两次跑分 <15/15；③ SSE 契约测试破裂；④ 危机路径出现 LLM 调用记录（ai_audit_log scene 审计）。回滚方式见 §7 每期开关。

---

## 7. 实施分期（5 期，每期独立可回退）

| 期 | 改什么 | 如何验证 | 失败如何退 |
|---|---|---|---|
| **P0 门禁与用例** | 补评测用例（§6.1）；yaml 增加 `dialogue.*` 配置骨架（全部默认关闭）；本设计评审 | 新用例对现系统跑一遍，该过的全过；CI 绿 | 纯增量，删配置段即可 |
| **P1a 注销清理缺口（独立先行，决策⑤，由用户另行派发，不在本设计线执行）** | `AccountLifecycle.erase` 补 `user_ai_memory`/`practice_session` 两张表的现状缺口；补 erase 覆盖测试缺口 | erase 后两表无残留的测试断言转绿；features 16-user-privacy 全绿；全量 mvn test | 红线修复，不退 |
| **P1b 上下文服务 + 滚动摘要（仅倾听链）** | `dialogue_context_summary` 建表 + 迁移脚本 + erase 条目；`DialogueContextService`（原文窗口读配置 + 滚动摘要）接入倾听链；修 D4 | 迁移首次/重复执行验证；features 02/25 全绿；摘要忠实性用例过；注销冒烟（erase 后摘要表无残留） | `zhiyu.dialogue.rolling-summary=false` 关摘要；迁移回滚脚本 drop 新表 |
| **P2 调度层接管倾听** | 新建 `modules/dialogue`：DialogueCommander + 规则路由 + 三道闸；倾听作为第一个子能力迁入；`/api/emotion/chat` 内部转调度层（协议不变）；统一危机话术（D6） | SSE 契约双入口双跑；§6.2 全表；首字时间抽测 | `zhiyu.dialogue.commander=false` 恢复直连旧路径 |
| **P3 companion 并入** | 练习陪伴整体迁入为子能力（Orchestrator 逻辑原样）；companion 消息开始落库 emotion_message（D3）；练习会话回填 emotion_session_id；`/api/companion/sessions/{id}/message` 改薄适配器；统一降级语义表述（D1）；记忆服务升格全局（D2 渲染链统一） | features 28/29/32 全绿；落库后历史连续可见；trace scene 覆盖 dialogue-route | `zhiyu.dialogue.companion-adapter=false` 恢复旧控制器直调编排器 |
| **P4 退役与清理** | 删旧非 live 分支与前端 raw 兜底；评审退役两个旧端点；docx/docs 冲突清单刷新（doc-conflicts #2 收口）；评测报告归档 docs/eval-runs/ | §4.3 退役条件全部满足 | 本期只做删除，前置期全部稳定才进入 |

---

## 8. 决策记录（2026-11-04 全部拍板，理由保留备查）

| # | 问题 | 结论（=原默认建议，全部采纳） | 状态 | 理由（备查） |
|---|---|---|---|---|
| 1 | 统一入口形态：A 新增 `/api/dialogue/chat` + 旧端点改适配器，还是 B 直接扩展 `/api/emotion/chat`？ | **A** | 已定 | 语义正名（倾听只是能力之一）；旧端点协议零破坏；退役节奏可控（§4.3）。代价是多一个端点进契约 |
| 2 | 路由是否需要模型判定？ | 不需要，**规则 + 入口上下文**（§2.3）；模型分类器不进上线路径，仅作离线评测候选 | 已定 | 分类器使每轮多一次 LLM 调用，首字时间与成本双压，当前日调用量不值得 |
| 3 | companion 消息是否补落库？ | **补**：写入 emotion_message（加密），工具/引用事件仍只进 ai_trace | 已定 | 不落库则 D3 永存：历史断裂、倾听小结读不到练习发言、统一会话名不副实；代价是加密存储面扩大（已被 erase 链覆盖） |
| 4 | 滚动摘要模型档位 | **standard 档 + 摘要忠实性评测门禁**；若忠实性用例连续掉分再升 strong（fallback_chain 现成） | 已定 | 摘要是内部上下文非用户可见产物；strong 档成本与延迟更高，先用评测证明标准档够用 |
| 5 | 注销清理缺口 D5（user_ai_memory/practice_session 不在 erase 链）何时修？ | **立即修**：单独先行、由用户另行派发（当前后端另有 work/导出审计线，避免两线并行改后端），先于 P1b 及以后各期 | 已定 | 隐私红线项，与收敛无关也成立；越晚修残留越久 |

**截至 2026-11-04 无新增待拍板事项。**

---

## 9. P0/P1 施工清单（文件级）

> 验收命令中的 `"-Dmaven.repo.local=../.ai-tmp/m2"` 为 AGENTS §10 沙箱约束，不可省略；
> 编译/测试前先确认并停止本任务自启的后端进程，不动用户启动的进程。

### 9.1 P0 门禁与用例（纯增量，无运行时行为变化）

| # | 事项 | 文件（改/新增） | 依赖顺序 | 验收命令 | 回退方式 |
|---|---|---|---|---|---|
| P0-1 | 补评测用例：路由层（危机零 LLM 调用）、防循环（重复意图/振荡锁定）、摘要忠实性、协议平价 | `zhiyu-backend/src/main/resources/eval/companion-eval.json`（version 升级并留档）；如需新字段则同步 `CompanionEvalCatalog.java`。依赖未交付功能的用例按既有 SKIPPED 类别登记，随对应期次激活 | 无前置 | `mvn test "-Dmaven.repo.local=../.ai-tmp/m2" "-Dtest=CompanionEvalMockTest,CompanionEvalCatalogTest"` | 删新增用例 |
| P0-2 | `dialogue.*` 配置骨架（routing_rules / guards / rolling_summary，全部默认关闭） | `ai-config/ai-capabilities.yaml`；`zhiyu-backend/src/main/resources/application.yml` 与 `application-test.yml` 的 `zhiyu.dialogue.*` 开关默认值 | 无前置 | 全量 `mvn test "-Dmaven.repo.local=../.ai-tmp/m2"` | 删配置段 |
| P0-3 | 契约登记（契约先行，不改代码行为）：C1 新端点登记 + C2/C3 deprecated 标注 | `openapi/openapi.yaml`（改后解析 YAML 校验路径与 operationId） | 无前置 | YAML 解析通过 | 删登记条目 |
| P0-4 | Gherkin 场景：路由、防循环、危机绕过、摘要忠实性 | `features/48-dialogue-routing.feature`（下一个可用编号为 48） | 无前置 | 随对应期次转绿 | 删文件 |

### 9.2 P1a 注销清理缺口（独立先行，决策⑤；由用户另行派发，本设计线不动代码）

| # | 事项 | 文件（改/新增） | 依赖顺序 | 验收命令 | 回退方式 |
|---|---|---|---|---|---|
| P1a-1 | `erase` 补两张表，建议语义：`user_ai_memory` → `value_cipher=''` + `deleted=1`；`practice_session` → `context_cipher=NULL` + `deleted=1`（终稿措辞由执行线定） | `zhiyu-backend/src/main/java/com/zhiyu/modules/auth/service/AccountLifecycle.java`（erase 方法，现 L29-54） | **无前置，最先做** | 见 P1a-2 | 红线修复，不退 |
| P1a-2 | 补既有测试缺口：seed 两表 → erase → 断言 `deleted=1` 且密文字段清空 | `zhiyu-backend/src/test/java/com/zhiyu/modules/FinalBusinessTest.java`（扩展现有用例或新增）；`features/16-user-privacy.feature` 补场景 | 随 P1a-1 同批 | `mvn test "-Dmaven.repo.local=../.ai-tmp/m2" "-Dtest=FinalBusinessTest"` + 全量 mvn test | — |

**既有测试缺口证据**：现 erase 唯一覆盖点 [FinalBusinessTest.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/test/java/com/zhiyu/modules/FinalBusinessTest.java#L30) `eraseClearsMessagesQueuesMediaAndTransfersLeadership` 只断言 `guidance_message`/`file_cleanup_job`/组长移交/`user.status`，**没有 `user_ai_memory` 与 `practice_session` 的任何断言**——缺口与代码缺口一一对应。

### 9.3 P1b 上下文服务 + 滚动摘要（仅倾听链）

严格依赖顺序（1→8）：

| 序 | 事项 | 文件（改/新增） | 验收命令 | 回退方式 |
|---|---|---|---|---|
| 1 | 契约核对：本期无 REST 契约变更（摘要注入与级联删除不体现在接口签名上）；仅确认 C 清单无需提前 | — | — | — |
| 2 | DDL 四件套：新表 `dialogue_context_summary` | `sql/schema.sql` + `sql/schema-h2.sql` + `zhiyu-backend/src/test/resources/schema-h2.sql`（两份 H2 必须同步）+ `sql/migrations/<日期>_dialogue_context_summary.sql`（幂等增量 + 回滚脚本；先备份，测试库验证首次与重复执行） | 迁移验证 + `mvn test "-Dmaven.repo.local=../.ai-tmp/m2" "-Dtest=SchemaCompatibilityCheckTest"` | 回滚脚本 drop 新表 |
| 3 | 启动结构核对加新表必检列 | `zhiyu-backend/src/main/java/com/zhiyu/config/SchemaCompatibilityCheck.java` + `SchemaCompatibilityCheckTest.java` | 同上 | 随序 2 回退 |
| 4 | `AiClient` 新增 `contextSummary` 场景方法（熔断 key=`llm-message`，不新增 key）；prompt 外置且显式 UTF-8 读取 | `zhiyu-backend/src/main/java/com/zhiyu/ai/AiClient.java`；新增 `zhiyu-backend/src/main/resources/prompts/context-summary.st`；`ai-config/ai-capabilities.yaml` 的 `routing.tasks` 加 `context_summary: standard` | 全量 mvn test | 删方法与模板 |
| 5 | 新包落位 `modules/dialogue/`：`DialogueContextService`（原文窗口读配置 + 摘要生成/注入/90 天滚动过期）；P2 再放 Commander | 新增 `zhiyu-backend/src/main/java/com/zhiyu/modules/dialogue/DialogueContextService.java` | 见序 8 | `zhiyu.dialogue.rolling-summary=false` |
| 6 | 倾听链接入：历史窗口改读配置（修 D4）；第 11 轮起触发滚动摘要；会话删除级联逻辑删摘要 | `EmotionController.java`（现 L83 硬编码 10）、`EmotionServiceImpl.java` | `mvn test ... "-Dtest=EmotionServiceTest"` + features 02/25 场景 | 同序 5 开关 |
| 7 | `AccountLifecycle.erase` 补 `dialogue_context_summary` 条目 | `AccountLifecycle.java`——**必须在 P1a 合入后再动手**（两线先后触碰同一文件，防冲突） | `mvn test ... "-Dtest=FinalBusinessTest"` | 随序 2 回退 |
| 8 | 测试与评测：新增上下文服务单测；摘要忠实性用例（P0-1 登记）转激活；真实评测对照 | 新增 `zhiyu-backend/src/test/java/com/zhiyu/modules/dialogue/DialogueContextServiceTest.java`；跑 `CompanionEvalMockTest` + `CompanionEvalRealIT`（tag=unify-p1b，L1 15/15 一票否决，L3 不掉于基线） | 全量 mvn test + 评测报告落 docs/eval-runs/ | — |

---

## 10. 开工前置条件

| 类别 | 内容 | 解锁哪期 |
|---|---|---|
| **必须先有后端改动** | `dialogue_context_summary` 建表（序 9.3-2）——一切上下文工作的前提 | P1b |
| | `ai_trace` 的 `scene=dialogue-route` 记账（表已存在无需 DDL，`scene VARCHAR(30)` 容量够，仅新增取值与写入点） | P2 |
| | yaml `dialogue.*` 配置段（P0-2） | P1b/P2 |
| **纯文档（可立即做）** | openapi C1-C3 登记（P0-3）、features/48（P0-4）、本设计评审 | P0 |
| **纯前端** | P0/P1 **无任何前端改动**；最早前端改动在 P3（CompanionPanel 切统一入口）与 P4（删 emotion.js 的 raw 兜底） | — |
| **跨线约束** | `AccountLifecycle.java` 被 P1a 与 P1b 先后触碰 → P1b 序 7 必须在 P1a 合入后进行；当前后端另有 work/导出审计线在改，P1a 排期由用户控制 | P1a→P1b |

---

## 附：本文证据索引

- 控制器：EmotionController.java L62-204；CompanionMessageController.java L49-101
- 编排与策略：CompanionOrchestrator.java L71-302；CompanionPolicy.java L14-68；CompanionEvent.java L14-64
- 门面：AiClient.java L116-192（streamChat/SAFE_CHAT_REPLY）、L230+（streamChatLive）、L503-555（companionPlan）、L171/L239/L341/L525（熔断 key）
- 安全：KeywordContentSafetyFilter.java L77-79/L126（care_reminder）
- 记忆：CompanionMemoryService.java L38/L86-153
- 小结：ListeningSummaryService.java L59-145（wellbeing_record LISTENING_SUMMARY）
- 表：schema.sql L90-113（emotion_*）、L543-563（practice_session）、L566-580（user_ai_memory）、L583-602（ai_trace）
- 清理：AccountLifecycle.java L29-54（erase 清单，缺 user_ai_memory/practice_session）
- 配置：ai-capabilities.yaml L22-28（chat）、L71-88（resilience）、L90-118（routing，crisis: none）
- 契约：openapi.yaml L1119-1194（emotion sessions/summary）、L1966（chat）、L2525-2615（companion）、L4020（state）
- 前端：zhiyu-app/src/stores/emotion.js L190-231、components/CompanionPanel.vue L181、pages/settings/memory.vue
- 评测：eval-baseline-20261104.md（baseline-final 五层全绿）；features/02、43、28、29、32

---

## 附录：AK 批灰度与前端接入说明（2026-11-04）

### 开关模型（细粒度，全部默认关闭）

| 配置（application.yml / 环境变量） | 作用 | 默认 |
|---|---|---|
| zhiyu.dialogue.commander / ZHIYU_DIALOGUE_COMMANDER | C1 端点 /api/dialogue/chat 注册（物理入口） | false |
| zhiyu.dialogue.listening-via-commander / ZHIYU_DIALOGUE_LISTENING_COMMANDER | 倾听链 /api/emotion/chat 委托调度层（AK1 灰度主体） | false |
| zhiyu.dialogue.companion-adapter / ZHIYU_DIALOGUE_COMPANION_ADAPTER | P3 陪伴适配器（预留，未接线） | false |

危机路径**永久直连、无开关**：两条路径都先做入口安检、零 LLM（用户拍板）。

**默认值理由**：灰度决策要求「评测达标才开」——默认 false 保证未过 gate 时部署即是旧行为；开关分离使「只放倾听、陪伴不动」可表达。

**回滚**：把 ZHIYU_DIALOGUE_LISTENING_COMMANDER 置回 false（或缺省）重启即退回旧路径；委托与直连写同一批表（emotion_session/emotion_message），无数据迁移。/api/dialogue/chat 可独立用 ZHIYU_DIALOGUE_COMMANDER=false 摘掉。

### 前端接入说明（/api/dialogue/chat，开关开启后）

- 请求：POST，JSON `{ sessionId?, text, entryContext, practiceSessionId?, intentHint? }`；entryContext ∈ LISTENING_PAGE / PRACTICE_PAGE / KNOWLEDGE_PAGE（缺省 LISTENING_PAGE）。
- 事件（SSE，与 /api/emotion/chat mode=live 平价）：`chunk` {text} → 可选 `revoke` {text,reason} → `done` {sessionId, capability, crisis, revoked, toolUsed}；`error` {code,message}。
- **显式差异点（文档化）**：done 载荷比旧端点多 `capability`（LISTENING / PRACTICE_COMPANION / KNOWLEDGE / CRISIS）与 `toolUsed` 两个字段；不新增其它事件类型。
- 开关关闭时该端点未注册：已认证访问得到 1004、匿名 1002（不是假端点报错）。前端切换前应先用一次轻量请求探测或由后端公告灰度状态。
- 危机输入：两入口行为一致（关怀话术 chunk + done(crisis=true, revoked=true)）；危机对话不存原文（AJ 危机留存表仅存脱敏摘要）。

### AK gate（可重复执行：powershell -File scripts/dialogue-gate.ps1）

本次跑分结果见 docs/eval-baseline-20261104.md 文末追加与 docs/eval-runs/ 报告文件。
