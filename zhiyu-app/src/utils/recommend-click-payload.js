/**
 * 推荐点击事件的纯数据构造，便于在 Node 测试中直接验证。
 * ID 一律转成字符串：雪花 ID 不能经过 Number()。
 * eventType（AM2）：缺省 CLICK（正反馈，报文不带该字段，与旧结构一致）；
 * DISMISS（不感兴趣，负反馈）时显式携带。
 */
export function buildRecommendationClickPayload(recommendationId, projectId, source, eventType) {
	if (!recommendationId || !projectId) return null
	if (source !== 'HOME' && source !== 'RECOMMEND') return null
	const payload = {
		recommendationId: String(recommendationId),
		projectId: String(projectId),
		source,
	}
	if (eventType === 'DISMISS') payload.eventType = 'DISMISS'
	return payload
}
