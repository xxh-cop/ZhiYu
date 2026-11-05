# 心流时长占比看板：口径设计与文献依据

日期：2026-11-05 ｜ 范围：文献检索 + 指标口径设计（只读分析，未改代码）｜ 2026-11-05 增补 §8「本批产品决策」、§9「数据口径与下架状态的对齐」

---

## 0. 一句话结论（推荐口径）

> **分母用「人×周聚合的活跃创作时长」，分子用「判为心流的活跃秒数（剔除自动化播放段与挂机段）」，先算会话级占比再取人×周中位数，按项目难度（`craft_project.level`）与时长桶分层呈现，报分位数而非均值。**

`sql/schema.sql` 注释里现有的口径 `占比 = flow_seconds/(end_time-start_time)`（墙钟分母）不建议直接用于看板：它把暂停、挂机、离开时页面静置的时间全部计入分母，系统性压低并污染该指标。

三个必须事先声明的立场：

1. 该指标是**行为代理**（behavioral proxy），不是主观沉浸体验。文献中心流的测量金标准是自评（量表 / 经验取样），不存在「无交互计时 ≥ 阈值 ⇒ 心流」的实证效度证据（见 §4）。未引入校准锚点之前，看板文案建议写「**专注创作时长占比（行为代理）**」，不写「心流体验」。
2. engagement ≠ 疗效。数字心理健康文献对「用得多 = 有效果」有明确批评（§4.2）。看板不得把该指标与情绪改善并排放置暗示因果。
3. 当前数据库结构下，本文指标表中有 5 项**算不出来**（§2.3），看板必须如实显示「不可得」，不得用代理值冒充。

---

## 1. 证据分级说明

- **【实证】**：有同行评议文献支撑，且已核验出处（作者/年份/期刊/DOI 经检索确认）。
- **【共识】**：领域专业共识或方法学惯例，无单一定论性研究。
- **【工程折中】**：无直接实证依据，为实现成本与体验约束下的设计决策，需靠校准机制事后检验。
- 参考文献条目末尾标「未核验」者，表示题名/作者可信但卷期页码或 DOI 未逐条确认。

---

## 2. 仓库现状盘点（数据可得性）

### 2.1 已实现（本次核对确认）

| 事实 | 位置 |
|---|---|
| 心流判定：`dwellSeconds ≥ focus_idle_seconds(300) 且未主动退出` 判 FLOW；`回退次数 > stuck_repeat_times(3) 且未响应呼吸` 判 ANXIOUS | [FlowDetectionService.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/ai/flow/FlowDetectionService.java) |
| `flow_seconds` 会话级累计落库；步骤切换时 StepStat 重置 | [CraftSessionServiceImpl.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/modules/craft/service/impl/CraftSessionServiceImpl.java) |
| `craft_session`：user_id / project_id / status / current_step / start_time / end_time / flow_seconds | [schema.sql](file:///d:/develop/Zhi_Yu/sql/schema.sql)（L240-256） |
| `craft_project` 有 `level`（1入门/2进阶/3高阶）、`est_minutes`、`category` —— 分层维度**已存在** | schema.sql（L148-168） |
| 前端 30s 心跳，`onHide/onUnload` 发 `isExited=true`；页面隐藏即停心跳 | [session.vue](file:///d:/develop/Zhi_Yu/zhiyu-app/src/pages/craft/session.vue)（L317-351） |
| 呼吸完成回调发 `isBreathingResponded=true`（仅作心跳标志，**不落库**） | session.vue（L488-497） |
| `wellbeing_record`：通用加密自评存储（kind/record_key/payload），**可复用做校准锚点，无需新表** | schema.sql（L391-398） |
| `practice_session`：正念/绘色等会话有 status + started_at/completed_at，无心跳无心流字段 | schema.sql（L547-567） |

### 2.2 已发现的口径缺陷（设计看板前必须知道）

1. **`flow_seconds` 含 300 秒爬坡段**。`FlowDetectionService` 步骤 8：dwell 首次跨越阈值时 `delta = dwellSeconds - 0`，即进入判定前的 5 分钟被整段计入。一个刚达标的步骤贡献 ≈300s 而非 ≈0s。**这是分子放大的结构性来源**。
2. **挂机漏洞**：dwell 是墙钟（`Date.now()-stepStartTime`），只要页面停留即每 30s 心跳一次、dwell 持续增长。用户把页面开着放一边 5 分钟即被判心流。没有交互信号（点击/触摸）校验，无法区分「专注手作」与「人不在」。
3. **分母墙钟化**：schema 注释口径 `flow_seconds/(end_time-start_time)` 把 PAUSED 时段计入分母（暂停只有最终状态、无起止记录，无法事后剔除）。
4. **文案与实现不一致**：AGENTS.md 写「同一步骤反复停留次数 > 3 判焦躁」，实现是「步骤**回退**次数累计 > 3」。二者语义不同，看板引用该指标前需先裁决统一（本次只指出，不改动）。
5. **自动化内容时长不可分**：步骤引导语音频（`craft_step.audio_url`）播放时长计入 dwell，分子无法剔除「听音频」这类被动时段。

### 2.3 当前算不出来的指标（看板须标「不可得」）

| 指标 | 缺失原因 |
|---|---|
| 分步骤耗时分布 | 无步骤级日志表，只有最终 `current_step` |
| 呼吸引导响应率 | 触发事件与 `isBreathingResponded` 均不落库（在 memory/redis 的 StepStat 里） |
| 焦躁发生率 | 同上，repeatCount 不落库 |
| 活跃时长（剔除挂机/暂停） | 无交互事件与暂停区间记录 |
| 正念/短练习的专注时长 | `practice_session` 无心跳机制 |

---

## 3. 口径设计

### 3.1 推荐口径（定义）

- **分子**：会话内判为 FLOW 的活跃秒数 = `flow_seconds` 修正版：扣除进入判定前的爬坡段（或明确文档化保留），扣除音频自动播放段，扣除挂机段（判定规则见下）。
- **分母**：会话活跃时长 = 各步骤有效 dwell 之和。有效 dwell 的截断规则【工程折中】：单次心跳上报的 dwell 增量超过 `2×心跳间隔(60s)` 且期间无交互事件时，该段按 60s 封顶计入。
- **会话级占比** = 分子 / 分母；**看板指标 = 人×周的会话占比中位数**，辅以 IQR 与达标人数占比。
- **分层**：`level`（必选）、`category`、`est_minutes` 时长桶（<5 / 5–15 / 15–30 / >30 min，按会话实际活跃时长入桶）。

### 3.2 三种口径的偏差分析

| 口径 | 主要偏差 | 对本项目的具体后果 |
|---|---|---|
| 按会话等权平均 | 短会话极化：判定阈值 300s 意味着 <5min 会话占比结构性为 0（地板效应）；刷量用户权重被放大 | 大量「点进来就走」的会话把均值拉向 0，掩盖真实创作者的体验 |
| 按步骤 | 步骤难度/性质不可比：等待类、音频类步骤天然长驻留 | 「晾干 10 分钟」的步骤会被误判为心流高发步骤，误导内容优化方向 |
| 按人（推荐） | 重度用户主导风险 | 用人×周中位数 + 分布呈现缓解；同时报告活跃人数防「3 个人的看板」 |

以「人」为分析单位是 EMA/数字健康使用数据的通行做法【实证：Shiffman et al. 2008；Baumel et al. 2019 均按人聚合使用数据】。

### 3.3 该口径的已知局限与可能导致的错误决策

1. **仍可能高估**：即使剔除挂机与音频，「无步骤切换 + 页面在前台」仍可能是卡住、读说明、发呆。若运营据「占比上升」加码推送长项目，可能把用户推向挫败区间。**对策**：占比只与完成率、主动退出率联动解读，不单独考核。
2. **分层后样本稀疏**：`level×category×时长桶` 交叉后单格人数可能 <10，中位数不稳定。**对策**：看板显示单元格样本量，<10 标灰。
3. **时长桶阈值是工程折中**【工程折中】：5/15/30 分钟桶与 `est_minutes` 内容结构对齐，无文献规定；改桶阈值属于口径变更，需记录版本。
4. **名称降级会造成口径断裂**：若按 §4.2 触发自评校准弱相关（ρ 持续 <0.3）→ 看板名称降级为「专注停留占比」，**必须在指标字典写明变更时点，且改名前后数据不得跨期比较**——否则看板会出现没人解释的断裂折线，被误读为「指标崩了」。

---

## 4. 误判与信度：行为代理的效度边界

### 4.1 文献怎么说

| 证据 | 对本项目的启示 |
|---|---|
| 心流原始定义是主观体验（行动-意识融合、时间感变换、自成目的体验），挑战-技能平衡是前提条件【实证：Csikszentmihalyi 1975, 1990；Nakamura & Csikszentmihalyi 2002】 | 「无操作计时」最多对应「专注」一个维度，覆盖不了心流构念；跨难度直接对比违反挑战-技能平衡前提 |
| FSS / FSS-2 / DFS-2 是自评量表金标准（36 题，9 维度），效度证据主要在体育运动领域【实证：Jackson & Marsh 1996；Jackson & Eklund 2002】 | 量表在**游戏**场景的因子结构都曾被质疑（Procci et al. 2012），迁移到「非遗手作 + 学生」场景更不能直接假定行为代理有效 |
| ESM 用随机打断式单题自评捕捉即时体验，是心流研究的奠基方法【实证：Csikszentmihalyi & Larson 1987；Hektner et al. 2007】 | 「轻量自评锚点」有方法学正统性；但打断本身破坏心流，采样时机与频率要克制 |
| EMA 方法学：合规率随推送频率下降，反应性（被测量改变行为）是已知成本【实证：Shiffman et al. 2008】 | 自评锚点必须**可跳过、低频率、结束后触发**，不做创作中弹窗 |
| 游戏化场景的 DFS-2 应用【实证：Hamari & Koivisto 2014】、短时流量表 FKS（10 题）【实证：Engeser & Rheinberg 2008】 | 极短量表（1–3 题）做校准锚点有先例；取「时间感变换」单维度单题成本最低 |
| 行为遥测代理与自评一致性的直接研究 | **证据空白**（本次检索未找到可核验的「无操作计时 ⇒ 自评心流」效度研究）。这正是必须自建校准锚点的原因 |

### 4.2 校准锚点设计（最小打扰方案）

- **题目**：会话结束后可选单题：「刚才创作时，我忘记了时间」（1–7 分），取自 FSS-2 时间感变换维度的改写【工程折中，维度选择有实证依据：Jackson & Eklund 2002】。
- **采样**：≤20% 已完成会话随机触发；可跳过；绝不在创作中打断【实证支持克制采样：Shiffman et al. 2008】。
- **存储**：复用 `wellbeing_record`（加密 payload），无新表。
- **校准用法**：每季度算行为占比与自评的人级 Spearman 相关。若持续弱相关（如 ρ<0.3），看板文案降级为「专注停留占比」并重新检讨判定阈值——**不允许在弱相关证据下继续用「心流」命名**。
- 红线：自评仅作指标校准，不做个体层面的任何「心理状态」推断或干预触发。

### 4.3 engagement ≠ 疗效（必须在看板页脚声明）

数字心理健康文献的一致立场【实证】：真实世界 App 参与度中位水平低且快速衰减（Eysenbach 2005「流失定律」；Baumel et al. 2019 面板数据）；使用量与结局的剂量-反应关系非线性且不稳定（Donkin et al. 2013）；engagement 是多维构念，行为数据只覆盖其中一部分（Perski et al. 2017；Bijkerk et al. 2023）；「有效 engagement」定义为「足以产生预期效果所需的参与水平」，而非越多越好（Yardley et al. 2015）。本项目是陪伴引导工具而非医疗工具（AGENTS.md 红线），看板任何位置不得暗示疗效因果。

---

## 5. 指标集与定义表

### 5.1 最小可行指标集（MVP）

| 名称 | 定义 | 分子/分母 | 数据来源 | 当前可得 | 局限 |
|---|---|---|---|---|---|
| 进入率 | 推荐点击后 24h 内创建会话 | 创建会话数 / `craft_recommend_event` CLICK 数 | recommend_event + craft_session | 可得 | 仅覆盖推荐入口，自然浏览进入未计 |
| 完成率（同群） | 同批创建会话的最终状态 | FINISHED / (FINISHED+ABANDONED)，在途不计 | craft_session.status | 可得 | 须按创建时间同群，遵守 T2.3 已立原则 |
| 主动退出率 | 用户主动放弃 | ABANDONED / 全部已创建会话 | craft_session.status | 可得 | 不含「静默流失」（页面关闭未放弃） |
| 人×周中位活跃时长 | 每人每周活跃时长中位数 | 见 §3.1 分母定义 | craft_session + 心跳 | **部分可得**（挂机不可剔除） | 上线事件表前为「墙钟近似」 |
| 专注时长占比（心流代理） | §3.1 定义 | 修正 flow_seconds / 活跃时长 | craft_session.flow_seconds | **部分可得**（分子含爬坡段、分母墙钟化） | 文案必须标「行为代理」 |
| 时长桶分布 | 会话活跃时长落桶占比 | 各桶会话数 / 总会话数 | craft_session | 可得（近似） | 挂机污染同上 |

### 5.2 进阶指标集（依赖事件落库）

| 名称 | 定义 | 当前可得 |
|---|---|---|
| 焦躁事件率 | 判 ANXIOUS 的会话占比（人×周） | **不可得**（repeatCount 不落库） |
| 呼吸引导响应率 | 响应次数 / 触发次数 | **不可得**（触发与响应均不落库） |
| 分步骤耗时分布 | 步骤号 × 项目的中位耗时箱线图 | **不可得**（无步骤日志） |
| 最长连续专注段 | 会话内最长 FLOW 连续段时长 | 不可得（只存累计值） |
| 自评校准相关系数 | 行为占比 × 单题自评的人级相关 | 不可得（自评未采集） |
| 7/30 日复访率 | 会话完成后窗口期内再创作 | 可得 |

---

## 6. 看板信息架构建议

### 6.1 画什么

1. **趋势**：人×周专注占比中位数折线 + IQR 带 + 活跃人数副轴。
2. **分布**：会话活跃时长直方图（时长桶着色）；会话占比箱线图按 `level` 分面。
3. **漏斗联动**：完成率 × 主动退出率 × 进入率三联卡（同群口径），与既有疗愈漏斗看板风格一致。
4. **异常**：焦躁事件率、呼吸响应率（事件落库后）的周环比告警；数据质量卡（样本量、心跳缺失率）。
5. **页脚固定声明**：「本面板指标为行为代理，反映参与度而非疗愈效果；本系统不能替代专业心理咨询。」

### 6.2 不要画什么 + 「不要这样解读」

1. **不要把不同难度项目画在同一条完成率/占比曲线上**。挑战-技能平衡是心流前提【实证：Nakamura & Csikszentmihalyi 2002；Engeser & Rheinberg 2008】，高阶项目占比天然偏低是设计使然，不是质量问题。
2. **不要用均值大数字卡呈现占比**。占比分布通常双峰（快速流失 vs 深度投入），均值落在无人区；用中位数 + 分布图。
3. **不要把占比环比上升解读为「疗愈更有效」**（engagement ≠ 疗效，§4.3）；也**不要把下降直接解读为变差**——先查样本构成（新用户占比、attrition 曲线形态，Eysenbach 2005；Baumel et al. 2019）。
4. **不要跨端（H5 vs 小程序）直接比较**：小程序 `onHide` 时机与 H5 不同，挂机和退出心跳口径存在端间差异【工程观察，见 session.vue 生命周期处理】。
5. **不要把焦躁率当作「用户状态恶化」指标推送个维度告警**：它是行为启发式，且当前判定口径（回退计数）与文档描述不一致（§2.2-4），裁决前只能作趋势参考。

---

## 7. 最小落地改造方案（建议，不含实施）

| 优先级 | 改动 | 解决问题 | 成本 |
|---|---|---|---|
| P0 | 新增 `craft_session_event` 流水表（session_id, event_type[HEARTBEAT/STEP_CHANGE/BREATHING_TRIGGER/BREATHING_RESPONSE/PAUSE/RESUME], step_no, dwell_seconds, client_ts），心跳摘要落库 | 一次性补齐 §2.3 全部不可得指标的数据源 | 一张表 + 心跳写路径插桩 |
| P0 | 口径裁决：分子是否含 300s 爬坡段（建议计入但写入 schema 注释与 openapi）；AGENTS.md「同步骤反复停留」与实现「回退计数」二选一统一 | §2.2-1、§2.2-4 | 文档 + 注释 |
| P1 | 前端心跳附带轻量交互标志（本周期是否有触摸/点击），后端据此剔除挂机段 | §2.2-2 挂机漏洞 | 前后端小改 |
| P1 | 暂停/恢复事件落库（并入 event 表），分母剔除暂停区间 | §2.2-3 分母墙钟化 | 随 P0 表 |
| P1 | 自评锚点：复用 `wellbeing_record`，结束页 20% 采样单题 | §4.2 校准 | 前端弹层 + 一个写入接口 |
| P2 | 音频播放段标记（播放起止随心跳上报），分子剔除被动收听段 | §2.2-5 | 依赖 P0 表 |
| 不变 | `craft_session.flow_seconds` 保留作实时 UI 信号（减少打扰的触发器），看板改用事件表重算 | 运行行为与统计口径解耦 | — |

合规提示：上述任何新接口/表需同步 `openapi/openapi.yaml`、`sql/schema.sql` + migrations、`.feature` 场景（AGENTS.md DoD）；自评属敏感数据，走既有加密与授权机制，不默认开启。

---

## 8. 本批产品决策（2026-11-05 拍板）

> 本节记录产品对本报告及同批「下架状态迁移 / 音频死引用」文献评估的拍板口径。与上文历史论证冲突时，执行以本节为准；历史论证保留不改。

### 8.1 下架提示语与窗口默认值

- **提示语方向**：采用 A「说明 + 出口型」结构——发生了什么 / 截止时间 / 你能做什么；措辞温和，但**禁止承诺人设**（不以小织口吻做「我会一直陪你」式承诺）。
- **双套模板**：合规下架与运营下架各一套 `notice_key`，合规中性、运营温和；学生端不出现「违规 / 审核未通过」字样，理由详情只发传承人（沿用 WORK_AUDIT 通道）。
- **默认值（全部配置化）**：合规导出窗口 **72 小时**；运营截止 **14 天**，最小允许 **7 天**（原建议最小 3 天被否决——用户可能一周才回来一次，3 天等于没给窗口）。上线后按实际导出行为数据调参，不按感觉调。
- **Woebot 案例仅作上限参考，不作依据**：Woebot 15 天导出窗口是**整体关停**场景，与「单个项目下架」不可直接类比；合规下架的公开面收缩优先级高于对齐行业案例，不得据此拉长合规窗口。

### 8.2 音频资产处置

- **定性**：步骤音频是核心创作链路的**语音增强通道**（文字 guide 完整），不是不可替代的治疗成分。
- **处置**：走**删除**——`craft_step.audio_url` 死引用置 NULL；同步补资产字段、`license_status` 校验、启动对账与死链率监控。
- **前端纪律**：按 `audio_url` 是否存在条件渲染播放按钮，**不允许渲染一个永远失败的播放按钮**。
- 「扎染种子项目音频增强」列入内容团队待办：拿到授权素材后经配置化接口挂回，并过 `license_status` 校验后上线。

### 8.3 心流看板执行顺序

1. **先修 300 秒灌水（§2.2-1 爬坡段整段计入）与挂机漏洞（§2.2-2）**；
2. `craft_session_event` 事件表并行建设；
3. 自评校准排在事件表与会话结束钩子之后；
4. **看板出图以校准跑完为准，不设日期**；谁催着先出图，延期理由统一为「灌水/挂机未修 + 自评未校准」。

### 8.4 挂机识别口径（取代 §7 P1 的交互标志方案）

- 强信号：**页面可见性 / 应用切后台 / 屏幕状态 / 后台态无音频**；**不得要求输入事件**——手作场景下「手在忙、手机没动」正是要识别的沉浸，输入事件要求会系统性误杀。
- **前台静置默认算沉浸**。
- 仅当「停留远超同类步骤 P95 且生命周期无任何事件」时标记「疑似离席」，**只标记、不扣除**。
- 本节拍板取代 §7 P1「心跳附带轻量交互标志、据此剔除挂机段」的建议（历史建议保留备查）。

### 8.5 焦躁信号口径

- **不采**「以实现为准改文档」的裁决路径；`dwell_stagnation`（同一步骤反复停留）与 `step_backtrack`（步骤回退）**两个信号分开记录、看板分开报告**，不合并成「焦躁率」。
- 未来若需合并，须产品给出权重并写入指标字典后方可启用。

### 8.6 口径变更管理

- 任何口径定义或指标命名的变更，**须在指标字典标注变更时间点**；改名/改口径前后数据**不得跨期比较**。

---

## 9. 数据口径与下架状态的对齐（2026-11-05）

> 背景（本批新事实）：后端下架状态机已落地——`craft_project.status` 扩为 `0待审/1上架/2下架/3归档`，下架带 `takedown_kind`（`compliance`|`operational`）、`takedown_at`、`archive_at`、`export_until`、`notice_key` 五列；合规下架时进行中/暂停会话转**只读**、心跳推进被拒绝（3001）、仅可回看与在 `export_until`（默认 72h）内导出，窗口过期按归档处理；运营下架请求必填 `archive_at`，存量会话可推进至该时间，到期转只读并归档；`listSteps` 仅「不存在/待审」返回 1004，下架/归档返回 200 结构化体（`projectStatus`/`readOnly`/`canExport`/`exportUntil`/`archiveAt`/`noticeKey`/`notice`）；`craft_step` 新增 `license_status`/`attribution`/`asset_status`，项目 101 的四条死链 `audio_url` 已置 NULL。决策依据见 [takedown-audio-literature-20261105.md](file:///d:/develop/Zhi_Yu/docs/takedown-audio-literature-20261105.md)，本节只规定看板数据口径如何对齐，与 §8 决策并列执行，不重复论证。

### 9.1 分子污染规则：只读与归档会话不得计入心流分子

- **规则**：凡处于只读状态（合规下架触发）或归属于已归档项目的会话，其停留时段**不得计入心流分子，也不得计入分母**（见 9.4）。
- **理由**：只读会话中心跳推进已被拒绝（3001），用户无法切换步骤，这段停留是「查看/回看」而非「创作」。心流判定的语义前提是「专注于创作动作」；把被系统挡住的停留算作心流会系统性**抬高**占比，且抬高幅度与下架事件量正相关——审核动作越多，看板越「好看」，形成反向激励。
- **排除判定依据**（按优先级）：
  1. 会话侧/步骤侧下发的 `readOnly` 标志为 true 的时段；
  2. 会话所属项目 `projectStatus ∈ {2（且 takedown_kind=compliance）, 3}`，且当前时间已过该会话转入只读的时点（运营下架未到期的不在此列，见 9.2）；
  3. 事件表落地后（§8.3），以事件流中首次 3001 拒绝推进的时间点作为只读段起点。
- **排除方式**：分子、分母同时剔除该时段；剔除条件写入指标字典的过滤器定义，不做事后人工修正。

### 9.2 运营下架批次：可计入，但必须分层

- 运营下架且未到 `archive_at` 的会话仍可正常推进步骤，**符合「创作」语义，可以计入**分子分母。
- 但必须按 `takedown_kind=operational` **单独分层呈现，不与常态会话并线比较**：下架批次往往伴随「赶在截止前做完」的冲刺行为，步骤节奏与停留形态与常态不同；并线会同时污染两边——常态线被冲刺行为拉高，下架批次的真实常态也无法还原。
- 过 `archive_at` 转只读后，按 9.1 规则剔除。

### 9.3 新增分层维度与用法

| 维度 | 取值 | 图表用法 | 误导性并线（禁止） |
|---|---|---|---|
| `takedown_kind` | 无下架 / 运营下架 / 合规下架 | 趋势图按此分面（facet）呈现；下架批次单独成线 | 下架批次与常态会话并线比较占比（9.2 已述） |
| `readOnly` | 是 / 否 | 作为**过滤器**而非分组维度：只读时段在计算前剔除，本身不上图 | 把「只读会话占比」当作心流质量的反证——它是审核动作的产物，不是用户体验信号 |
| `projectStatus` | 0待审/1上架/2下架/3归档 | 数据质量卡：展示各状态会话在样本中的占比，供解读时折算 | 待审项目会话（通常来自测试/演示数据）与上架项目会话混入同一分布图 |

### 9.4 分母纪律：只读/归档时长必须剔除

- 分母「人×周活跃创作时长」与分子**同口径**剔除只读/归档时段（9.1）；只剔分子不剔分母会把占比压向零，只剔分母不剔分子会把占比抬上天，两种半截做法都比不剔更糟。
- **剔与不剔的差异（定性）**：不剔除时，分母被「无法推进只能停留」的时段注水，而心流判定依赖 dwell 累计、被挡会话的 dwell 极易跨过 300s 阈值，分子注水速率高于分母，占比被**抬高**；在下架集中发生的周期内，这种抬高可能表现为量级可辨的跳变，且会被误读为「用户更投入了」。剔除后，下架事件对占比的影响收敛为「样本量减少」一项，可通过活跃人数副轴直接观察。不给出数字估计——真实幅度取决于下架批次规模与只读会话滞留时长，须以上线后实测为准。

### 9.5 数据可得性更新（并入 §2.3 口径）

| 指标/字段 | 可得性 | 说明 |
|---|---|---|
| 只读会话识别（`readOnly`） | **可得**（本批新增） | 会话/步骤接口结构化体下发；看板过滤器直接使用 |
| 导出窗口剩余时间（`export_until`） | **可得**（本批新增） | 可用于「窗口内未导出」的运营提示，不做疗效解读 |
| 下架类型分层（`takedown_kind`） | **可得**（本批新增） | 9.2/9.3 分层依据 |
| 步骤时间线与真实停留（`step_leave.dwell_seconds`） | **可得**（本批新增） | 判定能力就绪；需前端上报生命周期事件后才产生数据，**当前为空表** |
| 呼吸引导触发/响应率 | **可得**（本批新增） | 同上：判定能力就绪，数据待前端上报，**当前为空表** |
| 疑似离席标记（`suspected_away`） | **可得**（本批新增） | 同上：判定能力就绪，**当前为空表**；是标记不是扣除（见表后提醒） |
| 窗口内导出次数/成功率 | **不可得** | 导出端点尚未建立，`canExport` 目前只是标记位、无行为日志；端点建立前看板显示「不可得」，不得用 `canExport=true` 冒充已导出 |
| 「下架通知后用户回流」 | **不可得** | 通知无触达回执，分子分母均无数据源；需要时先补通知回执事件，再谈指标定义 |
| 自评校准相关系数 | **不可得** | 仅保留会话结束钩子，20% 采样未实现；无自评数据，校准未启动 |
| 看板出图 | **不可得** | 出图以校准跑完为准（§8.3），当前未出图 |

> **提醒**：`suspected_away` 是**标记不是扣除**——统计口径里不得把它当作「无效时长」直接减掉；确需扣除时，必须由产品另定规则并按 §9.6 登记口径变更。

### 9.6 口径变更记录（写入指标字典）

本批已登记三处口径变化，指标字典必须逐一登记：

| 变更日期 | 变更内容 | 跨期比较纪律 |
|---|---|---|
| 2026-11-05 | 新增 `archived` 归档态（status=3），下架带 `takedown_kind` 等五列 | 变更前后的「下架项目数」「项目状态分布」不得直接并线；看板按 2026-11-05 分段 |
| 2026-11-05 | 只读会话进入数据面（合规下架触发只读 + `readOnly` 字段下发），9.1 剔除规则同步生效 | 变更点之前无「只读」概念，前后占比数据**不得跨期比较**；趋势图在变更点画分隔标记并加注，避免出现没人解释的断裂折线（呼应 §3.3-4、§8.6） |
| 2026-11-05 | `flow_seconds` 语义变更：由「含阈值前停留的总沉浸」改为「**跨过 300s 阈值后的深度沉浸秒数**」，按心跳切片累加（dwell 300 记 0，301→310→320 记 1/10/10）；判定逻辑与 `zhiyu.flow` 阈值未变 | 存量数据与修复后**结构性不可比**：每个达标片段存量值约多记 300 秒。任何跨此界线的前后对比一律**作废重算**；修复后数值下降是口径修正，**不得解释为用户专注度下降**（详见 9.7） |

> 与 §8.6「口径变更管理」一致：后续对 9.1–9.4 的任何修订同样须登记变更时点，改名/改口径前后数据不得跨期比较。

### 9.7 `flow_seconds` 语义变更与分子定义同步（2026-11-05）

- **变更内容**：后端修复「300 秒灌水」（§2.2-1 记录的结构性缺陷）——`flow_seconds` 的物理含义由「含阈值前停留的总沉浸」改为「**跨过 300s 阈值之后的深度沉浸秒数**」，按心跳切片累加（dwell 300 记 0，301→310→320 记 1/10/10）。心流判定逻辑与 `zhiyu.flow` 阈值未变；§7 P0「爬坡段计入但文档化」的建议被实现选择「剔除爬坡段」取代，此处登记为准。
- **分子定义同步**：看板分子统一表述为「**阈值后深度沉浸秒数**」（存储语义，2026-11-05 起），不再使用「沉浸总时长」「含爬坡段的累计值」等旧措辞；§3.1 分子定义中「扣除爬坡段（或明确文档化保留）」的开放措辞与 §5.1「分子含爬坡段」的局限标注，自变更点起成为历史描述。分子仍需按 9.1 剔除只读/归档段；音频被动段剔除仍待 §7 P2；`suspected_away` 段只标记不扣除（§9.5 表后提醒）。
- **为什么修复前的数值不可用于任何基线**：存量值在每个达标片段上约多记 300 秒，误差大小与「跨过阈值的片段数」成正比——是**结构性偏差而非随机噪声**，无法通过整体缩放或回归修正；且不同长度会话的跨阈片段数不同，污染程度随会话长度分布变化，组间亦不可比。因此任何包含修复前数据的基线、A/B 对比或趋势分析结论一律无效，必须按新语义重算历史（若原始心跳/事件数据足以重算），或将基线起点设为变更点。

---

## 10. 参考文献

已核验 = 本次检索确认了出处要素；未核验 = 题名/作者可信但卷期页码或 DOI 未逐条确认。

1. Csikszentmihalyi, M. (1975). *Beyond Boredom and Anxiety: Experiencing Flow in Work and Play*. Jossey-Bass.（经典原著，公知文献）
2. Csikszentmihalyi, M. (1990). *Flow: The Psychology of Optimal Experience*. Harper & Row.（公知文献）
3. Nakamura, J., & Csikszentmihalyi, M. (2002). The concept of flow. In C. R. Snyder & S. J. Lopez (Eds.), *Handbook of Positive Psychology* (pp. 89–105). Oxford University Press.（页码未核验）
4. Jackson, S. A., & Marsh, H. W. (1996). Development and validation of a scale to measure optimal experience: The Flow State Scale. *Journal of Sport and Exercise Psychology*, 18(1), 17–35. https://doi.org/10.1123/jsep.18.1.17 〔已核验〕
5. Jackson, S. A., & Eklund, R. C. (2002). Assessing flow in physical activity: The Flow State Scale–2 and Dispositional Flow Scale–2. *Journal of Sport and Exercise Psychology*, 24(2), 133–150. https://doi.org/10.1123/jsep.24.2.133 〔期刊/年份已核验〕
6. Engeser, S., & Rheinberg, F. (2008). Flow, performance and moderators of challenge-skill balance. *Motivation and Emotion*, 32, 158–172. https://doi.org/10.1007/s11031-008-9102-4 〔已核验〕
7. Csikszentmihalyi, M., & Larson, R. (1987). Validity and reliability of the Experience-Sampling Method. *Journal of Nervous and Mental Disease*, 175(9), 526–536. https://doi.org/10.1097/00005053-198709000-00004 〔卷期页已核验〕
8. Shiffman, S., Stone, A. A., & Hufford, M. R. (2008). Ecological momentary assessment. *Annual Review of Clinical Psychology*, 4, 1–32. https://doi.org/10.1146/annurev.clinpsy.3.022806.091415 〔已核验〕
9. Hektner, J. M., Schmidt, J. A., & Csikszentmihalyi, M. (2007). *Experience Sampling Method: Measuring the Quality of Everyday Life*. Sage.（专著，未逐条核验）
10. Eysenbach, G. (2005). The law of attrition. *Journal of Medical Internet Research*, 7(1), e11. https://doi.org/10.2196/jmir.7.1.e11 〔已核验〕
11. Baumel, A., Muench, F., Edan, S., & Kane, J. M. (2019). Objective user engagement with mental health apps: Systematic search and panel-based usage analysis. *Journal of Medical Internet Research*, 21(9), e14567. https://doi.org/10.2196/14567 〔已核验〕
12. Perski, O., Blandford, A., West, R., & Michie, S. (2017). Conceptualising engagement with digital behaviour change interventions: A systematic review using principles from critical interpretive synthesis. *Translational Behavioral Medicine*, 7(2), 254–267. https://doi.org/10.1007/s13142-016-0453-1 〔卷期页已核验〕
13. Mohr, D. C., Cuijpers, P., & Lehman, K. (2011). Supportive accountability: A model for providing human support to enhance adherence to eHealth interventions. *Journal of Medical Internet Research*, 13(1), e30. https://doi.org/10.2196/jmir.1602 〔已核验〕
14. Donkin, L., Hickie, I. B., Christensen, H., et al. (2013). Rethinking the dose-response relationship between usage and outcome in an online intervention for depression: Randomized controlled trial. *Journal of Medical Internet Research*, 15(10), e231. https://doi.org/10.2196/jmir.2771 〔已核验〕
15. Bijkerk, L. E., Oenema, A., Geschwind, N., & Spigt, M. (2023). Measuring engagement with mental health and behavior change interventions: An integrative review of methods and instruments. *International Journal of Behavioral Medicine*, 30(2), 155–166. https://doi.org/10.1007/s12529-022-10086-6 〔已核验〕
16. Yardley, L., Morrison, L., Bradbury, K., & Muller, I. (2015). The person-based approach to intervention development: Application to digital health-related behavior change interventions. *Journal of Medical Internet Research*, 17(1), e10. https://doi.org/10.2196/jmir.4055 〔已核验；「有效 engagement」概念源〕
17. Riley, J., Corkhill, B., & Morris, C. (2013). The benefits of knitting for personal and social wellbeing in adulthood: Findings from an international survey. *British Journal of Occupational Therapy*, 76(2), 50–57.（期刊/卷期已核验；页码与 DOI 未核验）
18. Procci, K., Singer, A. R., Levy, K. R., & Bowers, C. (2012). Measuring the flow experience of gamers: An evaluation of the DFS-2. *Computers in Human Behavior*, 28(6), 2306–2312.（题名/作者已核验；卷期与 DOI 未核验）
19. Hamari, J., & Koivisto, J. (2014). Measuring flow in gamification: Dispositional Flow Scale-2. *Computers in Human Behavior*, 40, 133–143. https://doi.org/10.1016/j.chb.2014.07.048 〔DOI 已核验〕
