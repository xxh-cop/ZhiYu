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

export async function updateWorkStatus(id: number, status: number) {
  return requestClient.put(`/admin/works/${id}/status`, { status });
}

export async function deleteWork(id: number) {
  return requestClient.delete(`/admin/works/${id}`);
}
