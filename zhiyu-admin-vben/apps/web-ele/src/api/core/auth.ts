import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  export interface LoginParams {
    password?: string;
    username?: string;
  }

  export interface LoginResult {
    accessToken: string;
    userId?: number | string;
    nickname?: string;
    role?: number | string;
  }
}

/**
 * 登录
 * 「织愈」后端返回 Result(data)，data 结构为 { token, userId, nickname, role }
 * Vben 期望 accessToken 字段，此处做映射
 */
export async function loginApi(data: AuthApi.LoginParams) {
  const result = await requestClient.post<AuthApi.LoginResult>(
    '/auth/login',
    data,
  );
  return {
    ...result,
    accessToken: (result as any).token || result.accessToken || '',
  };
}

/**
 * 刷新 token
 * 「织愈」后端无此接口，preferences 中已关闭 enableRefreshToken，
 * 此函数不应被调用；保留以兼容 Vben 类型定义
 */
export async function refreshTokenApi() {
  throw new Error('Refresh token is not supported by ZhiYu backend');
}

/**
 * 登出
 * 「织愈」后端无此接口（JWT 无状态），直接 resolve
 */
export async function logoutApi() {
  return Promise.resolve();
}

/**
 * 获取权限码
 * 「织愈」后端无此接口，返回空数组（Vben 权限走 roles 判断）
 */
export async function getAccessCodesApi() {
  return [] as string[];
}
