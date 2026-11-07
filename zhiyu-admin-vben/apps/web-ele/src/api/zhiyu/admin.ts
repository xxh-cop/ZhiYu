import { requestClient } from '#/api/request';

export namespace ZhiyuAdminApi {
  export interface AuditQueueParams {
    type?: string;
    page?: number;
  }
  export interface AuditQueueItem {
    id: number;
    title: string;
    inheritorName?: string;
    category?: string;
    createTime?: string;
    [key: string]: any;
  }
  export interface AuditRequestBody {
    action: 'approve' | 'reject';
    reason?: string;
  }
  export interface CareRemindersParams {
    status?: number;
    page?: number;
  }
  export interface CareReminder {
    id: number;
    userId: number;
    sessionId?: number;
    riskLevel: number;
    summary: string;
    status: number;
    createTime: string;
    [key: string]: any;
  }
  export interface DashboardStats {
    totalUsers: number;
    totalWorks: number;
    pendingProjects: number;
    pendingCareReminders: number;
    moodDistribution: Array<{ mood: string; count: number }>;
    userTrend7d: Array<{ date: string; count: number }>;
  }
}

export async function getAuditQueue(params: ZhiyuAdminApi.AuditQueueParams) {
  const res = await requestClient.get<{
    total: number;
    pageNum: number;
    pageSize: number;
    records: ZhiyuAdminApi.AuditQueueItem[];
  }>('/admin/audit-queue', { params });
  return { ...res, total: Number(res.total) };
}

export async function auditProject(
  id: number,
  data: ZhiyuAdminApi.AuditRequestBody,
) {
  return requestClient.put(`/admin/projects/${id}/audit`, data);
}

/**
 * 下架已上架项目（AY1 + 分层下架 2026-11-05）：仅 status 1→2，
 * kind 必填（compliance 合规 / operational 运营）；operational 必须带 archiveAt
 * （距现在 ≥7 天，管理端按后端 default-deadline-days=14 预填）；compliance 不得带
 * archiveAt（下架时刻 + 72h 导出窗口后自动归档）。理由必填 ≤300 字，落
 * craft_project.audit_reason 并通知传承人；非法参数 1001、非上架态 3001（原文上屏）。
 */
export async function takedownProject(
  id: number | string,
  body: { archiveAt?: string; kind: 'compliance' | 'operational'; reason: string },
) {
  return requestClient.put(`/admin/projects/${id}/takedown`, body);
}

/**
 * 项目问责详情（BC1）：auditReason + lastAction（APPROVE/REJECT/TAKEDOWN/DELETE/null）
 * + statusOrigin（status=2 时推导：TAKEDOWN 事后下架 / 否则 REJECT 评审驳回）
 * + lastActionTime/lastActorId。仅 role=3；已删除项目 1004。
 */
export async function getProjectDetail(id: number | string) {
  return requestClient.get(`/admin/projects/${id}`);
}

/**
 * 逻辑删除项目（AY2）：任意未删除状态可删，级联步骤/材料并退役孤儿媒体；
 * 理由必填（≤300 字，query 参数），落 admin_operation_log（project.delete）。
 */
export async function deleteProject(id: number | string, reason: string) {
  return requestClient.delete(`/admin/projects/${id}`, { params: { reason } });
}

export async function getCareReminders(
  params: ZhiyuAdminApi.CareRemindersParams,
) {
  const res = await requestClient.get<{
    total: number;
    pageNum: number;
    pageSize: number;
    records: ZhiyuAdminApi.CareReminder[];
  }>('/admin/care-reminders', { params });
  return { ...res, total: Number(res.total) };
}

export async function getDashboardStats() {
  return requestClient.get<ZhiyuAdminApi.DashboardStats>(
    '/admin/dashboard/stats',
  );
}

/**
 * AI 审核辅助建议（BL，POST /api/admin/{works|comments}/{id}/ai-suggest，仅 role=3）。
 * 按需调用（点击才触发，真实模型调用有费用）；available=false 为正常降级（note 原文展示），
 * 频控超限（400/1001）是唯一显式错误。disclaimer 固定下发、前端必须常驻展示。
 */
export interface AiSuggestion {
  available: boolean;
  tendency?: 'HAS_CONCERNS' | 'NO_OBVIOUS_ISSUE' | 'UNCERTAIN';
  riskPoints?: Array<{ location: string; note: string }>;
  uncertainty?: string;
  note?: string;
  disclaimer: string;
}

export async function getWorkAiSuggestion(id: string) {
  // 本地模型演示兜底延迟 7-39s（docs/ollama-demo-fallback-20261107.md §5），
  // 仅本请求显式放宽超时，不影响全局 10s 默认
  return requestClient.post<AiSuggestion>(`/admin/works/${id}/ai-suggest`, undefined, {
    timeout: 60_000,
  });
}

export async function getCommentAiSuggestion(id: string) {
  // 同上：仅本请求显式 60s
  return requestClient.post<AiSuggestion>(`/admin/comments/${id}/ai-suggest`, undefined, {
    timeout: 60_000,
  });
}

/**
 * 心流/专注度量看板（阶段 1-2，2026-11-05；GET /api/admin/flow-metrics/dashboard，仅 role=3）。
 * 口径以 docs/flow-metrics-dashboard-literature-20261105.md §3/§9 为准：
 * 分母=step_leave 活跃秒数，分子=阈值后深度沉浸秒数（2026-11-05 起新语义）；
 * 人×周中位数 + p50/p90，不给均值大数字；只读/归档/待审会话剔除并在 exclusions 透明返回；
 * operational 分面不并线；unavailable[] 如实列出不可得项，不得用 0 冒充。
 */
export interface FlowMetricsDashboard {
  range: { days: number; from: string; to: string };
  generatedAt: string;
  /** 指标名由后端效度校准决定：「心流占时」或「专注停留占比」（insufficient/ρ<0.3 时后者） */
  metricLabel: string;
  calibration: {
    method: string;
    threshold: number;
    minSamples: number;
    sampleSize: number;
    status: 'insufficient' | 'ok';
    rho?: number | null;
  };
  selfReport: {
    sampled: number;
    answered: number;
    skipped: number;
    noResponse: number;
    responseRate?: number | null;
  };
  totals: {
    sessionsInRange: number;
    inProgressSessions: number;
    excludedReadOnly: number;
    excludedPendingProject: number;
    excludedNoEvents: number;
    includedSessions: number;
    activeSeconds: number;
    flowSeconds: number;
    activeUsers: number;
  };
  /** 剔除明细（雪花 ID 为字符串；限量返回，总数看 totals） */
  exclusions: Array<{
    sessionId: string;
    projectId?: string | null;
    reason: 'pending_project' | 'read_only' | string;
    readOnlySince?: string | null;
  }>;
  distribution: {
    personWeeks: number;
    p50?: number | null;
    p90?: number | null;
    buckets: Array<{ index: number; min: number; max?: number | null; count: number }>;
  };
  weekly: Array<{
    weekStart: string;
    activeUsers: number;
    sessionCount: number;
    medianRatio?: number | null;
    p25?: number | null;
    p75?: number | null;
    activeSeconds: number;
    flowSeconds: number;
  }>;
  facets: {
    byLevel: FlowMetricsFacetCell[];
    byDurationBucket: FlowMetricsFacetCell[];
    byTakedownKind: FlowMetricsFacetCell[];
  };
  /** 行为信号计数（区间内全量；无数据的类型为 0——这是计数，不是冒充有数据） */
  events: Record<string, number>;
  /** 口径变更点（变更前后数据不得跨期比较） */
  definitionChanges: Array<{ date: string; key: string; note: string }>;
  unavailable: Array<{ key: string; label: string; reason: string }>;
}

export interface FlowMetricsFacetCell {
  key: string;
  sessions: number;
  users: number;
  p50?: number | null;
  p90?: number | null;
  activeSeconds: number;
  flowSeconds: number;
  /** 单元去重用户数 <10 → 前端标灰（样本稀疏，中位数不稳定） */
  sparse: boolean;
}

export async function getFlowMetricsDashboard(params?: {
  from?: string;
  to?: string;
}) {
  return requestClient.get<FlowMetricsDashboard>('/admin/flow-metrics/dashboard', {
    params,
  });
}
