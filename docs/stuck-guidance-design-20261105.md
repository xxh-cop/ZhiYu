# 卡住引导的 AI 触点 · 设计稿

日期：2026-11-05 ｜ 基线：HEAD `75809da` ｜ 状态：仅设计；落地前须过本稿第 1 节核实结论与第 4 节验收项

---

## 0. 结论先行

**现状口径核实结论：卡住信号（dwell_stagnation）不受挂机误判影响，可以按现有信号落地。** 三问逐一核实如下（全部附行号证据，非凭记忆/报告）。

### 核实 Q1：`dwell_stagnation` 数的是「dwell 回落次数」还是「墙钟停留时长」？

**答：dwell 回落次数。** [FlowDetectionService.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/ai/flow/FlowDetectionService.java) L110-119：仅当本次心跳上报的 `dwellSeconds < stat.getDwellSeconds()`（同一步骤内 dwell 不增反降 = 一次重新停留）才 `dwellStagnationCount++`；L113 次数超 `stuckRepeatTimes`（默认 3）且本步骤未发过 → 发 `DWELL_STAGNATION`（一次性，`stagnationSignalled` 防重）。dwell 回落只在用户「离开步骤又回来」（前端计时器清零后重报更小值）时发生——**需要真实交互动作**。纯挂机 dwell 单调递增，永不回落，永不触发。
（对照项：心流判定 L135 `dwellSeconds ≥ focusIdleSeconds` 吃墙钟——那是 FLOW 信号，不是卡住信号，两路不混。）

### 核实 Q2：它与 `step_backtrack` 是否共用同一套采集/计时？

**答：同一事件源（心跳）、同一 `StepStat` 状态对象，但计数器与触发器独立——刻意解耦。**
- 同一入口：[CraftSessionServiceImpl.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/modules/craft/service/impl/CraftSessionServiceImpl.java) L233 心跳唯一调用 `evaluate(...)`，两信号都从本次心跳的 `(stepNo, dwellSeconds)` 派生。
- 触发器不同：`step_backtrack` 由步骤切换驱动（FlowDetectionService L95-99，stepNo 减小 → `repeatCount++`）；`dwell_stagnation` 由同步骤 dwell 回落驱动（L110-119，`dwellStagnationCount++`）。
- 解耦证据：步骤切换时 `dwellStagnationCount` 清零（L108），而 `repeatCount` **不清零**（L101-103，注释明写反复横跳必须累积）。
- **耦合关系（必须写进设计）**：两者共享前端 dwell 自报口径——前端计时规则若变（如暂停/节流策略调整），两个信号的输入同时变化；但修任一信号的判定逻辑不影响另一信号的计数器。落地卡住引导时**只读 `DWELL_STAGNATION` 信号，不改 `evaluate` 任何判定行**。

### 核实 Q3：后台态是否计入？有没有可见性校验？

**答：心跳载荷不含可见性字段，后端在计数路径上不区分前后台；可见性校验存在于旁路标记体系，与 stagnation 触发无关。**
- [HeartbeatReq.java](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/modules/craft/dto/HeartbeatReq.java) L11-25：仅 `stepNo/dwellSeconds/isExited/isBreathingResponded` 四字段，无 visibility。
- 若前端计时器后台继续走，dwell 继续涨 → 计入 FLOW（L135 墙钟口径）；后台不产生 dwell 回落 → **不制造 stagnation 误触发**。
- 可见性数据走另一通道：`visibility_change` / `app_lifecycle` 经事件上报落 `craft_session_event`，[AwayDetectionService](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/modules/craft/service/impl/AwayDetectionService.java) 据此判定 `suspected_away`（只标记、不扣 flow_seconds、不影响 stagnation）。
- **因此无需把「先接 suspected_away/visibility_change/app_lifecycle」列为卡住引导的落地前置**——这些字段旁路已存在，但与卡住信号无判定耦合。真正需要写明的残余风险不是挂机误判，而是**语义误伤**：真沉浸但反复翻看教程（ dwell 反复回落）会触发——由第 2 节的轻提示可拒绝设计 + 第 4 节回归集消化。

---

## 1. 触发与链路

- 触发源：心跳派生信号 `DWELL_STAGNATION`（现行链路已落 `craft_session_event` 并有看板计数，无需新增检测）。
- 触点位置：心跳响应之后、不打断心跳主链路。心跳响应延迟预算维持现状（不写进 LLM 同步等待）；引导语**异步生成、下一拍就绪**。
- 节奏约束：同一会话同一 stagnation 至多一次触点（信号本身一次性，`stagnationSignalled` 防重）；冷却期与呼吸提醒对齐（复用既有节奏，不叠加打扰）。

## 2. 提示语形态（产品红线）

- **轻提示、不阻塞、可一键收起、可全局关闭**；不弹窗、不强制播报、不模态打断。
- 形态建议：步骤页内嵌一行可收起提示条（非 overlay），收起状态本地记忆；用户级总开关（见 §4 落地代价）。
- 文案口径：陪伴语气、不评判、不诊断（「这一步可以慢一点，也可以先看看别人的做法」类）；模板兜底文案永在，AI 只负责改写润色。
- **设计原则（写入评审）**：「AI 的关心如果不可拒绝，本身就是控制感的剥夺。」任何不可关闭/不可跳过的关心形态一律不通过评审。

## 3. 模型与降级

- 复用**寄语档**（`llm-message` 熔断 key，与对话/推荐计数隔离，AGENTS §4 既有隔离口径）；模型走 ModelRouter standard 档，不新开档位。
- Prompt 外置 `resources/prompts/stuck-guide.st`（与 prompts/*.st 同纪律，显式 UTF-8）；模板兜底文案随兜底链路下发，**LLM 缺席/超时/熔断时用户看到的仍是完整可用的模板句**——AI 缺席不可感知为「坏了」。
- 延迟预算：引导语允许异步 5 秒内到位；超时直接落模板。心跳响应不因引导语生成而变慢。
- 审计：`ai_audit_log` scene 复用/沿用寄语口径登记；输入输出过既有安全过滤。

## 4. 验收项与落地代价

**验收项（不是建议，是门槛）**：
1. **与软档（呼吸提醒链路）共用同一回归集**，其中必须包含「真沉浸长停留」用例——**验证「没有在真沉浸时误触发」**：dwell 单调递增超阈值的会话，零 stagnation、零引导触点。回归集不过线不上线。
2. 语义误伤用例：反复翻看教程（dwell 反复回落）触发时，提示可一键收起且收起后同会话不再出现；全局关闭后任何路径不再出现。
3. LLM 熔断/超时/空响应三态下，模板兜底即时下发，链路无 500、无白屏。

**落地代价**：
- 用户级总开关需要 `user` 表加列（如 `stuck_guide_enabled TINYINT NOT NULL DEFAULT 1`）→ **触发 AGENTS §5.1 全套**（schema.sql + 幂等迁移 + 双 H2 + SchemaCompatibilityCheck）。这是本设计唯一的新结构。
- 无新表；事件沿用 `craft_session_event`；配置新增 prompt 文件与一个用户开关列。
- 前端：提示条组件 + 收起状态 + 设置项开关（另行派发，本稿不含前端实现）。

---

*本稿不含任何代码变更；所有行号以 HEAD `75809da` 为准。*
