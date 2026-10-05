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
