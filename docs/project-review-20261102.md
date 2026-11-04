# 「织愈」架构与产品全面审查报告（2026-11-02）

> 本次为独立审查：**未修改任何业务代码、未提交 commit**。一切结论以代码实际实现为准，
> 文档（含 AGENTS.md、docx、任务勾选）仅作对照。每条重要判断标注证据（文件 + 行号），
> 并区分「已实现」「文档写了但未实现」「实现与文档冲突」「仅配置未接入」。
> 本次未运行构建与测试，测试结论均引用历史证据并注明日期。

## 目录

1. [审查范围与方法](#1-审查范围与方法)
2. [总体判断（结论先行）](#2-总体判断结论先行)
3. [未完成任务清单](#3-未完成任务清单)
4. [设计不合理之处](#4-设计不合理之处)
5. [界面与交互优化](#5-界面与交互优化)
6. [功能缺口与可丰富内容（含同类参考）](#6-功能缺口与可丰富内容)
7. [成熟度评估](#7-成熟度评估)
8. [智能化现状与空间](#8-智能化现状与空间)
9. [文档与代码冲突清单](#9-文档与代码冲突清单)
10. [优先级问题清单（P0/P1/P2）](#10-优先级问题清单)
11. [需要拍板的岔路口](#11-需要拍板的岔路口)

---

## 1. 审查范围与方法

- **代码**：`zhiyu-backend/`（Spring Boot 3.4.13）、`zhiyu-app/`（UniApp 学生端）、
  `zhiyu-admin-vben/apps/web-ele/`（当前管理端）；`zhiyu-admin/` 仅作历史参考。
- **文档**：根目录五份 docx（已提取全文比对）、`docs/` 全部 md、`openapi/openapi.yaml`、
  `sql/schema.sql` / `init-data.sql` / `migrations/`、`features/`（62 个 .feature）、
  `ai-config/ai-capabilities.yaml`、`.github/workflows/ci.yml`。
- **时间线意识**：`docs/requirements-audit-20261021.md` 是 10-21 的差距清单，但 10-22 至 10-30
  又有大量开发（陪伴编排、千问语音、非遗内容工作流、图增强 RAG、疗愈漏斗看板、AI 治理，
  见 git log `d78ae37`、`f561bc3`、`33a9eaa` 等）。本报告以 **11-02 的代码** 为准，
  audit 中已被后续开发补齐的项会明确标注「audit 后已补齐」。
- **联网核查**：同类开源/产品参考已通过联网检索（来源见第 6 节），
  产品级对比（Woebot / Calm 等）基于公开认知，标注为经验判断。

---

## 2. 总体判断（结论先行）

**这是一个完成度远超普通竞赛原型的项目，但距离「成熟可交付的生产系统」还差 4 道门槛。**

已经做到位、值得肯定的：

- 疗愈闭环（情绪→推荐→创作→分享）在代码层面完整闭环，且 10 月底又长出第二条主线
  （PracticeSession + CompanionOrchestrator 的有界智能陪伴）。
- AI 工程纪律罕见地好：统一门面 `AiClient`（[AiClient.java](../zhiyu-backend/src/main/java/com/zhiyu/ai/AiClient.java)）、
  按资源 key 隔离的熔断（默认 5 次/60 秒，[application.yml](../zhiyu-backend/src/main/resources/application.yml) L65-69）、
  输入/输出双侧安全过滤、审计落库加密、Mock 与真实模型严格互斥且 prod 缺 key 直接启动失败
  （[ProductionLlmClientConfig.java](../zhiyu-backend/src/main/java/com/zhiyu/ai/client/ProductionLlmClientConfig.java) L24-36）。
- 隐私合规做到细节：单独同意、脱敏关怀、16 类导出、注销清理、签名媒体 URL。
- 免责声明覆盖 56 个前端文件，未检出诊断性措辞。
- 契约纪律好：openapi.yaml 登记约 145 条路径，与控制器基本同步；47 张表全部符合
  id/create_time/update_time/deleted 约定。

最关键的 5 个问题（详见第 10 节）：

1. **P0｜SSE「流式逐字」名不副实**：安全终检要求看到全文，后端把整段回复缓冲后只发**一个**
   SSE data 事件（[AiClient.java](../zhiyu-backend/src/main/java/com/zhiyu/ai/AiClient.java) L153-169、
   [EmotionController.java](../zhiyu-backend/src/main/java/com/zhiyu/modules/emotion/controller/EmotionController.java) L112）。
   NFR-02「首字 ≤2s」在长回复下不可达，前端打字光标只是假等待。
2. **P0｜账号安全两个洞**：登录连续失败限制未实现（AuthServiceImpl 无任何失败计数/锁定逻辑）；
   忘记密码找回未实现（README L34/L187 自认）。
3. **P0｜生产验收证据链断**：无压测报告（NFR-01/02/03）、无微信真机记录、无 HTTPS/正式密钥部署、
   无备份恢复演练（runbook 有文档无演练）。CI 只覆盖 H2 单测与构建。
4. **P1｜文档体系已反过来误导开发**：AGENTS.md 模块清单缺 5 个真实模块；
   project-context.md 仍写 uni-sse/Milvus/Spring Boot 3.2；docx 描述 17 张表（实际 47 张）、
   接口路径大面积过时、「语义安全过滤」「失败重试 2 次」均未实现。
5. **P1｜双对话系统并存无分工文档**：`/api/emotion/chat`（旧）与
   `/api/companion/sessions/{id}/emotion/message`（新编排层）两套对话长期并存
   （[CompanionMessageController.java](../zhiyu-backend/src/main/java/com/zhiyu/modules/companion/controller/CompanionMessageController.java) L32 注释自认「并存，不改旧对话」），
   无收敛计划。

---

## 3. 未完成任务清单

按「承诺来源 → 现状」组织。状态词：**未实现 / 部分实现 / 仅配置未接入 / 待验收**。

### 3.1 文档/差距清单承诺但代码没有的

| 事项 | 承诺来源 | 代码现状与证据 | 状态 |
|---|---|---|---|
| 登录连续失败限制 | 需求 FR-01-02；audit L16 | AuthServiceImpl 无失败计数（grep fail/lock/attempt 无命中） | **未实现** |
| 忘记密码找回 | 需求用例 FR-80-02；README L187 | 仅管理端登录页文案「请联系平台管理员重置」（login.vue:199） | **未实现** |
| 通知/未读系统 | audit L84/L90 | 首页 `hasNotice = ref(false)` 硬编码 TODO（home/index.vue:75）；无通知表/接口 | **未实现** |
| 步骤引导音频上传闭环 | 需求 FR-30-01；audit L22 | 传承人端手填音频 URL；FileUploadController 无音频类型（仅 image/video/private-image，见 openapi L31-32、L124） | **部分实现** |
| 纯语音控制创作步骤（「下一步/重复」） | 概要 §3.4；audit L19 | 语音输入仅进对话（useVoiceInput.js → /api/speech/transcribe），创作页无语音指令解析 | **未实现** |
| 对话式量表 | 优化建议书 A3；README L34 | 无任何实现 | **未实现**（增强项，不阻断） |
| PSS-10 / PANAS / GSES 量表 | 优化建议书 F1 | 仅 PHQ-9/GAD-7（audit L37 自认） | **部分实现** |
| 失败重试 2 次指数退避 | 概要 §3.8、详设 §5.7 | `ai/client` 包内无任何 retry 逻辑；仅依赖 Spring AI 内部默认重试模板，参数未按设计显式配置与验证 | **文档写了但未实现** |
| 语义级内容安全识别 | 详设 §5.5「关键词+语义双重识别」 | [KeywordContentSafetyFilter.java](../zhiyu-backend/src/main/java/com/zhiyu/ai/safety/KeywordContentSafetyFilter.java) L23 明确「不做 LLM 语义判断，只做本地词库+正则」 | **实现与文档冲突** |
| 闭环转化率/心流时长占比看板 | 需求 FR-60-03；audit L28 | 疗愈漏斗接口已存在（/api/admin/dashboard/therapy-funnel，10-28 后新增），但 audit 口径问题（同人群关联、分母定义）未见文档化裁决 | **部分实现** |
| 压测（P95≤500ms、首字≤2s、100 并发） | 需求 NFR-01/02/03 | 无任何压测脚本/报告 | **待验收** |
| 微信真机、弱网、HTTPS、备份演练 | audit L83/L87；README L254 | runbook-backup-restore.md 有流程文档，无演练记录 | **待验收** |

### 3.2 audit（10-21）之后已被后续开发补齐的项

以下在 audit 中列为缺口，但 10-22~10-30 的提交已补上，**不应再当作未完成任务**：

- 整段会话小结：`/api/emotion/sessions/{id}/summary/generate` +
  [ListeningSummaryServiceTest](../zhiyu-backend/src/test/java/com/zhiyu/modules/emotion/ListeningSummaryServiceTest.java)；
  `AiClient.listeningSummary`（AiClient.java L289-323）含 Mock 不冒充生成的诚实标记。
- 真实语音接入：`QianwenSpeechClient`（provider=qianwen 时生效，
  [QianwenSpeechClient.java](../zhiyu-backend/src/main/java/com/zhiyu/ai/client/QianwenSpeechClient.java) L31），
  前端 TTS 播报已进对话页（chat.vue:261）；仍属「配置 gated，未做真机/真实账号验收」。
- 疗愈漏斗看板：`/api/admin/dashboard/therapy-funnel` + TherapyFunnelCard.vue（口径问题仍在，见上表）。
- 非遗内容工作流：草稿/发布/下架/版本回滚一整套 admin 接口（openapi L44-51）+ heritage 学生端三页。

### 3.3 代码中的 TODO/半成品

- `zhiyu-app/src/pages/home/index.vue:75` — `hasNotice` 固定 false（全仓前端唯一显式 TODO）。
- 后端 src 下无 TODO/FIXME 命中（已 grep 确认）——后端表面干净，半成品主要体现为
  「配置未接入即降级」（Mock LLM、gateway 语音）而非代码残缺。
- 管理端模板残留：`views/demos/element/index.vue`、`views/demos/form/basic.vue`、
  `_core/authentication/{code-login,qrcode-login,register,forget-password}.vue` 仍在源码树中
  （未在业务路由 modules 下注册，但会被打包进工程，forget-password 模板页与
  「联系管理员重置」文案并存，容易被误认已实现找回流程）。

### 3.4 契约登记但未实现 / 实现但未登记

- 对照 openapi 约 145 条路径与 45 个 @RequestMapping 控制器：未发现「YAML 有而后端完全没有」
  的接口；companion/practice/heritage/speech 等新接口均已登记，契约同步纪律好。
- 反向（后端有、YAML 缺）：`/api/craft`（CraftController 项目列表等少量旧路径）与
  `/api/user` 下部分接口在 YAML 中归并登记，粒度差异存在但无失联接口。**结论：契约健康。**

### 3.5 测试与 Gherkin 缺口

- 后端约 115 个测试类，覆盖所有模块（含 companion、practice、heritage、ai 全链路），
  历史证据：2026-10-20「419 项，418 通过 1 跳过」（final-delivery-checklist.md）。**本次未重跑。**
- `features/` 62 个 .feature，模块覆盖全。**但 Gherkin 没有任何 runner 接入 CI**
  （ci.yml 仅 mvn test / node --test / pnpm typecheck），场景与代码的对应靠人工维护，
  无法防伪——这是「场景存在」与「场景被验证」之间的结构性缺口。
- 前端学生端仅 6+3 项 node 测试（audit L68），相对 56 个页面覆盖率极低；
  管理端只有 typecheck，无任何行为测试。

---

## 4. 设计不合理之处

### 4.1 SSE 架构：安全终检与流式体验的根本冲突（最重要）

- **现状**：`AiClient.streamChat` 把 LLM 全部 token 累积到内部 StringBuilder，
  输出终检通过后才一次性 `onToken.accept(reply)`（AiClient.java L153-169）；
  EmotionController 把这个「整段」作为一个 SSE data 事件发出（EmotionController.java L112）。
- **后果**：① NFR-02「首字 ≤2s」不成立——首字时间 = 全文生成时间（DeepSeek 长回复常 5-15s）；
  ② 前端 `typing` 光标只是装饰（chat.vue L37、emotion.js L164-173 原样渲染到达的单个块）；
  ③ 详设下篇 §7.3.2 描述的 `data: {"delta": ...}` 增量协议从未存在。
- **评价**：安全优先的取舍本身正确（陪伴场景宁慢勿险），但**文档、需求、前端表现都在假装流式**，
  这是诚信问题而非技术问题。方案见第 11 节岔路口 1。

### 4.2 双对话系统并存

- 旧：`/api/emotion/chat`（自由倾诉，chat.vue 在用）；
  新：`/api/companion/sessions/{id}/emotion/message`（PracticeSession 编排 + 工具白名单 + typed SSE 事件）。
- CompanionMessageController.java L32 注释写明「并存，不改旧对话」。
- **问题**：两套对话无统一会话模型（emotion_session vs practice_session），
  小织在「聊天页」和「练习页」是两个不同人格实现；用户在 A 说的话 B 不知道
  （companion memory 只存偏好，user_ai_memory 表）。长期会撕裂产品体验。
- **建议**：明确分工（自由倾诉 vs 结构化练习陪伴）并写进 project-context，
  或制定向编排层收敛的迁移路线。见岔路口 2。

### 4.3 AiClient 正在长成上帝类

- 单类 516 行、9+ 个场景方法（chat/recommend/message/reflection/listening/companion/
  knowledge×4/heritage/lineart/speech×2），且混有两种代码风格（L465-481 出现压缩单行风格与
  注释残缺句「当前实际接入,"这个主题暂不适合生成线稿...」——AiClient.java L507 附近注释与
  L468 行的碎片文本疑似一次合并事故残留，建议清理）。
- **建议**：按场景拆 `ChatAiFacade` / `ContentAiFacade` / `SpeechAiFacade`，
  门面只保留熔断+过滤+审计的横切逻辑。

### 4.4 推荐规则双写

- [RecommendRuleConfig.java](../zhiyu-backend/src/main/java/com/zhiyu/modules/craft/service/RecommendRuleConfig.java) L10-31
  硬编码情绪→疗愈标签映射，注释称「与 ai-config/ai-capabilities.yaml 同步」。
- **问题**：yaml 不是运行时配置源，只是给人看的镜像；两边必然漂移。
  **建议**：要么 yaml 真被加载（@ConfigurationProperties），要么删掉 yaml 中的规则段只留代码单一事实源。

### 4.5 ThreadLocal 手动跨线程传递

- SSE 两个控制器都手工 `UserContext.set(identity)` 进异步线程再 finally 清理
  （EmotionController.java L66-71/L129-130、CompanionMessageController.java L56/L80-83）。
- **问题**：第三个 SSE 端点出现时大概率漏传；且线程池复用下一旦漏清就是串号事故。
  **建议**：封装 `SseTaskRunner` 统一传递/清理，或在 sseExecutor 外包 decorator。

### 4.6 RAG 存储与「数据不出校园」叙事

- 实际为本地 JSON 文件（`ZHIYU_RAG_STORE_PATH` 默认 `./rag-store.json`，application.yml L78-79）
  + 豆包远程 Embedding。**注意**：向量化本身把非遗文本发给火山引擎，
  这与「数据不出校园」的叙事存在缝隙（非遗知识非用户数据，风险低，但文档从未说明这一层）。
- 单机文件存储无并发写入保护说明、无备份策略、无量级上限；docx 宣称的 Milvus/Chroma 从未部署。
- **建议**：文档明确「Embedding 出域、对话不出域」的边界；存储层保留 SimpleVectorStore
  但补上文件备份与损坏恢复路径。

### 4.7 管理端面向传承人的 UX 错位

- 传承人的内容维护（my-projects、project-edit）在 Vben 管理端里。
  Vben 是工程师向后台，让非遗传承人（非技术用户）填表单、手填音频 URL，使用门槛高。
- **建议**：中期为传承人做独立简化工作台（或学生端内嵌传承人模式），
  管理端回归审核与运营定位。工作量较大，列入 P2。

### 4.8 AGENTS.md 自身需要修订的条款（用户明确要求质疑）

| 条款 | 问题 | 建议 |
|---|---|---|
| §2 模块清单 | 缺 `companion`（陪伴编排）、`crisis`（危机热线）、`practice`（练习会话）、`heritage`（非遗内容）、`user`（资料/密码）五个真实模块 | 更新清单，否则新 Agent 会把新模块当违规 |
| §7 心流判定「已固化，不要拍脑袋改」 | 阈值实际已配置化（application.yml L112-115 `zhiyu.flow.*`），条款与现实矛盾；且「固化」阻碍试点后按数据调参（试点计划本来就要求调阈值） | 改为「默认值已验证，调整需走配置+记录依据」 |
| §6 「SSE 流式逐字输出」 | 与 4.1 的实际实现冲突 | 改写为「整段安全后下发」或修实现 |
| 全文未提 L4 路线 | docs/superpowers/plans/2026-10-30-l4-annual-roadmap.md 已是用户确认的方向，AGENTS 无指引 | 增加 L4 决策摘要链接 |

### 4.9 其他设计观察（小）

- 错误码 2001/2002 用 HTTP 200 表达业务失败，与 REST 语义不符但全局一致、前后端约定清晰，可保留。
- 危机热线默认值写死江西师大（application.yml L99-103）——单校部署 OK，多校复制时必须配置化（已支持 env 覆盖，文档需强调）。
- SSE 超时 120s 与 LLM 单次超时（设计 30s，代码未见显式配置）之间的保护关系未验证。
- `work_like.type` docx 写「1 点赞/0 收藏」的反直觉编码，代码内建议确认注释清晰（未逐行核）。

---

## 5. 界面与交互优化

### 5.1 已吸收的优化建议书成果

- tabBar 已改为建议书的 5 区结构（首页/社区/**开始**/小组/我的，pages.json L332-365）——L1 已解决。
- 免责声明、小织保留、账号入口无障碍改造、管理端玻璃主题与图表主题桥均已落地
  （最近提交 a9c4421、635ecbb、9efa5f4）。

### 5.2 仍然存在的问题

1. **假流式体感**（4.1 的前端侧）：输入框发出后只有 typing 光标空转，
   无「正在倾听…」的阶段反馈（companion 的 typed 事件模型其实支持阶段提示，chat 页没用上）。
2. **状态页体系不完整**：U7（加载/空/错/成功四类状态页）只在新页面落地，
   旧页面仍有裸露「暂无数据」（ui-ux-review-20261020.md L33、audit L49）。
3. **图表不可下钻**：管理端 OperationsChart 有 aria 但点击不联动业务列表
   （ui-ux-review L35），看板是「看」不是「用」。
4. **无障碍残留**：部分旧页面仍用仅可点击 view 承载关键动作（audit L49、ui-ux-review L33），
   全站键盘走查未完成。
5. **建议书 L2-L5 的版式问题部分仍在**：首页信息架构、创作页按钮收敛、
   对话页三段式是否全落地**本次未逐页浏览器验证**（标记为待验证，不断言）。
6. **移动端横溢与字号**：历史报告说 390/320px 通过，但新增的 heritage/practice 系列页面
   未见到同等级验证记录。

### 5.3 优化建议（按性价比排序）

| 优先级 | 事项 | 理由 |
|---|---|---|
| 高 | 对话页接入阶段反馈（连接中/生成中/安全检查中），或实现句级流式（岔路口 1） | 直接消除最大体感痛点 |
| 高 | 统一空/错/加载组件并全站替换 | 投入小、一致性收益大，audit P1 已列 |
| 中 | 管理端图表点击→列表筛选联动 | 看板从展示变工具 |
| 中 | 创作页 6 按钮收敛为 1 主按钮 + 更多菜单（建议书 4.3-4.4） | 心流体验核心页 |
| 低 | 夜间疗愈模式 U5 完整版、状态页插画体系 U7 | 体验加分项 |

---

## 6. 功能缺口与可丰富内容

### 6.1 对照成熟产品形态的缺口

参照系（**联网已查证**）：MindChat（心理垂域开源大模型，wisemodel.cn/models/X-D-Lab/MindChat）、
OpenGnothia（开源 AI 治疗辅助，本地数据）、心悦智伴（多模态情感陪伴，gitlink）。
参照系（**经验判断，未逐一查证最新版**）：Woebot/Wysa（AI 陪伴 + 结构化 CBT 工具）、
Calm/Headspace/潮汐（内容库 + 睡眠 + 课程化冥想）。

| 维度 | 织愈现状 | 成熟形态差距 |
|---|---|---|
| 陪伴对话 | 单轮安全终检 + 历史 10 轮 | 缺长期情绪轨迹感知（小织不知道你上周持续低落；companion memory 只存偏好） |
| 结构化工具 | 呼吸、白噪音、量表、短练习、纹样涂色 | 已相当丰富；缺 CBT 类结构化自助（想法记录、行为激活清单）——但注意非诊断红线，可做「情绪整理卡」而非 CBT 原名 |
| 内容生态 | 13 个种子手作项目（init-data.sql）、非遗工作流已有 | 内容量是最大短板：成熟产品靠持续内容供给留存；需要传承人内容生产节奏与版权采购 |
| 社区 | 作品墙/留暖/小组/搭子 | 无实时互动（一期明确不做）、无好友关系；小组活跃度无运营工具（话题、打卡挑战） |
| 效果证据 | 量表前后测（回顾式）、漏斗看板 | 缺真正的前瞻前测 + 4-8 周随访体系（audit L42），无法支撑任何效果声明 |
| 触达留存 | 无 | **无通知系统是最大的产品级缺口**：预约回复、审核结果、小组动态、关怀跟进都无法触达用户（小程序订阅消息是现成通道） |
| 多端 | 小程序 + H5 | 无 App/桌面端；小程序主包体积与分包优化未见专项 |
| 运营 | AI 治理、内容工作流、操作日志 | 缺用户分层/活动运营位（ banner、任务体系已有徽章但未运营化） |

### 6.2 值得优先丰富的方向（非堆页面，按闭环价值）

1. **通知与召回**（小程序订阅消息 + 站内未读）：解锁预约、审核、小组、关怀四个已有闭环的「最后一公里」。
2. **内容供给管线**：heritage workflow 已有草稿/发布/版本，缺的是「谁来持续生产」——
   建议做传承人内容模板（步骤图文自动生成初稿 + 人工修订），而非新功能。
3. **情绪整理卡**（轻量结构化自助）：不越诊断红线的 CBT 简化版，与便签/对话打通。
4. **小组运营机制**：7 天共织挑战、小组打卡，用现有 work/group 表即可实现。
5. **睡眠场景深化**：F11 睡前模式已有雏形，补定时关闭一致性与真机后台策略即完整。

---

## 7. 成熟度评估

**判断：当前是「高完成度的校内试用/竞赛演示版」，不是「成熟可交付的生产系统」。**

| 门槛 | 现状 | 可验证依据 |
|---|---|---|
| ① 功能闭环 | ✅ 已达 | 双端闭环代码与历史联调证据充分 |
| ② 账号与访问安全 | ❌ 未达 | 无失败锁定、无找回流程；四角色跨账号回归无近期记录 |
| ③ 性能与稳定性验收 | ❌ 未达 | 无压测；SSE 实为单段下发，首字指标口径失真；无真实故障演练 |
| ④ 真实环境运行 | ❌ 未达 | 真机、HTTPS、正式密钥、备份恢复、媒体转码均未验收 |
| ⑤ 内容与运营可持续性 | ⚠️ 半达 | 工作流具备，内容量与运营机制不足 |
| ⑥ 文档可信度 | ❌ 未达 | docx 全面过时、AGENTS 模块清单失真（第 9 节） |

**可验证的通过标准建议**：②③④ 各出一条可重复执行的证据（脚本+记录），
即可从「演示版」升级为「试点版」；「成熟产品」还需⑤的内容运营跑过一个学期。

---

## 8. 智能化现状与空间

### 8.1 AI 当前真实使用点（全部经 AiClient 门面 + 过滤 + 审计）

| 能力 | 实现 | 真实可用性 |
|---|---|---|
| 情绪对话 | DeepSeek（OpenAI 兼容）/ Mock 互斥切换 | 有 key 即真实；无 key 全站 Mock（modelMode() 有诚实标记，AiClient.java L508-510） |
| 推荐理由 / 作品寄语 / 创作回顾 / 倾听小结 | 同上，各有降级文案 | 真实；Mock 时小结明确返回 generated=false（L295-298） |
| 陪伴编排（工具白名单 JSON 计划） | CompanionOrchestrator + companionPlan（L344-383） | 真实；blocked 时不执行工具，设计稳健 |
| RAG 非遗问答 | 豆包 Embedding + 本地 JSON 向量库 + 关键词重排 + 图谱增强 | 真实但依赖豆包 key；本地存储非 Milvus |
| AI 线稿/纹样共创 | 受约束参数生成（motif/petals/rings 白名单） | 真实；不是任意图像生成（audit L46 口径正确） |
| ASR/TTS | Gateway（默认，未配置提示）/ 千问（provider=qianwen） | **仅配置未接入**：代码真实，但无真实账号验收记录 |
| 心流/焦躁判定 | 纯规则引擎（FlowDetectionService），非模型 | 已实现；阈值配置化 |
| AI 治理 | ai_trace、成本费率、评测目录、红队文档（docs/demo/2026-10-30-safety-redteam.md） | 框架真实；评测集规模与通过率无近期运行记录 |

### 8.2 智能化增量空间（按价值/成本排序）

1. **情绪基线偏离提醒**：已有量表+打卡+便签数据，用纯统计（个人基线 + 偏离度）即可做
   「最近一周和之前的你不太一样」的温和提示——不需新模型，价值高，风险低。
2. **推荐个性化**：当前规则层不看历史行为；craft_recommend_event 点击表已在积累数据，
   可加一层「点击率/完成率反馈进排序」的轻量学习，仍保持规则可解释。
3. **审核辅助**：评论/作品先把关键词过滤结果与 AI 二审建议呈现在审核页（人工最终裁决），
   降低运营成本；不自动放行。
4. **多模态创作反馈**：看作品照片生成过程性鼓励（L4 路线已提「多模态只做过程反馈」），
   需要视觉模型 key 与隐私授权流。
5. **对话式量表 / 情绪-介质知识图谱**：audit L47 已正确判定为「不建议先于基础可靠性投入」，维持。

### 8.3 空转风险点

- 无 DEEPSEEK_API_KEY 时全站 AI 为 Mock——治理页的「模型版本」展示虽读 modelName()，
  但演示时必须在显著位置标注 Mock，否则评委/用户会把 Mock 当真实能力。
- companion memory、eval catalog、红队集都是「建好框架待持续运行」状态，
  需要固定节奏（每次模型/prompt 变更跑评测）才有意义。

---

## 9. 文档与代码冲突清单

冲突本身即问题（AGENTS 约定「冲突以仓库内为准」，但过时文档持续误导新参与者与 AI Agent）。

| # | 文档说法 | 代码现实 | 证据 |
|---|---|---|---|
| 1 | docx/概设：SSE 逐字流式、delta 增量协议 | 整段缓冲后单个事件下发 | AiClient.java L153-169；EmotionController.java L112 |
| 2 | docx：17 张表 | 47 张表 | schema.sql grep CREATE TABLE 47 处 |
| 3 | docx 详设 §7.2 接口清单（/api/recommend 等） | 实际路径体系不同（/api/craft/recommend 等） | openapi.yaml vs 详设下篇 |
| 4 | docx：uni-sse 插件 | 自研 sse.js（fetch/ReadableStream + wx.request 分块 + canIUse fallback） | zhiyu-app/src/utils/sse.js L18-215 |
| 5 | docx/概设：Milvus/Chroma 向量库 | 本地 JSON 文件 + 豆包 Embedding | application.yml L78-79；README L52 |
| 6 | docx §5.5：关键词+语义双重安全识别 | 仅本地词库+正则，明确不做语义判断 | KeywordContentSafetyFilter.java L23 |
| 7 | 概设 §3.8/详设 §5.7：失败重试 2 次指数退避 | 应用层无重试逻辑 | ai/client 包 grep 无 retry |
| 8 | docx：MinIO/对象存储 | 本地私有存储 + 签名 URL | MediaController.java L12-14 |
| 9 | project-context.md：uni-sse、Milvus、Spring Boot 3.2、「数据不出校园」 | sse.js 自研、本地文件、Boot 3.4.13、Embedding 出域 | project-context.md L34-39 vs 代码 |
| 10 | AGENTS.md §2 模块清单 13 个 | 实际 18 个（缺 companion/crisis/practice/heritage/user） | @RequestMapping grep 结果 |
| 11 | AGENTS.md §7 心流「已固化」 | 已配置化（zhiyu.flow.*） | application.yml L112-115 |
| 12 | 详设下篇 NFR：500 并发、首字<1.5s | 需求规格是 100 并发、≤2s；两份 docx 自相矛盾且均未验收 | 详设下篇 §9.5 vs 需求规格 §4.1 |
| 13 | 管理端 forget-password 模板页存在 | 实际无找回流程，登录页写「联系管理员」 | login.vue:199 vs _core/authentication/forget-password.vue |
| 14 | audit(10-21) 列「会话小结/真实语音/漏斗看板」为缺口 | 10-22 后已补齐 | 见本报告 3.2；**audit 需要再刷一轮** |

---

## 10. 优先级问题清单

### P0 —— 阻断交付

| # | 问题 | 证据 | 影响 | 建议方案 | 工作量 | 风险 |
|---|---|---|---|---|---|---|
| P0-1 | SSE 假流式 | AiClient.java L153-169；EmotionController.java L112 | 首字延迟高、文档失信、NFR-02 不可达 | 岔路口 1 二选一 | 中（句级流式 3-5 天）/ 小（改口径+前端打字机 1 天） | 句级方案缩小安全窗口，需补段级过滤测试 |
| P0-2 | 无登录失败限制 | AuthServiceImpl 无计数 | 可无限爆破密码 | Redis 计数：账号+IP 双维，5 次锁 15 分钟，管理端可解锁 | 1-2 天 | 恶意锁号 DoS，需IP维度兜底 |
| P0-3 | 无忘记密码找回 | README L187 | 学生丢号即流失 | 校内场景走「管理员人工核验重置」流程（ campus 核验模块可复用），不做邮件/短信自助 | 2-3 天 | 核验流程被社工；需操作留痕 |
| P0-4 | 无压测与真实环境验收 | 无压测脚本/报告 | 「能上线」无任何证据 | k6/JMeter 脚本 + 100 并发基线 + 真机/弱网/HTTPS/备份演练各一次记录 | 3-5 天 | 发现真实瓶颈需返工 |

### P1 —— 明显缺陷

| # | 问题 | 证据 | 影响 | 建议方案 | 工作量 | 风险 |
|---|---|---|---|---|---|---|
| P1-1 | 无通知系统 | home/index.vue:75 | 预约/审核/小组/关怀四个闭环断在最后一公里 | 站内未读表 + 首页角标 + 小程序订阅消息（预约/审核两个模板先行） | 3-5 天 | 订阅消息需用户授权运营 |
| P1-2 | 双对话系统无分工 | CompanionMessageController.java L32 | 人格分裂、维护双倍成本 | 岔路口 2 先定方向再动工 | 决策 0.5 天 | 选错方向返工大 |
| P1-3 | 文档体系失真（14 条冲突） | 第 9 节 | 误导新成员与 AI Agent | 刷新 audit + AGENTS 模块清单/§6/§7 + docx 加「历史版本」水印页 | 1-2 天 | 低 |
| P1-4 | 步骤音频上传闭环缺 | audit L22 | 传承人体验断点 | FileUploadController 加 audio 类型 + project-edit 上传组件 | 1-2 天 | 低 |
| P1-5 | AiClient 上帝类 + 合并残留碎片 | AiClient.java L465-481、L507 附近 | 可读性/评审风险 | 拆三个场景门面；清理 L507 残句 | 1 天 | 低（有 115 个测试兜底） |
| P1-6 | 推荐规则双写漂移 | RecommendRuleConfig.java L10-31 vs ai-capabilities.yaml L30-48 | 配置幻觉 | 单一事实源（建议代码为准，yaml 删规则段或改为生成物） | 0.5 天 | 低 |
| P1-7 | 管理端模板残留 | views/demos/*、_core/authentication/{code-login,qrcode-login,register,forget-password}.vue | 误认功能、打包体积 | 删除模板残留页面与路由引用 | 0.5 天 | 误删需 git 恢复 |
| P1-8 | ThreadLocal 手动跨线程 | EmotionController.java L66-71 等 | 串号隐患 | SseTaskRunner 封装 | 0.5-1 天 | 低 |

### P2 —— 增强

| # | 问题 | 建议 | 工作量 |
|---|---|---|---|
| P2-1 | 情绪基线偏离提醒 | 纯统计实现，接首页/小织 | 2-3 天 |
| P2-2 | 推荐个性化反馈层 | 用 craft_recommend_event 调排序权重 | 2-3 天 |
| P2-3 | 审核 AI 辅助 | 二审建议进审核页，人工裁决 | 2-3 天 |
| P2-4 | 传承人独立工作台 | 简化表单、移动优先 | 5 天+ |
| P2-5 | 图表下钻联动 | OperationsChart 点击→列表筛选 | 1-2 天 |
| P2-6 | Gherkin 接入 CI | 选轻量 runner（或至少做场景-测试映射检查脚本） | 1-2 天 |
| P2-7 | 视频转码/字幕/断点续传 | 媒体生产化，README L41/L254 已自认 | 5 天+，依赖基础设施 |
| P2-8 | 孤立公开文件盘点清理 | 专项脚本 + 报告 | 1 天 |

---

## 11. 需要拍板的岔路口

### 岔路口 1：SSE 流式怎么做？

- **A. 句级安全缓冲流式**：按句号/换行切段，每段过轻量安全检后立即下发，尾段做完整终检可回滚提示。
  利：真首字 ≤2s、体感质变；弊：安全窗口从「全文」缩到「句」，极端情况前半句已展示后半句命中，
  需要「撤回并重写」UI 协议；开发 3-5 天 + 补测试。
- **B. 维持整段终检，把「假流式」做实**：改文档口径 + 前端加本地打字机模拟 + 「正在倾听/安全检查」
  阶段提示。利：安全模型不动、1 天完工；弊：首字仍是全文时间，长回复等待无解。
- **我的倾向**（不替您决定）：竞赛演示选 B 足够；若目标真实试点留存，A 值得投。

### 岔路口 2：双对话系统走向

- **A. 收敛到 companion 编排层**：emotion/chat 逐步迁移，统一会话模型与人格。
  利：长期架构干净、小织有统一记忆；弊：迁移期双测双验，旧页面回归风险，2 周级工作量。
- **B. 明确双轨分工**：emotion/chat = 自由情绪倾诉（无工具），companion = 结构化练习陪伴，
  文档写清边界，共享 memory。利：零迁移；弊：永久维护两套，用户侧人格仍分裂。

### 岔路口 3：UI 继续渐进还是局部推倒？

- **A. 渐进**：统一状态组件 + 创作页按钮收敛 + 图表下钻，保持现有视觉体系。
  利：风险小、每次可验收；弊：首页/对话页的「机器感」只能缓解。
- **B. 推倒重设计三页**（首页/对话/创作，按建议书 U8 完整版）：渐变+玻璃+沉浸。
  利：答辩观感跃升；弊：3-5 天纯前端 + 双端回归，且收益主要是「好看」。

### 岔路口 4：AI 投入的下一个方向

- **A. 先把现有做真**：真实 key 验收、评测集定期跑、红队扩容、Mock 标注 UI 化。
  利：证据链扎实，试点/答辩都站得住；弊：没有「新故事」。
- **B. 加新智能面**：多模态看作品、情绪趋势预测、对话式量表。
  利：叙事亮眼；弊：在 Mock 治理、评测未跑顺前叠加新面，空转风险大。

### 岔路口 5：下一阶段范围主线

- **A. 验收导向**：P0 四项（流式口径、账号安全、压测、真机/备份）全清 → 「可试点」。
  利：从演示版变试点版，质变；弊：无新功能，观感平淡。
- **B. 产品导向**：通知系统 + 内容供给管线 + 小组运营 → 「有人用、留得住」。
  利：产品完整度跃升；弊：地基（P0）未固，楼上装修。

> 若只能选一个方向：**先 A 后 B**——P0 四项合计约 2 周，不清掉则 B 的所有成果都无法真实运行。

---

## 附：本次审查的验证边界

- 本次未运行任何构建/测试；所有测试结论引用历史记录（2026-10-18 ~ 2026-10-30）并已注明。
- 未逐页浏览器走查新增 heritage/practice 页面；5.2 第 5、6 条标记为待验证。
- docx 内容经 word/document.xml 全文提取比对，未校对其中图片内容。
- 联网检索仅用于第 6 节同类参考；Woebot/Calm 等产品对比为经验判断，未逐一查证最新版本。
