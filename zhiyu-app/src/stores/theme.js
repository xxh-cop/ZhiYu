/**
 * 页面配色主题（2026-11-08）：white=云白（默认）/ warm=暖纸（可选）。
 * 仅切换面级 CSS 变量（bg/card/border/stitch/shadow/纸纹），不涉业务逻辑。
 * 持久化：uni storage 'zy-theme'；main.js 挂载前同步 init，首帧即正确主题，不闪色。
 * 原生 tabBar 为静态配置（pages.json 云白），切换时经 setTabBarStyle 联动。
 */
import { defineStore } from 'pinia'

const STORAGE_KEY = 'zy-theme'

/** 原生 tabBar 面级值（与 design/tokens.json themes 对齐） */
const TABBAR_BG = { white: '#FFFFFF', warm: '#FFFDF7' }
const TABBAR_BORDER = { white: 'white', warm: 'black' }
/** 导航条面级值（跟 --zy-bg；frontColor 恒黑） */
const NAV_BG = { white: '#FFFFFF', warm: '#F6F1E7' }

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
			try {
				uni.setTabBarStyle({
					backgroundColor: TABBAR_BG[this.theme],
					borderStyle: TABBAR_BORDER[this.theme],
					fail: () => {},
				})
			} catch (e) { /* H5 无原生 tabBar 时忽略 */ }
		},
	},
})
