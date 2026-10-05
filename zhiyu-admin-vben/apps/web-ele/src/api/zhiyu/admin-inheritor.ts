import { requestClient } from '#/api/request';

export namespace AdminInheritorApi {
  export interface InheritorListParams {
    page?: number;
    pageSize?: number;
    verified?: number;
    keyword?: string;
  }
  export interface InheritorListItem {
    id: number;
    userId: number;
    nickname: string;
    skill: string;
    honor?: string;
    verified: number;
    createTime: string;
  }
  export interface InheritorDetail extends InheritorListItem {
    intro?: string;
    school?: string;
  }
}

export async function getInheritorList(params: AdminInheritorApi.InheritorListParams) {
  const res = await requestClient.get<{
    total: number;
    pageNum: number;
    pageSize: number;
    records: AdminInheritorApi.InheritorListItem[];
  }>('/admin/inheritors', { params });
  return { ...res, total: Number(res.total) };
}

export async function getInheritorDetail(id: number) {
  return requestClient.get<AdminInheritorApi.InheritorDetail>(`/admin/inheritors/${id}`);
}

export async function verifyInheritor(id: number, verified: number) {
  return requestClient.put(`/admin/inheritors/${id}/verify`, { verified });
}

export async function updateInheritor(id: number, data: { skill?: string; intro?: string; honor?: string }) {
  return requestClient.put(`/admin/inheritors/${id}`, data);
}
