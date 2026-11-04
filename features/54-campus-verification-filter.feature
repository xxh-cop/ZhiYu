#language: zh-CN
功能: 校园身份核验列表的状态过滤（AG1，2026-11-04）
  作为管理员
  我希望校园身份核验列表支持按状态过滤
  以便审核工作台可以直接接入统一的待审队列，而不是前端跨页拼凑

  背景:
    假定 管理员 admin 已登录（role=3），库中存在 PENDING / APPROVED / REJECTED 三种状态的核验申请

  场景: 按 status=PENDING 过滤只返回待审记录
    当 admin 调 GET /api/admin/campus-verifications?status=PENDING
    那么 返回 200，records 全部为 PENDING，total 只计待审条数

  场景: status 大小写不敏感
    当 admin 调 GET /api/admin/campus-verifications?status=approved
    那么 返回 200，records 全部为 APPROVED

  场景: 不传 status 与旧行为完全一致（向后兼容）
    当 admin 调 GET /api/admin/campus-verifications（不带 status 或 status 为空白）
    那么 返回全状态混排，排序仍为 update_time DESC,id DESC，每页 20 条，total 为全量计数（JSON number）

  场景: 非法 status 值 → 400 + code=1001
    当 admin 调 GET /api/admin/campus-verifications?status=BOGUS
    那么 返回 400 且 code=1001（与页码越界同一显式校验风格，不静默忽略）

  场景: 非管理员不可访问（既有约束不变）
    当 学生或传承人调该接口
    那么 返回 403 + code=1003
