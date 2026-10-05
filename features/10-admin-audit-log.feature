#language: zh-CN
功能: 管理端 AI 审计日志查询（P1-A1）
  作为管理员或咨询师
  我希望查看 AI 审计日志列表与详情
  以便追溯 AI 调用的内容安全过滤记录

  背景:
    假定 管理员已登录管理端

  场景: 管理员查看审计日志列表
    当 管理员请求 GET /api/admin/audit-logs
    那么 返回 code=0，data 为审计日志分页列表
    而且 列表项不含 rawInput / aiOutput 字段

  场景: 咨询师查看审计日志列表
    当 咨询师（role=2）请求 GET /api/admin/audit-logs
    那么 返回 code=0，data 为审计日志列表

  场景: 学生不能访问审计日志
    当 学生（role=0）请求 GET /api/admin/audit-logs
    那么 返回 code=1003

  场景: 管理员查看审计日志详情（含解密内容）
    当 管理员请求 GET /api/admin/audit-logs/{id}
    那么 返回 code=0，data 包含 id / scene / action / reason / createTime
    而且 rawInput 为解密后的明文
    而且 aiOutput 为解密后的明文

  场景: 咨询师不能查看审计日志详情（仅管理员）
    当 咨询师（role=2）请求 GET /api/admin/audit-logs/{id}
    那么 返回 code=1003

  场景: 查看不存在的审计日志 → 1004
    当 管理员请求 GET /api/admin/audit-logs/99999
    那么 返回 code=1004

  场景: 按场景筛选审计日志
    当 管理员请求 GET /api/admin/audit-logs?scene=chat
    那么 返回 code=0，列表中只包含 scene=chat 的记录

  场景: 按动作筛选审计日志
    当 管理员请求 GET /api/admin/audit-logs?action=BLOCK
    那么 返回 code=0，列表中只包含 action=BLOCK 的记录
