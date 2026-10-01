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

## 2. 技术栈（固定，不要擅自换）

| 层 | 选型 |
|---|---|
| 后端 | Java 17、Spring Boot 3.2、Spring AI 1.0、MyBatis-Plus 3.5 |
| 存储 | MySQL 8.0、Redis 7、向量库（Milvus 或 Chroma）、对象存储（本地/MinIO） |
| 学生端 | UniApp（Vue3 + Vite + Pinia2），编译微信小程序 + H5；SSE 用 uni-sse 插件 |
| 管理端 | Vue3.4 + Vite + Element Plus 2 + ECharts5 + Pinia2 |
| AI | 大模型 DeepSeek / 通义千问（云端）；本地化模式 Ollama + Qwen |
| 鉴权 | JWT（无状态），Authorization: Bearer <token> |

**三个工程目录（与 README 一致）**：
- `zhiyu-backend/` —— Spring Boot 后端
- `zhiyu-app/` —— UniApp 学生端（小程序 + H5）
- `zhiyu-admin/` —— Vue3 管理端

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
│   └── audit/      # AI 审计日志、关怀提醒
```

## 3. 错误码（全局唯一，不要新造）

| code | 含义 | HTTP |
|---|---|---|
| 0 | 成功 | 200 |
| 1001 | 参数校验失败 | 400 |
| 1002 | 未登录 / token 失效 | 401 |
| 1003 | 无权限 | 403 |
| 2001 | 用户名或密码错误 | 200（业务失败） |
| 2002 | 用户名已存在 | 200 |
| 3001 | 非法状态流转 | 200 |
| 4001 | AI 服务不可用（已降级） | 200 |
| 5000 | 服务器内部错误 | 500 |

> 注：错误码 `4001` 仅用于**非 SSE 接口**（推荐、寄语等）。SSE 接口（`POST /api/emotion/chat`）
> 的 AI 故障不使用 `4001`，改用 `event: error` 推送，前端收到后**降级为缓存图文**。

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
> `role = 0` 跳转学生端或提示无权限；管理端不做独立登录入口。

## 4. AI 能力硬约束

- 所有 AI 调用必须经过统一门面 `AiClient`，**禁止业务代码直接 RestTemplate/OpenAI 客户端**。
  `AiClient` 内部持有 `LlmClient`（对话/推荐/寄语）、`EmbeddingClient`（RAG 向量）、`SpeechClient`（ASR/TTS）；业务只调 `AiClient`。
- 所有 AI 输入输出必须经过**内容安全过滤**，并落 `ai_audit_log`。
- AI 调用必须配熔断：**连续失败 5 次 → 熔断 60 秒**，按资源 key 隔离（`llm-chat`/`llm-recommend`/`llm-message`/`asr`/`tts`），不得全局共享一个计数器。
- 超时降级：AI 超时 → 返回缓存的图文推荐理由 + 图文引导，不得让用户白屏。
- Prompt 模板外置到 `resources/prompts/*.st`，不要硬编码在 Java 里。
- 外部 API Key 只存服务端环境变量，**绝不允许下发到前端**。

## 5. 数据与隐私硬约束

- 所有业务表必须含 `id`、`create_time`、`update_time`、`deleted`（逻辑删除）；
  **审计日志表 `ai_audit_log` 例外**，只追加不更新不删除，可只保留 `create_time`。
- **主键策略**：主键由应用侧 MyBatis-Plus `ASSIGN_ID`（雪花算法）生成，**DDL 不设 `AUTO_INCREMENT`**；
  种子数据可手动指定小整数 ID。
- 敏感字段（对话内容、心情便签）**应用层加密**，密钥只存服务端；
  前端拿到的永远是服务端解密后的明文，**不得在前端做加解密**。
- **上一条的例外**：`care_reminder.summary` 存**脱敏文本、不加密**（已去除可识别身份的信息，供授权角色查看）；
  `ai_audit_log.raw_input` / `ai_output` **加密存储**。
- 查询必须带「本人数据」或「授权范围」过滤；咨询师看到的是脱敏汇总。
- 建表 DDL 一律放 `sql/schema.sql`，**不要写在 .md 代码块里**；初始化数据放 `sql/init-data.sql`。

## 6. 接口约定

- 路径前缀统一 `/api`；学生端与管理端共用同一套后端，靠 JWT 角色区分。
- 对话接口 `POST /api/emotion/chat` 返回 `text/event-stream`（SSE）；前端用 uni-sse（学生端）/ fetch-event-source（管理端）。
- 接口契约以 `openapi/openapi.yaml` 为准，改接口先改 YAML 再改代码。

## 7. 心流判定（已固化，不要拍脑袋改）

- **心流**：连续 5 分钟无步骤切换且无主动退出 → 减少打扰。
- **焦躁**：同一步骤反复停留 > 3 次且呼吸提醒未响应 → 弹出 4s 吸 / 6s 呼呼吸引导。

## 8. 提交前自检（DoD）

- [ ] 新接口已在 `openapi/openapi.yaml` 登记。
- [ ] 新表/字段已写进 `sql/schema.sql`，并更新 `init-data.sql` 基础字典。
- [ ] 新功能有对应 `.feature` Gherkin 场景（至少 1 个 happy path + 1 个异常路径）。
- [ ] AI 相关调用走 `AiClient` 门面 + 安全过滤 + 审计日志。
- [ ] 不出现诊断性医疗措辞；涉及心理的页面有免责声明。
- [ ] `mvn -q compile` 通过；不提交硬编码密钥。

## 9. 关键文档索引

| 文档 | 路径 |
|---|---|
| 项目上下文/架构约束 | `docs/project-context.md` |
| 任务拆解 | `docs/task-breakdown.md` |
| 隐私政策/用户协议 | `docs/privacy-policy.md` |
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
  `AccessDeniedException`。拉依赖时加 `-Dmaven.repo.local=.ai-tmp/m2` 指到仓库内。
- **出网**：pwsh 侧的 `curl` / `Invoke-WebRequest` 被沙箱阻断（`schannel: SEC_E_NO_CREDENTIALS`）；
  走 Maven / Java 自己的 TLS 栈可正常访问网络。
- **中文文件分析**：**禁止**用 pwsh 的 `Get-Content` / `Select-String` 读本仓库的 `.md` / `.sql`
  （会把 UTF-8 当 GBK 解码，产生乱码并合并相邻行）；一律用 node 或 Java 显式按 UTF-8 读取。
- **运行期读 UTF-8 中文资源**：读取 UTF-8 中文资源（`resources/prompts/*.st`、
  `resources/lexicon/*.txt`、`resources/fallback/*.md`）时，必须显式指定 UTF-8，
  不得依赖 `file.encoding`（JDK 17 在 Windows 下默认 GBK）。
  例：`new InputStreamReader(in, StandardCharsets.UTF_8)`
- **改 `openapi/openapi.yaml`**：**优先用 `edit` 工具，不要用 node / pwsh 脚本批量改**
  （沙箱对该文件的脚本写入直接拒绝：`EPERM: operation not permitted`；`edit` / `write` 工具
  走的是另一条通道，不受此限）。确有必要用脚本时，需把该次命令升级到 `danger-full-access`。
- **`application-dev.yml` 的密码默认值**：`DB_PASSWORD` 的默认值（`123456`）是**本地开发密码**，
  生产 / 演示环境必须通过环境变量 `DB_PASSWORD` 覆盖。
  CI / 评审部署前必须确认该文件不含真实生产密码。

## 11. Git 约定

- 分支：`main`（可运行）/ `dev`（集成）/ `feature/*`（AI 工作分支）
- 提交信息：`feat(模块): 描述` / `fix(模块): 描述` / `docs: 描述` / `chore: 描述`
- 一次提交只做一件事；不提交 `.env`、`target/`、`node_modules/`。
- 每个任务完成后先 `git status` 检查，再提交。
- AI 不得执行 `git push --force`、不得删除 `main` 分支。
- 只读命令**允许**。约定只针对写操作。
