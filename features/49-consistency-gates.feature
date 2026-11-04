#language: zh-CN
功能: 敏感接口隐私同意门（S1）、评论通知 ref 语义（S2）、计数序列化口径（S3）
  作为平台运营方
  我希望敏感数据处理一律先取得隐私同意、通知可直接定位、计数口径一致
  以便履行合规义务并保证前后端行为可预期

  背景:
    假定 系统已启动，数据库已初始化

  # ── S1 隐私同意门 ──────────────────────────────

  场景: 未同意隐私政策时写入心情便签被拒（异常路径）
    假定 学生 alice 已登录但未同意隐私政策
    当 alice 调用 POST /api/emotion/notes 创建便签
    那么 返回 code=1003「请先阅读并同意隐私政策」
    并且 emotion_note 表没有新增记录

  场景: 同意隐私政策后敏感读写恢复（happy path）
    假定 alice 已同意隐私政策
    当 alice 调用 POST /api/emotion/notes 创建便签
    那么 返回 code=0 且便签加密落库
    并且 alice 可以分页查询、查看、编辑、删除本人便签

  场景: 同意门覆盖其他敏感写入面
    假定 学生 bob 已登录但未同意隐私政策
    当 bob 分别调用 POST /api/craft/session、GET /api/craft/recommend、POST /api/practice/sessions、POST /api/works/{id}/comments、POST /api/files/upload/image、POST /api/groups、POST /api/craft/recommend/click
    那么 每个请求都返回 code=1003，且不产生落库

  场景: 撤回与危机类接口不设卡（判定不挂门）
    假定 alice 已登录且已撤回隐私同意（privacy_agreed=0）
    当 alice 调用 POST /api/user/delete 注销账号
    那么 注销成功（撤回权不得设卡）
    并且 匿名访问 GET /api/crisis/hotlines 返回 code=0（危机干预不得设任何门槛）

  场景: 撤回同意后不再可读取心理敏感记录
    假定 alice 曾同意隐私政策并产生过情绪会话与成长记录，随后撤回同意
    当 alice 调用 GET /api/emotion/sessions 或 GET /api/growth/journey
    那么 返回 code=1003
    并且 alice 仍可通过 GET /api/works 浏览公开作品（公开内容读取不挂门）

  # ── S2 评论通知 ref 语义 ──────────────────────

  场景: 评论审核通知携带作品跳转目标与评论锚点（happy path）
    假定 学生 bob 在作品 W 下的评论被管理员审核（通过或隐藏）
    当 通知落库
    那么 该 COMMENT_AUDIT 通知 ref_type=comment、ref_id=W 的作品 ID、sub_ref_id=评论 ID
    并且 列表接口按字符串返回 refId 与 subRefId，学生端可直达作品并锚定评论

  场景: ref 语义变化不产生重复通知
    假定 bob 的评论已生成一条 COMMENT_AUDIT（status=1）通知
    当 同一审核事件被重复触发
    那么 先查后插去重命中（dedupe_key 按评论 id 生成），仍只有一条通知

  场景: 历史 COMMENT_AUDIT 数据迁移回填
    假定 数据库存在 ref_id=评论 ID、sub_ref_id 为 NULL 的历史 COMMENT_AUDIT 行
    当 执行迁移 20261104_notification_sub_ref.sql
    那么 能关联到 work_comment 的行被回填为 ref_id=work_id、sub_ref_id=评论 ID
    并且 评论已物理缺失的行保持原样
    并且 重复执行迁移结果不变（幂等）

  # ── S3 计数序列化口径 ─────────────────────────

  场景: 列表分页元数据统一为 JSON number
    当 请求通知列表、管理端评论列表、管理端导出留痕列表、我的作品/收藏、情绪会话列表、成长轨迹
    那么 各响应中的 total（及 page/pageNum/pageSize、journey 计数、mine 的 counts、programs 的 booked）均为 JSON number

  场景: 雪花 ID 仍为字符串
    当 请求上述任意列表
    那么 所有雪花 ID 字段（id/workId/refId/subRefId/userId 等）均按字符串返回

  场景: 统计看板计数为例外（只读展示）
    当 管理员请求 /api/admin/dashboard/stats、/api/admin/ops/health、/api/admin/ai-governance/overview
    那么 聚合计数按字符串返回（契约已显式标注为只读展示例外）
