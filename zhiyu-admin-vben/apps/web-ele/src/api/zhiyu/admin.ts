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
 * 下架已上架项目（AY1）：仅 status 1→2，理由必填（≤300 字），
 * 落 craft_project.audit_reason 并通知传承人；非上架态返回 3001（原文上屏，不吞）。
 */
export async function takedownProject(id: number | string, reason: string) {
  return requestClient.put(`/admin/projects/${id}/takedown`, { reason });
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
