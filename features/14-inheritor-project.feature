Feature: 传承人项目管理（M2 批 5A）
  作为传承人（role=1）
  我需要创建、编辑、删除手作项目及其步骤和材料
  以便向平台贡献非遗手作内容

  Background:
    Given 平台已启动
    And 传承人 "inheritor01" 已登录

  # ── 项目 CRUD ──

  Scenario: 传承人创建新项目
    When 传承人发起创建项目请求，title="扎染围巾" level=1 category="扎染"
    Then 返回 code=0
    And 返回 data.id 不为空
    And 数据库 craft_project 表存在 inheritor_id=传承人ID 且 status=0 的记录

  Scenario: 学生访问传承人接口被拒
    Given 学生 "student01" 已登录
    When 学生发起创建项目请求
    Then 返回 HTTP 403
    And 返回 code=1003

  Scenario: 传承人创建项目难度越界被拒
    When 传承人发起创建项目请求，title="越界难度" level=4 category="扎染"
    Then 返回 HTTP 400
    And 返回 code=1001

  Scenario: 传承人查看自己的项目列表
    When 传承人查询项目列表
    Then 返回 code=0
    And 返回 data.records 是数组

  Scenario: 传承人查看项目详情含步骤和材料
    Given 传承人已创建项目 "测试项目"
    And 该项目有 1 个步骤和 1 个材料
    When 传承人查询项目详情
    Then 返回 code=0
    And 返回 data.steps 长度为 1
    And 返回 data.materials 长度为 1

  Scenario: 传承人查看别人的项目被拒
    Given 传承人 "inheritor02" 已登录
    When 传承人查询不属于自己的项目
    Then 返回 HTTP 403
    And 返回 code=1003

  Scenario: 传承人编辑待审项目
    When 传承人编辑项目，title="新标题"
    Then 返回 code=0

  Scenario: 传承人编辑已上架项目被拒
    Given 传承人有一个已上架项目
    When 传承人编辑该项目
    Then 返回 code=3001

  Scenario: 传承人删除待审项目
    When 传承人删除项目
    Then 返回 code=0
    And 数据库该项目记录 deleted=1

  # ── 步骤 CRUD ──

  Scenario: 传承人添加步骤
    When 传承人添加步骤 stepNo=2 title="第二步"
    Then 返回 code=0
    And 返回 data.id 不为空

  Scenario: 传承人给别人项目添加步骤被拒
    Given 传承人 "inheritor02" 已登录
    When 传承人给别人的项目添加步骤
    Then 返回 HTTP 403
    And 返回 code=1003

  # ── 材料 CRUD ──

  Scenario: 传承人添加材料
    When 传承人添加材料 name="绣线" qty="10根"
    Then 返回 code=0
    And 返回 data.id 不为空

  Scenario: 传承人删除材料
    Given 项目已有材料 "绣布"
    When 传承人删除该材料
    Then 返回 code=0
