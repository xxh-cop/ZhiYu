/**
 * 推荐点击事件的纯数据构造，便于在 Node 测试中直接验证。
 * ID 一律转成字符串：雪花 ID 不能经过 Number()。
 */
export function buildRecommendationClickPayload(recommendationId, projectId, source) {
	if (!recommendationId || !projectId) return null
	if (source !== 'HOME' && source !== 'RECOMMEND') return null
	return {
		recommendationId: String(recommendationId),
		projectId: String(projectId),
		source,
	}
}
