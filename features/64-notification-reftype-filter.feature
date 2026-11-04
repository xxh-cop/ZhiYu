# BJ 批（2026-11-05）：通知列表 refType 筛选
Feature: BJ 通知列表按 refType 细分

  Scenario: type + refType 组合筛选，total 与筛选一致
    Given 我有作品审核（WORK_AUDIT/work）、项目下架与项目审核（WORK_AUDIT/project）、评论审核（COMMENT_AUDIT/comment）各若干条
    When GET /api/notifications?type=WORK_AUDIT&refType=project
    Then 只返回项目类审核通知，total=项目类条数（不是 WORK_AUDIT 总数）

  Scenario: 不传 refType 行为与旧版一致
    When GET /api/notifications?type=WORK_AUDIT（不带 refType）
    Then 返回该类型全部通知，total 为类型总数（向后兼容）

  Scenario: 非法 refType 不静默忽略
    When GET /api/notifications?refType=bogus
    Then 返回 400 + code=1001

  Scenario: refType 筛选不影响未读数且隔离他人通知
    Given 他人有 project 类通知
    When 我按 refType=project 查列表
    Then 看不到他人通知；我的未读数不受筛选参数影响
