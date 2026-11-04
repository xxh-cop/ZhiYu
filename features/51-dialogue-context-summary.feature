#language: zh-CN
功能: 对话上下文滚动摘要（P1b，rolling-summary 默认关闭）
  作为平台架构组
  我希望长会话在保留最近原文窗口的同时，把滑出窗口的早期对话压缩为服务端托管的滚动摘要
  以便倾听链在长会话中保持上下文连续，且功能可按开关灰度、失败可降级

  背景:
    假定 系统已启动，zhiyu.dialogue.rolling-summary 默认 false

  # ── 开关默认关闭（硬要求：行为与旧版一致） ────

  场景: 开关关闭时长会话零行为变化
    假定 学生 alice 已登录且同意隐私政策
    当 alice 在同一倾听会话连续对话 16 轮
    那么 每轮请求只携带最近 max_history_turns 条原文（与 P1b 之前完全一致）
    并且 dialogue_context_summary 表不产生任何记录
    并且 AiClient.contextSummary 从未被调用

  # ── 触发边界（开关开启，仅倾听链） ────────────

  场景: 第 10/11 轮不触发，第 15 轮首次压缩（happy path）
    假定 rolling-summary 已开启，alice 的会话已有 10 条消息
    当 第 11 轮消息落库（滑出 1 条，不足增量 5）
    那么 不生成摘要
    当 消息累计到 15 条（滑出 5 条）
    那么 生成首份摘要，覆盖第 1-5 轮，summary_cipher 以密文落库
    并且 ai_trace 记录 scene=dialogue-summary、result_status=OK

  场景: 第 16 轮不滚动，第 20 轮增量二滚
    假定 摘要已覆盖第 1-5 轮
    当 第 16 条消息落库（新滑出 1 条，不足 5）
    那么 摘要保持覆盖 1-5 不变
    当 消息累计到 20 条（再滑出 5 条）
    那么 摘要增量更新为覆盖第 1-10 轮（同一会话仍只有一行）

  场景: 摘要注入下一轮对话上下文
    假定 alice 的会话已有覆盖第 1-5 轮的有效摘要
    当 alice 发送第 16 条消息
    那么 发给模型的上下文为「system 摘要前缀（含第1-5轮标注与不代表用户原话声明）+ 最近 10 条原文」
    并且 摘要由服务端解密注入，客户端无需也无法拼装上下文

  场景: 摘要 TTL 到期按无摘要处理
    假定 摘要行的 update_time 早于 90 天前
    当 alice 继续对话
    那么 上下文不携带该摘要（视为不存在），原文窗口照常工作

  # ── 降级（失败不阻断对话） ────────────────────

  场景: 摘要生成失败时保留原文窗口并记降级
    假定 rolling-summary 已开启且到达第 15 条消息
    当 AiClient.contextSummary 返回 null（熔断/异常/输出未过终检）
    那么 本轮不写摘要行，对话正常完成
    并且 ai_trace 记录 result_status=FALLBACK、fallback_reason=SUMMARY_FAILED

  # ── 注销覆盖 ─────────────────────────────────

  场景: 注销后滚动摘要密文真正清空（异常路径/隐私红线）
    假定 alice 的会话存在滚动摘要
    当 alice 注销账号
    那么 dialogue_context_summary 中她的记录 deleted=1 且 summary_cipher 被清空为 ''
    并且 其他用户的摘要行内容不变
