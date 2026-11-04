#language: zh-CN
功能: 统一对话调度层（Dialogue Commander，P0 契约与配置骨架）
  作为平台架构组
  我希望情绪倾听与练习陪伴收敛到统一对话入口，路由可配置、循环有闸、危机绕行
  以便两条对话线不再各自演进，且每一期独立可回退
  （依据 docs/dialogue-unification-20261104.md；P0 仅配置与契约，场景随 P2/P3 期次转绿）

  背景:
    假定 系统已启动，且 ai-config/ai-capabilities.yaml 的 dialogue 段开关均为默认关闭

  # ── 路由规则（P2 落地后转绿） ─────────────────

  场景: 练习页输入路由到练习陪伴子能力
    假定 学生 alice 已登录且同意隐私政策，且她有一个进行中的练习会话
    并且 dialogue.commander 与 dialogue.routing_rules 已开启
    当 alice 从 PRACTICE_PAGE 向 /api/dialogue/chat 发送一条消息
    那么 路由命中规则①，由 PRACTICE_COMPANION 子能力接管（现 CompanionOrchestrator 逻辑原样）
    并且 ai_trace 记录 scene=dialogue-route 与命中规则

  场景: 知识页输入路由到非遗知识子能力
    假定 alice 位于 KNOWLEDGE_PAGE
    当 alice 发送一条非遗知识问题
    那么 路由命中规则②，由 KNOWLEDGE 子能力（现 RAG 链）回答并带 citation 事件

  场景: 显式快捷动作按 intentHint 路由
    当 alice 的请求携带 intentHint=recommend_craft
    那么 路由命中规则③，进入手作推荐子能力

  场景: 未命中任何规则时回落倾听（最安全默认）
    当 alice 的输入不满足规则①②③
    那么 路由到 LISTENING 子能力（无工具、无执行副作用）
    当 规则引擎自身异常
    那么 同样回落 LISTENING 并记 trace(ROUTE_FALLBACK)，用户无感

  # ── 防循环三道闸（P2 落地后转绿） ─────────────

  场景: 深度上限=1，子能力返回值不进二次路由
    假定 调度层已启用（guards.max_route_depth=1）
    当 任一子能力返回结果
    那么 该结果不进入第二次路由（结构上不可达）
    并且 若防御性代码检测到深度>1，抛出 IllegalState 并记 ERROR

  场景: 重复意图锁定
    假定 guards.repeat_intent_times=3、repeat_window_seconds=600
    当 alice 在 10 分钟内发送同一规范化文本（去标点/空白/大小写）达 3 次
    那么 当前子能力被锁定，返回确定性安抚话术并提示「我们先把这件事做完」
    并且 ai_trace 记 fallback_reason=REPEAT_INTENT_LOCK

  场景: A→B→A 振荡强制留 A
    假定 guards.oscillation_lock=true
    当 路由在 3 轮内出现 A→B→A 振荡
    那么 本轮强制留在 A 子能力，并记 trace(REPEAT_INTENT_LOCK)

  场景: 会话预算耗尽确定性收尾
    假定 guards.session_turn_budget=30
    当 会话内 LLM 轮次达到预算
    那么 返回确定性收尾话术并以 done 结束
    并且 ai_trace 记 BUDGET_EXHAUSTED；单轮超过 turn_wall_clock_ms=30000 记 TURN_TIMEOUT

  # ── 危机绕过调度（红线，P2 落地前即应可验） ────

  场景: 输入安检命中危机时零 LLM 调用
    当 alice 的输入命中高危词
    那么 调度层返回预置干预话术（chunk 关怀话术 + done(crisis=true)），全程不产生 LLM 调用
    并且 care_reminder 与 ai_audit_log 照常落库，ai_trace 记 ROUTE_CRISIS

  # ── 旧端点协议兼容（兼容期持续可验） ───────────

  场景: /api/emotion/chat 作为薄适配器协议不变
    假定 P2 已切流（dialogue.commander=true）
    当 alice 以 mode=live 调用 /api/emotion/chat
    那么 事件协议仍为 chunk/revoke/done，done 载荷含 sessionId/crisis/revoked
    并且 与 /api/dialogue/chat 对同一输入的最终安全文本一致（协议平价）

  场景: /api/companion/sessions/{id}/message 作为薄适配器事件序列不变
    假定 P3 已切流（dialogue.companion-adapter=true）
    当 alice 调用该端点
    那么 事件序列仍为 stage、message、tool、citation、artifact、done、error

  # ── P0 本期待验（配置骨架默认关闭） ────────────

  场景: P0 开关默认关闭时行为与既有完全一致
    假定 dialogue.* 三开关均为 false（P0 默认值）
    当 alice 使用 /api/emotion/chat 与 /api/companion/sessions/{id}/message
    那么 行为与 P0 之前完全一致（纯增量，无运行时变化）
    并且 全量 mvn test 保持绿色
