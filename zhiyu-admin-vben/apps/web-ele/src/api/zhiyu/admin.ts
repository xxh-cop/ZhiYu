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
  return requestClient.post<AiSuggestion>(`/admin/works/${id}/ai-suggest`);
}

export async function getCommentAiSuggestion(id: string) {
  return requestClient.post<AiSuggestion>(`/admin/comments/${id}/ai-suggest`);
}
