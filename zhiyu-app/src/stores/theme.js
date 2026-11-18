/**
 * 页面配色主题（2026-11-08）：white=云白（默认）/ warm=暖纸（可选）。
 * 仅切换面级 CSS 变量（bg/card/border/stitch/shadow/纸纹），不涉业务逻辑。
 * 持久化：uni storage 'zy-theme'；main.js 挂载前同步 init，首帧即正确主题，不闪色。
 * 原生 tabBar 为静态配置（pages.json 云白），切换时经 setTabBarStyle 联动。
 */
import { defineStore } from 'pinia'

const STORAGE_KEY = 'zy-theme'

/** 原生 tabBar 面级值（与 design/tokens.json themes 对齐） */
const TABBAR_BG = { white: '#FFFFFF', warm: '#FFFFFF' }
const TABBAR_BORDER = { white: 'black', warm: 'black' }
/** 导航条面级值（跟 --zy-bg；frontColor 恒黑） */
const NAV_BG = { white: '#F1F4F1', warm: '#F1F4F1' }

export const useThemeStore = defineStore('theme', {
	state: () => ({
		/** 'white' | 'warm' */
		theme: 'white',
	}),
	getters: {
		isWarm: (s) => s.theme === 'warm',
		/** 页面根 view 绑定用：暖纸主题挂 .zy-theme-warm */
		themeClass: (s) => (s.theme === 'warm' ? 'zy-theme-warm' : ''),
	},
	actions: {
		/** App 启动同步调用（main.js，挂载前） */
		init() {
			try {
				const saved = uni.getStorageSync(STORAGE_KEY)
				if (saved === 'warm' || saved === 'white') this.theme = saved
			} catch (e) { /* 读取失败保持默认 white */ }
			this.applyNavBar()
			this.applyTabBar()
		},
		setTheme(next) {
			if (next !== 'white' && next !== 'warm') return
			this.theme = next
			try { uni.setStorageSync(STORAGE_KEY, next) } catch (e) { /* 忽略持久化失败 */ }
			this.applyNavBar()
			this.applyTabBar()
			// H5 自定义 tabBar 监听此事件换纸色（mp 走下方 getTabBar().syncState()）
			try { uni.$emit('zy:themechange') } catch (e) { /* 忽略 */ }
		},
		/** 导航条随主题联动（pages.json 静态值只保冷启动首帧）；不支持平台静默跳过 */
		applyNavBar() {
			try {
				uni.setNavigationBarColor({
					frontColor: '#000000',
					backgroundColor: NAV_BG[this.theme],
					fail: () => {},
				})
			} catch (e) { /* 忽略 */ }
		},
		/** 原生 tabBar 随主题联动；非 tabBar 页面/不支持平台静默跳过 */
		applyTabBar() {
			// #ifndef H5
			try {
				const task = uni.setTabBarStyle({
					backgroundColor: TABBAR_BG[this.theme],
					borderStyle: TABBAR_BORDER[this.theme],
					fail: () => {},
				})
				// uni 的部分平台实现会返回 Promise：显式接住，避免 unhandled rejection
				if (task && typeof task.catch === 'function') task.catch(() => {})
			} catch (e) { /* 非 tabBar 页面忽略 */ }
			// #endif
			// #ifdef MP
			// custom tabBar 不吃 setTabBarStyle，直接通知当前页组件换纸色
			try {
				const pages = getCurrentPages()
				const current = pages.length ? pages[pages.length - 1] : null
				const bar = current && typeof current.getTabBar === 'function' ? current.getTabBar() : null
				if (bar && typeof bar.syncState === 'function') bar.syncState()
			} catch (e) { /* 页面栈不可用时由 tabBar 自身 show 兜底 */ }
			// #endif
		},
	},
})
