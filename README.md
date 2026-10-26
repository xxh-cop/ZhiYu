# 织愈 · ZhiYu

> 用一针一线，缝补青春的焦虑。

面向大学生的情绪陪伴与非遗手作平台，连接小织对话、手作学习、专注创作、音乐放松和社区分享。当前以江西师范大学人工智能学院校内试用为场景。

**本系统不能替代专业心理咨询，不提供医疗诊断或治疗承诺。** 当前具备校内试用的功能基础，尚未完成企业生产环境的全面验收。

> **当前状态（2026-10-21）**：核心业务已有实现与部分真实联调；仍有需求差距和部署验收项。请先阅读 [需求差距与验收边界](docs/requirements-audit-20261021.md)，不要仅凭旧任务表、构建通过或阶段「收尾」字样判断全部功能可用。

## 如何阅读与开始

- 运行项目：按下方「本地运行」准备数据库与三个工程。
- 判断完成范围：看 [最新需求核查](docs/requirements-audit-20261021.md)；查历史测试证据：看 [2026-10-20 交付记录](docs/final-delivery-checklist.md)。
- 参与开发：先读 [AGENTS.md](AGENTS.md)，再看 OpenAPI、数据库结构与对应业务场景。
- 判断自动化验证范围：看 [CI 工作流](.github/workflows/ci.yml) 与下方「构建与验证」；CI 只覆盖构建与 H2 单测，不含 MySQL、真机或真实 AI 验收。
- 当前管理端是 **zhiyu-admin-vben/apps/web-ele**；不要在历史 zhiyu-admin 中查找新功能。

## 功能概览

| 使用者 | 已实现的主要能力 |
| --- | --- |
| 学生 | 小织情绪陪伴、手作推荐与入门项目、步骤引导与创作恢复、音乐放松、成长记录、活动预约、图片／文字／视频作品分享 |
| 传承人 | 手作项目与步骤维护、材料安全提示、教学视频上传与预览，随项目提交审核 |
| 咨询师 | 在授权范围内查看脱敏关怀汇总，支持线下跟进 |
| 管理员 | 用户与内容管理、项目及社区视频审核、活动管理、真实业务数据看板 |

2026-10-20 批次补齐：预约双方授权交流与消息撤回、活动协作者、小组作品墙与创作搭子、历史续聊、推荐与模型配置、个人数据分类导出和原媒体保存、注销清理与文件删除重试。该批次范围与历史验证证据见 [交付清单](docs/final-delivery-checklist.md)，不代表所有设计需求均已完成。

| 状态 | 主要范围 |
| --- | --- |
| 已实现，有本地验证 | 注册登录、推荐/创作、社区审核、小组、课程/预约、关怀、数据导出与注销；具体覆盖见核查报告 |
| 部分实现 | 会话小结、步骤音频、跨阶段闭环统计、AI 几何线稿、轻量相机临摹；不能按名称推断为完整增强能力 |
| 尚未实现 | 登录连续失败限制、忘记密码找回、对话式量表、可审核知识图谱；PSS-10/PANAS/GSES 尚未启用 |
| 需要外部条件 | 学校统一认证、真实语音服务、校园运营与长期随访；当前人工核验方案按用户选择保留 |
| 尚未全面验收 | 微信真机、生产弱网/压测、安全审查、备份恢复及全部业务的四角色回归 |

- 学生端与管理端使用统一的松绿色视觉体系；小织以内嵌入口提供陪伴。
- 音乐需主动播放，支持切歌、音量、进度、定时停止和离页暂停；首批两首开放授权音乐附来源与署名。
- 新上传视频采用本地私有存储和有时效的播放链接，按当前权限访问；社区视频审核后才公开。教学视频在课程详情和创作步骤中观看。
- 视频目前支持 MP4，每个文件最大 50MB，每用户每天最多 20 次上传；建议 H.264 + AAC。尚未接入转码和断点续传。

## 工程与技术

| 工程 | 技术与用途 |
| --- | --- |
| `zhiyu-backend/` | Java 17、Spring Boot 3.4.13、Spring AI、MyBatis-Plus、MySQL 8；共用 `/api` 接口与角色权限 |
| `zhiyu-app/` | UniApp、Vue 3、Pinia、Wot Design Uni；学生端 H5 与微信小程序 |
| `zhiyu-admin-vben/` | **当前主业务管理端**，基于 Vue Vben Admin、Element Plus、ECharts，使用 pnpm 工作区 |
| `zhiyu-admin/` | 早期管理端，保留历史实现；本轮界面与音视频功能主要维护 Vben 版本 |

AI 调用通过统一 `AiClient` 门面，包含内容过滤、审计和失败降级。实际启用能力取决于服务端配置与密钥；不能将配置中的候选服务理解为已部署。当前 RAG 存储路径由 `ZHIYU_RAG_STORE_PATH` 配置，默认使用本地文件，不要求先部署 Milvus 或 Chroma。

## 目录约定

```text
Zhi_Yu/
├── README.md                       # 项目入口与运行说明
├── AGENTS.md                       # 协作规范
├── .env.example                    # 本地配置示例
├── .github/workflows/ci.yml         # CI：后端单测、学生端测试与构建、管理端类型检查
├── start-backend.ps1                # Windows 本地后端启动入口
├── docs/                           # 架构、隐私、视觉规范、阶段交付与素材署名
├── design/                         # 设计令牌等设计资料
├── features/                       # Gherkin 验收场景
├── openapi/openapi.yaml             # 唯一维护的接口契约
├── ai-config/                      # AI 能力配置
├── sql/
│   ├── schema.sql                  # 新数据库建表结构
│   ├── init-data.sql               # 初始数据
│   ├── schema-h2.sql               # H2 结构参考
│   └── migrations/                 # 现有数据库增量升级脚本
├── zhiyu-backend/
│   ├── src/main/java/              # 后端代码
│   ├── src/main/resources/         # 配置、提示词与静态素材
│   └── src/test/                   # 测试与测试库结构
├── zhiyu-app/src/                  # 学生端页面、组件与静态素材
├── zhiyu-admin-vben/apps/web-ele/   # 当前管理端应用
└── zhiyu-admin/                    # 早期管理端
```

业务 `.vue`、`.java`、SQL 迁移和接口契约应放在对应工程目录，**不要在根目录保留第二份**。`README.md`、启动脚本、Git 配置和根级工具配置可放在根目录。

`rag-store.json` 是运行时向量存储，位置与后端工作目录及配置有关；`uploads/`、`private-media/` 是运行数据，均不应提交。根目录的 `lefthook.yml` 是 Git 钩子配置，不属于业务源码。历史 `.docx` 保留在根目录；若与当前源码、Markdown 或接口契约冲突，以当前维护版本为准。

## 本地运行

### 1. 环境与配置

- Java 17、Maven、MySQL 8。
- Node.js 与 pnpm 以 `zhiyu-admin-vben/package.json` 为准：当前 Node.js `^22.18.0 || ^24.12.0`，包管理器 `pnpm@11.16.0`。
- 根据启用的共享存储能力准备 Redis；单机与多实例设置请查后端配置。

在仓库根目录复制配置示例，已有 `.env` 时不要覆盖：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

`.env` 用于本地配置，不提交密钥。直接执行 Maven **不会自动加载根目录 `.env`**；请在启动进程中设置环境变量，或使用根目录的本地启动脚本。

| 配置 | 用途 |
| --- | --- |
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | 数据库连接，以 `application-dev.yml` 为准 |
| `JWT_SECRET` | JWT 签名密钥，至少 32 字节 |
| `FIELD_ENCRYPT_SECRET` | 敏感字段加密密钥，格式要求见后端配置；已有数据的密钥不可随意更换 |
| `DEEPSEEK_API_KEY` | 云端对话能力；未配置有效服务时使用降级路径 |
| `DOUBAO_API_KEY` | 当前配置的向量模型服务密钥 |
| `ZHIYU_RAG_STORE_PATH` | 本地向量存储文件位置 |
| `ZHIYU_UPLOAD_DIR` | 图片等公开上传文件的位置 |
| `VITE_API_BASE_URL` | 学生端构建期接口地址（H5 与小程序共用，生产构建必填；仅前端公开地址，不放密钥） |

示例中的默认凭据仅用于本地开发。正式环境必须使用独立密钥与数据库凭据；不要将 `.env.example` 中关于开发默认值的注释理解为生产部署要求。

### 2. 初始化或升级数据库

先运行 `mysql --default-character-set=utf8mb4 -u root -p` 进入 MySQL 客户端，再执行以下命令。示例路径按仓库实际位置修改。

**首次安装：**

```sql
SOURCE D:/develop/Zhi_Yu/sql/schema.sql;
SOURCE D:/develop/Zhi_Yu/sql/dict-category.sql;
SOURCE D:/develop/Zhi_Yu/sql/dict-therapy-tags.sql;
SOURCE D:/develop/Zhi_Yu/sql/init-data.sql;
SOURCE D:/develop/Zhi_Yu/sql/migrations/20261011-starter-content.sql;
```

入门内容脚本依赖已有有效管理员账号；执行前确认初始化数据已导入。

**已有数据库升级：**先备份，再按需补齐以下迁移。不要使用 `reset.sql` 修复缺列，它用于重置数据。

```sql
SOURCE D:/develop/Zhi_Yu/sql/migrations/20261011-growth.sql;
SOURCE D:/develop/Zhi_Yu/sql/migrations/20261011-starter-content.sql;
SOURCE D:/develop/Zhi_Yu/sql/migrations/20261012-runtime-columns.sql;
SOURCE D:/develop/Zhi_Yu/sql/migrations/20261012-media-library.sql;
SOURCE D:/develop/Zhi_Yu/sql/migrations/20261015_business_workflows.sql;
SOURCE D:/develop/Zhi_Yu/sql/migrations/20261017_course_lessons.sql;
SOURCE D:/develop/Zhi_Yu/sql/migrations/20261018_group_messages.sql;
SOURCE D:/develop/Zhi_Yu/sql/migrations/20261020_final_business.sql;
```

`schema.sql` 中的 `CREATE TABLE IF NOT EXISTS` **不会给已有表补字段**。出现 `Unknown column 'media_type'` 时，检查是否对当前连接的数据库执行了 `20261012-runtime-columns.sql`；教学视频另需 `20261012-media-library.sql`。完成迁移后重启后端，查看启动兼容检查结果。

### 3. 后端

推荐在已配置环境变量的新终端中运行：

```powershell
Set-Location D:/develop/Zhi_Yu/zhiyu-backend
mvn spring-boot:run "-Dmaven.repo.local=../.ai-tmp/m2"
```

默认监听 `http://localhost:8080`。也可执行 `./start-backend.ps1`：按 UTF-8 加载可选的根目录 `.env`，已有进程环境变量优先，不输出配置值，不覆盖 JAVA_HOME。请自行准备 Java 17 与 Maven；不要依赖开发默认密码/密钥部署到其他环境。

### 4. 学生端

另开终端：

```powershell
Set-Location D:/develop/Zhi_Yu/zhiyu-app
npm install
npm run dev:h5 -- --port 5195
```

按终端显示的实际地址访问。学生端接口地址由构建期环境变量 `VITE_API_BASE_URL` 提供，统一在 `src/utils/config.js` 解析：开发默认值在 `zhiyu-app/.env.development`（真机/局域网改成可访问的后端地址），生产构建必须显式提供（模板见 `zhiyu-app/.env.production.example`；缺失时构建仍可能成功，但应用加载配置模块会立即抛错，不会静默回落到 localhost）。**微信真机与局域网访问不能用 localhost**；正式环境使用 HTTPS，并配置小程序合法域名。本地构建产物不等于部署完成。

### 5. 当前管理端

另开终端，进入 Vben 工作区根目录安装依赖：

```powershell
Set-Location D:/develop/Zhi_Yu/zhiyu-admin-vben
pnpm install
pnpm --filter @vben/web-ele dev --port 5194
```

按终端显示的实际地址访问。开发代理在 `apps/web-ele/vite.config.ts`，默认转发 `/api` 和 `/uploads` 到后端 8080 端口。不要误启动 `zhiyu-admin/` 后再查找新工作台或视频审核界面。

生产管理端使用同域 `/api`，正式部署时由网关反向代理到后端；独立域名可通过 `VITE_GLOB_API_URL` 指定。已屏蔽模板遗留的 Vben 演示接口地址，工作账号不会再发送到该演示服务。前端只可配置公开接口地址，不可保存服务端密钥。

### 6. 账号与首次使用

公开注册仅创建学生账号；学校名称为选填资料，校园身份需提交申请并由管理员核验。管理端只允许管理员、传承人、咨询师进入，不能通过注册参数自行选择高权限角色。试用时使用经授权配置的工作账号，不在 README 发布真实密码。

学生端注册/登录页面已统一布局并保留小织，支持错误定位、密码显示、键盘操作；注册成功但自动登录失败时可直接重试登录。隐私同意与咨询师联系授权仍由用户单独选择。已有密码修改功能；忘记密码后的自助找回尚未实现。

## 构建与验证

接口路径与操作数量不在本文硬编码，唯一来源是 [`openapi/openapi.yaml`](openapi/openapi.yaml)；测试数量以各命令当次的输出/报告为准。修改契约后用任意 YAML 解析器检查 `paths` 与 `operationId` 唯一性（本地可复用 `zhiyu-app/node_modules/yaml`，不新增依赖）。

### CI（GitHub Actions）

工作流：[`.github/workflows/ci.yml`](.github/workflows/ci.yml)。在 push 到 `main`/`dev` 与 pull request 时运行，`concurrency` 会取消同一分支的旧任务，权限仅为 `contents: read`。

| job | 关键命令 | 覆盖范围 |
| --- | --- | --- |
| `backend` | `mvn -B test`（Java 17，Maven 缓存） | H2 单测；不连 MySQL/Redis，不调真实 AI |
| `app` | `npm ci` → `node --test tests/*.test.mjs` → `npm run build:h5` → `npm run build:mp-weixin` | 学生端单测与两端构建；`VITE_API_BASE_URL` 仅为构建占位 |
| `admin` | `pnpm install --frozen-lockfile` → `pnpm --filter @vben/web-ele typecheck` | 管理端类型检查；不做浏览器 E2E |

**边界**：CI 只回答「能否编译、单测是否通过」。MySQL 迁移、微信真机、真实 AI/语音、弱网与压测必须在对应环境单独验收，CI 通过不能替代这些结论。

### 本地命令

在对应工程目录运行：

```powershell
# zhiyu-backend/（仓库内 Maven 缓存，避免写入工作区外的默认仓库）
mvn test "-Dmaven.repo.local=../.ai-tmp/m2"

# zhiyu-app/
node --test tests/*.test.mjs
npm run build:h5
npm run build:mp-weixin

# zhiyu-admin-vben/
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
```

微信开发者工具导入 `zhiyu-app/dist/build/mp-weixin/`。构建成功不等于真机播放、网络和权限验收通过。

历史证据：2026-10-20 集中验收中，后端全量单元测试通过（1 项真实 DeepSeek 集成跳过），H5、微信小程序、管理端构建及管理端类型检查通过；OpenAPI 契约已解析校验且 operationId 无重复。真实 MySQL/HTTP 验证覆盖预约授权、协作权限、小组媒体权限撤销、个人导出及注销并发保护。浏览器验证覆盖真实管理端登录、图表筛选联动、学生消息撤回、媒体文件下载和手机布局。

2026-10-21 账号入口复核：新增注册流程用例通过（数量见学生端测试输出）；H5、小程序、管理端构建及管理端类型检查通过。真实注册后模拟登录失败再恢复，确认只重试登录；新账号两项授权默认关闭、注销后旧凭证被拒绝。此前的后端全量回归属于历史证据，本次未重跑整套测试。

## 常见问题

| 现象 | 先检查 |
| --- | --- |
| Unknown column / SQLSyntaxErrorException | 应用实际连接的库与增量迁移是否一致；不要重置用户数据 |
| 登录后回到旧界面或找不到新工作台 | 是否启动了 zhiyu-admin-vben/apps/web-ele，浏览器是否仍缓存旧构建 |
| 电脑能用、手机无法连接 | 学生端 API 地址是否仍为 localhost；后端监听、局域网、防火墙、HTTPS 与小程序域名配置 |
| AI/语音显示未配置或使用缓存 | 服务端端点/密钥与配置状态；不要在前端填写密钥，降级提示不代表真实服务已验收 |
| 管理端 pnpm 无法运行 | Node 与 packageManager 指定版本、可访问的软件源；不要因此升级整个依赖树 |

## 文档与素材

- [架构与上下文](docs/project-context.md) · [任务拆解](docs/task-breakdown.md)
- [视觉规范](docs/ui-guidelines.md) · [品牌说明](docs/brand-assets.md)
- [隐私政策](docs/privacy-policy.md) · [接口契约](openapi/openapi.yaml)
- [成长与活动](docs/growth-modules.md) · [交互与产品建议](docs/interaction-and-product-review.md)
- [入门内容与校园支持](docs/starter-and-campus-delivery.md)
- [统一界面与音视频交付](docs/phase6-delivery.md) · [素材来源与授权记录](docs/media-attribution.json)

开放授权照片和音乐均保留作者、来源及许可证；品类照片不代表具体课程成品。管理端基于 Vue Vben Admin，具体开源参考与素材授权见阶段交付说明。

## 上线前仍需完成

先补账号失败限制/找回、步骤音频上传和严格口径的数据指标，再做全业务角色回归与真实设备验收。UI 优先统一表单、列表、加载/空/错误状态及键盘操作，而不是继续增加装饰动画。完整优先级与完成标准见 [下一阶段清单](docs/requirements-audit-20261021.md#下一阶段优先级与完成标准)。

HTTPS 与正式密钥配置、真实设备与弱网验收、媒体转码和字幕、对象存储与孤立文件清理、备份恢复演练、权限审查及并发压测。新视频的私有访问机制不自动覆盖历史公开上传文件；隐私文案需与最终部署和实际删除流程一致。

注销后的本人私有媒体已具备持久化删除重试；早期无法识别上传者的孤立公开文件仍需专项盘点。学校统一认证、真实语音服务需外部接入资料；当前采用管理员校园核验和语音未配置提示，不以模拟结果代替真实服务验收。
