# 本机 Ollama 演示兜底接入与实测报告（2026-11-07）

> **定位声明**：本文描述的是**演示兜底**链路。962 基线的所有数字来自云端 DeepSeek
> （deepseek-chat / deepseek-reasoner）；本机 `deepseek-r1:8b` **不是**语义等价物，
> 本文任何实测结果都**不构成**「AI 验收通过」的证据，只证明演示环境下四条链路真实可调通。
> 本地 7B 的输出质量、JSON 守约率与云端模型存在可见差距，见 §6。

## 1. 环境探测（动手改配置前完成）

| 项目 | 实测 |
|---|---|
| Ollama | 已安装，client v0.31.2；服务默认未运行，需 `ollama serve` |
| 模型 tag | **`deepseek-r1:8b`**（5.2GB，本机唯一模型；用户口径的 "7b" 即此 tag） |
| OpenAI 兼容端点 | `http://localhost:11434/v1` ✅（`/v1/models`、`/v1/chat/completions` 均可用） |
| GPU | RTX 4060 Laptop 8GB，驱动 566.24；`ollama ps` 显示 **100% GPU**，context 4096 |
| 冷启动 | 14.1s（含模型加载 + 275 tokens 生成） |
| 热态非流式 | 6.9s / 275 tokens（约 **40 tokens/s**） |
| 流式首字节 | 0.43s；但前 ~72% 的流是推理段，**可见正文首字约 5-6s** |

**DeepSeek-R1 输出形态（本版 Ollama）**：推理内容放在**独立的 `reasoning` 字段**
（流式在 `delta.reasoning`，原生 API 叫 `thinking`），正文 `content` 不混推理。
Spring AI 按 OpenAI schema 解析时自动忽略该字段——**推理段不会漏进业务正文，无需代码剥离**。

## 2. 接入方式（只动环境变量，未动任何仓库默认配置）

本次接入对 `application.yml` / `application-dev.yml` **零改动**（工作区中
`application.yml` 存在的未提交改动来自其他任务的软档影子配置，与本任务无关；
AI/DeepSeek 段保持原样）。测试实例在 **8081** 端口启动（不动 8080），环境变量：

```powershell
$env:DEEPSEEK_BASE_URL='http://localhost:11434'   # Spring AI 自拼 /v1/chat/completions
$env:DEEPSEEK_API_KEY='ollama-local-demo'          # 任意非 sk-placeholder 值；Ollama 不校验
$env:DEEPSEEK_CHAT_MODEL='deepseek-r1:8b'
$env:DEEPSEEK_STRONG_MODEL='deepseek-r1:8b'        # strong 档（审核/倾听小结）同指本地
$env:SERVER_PORT='8081'
$env:ZHIYU_CRYPTO_ALLOW_DEFAULT_SECRET='true'      # 仅本地 dev 密钥放行，与既有约定一致
mvn spring-boot:run "-Dmaven.repo.local=../.ai-tmp/m2"   # 在 zhiyu-backend 目录
```

链路不变：业务 → `AiClient` 门面（内容安全过滤 + 审计 + 熔断 + ai_trace）→
`SpringAiLlmClient`（Spring AI OpenAI starter）→ Ollama。未绕过任何一道约束。

## 3. 四条能力实测（全部真实调用、逐条分开说）

| 能力 | 入口 | 结果 | 实测延迟 |
|---|---|---|---|
| 情绪对话（SSE 句级流式） | `POST /api/emotion/chat` (mode=live) | ✅ 句级 chunk 正常，无推理段泄漏，done 事件 crisis/revoked 正常 | 首 chunk 12.4s/6.7s，全程 13.2s/7.9s（两次） |
| 手作推荐理由 | `GET /api/craft/recommend` | ✅ 理由为真实生成（含候选手作名），非降级文案 | 7.7s / 6.0s（两次） |
| AI 寄语 | `POST /api/works`（不传 message） | ✅ `work.message` 为真实生成寄语 | 7.2s |
| 审核建议 | `POST /api/admin/works/{id}/ai-suggest` | ✅ 用户可见 10/10 成功（`available=true`、tendency 合法、riskPoints 结构正确） | 7.4–38.9s，中位约 15s |

审核建议 10 次运行的 ai_trace 细节：**2 次 strong 档失败回落 standard**、
**2 次 JSON 重试**，全部被既有防线吸收，见 §4。

另：RAG 问答（`POST /api/ai/rag/ask`）在本实例走降级模式，见 §7。

## 4. 审核建议专项：推理模型 × 严格 JSON 的真实情况

### 4.1 推理段形态：不是问题的部分

本版 Ollama 把推理放独立 `reasoning` 字段，Spring AI 取 `content` 时天然隔离。
**不需要**为推理段改任何代码。

### 4.2 真正出问题的两处（均有实测证据）

**A. maxTokens=800 截断 → 空正文（2/10）**
运行配置默认 `maxTokens=800`（RuntimeSettings 默认值，非代码常量）。
推理模型的推理段**计入** completion 预算：800 tokens ÷ 40 tok/s ≈ 18.5-18.9s，
与两次 strong 档失败的耗时精确吻合；后端日志报 `empty moderation suggestion`
（推理吃光预算 → `content` 为空 → 抛错 → **显式回落 standard 档成功**）。

**B. 7B 的 JSON 守约率不足（端点级 A/B，6 次中 3 次违规）**
用应用同款渲染 prompt 直测 Ollama：

- 全角冒号 `"note"："…"`（JSON 非法）
- 值用单引号 + 内嵌未转义英文双引号（JSON 非法，即 p37 同类故障）
- 值不用引号直接写「…」（JSON 非法）
- tendency 输出非法枚举 `NO_OBVIOUS_ISSUE | HAS_CONCERNS`（JSON 合法但业务不合法）

应用侧 10 次运行中 2 次触发 `chatWithUsageJson` 的受控重试（ai_trace 输入 tokens
翻倍为 1544 可证），重试均成功。

### 4.3 选择的处理方式：不改代码，靠既有防线 + 一处运行配置

- **不新增剥离/修补逻辑**：`isParseableJson` 已容忍 markdown 围栏（与业务层口径一致）；
  语法违规由「同 prompt 重试 1 次」吸收；重试再败则 strong→standard 回落；
  再败则返回诚实的 `available=false`「暂无建议」。整条防线在本次实测中全部真实触发且生效。
- **截断问题用运行配置解决**：`runtime_config` 的 `model.maxTokens` 调为 **2048**
  （见 §5 SQL）。2048 下直测 3/3 `finish=stop`，不再截断。
  这是 DB 运行配置（管理端可调），不是代码改动；仓库默认值未动。
- **json_schema 结构化输出验证过但未接线**：Ollama 的 `/v1` 端点对 OpenAI 标准
  `json_schema` 实测可产出**语法级保证**的干净 JSON（一次通过、无围栏），
  能从根上消除 §4.2-B 的全部语法违规。但现有 `chatWithUsageJson` 走
  `JSON_OBJECT`（DeepSeek 云端只支持这个；`json_schema` 它需要按 provider 条件化
  改造 `SpringAiLlmClient`，超出「只动环境变量」的边界），故**本次不接**，
  仅作为后续可选增强记录。注意：Ollama 私有 `format` 字段在 `/v1` 端点被**忽略**，
  只在原生 `/api/chat` 生效；`json_object` 在任何一侧都只是指令级约束。

### 4.4 稳定性结论

2048 配置下，预期用户可见成功率接近 10/10，代价是 2-3 成调用会多一次
重试或档位回落（延迟翻倍到 15-39s）。偶发（<10%）双档皆败时前端展示
「暂无建议，请按人工流程审核」——这是**设计内**的诚实降级，不是故障。
（诚实声明：2048 的应用级复测受管理员频控 30 次/小时限制，本次仅完成端点级 A/B
验证与应用侧 trace 佐证；窗口释放后可按 §5 复测。）

## 5. 演示环境建议配置

**运行配置（一次性 SQL，演示后可删）：**

```sql
INSERT INTO runtime_config (config_key, payload)
VALUES ('model', '{"temperature":0.7,"maxTokens":2048}');
-- 删除：DELETE FROM runtime_config WHERE config_key='model';
```

**Ollama 侧：**

- 预热：演示前调一次任意生成（或 `ollama run deepseek-r1:8b "预热"`），
  把 14s 冷加载挡在演示外；并设 `OLLAMA_KEEP_ALIVE=1h`（默认 5 分钟卸载，
  实测 18:05 加载、18:21 重新加载，正中演示空档）。
- 并发：按**串行**准备。单模型单卡，并发请求排队，两条并行时各自延迟近似翻倍；
  演示脚本避免同时触发 SSE + 推荐。`OLLAMA_NUM_PARALLEL` 不建议调大（8GB 显存
  只剩约 1.3GB 余量，KV cache 会爆）。
- 显存：模型 5.6GB / 8GB，演示时关掉占显存的程序（浏览器硬件加速、游戏等）。

**超时核对（实测对照）：**

| 层 | 现值 | 是否够用 |
|---|---|---|
| 后端 SSE emitter | 120s | ✅ 足够 |
| 学生端 H5 sse.js | 无超时 | ✅ |
| 小程序 wx.request | 默认 60s | ⚠️ 够用但接近：2048 cap 下最坏约 50s+ |
| 学生端普通请求 | 60s | ✅（推荐/寄语 6-8s） |
| **管理端 axios** | **10s** | ❌ **审核建议 7.4-38.9s，约四成超时**。演示前把 `getWorkAiSuggestion` / `getCommentAiSuggestion` 两次调用的 timeout 显式提到 60s（`requestClient.post(url, data, { timeout: 60_000 })`），或接受「超时后点重新生成」（注意：重试消耗 30 次/小时频控额度）。本次未改前端代码，仅给出建议。 |

## 6. 已知限制（演示讲解口径）

1. **质量不可比**：7B 推荐理由/寄语可见模板化痕迹（开头多余 `\n`、偶发跑题推荐
   「纸鹤」等非候选手作）；审核建议的风险点质量低于云端基线。不得引用本文数字
   替代或对标 962 基线。
2. **首字延迟**：SSE 对话用户可见首字 5-12s（推理段不占正文但占时间），
   演示话术需预期管理；非流式能力 6-8s。
3. **JSON 违规残留风险**：重试+回落只能收敛不能根除，低频（本次 0/10 用户可见失败，
   端点级约 1/3 违规被防线吸收）「暂无建议」会出现。
4. **语音不在兜底范围**：ASR/TTS 走独立 SpeechClient（千问网关），Ollama 不覆盖；
   无密钥环境语音接口返回 4001「语音服务尚未配置」。
5. 单卡串行：并发即排队，不支持多人同时演示 AI 功能。

## 7. RAG embedding：不需要本地模型，影响范围已实测

- RAG 向量召回用的是**豆包 Embedding**（`DOUBAO_API_KEY`，火山引擎云端），
  与 LLM 端点完全解耦。Ollama 兜底 LLM **不影响**已有豆包配置——演示机加载
  `.env` 时 RAG 全功能照旧。
- 本次测试实例刻意不加载 `.env`（不动用真实云密钥），等价于「embedding 未配置」
  场景，实测降级行为：`POST /api/ai/rag/ask` 返回 **STRUCTURED_GRAPH 抽取式回答**
  （56ms，明确标注「未调用生成模型」），功能可用但无向量召回、无生成式摘要。
- 降级影响范围（embedding 缺位时）：`RagService` Bean 不创建 → 仅向量召回关闭；
  结构化图谱检索、审核建议、对话、推荐、寄语均不受影响。
- **不建议**为演示给 Ollama 配 embedding 模型（需联网拉模型、维度与既有向量文件
  不兼容、且属于替换生产配置）。

## 8. 演示当天执行清单（逐步照做；2026-11-07 全部实测通过）

> 约定：以下命令除注明外在仓库根目录 `d:\develop\Zhi_Yu` 的 PowerShell 执行；
> 后端命令在 `zhiyu-backend` 目录。本清单不产生任何仓库代码改动。

### 第 1 步：启动 Ollama 服务（带保活）

```powershell
$env:OLLAMA_KEEP_ALIVE='1h'; $env:OLLAMA_NUM_PARALLEL='1'; ollama serve
```

- 两个变量都是**服务端变量**，必须设在与 `ollama serve` 相同的进程环境里
  （对 `ollama` 客户端命令、对后端都无效）。`NUM_PARALLEL=1` 本就是 0.31.2 默认
  （启动日志 `server config` 行可见），显式写出防漂移。
- 保持该窗口开着；关窗口服务即停、设置即还原。
- 持久化替代（演示后想长期保留才用）：Windows「系统环境变量」加同名变量后
  重启托盘 Ollama；本清单不替你改系统设置。
- 实测验证：以此方式启动后，`ollama ps` 的 `UNTIL` 列为「59 minutes from now」
  （未设置时为「4 minutes from now」，实测 5 分钟即卸载并触发下一次冷加载）。

### 第 2 步：预热（约 10 秒，一次全热）

```powershell
powershell -ExecutionPolicy Bypass -File d:\develop\Zhi_Yu\.ai-tmp\ollama-probe\demo-prewarm.ps1
```

- 预热对象是**模型加载**（对话/推荐/寄语/审核共用同一模型，预热一次全部受益）；
  实测冷态 9.8s。成功标志：输出 `prewarm done in ...` 且 `ollama ps` 显示
  `100% GPU` + `UNTIL ≈ 59 minutes from now`。
- 脚本内容（如 .ai-tmp 被清理，可据此重建到任意路径；注意必须 ASCII/英文，
  PowerShell 5 对无 BOM 的 UTF-8 中文脚本会按 GBK 误读）：

```powershell
$ErrorActionPreference = 'Stop'
$bodyFile = Join-Path $PSScriptRoot 'prewarm-body.json'
'{"model":"deepseek-r1:8b","prompt":"warmup","stream":false,"options":{"num_predict":1}}' |
    Set-Content -Path $bodyFile -Encoding ascii
$sw = [System.Diagnostics.Stopwatch]::StartNew()
curl.exe -s http://localhost:11434/api/generate -d "@$bodyFile" | Out-Null
$sw.Stop()
Write-Host ("prewarm done in {0:N1}s" -f ($sw.ElapsedMilliseconds / 1000))
ollama ps
```

### 第 3 步：写入 maxTokens=2048（二选一；推荐接口方式）

**方式 A：管理接口（推荐，带范围校验与操作审计）**

admin 登录拿 token 后（`POST /api/auth/login`，账号 admin/admin123 仅本地）：

```powershell
# ① 读当前 revision（首次为 0）
curl.exe -s http://localhost:8081/api/admin/config/runtime -H "Authorization: Bearer <token>"
# ② 写入（body 里的 revision 填①读到的值）
# PUT /api/admin/config/runtime
# {"key":"model","revision":0,"value":{"temperature":0.7,"maxTokens":2048}}
```

- 校验规则（`RuntimeSettings.save`）：maxTokens ∈ [128, 2048] 整数；value 必须
  **同时**含 temperature 与 maxTokens（整行替换不做合并，缺字段会报错/空指针）；
  revision 乐观锁（并发改会得 3001，重新读取再改）。实测：PUT 后 revision 0→1。
- 生效时机：**无缓存、无开关**——`RuntimeSettings.values()` 每次调用实时查库，
  下一次 AI 调用即生效，**无需重启后端**。
- 生效确认：① 再 GET 一次看到 `revision=1, maxTokens=2048`；② 行为验证
  （本次实测）：审核建议 `ai_trace.output_tokens=1094`（>800 即证明新上限在起作用），
  且 strong 档无回落直连成功。
- 管理端「系统配置」页可视同一张表，改完效果相同。

**方式 B：SQL 直写（应急；无审计、绕过校验，演示环境专用）**

```sql
INSERT INTO runtime_config (config_key, payload)
VALUES ('model', '{"temperature":0.7,"maxTokens":2048}')
ON DUPLICATE KEY UPDATE payload = VALUES(payload);
```

**演示后恢复默认（800）：**

```sql
DELETE FROM runtime_config WHERE config_key = 'model';
```

### 第 4 步：启动后端（8081，指向本地 Ollama）

```powershell
# 在 zhiyu-backend 目录
$env:DEEPSEEK_BASE_URL='http://localhost:11434'; $env:DEEPSEEK_API_KEY='ollama-local-demo'
$env:DEEPSEEK_CHAT_MODEL='deepseek-r1:8b'; $env:DEEPSEEK_STRONG_MODEL='deepseek-r1:8b'
$env:SERVER_PORT='8081'; $env:ZHIYU_CRYPTO_ALLOW_DEFAULT_SECRET='true'
mvn spring-boot:run "-Dmaven.repo.local=../.ai-tmp/m2"
```

就绪标志：日志出现 `Started ZhiyuApplication` 与
`ModelRouter 初始化：standard=deepseek-r1:8b，strong=deepseek-r1:8b`。

### 第 5 步：管理端超时（已改好，无需操作）

- [admin.ts L124-130](../zhiyu-admin-vben/apps/web-ele/src/api/zhiyu/admin.ts#L124-L130)
  `getWorkAiSuggestion`、L132-137 `getCommentAiSuggestion`：
  仅这两个请求显式 `timeout: 60_000`（第三参 axios config），
  **全局 10s 默认（request-client.ts L66）未动**，其余请求一律不变。
- 验证：`vue-tsc --noEmit --skipLibCheck` 通过；`pnpm vite build --mode production` 通过。

### 学生端等待体验（现状确认，无需改动）

首字 6-12s 期间**不是静默等待**，现状已有四重反馈
（[chat.vue](../zhiyu-app/src/pages/emotion/chat.vue) 与 [emotion.js](../zhiyu-app/src/stores/emotion.js)）：

1. 发送瞬间即出现小织头像 + 空助手气泡，气泡内 `.typing` 打字机光标
   （0.8s 闪烁青绿竖条，`chat.vue` L37、L556-570）；
2. 头部状态文案变为「正在回应你…」（`chat.vue` L3）；
3. 小织宠物进入 thinking 动画（`emotion.js` L128-130 `showThinking()`）；
4. 输入框与发送按钮禁用，防止重复发送。

正文以句为单位流入同一气泡。**结论：现有等待态足以覆盖本地模型延迟，不需要新增 UI。**

## 9. 本次验证与清理记录

**环境**：Windows 11、RTX 4060 Laptop 8GB、Ollama 0.31.2、MySQL 8.0.43、
后端 dev profile @8081、`deepseek-r1:8b`。

**测试数据**（按 ID 清理，已复核残留为 0）：

| 数据 | ID |
|---|---|
| work（第一轮） | 2119013341899821059 |
| work（第二轮，2048 行为验证用） | 2119028846110973954 |
| emotion_session | 2119012362974433281、2119017357270618113（含 emotion_message） |
| craft_recommendation(+item) | 2119012575323656194、2119017415625969668 |
| runtime_config 临时行 | 第一轮 SQL 行 999000001；第二轮经 PUT 接口写入的 `config_key='model'` 行（均已删，恢复默认 800） |

`ai_audit_log` / `ai_trace` 为只追加表（AGENTS.md §5 例外），本次调用痕迹
（user_id=1/4，模型标识 `deepseek-r1:8b`）按约定**保留**，可从 `model` 列区分于云端记录。

**实例**：8081 后端与 `ollama serve` 均为本次任务启动，报告完成后已停止并确认端口释放。

**第二轮验证补充（2026-11-07 19:30 前完成）**：

- 管理端超时改动：`apps/web-ele/src/api/zhiyu/admin.ts` L124-130、L132-137
  （仅两请求 60s），`vue-tsc` 与 `vite build` 均通过。
- runtime_config 接口写入（PUT revision 0→1）与 SQL 方式均验证；无缓存，
  下一次调用即生效；行为证据：审核建议 `ai_trace.output_tokens=1094`（800 旧上限下
  必然截断为空），strong 档两次直连无回落（19.8s/27.2s）。
- 预热脚本冷态 9.8s；`OLLAMA_KEEP_ALIVE=1h` 下 `ollama ps` 显示 UNTIL≈59 分钟，
  且距上次调用 5 分钟后复验模型仍在载（旧默认 5 分钟即卸载，实测于第一轮复现）。
- 管理端全局 10s 默认（`request-client.ts` L66）与其他请求未动。
