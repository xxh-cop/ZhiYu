#language: zh-CN
功能: 注销级联修复与校园核验审核通知（AH 批，2026-11-04）
  作为平台
  我希望有核验记录的账号能被管理端正常删除、注销级联不再静默漏表、审核结果能触达申请人
  以便账号治理链路闭环、学生及时知道核验结论

  # ── AH1：删除有核验记录的账号不再 500 ──

  场景: 有校园核验记录的账号被管理员删除成功
    假定 学生 dave 存在 campus_verification 记录（含审核理由，review_reason 为 NOT NULL 列）
    当 管理员调 DELETE /api/admin/users/{dave.id}
    那么 返回 200（不再是 500 + code 5000）
    并且 campus_verification 行被逻辑删除，identity_cipher 与 review_reason 清空（空串，不写 NULL）
    （根因：erase 把 NOT NULL 列 review_reason 置 NULL 被 MySQL 拒绝；修复为置空串）

  场景: 无核验记录账号的删除路径不回退
    当 管理员删除无核验记录的账号
    那么 返回 200，既有级联清理逐项生效

  # ── AH2：注销级联覆盖机器化复检 ──

  场景: schema 全表与 erase 覆盖差集为空
    假定 以 sql/schema.sql 全部 48 张表为基准
    当 机器化比对 AccountLifecycle.erase/suspend 覆盖
    那么 35 张用户数据表已覆盖，13 张在白名单（4 张审计只追加 + 9 张公共内容/字典/系统配置无用户归属列）
    并且 差集为空；新增表未纳入级联且未显式豁免时，AccountEraseCoverageTest 守卫用例失败

  # ── AH3：校园核验审核结果通知 ──

  场景: 审核通过 → 申请人收到通过通知
    假定 学生 erin 的核验申请处于 PENDING
    当 管理员审核通过
    那么 erin 收到一条 CAMPUS_AUDIT 通知，标题为「校园身份核验已通过」

  场景: 审核驳回 → 通知正文带审核理由
    当 管理员以理由「证件照片不清晰」驳回 erin 的申请
    那么 erin 收到 CAMPUS_AUDIT 通知，正文含「原因：证件照片不清晰」
    （与作品/评论驳回的文案风格一致）

  场景: 重复审核被拒绝且不重复通知
    当 管理员对已处理的申请再次审核
    那么 返回 3001「申请已被处理」，且不产生第二条通知

  场景: 通知未读数缓存沿用既有失效逻辑
    当 CAMPUS_AUDIT 通知写入
    那么 复用 push() 内既有 unreadCache 失效（不另建缓存）
