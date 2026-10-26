Feature: 校园身份、关怀跟进与会话隐私
  Scenario: 管理员核验校园身份
    Given 学生已同意隐私政策并提交姓名学号
    When 管理员通过学校认可渠道核实并批准申请
    Then 申请显示已通过且学生角色不变
    And 操作记录包含审核人和时间但不包含姓名学号
  Scenario: 拒绝重复审核
    Given 申请已被审核
    When 另一管理员再次提交审核
    Then 返回状态冲突且不覆盖原审核
  Scenario: 关怀任务闭环
    Given 咨询师领取了脱敏关怀任务
    When 咨询师记录评估并完成任务
    Then 保存跟进事件且任务变为已完成
  Scenario: 撤回联系授权
    Given 学生撤回了联系授权
    When 咨询师记录已联系
    Then 拒绝操作且不写入联系事件
  Scenario: 隔离其他用户会话
    Given 两个不同的登录用户
    When 一人请求另一人的会话或使用其会话编号聊天
    Then 返回不存在且不调用 AI
  Scenario: AI 输出先检查后展示
    Given AI 回复包含跨 token 拼接的禁止内容
    When 回复完成并进行安全检查
    Then 只展示与保存安全替换文本
  Scenario: 撤回隐私同意
    Given 学生撤回隐私同意
    When 学生发起聊天
    Then 在开始异步处理之前拒绝请求
