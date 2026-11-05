# 下架状态迁移与音频死引用：文献评估与决策依据

日期：2026-11-05 ｜ 用途：决策依据存档（供评审；只读分析，未改代码）

> **证据边界一句话**：本报告为「下架改状态迁移、区分合规/运营、温和有截止、提示语前置」提供**专业共识与相邻领域实证**支持；**不支持**「突然中断本身即疗愈伤害」这一强表述（无直接实证，仅有间接外推），也**不支持**以「保护用户」为由强制掐断用户私有创作会话。

---

## 0. 裁定一览

| 决策 | 裁定 | 一句话理由 |
|---|---|---|
| 1 · 下架重构为状态迁移 | **部分支持** | 「状态迁移不删除、区分合规/运营、温和有截止、提示语前置」方向有充分专业共识与相邻实证支持；但「合规性下架**强制中断**进行中会话」与创伤知情原则、艺术治疗作品归属伦理相冲突，且「突然中断本身即疗愈伤害」**没有直接实证**，属拔高表述，需改写为分层处理 |
| 2 · 种子音频死引用 | **支持**（含一处修正） | 该音频在核心创作流程（扎染种子项目步骤引导）中，不是氛围装饰；死链必须修。修正点：经核实前端播放失败已有显式 toast 兜底（非完全静默降级），且文字 guide 完整、音频是**增强通道**——因此「必须补真实音频否则阻塞上线」应改为「补或删二选一，不允许带死链上线；音频本身缺失不构成创作功能阻塞」 |

---

## 1. 仓库现状（只读核实，裁定的前提）

- `craft_project.status` 现语义 `0待审/1上架/2下架`，AY1（2026-11-05）已实现**终态下架** `1→2`：理由必填落 `audit_reason`、WORK_AUDIT 通知传承人、记 `admin_operation_log`、私有媒体授权即时失效、不可复活（[openapi.yaml](file:///d:/develop/Zhi_Yu/openapi/openapi.yaml) `adminProjectTakedown`）。
- 进行中会话**没有**显式中断机制：`create()` 挡新建（非上架 1004），但 `changeStatus()` 不校验项目状态；而 `listSteps()` 要求 `status==1` 否则 1004——**项目下架瞬间，进行中会话取步骤即 1004，构成无提示的隐性硬中断**（[CraftSessionServiceImpl.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/modules/craft/service/impl/CraftSessionServiceImpl.java)、[CraftProjectServiceImpl.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/modules/craft/service/impl/CraftProjectServiceImpl.java)）。这正是决策 1 要消除的问题，且它**已经以意外形式存在**。
- 音频死引用属实：[init-data.sql](file:///d:/develop/Zhi_Yu/sql/init-data.sql) L91–95 为项目 101（扎染）种了 `craft_step.audio_url=/static/audio/101-1..4.mp3`；`zhiyu-app/src/static/` 下无 `audio/` 目录。前端 [session.vue](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/craft/session.vue) 对 `/` 开头路径拼 `API_BASE_URL`，即实际请求**后端**静态路径，后端同样无此文件。
- 但「静默降级」的说法不准确：`session.vue` `playGuide` 的 `onError` 已 `toast.care('音频暂时无法播放，可继续阅读文字引导')`。死链的真实代价是：**旗舰种子项目的播放按钮永远失败**——每次点击都是一次可预期性违背，而非无提示卡死。
- 规格文档已有相关约束：BR-07「音频不得默认自动播放」；UC16 要求展示「音频来源/署名」；UC09 备选流 5a「项目已下架：系统说明不可继续并提供返回入口」。该死引用已在 [delivery-status-20261105.md](file:///d:/develop/Zhi_Yu/docs/delivery-status-20261105.md) §2.4 列为待产品决策（gap B1）。

---

## 2. 决策一评估：下架重构为状态迁移

### 2.1 有支持的部分

**「可预期、可处理的结束」优于「突然、单方的结束」——有相邻领域实证。**

- Rabinowitz, Yim & Muran（2025）对心理治疗终止阶段的 PRISMA 系统综述（67 篇）指出：治疗师发起或**双方协商的终止**与更高的治疗满意度和更好结局相关；成功的终止阶段与更好的整体疗效相关；负面的终止经历损害治疗关系。CBT 的传统做法是从治疗开始就预告终止、逐步拉长间隔（*Cogent Mental Health*, 4(1):2535626）。
- Eubanks, Muran & Safran（2018）的元分析（11 研究、1314 名来访者）发现：联盟破裂**被修复**的来访者结局显著优于破裂未修复者（r=0.29, d=0.62），甚至优于从未破裂者（r=0.24, d=0.50）——即「断裂不可怕，没有交代和出口的断裂才可怕」（*Psychotherapy*, 55(4):508–519, DOI: 10.1037/pst0000185）。
- Swift & Greenberg（2012）元分析显示成人心理治疗非计划脱落率约 1/5（*JCCP*, 80(4):547–559, DOI: 10.1037/a0028226）——「无交代的结束」在现实中大量发生且与研究关注的较差结局聚集。

**「透明、可预期、选择权、避免二次伤害」是创伤知情设计的核心原则——有权威指南支持。**

- SAMHSA（2014）*SAMHSA's Concept of Trauma and Guidance for a Trauma-Informed Approach*（HHS Pub. No. SMA 14-4884）六原则：安全；**可信与透明**；同伴支持；协作；**赋能、发声与选择**；文化/历史/性别。系统对用户的每一次状态变更都应「可预期、有解释、给选择」。
- Chen et al.（2022, CHI, DOI: 10.1145/3491102.3517475）将创伤知情原则形式化为计算系统的设计框架，质性证据支持「技术系统会制造或加重创伤体验，应按安全/信任/选择设计」。

**「作品归属用户、终止时归还、告知保管与留存方式」是艺术治疗专业伦理。**

- AATA《Ethical Principles for Art Therapists》（2013 修订）§4.0：「client artwork as a form of protected information and **the property of the client**」；§4.1：治疗过程中及**终止时**作品可归还来访者，机构留存副本需告知（§4.1.a）；§4.7：必须向来访者说明作品的存储方式与留存时长；§5.0 重申作品归属来访者。这直接支持「内容留存取证、只读、可导出」的设计方向。

**「通知 + 申诉 + 数据可得」是内容治理的通行底线。**

- Santa Clara Principles（2018 三原则：Numbers/**Notice**/**Appeal**；2021 2.0 扩展）要求：下架通知须包含可识别内容、违反的具体条款、检测途径、申诉路径，且**通知必须以持久形式可得，即便账号被停权也能访问**。Apple、Meta、Google、Reddit 等 12 家公司背书。

### 2.2 必须修改的部分

**修改点 A：合规性下架「强制中断进行中会话」应改为「分层处理」。**
强制中断的是**对外可见性与新增**，不应是**用户手中的私有会话**。理由：

1. 合规下架的保护对象是公众与平台（停止传播、留存取证），用户私有的创作进度不是传播面，强制掐断它没有合规收益；
2. 创伤知情原则（SAMHSA 2014）与断裂-修复证据（Eubanks 2018）都指向「可预期 + 给出口」，而「系统替你做主、立刻掐断」恰恰是创伤触发模式（丧失控制、无预告、无选择）；
3. AATA §4.1 作品在终止时**归还来访者**——对会话产物，「导出」比「掐断」更贴近专业伦理。

建议：合规下架 → 立即停止公开展示/授权/新建（现状已做到），进行中会话进入**只读 + 短窗口导出**，窗口结束转 `archived`（留存、不可交互）。

**修改点 B：「突然中断本身即疗愈伤害」必须降级表述。**
如实声明：**没有**针对「数字内容/功能被下架导致用户可测量心理伤害」的直接实证研究。Rabinowitz et al.（2025）明确指出终止阶段实证研究稀缺，且该综述**排除**了 premature termination 文献；forced termination（被迫终止）方向的现有文献**仅有临床个案观察与质性描述，不构成实证支撑**（落盘版不引用具体篇目）。现有的只是**间接外推链**：协商终止 > 单方终止（咨询领域）+ 断裂需修复（联盟研究）→「在疗愈语境的产品里，突然、无解释、无出口的中断更可能损害信任与继续求助意愿」。这是合理的**专业共识级推理**，不是实证结论；在设计文档里应写为设计原则（precautionary），不得写为「已证实的伤害机制」。

**修改点 C：提示语应按「类别」而非「原因详情」向学生披露。**
合规下架的具体理由给传承人（现状 WORK_AUDIT 已做），给学生只给**类别 + 状态 + 截止 + 出口**——避免把学生卷入审核细节，也避免「违规」等指向性措辞造成的羞耻感（NN/g 错误信息研究：敌意/指责性措辞增加认知负荷与负面情绪，Kaplan 2022）。

### 2.3 反对证据与权衡

- **对抗证据 1（合规侧）**：DMCA 17 U.S.C. §512(c) 要求服务商收到通知后「**expeditiously** 移除或禁用访问」才能保安全港；主流 UGC 平台对违规内容普遍**立即下线**。用户「温和下架」的主张与合规惯例冲突。**权衡结论：分层处理可兼得**——公开面立即收（合规），私有面给窗口（创伤知情）。延迟「公开下架」不可接受；延迟「私有数据销毁/不可访问」则可接受且被头部产品实践。
- **对抗证据 2（实践侧）**：Woebot（150 万用户、14 项 RCT 的头部循证心理健康产品）2025-06-30 整体关停时，账号于 07-31 锁定，**导出申请窗口仅 15 天**（07-15 截止），之后数据匿名化（Woebot Health 官方 FAQ）。说明即便最谨慎的玩家也是「固定截止 + 到期处置」，**不是无限期留存**。注意：这是**整体关停**场景，与「单个项目下架」不可直接类比，仅作「上限参考」，不得据此拉长合规窗口（见 §6 拍板）。
- **对抗证据 3（实证侧）**：abrupt termination 的伤害证据本身薄弱（见修改点 B），且 Eubanks 2018 显示修复后的破裂甚至比无破裂结局更好——意味着「一次处理得当的下架沟通」可能是信任建设机会而非纯损失。权衡：不必把下架视为纯防御动作，提示语质量本身就是干预点。

---

## 3. 决策二评估：音频死引用

### 3.1 音频角色定性

死引用位于**核心创作流程**：`craft_step.audio_url` 是扎染种子项目（id=101）四个步骤的引导语音，[session.vue](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/craft/session.vue) 在步骤卡渲染「播放引导语音」按钮。它不是背景氛围（氛围在音乐/正念页，走 `data/music.js` 与合成白噪音，是另一条链路）。
但注意：每步的**文字 guide 是完整且必有的**（同一 INSERT 语句里的 `guide` 列），音频是**同一引导内容的语音增强通道**。因此准确的角色定性是：**核心链路上的增强通道，不是不可替代的治疗成分**。据此：

- 「核心疗愈引导 → 必须补真实音频、列为上线阻塞项」的判断**过重**：音频缺失时文字引导完整可用，UC10 主流程不阻塞；
- 「不允许静默降级、要显式占位」的判断**方向正确且已部分实现**（onError toast + 文字兜底），真正的问题是**死链本身**——按钮承诺了永远失败的交互。

### 3.2 支持与修正

**支持「补或删，二选一，不允许死链上线」**：

- 可预期性/可信透明（SAMHSA 2014；NN/g 系统状态可见性原则）：一个永远失败的按钮是持续的可预期性违背，比没有按钮更伤信任。
- 错误处理研究（Park, MacDonald & Khoo 2012, CHI EA:731–740, DOI: 10.1145/2317956.2318067；Mahmood et al. 2022, CHI, DOI: 10.1145/3491102.3517565）：带歉意、解释与修复路径的信息能改善错误后的情绪反应与信任恢复——但前提是**错误是例外**；对**必然失败**的功能，正确做法是移除承诺，而非靠道歉文案兜底。

**修正「上线阻塞项」的范围**：阻塞的是「音频资产死链与授权不明」这件事，不是创作功能。两条合法路径：

- 路径一（补）：替换为 CC0 / 自有录制 / 已购授权音频，`license_status` 标记为 `pending_review`，上线前必须转 `licensed`；
- 路径二（删）：迁移脚本将 4 条 `audio_url` 置 NULL（其他种子项目本就为 NULL），音频能力等资产体系补齐后再挂。

评估时建议路径二（尚无真实音频资产、小程序端无合成能力），路径一留给内容团队拿到授权素材后。**最终拍板确认为路径二**（见 §6）。

**支持「音频资产管理抽象缺失」的定性**：现状 `audio_url` 只是 `craft_step` 上一个裸字符串字段，无存储、无授权状态、无完整性对账。死引用能活着进种子数据，正是因为没有「资产」概念。BR-07、UC16 已要求来源/署名，字段层面现在没有载体。

### 3.3 反对意见

有观点认为「音频是疗愈核心、缺了就该整体阻塞」——过度。音乐/正念干预有独立实证基础（如音乐治疗对抑郁的 Cochrane 综述，Aalbers et al. 2017，**本次未展开核验**），但那指的是**结构化音乐治疗干预**，不是「步骤说明的语音版」。把步骤朗读音频定性为治疗成分会拔高资产要求、拖慢上线。建议：音频定位为**可达性/沉浸增强**（服务不方便阅读或偏好听觉引导的用户），缺失时功能完整。

---

## 4. 证据分级总表

| # | 主张 | 等级 | 依据 |
|---|---|---|---|
| 1 | 协商/有计划的结束优于单方突然结束 | **实证（系统综述）** | Rabinowitz et al. 2025（67 篇 PRISMA 综述） |
| 2 | 关系断裂需被修复，修复后结局可优于未断裂 | **实证（元分析）** | Eubanks et al. 2018（11 研究，r=0.29/d=0.62） |
| 3 | 非计划脱落普遍且与治疗失败聚集 | **实证（元分析）** | Swift & Greenberg 2012 |
| 4 | 系统提示语应可预期、透明、给选择 | **专业伦理守则与指南** | SAMHSA 2014（SMA 14-4884）六原则 |
| 5 | 数字产品应按创伤知情原则设计 | **实证（质性）+ 框架** | Chen et al. 2022（CHI） |
| 6 | 作品归来访者所有、终止时归还、告知留存方式 | **专业伦理守则与指南** | AATA 2013 §4.0/4.1/4.7、§5.0 |
| 7 | 治疗师不得遗弃来访者、终止前需辅导与转介 | **专业伦理守则与指南** | APA 伦理守则 §10.10（条文存在性已核验、**逐字内容未核验**） |
| 8 | 下架须通知（含条款/申诉/持久可得） | **治理规范与法规** | Santa Clara Principles 1.0/2.0（多组织共识） |
| 9 | 合规内容须迅速移除 | **治理规范与法规** | DMCA 17 U.S.C. §512(c)/(g) |
| 10 | 道歉/真诚认错的系统语言改善情绪与信任 | **实证（HCI 实验）** | Park et al. 2012；Mahmood et al. 2022 |
| 11 | 「突然中断数字疗愈内容造成可测量伤害」 | **无直接证据** | 仅有临床个案观察与质性描述，不构成实证支撑；仅存在相邻领域间接外推 |
| 12 | 「作品是情绪记录」 | **专业共识（非实证）** | AATA 作品受保护信息原则 + 艺术治疗学理传统；无量化伤害研究 |
| 13 | 头部心理健康产品关停实践（限时导出 + 到期匿名化） | **行业实践（官方公告）** | Woebot Health FAQ（2025-06-30 整体关停；**仅作上限参考**） |

---

## 5. 落地建议（评估稿，供对照；最终执行口径以 §6 与 flow 报告 §8 为准）

### 5.1 状态机（`craft_project`）

现状只有 `0/1/2`，建议扩字段而非硬编码语义：

| 新增列 | 类型 | 说明 |
|---|---|---|
| `takedown_kind` | VARCHAR(16) NULL | `compliance` / `operational`，仅 status=2 时有值 |
| `takedown_at` | DATETIME NULL | 下架时间 |
| `archive_at` | DATETIME NULL | 转 archived 的截止时间（运营下架必填，合规下架可空=立即） |
| `export_until` | DATETIME NULL | 私有导出/只读窗口截止 |
| `notice_key` | VARCHAR(64) NULL | 提示语模板 key（见 5.4） |

合法迁移：`1→2`（带 kind）、`2→4`（archived，留存到期或窗口结束）、`1→4`（仅数据清理场景）；**2/4 不可回 1**（与 AY1 终态口径一致；恢复上线走新建送审）。若暂不加状态值，可保留 `status=2` 并用 `takedown_kind` 区分两类下架，`archived` 用 `archive_at` 是否到期派生——**二选一，不要同时存在两套语义**。

### 5.2 进行中会话的差异化处理（修当前隐性硬中断）

- **合规下架**：`create` 继续挡新建；进行中/暂停会话置**只读**（新增会话状态如 `5 suspended` 或复用 `2 暂停` + 标记位），`export_until` 内可查看历史步骤与导出产物，不可推进步骤；窗口结束随项目转 archived（留存只读）。**消除 `listSteps` 对进行中会话的 1004 隐性中断**——改为按会话状态返回「已中止 + 截止时间 + 导出入口」的结构化响应。
- **运营下架**：挡新建（现状），存量会话**不受影响**运行至 `archive_at`；到期未完成会话自动转只读。
- 参数配置化（参照 `zhiyu.flow.*` 先例，放 `application*.yml` + 管理端配置表，代码只读配置）：`zhiyu.takedown.compliance.export-window-hours`、`zhiyu.takedown.operational.default-deadline-days`、`zhiyu.takedown.export-enabled`。**默认值是产品决策，文献只支持「必须有明确截止」，不支持任何具体天数**（最终取值见 §6）。

### 5.3 导出 / 只读的取舍

**导出优先，只读兜底**：导出范围限用户自己的会话产物（步骤进度、完成作品草稿、心情记录），对应 AATA §4.1「终止时归还」与 GDPR Art.20 数据可携带方向；平台侧内容（非遗目录、项目图文）不留导出义务，只读快照即可。Santa Clara Notice 原则要求通知「持久可得」——下架通知与截止时间应在站内信持久可见，不能只是 toast。

### 5.4 提示语：设计原则 + 表述方向（不定稿文案）

原则（均有出处）：① 给**具体时间**与状态（透明/SAMHSA；NN/g 系统状态可见性）；② 承认中断、说明类别、不指责用户（Park 2012；Kaplan 2022 敌意错误信息研究）；③ 给**出口**：导出、回看、替代入口（SCP Notice；UC09 备选流 5a 雏形）；④ 页面保留「不能替代专业心理咨询」声明（仓库红线）；⑤ 模板外置到 `resources/notices/*.md` 或配置表，代码只引用 `notice_key`（与 prompts/*.st 同纪律）。

三个可选方向（供产品定稿前筛选）：

- **A 说明+出口型**：事实结构——「该项目因〔类别〕已于 X 停止。你做到这里的内容在 Y 前可导出/回看。」
- **B 陪伴口吻型**：小织在场、短句、不拟人化承诺——「这段创作旅程到这里先停一下。你已完成的部分都在，Y 前可以随时保存下来。」
- **C 极简系统型**：状态+截止+按钮——「项目已下架（〔类别〕）。导出进度（Y 截止）· 返回」

给学生不出现「违规/审核未通过」字样；理由详情只发传承人（现状 WORK_AUDIT 机制沿用）。

### 5.5 音频资产

- 抽象资产字段（新增媒体资产表或扩展 craft_step）：`url`、`license_status`（`none/cc0/owned/licensed/pending_review`）、`attribution`、`source_note`、`asset_status`（`ready/missing/disabled`）。UC16「来源/署名」前端展示直接读这两个字段。
- 死引用处置：迁移脚本将 init-data.sql L91–95 四条 `audio_url` 置 NULL；如选择补，替换文件后 `license_status=pending_review` + TODO，上线前必须 `licensed`。
- 监控：启动对账（仿 `SchemaCompatibilityCheck` 思路）——DB 中 `asset_status=ready` 的音频与存储逐一核对，缺失即告警降级为 `missing`；前端播放失败埋点计数（沿用现有 error 回调），死链率入管理端健康页。

---

## 6. 本批产品决策（2026-11-05 拍板）

> 本节与 [flow-metrics-dashboard-literature-20261105.md](file:///d:/develop/Zhi_Yu/docs/flow-metrics-dashboard-literature-20261105.md) §8 同源记录；两处内容以「不重复长篇论证、交叉引用」方式维护，如有出入以最新拍板为准。

- **提示语**：采用 **A「说明 + 出口型」**（发生了什么 / 截止时间 / 你能做什么），措辞温和但**禁止承诺人设**；合规与运营两套 `notice_key`，合规中性、运营温和；学生端不出现「违规/审核未通过」字样，理由详情只发传承人（沿用 WORK_AUDIT）。
- **默认值（全部配置化）**：合规导出窗口 **72 小时**；运营截止 **14 天**，最小允许 **7 天**（否决评估稿的最小 3 天——用户可能一周才回来一次，3 天等于没给窗口）；上线后按实际导出行为数据调参，不按感觉调。Woebot 案例仅作上限参考（整体关停场景，不可直接类比）。
- **合规下架不掐断私有会话**：公开面立即收 + 进行中会话只读 + 窗口内可导出 + 到期转 archived 归档；本地代码已有的隐性硬中断（`listSteps` 项目非上架即 1004）在本批改造中改为「已中止 + 截止时间 + 导出入口」的结构化响应。
- **音频处置走删除**：`craft_step.audio_url` 死引用置 NULL + 资产字段（`license_status`/`attribution`/`asset_status`）+ 启动对账 + 死链率监控；前端按 `audio_url` 是否存在条件渲染，**不渲染永远失败的播放按钮**；「扎染种子项目音频增强」列入内容团队待办，拿到授权后按配置化接口挂回并过 `license_status` 校验。
- **挂机识别与焦躁双信号口径**：以 [flow 报告](file:///d:/develop/Zhi_Yu/docs/flow-metrics-dashboard-literature-20261105.md) §8.4/§8.5 为准（生命周期强信号、前台静置算沉浸、疑似离席只标记不扣除；`dwell_stagnation` 与 `step_backtrack` 分开记报），本节不重复。

---

## 7. 参考文献

已核验 = 本次检索确认了出处要素（作者/年份/期刊/DOI 或官方页面原文）；标注「未核验」者表示该要素未逐条确认，不补页码或卷期。**落盘版剔除了两条个案级引用**（forced termination 方向的临床个案观察），相应论证已在 §2.2 改为降级表述。

1. Rabinowitz, Y. L., Yim, B., & Muran, J. C. (2025). Termination of psychotherapy: A systematic review. *Cogent Mental Health*, 4(1), 2535626. https://doi.org/10.1080/28324765.2025.2535626 〔已核验〕
2. Eubanks, C. F., Muran, J. C., & Safran, J. D. (2018). Alliance rupture repair: A meta-analysis. *Psychotherapy*, 55(4), 508–519. https://doi.org/10.1037/pst0000185 〔已核验〕
3. Swift, J. K., & Greenberg, R. P. (2012). Premature discontinuation in adult psychotherapy: A meta-analysis. *Journal of Consulting and Clinical Psychology*, 80(4), 547–559. https://doi.org/10.1037/a0028226 〔已核验〕
4. Chen, J. X., McDonald, A., Zou, Y., Tseng, E., Roundy, K., Tamersoy, A., Schaub, F., Ristenpart, T., & Dell, N. (2022). Trauma-informed computing: Towards safer technology experiences for all. *CHI '22*, 1–20. https://doi.org/10.1145/3491102.3517475 〔已核验〕
5. Park, S. J., MacDonald, C. M., & Khoo, M. (2012). Do you care if a computer says sorry? *CHI EA '12*, 731–740. https://doi.org/10.1145/2317956.2318067 〔已核验〕
6. Mahmood, A., Fung, J. W., Won, I., & Huang, C.-M. (2022). Owning mistakes sincerely: Strategies for mitigating AI errors. *CHI '22*, 1–11. https://doi.org/10.1145/3491102.3517565 〔已核验〕
7. SAMHSA (2014). *SAMHSA's Concept of Trauma and Guidance for a Trauma-Informed Approach* (HHS Publication No. SMA 14-4884). Substance Abuse and Mental Health Services Administration. 〔官方原文已读〕
8. American Art Therapy Association (2013). *Ethical Principles for Art Therapists*（§4.0/4.1/4.7/5.0 已读原文）. 〔已核验〕
9. American Psychological Association (2017). *Ethical Principles of Psychologists and Code of Conduct*, §10.10 Terminating Therapy. 〔条文存在性已核验、**逐字内容未核验**〕
10. Santa Clara Principles 1.0 (2018) & 2.0 (2021). *Santa Clara Principles on Transparency and Accountability in Content Moderation*. santaclaraprinciples.org（Notice/Appeal 原文已读）. 〔已核验〕
11. 17 U.S.C. §512（DMCA 安全港与 notice-and-takedown）, govinfo / copyright.gov. 〔法规原文存在性已核验〕
12. Kaplan, K. (2022). Hostile patterns in error messages. *Nielsen Norman Group*；Neusesser, T., & Sunwall, E. (2023). An error messages scoring rubric. *Nielsen Norman Group*. 〔专家意见；页面已核验〕
13. Woebot Health 官方 FAQ（2025）：应用 2025-06-30 退役、07-15 导出申请截止、07-31 账号锁定、数据匿名化。 〔官方页面已读；**行业实践证据，整体关停场景**〕

---

## 8. 局限声明

- 音乐治疗疗效文献（如 Aalbers et al. 2017 Cochrane 综述）**本次未展开核验**，仅用于说明「音频 ≠ 治疗成分」的论证边界。
- 「数字疗愈内容下架造成直接伤害」未检索到任何直接实证研究；报告中所有相关推断均已标注为间接外推或专业共识，不得引为实证结论。
- 行业实践案例（Woebot）为**整体关停**场景，与「单个项目下架」不可直接类比，仅作上限参考，不得据此拉长合规下架的导出窗口。
