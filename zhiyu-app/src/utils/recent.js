/**
 * 最近访问（仅本机、仅路由与页面名，不存任何内容）。
 * 用途：首页「继续上次」与「最近」快捷入口，把深层功能提回一层。
 */
const KEY = 'zy-recent-v1'
const MAX = 4

const LABELS = [
	['/pages/emotion/chat', '玄墨倾听室'],
	['/pages/emotion/notes', '心情便签'],
	['/pages/emotion/history', '倾听记录'],
	['/pages/craft/session', '专注创作'],
	['/pages/craft/recommend', '手作推荐'],
	['/pages/craft/detail', '手作详情'],
	['/pages/craft/reflection', '创作回顾'],
	['/pages/materials/kits', '材料包'],
	['/pages/materials/index', '材料图鉴'],
	['/pages/inheritor/story', '传承人故事'],
	['/pages/inheritor/list', '传承人'],
	['/pages/inheritor/detail', '传承人'],
	['/pages/group/detail', '小组'],
	['/pages/group/list', '互助小组'],
	['/pages/work/detail', '作品'],
	['/pages/work/mine', '我的作品'],
	['/pages/work/favorites', '收藏'],
	['/pages/work/list', '作品'],
	['/pages/journey/index', '成长旅程'],
	['/pages/music/index', '声景'],
	['/pages/mindfulness/index', '正念'],
	['/pages/practice/short', '短练习'],
	['/pages/assessment/index', '情绪自评'],
	['/pages/drawing/index', '线稿'],
	['/pages/settings/memory', '小织的记忆'],
	['/pages/settings/index', '设置'],
	['/pages/notification/index', '通知'],
	['/pages/support/index', '支持'],
	['/sub-heritage/pages/heritage/studio', '共创工作室'],
	['/sub-heritage/pages/heritage/gallery', '非遗纹样馆'],
	['/sub-programs/pages/programs/index', '团体活动'],
	['/sub-programs/pages/programs/conversations', '预约交流'],
	['/sub-programs/pages/programs/feedback', '活动反馈'],
]

export function routeLabel(path) {
	const clean = String(path || '').split('?')[0]
	const hit = LABELS.find(([prefix]) => clean === prefix || clean.startsWith(prefix))
	return hit ? hit[1] : ''
}

function read() {
	try {
		const raw = uni.getStorageSync(KEY)
		const list = typeof raw === 'string' ? JSON.parse(raw) : raw
		return Array.isArray(list) ? list.filter((x) => x && x.path && x.label) : []
	} catch (e) {
		return []
	}
}

export function recordVisit(path, label) {
	if (!path || !label) return
	const clean = String(path).split('?')[0]
	// 首页、登录、引导不进入「最近」
	if (clean === '/pages/home/index' || clean.startsWith('/pages/login') || clean.startsWith('/pages/register') || clean.startsWith('/pages/onboarding')) return
	const list = read().filter((x) => String(x.path).split('?')[0] !== clean)
	list.unshift({ path, label, ts: Date.now() })
	try { uni.setStorageSync(KEY, JSON.stringify(list.slice(0, MAX))) } catch (e) { /* 存储不可用时静默 */ }
}

export function getRecent() {
	return read()
}
