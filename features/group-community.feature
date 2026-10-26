Feature: 小组成员与组内交流
@FR-40-04 @FR-70-02
  Scenario: 成员发送消息
    Given 用户已加入开放小组并同意隐私政策
    When 用户发送安全的交流文字
    Then 消息加密保存且组内成员可以读取
    And 非成员无法读取
@FR-40-04
  Scenario: 退出后重新加入
    Given 普通成员已加入小组
    When 成员退出后重新加入
    Then 恢复原成员记录且人数正确
    And 退出期间无法读取组内消息
@FR-40-04
  Scenario: 组长转让
    Given 小组还有其他成员
    When 组长未转让就尝试退出
    Then 提示先转让且保留成员状态
    When 组长转让给另一名有效成员后退出
    Then 新组长获得管理权且成员人数减少一
@FR-40-04 @FR-01-04
  Scenario: 删除权限
    Given 普通成员不是消息作者
    When 成员尝试删除他人的消息
    Then 返回权限错误且原消息不变
