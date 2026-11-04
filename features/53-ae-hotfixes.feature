#language: zh-CN
功能: AE 批热修（2026-11-04，学生端真实联调复测发现的后端三缺陷）
  作为后端
  我希望 SSE 通道的隐私门返回正确的错误语义、作者能看到自己作品的审核理由、作品列表计数口径一致
  以便学生端隐私引导、驳回提示与分页计数不再被误导

  # ── AE1：SSE 通道隐私门状态码（最高优先级） ──

  场景: 已认证未同意 + Accept: text/event-stream → 403 + code 1003
    假定 学生 bob 已登录但未同意隐私政策
    当 bob 带 Accept: text/event-stream 调 POST /api/emotion/chat
    那么 返回 HTTP 403 且 code=1003「请先阅读并同意隐私政策」
    （修复前因内容协商失败 → /error → EntryPoint 被错写为 401/1002；
     GlobalExceptionHandler 改为直接向响应写 JSON、绕开协商）

  场景: 已认证未同意 + 无 SSE Accept 头 → 403 + code 1003（两种头语义一致）
    当 bob 不带 Accept 头调 POST /api/emotion/chat
    那么 返回 HTTP 403 且 code=1003

  场景: 无 token / token 无效 → 401 + code 1002（语义不变）
    当 匿名请求带或不带 Accept: text/event-stream 调 POST /api/emotion/chat
    那么 均返回 HTTP 401 且 code=1002

  场景: 已认证已同意 → 正常流式（chunk/revoke/done 协议与首字时间不变）
    假定 学生 carol 已登录且同意隐私政策
    当 carol 带 Accept: text/event-stream 调 POST /api/emotion/chat
    那么 返回 200 并按既有 chunk/done 协议流式输出

  # ── AE2：WorkVO.auditReason 作者可见 ──

  场景: 作者本人查看被下架/驳回作品 → 详情带 auditReason
    假定 carol 的作品被下架且审核理由为「封面不符合社区规范」
    当 carol 调 GET /api/works/{id}
    那么 data.auditReason = "封面不符合社区规范"

  场景: 他人查看非公开作品 → 1004（auditReason 不下发的前提）
    当 bob 调 GET /api/works/{carol 的下架作品 id}
    那么 返回 1004，不泄露作品存在性与审核理由

  场景: 未审核/已通过 → auditReason 为 null
    当 carol 查看自己已通过的作品
    那么 data.auditReason 为 null

  # ── AE3：GET /api/works 的 total 口径 ──

  场景: total 为 JSON number，雪花 id 仍为字符串
    当 任意已认证用户调 GET /api/works
    那么 data.total 为 JSON number（与 PageVO 口径一致）
    并且 data.records[*].id 仍为字符串雪花
