# 文档与代码硬冲突清单（2026-11-03）

> 目的：评审最容易被打的就是「文档失信」。本清单把 docx（需求规格 / 概设 V4 / 详设上下篇）与
> 旧 markdown 同当前代码的硬冲突逐条列出，每条给出「文档说 / 代码实际 / 建议以哪个为准」。
> 依据 AGENTS.md §9：docx 与仓库内 `.md` / `.yaml` / `.sql` 冲突时**以仓库内为准**，
> 下表的「建议」不推翻该约定，只说明文档应往哪个方向修。
> 与 2026-11-02 审查报告（project-review-20261102.md §9）的关系：本表是其冲突清单的
> 刷新版，标注了 2026-11-02/03 两天的状态变化。

## 冲突条目

| # | 文档说 | 代码实际 | 建议以哪个为准 |
|---|---|---|---|
| 1 | docx：数据库共 17 张表 | `sql/schema.sql` 共 47 张表（grep `CREATE TABLE` 47 处） | **以代码为准**。docx 表清单冻结在早期版本，评审引用表数量时必须数 schema.sql，不引 docx |
| 2 | docx：SSE 逐字流式、delta 增量协议 | 2026-11-02 前为整段缓冲单事件（假流式）；**2026-11-02 起已实现 `mode=live` 句级真流式**（chunk/revoke/done 事件，见 openapi.yaml `/api/emotion/chat` 与 features/43-sse-live-stream.feature）；默认旧协议保留一个迭代周期兼容 | **以 openapi.yaml 为准**。docx 的「逐字 delta」与实际协议不同，但 live 协议是新的契约事实；docx 该段应整段重写 |
| 3 | docx：学生端引入 uni-sse 插件 | 自研 `zhiyu-app/src/utils/sse.js`（H5 fetch/ReadableStream，小程序 wx.request 分块 + `wx.canIUse` 检测 enableChunked 降级） | **以代码为准**。AGENTS.md §6 已明确「不要误写为已引入 uni-sse 插件」；docx 依赖清单删除 uni-sse |
| 4 | docx/概设：Milvus/Chroma 向量库 | 本地 JSON 文件向量库（`SimpleVectorStore`，`zhiyu.ai.rag.vector-store-path`）+ 豆包云 Embedding（`zhiyu.ai.doubao.*`）；Milvus 从未部署 | **以代码为准**。AGENTS.md §2 已写明「RAG 当前使用本地文件存储，不能写成已部署 Milvus/Chroma」；评审若问向量库，答「本地文件 + 云 Embedding」 |
| 5 | docx 详设 §5.5：关键词 + 语义双重安全识别 | 仅本地词库 + 正则（`KeywordContentSafetyFilter` 类注释明确「不做 LLM 语义判断」）；语义安全过滤**未实现** | **以代码为准**。安全红线不依赖语义层也成立（双侧关键词 + 高危词库 + 审计）；若评审要求语义层，属新增需求而非既有能力 |
| 6 | 概设 §3.8 / 详设 §5.7：失败重试 2 次指数退避 | `ai/client` 包内无任何 retry 逻辑（grep 无 `retry`）；仅有熔断（连续失败 5 次熔断 60 秒）+ 超时降级缓存图文 | **以代码为准**。重试 2 次**未实现**；现行容错语义是「熔断 + 降级」，docx 的重试描述应删除或单独立项 |
| 7 | docx：MinIO / 对象存储 | 本地私有存储 + 签名 URL（`MediaController` / `LocalMediaStorage`）；`MediaStorage` 是扩展接口，不等于已部署 MinIO | **以代码为准**。AGENTS.md §2 已写明；docx 部署图删掉 MinIO 节点 |
| 8 | docx 详设 §7.2 接口清单（/api/recommend 等） | 实际路径体系不同（/api/craft/recommend 等），openapi.yaml 145 条路径 | **以 openapi.yaml 为准**（AGENTS.md §6 契约先行）；docx 接口清单不再维护 |
| 9 | 详设下篇 NFR：500 并发、首字 <1.5s | 需求规格 §4.1：100 并发、首字 ≤2s；两份 docx 自相矛盾 | **以需求规格为准**（100 并发 / ≤2s）；实测见 docs/perf-baseline-20261102.md：修复后 20 并发 P95=1818ms 达标，>20 并发未验收 |
| 10 | project-context.md：uni-sse、Milvus、Spring Boot 3.2、「数据不出校园」 | sse.js 自研、本地 JSON 向量库、Spring Boot 3.4.13、Embedding 调豆包云端（向量化出域） | **以代码为准**。「数据不出校园」在云模型模式下不成立，属设计目标不是事实；project-context.md 需按本轮结论重写对应段 |
| 11 | AGENTS.md §2 模块清单 13 个 | 实际 18 个模块目录 | **已修复（2026-11-03）**：§2 已补齐 companion/crisis/practice/heritage/user，以代码为准 |
| 12 | AGENTS.md §7 心流判定「已固化」 | 已配置化（`zhiyu.flow.focus-idle-seconds` / `stuck-repeat-times` / `store`，与 ai-config/ai-capabilities.yaml `flow` 段对应） | **已修复（2026-11-03）**：§7 改为「阈值已配置化，默认值不要拍脑袋改」，以代码为准 |
| 13 | requirements-audit-20261021：登录连续失败限制、忘记密码「未完成」 | **2026-11-03 已实现**：5 次失败锁 15 分钟（Redis 计数 + 内存降级，不泄露账号是否存在）；`/api/auth/password/forgot` + `/reset` 一次性令牌（30 分钟、用后失效、只存哈希、通道可配置、未配置返回 3001「该功能暂未开放」） | **已修复（2026-11-03）**：audit 对应行本轮刷新，以代码 + features/44-auth-account-security.feature 为准 |
| 14 | requirements-audit-20261021：真实语音 / 会话小结 / 漏斗看板列为缺口 | 10-22 后已落地：千问 ASR/TTS（`QianwenSpeechClient`，`zhiyu.speech.provider=qianwen` 启用，默认 gateway）；`/api/emotion/sessions/{id}/summary/generate`（授权后 AI 生成小结）；`/api/admin/therapy-funnel`（`TherapyFunnelService`） | **以代码为准**；audit 对应行本轮刷新 |
| 15 | 管理端存在 forget-password 模板页 | 2026-11-03 前无任何找回流程；**后端本轮已提供 forgot/reset 接口**，管理端/学生端页面接线属前端两条线的范围 | **以代码为准**；前端页面是否接入以各自工程为准，接入前不宣称「用户可自助找回」 |

## 使用约定

1. 评审问答涉及以上条目时，一律按「代码实际」列回答，并指明证据文件；不引 docx 原文。
2. docx 是历史交付物，不逐条回改；如需对外统一口径，以本清单 + AGENTS.md + openapi.yaml 为准。
3. 新增冲突条目时带日期与证据（文件 + 行号或 grep 结果），标注「已修复」时必须给出修复证据。
