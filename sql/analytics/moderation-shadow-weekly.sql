-- ============================================================================
-- 软档影子模式·周报取数脚本（2026-11-07）
--
-- 用途：管理端未做页面（任务裁决），周报应答「若开启软档会怎样」的直接取数通道。
-- 口径与 GET /api/admin/moderation/shadow-stats 完全一致；差异仅为无 degraded 布尔封装
-- （第 1 段直接给出 coverage_rate 与 0.8 阈值的比较列，读者自判）。
--
-- 跑法（MySQL 8，仅只读 SELECT，可安全重复执行）：
--   1) 改下面三行窗口日期与 provider（窗口含头含尾，按对象送审时间过滤）；
--   2) mysql -h <host> -u <user> -p zhiyu < sql/analytics/moderation-shadow-weekly.sql
--      或在任意 MySQL 客户端整段执行。H2 不适用（测试库请直接调统计接口）。
--
-- 口径提醒（防误读，与接口契约一致）：
--   - soft_rate（主指标）= soft / judged；judged = judge_status='OK' 的影子记录数；
--   - submitted = work+work_comment 窗口内实际新增（含已逻辑删除——送审即分母，
--     不随 @provider 过滤变化）；
--   - coverage_rate = judged / submitted < 0.8 时本周数字不得当发生率引用（显式降级纪律）；
--   - UNAVAILABLE（达重试上限终态）与 PENDING（待重试）单独列示，不进分子分母；
--   - harmless_rate = 落软档样本中人工终审通过(status=1) / 已审结(status∈{1,2})；
--     人工结论实时 JOIN 业务表，未审结(status=0) 计入 pending 不进分母；
--   - provider 口径（2026-11-07 反向排除）：默认 @provider='production' 排除演示来源
--     （清单 ollama,mock——以 zhiyu.moderation.soft-tier.shadow.demo-providers 配置为准，
--     本文件因独立成文持有字面量副本，改配置需同步本行下方 NOT IN 列表）；
--     其余含 unknown 存量都算生产（云端换供应商不会静默归零）；
--     演示数据改 @provider 单独取，'all' 不过滤；unknown 同时在第 5 段分桶单独可见；
--   - 分子分母同源声明：@provider='all' 时分子分母完全同源；'production' 口径分子排除
--     演示判定、分母含全部送审——窗口内存在演示判定时覆盖率如实偏低（第 1 段
--     demo_judged_in_window 列显式标注被排除条数）。
-- ============================================================================

SET @from_date = '2026-11-02';  -- 窗口起始日（含）
SET @to_date   = '2026-11-08';  -- 窗口截止日（含）
SET @provider  = 'production';  -- 归属过滤：production=排除演示（默认）/ 具体 provider 值 / all=不过滤
-- 演示来源清单（与 zhiyu.moderation.soft-tier.shadow.demo-providers 默认保持一致；CSV 供 FIND_IN_SET）：
SET @demo_providers = 'ollama,mock';

-- ── 1. 主指标与覆盖率 ──
SELECT
  (SELECT COUNT(*) FROM work         WHERE create_time >= @from_date AND create_time < @to_date + INTERVAL 1 DAY)
+ (SELECT COUNT(*) FROM work_comment WHERE create_time >= @from_date AND create_time < @to_date + INTERVAL 1 DAY)
    AS submitted_total,
  (SELECT COUNT(*) FROM moderation_soft_shadow d
    WHERE d.judge_status = 'OK' AND d.deleted = 0
      AND FIND_IN_SET(d.provider, @demo_providers)
      AND d.target_create_time >= @from_date AND d.target_create_time < @to_date + INTERVAL 1 DAY)
    AS demo_judged_in_window,
  SUM(s.judge_status = 'OK')                                                          AS judged_total,
  SUM(s.judge_status = 'OK' AND s.tendency = 'SOFT_CONCERN')                          AS soft_total,
  SUM(s.judge_status = 'UNAVAILABLE')                                                 AS unavailable_total,
  SUM(s.judge_status = 'PENDING')                                                     AS pending_retry_total,
  ROUND(SUM(s.judge_status = 'OK' AND s.tendency = 'SOFT_CONCERN')
        / NULLIF(SUM(s.judge_status = 'OK'), 0), 4)                                   AS soft_rate,
  ROUND(SUM(s.judge_status = 'OK') / NULLIF((
        (SELECT COUNT(*) FROM work         WHERE create_time >= @from_date AND create_time < @to_date + INTERVAL 1 DAY)
      + (SELECT COUNT(*) FROM work_comment WHERE create_time >= @from_date AND create_time < @to_date + INTERVAL 1 DAY)
      ), 0), 4)                                                                       AS coverage_rate,
  ROUND(SUM(s.judge_status = 'OK' AND s.tendency = 'SOFT_CONCERN') / NULLIF((
        (SELECT COUNT(*) FROM work         WHERE create_time >= @from_date AND create_time < @to_date + INTERVAL 1 DAY)
      + (SELECT COUNT(*) FROM work_comment WHERE create_time >= @from_date AND create_time < @to_date + INTERVAL 1 DAY)
      ), 0), 4)                                                                       AS soft_rate_on_submitted,
  IF(ROUND(SUM(s.judge_status = 'OK') / NULLIF((
        (SELECT COUNT(*) FROM work         WHERE create_time >= @from_date AND create_time < @to_date + INTERVAL 1 DAY)
      + (SELECT COUNT(*) FROM work_comment WHERE create_time >= @from_date AND create_time < @to_date + INTERVAL 1 DAY)
      ), 0), 4) < 0.8, 'DEGRADED（覆盖率不足，勿当发生率）', 'OK')                       AS degraded_check
FROM moderation_soft_shadow s
WHERE s.deleted = 0
  AND (@provider = 'all' OR (@provider = 'production' AND NOT FIND_IN_SET(s.provider, @demo_providers)) OR (@provider <> 'production' AND s.provider = @provider))
  AND s.target_create_time >= @from_date
  AND s.target_create_time < @to_date + INTERVAL 1 DAY;

-- ── 2. 无害率（落软档样本的人工终审分布；实时 JOIN 业务表） ──
SELECT
  SUM(t.status = 1)                                                AS approved,
  SUM(t.status = 2)                                                AS rejected,
  SUM(t.status = 0)                                                AS pending,
  ROUND(SUM(t.status = 1) / NULLIF(SUM(t.status IN (1, 2)), 0), 4) AS harmless_rate
FROM (
  SELECT w.status  FROM moderation_soft_shadow s JOIN work         w ON w.id = s.target_id
   WHERE s.target_type = 'work'    AND s.judge_status = 'OK' AND s.tendency = 'SOFT_CONCERN' AND s.deleted = 0
     AND (@provider = 'all' OR (@provider = 'production' AND NOT FIND_IN_SET(s.provider, @demo_providers)) OR (@provider <> 'production' AND s.provider = @provider))
     AND s.target_create_time >= @from_date AND s.target_create_time < @to_date + INTERVAL 1 DAY
  UNION ALL
  SELECT c.status  FROM moderation_soft_shadow s JOIN work_comment c ON c.id = s.target_id
   WHERE s.target_type = 'comment' AND s.judge_status = 'OK' AND s.tendency = 'SOFT_CONCERN' AND s.deleted = 0
     AND (@provider = 'all' OR (@provider = 'production' AND NOT FIND_IN_SET(s.provider, @demo_providers)) OR (@provider <> 'production' AND s.provider = @provider))
     AND s.target_create_time >= @from_date AND s.target_create_time < @to_date + INTERVAL 1 DAY
) t;

-- ── 3. 分层：内容类型 ──
SELECT
  s.target_type                                                              AS target_type,
  COUNT(*)                                                                   AS judged,
  SUM(s.tendency = 'SOFT_CONCERN')                                           AS soft,
  ROUND(SUM(s.tendency = 'SOFT_CONCERN') / NULLIF(COUNT(*), 0), 4)           AS soft_rate
FROM moderation_soft_shadow s
WHERE s.judge_status = 'OK' AND s.deleted = 0
  AND (@provider = 'all' OR (@provider = 'production' AND NOT FIND_IN_SET(s.provider, @demo_providers)) OR (@provider <> 'production' AND s.provider = @provider))
  AND s.target_create_time >= @from_date AND s.target_create_time < @to_date + INTERVAL 1 DAY
GROUP BY s.target_type;

-- ── 4. 分层：校园（campus_verification 人工核验；未认证落「未认证」桶） ──
SELECT
  COALESCE(cv.school, '未认证')                                              AS school,
  COUNT(*)                                                                   AS judged,
  SUM(s.tendency = 'SOFT_CONCERN')                                           AS soft,
  ROUND(SUM(s.tendency = 'SOFT_CONCERN') / NULLIF(COUNT(*), 0), 4)           AS soft_rate
FROM moderation_soft_shadow s
LEFT JOIN campus_verification cv ON cv.user_id = s.author_id AND cv.deleted = 0
WHERE s.judge_status = 'OK' AND s.deleted = 0
  AND (@provider = 'all' OR (@provider = 'production' AND NOT FIND_IN_SET(s.provider, @demo_providers)) OR (@provider <> 'production' AND s.provider = @provider))
  AND s.target_create_time >= @from_date AND s.target_create_time < @to_date + INTERVAL 1 DAY
GROUP BY COALESCE(cv.school, '未认证')
ORDER BY judged DESC;

-- ── 5. 归属分桶（不分 @provider 过滤：unknown 单独成桶，演示归属可见，绝不并入云端口径） ──
SELECT
  s.provider                                                                 AS provider,
  COUNT(*)                                                                   AS rows_total,
  SUM(s.judge_status = 'OK')                                                 AS judged,
  SUM(s.judge_status = 'OK' AND s.tendency = 'SOFT_CONCERN')                 AS soft,
  ROUND(SUM(s.judge_status = 'OK' AND s.tendency = 'SOFT_CONCERN')
        / NULLIF(SUM(s.judge_status = 'OK'), 0), 4)                          AS soft_rate
FROM moderation_soft_shadow s
WHERE s.deleted = 0
  AND s.target_create_time >= @from_date AND s.target_create_time < @to_date + INTERVAL 1 DAY
GROUP BY s.provider
ORDER BY rows_total DESC;

-- ── 6. 当日预算用量（评审 b：truncated=1 表示当日已截断，积压次日在回扫窗口内继续） ──
SELECT
  COUNT(*)                                                                          AS used_today,
  2000                                                                              AS daily_budget_default,
  IF(COUNT(*) >= 2000, 1, 0)                                                        AS truncated
FROM ai_audit_log
WHERE scene = 'moderation_shadow'
  AND create_time >= CURDATE();
