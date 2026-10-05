import { requestClient } from '#/api/request';

export namespace AdminUserApi {
  export interface UserListParams {
    page?: number;
    pageSize?: number;
    role?: number;
    status?: number;
    keyword?: string;
  }
  export interface UserListItem {
    id: number;
    username: string;
    nickname: string;
    avatarUrl?: string;
    school?: string;
    role: number;
    status: number;
    canContact: number;
    createTime: string;
  }
  export interface UserUpdateBody {
    nickname?: string;
    school?: string;
    avatarUrl?: string;
  }
}

export async function getUserList(params: AdminUserApi.UserListParams) {
  const res = await requestClient.get<{
    total: number;
    pageNum: number;
    pageSize: number;
    records: AdminUserApi.UserListItem[];
  }>('/admin/users', { params });
  return { ...res, total: Number(res.total) };
}

export async function getUserDetail(id: number) {
  return requestClient.get<AdminUserApi.UserListItem & { craftPreferences?: string }>(
    `/admin/users/${id}`,
  );
}

export async function updateUser(id: number, data: AdminUserApi.UserUpdateBody) {
  return requestClient.put(`/admin/users/${id}`, data);
}

export async function updateUserStatus(id: number, status: number) {
  return requestClient.put(`/admin/users/${id}/status`, { status });
}

export async function updateUserRole(id: number, role: number) {
  return requestClient.put(`/admin/users/${id}/role`, { role });
}

export async function deleteUser(id: number) {
  return requestClient.delete(`/admin/users/${id}`);
}
