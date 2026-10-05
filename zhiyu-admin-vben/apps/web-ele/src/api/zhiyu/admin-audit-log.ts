import { requestClient } from '#/api/request';

export namespace AdminAuditLogApi {
  export interface AuditLogListParams {
    page?: number;
    pageSize?: number;
    scene?: string;
    action?: string;
    startTime?: string;
    endTime?: string;
  }
  export interface AuditLogItem {
    id: number;
    scene: string;
    action: string;
    reason?: string;
    createTime: string;
  }
  export interface AuditLogDetail extends AuditLogItem {
    rawInput?: string;
    aiOutput?: string;
  }
}

export async function getAuditLogList(params: AdminAuditLogApi.AuditLogListParams) {
  const res = await requestClient.get<{
    total: number;
    pageNum: number;
    pageSize: number;
    records: AdminAuditLogApi.AuditLogItem[];
  }>('/admin/audit-logs', { params });
  return { ...res, total: Number(res.total) };
}

export async function getAuditLogDetail(id: number) {
  return requestClient.get<AdminAuditLogApi.AuditLogDetail>(
    `/admin/audit-logs/${id}`,
  );
}
