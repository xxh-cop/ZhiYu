# 按能力分档的 provider 兜底（2026-11-08 装配层批，D1/D2/D4/D8）
# 覆盖：云不可用→本地兜底（可兜底能力）、审核建议 cloud-only、影子判定双开关。
Feature: 能力级 provider 兜底（云为主、本地兜底）

  Background:
    Given 云端模型端点不可达（连接拒绝或持续超时）
    And 本地 Ollama 兜底端点可用（zhiyu.ai.providers.local.enabled=true）

  Scenario: 推荐理由在云不可用时切到本地且不静默
    When 用户打开手作推荐
    Then 系统先尝试云端并失败（记入该能力熔断计数）
    And 系统按策略调用本地兜底生成推荐理由
    And ai_trace 依次落两行：cloud 段 FALLBACK/provider=deepseek、local 段 OK/provider=ollama
    And 推荐理由正常展示，不降级为固定文案

  Scenario: 情绪对话（SSE）在云不可用时切到本地
    When 学生发送一条情绪倾诉
    Then 系统在零外发前提下改用本地流式生成
    And 句级安全过滤与输出终检照常执行
    And ai_trace 的 llm-chat 行如实标注 provider=ollama

  Scenario: 云端返回 400 请求错误时不兜底
    When 云端对推荐请求返回 400
    Then 系统判定为请求自身错误，不调用本地
    And 返回既有降级文案

  Scenario: 审核建议在云不可用时绝不本地顶替（D4）
    When 审核员对一条待审作品请求 AI 建议
    Then 云端 strong 与 standard 两档均失败
    And 系统返回「AI 暂时无法给出建议，请按人工流程审核」
    And ai_trace 中 llm-moderation 没有任何 provider=ollama 的行

  Scenario: 倾听小结等 cloud-only 能力在云缺席时不走本地（D1）
    Given 云端 key 未配置
    When 用户结束倾听并请求小结
    Then 系统按「未配置真实模型」降级（固定模板/不可用）
    And 本地兜底实现从未被调用

  Scenario: 影子判定双开关只开一个不走本地（D8）
    Given local-demo-mode=false 且 shadow.demo-providers 含 ollama
    When 影子判定任务运行且云端不可用
    Then 判定按云端不可用口径记 PENDING/UNAVAILABLE
    And 本地兜底实现从未被调用
    And moderation_soft_shadow 的 provider 保持 unknown，不冒充判成
