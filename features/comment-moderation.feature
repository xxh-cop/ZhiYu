Feature: 留暖评论审核
  Scenario: 评论通过审核才向其他人公开
    Given 用户提交了一条通过内容安全过滤的评论
    Then 评论处于待审核状态且仅作者可见
    When 管理员通过评论
    Then 评论公开且留下审核操作记录

  Scenario: 权限与过期审核保护
    Given 评论已被作者撤回
    When 管理员使用旧页面审核该评论
    Then 审核失败且评论不会恢复
    And 普通用户不能审核或撤回他人评论

  Scenario: 私密作品的互动不泄露信息
    Given 一份仅自己可见的作品
    When 其他用户请求评论列表或点赞
    Then 返回资源不存在
