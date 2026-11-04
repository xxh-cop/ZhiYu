#language: zh-CN
功能: 审核理由落库与审核队列聚合（I1/I2）
  作为平台管理员
  我希望驳回时必须填写理由并送达用户、审核工作台能看到作品/评论的待审队列
  以便用户知道为什么被驳回，审核不再对着空队列工作

  背景:
    假定 系统已启动，数据库已初始化
    并且 管理员已登录

  场景: 下架作品必须填写理由（happy path）
    假定 学生 alice 发布了一件待审作品
    当 管理员以理由「含未经证实的疗效宣传」将其下架
    那么 接口返回 code=0
    并且 work 表的 audit_reason 落库为该理由
    并且 alice 收到的下架通知正文包含「原因：含未经证实的疗效宣传」

  场景: 下架作品缺少理由被拒绝
    假定 学生 alice 发布了一件待审作品
    当 管理员不提供理由将其下架
    那么 接口返回 code=1001
    并且 作品状态不发生变更

  场景: 发布/通过时理由可空
    假定 学生 alice 发布了一件待审作品
    当 管理员不提供理由将其发布
    那么 接口返回 code=0，audit_reason 为 NULL
    并且 alice 收到「审核已通过并发布」的通知（正文不含「原因：」）

  场景: 隐藏评论必须填写理由并通知作者
    假定 bob 的评论处于待审状态
    当 管理员以理由「含不当引导」隐藏该评论
    那么 work_comment.audit_reason 落库
    并且 bob 收到的评论审核通知正文包含该理由但不含评论原文

  场景: 历史无理由数据读取兼容
    假定 数据库中存在 audit_reason 为 NULL 的历史审核记录
    当 打开作品/评论审核列表与详情
    那么 读取不报错，理由字段显示为空

  场景: 审核队列返回待审作品与待审评论
    假定 存在 1 件待审作品与 2 条待审评论
    当 管理员请求 GET /api/admin/audit-queue?type=work
    那么 返回 1 条 work 记录（含 id/title/authorId/createTime）
    当 管理员请求 GET /api/admin/audit-queue?type=comment
    那么 返回 2 条 comment 记录（含 excerpt 评论摘要与 refId 所属作品）

  场景: 对象级 AI 审计摘要按 ref 精确关联，不伪造
    假定 待审作品 W 在 2026-11-04 前发布（历史审计无 ref 关联）
    当 管理员查看 W 的队列项
    那么 aiAudits 为空数组，auditHint 说明「历史审计未记录对象关联」
    并且 不返回按作者或时间窗猜测的审计记录
    假定 此后产生一条 ref_type=work、ref_id=W 的新审计
    当 管理员再次查看 W 的队列项
    那么 aiAudits 返回该条摘要（scene/action/reason/createTime）
    并且 摘要不包含 raw_input 与 ai_output 原文列
