# 2026-11-07：审核软档「影子模式」观测能力
# 口径：只统计「若开启软档会怎样」，绝不改变线上判定与可见性——不写 work/work_comment 软档字段、
# 不影响审核结论、不影响可见性/排序、不触发通知、不入抽检池；只 INSERT/UPDATE moderation_soft_shadow。
# 开关：zhiyu.moderation.soft-tier.shadow.enabled 默认 false（与软档总开关、处置链开关相互独立）
Feature: 审核软档影子模式

  Scenario: 影子判定 happy path（只落观测台账，线上零差异）
    Given 影子开关开启，软档总开关与处置链开关均关闭
    And 作者发布一条作品与一条评论（先审后发，status=0 待审）
    When 影子扫描执行，AI 按「若开启」口径（提示词含软档指引、枚举含 SOFT_CONCERN、过窄门）判定作品为 SOFT_CONCERN、评论为 NO_OBVIOUS_ISSUE
    Then 两条内容各落一行 moderation_soft_shadow：judge_status=OK、tendency 如上、attempts=1、narrative 留证、audit_log_id 关联 scene=moderation_shadow 的审计行
    And work/work_comment 的 soft_demote_until 仍为 NULL，status 不变，moderation_soft_review 无新行，无任何通知产生
    And 社区流与评论列表排序、作者视角徽标均与影子模式开启前完全一致

  Scenario: AI 不可用不一次定终态（重试至上限才落 UNAVAILABLE）
    Given 影子开关开启，max-attempts=3，AI 连续不可用（moderationSuggest 返回 null）
    When 第一轮扫描命中一条新送审内容
    Then 落影子行 judge_status=PENDING、attempts=1（不进分子分母）
    When 第二、三轮扫描重试仍不可用
    Then attempts=3 且 judge_status=UNAVAILABLE（终态，单独统计，不再重试）
    When 第四轮 AI 恢复
    Then 该 UNAVAILABLE 行不再被重试；后续新内容正常判定落 OK

  Scenario: 日级预算硬上限（到限当日截断，次日恢复）
    Given 影子开关开启，daily-budget=2，当日已产生 2 条影子判定（ai_audit_log scene=moderation_shadow 计数=2）
    When 新一轮扫描执行
    Then 不再发起任何 AI 调用（无新影子行、无新审计行），日志标记当日已截断
    And 统计接口 dailyBudget.truncated=true 且 usedToday=2
    When 次日零点后扫描
    Then 恢复判定（积压样本在回扫窗口内继续）

  Scenario: 统计口径（主指标 + 保守下界 + 覆盖率降级）
    Given 窗口内业务表实际送审 10 条，影子判定 OK 8 条（其中 SOFT_CONCERN 2 条）、UNAVAILABLE 1 条、PENDING 1 条
    When 管理员 GET /api/admin/moderation/shadow-stats?from=...&to=...
    Then submittedTotal=10、judgedTotal=8、softTotal=2、softRate=0.25（soft/judged，主指标）
    And coverageRate=0.8、softRateOnSubmitted=0.2（保守下界）
    And coverageRate 达到阈值（默认 0.8）时 degraded=false；低于阈值或窗口无数据时 degraded=true 且返回实际 coverageRate
    And UNAVAILABLE/PENDING 单独计数，不进分子分母

  Scenario: 无害率与分层明细（人工结论实时 JOIN）
    Given 窗口内 2 条作品被影子判为 SOFT_CONCERN
    When 人工终审 1 条通过（status=1）、1 条驳回（status=2）
    Then 统计接口 softReviewed={approved:1, rejected:1, pending:0, harmlessRate:0.5}
    And byTargetType 按 work/comment 分行给出 submitted/judged/soft/softRate
    And bySchool 按 campus_verification.school 分组（未认证落「未认证」桶），只统计已判定行
    And 人工终审尚未审结（status=0）的落软档样本计入 pending，不进 harmlessRate 分母

  Scenario: 开关关闭（默认）整体空转
    Given 影子开关关闭（默认）
    When 有新送审内容且扫描周期到达
    Then 不产生任何影子行与 AI 调用
    And 统计接口仍可读取此前开启窗口采集的历史数据（不随开关 gated）

  Scenario: 注销级联与隐私纪律
    Given 某作者存在若干影子行
    When 该作者注销账号（AccountLifecycle.erase）
    Then 其 moderation_soft_shadow 行全部逻辑删除（deleted=1），其他作者的影子行不受影响
    And 全程日志只含对象 ID 与判定结果；内容原文仅加密存于 ai_audit_log（scene=moderation_shadow，与审核员 moderation 场景隔离）

  Scenario: 模型归属（反向排除：演示数据不混生产口径）
    Given 影子开关开启且 AI 可用，一条送审内容被实际判成
    Then 影子行落真实 provider/model（如 deepseek/deepseek-reasoner）
    When 首次判定失败（PENDING）后重试成功
    Then provider/model 以最后一次实际判定为准被覆盖；失败尝试本身不污染归属（保持 unknown）
    And 加列前存量行 provider/model=unknown（显式未知标记，绝不默认当成云端）
    When 管理员 GET /api/admin/moderation/shadow-stats（默认口径 production）
    Then 排除演示来源清单（demo-providers，默认 ollama/mock，单一集中定义可配置）；
      其余全部计入——含 unknown 存量（云端时代判定），云端换供应商（如 aliyun）也不会被静默归零
    And demoJudgedInWindow 显式标注被排除的演示判定条数（分子分母不同源不让用户自己发现）
    And byProvider 分桶中 unknown 单独成桶可见；softRate/coverageRate/harmlessRate 定义与 degraded 判定不变
    When 带 ?provider=ollama 或 ?provider=all 查询
    Then 分别仅返回该归属 / 不过滤的口径数据（显式取数能力保留）
