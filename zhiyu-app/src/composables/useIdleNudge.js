import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { useCompanionNudge } from './useCompanionNudge'

/**
 * useIdleNudge —— 按需陪伴时机②（R3）：页内长停无操作后小织出现一次。
 *
 * 仅在「正在做事」的页面启用（对话页 / 创作页）；列表浏览页不启用。
 * 任何交互（触摸 / 点击 / H5 键盘）重置计时；每次进入页面最多出现一次；
 * 频次闸门复用 useCompanionNudge（同一 quotaKey 每天 ≤2 次）。
 */
export function useIdleNudge({ quotaKey = 'page-idle', idleMs = 120000, onTrigger }) {
	const quota = useCompanionNudge()
	let timer = null
	let fired = false

	function arm() {
		clearTimeout(timer)
		if (fired) return
		timer = setTimeout(() => {
			if (!quota.canShow(quotaKey)) return
			quota.markShown(quotaKey)
			fired = true
			onTrigger && onTrigger()
		}, idleMs)
	}
	function disarm() {
		clearTimeout(timer)
	}

	onShow(() => {
		fired = false
		arm()
		// #ifdef H5
		window.addEventListener('keydown', arm)
		// #endif
	})
	onHide(() => {
		disarm()
		// #ifdef H5
		window.removeEventListener('keydown', arm)
		// #endif
	})
	onUnload(() => {
		disarm()
		// #ifdef H5
		window.removeEventListener('keydown', arm)
		// #endif
	})

	return { arm, disarm }
}
