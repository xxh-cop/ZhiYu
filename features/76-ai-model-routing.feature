# AI 模型路由只读总览（2026-11-08，方案 A：管理端答辩/运维自查面板的数据端点）
# 覆盖：配置呈现（capabilities）+ provider 维度纯计数（distribution）+ 参数/权限校验。
#language: zh-CN
功能: AI 模型路由只读总览
  作为管理员
  我希望在一个端点看到每个能力当前路由到云端还是本地、cloud-only 约束、
  以及时间窗内各 scene 实际走过的 provider 分布
  以便答辩演示与日常运维自查（本应云端却走本地、兜底占比异常、cloud-only 出现本地记录）

  背景:
    假定 后端按能力分档路由已生效（ModelRouter 归属表 + LlmClientRegistry 装配状态）
    并且 ai_trace 表含 provider 列（2026-11-08 迁移），如实记录每次调用的实际归属

  场景: 总览返回路由配置与 provider 分布（happy path）
    当 管理员（role=3）请求 GET /api/admin/ai-routing/overview 携带 from/to（区间 ≤ 90 天）
    那么 返回 capabilities：每个能力的 task/tier/cloudModel/llmAllowed/cloudOnly/fallbackAllowed/sceneKeys
    并且 返回 registry：cloudAvailable/cloudProvider/localAvailable/localModel/mockAvailable/localDemoMode/shadowLocalEffective
    并且 返回 distribution：按 scene+provider 透视的 calls/okCount/fallbackCount/errorCount/blockedCount/otherCount
    并且 cloud-only 能力（listening_summary/companion/summary/lineart/moderation）的 cloudOnly=true 且 fallbackAllowed=false
    并且 crisis 的 llmAllowed=false 且 sceneKeys 为空（红线：不走 LLM）
    并且 响应只含配置与纯计数，不含用户 ID、会话 ID、prompt 或对话原文

  场景: sceneKeys 表达多场景归属（happy path）
    当 管理员查看 capabilities
    那么 listening_summary 的 sceneKeys 为 [llm-strong, llm-message]（strong 主路径 + standard 回落路径）
    并且 stuck_guide 与 message 共用 scene=llm-message（契约注明按能力汇总时该 scene 计数会重叠）
    并且 moderation_shadow 的 sceneKeys 为空（影子判定走 ai_audit_log 与影子台账，不在 ai_trace）

  场景: cloud-only 本地接管区分「历史事故」与「现行」（happy path）
    假定 事故锚点已配置（zhiyu.admin.ai-routing.incident-fixed-at，默认值为修复提交 ffbcd7c 时间）
    并且 ai_trace 中 llm-moderation 存在锚点前的 ollama 记录（已修复的注入歧义 bug 证据）
    当 管理员请求总览
    那么 cloudOnlyLocalCheck.postFixLocalCalls 只统计锚点后独占 scene 的 ollama 记录（应为 0，verdict=CLEAN）
    并且 cloudOnlyLocalCheck.preFixLocalCalls 单独给出锚点前历史事故计数，不计入现行红线
    并且 distribution 每行按锚点标 period=preFix/postFix
    当 请求携带 anchor 参数临时覆盖锚点
    那么 分桶与 pre/post 计数按覆盖值重算，incidentAnchor.source=override

  场景: 兜底按成因拆分「云缺席直供」与「云在场失败」（happy path）
    当 管理员查看异常自查区
    那么 「云在场·云端失败」= deepseek 行的 FALLBACK/ERROR 计数（逐行精确事实，真故障信号）
    并且 「云缺席·本地直供」= 本地服务数 − 云端失败数（聚合差值，标注为估算口径）
    并且 provider 未记录的降级（模板/缓存或加列前存量）单独标注，不混入上两数

  场景: 参数与权限校验（异常路径）
    当 from 不早于 to 或区间超过 90 天
    那么 返回 code=1001（参数校验失败）
    并且 学生（role=0）、传承人（role=1）、咨询师（role=2）访问返回 HTTP 403 + code=1003
    并且 未登录访问返回 HTTP 401 + code=1002

  场景: 端点只读（红线）
    当 对 /api/admin/ai-routing/overview 发起 POST/PUT/DELETE
    那么 返回 HTTP 405 + code=1005
    并且 任何路由策略（cloud-only 集合、兜底开关）不因此端点改变——切换走启动参数
