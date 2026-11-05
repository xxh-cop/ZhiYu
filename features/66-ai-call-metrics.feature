#language: zh-CN
功能: AI 调用用量与质量指标采集（BO，2026-11-05）
  作为平台运营方
  我希望每次 AI 调用都留下脱敏的用量与质量元数据（资源 key、延迟、降级/熔断/失败、模型、tokens）
  以便后续换模型、调阈值、算成本、做质量看板有真实数据底座

  背景:
    假定 学生 grace 已登录且同意隐私政策
    并且 指标落库载体为既有 ai_trace 表（只追加、脱敏元数据），不新建表、不改 ai_audit_log

  场景: 成功调用记录用量指标（happy path）
    当 grace 发起一次 AI 手作推荐理由生成且模型正常返回
    那么 ai_trace 旁路异步落一条记录：scene=llm-recommend、result_status=OK、
      latency_ms 为实际耗时、model 为当前模型标识
    并且 tokens 在客户端可获取时如实记录，获取不到时留 NULL（不估算）
    并且 记录不含任何对话原文、prompt 或工具参数

  场景: 降级与失败按业务结局分类记录
    当 llm-recommend 熔断打开或模型调用异常
    那么 ai_trace 落 result_status=FALLBACK，fallback_reason 分别为 BREAKER_OPEN / PROVIDER_ERROR
    当 情绪对话输入命中高危词被拦截（未发起模型调用）
    那么 ai_trace 落 result_status=BLOCKED，fallback_reason=INPUT_BLOCK
    当 SSE 对话链路模型调用异常（无降级文案、错误抛给调用方）
    那么 ai_trace 落 result_status=ERROR，fallback_reason=PROVIDER_ERROR

  场景: 指标采集不拖慢、不阻断主流程（降级路径）
    当 指标写库失败或变慢
    那么 AI 业务响应照常即时返回（采集旁路单线程异步、失败仅记 warn 日志）
    并且 主流程不新增同步 LLM 调用、不重复写 ai_audit_log

  场景: 管理员按时间区间查询聚合指标（happy path）
    当 管理员（role=3）请求 GET /api/admin/ai-metrics 携带 from/to（区间 ≤ 90 天）
    那么 返回按资源 key 分组的调用量、成功率、降级率、错误率、avg/P50/P95 延迟、tokens 与模型标识
    并且 聚合只基于 ai_trace 脱敏元数据，不返回用户 ID、会话 ID 或对话原文

  场景: 聚合接口参数与权限校验（异常路径）
    当 from 不早于 to 或区间超过 90 天
    那么 返回 code=1001（参数校验失败）
    并且 学生（role=0）、传承人（role=1）、咨询师（role=2）访问返回 HTTP 403 + code=1003
    并且 未登录访问返回 HTTP 401 + code=1002
