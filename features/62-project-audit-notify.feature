# BE 批（2026-11-05）：项目审核结果触达传承人
Feature: BE 项目审核结果通知

  Scenario: 审核通过与驳回都通知传承人
    Given 传承人提交了项目（status=0）
    When 管理员审核通过
    Then 传承人收到 WORK_AUDIT 通知（refType=project），标题「你的项目已通过审核」，正文不含「原因」
    Given 另一个待审项目
    When 管理员驳回并填写理由
    Then 传承人收到通知，标题「你的项目未通过审核」，正文含项目名与「原因：xxx」
    And 理由超过 120 字时通知内截断到 120 字，库中 audit_reason 保留完整理由

  Scenario: 通知失败不阻断审核
    Given 通知存储不可用
    When 管理员审核通过
    Then 审核仍成功（status=1 落库），仅记 warn 日志

  Scenario: 同一项目同一结果不重复推送
    Given 项目已收到「通过」通知
    When 同结果通知再次被触发（dedupe 键相同）
    Then 不产生第二条通知（先查后插 + 唯一索引兜底）
    And 不同结果（如未来重审驳回）是不同 dedupe 键，正常推送
