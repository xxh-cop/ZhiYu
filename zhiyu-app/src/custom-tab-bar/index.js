/**
 * 微信小程序自定义 tabBar（原生组件）。
 * 必须输出为 custom-tab-bar/index.{js,json,wxml,wxss}：uni-app 只把该目录原样拷贝，
 * 不会编译其中的 .vue，故此处不复用 src/components/ZyTabBar.vue（H5 专用）。
 * 与 src/utils/tabbar.js 的 TAB_ITEMS 保持一致；颜色/字号与 ZyTabBar.vue 对齐。
 */
const TAB_ITEMS = [
	{ path: '/pages/home/index', text: '首页', icon: '/static/tabbar/home-normal.png', activeIcon: '/static/tabbar/home-active.png' },
	{ path: '/pages/work/list', text: '社区', icon: '/static/tabbar/community-normal.png', activeIcon: '/static/tabbar/community-active.png' },
	{ path: '/pages/start/index', text: '手作', center: true, icon: '/static/tabbar/start-normal.png', activeIcon: '/static/tabbar/start-active.png' },
	{ path: '/pages/group/list', text: '小组', icon: '/static/tabbar/group-normal.png', activeIcon: '/static/tabbar/group-active.png' },
	{ path: '/pages/profile/index', text: '我的', icon: '/static/tabbar/profile-normal.png', activeIcon: '/static/tabbar/profile-active.png' },
]

function normalizeRoute(route) {
	if (!route) return ''
	const value = String(route)
	return value.charAt(0) === '/' ? value : '/' + value
}

Component({
	data: {
		items: TAB_ITEMS,
		active: '',
		warm: false,
	},
	lifetimes: {
		attached() {
			// attached 时页面栈可能还没就绪，下一帧补一次
			this.syncState()
			if (typeof wx.nextTick === 'function') {
				wx.nextTick(() => this.syncState())
			}
		},
		ready() {
			this.syncState()
		},
	},
	// 每个 tab 页各有一个实例；切页后由 show 同步选中态（tabBar 是页面级组件）
	pageLifetimes: {
		show() {
			this.syncState()
		},
	},
	methods: {
		/** 同步选中项与主题；主题切换时由 stores/theme.js 直接调用 */
		syncState() {
			let active = ''
			try {
				const pages = getCurrentPages()
				const current = pages.length ? pages[pages.length - 1] : null
				active = normalizeRoute(current && current.route)
			} catch (e) {
				active = ''
			}
			let warm = false
			try {
				warm = wx.getStorageSync('zy-theme') === 'warm'
			} catch (e) {
				warm = false
			}
			if (active !== this.data.active || warm !== this.data.warm) {
				this.setData({ active: active, warm: warm })
			}
		},
		onTap(event) {
			const path = event.currentTarget.dataset.path
			if (!path || path === this.data.active) return
			wx.switchTab({
				url: path,
				fail: function () {
					wx.reLaunch({ url: path })
				},
			})
		},
		onCenterTap(event) {
			try {
				wx.vibrateShort({ type: 'light' })
			} catch (e) {
				/* 部分基础库不支持轻震动 */
			}
			this.onTap(event)
		},
	},
})
