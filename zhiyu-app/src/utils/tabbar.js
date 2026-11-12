export const TAB_ITEMS = [
	{ path: '/pages/home/index', text: '首页', icon: '/static/tabbar/home-normal.png', activeIcon: '/static/tabbar/home-active.png' },
	{ path: '/pages/work/list', text: '社区', icon: '/static/tabbar/community-normal.png', activeIcon: '/static/tabbar/community-active.png' },
	{ path: '/pages/start/index', text: '手作', center: true, icon: '/static/tabbar/start-normal.png', activeIcon: '/static/tabbar/start-active.png' },
	{ path: '/pages/group/list', text: '小组', icon: '/static/tabbar/group-normal.png', activeIcon: '/static/tabbar/group-active.png' },
	{ path: '/pages/profile/index', text: '我的', icon: '/static/tabbar/profile-normal.png', activeIcon: '/static/tabbar/profile-active.png' },
]

export const CENTER_PATH = '/pages/start/index'

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
