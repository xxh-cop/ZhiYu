# 2026-11-07：审核软档处置链（抽检池/位次降权/SLA/回流）
# 设计：docs/moderation-soft-tier-disposition-chain-20261107.md
# 开关：zhiyu.moderation.soft-tier.disposition.enabled 默认 false（G0 回滚点）
Feature: 审核软档处置链

  Scenario: 审核员「通过并标记软档」happy path（mandatory_author_first → 抽中 → CLEAR 恢复）
    Given 处置链开关开启，且作品 AI 建议为 SOFT_CONCERN（narrative=self_disclosure）
    When 审核员 PUT /api/admin/works/{id}/status {status:1, softTier:true, narrative:"self_disclosure"}
    Then 作品 status=1 且同事务落 moderation_soft_review 台账
    And 该作者当日首条软档 → sample_layer=mandatory_author_first、sampled=1、pool_status=PENDING、sla_due_at=+24h
    And work.soft_demote_until ≈ +30 天（抽中硬上界），作者收到 SOFT_TIER_NOTICE 降权通知
    When 管理员 POST /api/admin/soft-tier-pool/{reviewId}/verdict {action:"CLEAR"}
    Then work.soft_demote_until 清空（恢复曝光），pool_status=CLEAR，作者收到恢复通知

  Scenario: 全覆盖层强制入池（已实证逃逸形态不得按概率抽）
    Given 处置链开关开启
    When 软档内容命中高危词库（mandatory_lexicon）或 AI 建议 riskPoints 非空（mandatory_riskpoint）
    Then sampled=1 恒成立，不受 sample-rate 与 daily-cap 约束
    And cap 打满只把 mandatory_author_first / random 层溢出为 sampled=0（overflow_* 标记、保留 72h 降权窗口、次日队列优先）

  Scenario: 位次降权读侧语义（开关开启/关闭两态）
    Given 社区流中存在软档内容（soft_demote_until 未过期）
    When 处置链开关开启
    Then 软档内容排在第 N 条非软档内容之后（N=demote-position 默认 20），组内按时间倒序；过期视同正常
    And 评论列表同口径（时间正序，软档评论落在第 N 条普通评论之后）
    When 处置链开关关闭（回滚）
    Then 读侧完全忽略 soft_demote_until，恢复纯时间序

  Scenario: SLA 超时默认偏保守（不自动恢复、不自动下架）
    Given 一条 PENDING 复核单 sla_due_at 已过 24h
    When SLA 扫描执行
    Then pool_status=ESCALATED（队列置顶），全部 role=3 管理员收到升级通知
    And 内容维持降权（demote_until 不变），逾期 48h 起每日提醒（按日窗口去重）
    And 未抽中内容走 72h 固定窗口自动恢复，不受 SLA 约束

  Scenario: BLOCK 改判复用既有状态机
    Given 一条 PENDING 复核单
    When 管理员 verdict {action:"BLOCK"} 但未填 reason
    Then 1001（理由必填）
    When verdict {action:"BLOCK", reason:"人工确认不适合公开"}
    Then 作品走既有 updateWorkStatus 下架（status=2 + audit_reason + WORK_AUDIT 通知），pool_status=BLOCK，降权列清除
    When 对已终裁复核单再次裁决
    Then 3001（幂等防并发）

  Scenario: 申诉逃生通道（反静默折叠）
    Given 作者的作品处于「展示受限」（降权未过期且复核未终裁）
    When 作者 POST /api/soft-tier-appeals {targetType:"work", targetId, reason:"这条只是记录手工过程"}
    Then appeal_status=FILED，appeal_reason 落库，复核单升 ESCALATED（SLA 重新起算），管理员收到申诉通知
    And 理由必填（≤200 字）且过输入侧内容安全过滤（scene=soft-appeal 写 ai_audit_log；命中高危词 1001 并触发关怀流程）
    And 重复申诉幂等成功（理由不覆盖）；他人申诉 1003；复核已终裁后申诉 3001
    And 裁决后 appeal_status=RESOLVED

  Scenario: 申诉列表与看板聚合（管理端）
    Given 存在若干申诉单与复核单
    When 管理员 GET /api/admin/soft-tier-appeals
    Then 返回 FILED 申诉分页（含 appealReason、authorNickname，雪花按字符串），按申诉时间倒序
    When GET /api/admin/soft-tier-pool?status=APPEAL
    Then 与申诉列表同口径（appeal_status=FILED）
    When GET /api/admin/soft-tier-pool/stats
    Then 一次返回当日入池量/落档量、待复核量、超时数、申诉数、裁决分布（全 JSON number，前端不需要「—」和估算）

  Scenario: 池详情补全（作者昵称与封面授权读取）
    Given 一条作品复核单
    When 管理员 GET /api/admin/soft-tier-pool/{id}
    Then 返回 authorNickname（脱敏口径同既有管理端评论详情），作者行缺失降级「已注销用户」不 500
    And 作品 coverUrl 经既有私有媒体授权读取：1h 签名临时地址 + 每次请求重新鉴权（role=3 在授权范围）
    And 非媒体引用（静态/外链）原样透传；失效媒体引用降级 null；绝不下发可长期访问的私有媒体 URL

  Scenario: 作者视角三条件口径（work 与 comment 一致）
    Given 处置链开启且作者的作品/评论带未过期降权
    Then GET /api/works/{id}、GET /api/works/mine、作品详情评论流均向作者本人下发 softDemoteUntil
    And 开关关闭 / 非本人 / 已过期任一不满足 → 不下发（徽标必须消失，防「告诉你受限但其实没有」的反向静默）

  Scenario: 开关关闭（G0/G2 回滚点）
    Given 处置链开关关闭（默认）
    When 审核员带 softTier:true 提交通过动作
    Then 1001「软档处置链未开启」且状态变更整体回滚（不产生半提交）
    And 复核队列/裁决/申诉端点均返回 1001；读侧恢复纯时间序
