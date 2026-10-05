Feature: 管理端小组管理（P1-B1）
  作为管理员或咨询师
  我希望查看和管理互助小组
  以便维护小组秩序

  Background:
    Given 后端已启动
    And 管理员已登录获得 token

  # 接口 9
  Scenario: 管理员分页查询小组列表
    When 发送 GET /api/admin/groups?page=1
    Then 响应码为 200
    And 响应体 code 为 0
    And 响应体 data.records 是数组

  Scenario: 学生访问小组列表被拒绝
    Given 学生已登录获得 token
    When 发送 GET /api/admin/groups
    Then 响应码为 403

  # 接口 10
  Scenario: 管理员查询小组详情
    Given 存在小组 ID=1
    When 发送 GET /api/admin/groups/1
    Then 响应码为 200
    And 响应体 data.name 不为空

  Scenario: 查询不存在的小组
    When 发送 GET /api/admin/groups/99999
    Then 响应码为 404
    And 响应体 code 为 1004

  # 接口 11
  Scenario: 管理员关闭小组
    Given 存在小组 ID=1
    When 发送 PUT /api/admin/groups/1/status {"status":0}
    Then 响应码为 200
    And 响应体 code 为 0

  Scenario: 咨询师关闭小组被拒绝
    Given 咨询师已登录获得 token
    When 发送 PUT /api/admin/groups/1/status {"status":0}
    Then 响应码为 403

  # 接口 12
  Scenario: 管理员删除小组
    Given 存在小组 ID=1
    When 发送 DELETE /api/admin/groups/1
    Then 响应码为 200
    And 响应体 code 为 0

  Scenario: 删除不存在的小组
    When 发送 DELETE /api/admin/groups/99999
    Then 响应码为 404
    And 响应体 code 为 1004
