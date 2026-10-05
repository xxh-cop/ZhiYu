Feature: 管理端作品管理（P1-B1）
  作为管理员或咨询师
  我希望查看和管理学生作品
  以便维护作品展示秩序

  Background:
    Given 后端已启动
    And 管理员已登录获得 token

  # 接口 5
  Scenario: 管理员分页查询作品列表
    When 发送 GET /api/admin/works?page=1
    Then 响应码为 200
    And 响应体 code 为 0
    And 响应体 data.records 是数组

  Scenario: 学生访问作品列表被拒绝
    Given 学生已登录获得 token
    When 发送 GET /api/admin/works
    Then 响应码为 403

  # 接口 6
  Scenario: 管理员查询作品详情
    Given 存在作品 ID=1
    When 发送 GET /api/admin/works/1
    Then 响应码为 200
    And 响应体 data.title 不为空

  Scenario: 查询不存在的作品
    When 发送 GET /api/admin/works/99999
    Then 响应码为 404
    And 响应体 code 为 1004

  # 接口 7
  Scenario: 管理员下架作品
    Given 存在作品 ID=1
    When 发送 PUT /api/admin/works/1/status {"status":2}
    Then 响应码为 200
    And 响应体 code 为 0

  Scenario: 咨询师下架作品被拒绝
    Given 咨询师已登录获得 token
    When 发送 PUT /api/admin/works/1/status {"status":2}
    Then 响应码为 403

  # 接口 8
  Scenario: 管理员删除作品
    Given 存在作品 ID=1
    When 发送 DELETE /api/admin/works/1
    Then 响应码为 200
    And 响应体 code 为 0

  Scenario: 删除不存在的作品
    When 发送 DELETE /api/admin/works/99999
    Then 响应码为 404
    And 响应体 code 为 1004
