import type { UserInfo } from '@vben/types';

import { requestClient } from '#/api/request';

/**
 * 获取当前用户信息
 * 「织愈」后端接口是 /api/auth/me，返回 Result(data)
 * data 结构：{ id, username, nickname, role, school, avatarUrl, ... }
 * 需映射为 Vben 的 UserInfo 结构：
 *   userId ← id
 *   realName ← nickname
 *   roles ← ['ROLE_' + role]（与 JwtAuthFilter 的 authority 格式一致）
 */
export async function getUserInfoApi(): Promise<UserInfo> {
  const result = await requestClient.get<any>('/auth/me');

  const roleCode =
    result?.role !== undefined && result?.role !== null
      ? `ROLE_${result.role}`
      : 'ROLE_0';

  // 首页按角色分流：管理员落工作台，传承人 / 咨询师落活动与预约。
  // 工作台与数据中心已收紧为 ROLE_3，若非管理员仍按默认首页跳转会匹配到 404。
  const homePath =
    roleCode === 'ROLE_3' ? '/zhiyu/dashboard' : '/zhiyu/programs';

  return {
    // 保留其他字段以备将来使用
    ...result,
    userId: String(result?.id ?? result?.userId ?? ''),
    username: result?.username ?? '',
    realName: result?.nickname ?? result?.username ?? '',
    avatar: result?.avatarUrl ?? '',
    roles: [roleCode],
    homePath,
  } as UserInfo;
}

/**
 * 更新个人资料（批次 4.5）
 * PUT /api/user/profile
 */
export async function updateUserProfile(data: {
  nickname?: string;
  avatarUrl?: string;
  school?: string;
  intro?: string;
}) {
  return requestClient.put<UserInfo>('/user/profile', data);
}

/**
 * 修改密码（批次 4.5）
 * PUT /api/user/password
 */
export async function changePassword(data: {
  oldPassword: string;
  newPassword: string;
}) {
  return requestClient.put('/user/password', data);
}
