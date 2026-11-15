# 三项体验改造交付：文案与人设、设计收口、“小织记得的”

日期：2026-11-15 ｜ 范围：只做用户点名的三件事，不含方案中的首页结构改版、打字机/骨架屏、半透明导航、管理端改造。

## 1. 文案与 persona

### 1.1 高频文案（新文案已落到页面）

| 位置 | 新文案 |
|---|---|
| 首页 eyebrow | 非遗手作 · 校园陪伴 |
| 首页主标题 / 副标题 | 说不出口的，手上慢慢做。 / 选一件手作，跟着步骤做。做不完也没关系。 |
| 首页主按钮 | 开始一件小手作 |
| 登录页 | 回来就好。没做完的那件，还在等你。 |
| 注册页 | 起个名字，就从今天开始。 |
| 引导页标题 | 把情绪，慢慢放下来。 |
| 对话空状态 | 今天想从哪儿说起？ / 不用想好句子，说半句也行。 |
| 对话页头状态 | 在听 |
| 对话危机卡 | 我先陪你一会儿。如果撑不住，这些电话现在就能打。 |
| 发布作品 | 做完了，给大家看看。 / 不用修得很完美，真实就很好。 |
| 陪伴面板头部 | 今天过得怎么样？ / 想说话就说，想做点什么我陪你。 |
| 练习总结 | 这一针，你完成了。 |
| 倾听小结 | 今天谢谢你愿意说。 |
| 记忆开关 | 开启后，小织记住你告诉它的练习偏好和不想聊的话题；不保存对话原文、情绪细节或高危信息；半年后自动忘记，可随时删除。 |
| 记忆设置页 | 我记得什么，你说了算。 |
| 记忆页免责声明 | 小织是陪伴工具，不能替代专业心理咨询；感到撑不住时请立即联系学校心理中心或心理援助热线。 |

`CompanionMemoryService.DISCLAIMER` 同步改为“半年后自动忘记”，与页面口径一致（TTL 仍为 180 天）。

### 1.2 小织人设 `zhiyu-backend/src/main/resources/prompts/companion.st`

整体重写为「手作社里愿意听人说话的学姐」：
- 先接一个具体细节，不复述情绪词；不总结、不替用户下结论。
- 每次 1–2 句、尽量 ≤40 字；一次最多一个问题；可以只回「嗯，我在。」。
- 黑名单：我懂你 / 你已经很棒了 / 一切都会好起来 / 慢慢来 / 想开点 / 你的感受很重要，以及「首先/其次/总之」。
- 手作只在用户明确想转移注意力、或连续两轮低落时提一次，且只说一个具体动作。
- 不诊断、不承诺疗效；自伤/绝望/告别时仍走安全转介。

> 未用真实模型做离线评测，人设效果需要后续用 20 组对话语料验收。

## 2. 字阶 / 颜色 / 图标收口

- 新增 `ZyIcon.vue`：统一 1.5px 描边、圆角端点的 SVG 图标（data URI 背景，兼容小程序），支持 send/mic/play/sound/share/external/arrow/search，尺寸用 rpx。
- 接入位置：ChatInputBar、创作会话、作品列表、手作详情、RAG 来源、非遗详情、音乐、隐私设置、开始页、入门页、小织卡片、悬浮小织、传承人列表、UI Kit。
- 情绪便签与编辑页的 emoji 全部换成既有 `MoodFace`（新增 `size/flat/showLabel` 属性，kind 语义不变：0 开心 1 平静 2 一般 3 有点累 4 不太好）。
- 新增可复跑 codemod `zhiyu-app/scripts/apply-design-tokens.js`：只处理 style 块，字号映射到 22/24/28/30/32/40/56rpx 七档，品牌色/冷灰映射到 `var(--zy-*)`，不改模板/脚本/SVG 属性，不碰 `theme.scss` 定义。首次执行修改 87 个文件。
- `growth.css` 与全局骨架屏改暖纸体系；新增 `--zy-brand-200`、`--zy-bg-tint-care`；Wot 的浅灰令牌改暖灰。
- 审计脚本 `zhiyu-app/scripts/check-design-tokens.js`（`pnpm check:tokens`）：
  - 裸字号 0；冷灰 0；emoji/字符图标 0；样式硬编码品牌色仅剩 `theme.scss` 9 处令牌定义。
  - 模板/脚本里的图标色保留 68 处（SVG `stroke`/`fill`、`:color` 传参，属图标色而非品牌硬编码）。
  - `zy-t-*` 旧排版类仍在 5 个页面使用（39 处）；纯文字「加载中…」18 处，均未在本次范围内处理。
- 修复 codemod 引入的一处渐变括号不平衡（`emotion/notes.vue` 新建便签按钮），并对全部 112 个样式文件做括号平衡检查，无其他异常。

## 3. “小织记得的”

### 3.1 存储与派生
- 不新增表/列、不做 SQL 迁移。用户填写的 3 个练习偏好 + 新增 `avoid_topics` 仍加密存 `user_ai_memory`；派生档案读取时实时统计，不持久化。数据访问走 `JdbcTemplate`，未新增 VO/Mapper 类。
- 授权版本升级为 `memory-v2`，旧 `memory-v1` 视为未授权，需要重新确认。
- 派生规则（仅次数/时段，不读内容）：
  - `calm_strategy`：近 30 天呼吸响应 ≥2 次 → 「先做一次呼吸引导，再决定要不要继续」；否则有完成手作 → 「从一件 10 分钟的小手作开始」。
  - `completed_crafts`：近 30 天完成手作数。
  - `preferred_time_window`：近 30 天 ≥3 次手作会话，按开始小时聚合（凌晨/上午/中午/下午/晚上）。
  - `recent_low_days`：近 14 天 tired/down 便签的不同日期数，≥2 天才显示。
  - 统计异常整体降级为空，不影响设置页与对话。

### 3.2 对话注入
- `CompanionMemoryService.chatProfile`：未开启/撤回授权返回空；最多 3 条，顺序为「不提的话题 → 安定方式 → 练习偏好」；低落天数只展示、不注入。
- `EmotionController` 把它作为一条 system 消息放在历史前，并注明「不是本次对话内容，不主动复述、不评价、不贴标签」；危机短路顺序不变。
- 边界：`POST /api/emotion/chat/commander` 委托路径未注入（沿用原编排）。

### 3.3 接口与页面
- 新端点 `DELETE /api/companion/memory/item?key=...`：只允许删 4 个用户填写键，派生 key 拒绝；整份 `DELETE /api/companion/memory` 不变（软删并关闭记忆）。
- 记忆接口返回结构新增 `avoidTopics/derived/consentVersion/profileNote/maxPromptLines`，`openapi/openapi.yaml` 已登记新端点与字段。
- 设置页改为「小织记得的」：记忆开关 / 你告诉我的 / 我注意到的（含来源）/ 聊天里怎么用 / 删除全部记忆；对话空状态新增记忆提示胶囊，最多 2 条，可跳到设置页。
- 新增 `features/companion-memory-profile.feature`（happy path + 未授权/未开启/超限/派生 key 删除/撤回）。

## 4. 本次验证

| 项 | 结果 |
|---|---|
| 后端针对性测试 | `mvn test "-Dtest=CompanionMemoryServiceTest,CompanionMemoryControllerTest,CompanionOrchestratorTest,AdaptivePracticeServiceTest,EmotionControllerViaCommanderTest"` 通过，34 个用例全绿（BUILD SUCCESS） |
| 新增单测 | 回避话题加密存储、上限与换行校验；注入 ≤3 条且不含低落天数；派生 key 不可删除 |
| H5 构建 | `VITE_API_BASE_URL=http://localhost:8080 pnpm build:h5` 通过 |
| 微信小程序构建 | `VITE_API_BASE_URL=http://localhost:8080 pnpm build:mp-weixin` 通过 |
| 样式审计 | `pnpm check:tokens` 通过 |
| H5 截图 | `output/playwright/{home,chat,memory,notes,onboarding}.png`，Playwright 渲染并逐张查看 |

截图脚本 `.ai-tmp/screenshot-ui.js`：使用本机 Chromium（1223），只拦截 `http://localhost:8080/api/**`，记忆/便签/用户接口为 mock 数据，登录态为本地演示 token；不连接真实后端、不写数据库。启动方式：`pnpm dev:h5` 后 `node .ai-tmp/screenshot-ui.js`。

## 5. 未验收 / 边界

- 真实模型的人设效果、真实 MySQL/Redis、真机小程序均未验收；派生统计使用 MySQL `DATE_SUB/HOUR/CURDATE` 语法，仅经 mock 单测。
- 自动样式收口改了 87 个文件，只抽查了首页/对话/记忆/便签/引导页，未逐页视觉回归。
- 18 处纯文字加载、39 处 `zy-t-*` 旧类、68 处图标色保持现状。
- 本任务未执行任何 `git add/commit/push`。

## 6. 运行

```powershell
cd zhiyu-backend; mvn spring-boot:run "-Dmaven.repo.local=../.ai-tmp/m2"
cd zhiyu-app; pnpm dev:h5            # H5
cd zhiyu-app; pnpm build:mp-weixin   # 微信小程序，构建期需要 VITE_API_BASE_URL
cd zhiyu-app; pnpm check:tokens      # 样式审计
```
