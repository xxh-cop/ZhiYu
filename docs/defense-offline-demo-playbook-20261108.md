# 答辩断网演示预案（2026-11-08，一页速查 + 现场操作手册）

> **使用方式**：§0 打印随身带；§1-§6 现场照念照敲。
> **数据纪律**：文中所有数字均为 2026-11-07/08 在本机（RTX 4060 Laptop 8GB、
> Ollama 0.31.2、`deepseek-r1:8b`）的实测值；没测过的条目明确标【未验证】。

> **2026-11-08 彩排修订记录**（真实云 key 取自 .env 全程彩排后修订，各处改动原因）：
> 1. **§0/§1 判断表**：真实 401 实测为**半兜底**（对话兜底、推荐/寄语不兜底），
>    原文"兜底自动生效（已验证）"不成立——同步路径 Spring AI 抛
>    `NonTransientAiException`，`CloudErrorClassifier` 只认
>    `RestClientResponseException`/`WebClientResponseException`，归类 FATAL 不兜底；
>    流式对话抛 `WebClientResponseException$Unauthorized` 所以兜底成功（后端日志实证）。
> 2. **§2 步骤③**：注入方式由"占位 key"改为**死端点**（`DEEPSEEK_BASE_URL=http://localhost:9`）。
>    占位 key=真实 401，会把推荐/寄语演成固定文案；死端点=ConnectException=UNAVAILABLE，
>    三能力全部兜底（已实测）。**现场唯一安全注入方式是死端点。**
> 3. **§3 差异表**：云端列补上真 key 实测延迟（原为"秒级"笼统值）；
>    【未验证】清单中 401/403 改为实测结论，429 保留并标注推断。
> 4. **§5 回滚**：补回滚实测耗时（就绪 17s、云端确认 28s）。
> 5. **§6 附录**：补 H5 dev server 起法（pnpm 10 preflight 坑）与审核建议频控提示。
> 6. 切换耗时首次实测：决定→后端就绪 37.3s、→首个用户请求返回 59.9s（含排障则另计，见 §2 末）。
>
> **2026-11-08 修订②（分类器修复，D2 对齐）**：彩排发现的半兜底根因是 `CloudErrorClassifier`
> 不认 Spring AI 的 `NonTransientAiException`/`TransientAiException`（同步 call 路径的 4xx 包装）。
> 已按 D2 修复：message 前缀 `^HTTP (\d{3})` 窄匹配 401/403→AUTH、429→UNAVAILABLE、
> Transient→UNAVAILABLE，其余（400/无状态码/非前缀字样）维持 FATAL。
> 修复后真实 401 实测（错 key 注入）：**推荐/对话/寄语全部兜底**（双段 trace：
> 393ms→6.6s / 239ms→7.0s / 104ms→6.4s），D2 的 ERROR 鉴权告警在三条链路均如实触发。
> 因此 **401（错 key）注入恢复为可用方式**，死端点保留为更稳备选（不依赖异常包装细节）。
> 相应更新 §0/§1 判断表、§2 步骤③、§3 结论段；§2 末补"切换嵌入架构段"的编排建议。

## §0 一页速查表（打印这张）

| 情形 | 十秒判断动作 | 判定依据 | 对策 |
|---|---|---|---|
| 云 key 无效/额度尽（真实 401/403） | 查 trace：`SELECT scene,provider,result_status FROM ai_trace ORDER BY id DESC LIMIT 5;` | **全兜底（2026-11-08 修订②修复后实测）**：三能力均双段——`deepseek FALLBACK`（104-393ms）→ `ollama OK`（6.4-7.0s）；日志有「云端鉴权失败（401/403）」ERROR 告警。（修复前曾是半兜底：推荐/寄语判 FATAL 不兜底，见修订记录） | 无需操作——兜底自动生效 |
| 云端点连不通（动态不可用，**演示注入形态**） | 同上 | 全能力双段：`deepseek FALLBACK PROVIDER_ERROR`（4-85ms 快速失败）+ 紧接 `ollama OK`（6.9-7.8s） | 无需操作——自动切本地（已实测：推荐 7.96s / 对话 TTFB 6.32s / 寄语 6.95s 全部本地真实生成） |
| 只是慢 | 连续调两次推荐 | 无 FALLBACK 行、provider 全 deepseek、延迟高 | 不动，等；超时 60s 才计失败 |
| 双端皆挂（云死+本地没起） | 推荐接口 <2s 返回固定文案「（暂时无法生成个性化理由…）」 | trace 两行 FALLBACK（deepseek + ollama——ollama 行存在即本地试过且失败） | 启动 Ollama（§2 第 1-2 步），约 10s 恢复 |

**保底口令**（任何情况都不慌）：「这是系统的本地兜底在接管，陪伴主流程不受云端影响。」

## §0.5 演示顺序（硬约束：审核建议只在云端在线段演示）

**为什么**：审核建议在本地是**明确不可用**（实测 41ms 返回「AI 暂时无法给出建议，请按人工流程审核」，不生成任何内容）——断网段跳去管理端点「AI 建议」，评委看到的就是一个「不可用」提示，把精心设计的诚实降级看成系统缺陷。

**约束**：断网环节**只演示学生侧**——情绪对话（SSE）、手作推荐、作品寄语；
「管理员看 AI 建议」必须排在**云端在线**时完成，不与断网段混排。

**编排 A（推荐：云端段在前）**
1. 云端在线：管理端审核建议（AI 倾向 + 风险点）→ 学生侧对话/推荐（云端速度，对照组）；
2. 宣布「现在模拟云端失效」，按 §2 步骤③ 重启（错 key=401 或死端点二选一，均实测；
   **宣布完立刻开讲架构段**——切换实测 37s 就绪 / 60s 首答，用讲双端点设计的 1 分钟盖住空窗，见 §2 末）；
3. 断网段：学生侧对话 → 推荐 → 寄语（本地兜底，指出气泡下「本地模型生成」归属标注）；
4. 收尾：展示 trace 里 `provider=deepseek→ollama` 的双段留痕，回滚。

**编排 B（断网段单独成段）**
1. 完整演示云端全功能（含管理端审核建议）；
2. 中场明确换页：「接下来是韧性环节」；
3. 断网段同 A 的第 3、4 步。适合时间充裕、想让兜底成为独立章节的讲法。

**违反顺序的现场现象**：断网段打开管理端点「AI 建议」→ 页面显示「AI 暂时无法给出建议，请按人工流程审核」→ 评委可能追问「是不是坏了」。预案应对：这就是设计（§3 表与 §4 展开版话术），但**不要让自己陷入需要解释的被动**——按顺序演就不会出现这一幕。

## §1 判断：怎么最快看出云挂了

| 情况 | 接口表现 | 日志关键字 | trace 行 |
|---|---|---|---|
| key 无效/额度尽（真实 401，2026-11-08 修订②修复后实测） | 对话/推荐/寄语均变慢但全部有真实内容（6.5-8.6s，本地接管） | `云端鉴权失败（401/403）…请尽快核查云 key`（ERROR，三条链路均触发） | 全双段：`deepseek FALLBACK`（104-393ms）→ `ollama OK`（6.4-7.0s）。（修复前为半兜底：同步路径 `NonTransientAiException` 判 FATAL，推荐/寄语固定文案） |
| 端点连不通（演示注入形态） | 对话/推荐/寄语均变慢但全部有真实内容 | `云端不可用（UNAVAILABLE），按 D2 策略走本地兜底` | 实测：`llm-recommend deepseek FALLBACK 85ms → ollama OK 10.3s`；2026-11-08 复测 7ms → 7.8s |
| 只是慢 | 有内容、更慢、无降级文案 | 无兜底日志 | 全 `deepseek OK`，无 FALLBACK |
| 双端皆挂 | 推荐=固定降级文案；对话 SSE 收 error 事件 | `本地兜底调用也失败` | 两行 FALLBACK（deepseek+ollama，ollama 行存在即试过本地） |

**最快判断（10 秒内，2026-11-08 实操：SQL 本身 0.1-1.8s）**：连点两次「手作推荐」——
- ~7-10s 出真实理由 → 兜底在服务（想确认是谁在服务，查上面那条 SQL 的 provider 列；
  deepseek FALLBACK → ollama OK 双段即自动切本地，401 与死端点两形态均如此）；
- <2s 出固定文案 → 双端皆挂（trace 有 `ollama FALLBACK` 行），去 §2 起 Ollama。

## §2 切换：命令与环境变量

> 设计即「自动兜底」：本地兜底开关默认开（`OLLAMA_FALLBACK_ENABLED=true`），
> 云不可用**不需要任何切换动作**。以下是把演示环境调到「确定可用」的步骤。

**演示前 10 分钟（一次性，约 1 分钟敲完）**

```powershell
# ① 起 Ollama（保活 1h、串行防显存爆；两个都是服务端变量，必须和 serve 同窗口）
$env:OLLAMA_KEEP_ALIVE='1h'; $env:OLLAMA_NUM_PARALLEL='1'; ollama serve
# ② 预热（约 10s，实测 9.8s；成功后 ollama ps 显示 100% GPU、UNTIL≈59 分钟）
powershell -ExecutionPolicy Bypass -File d:\develop\Zhi_Yu\.ai-tmp\ollama-probe\demo-prewarm.ps1
# ③ 起后端（zhiyu-backend 目录；两种注入方式二选一，均 2026-11-08 实测）
# 方式一（演示 key 失效剧情）：错 key=真实 401。修订②修复后三能力全部兜底，
#   且日志有 D2 的 ERROR 鉴权告警——适合讲"key 失效也能感知"的故事。
$env:DEEPSEEK_API_KEY='sk-invalid-demo'; $env:OLLAMA_FALLBACK_ENABLED='true'
$env:SERVER_PORT='8081'; $env:ZHIYU_CRYPTO_ALLOW_DEFAULT_SECRET='true'
mvn spring-boot:run "-Dmaven.repo.local=../.ai-tmp/m2"
# 方式二（更稳备选）：死端点（localhost:9 无监听）=ConnectException=UNAVAILABLE，
#   不依赖 Spring AI 异常包装细节。把上面第一行换成：
#   $env:DEEPSEEK_BASE_URL='http://localhost:9'
#   （错 key 曾在修订②修复前把推荐/寄语演成固定文案——修复后两方式等价，死端点仍最稳。）
# （云端段演示用真 key：只设 SERVER_PORT 与 ZHIYU_CRYPTO_ALLOW_DEFAULT_SECRET，
#  key 由 start-backend.ps1 从 .env 注入，全程不打印。）
# ④ maxTokens 调 2048（防推理模型截断空输出；不重启、立即生效，已实测）
mysql -uroot -p123456 zhiyu -e "INSERT INTO runtime_config(config_key,payload) VALUES('model','{\"temperature\":0.7,\"maxTokens\":2048}') ON DUPLICATE KEY UPDATE payload=VALUES(payload);"
```

| 配置 | 何时定 | 改了要重启吗 |
|---|---|---|
| `OLLAMA_KEEP_ALIVE` / `OLLAMA_NUM_PARALLEL` | 启动前 | 要重启 `ollama serve`（实测默认 5 分钟就卸载模型） |
| `DEEPSEEK_API_KEY` / `OLLAMA_FALLBACK_ENABLED` | 启动前 | 要重启后端 |
| `maxTokens=2048` | 临场可改 | **不要重启**（RuntimeSettings 无缓存，下次调用即生效，已实测：PUT 后 revision 0→1、`output_tokens=1094`） |
| `ZHIYU_LOCAL_DEMO_MODE`（影子演示总闸） | 启动前 | 要重启后端；与 `shadow.demo-providers` 组成双开关，只开一个不生效（已测） |

maxTokens 也可以走管理端「运行配置」页（runtime-config.vue）或
`PUT /api/admin/config/runtime`（body `{"key":"model","revision":N,"value":{"temperature":0.7,"maxTokens":2048}}`，revision 用 GET 读到的当前值）。

**切换实测耗时（2026-11-08 彩排，决定→用户可用）**：
- 决定切换（00:04:16）→ 停旧实例、带故障环境变量重启 → 后端就绪：**37.3s**（Ollama 已保活，预热不在切换路径上）；
- → 首个学生请求成功返回：**59.9s**。
- **编排建议：把切换做在讲架构那一段。** 宣布「模拟云端失效」后立刻开讲双端点设计
  （云为主/本地兜底/按能力分档，约 1 分钟），讲完后端恰好就绪、本地首答（7-8s）正好接上——
  空窗为零，评委看到的是"讲过的设计真的在跑"，而不是干等。不要让切换空坐在演示台上。
- 卡点实录：StopCommand 停 mvn 外壳后 java 子进程可能仍占端口（本次已释放，但要在敲重启前用
  `Get-NetTCPConnection -LocalPort 8081` 确认）；重启后第一次轮询就绪即通，无需额外等待。

## §3 切换后的可见差异（全部实测值）

| 能力 | 云端（基线，2026-11-08 真 key 实测） | 本地兜底实测 | 说明 |
|---|---|---|---|
| 情绪对话 SSE | TTFB **1.70s** / 全程 **2.11s** | 首 chunk **6.3-12.4s**，全程 7.6-13s | 页面有打字光标+「正在回应你…」等待态（已核对现状，不静默） |
| 手作推荐理由 | **1.56s** | **6.0-10.3s**（2026-11-08 复测 7.96s） | 真实生成，偶有跑题/开头多空行的 7B 痕迹 |
| AI 寄语 | **1.41s** | **6.5-7.2s**（2026-11-08 复测 6.95s） | 真实生成 |
| RAG 非遗问答 | 秒级 | 向量召回缺 key 时降级为**图谱抽取式**（实测 56ms，注明「未调用生成模型」） | embedding 走豆包独立线，与 Ollama 无关 |
| **审核建议** | **2.41s**（strong 档含推理，真 key 实测；30 次/小时频控，彩排+演示合计用量要预留） | **明确不可用**（40-41ms 返回「AI 暂时无法给出建议，请按人工流程审核」） | **这是设计不是故障**：D4 决策禁止本地 7B 顶替高判定质量要求的能力，宁可诚实说不可用（已实测 trace 无任何 ollama 行） |
| 倾听小结/陪伴编排/滚动摘要/线稿共创 | 正常 | 同样**不可用于本地**（D1 cloud-only），走固定模板/跳过 | 理由同上 |
| 危机干预 | 词库规则 | **完全一致**（规则层在 provider 之前，有顺序钉子测试守着） | 与模型无关，断网不影响 |

【已实测】真实 401/403（2026-11-08，错误 key + 真实端点）：**三能力全部兜底**（修订②修复后实测：
推荐 393ms→ollama 6.6s、对话 239ms→6.99s、寄语 104ms→6.4s，日志 D2 ERROR 鉴权告警×3）。
修复前曾为半兜底（同步路径 `NonTransientAiException` 盲区），已由 `CloudErrorClassifier` 窄匹配修复，
单测矩阵 13 条 + ai 包 198 条全绿。
【未验证】429 限流的真实兜底（分类器已按 D2 将 429 归 UNAVAILABLE，有单测覆盖；真实 429 未实测）；
物理拔网线后的整机行为（架构上后端/Ollama/MySQL 全在本机，预期可用，但未实测）；
多人同时演示的并发表现（Ollama 串行，未实测）；小程序端彩排未覆盖（本次仅 H5）。

## §4 现场话术（诚实版，照念）

**一句话版：**
「您现在看到的回复由校内本地模型实时生成——云端接口失效时系统会自动切到本地兜底，陪伴主流程不中断。」

**展开版（评委追问时）：**
「系统是云为主、本地兜底的双端点设计。云端一旦超时、限流或鉴权失败，对话、推荐、寄语这类陪伴能力会切到本机的小参数模型继续服务——您能看到响应变慢了几秒，这是真实代价。
有两件事我们刻意不做：一，审核建议这类判定质量要求高的能力，本地小模型不顶替，直接告诉您『暂不可用、请走人工』，不拿低质量结果冒充；二，情绪危机识别不走模型，是本地词库规则，断网也不受影响。每次调用用了哪个模型，后台都有留痕，可复查。」

**不要说**：「跟云端效果一样」「没有影响」。要说「能撑住基本陪伴，但有可见的变慢和质量差距，高要求能力明确降级」。

## §5 回滚（演示完 5 分钟内）

```powershell
# ① 恢复 token 配置（恢复默认 800）
mysql -uroot -p123456 zhiyu -e "DELETE FROM runtime_config WHERE config_key='model';"
# ② 停 Ollama（其 serve 窗口 Ctrl+C 或直接关掉）
# ③ 后端按日常方式重启（不再带演示环境变量；真实云 key 走 .env 由 start-backend.ps1 注入）
```

**回滚实测（2026-11-08）**：重启→就绪 **17s**；→对话确认 `done.provider=deepseek`、推荐恢复 1.3s：**28s**；
浏览器同会话内再发一条，气泡脚注与输入区「本地陪伴模式」提示条**均消失**（localMode 随新 done 重置，无需刷新页面）。
确认口令：发一条消息后查 `done.provider` 应为 `deepseek`，且学生端不出现任何「本地」字样。

**数据不污染口径**：
- 本地兜底期间的 `ai_trace` 行带 `provider=ollama`，与云端 `deepseek` 机械可区分——这是留痕不是污染，**保留**（审计表只追加）。
- 影子判定：双开关默认关 → 演示期**不会**产生本地影子行；即便演示开了本地影子，`shadow.demo-providers` 默认含 ollama，统计接口默认反向排除（已核对实现）。
- 演示建的测试作品/会话按 ID 物理删除（SQL 见 §6 附录），`ai_audit_log`/`ai_trace` 按只追加约定保留。

## §6 附录：现场可能用到的碎片

```sql
-- 查最近 AI 调用是谁在服务（10 秒判断用）
SELECT scene, model, provider, result_status, fallback_reason, latency_ms
FROM ai_trace ORDER BY id DESC LIMIT 8;

-- 清理演示测试数据（把 ID 换成实际值；清理后跑 UNION 复核应为 0）
DELETE FROM work WHERE id IN (<workId>);
DELETE FROM emotion_message WHERE session_id = <sessionId>;
DELETE FROM emotion_session WHERE id = <sessionId>;
DELETE FROM craft_recommendation_item WHERE recommendation_id IN (<recId>);
DELETE FROM craft_recommendation WHERE id IN (<recId>);
```

- 预热脚本内容备份（.ai-tmp 被清也能重建）：见 `docs/ollama-demo-fallback-20261107.md` §8 第 2 步。
- 完整实测依据：`docs/ollama-demo-fallback-20261107.md`（探测与延迟）、本批装配层 E2E（trace 双段证据）。
- 小程序端 wx.request 默认 60s 超时：本地对话最坏 ~50s（2048 cap × 实测 ~40 tok/s），贴边但不超【极限长度未逐项验证】；H5 无超时限制。
- **H5 dev server 起法（2026-11-08 彩排卡点）**：`pnpm dev:h5` / `pnpm exec uni` 会被 pnpm 10 的
  deps preflight 卡死（`ERR_PNPM_IGNORED_BUILDS`，esbuild 等 build script 未 approve，交互式
  `pnpm approve-builds` 不适合现场）。绕过：直接调二进制——
  `$env:VITE_API_BASE_URL='http://localhost:8081'; & node_modules\.bin\uni.cmd`（zhiyu-app 目录），
  约 28s 编译就绪，默认 5173 端口。
- 登录页路径是 `/#/pages/login/index`（不是 /pages/auth/login）；登录按钮前有隐私协议勾选框，不勾不能点。
