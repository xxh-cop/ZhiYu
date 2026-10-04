#language: zh-CN
功能: 管理端内容审核与看板（M2 批 4 实现验证）
  作为管理员/心理咨询师
  我希望审核手作内容、查看脱敏后的关怀提醒与运营数据
  以保障平台安全与内容质量

  背景:
    假定 管理员已登录管理端

  场景: 管理员查看待审核项目队列
    当 管理员请求 GET /api/admin/audit-queue
    那么 返回 code=0，data 为待审项目列表
    而且 列表中只有 status=0 的项目

  场景: 管理员通过待审项目
    当 管理员对 status=0 的项目发起 PUT /api/admin/projects/{id}/audit，action=approve
    那么 返回 code=0，项目 status 变为 1（上架）

  场景: 管理员驳回待审项目
    当 管理员对 status=0 的项目发起 PUT /api/admin/projects/{id}/audit，action=reject，reason="内容不完整"
    那么 返回 code=0，项目 status 变为 2（下架）

  场景: 驳回项目不填理由 → 1001
    当 管理员对 status=0 的项目发起 PUT /api/admin/projects/{id}/audit，action=reject，reason 为空
    那么 返回 code=1001，提示"驳回必须填写理由"

  场景: 审核不存在的项目 → 1004
    当 管理员对不存在的 projectId 发起审核
    那么 返回 code=1004

  场景: 审核已上架项目 → 3001
    当 管理员对 status=1 的项目发起审核
    那么 返回 code=3001，提示"非法状态流转"

  场景: 咨询师不能审核项目（仅管理员）
    当 咨询师（role=2）发起 PUT /api/admin/projects/{id}/audit
    那么 返回 code=1003

  场景: 咨询师查看脱敏关怀提醒
    当 咨询师请求 GET /api/admin/care-reminders
    那么 返回 code=0，data 为关怀提醒列表
    而且 summary 字段为脱敏文本，不含完整对话原文

  场景: 学生不能访问管理端
    当 学生账号访问 /api/admin/**
    那么 系统应返回 code=1003

  场景: 管理员查看数据看板
    当 管理员请求 GET /api/admin/dashboard/stats
    那么 返回 code=0，data 包含 totalUsers / totalWorks / pendingProjects / pendingCareReminders
    而且 moodDistribution 为数组
    而且 userTrend7d 为包含 7 天数据的数组
