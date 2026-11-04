/**
 * useCompanionNudge —— 小织「按需出现」的频次闸门（F2）。
 *
 * 规则：同一 quotaKey 每天最多出现 maxPerDay 次，跨天自动重置。
 * 纯本地存储（zy-nudge-quota），不上报、不与账号绑定。
 */

const QUOTA_KEY = 'zy-nudge-quota'
const PENDING_KEY = 'zy-nudge-pending'

function readLog() {
	try {
		const log = uni.getStorageSync(QUOTA_KEY)
		if (log && log.date && log.counts) return log
	} catch (e) { /* 存储不可用时按空记录 */ }
	return { date: '', counts: {} }
}

function today() {
	return new Date().toLocaleDateString('sv-SE')
}

export function useCompanionNudge() {
	/** 是否允许本时机出现（不消耗额度） */
	function canShow(quotaKey, maxPerDay = 2) {
		const log = readLog()
		if (log.date !== today()) return true
		return (log.counts[quotaKey] || 0) < maxPerDay
	}

	/** 记录一次出现（消耗额度） */
	function markShown(quotaKey) {
		const log = readLog()
		const counts = log.date === today() ? log.counts : {}
		counts[quotaKey] = (counts[quotaKey] || 0) + 1
		try {
			uni.setStorageSync(QUOTA_KEY, { date: today(), counts })
		} catch (e) { /* 存储不可用时只在本次会话内生效，从略 */ }
	}

	/** 记录一条「下一页面再出现」的陪伴（R3 时机③：创作完成后回首页出现） */
	function scheduleNudge(quotaKey, text) {
		try {
			uni.setStorageSync(PENDING_KEY, { quotaKey, text })
		} catch (e) { /* 从略 */ }
	}

	/** 取出并清除待出现的陪伴（无则 null） */
	function takePendingNudge() {
		try {
			const p = uni.getStorageSync(PENDING_KEY)
			if (p && p.quotaKey) {
				uni.removeStorageSync(PENDING_KEY)
				return p
			}
		} catch (e) { /* 从略 */ }
		return null
	}

	return { canShow, markShown, scheduleNudge, takePendingNudge }
}
