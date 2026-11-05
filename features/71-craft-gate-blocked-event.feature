# language: zh-CN
@craft @takedown @metrics
功能: 心跳被下架闸口拒绝时落 step_gate_blocked 事件，看板只读起点从近似变精确
  此前看板剔除只读会话的事件证据只有归档 Worker 派生的 session_archive（扫描周期近似）。
  本特性让闸口拒绝当场落服务端派生事件 step_gate_blocked（每会话至多一条，幂等），
  看板剔除判定第三档优先消费 gate 事件（精确档），session_archive 退为兜底档；
  剔除明细 readOnlyEvidence 如实标注档位。3001 对外语义不变，事件落库失败不影响被拒响应。

  背景:
    假设项目 P 已被合规下架（会话转只读），学生 S 在 P 有进行中会话

  @happy-path
  场景: 首次心跳被拒落证据事件，看板取精确起点
    当学生 S 发送心跳推进步骤
    那么返回 code=3001（会话只读，无法推进）
    并且 craft_session_event 落一条 step_gate_blocked（含 step_no 与 gate=takedown context）
    当学生 S 再次发送心跳
    那么仍返回 code=3001，且不再新增 step_gate_blocked（每会话至多一条）
    当管理端查看心流看板
    那么该会话被剔除（reason=read_only），readOnlySince 取 gate 事件 occurred_at，
      readOnlyEvidence=step_gate_blocked

  @happy-path
  场景: 无 gate 事件的会话退回兜底档
    假设会话在只读期间从未再发心跳（无 step_gate_blocked），仅有 session_archive 事件
    当管理端查看心流看板
    那么剔除起点取 session_archive 首次时点，readOnlyEvidence=session_archive

  @error-path
  场景: 事件落库失败不改变被拒语义
    当事件表写入失败
    那么心跳仍返回 code=3001（不出现 5000，也不假装成功）
    并且 step_gate_blocked 不允许客户端经 events 上报接口伪造（上报即 1001）
