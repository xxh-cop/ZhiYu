# language: zh-CN
@craft @takedown @export
功能: 合规下架窗口内的本人会话创作数据导出（GET /api/craft/session/{id}/export）
  分层下架给了只读窗口，本功能补上窗口里的真实出口：学生本人在 export_until 前
  可下载自己该项目下自己会话的创作数据（JSON 附件）；窗口过期明确报 3001 而非 1004；
  每次导出落 craft_session_event 的 export 事件，不静默。

  背景:
    假设学生 S 在项目 P 有会话（进行中或已转只读）
    并且管理员以 kind=compliance 下架了项目 P（export_until=下架时刻+72 小时）

  @happy-path
  场景: 窗口内导出成功，附件内容与事件留痕
    当学生 S 请求 GET /api/craft/session/{id}/export
    那么返回 200 附件：Content-Disposition: attachment，文件名含会话与项目标识，
      Content-Type 为 application/json;charset=UTF-8，Cache-Control 为 no-store
    并且导出 JSON 含 project（id/title/takedownKind）、session（起止/时长/当前步骤/flowSeconds）、
      events（本人本会话全部已发生事件，列口径同 data-export 的 craft-events）、
      works（该会话产出的作品标识，不含媒体内容）、exportUntil（真实窗口截止）
    并且 craft_session_event 新增一条 event_type=export 记录（含在本次导出文件中自证）
    并且不导出他人内容、不导出平台步骤内容；当前拿不到的数据类别在 omitted 字段如实说明

  @error-path
  场景: 窗口过期后导出被拒并给可读原因
    假设导出窗口已过期（export_until 早于当前时刻）
    当学生 S 请求导出
    那么返回 HTTP 200 但 body code=3001，提示「导出窗口已于 X 关闭，现仅可回看」
    并且不产生附件、不写 export 事件

  @error-path
  场景: 权限与开关边界
    当他人请求导出学生 S 的会话，那么返回 code=1003
    当未登录请求导出，那么返回 code=1002
    当会话不存在，那么返回 code=1004
    当 zhiyu.takedown.export-enabled=false，即使窗口内也返回 code=3001「导出功能暂未开放」
    当项目为上架或运营下架（不在合规导出窗口），返回 code=3001「该项目不在可导出窗口内」
