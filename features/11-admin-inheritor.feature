Feature: 管理端传承人管理（P1-B1）
  作为管理员或咨询师
  我希望查看和管理传承人资料
  以便维护非遗传承人信息库

  Background:
    Given 后端已启动
    And 管理员已登录获得 token

  # 接口 1
  Scenario: 管理员分页查询传承人列表
    When 发送 GET /api/admin/inheritors?page=1
    Then 响应码为 200
    And 响应体 code 为 0
    And 响应体 data.records 是数组

  Scenario: 学生访问传承人列表被拒绝
    Given 学生已登录获得 token
    When 发送 GET /api/admin/inheritors
    Then 响应码为 403

  # 接口 2
  Scenario: 管理员查询传承人详情
    Given 存在传承人资料 ID=1
    When 发送 GET /api/admin/inheritors/1
    Then 响应码为 200
    And 响应体 data.skill 不为空

  Scenario: 查询不存在的传承人
    When 发送 GET /api/admin/inheritors/99999
    Then 响应码为 404
    And 响应体 code 为 1004

  # 接口 3
  Scenario: 管理员认证传承人
    Given 存在传承人资料 ID=1
    When 发送 PUT /api/admin/inheritors/1/verify {"verified":1}
    Then 响应码为 200
    And 响应体 code 为 0

  Scenario: 咨询师认证传承人被拒绝
    Given 咨询师已登录获得 token
    When 发送 PUT /api/admin/inheritors/1/verify {"verified":1}
    Then 响应码为 403

  # 接口 4
  Scenario: 管理员编辑传承人资料
    When 发送 PUT /api/admin/inheritors/1 {"skill":"陶艺"}
    Then 响应码为 200
    And 响应体 data.skill 为 "陶艺"

  Scenario: 编辑不存在的传承人
    When 发送 PUT /api/admin/inheritors/99999 {"skill":"陶艺"}
    Then 响应码为 404
    And 响应体 code 为 1004
