# 按能力分档的模型路由设计（2026-11-07）

> 状态：**设计稿，未实施**。本文档不含代码改动；`application.yml` 未动；未 commit。
> 勘察对象：`zhiyu-backend` 当前主干（AiClient / LlmClient / ModelRouter / 审计与 trace 链路），
> 结论均可在文中引用的文件与行号复核。

## 0. 目标形态（用户已拍板，作为设计基线）

| 能力 | 主用 | 兜底 | 备注 |
|---|---|---|---|
| 情绪对话 | 云 API | 本地 Ollama | 切本地时界面需可见提示 |
| 手作推荐理由、AI 寄语 | 云 API | 本地 Ollama | 失败不致命 |
| 审核建议 | 云 API | **不兜底** | 云不可用就走「AI 不可用」降级，不得本地顶替 |
| 影子判定 | 云 API（生产口径） | 本地仅用于演示 | 影子不影响用户 |

## 1. 现状勘察结论

### 1.1 LLM 接入：全局单端点（这是按能力分档的硬约束）

- 装配链：Spring AI OpenAI starter 自动装配**唯一** `OpenAiChatModel`，
  配置源是全局单例 `spring.ai.openai.base-url / api-key / chat.options.model`
  （[application.yml L20-27](../zhiyu-backend/src/main/resources/application.yml#L20-L27)）。
- `LlmClient` 只有一个生产实现 [SpringAiLlmClient](../zhiyu-backend/src/main/java/com/zhiyu/ai/client/SpringAiLlmClient.java)
  （`ChatClient.Builder` 全仓库仅此一处消费），与 [MockLlmClient](../zhiyu-backend/src/main/java/com/zhiyu/ai/client/MockLlmClient.java)
  按「api-key 是否占位」**条件互斥**；[ProductionLlmClientConfig](../zhiyu-backend/src/main/java/com/zhiyu/ai/client/ProductionLlmClientConfig.java)
  在 prod 缺 key 时 fail-fast。
- [ModelRouter](../zhiyu-backend/src/main/java/com/zhiyu/ai/routing/ModelRouter.java) 的「分档」
  只切换**模型名**（standard=deepseek-chat / strong=deepseek-reasoner），
  注释里写死了「与 standard 同 endpoint/key」——**端点从来只有一个，provider 维度不存在**。
- `AiClient` 门面持有单个 `LlmClient` 引用，所有能力共用（[AiClient L131-145](../zhiyu-backend/src/main/java/com/zhiyu/ai/AiClient.java#L131-L145)）。

**结论**：现在**只能全局一个端点**。按能力分档不是配置能打开的能力，
必须在装配层新增「provider 维度」。好消息是约束面很窄：业务代码只依赖
`LlmClient` 接口与 `AiClient` 门面，改造可以收敛在 `ai/client` 与 `ai/routing`
两个包内，业务模块零改动（详见 §6 改造点清单）。

### 1.2 Embedding / Speech 现状

- Embedding：`zhiyu.ai.doubao.*` 条件装配 [DoubaoEmbeddingModel](../zhiyu-backend/src/main/java/com/zhiyu/ai/embedding/DoubaoEmbeddingConfig.java)，
  key 缺位则不建 Bean → RAG 降级为图谱抽取（已实测，见 ollama-demo-fallback 报告 §7）。
  与对话 provider **完全解耦**，本设计不改动它。
- Speech：`zhiyu.speech.provider` 条件装配（[GatewaySpeechClient](../zhiyu-backend/src/main/java/com/zhiyu/ai/client/GatewaySpeechClient.java)
  默认 / QianwenSpeechClient），已是「按 provider 选 Bean」形态，但与能力分档无关，本设计不改动。

### 1.3 审计与用量留痕现状（评测口径的根基）

| 表 | model/provider 留痕 | 现状 |
|---|---|---|
| `ai_trace` | **有** `model varchar(80)`，另有 `prompt_version/result_status/fallback_reason/latency/tokens` | 可区分 deepseek-chat / deepseek-reasoner / deepseek-r1:8b（本次实测已用此列区分云与本地记录） |
| `ai_audit_log` | **无** model/provider 列（scene/action/reason/ref_type/ref_id） | 只追加表；安全审计口径，不含模型身份 |
| `moderation_soft_shadow` | **无** model/provider 列，仅 `audit_log_id` 回链审计表 | **影子判定结果目前无法直接追到具体模型**（§5.3 的缺口） |

### 1.4 可复用件

- [AdminAiGovernanceService L92-107](../zhiyu-backend/src/main/java/com/zhiyu/modules/admin/service/AdminAiGovernanceService.java#L92-L107)
  已向管理端运营页输出 `chatMode(MOCK/EXTERNAL)`、`modelName`——只读面板的天然落点。
- 熔断按资源 key 隔离（llm-chat/llm-recommend/llm-message/llm-strong/llm-moderation/llm-summary/llm-rag/llm-lineart），
  兜底触发条件可以直接挂在既有熔断与异常分类上。
- `ai-config/ai-capabilities.yaml` 已有**声明式**的 providers（deepseek/qwen/ollama-local）
  与 routing.tiers 草图——但它只是文档态，运行不读它；本设计把其中的 provider 概念落成真实接线。

## 2. 目标架构

**原则：业务模块零改动；provider 选择收敛在装配层；兜底永不静默。**

```
业务模块 → AiClient 门面（安全过滤/审计/熔断/指标，不变）
              │  对每个能力调用，先经 ModelRouter.resolve(task) 得到 Route{task, tier, provider, model}
              ▼
        LlmClientRegistry（新）
              ├── cloud LlmClient（现 SpringAiLlmClient 泛化：DeepSeek/OpenAI 兼容云）
              └── local LlmClient（Ollama OpenAI 兼容端点，新实现，复用同一 Spring AI 栈）
```

- `ModelRouter.Route` record 增加 `provider` 字段；task→(tier, provider, model) 的映射
  从「最小静态映射」扩为「能力×双 provider 策略表」（§3）。
- `AiClient` 不再持有单个 `LlmClient`，改为按 `Route.provider()` 从 registry 取。
  **streamChat/recommendReason/... 的方法签名不变**，业务无感。
- 两个下游 client 都是 Spring AI `ChatModel` 的薄封装：cloud 用现有
  `spring.ai.openai.*` 自动装配；local 用一组并行的 `zhiyu.ai.providers.local.*`
  手工装配第二个 `OpenAiChatModel`（Ollama 兼容 OpenAI 协议，与 DeepSeek 同栈，已实测）。
- Mock 语义不变：cloud 无 key 且 local 未配置时，非 prod 仍走 MockLlmClient；
  prod 的 fail-fast 守卫保留。

### 2.1 影子判定的路由身份

影子判定与审核建议共用 `AiClient.moderationSuggest`（同一熔断 key、同一 strong 档），
但审计 scene 独立（`moderation_shadow`）。设计给影子判定**独立 task 键**
（`moderation_shadow`），使其能挂与 `moderation` 不同的 provider 策略
（云为主，演示可显式切本地并打标），而审核建议保持 cloud-only。

## 3. 配置键（全部环境变量占位，默认值即「云为主 + 本地兜底」）

风格沿用 `application.yml` 既有 `zhiyu.ai.*` 段与 `${ENV:default}` 占位约定：

```yaml
zhiyu:
  ai:
    providers:
      cloud:                       # 主用；现有 spring.ai.openai.* 键保持原样不迁移
        label: deepseek-cloud
      local:
        enabled: ${OLLAMA_FALLBACK_ENABLED:true}          # 本地兜底总开关（默认开=目标形态）
        base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
        api-key: ${OLLAMA_API_KEY:ollama-local}           # Ollama 不校验，占位非 sk-placeholder 即可
        model: ${OLLAMA_CHAT_MODEL:deepseek-r1:8b}
        connect-timeout-ms: ${OLLAMA_CONNECT_TIMEOUT_MS:5000}
        read-timeout-ms: ${OLLAMA_READ_TIMEOUT_MS:90000}  # 本地 7B 慢，超时宽于云
    routing:
      fallback:                    # 能力级兜底策略表（静态，禁动态打分）
        chat: local                # 情绪对话：云→本地
        recommend: local           # 推荐理由：云→本地
        message: local             # 寄语/创作回顾：云→本地
        moderation: none           # 审核建议：不兜底（§4）
        moderation_shadow: none    # 影子判定：默认不兜底；演示置 local 需同时开 demo 开关
        summary: none              # 滚动摘要：不兜底（后台任务，宁可跳过）
        rag: local                 # 非遗问答：云→本地（拍板项 D1）
        companion: none            # 陪伴编排：不兜底（工具执行语义敏感，拍板项 D1）
        lineart: none              # 线稿/纹样共创：不兜底（拍板项 D1）
        listening_summary: none    # 倾听小结：不兜底（拍板项 D1）
      local-demo-mode: ${ZHIYU_LOCAL_DEMO_MODE:false}   # 演示模式总闸：开才允许 shadow 走 local
```

- 默认形态 = 云主用 + 表内三条（chat/recommend/message）本地兜底，与拍板基线一致。
- `zhiyu.ai.routing.strong-model`（现有）与 provider 是**两个正交维度**：
  Route 解析为 (provider, tier) → 模型名 = provider 侧该 tier 的模型
  （cloud: deepseek-chat/deepseek-reasoner；local: 同一 local.model，本地只有一档）。
- 与 `ai-config/ai-capabilities.yaml` 的同步：routing 段补 provider 维度与 fallback 表，
  保持「配置文件是文档、application.yml 是运行承载」的既有分工。

## 4. 兜底触发条件（什么情况算「云不可用」）

| # | 条件 | 判定来源 | 触发兜底？ | 说明 |
|---|---|---|---|---|
| 1 | 云 key 缺失/占位（sk-placeholder） | 启动期装配判定 | ✅ | 此时 cloud client 根本不装配，registry 直接路由 local；**这是当前大多数时间的真实状态** |
| 2 | 调用抛出连接/读写超时、网络错误、5xx | 调用期异常分类 | ✅ | 记入对应资源 key 熔断计数（与现状一致） |
| 3 | 该资源 key 熔断 OPEN | `breaker.allowRequest(key)==false` | ✅ | 熔断期间直达 local，不先打云 |
| 4 | HTTP 429（限流） | 异常分类 | ✅（拍板项 D2） | 限流是可用性问题 |
| 5 | HTTP 401/403（鉴权失败） | 异常分类 | ✅ 但记 ERROR 级告警（拍板项 D2） | 多半是 key 配置错误，兜底同时必须显眼告警，防止长期吃兜底不自知 |
| 6 | HTTP 400/422（请求本身非法） | 异常分类 | ❌ | 我方构造错误，打本地也会败；直接报错 |
| 7 | 输出安全 REWRITE/BLOCK | 安全过滤结果 | ❌ | 不是可用性问题；换 provider 不改变安全判定 |
| 8 | moderation JSON 解析失败 | 业务层判定 | ❌ | moderation 本来就不兜底；且 PARSE_FAIL 已有受控重试 |
| 9 | 响应为空（finish_reason=length 截断等） | 空响应检测 | ❌（走既有重试/档内回落） | 先 strong→standard 档内回落；这是模型行为不是云故障 |

**兜底永不静默（三条留痕，缺一不可）：**

1. **调用结果打标**：`LlmChatResult` 增加 `provider` 字段（cloud/local）；
   流式对话的完成事件（SSE done payload）增加 `provider` 标志位。
2. **trace/审计**：`ai_trace` 增加 `provider` 列与 `fallback_reason` 扩展枚举
   （如 `CLOUD_TIMEOUT`/`CLOUD_5XX`/`CLOUD_NO_KEY`/`CLOUD_BREAKER_OPEN`，
   与现有 PROVIDER_ERROR/BREAKER_OPEN 区分「打云失败」与「转投本地」两段）；
   兜底发生的当次写**两条** trace（cloud 失败一条 + local 结果一条），与既有
   strong→standard 回落的双行口径一致（本次实测已验证该口径可读）。
3. **前端可见**：
   - 学生端：SSE done 带 `provider=local` 时，对话区展示一次性轻提示
     （候选文案见拍板项 D3，红线：不得出现「诊断/治疗」字样，不得夸大本地能力）；
   - 管理端运营页：provider 分布（近 N 日 cloud/local 调用量）、当前兜底状态
     （哪个能力正在吃兜底、从何时开始）。

## 5. 审核建议「不兜底」的实现方式

### 5.1 三层防线（推荐全做，强度递增）

1. **配置层**：策略表 `moderation: none`（§3）——无兜底目标，路由解析不出 local。
2. **装配层硬校验（防改配置打开）**：启动期校验器拒绝任何把 `moderation`
   解析到 local provider 的策略，**fail-fast 拒绝启动**（与 ProductionLlmClientConfig
   的守卫同款思路）。代价：若未来真要开（如合规变化），必须改代码+评审——
   这正是我们要的成本。
3. **契约测试钉死**：单测断言 `ModelRouter.resolve("moderation")` 在任何
   合法配置组合下都不会返回 local provider；断言 `AiClient.moderationSuggest`
   在云不可用路径返回 null（「暂无建议」）而非本地产出。
   运行期再加一道便宜校验（`AiClient` 内 task=moderation && provider=local →
   直接抛配置异常并记 ERROR），防运行期配置热改绕过启动校验。

### 5.2 为什么不能只在配置层

`runtime_config`/yaml 都是运行期可写面；审核建议若被本地 7B 顶替，
产出质量与云端口径不等价却会出现在同一审核工作台——这是**静默降质**，
比「暂无建议」更误导审核员。所以必须让「打开它」的成本高于改一行配置。

### 5.3 例外：影子判定的演示模式

- 影子判定不面向用户、不改变任何处置（只统计「若开软档会怎样」），
  允许演示目的走本地——但必须同时满足：`zhiyu.ai.routing.local-demo-mode=true`
  **且** `moderation_shadow: local`，双开关缺一不可（单误配不生效）。
- 走本地产出的影子判定：`moderation_soft_shadow` 需要能区分口径
  （当前表无 model 列，见 §6 缺口 G3）；演示期产出的行在打标前**不得**混入
  生产口径统计，`/shadow-stats` 聚合必须按口径过滤或分列展示。

## 6. 改造点清单（文件级）与已知缺口

> 仅列位置与职责，不写实现。标 ★ 为新增文件。

| # | 文件 | 改动 |
|---|---|---|
| 1 | `ai/routing/ModelRouter.java` | Route 增加 provider 字段；任务→(tier,provider,model) 策略表；cloud-only 任务集合 |
| 2 | ★ `ai/client/LlmClientRegistry.java` | 持有 cloud/local 两个 LlmClient，按 provider 取；cloud 缺位时本地直连；两者皆缺回 Mock（非 prod） |
| 3 | ★ `ai/client/OllamaLlmClient.java` | Ollama OpenAI 兼容实现（可与 SpringAiLlmClient 共基类/组合第二个 ChatModel） |
| 4 | `ai/client/SpringAiLlmClient.java` | 泛化为可双实例（cloud 由 spring.ai.openai.* 装配）；`LlmChatResult` 增加 provider 字段 |
| 5 | `ai/AiClient.java` | 构造改注入 registry；各能力方法按 Route.provider 选 client 并写双段 trace；moderation 运行期硬校验（§5.1-3） |
| 6 | `application.yml` | 新增 `zhiyu.ai.providers.local.*` 与 `zhiyu.ai.routing.fallback.*` 占位（默认值见 §3；现有键不动） |
| 7 | `ai-config/ai-capabilities.yaml` | routing 段补 provider 维度与 fallback 表（文档态同步） |
| 8 | ★ `sql/migrations/2026xxxx_ai_trace_provider.sql` | `ai_trace` 加 `provider varchar(20)`、必要时 `fallback_from varchar(20)`；同步 schema.sql、schema-h2.sql、SchemaCompatibilityCheck |
| 9 | `AdminAiGovernanceService` + 运营页前端 | 只读面板：provider 分布、兜底状态、当前模型口径标识 |
| 10 | `EmotionController`/SSE done payload + 学生端 chat 页 | done 加 provider 标志；本地态轻提示（拍板项 D3） |
| 11 | ★ 契约测试 | moderation 不可路由 local（多配置组合）；兜底双段 trace 断言；云无 key 直连 local 断言 |

**勘察发现的缺口（随本设计一并处理或单列任务）：**

- G1：`ai_audit_log` 无 model/provider 列。审计表只追加，加列需迁移；
  安全审计口径是否需要模型身份，涉及合规边界（拍板项 D6）。
- G2：`ai_trace` 无 provider 列（只有 model 名，靠名字推断 provider 不可靠——
  云与本地可以同名）。改造点 #8 覆盖。
- G3（2026-11-08 勘误）：初版判断「`moderation_soft_shadow` 无 model/provider 列」**有误**——
  前序影子模式批已通过 `sql/migrations/20261107_moderation_soft_shadow_attribution.sql`
  补齐 provider/model 归属列（失败样本保持 'unknown'，绝不冒充判成端点），
  且 `shadow.demo-providers` 清单已在 `ModerationShadowService` 承担演示归属排除口径。
  本设计的 D8 双开关复用同一把钥匙：影子走本地的判定自动落入演示排除口径。

## 7. 切换留痕与权限

**方案 A：只读面板 + 配置改动走运维流程（推荐，用户倾向）**

- 面板：管理端 AI 运营页扩展现有 config 区（改造点 #9），展示
  各能力当前 provider、兜底触发中的能力与起始时间、近 7 日 cloud/local 调用分布。
- 改动路径：策略表静态在 `application.yml`/env（改动=发版或重启）；
  `maxTokens` 等运行参数继续走现有 `PUT /api/admin/config/runtime`
  （role=3 + revision 乐观锁 + `admin_operation_log` 审计，全部现成）。
- 成本：小；风险：低（无运行期可写面）。回滚=还原 env 重启。

**方案 B：可编辑路由（runtime_config 扩展键 + 管理 UI）**

- 把 `zhiyu.ai.routing.fallback.*` 放进 runtime_config，管理 UI 可改，
  即时生效（无缓存，同 RuntimeSettings 现状），改动落 `admin_operation_log`。
- 需要：乐观锁沿用；二次确认（前端确认框 + 审计 action 区分）；
  moderation 键被 §5.1 的校验器排除在可写集合外。
- 成本：中（多一个可写面 + UI + 测试）；风险：运行期误配窗口变大。

**建议：A。** 演示兜底的切换频率低（演示前一次），不值得为它开一个
运行期可写的安全敏感面。若后续运营确实需要频繁切换，再按 B 单独立项。

## 8. 评测口径绑定（最关键）

1. **现状**：`ai_trace.model` 已能区分模型名（962 基线的 deepseek-reasoner 与
   本地 deepseek-r1:8b 记录可机械区分，本次实测已验证）；但**缺 provider 维度**
   与**兜底两段留痕**（改造点 #8）。
2. **规则**：
   - 任何对外数字必须能追到 (provider, model, prompt_version) 三元组；
     trace 三元组齐全前，相关数字不得进入对外报告。
   - **换模型/换 provider 后旧基线不可直接复用**：文档侧在评测报告模板头部
     固定「模型口径」段（provider+model+日期+prompt_version）；
     界面侧运营页在当前模型与 962 基线模型（deepseek-reasoner@cloud）不一致时
     展示常驻警示条「当前口径 ≠ 962 基线口径，数字不可直接对比」。
   - 影子判定的生产口径统计（`/shadow-stats`）在 G3 补齐前，
     演示期产生的本地口径行必须可过滤；**演示数据不得计入对外发生率**。
3. **诚实口径兜底**：本地兜底期间的对外展示（如果有），统一标
   「本地演示模式产出，非云端基线模型」，与运营页警示同源。

## 9. 不做的事（范围外）

- 多模型 A/B 评测框架、自动选模/打分路由（ModelRouter 保持最小静态映射）。
- 按用户/按校园灰度 provider。
- Embedding / Speech 的 provider 分档（现状已够用，见 §1.2）。
- 云 provider 多云切换（qwen 档保持显式不可用）。
- 本地模型的量化/推理优化、并发调优（属部署运维，属 ollama-demo-fallback 报告 §5 范畴）。
- 前端「本地模式」的视觉重设计（仅一个轻提示位，拍板项 D3）。

## 10. 必须由用户拍板的决策项

| # | 决策项 | 备选 | 我的建议 |
|---|---|---|---|
| **D1** | 基线表未覆盖能力的分档归属：listening_summary / rag / companion / summary / lineart(heritage) / stuck-guide | 各自 cloud→local 或 cloud-only | rag 同 message 档（云→本地）；listening_summary 与 companion/summary/lineart **cloud-only**（小结质量敏感、编排涉及工具执行、共创有白名单契约，本地 7B 失败语义都不致命，宁可降级模板）；stuck-guide 同 message 档 |
| **D2** | 429 与 401/403 是否算「云不可用」 | §4 表 #4/#5 | 429 兜底；401/403 兜底但 ERROR 告警（防长期吃兜底不自知） |
| **D3** | 学生端本地兜底提示文案与位置 | 头部状态条 / 气泡下轻注 / 进入对话前一次性提示 | 气泡下轻注：「当前为本地陪伴模式，回复可能更简单；紧急情况请使用『寻求支持』」——措辞待定，必须过内容安全红线审读 |
| **D4** | 审核建议防误配强度 | §5.1 三层全做 / 只做契约测试 / 只做启动校验 | 三层全做（成本最高的也只是一个校验器+两个测试） |
| **D5** | 面板形态 | A 只读 / B 可编辑 | A（用户已倾向，确认） |
| **D6** | `ai_audit_log` 是否加 provider/model 列 | 加（迁移）/ 不加（只加 ai_trace） | 不加：审计管内容安全、trace 管用量口径，分工现状清晰；除非合规要求审计可追模型 |
| **D7** | strong 档与 provider 的交叉：cloud 侧 strong=deepseek-reasoner 保留？本地侧一律单档？ | 二维保留 / 本地也分两档 | 二维保留：cloud 侧不动；本地侧单档（本地只有一模型），Route 解析到 (local, strong) 时映射 local.model |
| **D8** | 演示模式总闸 `local-demo-mode` 的默认与持有者 | env（运维持）/ runtime_config（管理端可写） | env（默认 false）；演示当天由运维显式开启，进 §5.3 双开关 |
