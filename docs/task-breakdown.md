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
- [x] T0.1 后端脚手架：Spring Boot 3.2、包结构、统一 Result、全局异常处理（错误码见 AGENTS.md §3）
  - Done：`mvn compile` 通过；`/api/auth/register`、`/api/auth/login` 可用；返回统一体。
- [x] T0.2 数据库：执行 `sql/schema.sql`、`sql/init-data.sql`；MyBatis-Plus 分页插件、逻辑删除、自动填充
  - Done：`User` 表 CRUD 单测通过；`create_time/update_time/deleted` 自动维护。
  - 测试库策略：单元测试用 **H2 + MySQL 模式**，DDL 见 `sql/schema-h2.sql`；JDBC URL **必须包含 `NON_KEYWORDS=USER`**（H2 2.x 中 `user` 是保留字，否则建表与 `SELECT ... FROM user` 都报 `expected identifier`）；集成测试用真实 MySQL。
- [x] T0.3 JWT 鉴权过滤器 + ThreadLocal 当前用户；角色拦截器
  - Done：未登录访问 `/api/emotion/**` 返回 1002；学生访问 `/api/admin/**` 返回 1003。
  - Done（HTTP 层）：未登录访问受保护接口 → **HTTP 401** + body `Result.error(1002)`；越权访问 → **HTTP 403** + body `Result.error(1003)`。
  - 实现归属：这两条路径由 `JwtAuthFilter` / 权限拦截器**直接写响应**，**不经过** `GlobalExceptionHandler`（该 handler 只接 `BusinessException` 与 `MethodArgumentNotValidException`）。
- [x] T0.4 跨域、全局异常、参数校验（`@Valid` + JSR-303）
  - Done：缺字段返回 1001；错误信息温和。
  - 必须覆盖的 5 类异常（**M0/T0.3 收尾批次已全部落地**）：
    1. `MethodArgumentNotValidException` —— **已处理**（`@RequestBody` + `@Valid`），1001 / HTTP 400
    2. `HandlerMethodValidationException` —— **已处理**（Spring Boot 3.2 / Spring 6.1 新增；`@RequestParam`、`@PathVariable` 校验失败走它，**不是** `ConstraintViolationException`），1001 / HTTP 400
    3. `HttpMessageNotReadableException` —— **已处理**（请求体 JSON 格式错），1001 / HTTP 400
    4. `NoHandlerFoundException` / `NoResourceFoundException` —— **已处理**，1004 / HTTP 404
       - Boot 3.2 默认 `spring.mvc.throw-exception-if-no-handler-found=false`，实际抛出并被接住的是 `NoResourceFoundException`；`NoHandlerFoundException` 是防御性保留
       - 匿名访问未注册路径会**先**被 Spring Security 拦成 401（1002），不泄露「哪些路径存在」
    5. `HttpRequestMethodNotSupportedException` —— **已处理**，1005 / HTTP 405
  - 对应错误码 `1004 资源不存在 | 404`、`1005 请求方法不支持 | 405` 已写入 AGENTS.md §3 与 `openapi/openapi.yaml`（所有 operation 均声明 404/405，且所有错误响应用 `ErrorResult`）。
  - **待验证**：第 2 项目前**没有可触发的接口**（现有接口只在 body 上用 `@Valid`，没有 `@RequestParam` / `@PathVariable` 上的约束注解），属「代码就绪、未经运行期验证」，回归见 T1.1。
- [x] T0.5 学生端 UniApp 工程初始化：pages.json、Pinia、request 封装（token 注入、401 跳登录）
  - Done：H5 能登录、拿到用户信息。
- [x] T0.6 管理端 Vue3 工程初始化：路由守卫、Element Plus、Pinia
  - Done：浏览器登录后看到空看板。
- [ ] T0.7 仓库基线：AGENTS.md 自检、`.env.example` 校验、SQL 可重复执行、`openapi.yaml` 可解析
  - Done：`sql/schema.sql` 与 `init-data.sql` 全新环境执行无报错；OpenAPI YAML 可被 Swagger/Apifox 导入。
- [x] T0.8 AI 资源占位（占位文件已在 T0.0 顺带完成，Done 待 M1 验证）
  - 内容：`resources/prompts/*.st` ×4（`companion.st` / `recommend.st` / `work_message.st` / `safety.st`）、`resources/lexicon/high-risk.txt`、`resources/fallback/` ×2。
  - Done：占位文件已建；PromptTemplate 加载验证待 M1 建 Spring 上下文后回填；Prompt 正文与高危词库的内容完善留到 T1.1 / T1.4。

## M0 收尾技术债（不阻塞 M1，建议 M1.5 或 M2 处理）

- [ ] 管理端主 chunk 90KB（Element Plus 通用组件共享）——
      可用 `manualChunks` 拆出独立 vendor chunk
- [ ] Analytics 页 CSS 51KB（ECharts 占大头）——
      ECharts 的动态 import 已把 JS 拆出 1MB，但 CSS 可进一步拆分
- [ ] Vite 构建 warning `[INEFFECTIVE_DYNAMIC_IMPORT]`：
      router 被 user.js/request.js 动态导入但被 main.js 静态导入 ——
      可选优化（改成静态导入或去掉动态）
- [ ] openapi 的 28 个 operation 缺 operationId ——
      M3 契约稳定后统一命名
- [ ] zhiyu-app 的模板保留了非目标平台的依赖（alipay / baidu /
      toutiao / quickapp / app-plus / harmony）——M1.5 评估裁剪

## M1 · 疗愈闭环 MVP（P0）

- [x] T1.1 `AiClient` 门面：LLM 对话（SSE）、推荐理由生成、作品寄语生成
  - Done：能流式返回；超时降级为图文。
  - 回归清单（承接 T0.4 遗留）：**本里程碑第一个使用 `@RequestParam` / `@PathVariable` 校验注解的接口**，
    必须补一条用例验证「校验失败 → HTTP 400 + `code=1001`」。
    T0.4 已写好 `HandlerMethodValidationException` 处理器，但 M0 阶段没有任何接口能触发它，
    属「代码就绪、未经运行期验证」。
- [x] T1.2 熔断：按资源 key 隔离（`llm-chat`/`llm-recommend`/`llm-message`/`asr`/`tts`），连续 5 次失败熔断 60s
  - Done：mock AI 故障后，第 6 次请求走降级。
- [x] T1.3 情绪会话：`emotion_session` + `emotion_message` 多轮；SSE 接口 `POST /api/emotion/chat`
  - Done：学生端 uni-sse 能逐字接收中文。
- [x] T1.4 内容安全过滤：输入输出双向过滤，命中时改安全话术；写 `ai_audit_log`
  - Done：输入辱骂/自杀倾向能被识别并落入审核日志。
- [x] T1.5 手作推荐：规则层（焦虑→编织、压力→扎染…）+ LLM 生成个性化理由
  - Done：返回 2–3 个项目，含理由。
- [x] T1.6 创作会话状态机：待开始/进行中/暂停/完成/放弃
  - Done：非法跳转返回 3001。
- [x] T1.7 分步引导：项目步骤、引导语、引导音频
  - Done：学生端按步骤前进，进度上报 heartbeat。
- [x] T1.8 作品发布：匿名上传照片、可见性控制、点赞、留暖评论
  - Done：作品能出现在社区流。

- [x] T1.9 学生端 5 个页面 + 小程序端联调
  - Done：H5 + 微信小程序端 4 步闭环全通过（实际拆为 T1.9a/b/c/d，2026-10 完成）

## M1 收尾技术债（不阻塞 M2，建议 M1.5 或 M2 处理）

**AI 与数据层：**
- [ ] RedisFlowStatStore 未用 Lua 脚本，依赖「同 session 心跳串行」假设
- [ ] `craft_session` 缺 `emotion_session_id` 外键，`mood` 暂用空字符串
- [ ] `work.like_count` 未做 `GREATEST(x, 0)` 保护
- [ ] `work.message` 明文存储（按 T1.5 reason 处理一致）
- [ ] MockLlmClient 与 SpringAiLlmClient 的切换依赖 `sk-placeholder` 字面值
- [ ] ContentSafetyFilter 未嵌入 AiClient，Controller 层显式调用

**测试与验证：**
- [ ] SSE happy path 的 MockMvc 测试受限（Security + async 上下文丢失）
- [ ] EmotionControllerTest 的 `@DirtiesContext` 是 workaround
- [ ] EasyCom 未真实验证（未实际使用 `<li-card>` 组件）

**前端与视觉：**
- [ ] `work/detail.vue` 点赞乐观更新的失败回滚未实现
- [ ] 小程序端呼吸动画性能待评估
- [ ] 小程序端 baseURL 硬编码（批 B 处理）

**契约与路径：**
- [ ] 仓库根 `resources/` 是 T0.8 路径笔误产物
- [ ] 项目 102/103 缺 init-data 步骤数据（批 B 处理）

## M1.5 收尾技术债（不阻塞 M2，建议 M2 处理）

**AI 伴侣（小织）：**
- [ ] 三张 PNG 素材（avatar/blink/talk）人物内容未对齐，
      眨眼时有轻微缩小感。CSS 无法根治，需用 Photopea 统一画布尺寸
- [ ] AI 伴侣的说话动效未接入 SSE——目前只能通过 Console
      `window.__aiStore.startTalking()` 触发，M2 时接入 emotion/chat 页
- [ ] 拖动后 click 抑制时长 100ms 是经验值，可能需要在真机上微调
- [ ] 小程序端未在微信开发者工具实测（pointer-events/
      动画效果均未验证）

**wot-design-uni：**
- [ ] CSS 变量覆盖只做了主色，衍生色（暗态/禁用态）是否完全
      统一为薄荷青需实测
- [ ] `theme.scss` 里的变量未与 `uni.scss` 的 SCSS 变量完全对应，
      存在两套体系

**首页：**
- [ ] 首页"今日推荐手作"是假数据（硬编码 3 条），M2 接真实推荐 API
- [ ] 心情打卡仅本地状态（ref），未接后端持久化
- [ ] 头像上的未读消息点 `hasNotice` 硬编码为 `true`

**视觉：**
- [ ] 三张 PNG 去背后背景可能不完全是纯透明（边缘半透明像素），
      在深色背景上可能出现白边
- [ ] 部分页面（如 chat、detail）的 tabBar 和底部布局在小屏机上
      可能挤压内容区

**其他：**
- [ ] 仓库根仍有 `leaf-island-uniapp-main` 残留文件需要清理
      （或已在 `.gitignore` 中）

**M1.5 批 B 走查发现：**
- [ ] 种子账号 `student01` / `admin` 等的密码 hash 与 `123456` 不匹配，
      后端返回 2001；需重新执行 `init-data.sql` 或排查 BCrypt 加密
- [ ] 发布作品页缺少图片上传区域，提交后新作品未出现在列表中
      （可能缺少必填字段或未关联创作会话 ID）
- [ ] 创作引导的呼吸模式未在走查中触发（心流检测逻辑待 M3）
- [ ] 小程序端 AI 伴侣的 `pointer-events` / 动画效果 / 拖动
      未在微信开发者工具中实测（仅验证了构建产物正确）

## M2 · 管理端 + 非遗内容（P1）

### M2 批 1：悬浮聊天面板（已完成）

- [x] M2-1 悬浮聊天面板：点击小织 → 悬浮面板 → 复用 SSE 对话
  - 新增 `ChatPanel.vue`（底部滑出 75vh，三段式：头部/消息区/输入区）
  - SSE 逻辑从 `chat.vue` 抽取到 `emotionStore`（`sendMessage`/`retryLast`/`abortSSE`）
  - `AiCompanion.vue` 点击改为眨眼 → 打开面板（不再弹气泡）
  - 面板打开时小织隐藏（`visible=false`，不动 `isHidden`）；关闭后恢复
  - `preserveMessages` 机制：面板关闭后跳 chat.vue 保留消息上下文
  - 小程序端键盘遮挡：`onKeyboardHeightChange` 条件编译 + `cursor-spacing`
  - H5 6 步测试全通过（打开/关闭/发消息/流式回复/消息保留/跨页面跳转）
  - 小程序构建通过（键盘行为待微信工具实测）

**M2 批 1 技术债：**
- [ ] 小程序端键盘遮挡方案未经真机/微信工具实测——`onKeyboardHeightChange` +
      `cursor-spacing` 在 fixed 面板内的实际效果待验证
- [ ] ChatPanel 未引入 AiCompanion.vue——面板内无小织形象（后续可考虑在面板头部
      加入小织 mini 形象 + 说话动效联动）
- [ ] `preserveMessages` 目前无场景触发——面板内暂无「跳转完整对话」按钮，
      后续加入时需在跳转前设 `emotionStore.preserveMessages = true`
- [ ] `emotionStore.sendMessage` 和 `retryLast` 中 SSE 配置代码重复（~30 行），
      可抽为内部方法 `_startSSE(content)`
- [ ] 面板和 chat.vue 共享 `scrollSignal`——如果同时渲染（不会发生但未防护），
      滚动信号会互相干扰

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
- [ ] T3.8 为 `openapi/openapi.yaml` 全部 operation 补 `operationId`（契约质量；补完可清掉 Redocly 的 28 条 `operation-operationId` warning）
  - 命名规则：`<模块><动作>`，小驼峰，如 `authRegister`、`craftSessionHeartbeat`、`adminDashboardStats`。
  - 注意：`operationId` 一旦定名会影响后续 SDK 生成，需先确认命名口径再批量补。

---

## 接口优先级

- P0：auth、emotion/chat、craft/recommend、craft/session、work/publish
- P1：inheritor/course、group、admin/audit、admin/dashboard
- P2：group/chat、直播、付费

## 测试要求

- 每个 P0 接口至少 1 个 happy path + 1 个异常路径 Gherkin 场景。
- AI 相关用例必须包含「AI 不可用」降级场景。
- 危机干预必须端到端可演示。
