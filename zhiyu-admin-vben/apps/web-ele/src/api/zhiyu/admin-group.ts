import { requestClient } from '#/api/request';

export namespace AdminGroupApi {
  export interface GroupListParams {
    page?: number;
    pageSize?: number;
    status?: number;
    keyword?: string;
  }
  export interface GroupListItem {
    id: number;
    name: string;
    description?: string;
    memberCount: number;
    maxCount: number;
    status: number;
    createTime: string;
  }
}

export async function getGroupList(params: AdminGroupApi.GroupListParams) {
  const res = await requestClient.get<{
    total: number;
    pageNum: number;
    pageSize: number;
    records: AdminGroupApi.GroupListItem[];
  }>('/admin/groups', { params });
  return { ...res, total: Number(res.total) };
}

export async function getGroupDetail(id: number) {
  return requestClient.get<AdminGroupApi.GroupListItem>(`/admin/groups/${id}`);
}

export async function updateGroupStatus(id: number, status: number) {
  return requestClient.put(`/admin/groups/${id}/status`, { status });
}

export async function deleteGroup(id: number) {
  return requestClient.delete(`/admin/groups/${id}`);
}
