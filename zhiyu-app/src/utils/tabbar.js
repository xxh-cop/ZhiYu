export const TAB_ITEMS = [
	{ path: '/pages/home/index', text: '首页' },
	{ path: '/pages/work/list', text: '社区' },
	{ path: '/pages/start/index', text: '手作' },
	{ path: '/pages/group/list', text: '小组' },
	{ path: '/pages/profile/index', text: '我的' },
]

export function normalizeRoute(route) {
	if (!route) return ''
	const value = String(route)
	return value.startsWith('/') ? value : '/' + value
}

export function currentTabRoute() {
	const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
	const current = pages.length ? pages[pages.length - 1] : null
	return normalizeRoute(current && current.route)
}

export function switchTabTo(path) {
	uni.switchTab({
		url: path,
		fail: () => {
			uni.reLaunch({ url: path })
		},
	})
}

export function isTabRoute(path) {
	return TAB_ITEMS.some((item) => item.path === path)
}
