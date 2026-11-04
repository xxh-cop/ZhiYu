import { requestClient } from '#/api/request';

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
export async function refreshTokenApi(): Promise<{ data: string }> {
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

/**
 * 申请密码重置令牌（忘记密码），POST /api/auth/password/forgot（匿名放行）。
 * 服务端语义（不自造状态）：
 * - 账号不存在也返回成功（不泄露账号是否存在）；
 * - 发送通道未配置时返回 3001「忘记密码功能暂未开放」——前端必须如实展示，不假装成功。
 */
export async function forgotPasswordApi(username: string) {
  return requestClient.post('/auth/password/forgot', { username });
}

/**
 * 用一次性令牌重置密码，POST /api/auth/password/reset（匿名放行）。
 * 令牌无效/过期/已使用返回 3001「重置令牌无效或已过期，请重新申请」。
 */
export async function resetPasswordApi(token: string, newPassword: string) {
  return requestClient.post('/auth/password/reset', { token, newPassword });
}
