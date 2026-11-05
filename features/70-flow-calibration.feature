# language: zh-CN
@craft @flow @calibration
功能: 心流自评校准（阶段 1-1）——为「心流」指标建立效度锚点
  行为代理「无操作计时 ⇒ 心流」此前没有效度证据。会话 finish 时按配置比例抽样，
  向被抽中用户发单题自评（1~7），用 Spearman 等级相关检验自评与「专注停留占比」的
  一致性；效度不足时管理端指标名降级为「专注停留占比」。抽样结论落库（wellbeing_record）
  稳定可追溯；跳过也落记录，区分「没看到」与「看到了不答」。

  背景:
    假设学生 S 有进行中会话，zhiyu.flow.calibration.enabled=true

  @happy-path
  场景: 抽样 → 查询 → 作答 → 管理端聚合
    当会话 finish 且命中抽样（sample-rate）
    那么落一行 wellbeing_record（kind=FLOW_CAL，record_key=会话 ID），sampled=true
    当学生 S 查询 GET /api/craft/session/{id}/calibration
    那么返回 sampled=true + question + scale（1~7，含两端文案）
    并且重复查询结论不变（抽样稳定可追溯）
    当学生 S 提交 { "score": 6 }
    那么记录 answered=true、score=6，返回提交后状态
    当管理端请求 GET /api/admin/flow-calibration/stats
    那么样本 ≥30 时返回 status=ok、Spearman ρ；ρ≥0.3 时 metricLabel=「心流占时」

  @happy-path
  场景: 跳过同样落记录，之后仍可补答
    假设会话被抽中且未作答
    当学生 S 提交 { "skip": true }
    那么记录 skipped=true、answered=false（与「未抽中/没看到」可区分）
    当学生 S 再提交 { "score": 5 }
    那么允许补答（answered=true、score=5）

  @error-path
  场景: 非法与权限边界
    当提交 score=0 或 8 或两字段都空，那么返回 code=1001
    当已作答后重复提交，那么返回 code=3001「已提交过自评」
    当会话未被抽中（sampled=false 或无记录），提交返回 code=3001
    当非本人查询或提交，那么返回 code=1003
    当会话不存在，那么返回 code=1004
    当未登录，那么返回 code=1002

  @error-path
  场景: 样本不足时不假装有结论
    当管理端已作答样本 < 30
    那么聚合接口返回 status=insufficient、rho=null、metricLabel=「专注停留占比」
    当 ρ < 0.3（含常数序列按 0 处理）
    那么 metricLabel 降级为「专注停留占比」而非「心流占时」
