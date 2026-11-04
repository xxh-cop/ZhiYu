# 织愈「全面智能化」路线图（2026-11-03）

> 本文是研究与方案文档，**不含任何代码改动**。所有「现状」结论均来自对仓库文件的实际读取
> （读取日期 2026-11-03）；所有外部项目结论均标注可查证来源。
> 红线：不出现诊断性结论；高危走危机干预不做疏导结论；Prompt 外置 `resources/prompts/*.st`；
> 外部 API Key 只存服务端环境变量。本文不包含、也不引用任何真实密钥。

---

## 0. 结论先行：最该先做的三件事

| 顺序 | 动作 | 为什么是它 | 性质 |
|---|---|---|---|
| 1 | **把已有评测集跑起来**：为 `eval/companion-eval.json`（20 条，v2026-10-30）写一个离线 EvalRunner，跑分报告落盘归档 | 评测集和 `CompanionEvalCatalog` 都已存在但**零运行记录**。没有评测基线，后面每一项智能化都无法区分「真改进」与「看起来智能」。成本最低、收益是其他一切的前提 | 现有代码可改 |
| 2 | **收敛对话与门面**：定下 `/api/emotion/chat`（SSE 倾听）与 `companion` 编排层（`CompanionOrchestrator`）的主从关系；把推荐规则从「Java + yaml 双写」收敛为 yaml 单写 | 两套对话安全策略与降级路径不一致是当前最大的**正确性风险**（不是体验问题）；`AiClient` 实测已 629 行、承载 8+ 场景方法，继续堆功能会先压垮可维护性 | 现有代码可改 |
| 3 | **任务分档的最小模型路由**：在 `ai-config/ai-capabilities.yaml` 增加 `task → provider/model` 映射，危机干预与内容审核走强模型、日常对话保持 `deepseek-chat`、Embedding 独立 | 现在所有场景共用 `default_provider: deepseek` 一个模型，危机场景值得更高质量，日常对话值得更省钱。不需要引入路由框架，一个映射表 + 按场景选模型即可 | 现有代码可改 |

三件事都不需要新基础设施，都能在两周内做出可验证切片。详见 §3 六面方案与 §4 优先级表。

---

## 1. 配置事实核对（2026-11-03 实测）

来源：[ai-capabilities.yaml](file:///d:/develop/Zhi_Yu/ai-config/ai-capabilities.yaml)、
[.env.example](file:///d:/develop/Zhi_Yu/.env.example)、`.env`（只读变量名与是否配置，未读取/未记录任何 key 值）。

### 1.1 当前实际可用的提供方

| 能力 | 提供方 / 模型 | 配置状态 | 说明 |
|---|---|---|---|
| 对话 LLM | DeepSeek `deepseek-chat` | **已配置，默认**（`default_provider: deepseek`） | 超时 20s，max_output 600，温度 0.7 |
| 对话 LLM | 通义千问 `qwen-plus` | **声明了但未配置**（yaml 里引用 `QWEN_BASE_URL`/`QWEN_API_KEY`，`.env` 中不存在这两个变量） | 启动依赖 `default_provider` 不指向它，当前不影响运行；属于「配了壳没装芯」 |
| 对话 LLM | Ollama 本地 `qwen2.5:7b` | **备选，未部署**（`http://localhost:11434`） | 「数据不出校园」的本地化叙事，当前无运行实例 |
| Embedding | 豆包（火山引擎方舟）`DoubaoEmbeddingModel` | **已配置** | 服务于 RAG 向量化 |
| 语音 ASR/TTS | 千问语音 `fun-asr-flash-2026-06-15` / `qwen-audio-3.1-tts-flash` | **已配置并启用**（`SPEECH_PROVIDER=qianwen`，`QIANWEN_SPEECH_ENABLED=true`） | 代码层有 provider gated 开关；音色 `longanhuan_v3.1` |
| 语音网关 | 自建 JSON 网关 `ZHIYU_JSON_GATEWAY_V1` | **未配置**（`ZHIYU_SPEECH_ENABLED=false`） | 见 docs/speech-gateway.md |
| RAG 向量库 | 本地文件存储 + 非遗目录图谱（`heritage_catalog`） | **已启用**（`vector_store: local_file`，top_k=3，rerank=keyword_overlap） | **不是**已部署 Milvus/Chroma，文档不得写成已部署 |
| 成本核算 | `AI_INPUT_COST_PER_MILLION` / `AI_OUTPUT_COST_PER_MILLION` | **未配置**（`.env` 中无） | 管理端运营页只能显示 token 用量，无法显示金额 |

### 1.2 已落地的智能化资产（易被低估，路线图中要复用）

| 资产 | 位置 | 状态 |
|---|---|---|
| 统一门面 + 隔离熔断 | [AiClient.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/ai/AiClient.java)（实测 629 行）、`ai/resilience/*`（Redis/InMemory 两套 CircuitBreakerStore） | 5 个资源 key 隔离，连续失败 5 次熔断 60s，半开探测 |
| 真流式 SSE | `AiClient.streamChatLive` + `SentenceSplitter` | 句级 chunk + 全文终检 revoke（S 级已拍板 A 方案） |
| 双侧安全过滤 + 高危词库 | `ai/safety/*`、`resources/lexicon/high-risk.txt`、`prompts/safety.st` | 输入/输出双侧，`on_block: safe_reply` |
| 加密审计 | `ai_audit_log`（raw_input/ai_output 加密，只追加） | 符合 §5 隐私约束 |
| Prompt 外置 | `resources/prompts/*.st` 共 10 个模板 | companion / recommend / work_message / rag / safety / reflection / lineart / heritage-co-create / listening-summary / companion-plan |
| **Agent 种子** | `modules/companion/`：`CompanionOrchestrator`（plan→validate→fallback，带每会话 budget）、`CompanionToolRegistry` + 3 个工具（`RecommendPracticeTool`/`SuggestBreathingTool`/`SearchHeritageTool`）、`CompanionTraceService` | **工具调用框架已经存在**，不是从零开始 |
| **记忆种子** | `CompanionMemoryService` / `CompanionMemoryController` | 偏好 key（时长/材料/门类）、consent 授权门、`deleteAll` 可撤回、过期时间、AES 加密 |
| **评测种子** | `CompanionEvalCatalog` + `resources/eval/companion-eval.json`（20 条：FACT×5、RELATION×4、INSUFFICIENT×4、CROSS_REGION×2、SYMBOL×2、INJECTION×2、RECOMMEND×1） | 有数据集、有装载器、**无运行器、无跑分记录** |

### 1.3 已确认的缺口

1. 双对话系统并存：`/api/emotion/chat` 与 companion 编排层会话模型不互通，安全/降级策略有漂移风险。
2. `AiClient` 629 行上帝类：对话、推荐、寄语、反思、倾听小结、陪伴计划、知识问答、非遗二创、线稿、语音全在一个类里。
3. 推荐规则双写：mood→tag 规则同时在 [ai-capabilities.yaml](file:///d:/develop/Zhi_Yu/ai-config/ai-capabilities.yaml) `recommendation.rules` 与 Java 代码中各存一份。
4. 评测集无运行记录；`.env` 未配置成本费率，成本不可核算。
5. 无通知系统（预约/审核/小组/关怀消息触达不到用户）——这是「管理运营智能化」的前置依赖。

---

## 2. 开源项目借鉴（2026-11-03 查证）

原则：**抄思路分层，不抄代码**。织愈后端是 Java/Spring AI，以下项目多为 Python/TS，直接引入均不现实；
可借鉴的是架构分层与工程纪律。star 数为 GitHub API 当日查证值。

### 2.1 LangGraph — Agent 编排的状态机思维
- 解决什么：长时运行、有状态 Agent 的低层编排——把「确定性代码步骤」与「LLM 决策步骤」混排在一张图里，支持条件分支、循环、中断恢复、持久化记忆。⭐43,962，MIT。
- 抄哪层：**图 = 显式状态 + 节点 + 条件边**。`CompanionOrchestrator` 目前是「一次 LLM 调用产出 plan（reply/stage/tool）→ 校验 → fallback」的单跳结构；可借鉴 LangGraph 把 stage 流转画成显式状态机（倾听→共情→建议→工具→收尾），每步可校验、可回退、可 trace（已有 `CompanionTraceService` 做落点）。
- 适配度：**高（思路）/ 低（代码）**。不值得引入 LangGraph4j 之类的移植框架，值得引入的是「状态机显式化 + 每节点 budget」的建模方式。
- 引入成本：仅重构 `CompanionOrchestrator`，无新依赖。
- 来源：[github.com/langchain-ai/langgraph](https://github.com/langchain-ai/langgraph)、[LangGraph 官方文档](https://docs.langchain.com/oss/python/langgraph/overview)

### 2.2 mem0 — 分层记忆与「可遗忘」
- 解决什么：Agent 的长期记忆层——从对话中抽取、固化、检索用户偏好与事实，支持 add/search/delete 三操作与图记忆变体；有 LOCOMO 基准评测论文背书。⭐36,607，Apache-2.0。
- 抄哪层：**记忆不是聊天记录的堆叠，而是「抽取→归一→带过期→可删除」的独立子系统**。织愈 `CompanionMemoryService` 已经是这个形状（偏好 key 白名单、consent、expiresAt、deleteAll），mem0 验证了方向；可补的是「跨会话摘要」这一层（把会话小结提炼成长期事实，而不是只存手工偏好）。
- 适配度：**高（思路）**。隐私边界上 mem0 的 delete 语义与我们「撤回隐私授权走 AccountLifecycle」一致。
- 引入成本：低——在现有 memory 服务上加「会话小结→偏好提炼」一步，仍是服务端加密存储。
- 来源：[github.com/mem0ai/mem0](https://github.com/mem0ai/mem0)、[Mem0 论文 arXiv:2504.19413](https://arxiv.org/pdf/2504.19413)

### 2.3 RAGFlow — 答案可溯源与混合检索
- 解决什么：以「深度文档理解」为卖点的 RAG 引擎，强调**带引用的可信问答**（chunk 级引用回链）、全文+向量混合检索（Elasticsearch/Infinity）、可视化切分干预。⭐93,147，Apache-2.0（Go 后端）。
- 抄哪层：**「每个答案都必须能指回出处」是产品级 RAG 的及格线**。织愈 `RagService`/`KnowledgeAskService` 已有 `RagSourceVO` 引用模型，可借鉴 RAGFlow 把引用做成强约束（无引用=降级为「我不确定」话术），以及混合检索（当前 rerank 只有 `keyword_overlap`，可加 BM25 式词项权重）。
- 适配度：**高（思路）**。不引入其引擎；非遗语料规模小（目录级），本地文件向量库 + 图谱已够用，暂不虚构 Milvus。
- 引入成本：低-中——改 `RagService` 检索与答案模板（`prompts/rag.st`）。
- 来源：[github.com/infiniflow/ragflow](https://github.com/infiniflow/ragflow)、[RAGFlow 文档](https://ragflow.io/docs/)

### 2.4 Dify — 模型/提示词/观测的运营化分层
- 解决什么：LLM 应用平台——可视化工作流、RAG 管线、模型管理、可观测性一站式。⭐89,287，**自定义许可证**（Apache-2.0 附加条款，商用有限制，禁止多租户 SaaS 转售）。
- 抄哪层：**「提示词与模型配置是运营资产，不是代码」**。织愈 prompt 已外置（✓），但模型选择、温度、token 上限散在 yaml/Java 两侧；可借鉴 Dify 的「按应用（场景）维度管理模型配置 + 每次调用记录 token/延迟」的观测口径，接到已有的 therapy-funnel 看板。
- 适配度：**中**。平台本身不引入（我们已有管理端）；其「场景级配置 + 调用计量」的思路可直接落在 `ai-capabilities.yaml` 扩展上。
- 引入成本：低（配置结构扩展 + 审计字段补齐）。
- 来源：[github.com/langgenius/dify](https://github.com/langgenius/dify)、[dify.ai](https://dify.ai/)

### 2.5 FastGPT — 知识库工程化与 QA 拆分
- 解决什么：中文知识库问答平台，数据处理→RAG 检索→可视化工作流编排；特色是 QA 拆分导入、chunk 可人工修改、多库复用、简易/编排双模式。⭐25,560，**自定义许可证**（商用付费限制）。
- 抄哪层：**知识库的维护是「内容工程」不是一次性导入**——chunk 可改、可删、可按库复用。织愈非遗知识库目前是静态文件，可借鉴其「知识条目生命周期」（传承人/管理员可修订条目 → 重新向量化 → 版本留痕），与管理端审核流打通。
- 适配度：**中高（思路）**。
- 引入成本：中——涉及知识条目表与重建索引任务，建议 P2。
- 来源：[github.com/labring/FastGPT](https://github.com/labring/FastGPT)、[FastGPT 文档](https://doc.fastgpt.cn/docs/faq)

### 2.6 open-webui — 自托管与模型可替换的接口抽象
- 解决什么：自托管 AI 界面，一套 UI 接 Ollama/OpenAI/Anthropic 任意兼容端点，含 RAG、工具、RBAC、模型可见性控制。⭐157,650，**自定义许可证**（BSD-3 附加品牌条款）。
- 抄哪层：**provider 抽象的「兼容端点」思路**——所有模型都当作 OpenAI 兼容端点来抽象，切换只改配置。织愈 `LlmClient` 接口 + `SpringAiLlmClient`/`MockLlmClient` 已是这个形状；可补的是「按任务声明端点」（见 §3.1 路由）。
- 适配度：**中（思路）**。产品形态不借鉴（我们不提供模型管理 UI 给学生）。
- 引入成本：低。
- 来源：[github.com/open-webui/open-webui](https://github.com/open-webui/open-webui)、[openwebui.com](https://www.openwebui.com/)

### 2.7 MindChat（漫谈）— 中文心理领域微调与语料形态
- 解决什么：X-D Lab 的中文心理大模型，从咨询/评估/诊断/治疗四维度微调（Qwen/InternLM/Baichuan 等基座 + LoRA）。⭐721，**GPL-3.0**。
- 抄哪层：**不抄模型，抄「语料形态与免责姿态」**——多轮共情对话的语料结构（场景标签 + 回应风格）可指导我们扩充评测集的中文心理场景用例。其「不提供诊断结论」的对外话术与织愈红线一致，可作措辞参考。
- 适配度：**中**。GPL-3.0 与我们的仓库不兼容，且**自微调模型超出当前阶段**（无 GPU 基础设施、无医疗资质背书）。
- 引入成本：仅评测集语料风格参考，零代码。
- 来源：[github.com/X-D-Lab/MindChat](https://github.com/X-D-Lab/MindChat)、[wisemodel 模型页](https://wisemodel.cn/models/X-D-Lab/MindChat)

### 2.8 EmoLLM — 心理健康 LLM 的全链路清单
- 解决什么：「理解用户→支持用户→帮助用户」链路的心理健康大模型系列，仓库含数据构造、微调、**评测**、部署、RAG 全链路。⭐1,798，MIT。
- 抄哪层：**它的目录就是一张「心理 AI 自检清单」**——尤其是评测维度（共情质量、安全性、多轮一致性）和数据构造方法（角色扮演合成 + 人工筛选）。直接用于 §3.5 评测集分层设计。
- 适配度：**中高（清单与评测思路）**。MIT 许可证友好，但同样不引入模型本身。
- 引入成本：零代码，指导评测集扩充。
- 来源：[github.com/SmartFlowAI/EmoLLM](https://github.com/SmartFlowAI/EmoLLM)

### 2.9 借鉴总结：一张映射表

| 织愈要补的面 | 主要参考 | 次要参考 |
|---|---|---|
| 多模型路由与降级 | open-webui（端点抽象） | Dify（场景级配置） |
| 记忆与个性化 | mem0（分层与可遗忘） | LangGraph（会话状态持久化） |
| RAG 升级 | RAGFlow（引用强约束、混合检索） | FastGPT（知识条目生命周期） |
| Agent 化 | LangGraph（显式状态机） | Dify/FastGPT（工具编排形态） |
| 评测与回归 | EmoLLM（评测维度） | MindChat（中文心理语料形态） |
| 运营智能化 | Dify（调用计量与观测） | — |

---

## 3. 六面方案（每面落到具体文件与验收口径）

### 3.1 多模型路由与降级

**现状**：所有场景共用 `default_provider: deepseek`（`deepseek-chat`）；熔断按 5 个资源 key 隔离（✓ 已做）；qwen 声明未配置；ollama-local 未部署；成本费率未配置。

**方案**（最小版，不引入路由框架）：
1. 扩展 [ai-capabilities.yaml](file:///d:/develop/Zhi_Yu/ai-config/ai-capabilities.yaml)，新增 `routing` 段：

```yaml
  routing:
    policy: task_tier
    tiers:
      standard:            # 日常对话、寄语、推荐理由
        provider: deepseek
        model: deepseek-chat
      strong:              # 危机干预话术、内容审核辅助、倾听小结
        provider: deepseek
        model: deepseek-reasoner   # 或配置切换为 qwen-max 等强模型
      embedding:
        provider: doubao
```

2. `AiClient` 每个场景方法声明 tier，内部按 tier 选 provider/model——**先做静态映射，不做动态打分路由**。
3. 失败回落链（在现有熔断器之后串接）：
   - `llm-chat`：强模型熔断 → 降级 standard 模型 → 再熔断 → 缓存图文话术（现有 fallback）。
   - 危机干预：**不走 LLM 降级链**，高危命中词库时直接返回预置干预话术（现有 `safe_reply` 路径），LLM 只负责非高危部分。这是红线，不是优化项。
4. 成本核算：在 `.env.example` 补齐 `AI_INPUT_COST_PER_MILLION`/`AI_OUTPUT_COST_PER_MILLION` 并录入真实费率（key 只进服务端 `.env`），让运营页从「token 用量」升级到「金额估算」。

**取舍**：标准/强双档而非按 prompt 动态路由——动态路由本身需要一次分类调用，延迟和成本都翻倍，当前日调用量不值得。
**验收口径**：同一危机语料分别命中 strong tier；`llm-chat` 熔断时 SSE 仍能在 `first_token_timeout_ms: 2000` 内返回降级内容（NFR-02）；管理端运营页显示分 tier 的调用量与金额。
**新建点**：`ai/routing/ModelRouter.java`（或并入 `LlmClient` 工厂）；改 `AiClient` 场景方法签名（带 tier）。
**性质**：现有代码可改。

### 3.2 记忆与个性化

**现状**：`CompanionMemoryService` 已有偏好记忆（时长/材料/门类三 key、consent 门、AES 加密、过期、deleteAll 可撤回）；倾听小结 `listeningSummary` 已存在但未沉淀为长期记忆；两套对话系统的记忆不互通。

**方案**（三层，全部服务端加密、可撤回）：
1. **会话内**：维持现状（`max_history_turns: 10`），不扩窗——扩窗=扩成本+扩泄露面。
2. **跨会话摘要**：会话结束时用 `listeningSummary` 的现成通道产出 ≤100 字摘要，**脱敏后**写入新的 `companion_session_summary` 表（含 `expires_at`，默认 90 天滚动过期）；下次会话注入 system prompt。摘要**不存原句、不存可识别信息**，格式接近 `care_reminder.summary` 的脱敏语义。
3. **长期偏好**：维持现有三 key 白名单（`CompanionMemoryService.PREFERENCE_KEYS`），**不开放自由文本记忆**——自由文本记忆是隐私与合规黑洞（用户随手写的病史/住址会被永久记住）。

**隐私边界**：三层记忆全部挂到 AccountLifecycle 的撤回/注销清理链；`deleteAll` 要同时清摘要层；memory 的 `consent=false` 时 prompt 不注入任何记忆（`promptSummary` 返回空）。
**验收口径**：开启授权后第 2 次会话能体现偏好（评测集加记忆用例）；撤回授权后 `promptSummary` 为空且表中无残留；注销后三层记忆全部物理删除（走现有清理机制）。
**新建点**：`companion_session_summary` 表（DDL 进 `sql/schema.sql` + 迁移脚本）；`CompanionMemoryService` 加摘要读写。
**性质**：现有代码可改（一张新表）。

### 3.3 RAG 升级

**现状**：本地文件向量库 + 非遗目录图谱（`HeritageGraphIndex`），top_k=3，rerank=keyword_overlap；`RagSourceVO` 引用模型已有；`prompts/rag.st` 外置。

**方案**（不虚构已部署 Milvus，全部在本地文件存储上做）：
1. **切分**：非遗条目按「材料/工序/纹样寓意/地域」结构化字段切 chunk，替代定长切分——目录级小语料下，结构切分收益大于任何算法优化。
2. **混合检索**：向量分 + 关键词分线性融合（`score = α·cosine + (1-α)·keyword_overlap`，α 默认 0.7 写进 yaml），替代单一 `keyword_overlap` rerank。
3. **引用强约束**：`prompts/rag.st` 增加硬指令「每个事实句必须带来源编号；无来源支撑的问题必须回答『这个问题超出了我了解的非遗知识范围』」；`KnowledgeAskService` 对输出做引用编号校验，无引用命中时降级为兜底话术。
4. **答案可溯源 UI**：学生端展示来源条目名（`RagSourceVO` 已有字段，前端补齐展示即可）。

**验收口径**：评测集 FACT/RELATION/INSUFFICIENT/CROSS_REGION 四类用例（已有 15 条）离线跑分：FACT 引用命中率 ≥90%；INSUFFICIENT 正确拒答率 100%（不许编）；CROSS_REGION 不张冠李戴。
**性质**：现有代码可改。

### 3.4 Agent 化

**现状**：`CompanionOrchestrator` + `CompanionToolRegistry` + 3 工具 + trace 已是**单跳 Agent**（LLM 产 plan→校验→执行→fallback）。这是种子，不是空白。

**方案**（渐进，不追多 Agent）：
1. **先把工具集补齐到 5 个**：现有推荐手作/呼吸引导/非遗检索，新增：
   - `QueryEmotionTrendTool`（查本人近 7 天心情打卡趋势，只读本人数据）——回应「我最近怎么样」类问题；
   - `QueryCraftStepTool`（查当前创作会话的步骤详情）——替代把步骤全文塞进 prompt。
2. **状态机显式化**（借鉴 LangGraph）：把 `CompanionStage` 流转画成显式图（倾听→共情→建议→工具→收尾），每节点 budget 与可回退边写进 `CompanionPolicy`，trace 记录节点路径。这让「Agent 行为」可审、可测、可回放。
3. **何时不该用 Agent（明确负面清单）**：
   - 危机场景：命中高危词 → **跳过整个编排**，直接返回预置干预话术。Agent 的「自主决策」在危机场景是负资产。
   - 内容审核：走固定管线不过 Agent（审核要的是确定性，不是创造性）。
   - 简单 FAQ/规则可查的推荐：`rule_first: true` 已是正确姿势，不为了「显得智能」套 LLM。

**验收口径**：工具调用 trace 在 `CompanionTraceService` 可查（已有）；负面场景用例进评测集（危机输入下编排器不被触发，由测试断言）。
**性质**：现有代码可改。

### 3.5 评测与回归

详细方案单独成文：[ai-benchmark-20261103.md](file:///d:/develop/Zhi_Yu/docs/ai-benchmark-20261103.md)。要点：
- 现有 20 条评测集扩为五层（红线安全 / 检索质量 / 对话质量 / 降级路径 / 注入防御），参考 EmoLLM 评测维度与 MindChat 中文心理语料形态。
- 新建离线 EvalRunner（JUnit 或独立 main，走 Mock + 真实 provider 双模式），跑分报告落盘 `docs/eval-runs/` 归档，**每次改 prompt/模型/检索参数必跑**。
- 线上抽样：从 `ai_audit_log` 每周抽样人工标注，离线跑分与线上抽样双轨，防「看起来智能」。
- 性质：现有代码可改 + 少量新文件（runner 与报告模板）。

### 3.6 管理与运营智能化 + 学生端「不打扰」

**管理端**：
1. **审核辅助分层**：内容安全过滤结果分三层——明确违规（关键词硬命中）自动拦截；灰区（模型判定不确定）进人工队列并附「建议理由」（AI 辅助不替代人审）；明确安全直接放行。审核辅助走 strong tier（§3.1），但**最终判定权在人**。
2. **看板洞察文案**：therapy-funnel 看板已有数据，新增「本周洞察」文案由 LLM 基于**聚合后的匿名统计数据**生成（不喂个体数据），带「数据解读仅供参考」标注。
3. **异常预警**：熔断器状态（已有 `CircuitBreakerStore`）+ 4001 降级率超阈值 → 管理端告警条。前置依赖：通知系统（当前缺失，属另一线任务）。

**学生端「不打扰」原则**：
- 心流状态（连续 5 分钟无切换，§7 已固化）下：**禁止**任何 AI 主动弹窗/推送/「小织想对你说」。
- 焦躁判定（同步骤停留 >3 次且呼吸未响应）只触发呼吸引导，不追加 LLM 话术。
- AI 生成内容一律可关闭（语音可静默、寄语可隐藏），默认克制。

**验收口径**：审核辅助分层在灰区用例上「人工队列命中率」可测；看板文案只基于聚合表（代码评审确认 SQL 无个体字段）；心流态 UI 测试断言无打扰元素出现。
**性质**：审核分层现有代码可改；预警依赖通知系统（新基础设施，P2）。

---

## 4. 优先级与排期

> 用户取向：无硬性时间节点，质量优先。下表按「依赖关系 + 风险降低速度」排序，周数为高质量路线的参考量级而非承诺。

| 优先级 | 事项 | 性质 | 最小可验证切片 | 参考量级 |
|---|---|---|---|---|
| **P0-1** | 评测基线：EvalRunner + 跑分归档（§3.5） | 现有代码可改 + 新 runner 文件 | 20 条现有用例在 Mock 与真实 provider 各跑一遍，报告落 `docs/eval-runs/`，CI/手动可重复 | ~1 周 |
| **P0-2** | 对话系统主从决策 + 推荐规则收敛 yaml 单写（§0-2） | 现有代码可改 | 决策记录进 docs；`mood→tag` 只剩 yaml 一份，Java 侧改为读取；改动后评测集 RECOMMEND 用例通过 | ~1 周 |
| **P0-3** | 危机/审核走 strong tier 的最小路由（§3.1） | 现有代码可改 | yaml `routing` 段生效；危机语料命中 strong；熔断降级链评测通过 | ~1 周 |
| **P1-1** | RAG 混合检索 + 引用强约束（§3.3） | 现有代码可改 | 15 条检索用例达标（FACT 引用 ≥90%，INSUFFICIENT 拒答 100%） | ~2 周 |
| **P1-2** | 跨会话摘要记忆层（§3.2） | 现有代码可改 + 1 张新表 | 授权→第 2 次会话体现偏好；撤回→无残留；注销→物理删除 | ~2 周 |
| **P1-3** | 工具集补到 5 个 + 状态机显式化（§3.4） | 现有代码可改 | trace 可见节点路径；评测集新增工具场景用例通过 | ~2 周 |
| **P2-1** | 审核辅助三层分流 + 看板洞察文案（§3.6） | 现有代码可改 | 灰区进人工队列；洞察文案只基于聚合统计 | ~2 周 |
| **P2-2** | 知识条目生命周期（借鉴 FastGPT） | 需要新基础设施（条目表 + 重建索引任务） | 管理端可修订条目并重建索引，版本留痕 | ~3 周 |
| **P2-3** | 异常预警通知（熔断/降级率） | 需要新基础设施（通知系统） | 依赖通知系统先落地 | 排期随通知系统 |
| **不做** | 自微调心理模型（MindChat/EmoLLM 路线）、多 Agent 框架引入、Milvus/Chroma 部署、动态模型打分路由 | — | — | 见 §5 禁用清单 |

---

## 5. 风险与红线（禁用清单）

### 5.1 医疗合规红线（踩了就是产品事故）

| 禁止项 | 原因 | 落地约束 |
|---|---|---|
| 任何「抑郁/焦虑/你患有…」等诊断性措辞（包括「疑似」「轻度xx倾向」） | 陪伴工具非医疗工具，AGENTS.md §1 红线 | 评测集红线层必须含诊断措辞检测用例；输出过滤拦截 |
| 高危情绪的「心理疏导结论」 | 高危只走危机干预流程 | 干预话术全部预置在 prompt/兜底文件，**LLM 不自由生成危机回应** |
| 用 LLM 输出直接替代人工审核结论 | 审核权在人 | 审核辅助只给「建议理由 + 分层」，不自动终审 |
| 对外宣称「疗效」「治愈率」「改善率」 | 无医疗资质 | 看板/洞察文案禁用疗效词；评测集 notes 已声明「不构成疗效证明」，保持 |

### 5.2 隐私红线

| 禁止项 | 原因 |
|---|---|
| 自由文本长期记忆 | 用户随手写的敏感信息会被永久记住；只允许白名单 key + 脱敏摘要 |
| 把个体对话/便签喂给「看板洞察」类生成 | 洞察只能基于聚合匿名统计 |
| 在前端做任何加解密、把任何 API key 下发前端 | AGENTS.md §4/§5；key 只在服务端环境变量 |
| 记忆数据绕过 AccountLifecycle 撤回/注销清理 | 撤回后必须无残留，评测用例覆盖 |

### 5.3 成本失控红线

| 禁止项 | 原因 |
|---|---|
| 无 budget 的 Agent 循环 | `CompanionOrchestrator` 已有每会话 budget，新增工具不得绕过 |
| 对话历史窗口无上限扩张 | 维持 `max_history_turns: 10`；扩窗需走评审 |
| 危机/审核以外的场景默认走 strong tier | 双档而非全量强模型；成本费率必须先录入 `.env` 再上量 |
| 无熔断新资源 key | 任何新 AI 调用点必须注册到 5 类资源 key 之一或新增独立 key，禁止裸调 |

### 5.4 工程纪律红线

| 禁止项 | 原因 |
|---|---|
| 绕过 `AiClient` 直接 RestTemplate/OpenAI 客户端 | AGENTS.md §4 |
| Prompt 写进 Java 代码 | 必须外置 `resources/prompts/*.st` |
| 把本地文件向量库写成「已部署 Milvus/Chroma」 | 文档真实性红线 |
| 无评测跑分记录就宣称「智能化提升」 | §3.5：先基线后改进 |
| 引入 GPL-3.0 依赖（如 MindChat 代码/模型） | 许可证不兼容；仅作语料形态参考 |

---

## 6. 引用来源汇总

- LangGraph：[github.com/langchain-ai/langgraph](https://github.com/langchain-ai/langgraph)（⭐43,962，MIT，2026-11-03 GitHub API 查证）、[官方文档](https://docs.langchain.com/oss/python/langgraph/overview)
- mem0：[github.com/mem0ai/mem0](https://github.com/mem0ai/mem0)（⭐36,607，Apache-2.0）、[Mem0 论文](https://arxiv.org/pdf/2504.19413)
- RAGFlow：[github.com/infiniflow/ragflow](https://github.com/infiniflow/ragflow)（⭐93,147，Apache-2.0）、[官方文档](https://ragflow.io/docs/)
- Dify：[github.com/langgenius/dify](https://github.com/langgenius/dify)（⭐89,287，自定义许可证）、[dify.ai](https://dify.ai/)
- FastGPT：[github.com/labring/FastGPT](https://github.com/labring/FastGPT)（⭐25,560，自定义许可证）、[官方文档](https://doc.fastgpt.cn/docs/faq)
- open-webui：[github.com/open-webui/open-webui](https://github.com/open-webui/open-webui)（⭐157,650，自定义许可证）、[openwebui.com](https://www.openwebui.com/)
- MindChat：[github.com/X-D-Lab/MindChat](https://github.com/X-D-Lab/MindChat)（⭐721，GPL-3.0）、[wisemodel 模型页](https://wisemodel.cn/models/X-D-Lab/MindChat)
- EmoLLM：[github.com/SmartFlowAI/EmoLLM](https://github.com/SmartFlowAI/EmoLLM)（⭐1,798，MIT）

> 仓库内事实来源：[ai-capabilities.yaml](file:///d:/develop/Zhi_Yu/ai-config/ai-capabilities.yaml)、
> [AiClient.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/ai/AiClient.java)、
> [CompanionOrchestrator.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/modules/companion/CompanionOrchestrator.java)、
> [CompanionMemoryService.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/modules/companion/memory/CompanionMemoryService.java)、
> [CompanionEvalCatalog.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/modules/companion/eval/CompanionEvalCatalog.java)、
> `zhiyu-backend/src/main/resources/eval/companion-eval.json`、`.env`（仅变量名）。
