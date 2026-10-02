import axios from 'axios'

/** localStorage 里存 token 的 key（与 stores/user.js 共用） */
export const TOKEN_KEY = 'token'

/** 默认空字符串 = 走同源相对路径，由 vite proxy（开发）/ Nginx（生产）转发到后端 */
const BASE_URL = (import.meta.env && import.meta.env.VITE_API_BASE_URL) || ''

export class RequestError extends Error {
  constructor(code, message, httpStatus) {
    super(message)
    this.name = 'RequestError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

/** 401/1002：清 token + 回登录页（动态 import 规避循环依赖） */
async function handleUnauthorized(message) {
  localStorage.removeItem(TOKEN_KEY)
  const { default: router } = await import('../router')
  if (router.currentRoute.value.path !== '/login') {
    router.replace({ path: '/login' })
  }
  ElMessage.error(message || '登录已过期，请重新登录')
  return Promise.reject(new RequestError(1002, message || '登录已过期，请重新登录', 401))
}

function handleForbidden(message) {
  const msg = message || '无访问权限'
  ElMessage.error(msg)
  return Promise.reject(new RequestError(1003, msg, 403))
}

const http = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 非统一响应体（理论上不会走到）原样返回
    if (!body || typeof body !== 'object' || typeof body.code === 'undefined') {
      return body
    }
    if (body.code === 0) {
      return body.data
    }
    if (body.code === 1002) {
      return handleUnauthorized(body.message)
    }
    if (body.code === 1003) {
      return handleForbidden(body.message)
    }
    const message = body.message || `请求失败（code=${body.code}）`
    ElMessage.error(message)
    return Promise.reject(new RequestError(body.code, message, response.status))
  },
  (error) => {
    const response = error.response
    const body = response && response.data
    const status = response ? response.status : 0
    const code =
      body && typeof body.code !== 'undefined' ? body.code : status

    if (status === 401 || code === 1002) {
      return handleUnauthorized(body && body.message)
    }
    if (status === 403 || code === 1003) {
      return handleForbidden(body && body.message)
    }
    const message =
      (body && body.message) || error.message || '网络异常，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(new RequestError(code, message, status))
  },
)

export default http
