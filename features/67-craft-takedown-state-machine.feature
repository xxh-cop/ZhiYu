# language: zh-CN
@craft @admin @takedown
功能: 手作项目分层下架状态机（1→2 带类型，2→3 归档）与步骤结构化只读视图
  下架不再是终态删除式硬中断：合规下架给 72 小时只读导出窗口，运营下架让存量会话
  运行到截止时间；到期统一转归档。listSteps 对下架/归档项目返回结构化视图
  （步骤可回看 + 会话状态 + readOnly/canExport/canReview + 截止时间 + noticeKey/notice），
  仅项目不存在或待审才返回 1004。

  背景:
    假设存在已上架项目 P（status=1，2 个步骤）
    并且学生 S 在项目 P 有进行中会话（status=1）

  @happy-path
  场景: 合规下架 → 会话只读 → 窗口内可导出 → 到期归档
    当管理员以 kind=compliance、理由「内容需复核」下架项目 P
    那么项目 P status=2，takedown_kind=compliance，export_until=下架时刻+72 小时，notice_key=craft-takedown-compliance
    并且学生 S 不能再创建该项目新会话（1004）
    当学生 S 查询项目 P 的步骤列表
    那么返回 200 结构化视图：projectStatus=2、readOnly=true、canReview=true、canExport=true
    并且 steps 含 2 个历史步骤，notice 含项目标题与窗口截止时间、不含指向性措辞
    当学生 S 对该会话发送心跳推进步骤
    那么被拒绝（code=3001，提示会话已转只读）
    但学生 S 仍可 finish/abandon 退出会话
    当导出窗口过期后学生 S 再查步骤列表
    那么按归档下发：projectStatus=3、canExport=false、noticeKey=craft-archived
    并且归档 Worker 把项目落为 status=3、archive_at=export_until（幂等）

  场景: 运营下架 → 存量会话不受影响 → 到期归档
    当管理员以 kind=operational、archiveAt=10 天后下架项目 P
    那么项目 P status=2，archive_at 为指定截止时间，export_until 为空
    当学生 S 查询项目 P 的步骤列表
    那么返回 200 结构化视图：projectStatus=2、readOnly=false、canExport=false、noticeKey=craft-takedown-operational
    并且学生 S 的进行中会话仍可正常心跳推进
    当 archive_at 到期后
    那么步骤列表按归档下发（readOnly=true），归档 Worker 落 status=3

  @error-path
  场景: 运营下架未填截止时间被拒
    当管理员以 kind=operational 但不提供 archiveAt 下架项目 P
    那么请求被拒（code=1001，提示必须填写截止时间）
    并且项目 P 仍保持 status=1
    当管理员以 kind=operational、archiveAt=3 天后下架项目 P
    那么请求被拒（code=1001，截止时间距现在至少 7 天）

  @error-path
  场景: 窗口过期后仍尝试推进步骤被拒
    假设项目 P 已被合规下架且导出窗口已过期
    当学生 S 对进行中会话发送心跳推进步骤
    那么被拒绝（code=3001）
    但学生 S 查询步骤列表仍返回 200 归档视图（可回看，不可导出）
