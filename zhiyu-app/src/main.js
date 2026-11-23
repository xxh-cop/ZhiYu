import {
	createSSRApp
} from "vue";
import {
	createPinia
} from "pinia";
import {
	watch
} from "vue";
import App from "./App.vue";
import { useThemeStore } from "./stores/theme";
import { recordVisit, routeLabel } from "./utils/recent";
// #ifdef H5
import ZyTabBar from "./components/ZyTabBar.vue";
import { currentTabRoute, isTabRoute } from "./utils/tabbar";
// #endif

// wot-design-uni：通过 EasyCom 按需引入组件，无需全局 css import
// 主题色通过 App.vue 引入 @/styles/theme.scss 覆盖 CSS 变量

export function createApp() {
	const app = createSSRApp(App);
	const pinia = createPinia();
	app.use(pinia);

	// 页面配色主题（white 默认 / warm 可选）：挂载前同步读 storage，首帧即正确主题
	const theme = useThemeStore(pinia);
	theme.init();

	// H5 专享：html 同步挂主题 class。用途有二——
	// 1) H5 模拟 tabBar 是 body 级 DOM，不在页面根 view 子树内，靠 .zy-theme-warm .uni-tabbar 规则换肤；
	// 2) H5 的 page 容器（uni-page-body）随之继承覆盖变量，整页底色无平台限制。
	// mp 无 document，守卫跳过（mp 的 page 元素限制见规格 §8.4，tabBar 走 uni.setTabBarStyle）。
	if (typeof document !== "undefined") {
		watch(
			() => theme.themeClass,
			(cls) => {
				document.documentElement.classList.toggle("zy-theme-warm", cls === "zy-theme-warm");
				document.documentElement.classList.toggle("zy-theme-dark", cls === "zy-theme-dark");
			},
			{ immediate: true }
		);
	}

	// 全局提供 zyThemeClass：各页面根 view 绑定 :class="zyThemeClass" 即可随主题切换。
	// mp 原生导航条为页面实例级：setNavigationBarColor 仅对当前页生效，切页即回 pages.json
	// 静态值，故在每个页面 onLoad（新页尽早）与 onShow（tab 页切换不再 onLoad）接力应用；
	// 冷启动首帧由 pages.json 云白默认值保证不闪旧色。
	app.mixin({
		computed: {
			zyThemeClass() {
				return theme.themeClass
			},
		},
		onLoad() {
			theme.applyNavBar()
		},
		onShow() {
			theme.applyNavBar()
			// #ifdef H5
			try {
				const host = document.getElementById('zy-tabbar-host')
				if (host) host.style.display = isTabRoute(currentTabRoute()) ? '' : 'none'
			} catch (e) { /* 忽略 */ }
			// #endif
			let current = null
			try {
				const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
				current = pages.length ? pages[pages.length - 1] : null
				if (current && current.route) {
					uni.$emit('zy:tabshow', '/' + current.route)
					// 记录「最近访问」：仅路由与页面名，供首页继续上次/最近入口
					let full = '/' + current.route
					const query = current.options || {}
					const keys = Object.keys(query)
					if (keys.length) full += '?' + keys.map((k) => k + '=' + encodeURIComponent(query[k])).join('&')
					const label = routeLabel(full)
					if (label) recordVisit(full, label)
				}
			} catch (e) {
				/* 页面栈不可用时由自定义导航条自行读取 */
			}
			// #ifdef MP
			// 微信 custom tabBar 的 pageLifetimes 不保证触发，官方做法是在页面 onShow 主动同步选中态
			try {
				const bar = current && typeof current.getTabBar === 'function' ? current.getTabBar() : null
				if (bar && typeof bar.syncState === 'function') bar.syncState()
			} catch (e) {
				/* 非 tabBar 页面或组件未就绪时忽略 */
			}
			// #endif
		},
	});

	// #ifdef H5
	// uni-h5 不会自动挂载 custom-tab-bar：注入全局宿主，并隐藏原生 tabBar
	setTimeout(() => {
		try {
			const hideTask = uni.hideTabBar({ animation: false, fail: () => {} })
			// uni-h5 在 tabBar 尚未挂载时可能返回 rejected Promise；接住，避免每页一条 pageerror
			if (hideTask && typeof hideTask.catch === 'function') hideTask.catch(() => {})
			if (!document.getElementById('zy-tabbar-host')) {
				const host = document.createElement('div')
				host.id = 'zy-tabbar-host'
				document.body.appendChild(host)
				createSSRApp(ZyTabBar).mount('#zy-tabbar-host')
				host.style.display = isTabRoute(currentTabRoute()) ? '' : 'none'
			}
		} catch (e) {
			/* 挂载失败时保留原生 tabBar，不阻断页面 */
		}
	}, 0)
	// #endif

	return {
		app,
	};
}
