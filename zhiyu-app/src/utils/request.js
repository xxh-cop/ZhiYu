/**
 * uni.request 的统一封装。
 *
 * 约定（与 openapi/openapi.yaml 一致）：
 *  - 响应体一律形如 { code, message, data }；
 *  - code === 0 视为成功，Promise resolve 出来的是 data 本身；
 *  - code !== 0 视为业务失败，Promise reject 一个带 code/message 的 Error；
 *  - HTTP 401 或 body.code === 1002 视为登录失效：清本地凭证 + 回登录页。
 *
 * 不引入 axios —— uni.request 在 H5 与小程序端行为一致，已经够用。
 */
import { useUserStore } from '../stores/user'
import { API_BASE_URL, TOKEN_KEY, REQUEST_TIMEOUT } from './config.js'
import { safeWorkReturn } from './work-navigation'

const BASE_URL = API_BASE_URL

const LOGIN_PAGE = '/pages/login/index'

/** 统一错误类型：调用方靠 err.code 区分业务错误码。 */
export class RequestError extends Error {
	constructor(code, message) {
		super(message)
		this.name = 'RequestError'
		this.code = code
	}
}

function readToken() {
	try {
		return uni.getStorageSync(TOKEN_KEY) || ''
	} catch (e) {
		return ''
	}
}

function currentRoute() {
	const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
	return pages.length ? pages[pages.length - 1].route : ''
}

/**
 * 登录失效的统一出口：清 token + 跳登录页。
 * 已经在登录页时只清凭证不再跳转，否则会把自己正在显示的错误提示冲掉。
 */
function handleUnauthorized() {
	try {
		useUserStore().clearAuth()
	} catch (e) {
		try {
			uni.removeStorageSync(TOKEN_KEY)
		} catch (e2) {
			/* 存储不可用时忽略 */
		}
	}
	if (currentRoute() === 'pages/login/index') return
	const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
	const page = pages[pages.length - 1]
	const options = page?.options || page?.$page?.options || {}
	const next=safeWorkReturn('/'+currentRoute()+'?id='+options.id)
	uni.reLaunch({ url: LOGIN_PAGE+(next?'?next='+encodeURIComponent(next):'') })
}

/**
 * 发起一次请求。
 *
 * @param {object} options
 * @param {string} options.url      以 / 开头的接口路径，会拼在 BASE_URL 之后
 * @param {string} [options.method] 默认 GET
 * @param {any}    [options.data]   请求体 / query
 * @param {object} [options.header] 额外请求头
 * @param {boolean}[options.auth]   是否附带 Authorization，默认 true
 * @param {number} [options.timeout] 请求超时毫秒数，默认取 config.REQUEST_TIMEOUT（60000）；账号入口使用 15000
 * @returns {Promise<any>} resolve 业务 data
 */
export function request(options = {}) {
	const { url, method = 'GET', data, header = {}, auth = true, timeout = REQUEST_TIMEOUT } = options

	return new Promise((resolve, reject) => {
		const finalHeader = Object.assign({ 'Content-Type': 'application/json' }, header)

		if (auth) {
			const token = readToken()
			if (token) {
				finalHeader.Authorization = 'Bearer ' + token
			}
		}

		uni.request({
			url: BASE_URL + url,
			method,
			timeout,
			data,
			header: finalHeader,
			success: (res) => {
				const body = res.data

				// 登录失效：HTTP 401 与业务码 1002 后端都会出现，一并处理
				if (res.statusCode === 401 || (body && body.code === 1002)) {
					if (auth) handleUnauthorized()
					reject(
						new RequestError(1002, (body && body.message) || '未登录或登录已过期')
					)
					return
				}

				if (body && typeof body.code === 'number') {
					if (body.code === 0) {
						resolve(body.data)
					} else {
						reject(new RequestError(body.code, body.message || '请求失败'))
					}
					return
				}

				if (res.statusCode >= 200 && res.statusCode < 300) {
					resolve(body)
					return
				}

				reject(
					new RequestError(res.statusCode, '请求失败（HTTP ' + res.statusCode + '）')
				)
			},
			fail: (err) => {
				reject(new RequestError(-1, (err && err.errMsg) || '网络异常，请稍后重试'))
			},
		})
	})
}

export const http = {
	get: (url, options) => request(Object.assign({ url, method: 'GET' }, options)),
	post: (url, data, options) =>
		request(Object.assign({ url, method: 'POST', data }, options)),
	put: (url, data, options) =>
		request(Object.assign({ url, method: 'PUT', data }, options)),
	del: (url, options) => request(Object.assign({ url, method: 'DELETE' }, options)),
}

export default request
