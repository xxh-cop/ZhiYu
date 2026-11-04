import { reactive, ref } from 'vue'

/**
 * useToast —— 轻提示队列（计划 §3.2），替代散落的 uni.showToast 一次性调用。
 *
 * 用法：
 *  1) 页面挂载一次 <Toast />（组件）；
 *  2) 脚本里 const toast = useToast()，toast.show('已收藏') / toast.success(...) / toast.care(...)。
 *
 * 模块级单例队列：同页多来源调用共用一条队列；跨页面各自挂载各自的 <Toast />。
 *
 * notify()（composables 专用）：页面已挂载 <Toast /> 时走统一队列，
 * 未挂载时回退 uni.showToast —— 跨页面 composable（useVoiceInput 等）无需
 * 感知宿主页面是否挂载，提示在任何页面都不丢。
 */
const state = reactive({ items: [] })
const mountedCount = ref(0)
let seed = 0

/** 由 Toast.vue 在挂载/卸载时调用（组件内部使用，业务代码勿调）。 */
export function _trackToastMount(delta) {
	mountedCount.value = Math.max(0, mountedCount.value + delta)
}

export function useToast() {
	function show(message, { tone = 'default', duration = 2400 } = {}) {
		if (!message) return
		const id = ++seed
		// 同内容去重：避免快速重复点击堆叠相同提示
		const existing = state.items.find((item) => item.message === message)
		if (existing) {
			clearTimeout(existing.timer)
			existing.timer = setTimeout(() => dismiss(existing.id), duration)
			return
		}
		const timer = setTimeout(() => dismiss(id), duration)
		state.items.push({ id, message: String(message), tone, timer })
		if (state.items.length > 3) dismiss(state.items[0].id)
	}
	function dismiss(id) {
		const index = state.items.findIndex((item) => item.id === id)
		if (index >= 0) {
			clearTimeout(state.items[index].timer)
			state.items.splice(index, 1)
		}
	}
	return {
		show,
		dismiss,
		success: (message, options) => show(message, { ...options, tone: 'success' }),
		care: (message, options) => show(message, { ...options, tone: 'care' }),
	}
}

/**
 * composables 的统一提示出口：有挂载走队列（统一视觉），无挂载回退原生 toast（不丢提示）。
 * @param {string} message
 * @param {{ tone?: 'default'|'success'|'care', duration?: number }} [options]
 */
export function notify(message, options = {}) {
	if (mountedCount.value > 0) {
		useToast().show(message, options)
		return
	}
	uni.showToast({ title: String(message || ''), icon: 'none', duration: options.duration || 2400 })
}

export { state as toastState }
