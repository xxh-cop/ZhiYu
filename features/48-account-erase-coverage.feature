#language: zh-CN
功能: 注销清理全表覆盖（O1）与管理端评论查询三缺口（O2）
  作为平台运营方
  我希望用户注销后其个人数据逐表清零、管理端能按作品定位评论上下文
  以便履行隐私合规义务、审核通知可跳转定位

  背景:
    假定 系统已启动，数据库已初始化

  # ── O1 注销清理 ──────────────────────────────

  场景: 注销后陪伴记忆与练习会话清零（happy path）
    假定 学生 alice 已登录，且存在她的 user_ai_memory（value_cipher 加密偏好）与 practice_session（context_cipher 加密上下文、mood_code 情绪标签）
    当 alice 调用注销接口
    那么 user_ai_memory 中她的记录 deleted=1 且 value_cipher 被真正清空（不只是逻辑删除）
    并且 practice_session 中她的记录 deleted=1 且 context_cipher、mood_code 均为 NULL

  场景: 注销后通知、推荐、传承人资料等内容列清空
    假定 alice 存在 notification（标题/正文）、craft_recommendation（mood/reason）、inheritor_profile（skill/intro/honor）、craft_recommend_event、group_work（本人作品关联）
    当 alice 调用注销接口
    那么 notification 中她的记录 deleted=1 且 title/content 清空
    并且 craft_recommendation 中她的记录 deleted=1 且 mood/reason 为 NULL
    并且 craft_recommendation_item 中对应推荐明细随主单级联逻辑删除
    并且 inheritor_profile 中她的记录 deleted=1 且 skill/intro/honor 清空
    并且 craft_recommend_event 与 group_work（本人作品关联）记录 deleted=1

  场景: 注销后情绪链与作品内容列清空（既有覆盖回归）
    假定 alice 存在 emotion_session/emotion_message/emotion_note 与已发布作品
    当 alice 调用注销接口
    那么 emotion_message.content 清空、emotion_session.summary/mood 清空、emotion_note.content 清空
    并且 她的作品标题置为「已移除作品」、媒体引用清空、deleted=1

  场景: 只追加类审计表不删除（合规保留）
    假定 alice 的使用过程产生过 ai_audit_log 与 admin_export_log 记录
    当 alice 调用注销接口
    那么 ai_audit_log 记录保持不变（只追加审计，不含可识别身份列；身份关联经 user 表匿名化脱敏）
    并且 admin_export_log 记录保持不变（操作留痕合规要求）

  场景: 注销不误删其他用户数据（反向）
    假定 学生 bob 同样存在 user_ai_memory、practice_session、notification、craft_recommendation、inheritor_profile、group_work 与作品
    当 alice 调用注销接口
    那么 bob 的各表记录内容与 deleted=0 状态完全不变
    并且 bob 的小组作品关联保留

  # ── O2 管理端评论查询 ─────────────────────────

  场景: 评论脱敏详情供通知跳转（happy path）
    假定 管理员已登录，作品 W 下有一条长评论
    当 管理员调用 GET /api/admin/comments/{id}
    那么 返回 code=0，data 含 workId（字符串雪花）、status、authorName 与 contentExcerpt（≤50 字截断，不含全文）
    并且 咨询师（role=2）调用同一地址同样可读

  场景: 评论详情权限与已删除语义
    假定 学生 alice 已登录
    当 alice 调用 GET /api/admin/comments/{id}
    那么 返回 code=1003
    当 管理员请求一条已删除评论的详情
    那么 返回 code=1004

  场景: 评论列表按作品过滤
    假定 作品 A 下有 2 条待审评论、作品 B 下有 1 条待审评论
    当 管理员调用 GET /api/admin/comments?status=0&workId=A
    那么 返回 total=2 且全部属于作品 A
    当 管理员不传 workId 调用 GET /api/admin/comments?status=0
    那么 返回 total=3（旧调用方行为不变）

  场景: 作品详情抽屉展示历史驳回理由
    假定 管理员已下架作品 A 并填写理由「含未经证实的疗效宣传」
    当 管理员调用 GET /api/admin/works/{A}
    那么 返回 data.auditReason=「含未经证实的疗效宣传」
    并且 未审核过的作品 B 详情中 auditReason 为 null
