Feature: 传承人公开查询接口（M2 批 6A）
  作为学生（role=0）
  我需要浏览已认证传承人列表和传承人主页
  以便了解非遗传承人及其手作项目

  Background:
    Given 平台已启动
    And 学生 "student01" 已登录

  # ── 接口 1：GET /api/inheritors ──

  Scenario: 学生查询已认证传承人列表
    When 学生查询传承人列表
    Then 返回 code=0
    And 返回 data.records 是数组
    And 返回的传承人都是 verified=1

  Scenario: 未登录用户查询传承人列表被拒
    When 未登录用户查询传承人列表
    Then 返回 HTTP 401
    And 返回 code=1002

  Scenario: 学生按关键词搜索传承人
    When 学生查询传承人列表，keyword="赣绣"
    Then 返回 code=0
    And 返回 data.records 中 skill 包含 "赣绣"

  # ── 接口 2：GET /api/inheritors/{id} ──

  Scenario: 学生查询传承人详情
    Given 已认证传承人 "赣绣大师" 已存在
    When 学生查询该传承人详情
    Then 返回 code=0
    And 返回 data.skill 为 "赣绣"
    And 返回 data.school 不为空

  Scenario: 学生查询不存在的传承人
    When 学生查询传承人详情，id=99999
    Then 返回 HTTP 404
    And 返回 code=1004

  Scenario: 学生查询未认证传承人被拒
    Given 未认证传承人 "陶艺新人" 已存在
    When 学生查询该传承人详情
    Then 返回 HTTP 404
    And 返回 code=1004

  # ── 接口 3：GET /api/inheritors/{id}/projects ──

  Scenario: 学生查询传承人的已上架项目列表
    Given 已认证传承人 "陶艺大师" 有 1 个已上架项目
    When 学生查询该传承人的项目列表
    Then 返回 code=0
    And 返回 data.records 是数组
    And 返回的项目都是 status=1

  Scenario: 学生查询未认证传承人的项目被拒
    Given 未认证传承人 "剪纸新人" 已存在
    When 学生查询该传承人的项目列表
    Then 返回 HTTP 404
    And 返回 code=1004

  # ── 接口 4：GET /api/craft/projects/{id} ──

  Scenario: 学生查询已上架项目详情
    Given 已上架项目 "入门陶艺杯" 已存在
    And 该项目有 1 个步骤和 1 个材料
    When 学生查询该项目详情
    Then 返回 code=0
    And 返回 data.title 为 "入门陶艺杯"
    And 返回 data.inheritorNickname 不为空
    And 返回 data.steps 长度为 1
    And 返回 data.materials 长度为 1

  Scenario: 学生查询未上架项目被拒
    Given 待审项目 "待审作品" 已存在
    When 学生查询该项目详情
    Then 返回 HTTP 404
    And 返回 code=1004

  Scenario: 学生查询不存在的项目
    When 学生查询项目详情，id=99999
    Then 返回 HTTP 404
    And 返回 code=1004
