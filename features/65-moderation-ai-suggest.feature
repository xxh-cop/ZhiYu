# BL 批（2026-11-05）：AI 审核辅助建议（按需调用，人工裁决）
Feature: BL AI 审核辅助建议

  Scenario: 审核员按需获取作品建议
    Given 一个待审作品（标题 + 寄语 + 含视频）
    When 审核员 POST /api/admin/works/{id}/ai-suggest
    Then 返回 available=true + tendency（NO_OBVIOUS_ISSUE/HAS_CONCERNS/UNCERTAIN）+ riskPoints（每条含 location/note）+ uncertainty + disclaimer
    And riskPoints 不含「通过/驳回」等裁决词；含媒体时 media 位置提示人工核对
    And ai_audit_log 落 scene=moderation 且 refType=work、refId=作品 id（raw 加密）
    And 作品审核状态不被修改

  Scenario: AI 不可用时的明确降级
    Given 模型未配置或熔断中或输出未过安全过滤或返回格式不可用
    When 审核员请求建议
    Then HTTP 200 + code=0 + available=false + note 说明原因 + disclaimer 恒在
    And 不抛出阻断人工审核的错误，也绝不显示 Mock/改写文本冒充建议

  Scenario: 模型输出非法 JSON 时受控重试一次（2026-11-07，p37 转义故障）
    Given 模型首次输出未通过 JSON 语法解析（如 note 内未转义的英文双引号）
    When 审核员请求建议
    Then 客户端层以同 prompt + 重试告知片段重试恰好 1 次（同模型、同 JSON mode）
    And 重试输出合法 → 正常返回 available=true；两次 tokens 求和落 ai_trace，最终输出审计 1 次
    And 重试输出仍非法 → 原文原样交回业务层，按「格式不可用」降级（available=false + 人工兜底）
    And 任何情况下都不用正则/启发式修补模型输出，也不把解析失败吞掉当成功

  Scenario: 越权与频控
    When 学生或咨询师请求 ai-suggest
    Then 403/1003
    When 同一审核员一小时内第 31 次请求
    Then 400/1001（明确提示频控，不伪装成暂无建议）
