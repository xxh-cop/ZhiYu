# AGENTS.md — AI 协作约定（织愈）

本文件是所有 AI 编码 Agent（Cursor / Copilot / Claude Code / 豆包等）进入本仓库后的**第一阅读物**。
请在动手改代码前完整读本文件，并遵守其中的硬约束。

---

## 1. 项目一句话

**「织愈」**：AI 引导下的非遗艺术疗愈平台。学生端以 UniApp（微信小程序 + H5）提供情绪倾听、手作推荐、专注创作与作品分享；管理端以 Vue3 Web 提供内容审核、数据看板与团体活动。
口号：**用一针一线，缝补青春的焦虑。**

**产品定位红线（不可越过）**：
- 这是**陪伴与引导**工具，**不是医疗/诊断工具**。
- 前端禁止出现「抑郁症」「焦虑症」「你患有」等诊断性结论。
- 任何页面/对话流都必须能看到「本系统不能替代专业心理咨询」声明。
- 高危情绪（自伤、绝望）走危机干预流程，**不做心理疏导结论**。

## 2. 工程入口与技术栈（核对日期：2026-10-21）

版本以各工程 package.json、pnpm catalog、锁文件与 pom.xml 为准；下表描述现有实现，不授权升级框架。学生端与管理端独立构建，禁止混用依赖。

| 层 | 当前实现 |
|---|---|
| 后端 | Java 17、Spring Boot 3.4.13、Spring AI 1.0.0、MyBatis-Plus 3.5.7 |
| 数据 | MySQL 8；Redis 用于共享状态；RAG 当前使用本地文件存储，不能写成已部署 Milvus/Chroma |
| 学生端 | UniApp、Vue 3.4、Pinia 2、Vite 5；Wot Design Uni 与仓库自有组件；H5 + 微信小程序 |
| 当前管理端 | Vue Vben Admin 的 Element Plus 工程；Vue 3.5、Pinia 4、Vite 8、Element Plus 2、ECharts 6（以 catalog 为准） |
| 媒体 | 当前本地私有存储、授权播放；MediaStorage 是扩展接口，不等于已部署 MinIO |
| AI | AiClient 统一接入；可配置云模型/本地端点，真实可用性取决于环境；无配置的降级不算真实 AI 验收 |
| 鉴权 | 共用后端 JWT，Authorization: Bearer <token>；以服务端当前身份和授权为准 |

**实际维护的三个工程**：
- `zhiyu-backend/` —— Spring Boot 后端。
- `zhiyu-app/` —— UniApp 学生端。
- `zhiyu-admin-vben/apps/web-ele/` —— 当前管理端；在 `zhiyu-admin-vben/` 用 pnpm 安装依赖。
- `zhiyu-admin/` —— 早期管理端，仅保留历史实现。除非任务明确指向它，不在此目录实现新功能。

`Leaf Island UI`、旧管理端目录和旧模板版本属于历史说明，不作为当前选型依据。

**包结构约定**（后端 Maven 多模块或单模块均可，包名前缀 `com.zhiyu`）：
```
com.zhiyu
├── common/        # Result 包装、异常、常量、工具
├── config/        # Spring 配置（跨域、MyBatis-Plus、Redis、SSE、熔断）
├── security/      # JWT 过滤器、ThreadLocal 当前用户
├── modules/
│   ├── auth/      # 注册登录
│   ├── emotion/   # 情绪会话、消息、便签、AI 对话
│   ├── craft/      # 手作项目、步骤、推荐、创作会话
│   ├── work/       # 作品、评论、点赞
│   ├── group/      # 互助小组
│   ├── inheritor/  # 传承人
│   ├── admin/      # 内容审核、看板、团体活动、配置
│   ├── audit/      # AI 审计日志
│   ├── campus/     # 校园身份人工核验
│   ├── care/       # 授权关怀跟进
│   ├── growth/     # 自评、成长、课程、预约与反馈
│   ├── file/       # 媒体授权、存储、清理重试
│   ├── speech/     # 语音接入与配置状态
│   ├── companion/  # 陪伴编排（记忆、工具、消息、评估）
│   ├── crisis/     # 危机干预热线与流程
│   ├── practice/   # 短练习、专注会话与创作产物
│   ├── heritage/   # 非遗目录、知识图谱与共创
│   ├── notification/ # 通知（审核/预约/小组/关怀事件触达，未读数短缓存）
│   └── user/       # 个人资料、账号设置与密码修改
```

## 3. 错误码（全局唯一，不要新造）

| code | 含义 | HTTP |
|---|---|---|
| 0 | 成功 | 200 |
| 1001 | 参数校验失败 | 400 |
| 1002 | 未登录 / token 失效 | 401 |
| 1003 | 无权限 | 403 |
| 1004 | 资源不存在 | 404 |
| 1005 | 请求方法不支持 | 405 |
| 2001 | 用户名或密码错误 | 200（业务失败） |
| 2002 | 用户名已存在 | 200 |
| 3001 | 非法状态流转 | 200 |
| 4001 | AI 服务不可用（已降级） | 200 |
| 5000 | 服务器内部错误 | 500 |

> 注：错误码 `4001` 仅用于**非 SSE 接口**（推荐、寄语等）。SSE 接口（`POST /api/emotion/chat`）
> 的 AI 故障不使用 `4001`，改用 `event: error` 推送，前端收到后**降级为缓存图文**。

> 注：`1002` / `1003` 由 Spring Security 过滤器链**直接写出响应体**，不经过
> `GlobalExceptionHandler`（401/403 发生在 DispatcherServlet 之前）。JWT 未通过（未带 token、
> token 非法或过期）时返回 **HTTP 401 + body `Result(1002)`**；已认证但权限不足时返回
> **HTTP 403 + body `Result(1003)`**。两者**都不是空响应体**，且 `data` 固定为 `null`。

> 注：`1004` / `1005` 由 `GlobalExceptionHandler` 捕获 `NoResourceFoundException` /
> `NoHandlerFoundException` 与 `HttpRequestMethodNotSupportedException` 后返回，
> HTTP 状态分别为 **404 / 405**。注意顺序：**匿名**访问未注册路径仍先被 Spring Security
> 拦成 401（不泄露「哪些路径存在」），只有**已认证**用户的路径 / 方法失误才拿到 1004 / 1005。

统一响应体：
```json
{ "code": 0, "message": "操作成功", "data": {} }
```

### 3.1 角色码（全局唯一，不要新造）

| code | 角色 | 说明 |
|---|---|---|
| 0 | 学生 | 默认角色；管理端守卫拒绝，跳转学生端 |
| 1 | 传承人 | 管理端守卫放行（可维护非遗项目与课程） |
| 2 | 咨询师 | 管理端守卫放行（可查看脱敏关怀汇总） |
| 3 | 管理员 | 管理端守卫放行（可访问 `/api/admin/**`） |

> 与 `sql/schema.sql` 的 `user.role TINYINT` 一一对应，后端由
> `com.zhiyu.modules.auth.enums.RoleEnum` 承载。管理端路由守卫按 `role ∈ {1,2,3}` 放行，
> `role = 0` 跳转学生端或提示无权限。两端可有各自登录页面，但共用认证接口与账号体系；
> 公开注册只创建学生账号，不提供自助提权。

## 4. AI 能力硬约束

- 所有 AI 调用必须经过统一门面 `AiClient`，**禁止业务代码直接 RestTemplate/OpenAI 客户端**。
  `AiClient` 内部持有 `LlmClient`（对话/推荐/寄语）、`EmbeddingClient`（RAG 向量）、`SpeechClient`（ASR/TTS）；业务只调 `AiClient`。
- 所有 AI 输入输出必须经过**内容安全过滤**，并落 `ai_audit_log`。
- AI 调用必须配熔断：**连续失败 5 次 → 熔断 60 秒**，按资源 key 隔离（`llm-chat`/`llm-recommend`/`llm-message`/`asr`/`tts`），不得全局共享一个计数器。
- 超时降级：AI 超时 → 返回缓存的图文推荐理由 + 图文引导，不得让用户白屏。
- Prompt 模板外置到 `zhiyu-backend/src/main/resources/prompts/*.st`，不要硬编码在 Java 里。
- 外部 API Key 只存服务端环境变量，**绝不允许下发到前端**。
- 例外：非 AI 场景的文本过滤（如用户评论 `WorkInteractionServiceImpl.comment`）可直接使用 `ContentSafetyFilter`，无需走 `AiClient`。

## 5. 数据与隐私硬约束

- 所有业务表必须含 `id`、`create_time`、`update_time`、`deleted`（逻辑删除）；
  **审计日志表 `ai_audit_log` 例外**，只追加不更新不删除，可只保留 `create_time`。
- **主键策略**：主键由应用侧 MyBatis-Plus `ASSIGN_ID`（雪花算法）生成，**DDL 不设 `AUTO_INCREMENT`**；
  种子数据可手动指定小整数 ID。
- 敏感字段（对话内容、心情便签）**应用层加密**，密钥只存服务端；
  前端拿到的永远是服务端解密后的明文，**不得在前端做加解密**。
- **上一条的例外**：`care_reminder.summary` 存**脱敏文本、不加密**（已去除可识别身份的信息，供授权角色查看）；
  `ai_audit_log.raw_input` / `ai_output` **加密存储**。
- `work.message`（AI 寄语）为**非敏感字段例外**，明文存储（T1.8a 裁决①，公开展示无隐私风险）。
- 查询必须带「本人数据」或「授权范围」过滤；咨询师看到的是脱敏汇总。
- 用户注册默认 `can_contact=0`；咨询师联系用户必须由用户单独授权，代码不得默认置 1。
- 建表 DDL 一律放 `sql/schema.sql`，**不要写在 .md 代码块里**；初始化数据放 `sql/init-data.sql`。

### 5.1 数据库升级与媒体权限

- 新建结构维护在 sql/schema.sql；已有数据库必须有 sql/migrations 增量脚本，不能只改 CREATE TABLE IF NOT EXISTS。
- 结构修改同时核对 H2 测试结构与启动 SchemaCompatibilityCheck。迁移先备份，在测试库验证首次执行和重复执行，不用 reset.sql 修复缺列。
- 雪花 ID 在前端/API 中按字符串传递，禁止转成 JavaScript Number 后再提交。
- 私有媒体不能退回公开 uploads 路径。读取、播放、下载均检查本人/受众权限；撤销授权、退出小组、下架、注销后重新鉴权。
- 撤回隐私授权、导出和注销使用已有 AccountLifecycle/清理机制，禁止顺手删除其他用户数据。临时验收数据只能按本次明确记录的 ID 清理。

## 6. 接口约定

- 路径前缀统一 `/api`；学生端与管理端共用同一套后端，靠 JWT 角色区分。
- 对话接口 `POST /api/emotion/chat` 返回 `text/event-stream`（SSE）；学生端使用 `src/utils/sse.js` 自研适配（H5 fetch/ReadableStream，小程序 wx.request 分块），不要误写为已引入 uni-sse 插件。
- 接口契约以 `openapi/openapi.yaml` 为准，改接口先改 YAML 再改代码。

## 7. 心流判定（阈值已配置化，默认值不要拍脑袋改）

- **心流**：连续无步骤切换且无主动退出超过 `zhiyu.flow.focus-idle-seconds`（默认 300 秒）→ 减少打扰。
- **焦躁**：同一步骤反复停留次数超过 `zhiyu.flow.stuck-repeat-times`（默认 3 次）且呼吸提醒未响应
  → 弹出 4s 吸 / 6s 呼呼吸引导（呼吸参数见 `ai-config/ai-capabilities.yaml` 的 `flow.breathing` 段）。
- 阈值与存储（`zhiyu.flow.store`: memory|redis）均已配置化，默认值与
  `ai-config/ai-capabilities.yaml` 的 `flow` 段一一对应；改默认值必须两边同步，
  且属产品级决策，不得在单次任务里顺手调参。

## 8. 提交前自检（DoD）

- [ ] 新接口已在 `openapi/openapi.yaml` 登记。
- [ ] 新表/字段已写进 `sql/schema.sql`，并更新 `init-data.sql` 基础字典。
- [ ] 新功能有对应 `.feature` Gherkin 场景（至少 1 个 happy path + 1 个异常路径）。
- [ ] AI 相关调用走 `AiClient` 门面 + 安全过滤 + 审计日志。
- [ ] 不出现诊断性医疗措辞；涉及心理的页面有免责声明。
- [ ] 后端修改执行有针对性的测试和编译；前端修改执行对应构建，跨端组件需 H5 与小程序构建，管理端需类型检查。
- [ ] 表单有可见标签、键盘焦点、加载/错误/空状态；手机无横向溢出；不依赖仅鼠标点击的 view 完成关键动作。
- [ ] 不提交硬编码密钥；日志、截图、测试报告不包含真实密码、token、对话或个人资料。
- [ ] 交付说明区分「已实现」「本次验证」「历史验证」「未接入/待验收」。构建、H2 测试、浏览器模拟不能代替 MySQL、真机、真实外部服务或压测验收。
- [ ] 新接口/字段/状态流转同步契约、迁移、场景与状态文档；不把旧任务框的勾选作为唯一完成依据。

### 8.1 工作区与验证约定

- 先看 git status 和局部 diff，保留用户及前轮未提交工作；不要为了整洁重写整个仓库或删除不明文件。
- 临时脚本与日志放 .ai-tmp/；截图放 output/playwright/。业务源文件归入对应工程，不放根目录。
- 编译/测试前检查本任务启动的后端进程，停止后再编译，避免在线 class 被覆盖导致 NoClassDefFoundError；不要结束用户启动的其他进程。
- 运行说明与业务缺口分别维护 README 和 docs/requirements-audit-20261021.md；阶段报告保留日期，不能当作永久有效的当前验收结果。
- UI 复用已有设计令牌、AccessibleButton/AccessibleDialog 等组件，保留小织，不擅自增加新吉祥物。外部图片/音乐保留来源与授权。


## 9. 关键文档索引

| 文档 | 路径 |
|---|---|
| 最新需求差距与验收边界 | `docs/requirements-audit-20261021.md` |
| 历史集中验收记录 | `docs/final-delivery-checklist.md` |
| 项目上下文/架构约束 | `docs/project-context.md` |
| 任务拆解 | `docs/task-breakdown.md` |
| 隐私政策/用户协议 | `docs/privacy-policy.md` |
| 视觉规范（色板/字体/间距） | `docs/ui-guidelines.md` |
| Gherkin 用例 | `features/` |
| OpenAPI 契约 | `openapi/openapi.yaml` |
| AI 能力配置 | `ai-config/ai-capabilities.yaml` |
| 数据库 DDL | `sql/schema.sql` |
| 初始化数据 | `sql/init-data.sql` |
| 环境变量模板 | `.env.example` |

> 根目录 docx（概要设计说明书 V4 / 详细设计说明书上、下篇 / 需求规格说明书）为历史交付物；
> 当 docx 与仓库内的 `.md` / `.yaml` / `.sql` 冲突时，**以仓库内为准**。

## 10. 环境注意（Windows + DSH 沙箱）

- **Maven 本地仓库**：默认路径 `D:\Java\mvn-repository` 在工作区之外，沙箱会拒绝写入并报
  `AccessDeniedException`。拉依赖时加 `"-Dmaven.repo.local=../.ai-tmp/m2"`（在 zhiyu-backend 目录运行）指到仓库内。
- **出网**：网络与权限限制以当前工具环境为准。必要下载被沙箱阻断时使用工具规定的权限申请，不换工具绕过限制；不得关闭 TLS 校验。
- **中文文件分析**：**禁止**用 pwsh 的 `Get-Content` / `Select-String` 读本仓库的 `.md` / `.sql`
  （会把 UTF-8 当 GBK 解码，产生乱码并合并相邻行）；一律用 node 或 Java 显式按 UTF-8 读取。
- **运行期读 UTF-8 中文资源**：读取 UTF-8 中文资源（`resources/prompts/*.st`、
  `resources/lexicon/*.txt`、`resources/fallback/*.md`）时，必须显式指定 UTF-8，
  不得依赖 `file.encoding`（JDK 17 在 Windows 下默认 GBK）。
  例：`new InputStreamReader(in, StandardCharsets.UTF_8)`
- **改 `openapi/openapi.yaml`**：优先使用可用的补丁/编辑工具（如 apply_patch），保持改动局部。修改后解析 YAML，检查路径和 operationId。遇到权限拒绝按实际工具机制处理，不宣称存在不可用的权限模式。
- **`application-dev.yml` 的密码默认值**：`DB_PASSWORD` 的默认值（`123456`）是**本地开发密码**，
  生产 / 演示环境必须通过环境变量 `DB_PASSWORD` 覆盖。
  CI / 评审部署前必须确认该文件不含真实生产密码。
- **`zhiyu.jwt.secret` 的默认值**：`application.yml` 里的
  `zhiyu-local-dev-only-secret-0123456789abcdef` 是**本地开发用的假密钥**（放在
  `application.yml` 而非 `application-dev.yml`，是为了让 `@ActiveProfiles("test")` 的单测
  也能拿到密钥）；生产 / 演示环境必须通过环境变量 `JWT_SECRET` 覆盖。
  `JwtUtil` 在密钥短于 32 字节时**构造即抛异常**，启动直接失败，不会静默降级。
- **改 `openapi/openapi.yaml` 的文本字段**：YAML 中 `冒号 + 空格` 是映射分隔符，
  自由文本（`description` / `summary`）里出现 `: ` **必须加引号**，否则解析直接报
  `bad indentation of a mapping entry`。例：
  `description: '以 "Authorization: Bearer <token>" 携带'`
- **换行符与 `core.autocrlf`**：Windows 上 `core.autocrlf=true` 会让工作区文件显示为 CRLF，
  但只要 `git ls-files --eol` 显示 `i/lf`，索引就是规范的。
  不要为此执行 `git add --renormalize`（对 `i/lf` 文件无效），
  也不要重写工作区（有风险）。跨平台协作由 `.gitattributes`
  保证：Linux/CI checkout 时得到 LF，Windows 视编辑需求处理。
- **PowerShell 下 mvn 的 `-D` 参数**：必须用引号包住，否则 PowerShell 会把 `.` 解析为
  属性访问或断句。例：`mvn test "-Dmaven.repo.local=../.ai-tmp/m2"`（**不是**
  `mvn test -Dmaven.repo.local=../.ai-tmp/m2`）。
- **多行 commit message**：用 `git commit -F <文件>`，不要用 `-m "..."` 拼多行
  （PowerShell 对引号转义处理不可靠，容易截断或混入换行符）。
- **小程序端 baseURL**：微信小程序不能用 `localhost`，需用局域网 IP（如
  `http://192.168.x.x:8080`）。接口地址由构建期 `VITE_API_BASE_URL` 提供：开发默认值改
  `zhiyu-app/.env.development`，生产模板见 `.env.production.example`；`zhiyu-app/src/utils/config.js`
  只做统一解析，请求与 SSE 复用该配置。正式域名还需平台白名单与 HTTPS。
- **微信小程序 enableChunked**：`wx.request` 的 `enableChunked: true` +
  `onChunkReceived` 需要基础库 **≥ 2.20.1**。`sse.js` 已用 `wx.canIUse` 检测并
  提供 fallback（`enableChunked: false` + `success` 回调一次性解析）。

## 11. Git 约定

**分支模型**：
- `main` —— 永远可运行，只接受合并
- `dev` —— 日常集成分支
- `feature/*` —— 单个任务的工作分支，**只在本地存在**，不 push 到远程
- 远程只保留 `main` 和 `dev`

**Commit 信息**：`<type>(<scope>): <description>`
- type：`feat` / `fix` / `docs` / `chore` / `test` / `refactor`
- scope：模块名（如 `T0.3` / `auth` / `sql` / `openapi`）
- 中文描述，一行标题 + 空行 + 分点详情

**AI Agent 的权限**：
- 允许：`git add` / `git commit` / 只读命令（status/diff/log/show/ls-tree/branch -a）
- **禁止**：`git push` / `merge` / `rebase` / `reset` / `checkout`（切换分支）
- 允许 commit，但**必须先给用户看** `git status --short` 与 `git diff --stat`，
  得到明确确认后再执行
- 允许 commit message 由 Agent 起草，用户可以 commit 后 `--amend` 修改

**禁止**：
- 提交 `.env` / `application-local.yml` / 任何真实密钥
- 提交 `target/` / `node_modules/` / `dist/` / `.ai-tmp/`（.gitignore 已覆盖）
- `git push --force`（除非用户明确要求，且用 `--force-with-lease`）
- 删除 `main` 或 `dev` 分支
