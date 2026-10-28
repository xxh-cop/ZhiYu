import { API_BASE_URL, TOKEN_KEY } from './config'
import { buildRecommendationClickPayload } from './recommend-click-payload'

/**
 * 静默上报推荐点击。
 *
 * 这是辅助埋点：不等待、不弹 Toast、不触发登录跳转；失败不影响用户进入详情。
 */
export function trackRecommendationClick({ recommendationId, projectId, source }) {
	const payload = buildRecommendationClickPayload(recommendationId, projectId, source)
	if (!payload) return

	let token = ''
	try {
		token = uni.getStorageSync(TOKEN_KEY) || ''
	} catch (e) {
		return
	}
	if (!token) return

	try {
		uni.request({
			url: API_BASE_URL + '/api/craft/recommend/click',
			method: 'POST',
			timeout: 5000,
			data: payload,
			header: {
				'Content-Type': 'application/json',
				Authorization: 'Bearer ' + token,
			},
			success: () => {},
			fail: () => {},
		})
	} catch (e) {
		/* 埋点失败静默 */
	}
}
