import { defineStore } from 'pinia'

const TOKEN_KEY = 'token'

const B64_CHARS =
	'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

/** base64 / base64url → 二进制字符串。不依赖 atob，小程序端也能跑。 */
function base64ToBinary(input) {
	const str = String(input).replace(/-/g, '+').replace(/_/g, '/')
	let out = ''
	let buffer = 0
	let bits = 0
	for (let i = 0; i < str.length; i++) {
		const ch = str.charAt(i)
		if (ch === '=') break
		const idx = B64_CHARS.indexOf(ch)
		if (idx === -1) continue
		buffer = (buffer << 6) | idx
		bits += 6
		if (bits >= 8) {
			bits -= 8
			out += String.fromCharCode((buffer >> bits) & 0xff)
		}
	}
	return out
}

/** 二进制字符串 → UTF-8 文本。 */
function binaryToUtf8(binary) {
	let out = ''
	for (let i = 0; i < binary.length; ) {
		const c = binary.charCodeAt(i)
		if (c < 0x80) {
			out += String.fromCharCode(c)
			i += 1
		} else if (c < 0xe0) {
			out += String.fromCharCode(((c & 0x1f) << 6) | (binary.charCodeAt(i + 1) & 0x3f))
			i += 2
		} else if (c < 0xf0) {
			out += String.fromCharCode(
				((c & 0x0f) << 12) |
					((binary.charCodeAt(i + 1) & 0x3f) << 6) |
					(binary.charCodeAt(i + 2) & 0x3f)
			)
			i += 3
		} else {
			const cp =
				((c & 0x07) << 18) |
				((binary.charCodeAt(i + 1) & 0x3f) << 12) |
				((binary.charCodeAt(i + 2) & 0x3f) << 6) |
				(binary.charCodeAt(i + 3) & 0x3f)
			const s = cp - 0x10000
			out += String.fromCharCode(0xd800 + (s >> 10), 0xdc00 + (s & 0x3ff))
			i += 4
		}
	}
	return out
}

/**
 * 解析 JWT 的 payload —— **只解码，不校验签名**。
 * 仅用于路由跳转这类展示层判断，不是安全边界，后端仍然独立鉴权。
 */
export function decodeJwtPayload(token) {
	if (!token || typeof token !== 'string') return null
	const parts = token.split('.')
	if (parts.length !== 3) return null
	try {
		return JSON.parse(binaryToUtf8(base64ToBinary(parts[1])))
	} catch (e) {
		return null
	}
}

/** 从 token 里读角色码；读不到返回 null。 */
export function decodeRoleFromToken(token) {
	const payload = decodeJwtPayload(token)
	if (!payload || payload.role === undefined || payload.role === null) return null
	const role = Number(payload.role)
	return Number.isNaN(role) ? null : role
}

function readToken() {
	try {
		return uni.getStorageSync(TOKEN_KEY) || ''
	} catch (e) {
		return ''
	}
}

export const useUserStore = defineStore('user', {
	state: () => ({
		token: readToken(),
		userInfo: null,
	}),

	getters: {
		isLogin: (state) => !!state.token,
		// 角色码：0 学生 / 1 传承人 / 2 咨询师 / 3 管理员（AGENTS.md §3.1）
		role: (state) => decodeRoleFromToken(state.token),
	},

	actions: {
		setToken(t) {
			this.token = t || ''
			try {
				if (this.token) {
					uni.setStorageSync(TOKEN_KEY, this.token)
				} else {
					uni.removeStorageSync(TOKEN_KEY)
				}
			} catch (e) {
				/* 存储不可用时只保留内存态 */
			}
		},

		/**
		 * 归一化用户信息：/api/auth/login 返回 userId，/api/auth/me 返回 id，
		 * 两个接口字段名不一致，这里统一成 userId。
		 */
		setUserInfo(info) {
			if (!info) {
				this.userInfo = null
				return
			}
			this.userInfo = {
				userId: info.userId != null ? info.userId : info.id != null ? info.id : null,
				username: info.username || '',
				nickname: info.nickname || '',
				school: info.school || '',
				role: info.role != null ? Number(info.role) : null,
			}
		},

		/** 只清凭证不跳转 —— 跳转统一由 utils/request.js 负责，避免跳转逻辑两处开花。 */
		clearAuth() {
			this.token = ''
			this.userInfo = null
			try {
				uni.removeStorageSync(TOKEN_KEY)
			} catch (e) {
				/* 忽略 */
			}
		},

		logout() {
			this.clearAuth()
			uni.reLaunch({ url: '/pages/login/index' })
		},

		decodeRole() {
			return decodeRoleFromToken(this.token)
		},
	},
})
