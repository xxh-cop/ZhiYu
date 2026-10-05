import http from '../utils/request'

/**
 * 认证相关 API 封装。
 *
 * 对应后端 com.zhiyu.modules.auth.controller.AuthController。
 * http 拦截器已统一处理 code=0 返回 data、401/403 跳转、错误码 ElMessage。
 */

/**
 * 登录。
 * @param {{ username: string, password: string }} data
 * @returns {Promise<{ token: string, userId: number, role: number, nickname: string }>}
 */
export function login(data) {
  return http.post('/api/auth/login', data)
}

/**
 * 获取当前登录用户信息。
 * @returns {Promise<{ userId: number, role: number, nickname: string, ... }>}
 */
export function getCurrentUser() {
  return http.get('/api/auth/me')
}
