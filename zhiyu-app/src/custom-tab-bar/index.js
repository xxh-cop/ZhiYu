/**
 * 微信小程序自定义 tabBar（原生组件）。
 * 必须输出为 custom-tab-bar/index.{js,json,wxml,wxss}：uni-app 只把该目录原样拷贝，
 * 不会编译其中的 .vue，故此处不复用 src/components/ZyTabBar.vue（H5 专用）。
 * 与 src/utils/tabbar.js 的 TAB_ITEMS 保持一致；v7 为五个纯文字入口，无中央悬浮球。
 */
const TAB_ITEMS = [
	{ path: '/pages/home/index', text: '首页' },
	{ path: '/pages/work/list', text: '社区' },
	{ path: '/pages/start/index', text: '手作' },
	{ path: '/pages/group/list', text: '小组' },
	{ path: '/pages/profile/index', text: '我的' },
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
		/** 同步选中项；主题切换时由 stores/theme.js 直接调用 */
		syncState() {
			let active = ''
			try {
				const pages = getCurrentPages()
				const current = pages.length ? pages[pages.length - 1] : null
				active = normalizeRoute(current && current.route)
			} catch (e) {
				active = ''
			}
			if (active !== this.data.active) {
				this.setData({ active })
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
	},
})
