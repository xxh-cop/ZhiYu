/**
 * 「展示受限」（审核软档）作者侧工具（2026-11-07 处置链）。
 *
 * 数据源：GET /api/works/{id} 的 softDemoteUntil（仅作者本人下发，他人与列表缺省不下发；
 * null 或未出现 = 未在保护期）。处置链开关关闭时后端从不下发，前端自然零渲染。
 *
 * 文案纪律：不出现「违规 / 处罚 / 降权 / 折叠 / 限流」；基调是保护性说明。
 */

/** 未抽中窗口 72h；抽中/已申诉的硬上界为 30 天。剩余超过该阈值即视为「人工复核中」。 */
const REVIEW_THRESHOLD_HOURS = 96

/** 解析后端 LocalDateTime（ISO 带 T；兼容空格分隔），失败返回 null。 */
export function parseSoftUntil(value) {
	if (!value) return null
	const text = String(value).trim()
	if (!text) return null
	const date = new Date(text.includes('T') ? text : text.replace(' ', 'T'))
	return Number.isNaN(date.getTime()) ? null : date
}

/** 是否处于展示受限保护期（字段存在且截止时间在将来）。 */
export function softActive(value) {
	const until = parseSoftUntil(value)
	return !!until && until.getTime() > Date.now()
}

/**
 * 保护期剩余时间的保护性说明（仅在 softActive 为 true 时调用）：
 * - 未抽中窗口（≤96h）：「约 X 小时后自动恢复 / 约 X 天后自动恢复」；
 * - 抽中复核 / 已申诉（30 天上界）：不承诺具体时刻，说明复核完成后恢复。
 */
export function softRemainText(value) {
	const until = parseSoftUntil(value)
	if (!until) return ''
	const hours = Math.ceil((until.getTime() - Date.now()) / 3600000)
	if (hours <= 0) return ''
	if (hours > REVIEW_THRESHOLD_HOURS) return '正在人工复核，复核完成后恢复正常展示。'
	if (hours >= 48) return `约 ${Math.ceil(hours / 24)} 天后自动恢复正常展示。`
	return `约 ${hours} 小时后自动恢复正常展示。`
}
