# 任务拆解（Task Breakdown）

> 给人读，AI 辅助理解。按里程碑拆分，每个任务都有可验收的 Done 标准。
> 优先级：P0 必做 / P1 二期 / P2 增强。

## 里程碑概览

| 里程碑 | 目标 | 周期建议 |
|---|---|---|
| M0 工程地基 | 仓库、CI、数据库、鉴权跑通 | 第 1–2 周 |
| M1 疗愈闭环 MVP | 情绪对话 → 推荐 → 创作 → 发作品 | 第 3–6 周 |
| M2 管理端 + 非遗内容 | 审核、看板、传承人上传 | 第 7–9 周 |
| M3 安全合规 + 心流 + 试点 | 危机干预、加密、试点上线 | 第 10–12 周 |

---

## M0 · 工程地基（P0）

- [x] T0.0 契约与文档修正：按 AI Agent 审查口径统一 A/B/C 三组口径，修正 `sql/`、`openapi/openapi.yaml`、`features/`、`ai-config/` 与文档配置
  - Done：`schema.sql` + `dict-*.sql` + `init-data.sql` 可重复执行无报错；`openapi.yaml` 通过 lint 与结构校验；7 个 `.feature` 均可被 Gherkin 解析且 Background 生效。
- [ ] T0.1 后端脚手架：Spring Boot 3.2、包结构、统一 Result、全局异常处理（错误码见 AGENTS.md §3）
  - Done：`mvn compile` 通过；`/api/auth/register`、`/api/auth/login` 可用；返回统一体。
- [ ] T0.2 数据库：执行 `sql/schema.sql`、`sql/init-data.sql`；MyBatis-Plus 分页插件、逻辑删除、自动填充
  - Done：`User` 表 CRUD 单测通过；`create_time/update_time/deleted` 自动维护。
  - 测试库策略：单元测试用 **H2 + MySQL 模式**，DDL 见 `sql/schema-h2.sql`；JDBC URL **必须包含 `NON_KEYWORDS=USER`**（H2 2.x 中 `user` 是保留字，否则建表与 `SELECT ... FROM user` 都报 `expected identifier`）；集成测试用真实 MySQL。
- [ ] T0.3 JWT 鉴权过滤器 + ThreadLocal 当前用户；角色拦截器
  - Done：未登录访问 `/api/emotion/**` 返回 1002；学生访问 `/api/admin/**` 返回 1003。
- [ ] T0.4 跨域、全局异常、参数校验（`@Valid` + JSR-303）
  - Done：缺字段返回 1001；错误信息温和。
- [ ] T0.5 学生端 UniApp 工程初始化：pages.json、Pinia、request 封装（token 注入、401 跳登录）
  - Done：H5 能登录、拿到用户信息。
- [ ] T0.6 管理端 Vue3 工程初始化：路由守卫、Element Plus、Pinia
  - Done：浏览器登录后看到空看板。
- [ ] T0.7 仓库基线：AGENTS.md 自检、`.env.example` 校验、SQL 可重复执行、`openapi.yaml` 可解析
  - Done：`sql/schema.sql` 与 `init-data.sql` 全新环境执行无报错；OpenAPI YAML 可被 Swagger/Apifox 导入。
- [ ] T0.8 AI 资源占位（占位文件已在 T0.0 顺带完成，Done 待 M1 验证）
  - 内容：`resources/prompts/*.st` ×4（`companion.st` / `recommend.st` / `work_message.st` / `safety.st`）、`resources/lexicon/high-risk.txt`、`resources/fallback/` ×2。
  - Done：应用启动时 `PromptTemplate` 加载不报错（**待 M1 验证**）；Prompt 正文与高危词库的内容完善留到 T1.1 / T1.4。

## M1 · 疗愈闭环 MVP（P0）

- [ ] T1.1 `AiClient` 门面：LLM 对话（SSE）、推荐理由生成、作品寄语生成
  - Done：能流式返回；超时降级为图文。
- [ ] T1.2 熔断：按资源 key 隔离（`llm-chat`/`llm-recommend`/`llm-message`/`asr`/`tts`），连续 5 次失败熔断 60s
  - Done：mock AI 故障后，第 6 次请求走降级。
- [ ] T1.3 情绪会话：`emotion_session` + `emotion_message` 多轮；SSE 接口 `POST /api/emotion/chat`
  - Done：学生端 uni-sse 能逐字接收中文。
- [ ] T1.4 内容安全过滤：输入输出双向过滤，命中时改安全话术；写 `ai_audit_log`
  - Done：输入辱骂/自杀倾向能被识别并落入审核日志。
- [ ] T1.5 手作推荐：规则层（焦虑→编织、压力→扎染…）+ LLM 生成个性化理由
  - Done：返回 2–3 个项目，含理由。
- [ ] T1.6 创作会话状态机：待开始/进行中/暂停/完成/放弃
  - Done：非法跳转返回 3001。
- [ ] T1.7 分步引导：项目步骤、引导语、引导音频
  - Done：学生端按步骤前进，进度上报 heartbeat。
- [ ] T1.8 作品发布：匿名上传照片、可见性控制、点赞、留暖评论
  - Done：作品能出现在社区流。

## M2 · 管理端 + 非遗内容（P1）

- [ ] T2.1 传承人端：项目/步骤/语音上传，提交后待审核
- [ ] T2.2 管理员：内容审核队列、上下架
- [ ] T2.3 数据看板：闭环完成率、心流时长占比、作品数（ECharts）
- [ ] T2.4 团体活动管理：发布活动、报名、统计
- [ ] T2.5 配置管理：推荐规则、AI Prompt、模型参数后台可配

## M3 · 安全合规 + 心流 + 试点（P0/P1）

- [ ] T3.1 心流检测：连续 5 分钟无切换=心流；同步骤 >3 次未响应呼吸=焦躁
- [ ] T3.2 呼吸引导动画（4s 吸 / 6s 呼）
- [ ] T3.3 危机干预流程：AI 识别高危 → 温和回应 → 展示热线 → 一键求助 → 写关怀提醒
- [ ] T3.4 敏感字段加密（对话/便签）；服务端解密后返回
- [ ] T3.5 隐私：首次进入单独同意、撤回授权、导出/删除入口
- [ ] T3.6 试点：心理中心 4 周，30–50 人，前后 PHQ-9/GAD-7（仅自我了解，不做诊断）+ 满意度
- [ ] T3.7 RAG：非遗知识库向量化（Milvus/Chroma），推荐时召回

---

## 接口优先级

- P0：auth、emotion/chat、craft/recommend、craft/session、work/publish
- P1：inheritor/course、group、admin/audit、admin/dashboard
- P2：group/chat、直播、付费

## 测试要求

- 每个 P0 接口至少 1 个 happy path + 1 个异常路径 Gherkin 场景。
- AI 相关用例必须包含「AI 不可用」降级场景。
- 危机干预必须端到端可演示。
