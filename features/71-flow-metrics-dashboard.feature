# language: zh-CN
@admin @flow @metrics
功能: 心流/专注度量看板（阶段 1-2）——管理端聚合 API + 看板页
  口径以 docs/flow-metrics-dashboard-literature-20261105.md §3/§9 为准：
  分母=各步骤 step_leave.dwell_seconds 之和（活跃创作时长），分子=flow_seconds
  （2026-11-05 起为「阈值后深度沉浸秒数」）；会话级占比 → 人×周中位数，
  报 p50/p90 与分桶样本数，不给均值大数字；只读/归档/待审会话分子分母同口径剔除
  且剔除明细透明返回；运营下架批次按 takedown_kind 单独分面不并线；
  不可得指标以 unavailable[] 如实列出，不用 0 冒充；指标名由自评校准效度下发
  （insufficient 或 ρ<0.3 时为「专注停留占比」）。页脚固定声明 engagement ≠ 疗效。

  背景:
    假设管理员 A（role=3）已登录，区间参数 from/to 为近 30 天

  @happy-path
  场景: 聚合返回同口径的分母分子与分布
    假设区间内有终态会话：正常会话（step_leave 合计 900s、flow_seconds=300）
    当管理端请求 GET /api/admin/flow-metrics/dashboard
    那么返回 totals.includedSessions 含该会话
    并且分母 activeSeconds 含 900、分子 flowSeconds 含 300（同口径）
    并且 distribution 给出人×周中位占比的 p50/p90 与 10 桶样本数
    并且 weekly 按周给出中位数、IQR（p25/p75）与活跃人数
    并且 definitionChanges 固定列出 2026-11-05 三处口径变更（归档态/只读剔除/flow_seconds 语义）

  @happy-path
  场景: 只读/归档/待审会话剔除且透明可见
    假设合规下架项目（takedown_at 昨天、导出窗口未过）有一会话在下架后仍有活动
    且一待审项目有终态会话
    当管理端请求看板聚合
    那么两会话不进分布，totals.excludedReadOnly/excludedPendingProject 各 +1
    并且 exclusions 明细返回会话/项目 ID、reason 与只读起点（不悄悄剔除）
    假设运营下架未到期项目有终态会话
    那么该会话计入分布，但在 facets.byTakedownKind 单列 operational 格，不与常态并线

  @happy-path
  场景: 样本不足时指标名降级并如实声明
    假设自评作答样本 < 30
    当管理端请求看板聚合
    那么 calibration.status=insufficient、metricLabel=「专注停留占比」
    并且 unavailable[] 含 self_report_rho（相关系数不可得）
    并且看板页显式说明「样本不足（n<30），尚未验证与自评心流的一致性」
    并且页脚固定声明「engagement ≠ 疗效」常驻可见（不折叠、非 hover）

  @error-path
  场景: 区间参数非法
    当请求 from 晚于 to / 跨度超过 90 天 / 日期格式非法
    那么返回 HTTP 400 + code=1001（参数校验失败），不产生聚合结果

  @error-path
  场景: 非管理员与匿名访问被拒
    当匿名请求看板聚合
    那么返回 HTTP 401 + code=1002（body 非空）
    当 role ∈ {0,1,2} 用户请求
    那么返回 HTTP 403 + code=1003（body 非空）
