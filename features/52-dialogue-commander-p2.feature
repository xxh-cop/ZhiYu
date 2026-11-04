#language: zh-CN
功能: 调度层 P2 落地（Dialogue Commander 三道闸与协议，AA 批 2026-11-04）
  作为平台架构组
  我希望调度层在默认关闭的前提下具备规则路由、三道闸与危机绕行能力
  以便开关开启后两条对话线安全收敛到统一入口，关闭时行为与今天完全一致
  （依据 docs/dialogue-unification-20261104.md §9 P2；开关 zhiyu.dialogue.commander 默认 false）

  背景:
    假定 系统已启动，学生 alice 已登录且同意隐私政策

  # ── 入口安检（危机红线，AA2） ─────────────────

  场景: 危机输入零 LLM 调用，预置关怀话术直达
    假定 dialogue.commander=true
    当 alice 发送命中高危词的输入
    那么 在路由之前即返回预置关怀话术（chunk + done(crisis=true, revoked=true)）
    并且 全程不产生任何 LLM 调用（mock 计数断言为零增长）
    并且 care_reminder 与 ai_audit_log 照常落库，ai_trace 记 fallback_reason=ROUTE_CRISIS

  # ── 规则路由（AA3，无模型分类器） ─────────────

  场景: 练习页且练习会话活跃 → PRACTICE_COMPANION（R1）
    假定 dialogue.commander=true，且 alice 有一个进行中的练习会话
    当 她以 entryContext=PRACTICE_PAGE 发送消息
    那么 命中 R1，由 PRACTICE_COMPANION 执行
    并且 ai_trace 记 scene=dialogue-route、capability=PRACTICE_COMPANION、rule=R1

  场景: 知识页 → KNOWLEDGE（R2），显式知识意图 → KNOWLEDGE（R3）
    假定 dialogue.commander=true
    当 alice 以 entryContext=KNOWLEDGE_PAGE 提问
    那么 命中 R2 路由到 KNOWLEDGE
    当 alice 携带 intentHint=knowledge_* 或 ask_heritage
    那么 命中 R3 路由到 KNOWLEDGE

  场景: 无命中回落倾听（R4，最安全默认）
    假定 dialogue.commander=true
    当 输入不满足 R1/R2/R3
    那么 路由到 LISTENING 并记 rule=R4
    当 路由判定自身异常
    那么 同样回落 LISTENING 并记 ROUTE_FALLBACK

  # ── 三道闸（AA4，全部显式降级 + trace） ───────

  场景: 深度上限=1
    假定 dialogue.commander=true
    当 一轮对话执行完成
    那么 本轮恰好 1 次路由记账 + 1 次能力执行（结构上不可递归）

  场景: 重复意图锁定（≥3 次/600s）
    假定 dialogue.commander=true，repeat_intent_times=3、repeat_window_seconds=600
    当 alice 在同一会话 10 分钟内发送同一规范化文本达 3 次
    那么 第 3 次返回确定性温和提示（「先把眼下这件事做完」语义）
    并且 该轮不再调用 LLM，ai_trace 记 REPEAT_INTENT_LOCK
    当 Redis 不可用
    那么 连续失败 3 次后冷却 30 秒降级内存计数，不直接放行

  场景: A→B→A 振荡锁定
    假定 dialogue.commander=true，oscillation_lock=true
    当 同一会话路由历史 3 条为 A→B→A 且本轮计算为 B
    那么 本轮强制留在 A，并记 trace（fallback_reason 含 oscillation 标记）

  场景: 会话预算与单轮墙钟
    假定 dialogue.commander=true，session_turn_budget=30、turn_wall_clock_ms=30000
    当 会话轮次达到 30
    那么 返回明确但不吓人的确定性收尾文案，ai_trace 记 BUDGET_EXHAUSTED
    当 单轮墙钟超过 30s
    那么 回包不被阻断，ai_trace 记 TURN_TIMEOUT

  # ── 协议（AA5） ─────────────────────────────

  场景: 开关关闭时 /api/dialogue/chat 不注册
    假定 zhiyu.dialogue.commander=false（默认）
    当 已认证用户访问 POST /api/dialogue/chat
    那么 按未注册路径返回既有 1004 语义（匿名 1002），不存在总是报错的假端点

  场景: 开关开启时 /api/emotion/chat 委托 commander 且协议零破坏
    假定 zhiyu.dialogue.commander=true
    当 alice 以 mode=live 调用 /api/emotion/chat
    那么 事件序列仍为 chunk/revoke/done，done 载荷恒为 sessionId/crisis/revoked（不新增字段）
    并且 与直调门面的最终安全文本一致（Mock 模式逐字相等，评测 cmd-protocol-parity 断言）
    并且 ai_trace 有 scene=dialogue-route 记账（委托路径恒路由 LISTENING）

  场景: 开关关闭时 /api/emotion/chat 调用链与今天完全一致
    假定 zhiyu.dialogue.commander=false
    当 alice 调用 /api/emotion/chat
    那么 走原实现一字不动（不经过 commander，无任何 dialogue-route trace）

  场景: /api/dialogue/chat 开启时 done 增加 capability 字段
    假定 zhiyu.dialogue.commander=true
    当 alice 调用 /api/dialogue/chat 完成一轮
    那么 done 载荷为 sessionId/capability/crisis/revoked/toolUsed
    （capability ∈ LISTENING / PRACTICE_COMPANION / KNOWLEDGE / CRISIS）

  # ── 评测激活（AA6） ─────────────────────────

  场景: Z2 登记的 7 条 P2 用例转绿
    假定 companion-eval.json 中 cmd-* 用例已去除 SKIPPED 状态并绑定 check 探针
    当 以 Mock 模式运行离线评测
    那么 7 条 COMMANDER_GATE 用例全部 PASS（不再是 SKIPPED，不计跳过不计失败）
