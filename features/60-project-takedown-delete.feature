# AY 批（2026-11-05）：已发布项目的下架与删除能力 + AX 残留清理
Feature: AY 项目下架与删除

  # ── AY1：下架 ──

  Scenario: 管理员下架已上架项目
    Given 一个 status=1（上架）的手作项目
    When 管理员 PUT /api/admin/projects/{id}/takedown 携带理由
    Then 项目 status 变为 2（下架，DDL 既有口径）
    And craft_project.audit_reason 落该理由
    And admin_operation_log 落 project.takedown
    And 传承人收到 WORK_AUDIT 通知（refType=project，正文含理由）

  Scenario: 下架的非法状态流转与参数校验
    Given 一个 status=0（待审）或 status=2（已下架）的项目
    When 管理员请求下架
    Then 返回 3001（HTTP 200 业务码）
    When 理由为空或超过 300 字
    Then 返回 1001（HTTP 400）
    When 项目不存在
    Then 返回 1004
    When 学生或咨询师请求下架/删除
    Then 返回 1003（HTTP 403）

  Scenario: 下架后全端不可访问且不可复活
    Given 一个已被下架的项目
    When 学生拉取步骤列表 / 项目详情 / 创建创作会话
    Then 均返回 1004（不泄露存在性）
    And 步骤音频等私有媒体签名播放即刻 403（授权含项目上架态）
    When 传承人查看该项目详情
    Then 返回 1004（列表仍可见状态标记，理由经通知送达）
    When 管理员对该项目再执行审核通过
    Then 返回 3001（已下架不能复活；重新上线只能新建项目送审）

  # ── AY2：删除 ──

  Scenario: 管理员逻辑删除项目并退役媒体
    Given 一个含步骤音频与项目视频（私有媒体引用）的项目
    When 管理员 DELETE /api/admin/projects/{id}?reason=...
    Then 项目 deleted=1、audit_reason 落理由
    And 步骤与材料级联逻辑删除
    And 不再被引用的音频/视频媒体 media_asset 逻辑注销 + 登记 PRIVATE 清理任务
    And admin_operation_log 落 project.delete，传承人收到删除通知

  Scenario: 仍被其他对象引用的媒体不误退役
    Given 同一媒体引用同时被一个作品引用
    When 删除引用它的项目
    Then 该媒体不退役（引用计数 > 0）

  # ── AY3：AX 残留清理 ──

  Scenario: 用新能力清理 AX 残留
    Given AX 批残留的两个 status=1 项目与一条音频媒体
    When 对其执行下架 + 删除
    Then craft_project / craft_step 无 deleted=0 残留，媒体逻辑注销且物理文件清除
    And SQL 复核为零
