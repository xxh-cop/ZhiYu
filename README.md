# 织愈（ZhiYu）

> 用一针一线，缝补青春的焦虑。
>
> AI 引导下的非遗艺术疗愈平台 —— 把情绪倾听、手作推荐、专注创作与社区分享串成完整疗愈闭环。

## 这是什么

面向在校大学生的情绪陪伴与非遗手作疗愈应用。学生在情绪低落时可与 AI 陪伴助手对话，
系统根据其当下状态推荐合适的非遗手作项目（赣绣、剪纸、扎染、陶艺等），在分步引导下完成创作，
并通过匿名作品分享获得同伴温暖反馈。

**产品定位**：陪伴与引导，非医疗诊断。本系统不能替代专业心理咨询。

## 技术栈

- 后端：Java 17 · Spring Boot 3.4.13 · Spring AI 1.0.0 · MyBatis-Plus 3.5.7 · MySQL 8 · Redis 7
- 学生端：UniApp（Vue3.4 + Vite 5 + Pinia2）→ 微信小程序 + H5；自研 SSE 封装
- 管理端：Vue3 + Vite + Element Plus + ECharts
- AI：DeepSeek / 通义千问（云端），Ollama + Qwen（本地模式）
- 向量库：Milvus / Chroma（非遗知识库 RAG）

## 目录结构

```
织愈/
├── AGENTS.md                  # AI 编码 Agent 必读约定
├── docs/
│   ├── project-context.md    # 项目上下文与架构约束、历史决策
│   ├── task-breakdown.md      # 任务拆解与里程碑
│   └── privacy-policy.md      # 隐私政策与用户协议
├── features/                  # Gherkin BDD 用例
├── openapi/openapi.yaml       # 后端接口契约
├── ai-config/ai-capabilities.yaml  # AI 能力与阈值配置
├── sql/
│   ├── schema.sql             # 建表 DDL
│   └── init-data.sql          # 字典/初始数据
├── .env.example               # 环境变量模板
├── zhiyu-backend/             # Spring Boot 后端
├── zhiyu-app/                 # UniApp 学生端（小程序 + H5）
└── zhiyu-admin/               # Vue3 管理端
```

## 快速开始

### 1. 准备环境

```bash
cp .env.example .env
# 编辑 .env，填入数据库、Redis、大模型 API Key 等
```

**必需环境变量：**

| 变量 | 用途 | 默认值 |
|---|---|---|
| `DEEPSEEK_API_KEY` | DeepSeek 大模型 API Key（必填，否则走 MockLlmClient 降级话术） | `sk-placeholder` |
| `DB_PASSWORD` | MySQL 密码 | `123456`（本地开发） |
| `JWT_SECRET` | JWT 签名密钥（≥ 32 字节） | 本地开发假密钥 |

### 2. 初始化数据库

```bash
# 按此顺序执行。schema.sql 不传库名，因为它内部已 `USE zhiyu`
mysql -u root -p < sql/schema.sql
mysql -u root -p zhiyu < sql/dict-category.sql
mysql -u root -p zhiyu < sql/dict-therapy-tags.sql
mysql -u root -p zhiyu < sql/init-data.sql
```

> 以上脚本均幂等（`CREATE TABLE IF NOT EXISTS` + `ON DUPLICATE KEY UPDATE`），可重复执行。
> 本地需要重置时：先 `mysql -u root -p < sql/reset.sql`，再重跑上面四条。

### 3. 启动后端

```powershell
cd zhiyu-backend
# PowerShell 下 -D 参数必须用引号包住
mvn spring-boot:run "-Dmaven.repo.local=../.ai-tmp/m2"
# 默认 http://localhost:8080
# 如需 AI 对话功能，先设置环境变量：
#   $env:DEEPSEEK_API_KEY='sk-your-key'
```

### 4. 启动学生端（UniApp）

```bash
cd zhiyu-app
npm install
npm run dev:h5              # H5 调试（http://localhost:5175）
npm run build:mp-weixin      # 构建微信小程序（输出 dist/build/mp-weixin/）
# 微信开发者工具导入 dist/build/mp-weixin 目录调试
```

### 5. 启动管理端

```bash
cd zhiyu-admin
npm install
npm run dev
# http://localhost:5174
```

## 角色

| 角色 | 端 | 主要能力 |
|---|---|---|
| 学生 | 小程序 / H5 | AI 情绪倾听、手作推荐、创作、作品社区、互助小组 |
| 传承人 | Web | 上传手作项目/步骤/语音、开课、一对一指导 |
| 心理咨询师 | Web | 查看脱敏关怀提醒汇总，线下兜底 |
| 管理员 | Web | 内容审核、用户管理、数据看板、团体活动、配置 |

## 核心闭环

```
情绪表达（AI 对话）
   ↓
疗愈推荐（规则 + LLM 生成理由）
   ↓
专注创作（分步引导 + 心流检测 + 呼吸提醒）
   ↓
作品分享（匿名发布 + 留暖 + 寄语）
   ↓
社区互助（小组结伴，循环回到情绪表达）
```

## 文档

- 设计文档（概要/详细/需求规格）见仓库根目录 `.docx`。
- 开发者与 AI Agent 协作先读 [`AGENTS.md`](./AGENTS.md)。

## 企业管理界面与正念工具

主业务管理端位于 `zhiyu-admin-vben/`，运行 `pnpm --filter @vben/web-ele dev`。
`zhiyu-admin/` 保留为早期基础管理端。学生端首页可进入“正念片刻”。
本批实施范围与后续计划见 [优化实施说明](docs/optimization-implementation.md)。

## 品牌、成长记录与活动预约

用户端新增五栏导航，底部“开始”集中进入自评、成长记录、活动预约与绘色。后台新增“活动与预约”和项目“材料安全”。
既有数据库部署前执行 `sql/migrations/20261011-growth.sql` 并重启后端；首次安装已包含在 `sql/schema.sql`。
功能范围、量表来源、验证及未开放事项见 [成长与活动交付说明](docs/growth-modules.md)，品牌源文件见 [品牌说明](docs/brand-assets.md)。


> 2026-10-11 后续交互、AI 辅助线稿、平面临摹和材料探索的实现状态，以[第三阶段交付与产品建议](docs/interaction-and-product-review.md)为准。


> 第四阶段已落实入门内容、条件筛选、可靠恢复、真实过程汇总与江西师大校内支持，详见[交付与数据来源](docs/starter-and-campus-delivery.md)。
