/**
 * ttlCache —— 会话级短时缓存（X2）。
 * 用途：首页等高频进入页面对「秒级可容忍陈旧」的读取做 15–60s 去重，
 * 避免每次 onShow 都重复打同一接口。失败结果不缓存（下次自然重试）。
 * 新鲜度敏感的数据（如通知未读数）不走这里。
 */
const store = new Map()

/**
 * @param {string} key 缓存键
 * @param {number} ttlMs 有效毫秒
 * @param {() => Promise<any>} fetcher 真正的请求
 */
export async function cached(key, ttlMs, fetcher) {
	const hit = store.get(key)
	if (hit && hit.until > Date.now()) return hit.value
	const value = await fetcher()
	store.set(key, { value, until: Date.now() + ttlMs })
	return value
}

/** 事件失效：数据被写操作改变后主动清除相关键 */
export function invalidate(...keys) {
	keys.forEach((k) => store.delete(k))
}

/** 键约定（跨页面统一，避免散落字符串） */
export const TTL_KEYS = {
	authMe: 'auth-me',
	userStats: 'user-stats',
	craftSessionActive: 'craft-session-active',
	craftRecommend: 'craft-recommend',
}
