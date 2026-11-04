import { reactive } from 'vue'
import { http } from '../utils/request'

/**
 * useNotifications —— 通知未读数的节流获取（K1）。
 *
 * 规则：
 * - 只在页面 onShow 时刷新（refreshUnread），不在每次渲染请求；
 * - 15 秒最小间隔节流；已读操作后用 force 立即校准；
 * - 服务端未读数本身有 5 秒缓存，页面隐藏期间不轮询，自然不做过期承诺；
 * - 获取失败静默处理——通知永远不阻塞页面。
 */
const state = reactive({ unread: 0, loaded: false, fetching: false, lastFetch: 0 })
const MIN_INTERVAL = 15000

export function useNotifications() {
	async function refreshUnread(force = false) {
		if (state.fetching) return
		if (!force && Date.now() - state.lastFetch < MIN_INTERVAL) return
		state.fetching = true
		try {
			const data = await http.get('/api/notifications/unread-count')
			state.unread = Number(data?.unread) || 0
			state.lastFetch = Date.now()
			state.loaded = true
		} catch (e) {
			/* 静默：通知角标失败不阻塞页面 */
		} finally {
			state.fetching = false
		}
	}
	function setUnread(n) {
		state.unread = Math.max(0, Number(n) || 0)
		state.lastFetch = Date.now()
	}
	return { state, refreshUnread, setUnread }
}
