# BC 批（2026-11-05）：项目审核理由下发 + 动作来源区分 + 删除级联真实验证
Feature: BC 项目问责详情与级联验证

  # ── BC1：理由与动作来源 ──

  Scenario: 管理员查看项目问责详情
    Given 一个被评审驳回的项目（reject 带理由）
    When GET /api/admin/projects/{id}
    Then data.auditReason 为驳回理由，lastAction=REJECT，statusOrigin=REJECT，lastActorId 为操作管理员
    Given 一个先通过后下架的项目
    When GET /api/admin/projects/{id}
    Then lastAction=TAKEDOWN，statusOrigin=TAKEDOWN，auditReason 为下架理由
    Given 一个 2026-11-05 前的历史 status=2 项目（无动作日志）
    Then statusOrigin=REJECT（下架能力此前不存在，推导无猜测）

  Scenario: 理由不下发给学生
    When 学生或咨询师 GET /api/admin/projects/{id}
    Then 返回 403/1003；学生端项目接口维持 1004 不泄露存在性

  # ── BC2：级联真实验证 ──

  Scenario: 完整级联——三步骤+材料+封面+视频
    Given 一个含 3 步骤（一步带音频）、1 材料、封面与视频（均为私有媒体）的已发布项目
    When 管理员删除该项目
    Then craft_project/craft_step/craft_material 全部逻辑删除
    And 音频/视频/封面 media_asset 逻辑注销，file_cleanup_job 登记 3 条 PRIVATE 任务
    And FileCleanupWorker 60s 调度内物理删除全部三个文件

  Scenario: 共享封面不误删
    Given 同一封面媒体被项目 A、B 共用
    When 删除项目 A
    Then 封面媒体不退役（B 仍引用）；删除 B 后封面退役
