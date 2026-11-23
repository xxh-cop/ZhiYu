/**
 * 页面配色主题：white=云白（默认）/ warm=暖纸（历史兼容，面级并入云白）/
 * dark=玄墨（全局深色）/ system=跟随系统。
 * 仅切换面级 CSS 变量，不涉业务逻辑。
 * 持久化：uni storage 'zy-theme'；main.js 挂载前同步 init，首帧即正确主题，不闪色。
 * 原生 tabBar / 导航条经 setTabBarStyle / setNavigationBarColor 联动。
 */
import { defineStore } from 'pinia'

const STORAGE_KEY = 'zy-theme'
const VALID = ['white', 'warm', 'dark', 'system']

/** 原生 tabBar 面级值 */
const TABBAR_BG = { white: '#FFFFFF', warm: '#FFFFFF', dark: '#111E1A' }
const TABBAR_BORDER = { white: 'black', warm: 'black', dark: 'white' }
/** 导航条面级值（跟 --zy-bg） */
const NAV_BG = { white: '#F1F4F1', warm: '#F1F4F1', dark: '#0B1513' }

export const useThemeStore = defineStore('theme', {
	state: () => ({
		/** 用户选择：'white' | 'warm' | 'dark' | 'system' */
		theme: 'white',
		/** 系统深色偏好（system 选项的解析依据） */
		systemDark: false,
	}),
	getters: {
		isWarm: (s) => s.theme === 'warm',
		/** 实际生效主题 */
		resolvedTheme: (s) => (s.theme === 'system' ? (s.systemDark ? 'dark' : 'white') : s.theme),
		isDark() { return this.resolvedTheme === 'dark' },
		/** 页面根 view 绑定用 */
		themeClass() {
			const r = this.resolvedTheme
			return r === 'warm' ? 'zy-theme-warm' : r === 'dark' ? 'zy-theme-dark' : ''
		},
	},
	actions: {
		/** App 启动同步调用（main.js，挂载前） */
		init() {
			try {
				const saved = uni.getStorageSync(STORAGE_KEY)
				if (VALID.includes(saved)) this.theme = saved
			} catch (e) { /* 读取失败保持默认 white */ }
			this.detectSystem()
			this.watchSystem()
			this.applyNavBar()
			this.applyTabBar()
		},
		detectSystem() {
			try {
				// #ifdef H5
				if (typeof window !== 'undefined' && window.matchMedia) {
					this.systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches
				}
				// #endif
				// #ifndef H5
				const info = uni.getSystemInfoSync()
				this.systemDark = info && info.theme === 'dark'
				// #endif
			} catch (e) { /* 不支持时按浅色 */ }
		},
		watchSystem() {
			if (this._systemWatched) return
			this._systemWatched = true
			try {
				// #ifdef H5
				if (typeof window !== 'undefined' && window.matchMedia) {
					const mq = window.matchMedia('(prefers-color-scheme: dark)')
					const onChange = (e) => { this.systemDark = e.matches; this.onResolvedChange() }
					if (mq.addEventListener) mq.addEventListener('change', onChange)
					else if (mq.addListener) mq.addListener(onChange)
				}
				// #endif
				// #ifdef MP
				if (typeof uni.onThemeChange === 'function') {
					uni.onThemeChange((res) => { this.systemDark = res && res.theme === 'dark'; this.onResolvedChange() })
				}
				// #endif
			} catch (e) { /* 平台不支持时仅手动切换 */ }
		},
		onResolvedChange() {
			this.applyNavBar()
			this.applyTabBar()
			try { uni.$emit('zy:themechange') } catch (e) { /* 忽略 */ }
		},
		setTheme(next) {
			if (!VALID.includes(next)) return
			this.theme = next
			try { uni.setStorageSync(STORAGE_KEY, next) } catch (e) { /* 忽略持久化失败 */ }
			this.onResolvedChange()
		},
		/** 导航条随主题联动；不支持平台静默跳过 */
		applyNavBar() {
			try {
				const r = this.resolvedTheme
				uni.setNavigationBarColor({
					frontColor: r === 'dark' ? '#ffffff' : '#000000',
					backgroundColor: NAV_BG[r] || NAV_BG.white,
					fail: () => {},
				})
			} catch (e) { /* 忽略 */ }
		},
		/** 原生 tabBar 随主题联动；非 tabBar 页面/不支持平台静默跳过 */
		applyTabBar() {
			const r = this.resolvedTheme
			// #ifndef H5
			try {
				const task = uni.setTabBarStyle({
					backgroundColor: TABBAR_BG[r] || TABBAR_BG.white,
					borderStyle: TABBAR_BORDER[r] || 'black',
					fail: () => {},
				})
				if (task && typeof task.catch === 'function') task.catch(() => {})
			} catch (e) { /* 非 tabBar 页面忽略 */ }
			// #endif
			// #ifdef MP
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
