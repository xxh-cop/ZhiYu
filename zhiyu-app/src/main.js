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
// #ifdef H5
import ZyTabBar from "./components/ZyTabBar.vue";
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
			try {
				const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
				const current = pages.length ? pages[pages.length - 1] : null
				if (current && current.route) uni.$emit('zy:tabshow', '/' + current.route)
			} catch (e) {
				/* 页面栈不可用时由自定义导航条自行读取 */
			}
		},
	});

	// #ifdef H5
	// uni-h5 不会自动挂载 custom-tab-bar：注入全局宿主，并隐藏原生 tabBar
	setTimeout(() => {
		try {
			uni.hideTabBar({ animation: false })
			if (!document.getElementById('zy-tabbar-host')) {
				const host = document.createElement('div')
				host.id = 'zy-tabbar-host'
				document.body.appendChild(host)
				createSSRApp(ZyTabBar).mount('#zy-tabbar-host')
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
