Feature: 小织记得的（跨会话陪伴档案）
  作为使用小织的学生
  我希望看到并管理小织记住的内容
  以便陪伴延续，同时隐私由我自己决定

  Background:
    Given 学生已登录并同意隐私政策
    And 小织记忆默认关闭

  Scenario: 开启记忆后保存偏好与回避话题
    When 学生勾选 memory-v2 授权并开启小织记忆
    And 学生保存练习偏好 preferred_minutes=10、preferred_material=bamboo
    And 学生设置不想聊的话题为 "考试,家庭"
    Then 偏好与话题加密保存，有效期 180 天
    And 「你告诉我的」显示 10 分钟、竹、考试、家庭

  Scenario: 派生档案只有计数与时段
    Given 学生近 30 天完成 3 件手作并响应 2 次呼吸引导
    And 学生近 14 天有 3 天便签标记为有点累
    When 学生打开「小织记得的」
    Then 「我注意到的」显示有效的安定方式、完成件数与低落天数
    And 每条都标注来源，且不包含便签原文或对话内容

  Scenario: 对话最多注入 3 条记忆且不注入原句
    Given 学生已开启记忆并设置回避话题 "考试"
    When 学生发起一轮情绪对话
    Then 注入的档案不超过 3 条
    And 包含 "不要主动提起考试" 的约束
    And 不包含任何便签原文与低落天数

  Scenario: 未勾选授权时开启失败
    When 学生未勾选授权说明就开启小织记忆
    Then 接口返回 code 1001
    And 不写入任何记忆行

  Scenario: 未开启记忆时保存偏好被拒绝
    When 学生直接保存练习偏好
    Then 接口返回 code 3001

  Scenario: 回避话题超过上限被拒绝
    Given 学生已开启记忆
    When 学生设置不想聊的话题为 "考试,家庭,体重,实习"
    Then 接口返回 code 1001

  Scenario: 派生档案不能按 key 删除
    Given 学生已开启记忆
    When 学生请求删除 key 为 calm_strategy 的记忆项
    Then 接口返回 code 1001

  Scenario: 撤回后对话不再使用记忆
    Given 学生已保存偏好与话题
    When 学生删除全部记忆
    Then 所有记忆行被软删且记忆关闭
    And 后续对话不注入任何陪伴档案
