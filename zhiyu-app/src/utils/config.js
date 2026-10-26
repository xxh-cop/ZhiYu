/**
 * 织愈学生端集中配置。
 *
 * 接口地址由 Vite 环境变量 `VITE_API_BASE_URL` 提供，H5 与微信小程序共用同一份配置：
 * - 开发：zhiyu-app/.env.development（本机 localhost；微信真机需改成电脑局域网 IP）
 * - 生产：部署 / CI 注入 `VITE_API_BASE_URL`，模板见 zhiyu-app/.env.production.example
 *
 * 生产构建缺少该变量时，模块加载即抛出明确错误，绝不静默回落到 localhost。
 * 注意：Vite 只加载 `.env.[mode]`，`.env.development` 不会进入 production build。
 */

/** 仅开发模式（import.meta.env.DEV）生效的本机兜底；生产分支不会走到这里。 */
const DEV_FALLBACK_API_BASE_URL = 'http://localhost:8080'

function normalizeBaseUrl(value) {
	if (typeof value !== 'string') return ''
	return value.trim().replace(/\/+$/, '')
}

function resolveApiBaseUrl() {
	const configured = normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL)
	if (configured) return configured
	if (import.meta.env.DEV === true || import.meta.env.MODE === 'development') {
		return DEV_FALLBACK_API_BASE_URL
	}
	throw new Error(
		'[织愈] 未配置 VITE_API_BASE_URL：生产构建必须提供后端接口地址，' +
			'请参考 zhiyu-app/.env.production.example 设置环境变量后重新构建。'
	)
}

/**
 * 后端接口根地址（已去掉末尾斜杠）。
 * 调用方统一用 `API_BASE_URL + '/api/...'` 拼接。
 */
export const API_BASE_URL = resolveApiBaseUrl()

/**
 * 本地存储中 JWT 的 key。
 * request.js、stores/user.js、页面与上传逻辑统一引用；保持 `'token'` 以兼容已登录用户。
 */
export const TOKEN_KEY = 'token'

/**
 * 普通 uni.request 的默认超时（毫秒）。
 * 与 request.js 的默认 timeout 共用：兼容 AI 非流式接口与普通接口；
 * 登录等账号入口仍显式使用 15000，视频上传显式使用 180000。
 * SSE（src/utils/sse.js）不经过 uni.request，不受此值限制。
 */
export const REQUEST_TIMEOUT = 60000
