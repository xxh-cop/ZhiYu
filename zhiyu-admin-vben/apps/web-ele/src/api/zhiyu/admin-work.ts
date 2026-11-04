import { requestClient } from '#/api/request';

export namespace AdminWorkApi {
  export interface WorkListParams {
    page?: number;
    pageSize?: number;
    status?: number;
    visible?: number;
    keyword?: string;
  }
  export interface WorkListItem {
    id: number;
    userId: number;
    authorName: string;
    title: string;
    coverUrl?: string;
    visible: number;
    anonymous: number;
    likeCount: number;
    status: number;
    createTime: string;
  }
  export interface WorkDetail extends WorkListItem {
    message?: string;
    projectId?: number;
    sessionId?: number;
  }
}

export async function getWorkList(params: AdminWorkApi.WorkListParams) {
  const res = await requestClient.get<{
    total: number;
    pageNum: number;
    pageSize: number;
    records: AdminWorkApi.WorkListItem[];
  }>('/admin/works', { params });
  return { ...res, total: Number(res.total) };
}

export async function getWorkDetail(id: number) {
  return requestClient.get<AdminWorkApi.WorkDetail>(`/admin/works/${id}`);
}

/**
 * 作品状态变更。I1（2026-11-04）起：status=2（下架/驳回）必须携带 reason，
 * 服务端缺失返回 1001；reason 落库 work.audit_reason 并随通知送达作者。
 */
export async function updateWorkStatus(id: number, status: number, reason?: string) {
  return requestClient.put(`/admin/works/${id}/status`, { status, reason });
}

export async function deleteWork(id: number) {
  return requestClient.delete(`/admin/works/${id}`);
}
