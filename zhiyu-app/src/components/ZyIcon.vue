<template>
	<!--
		ZyIcon —— 统一线性图标（2026-11-15 收口；2026-11-18 P0 扩容 8→32）。
		同一套 24 网格、1.5 描边、圆端点；data-URI SVG 背景，H5 与小程序同一份渲染。
		用法：<ZyIcon name="back" :size="32" tone="dark" />；color 显式传值优先于 tone。
		装饰性 aria-hidden；可点语义由外层 AccessibleButton 的 aria-label 承担。
	-->
	<view class="zy-icon" :style="iconStyle" aria-hidden="true"></view>
</template>

<script setup>
import { computed } from 'vue'
import { useThemeStore } from '../stores/theme'

defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })

const props = defineProps({
	name: { type: String, required: true },
	size: { type: Number, default: 32 }, // rpx，推荐 24 / 32 / 40 三档
	color: { type: String, default: '' }, // 显式色值优先；缺省按 tone 取 v7 色板
	tone: { type: String, default: 'text' },
})

/* v7「一线」色板快照（与 theme.scss 同步；SVG data-URI 无法读 CSS 变量，故在此对齐） */
const TONES = {
	text: '#42524C',
	title: '#111816',
	muted: '#6E7B75',
	faint: '#8B9994',
	brand: '#0E5B53',
	'brand-light': '#8FC7B8',
	care: '#A64842',
	white: '#FFFFFF',
	dark: '#F1F4F1',
	'dark-muted': '#A9BBB2',
}

/* 每个图标 = SVG 内部片段；stroke 继承根节点，实心元素自带 fill */
const ICONS = {
	send: "<path d='M21 3 3 10.5l7 2.5L12.5 20 21 3Z'/><path d='M10 13 21 3'/>",
	mic: "<path d='M12 3a2.5 2.5 0 0 1 2.5 2.5v6a2.5 2.5 0 0 1-5 0v-6A2.5 2.5 0 0 1 12 3Z'/><path d='M6.5 11a5.5 5.5 0 0 0 11 0'/><path d='M12 16.5V21'/>",
	play: "<path d='M8 5.5v13l11-6.5-11-6.5Z'/>",
	pause: "<path d='M8.5 5.5v13'/><path d='M15.5 5.5v13'/>",
	stop: "<rect x='6.5' y='6.5' width='11' height='11'/>",
	sound: "<path d='M4 9.5v5h3.5L12 19V5L7.5 9.5H4Z'/><path d='M15.5 9a4.2 4.2 0 0 1 0 6'/><path d='M18 6.5a8 8 0 0 1 0 11'/>",
	share: "<path d='M12 15V4'/><path d='M7.5 8.5 12 4l4.5 4.5'/><path d='M5 13v6h14v-6'/>",
	external: "<path d='M14 4h6v6'/><path d='M20 4 11 13'/><path d='M18 13v6H5V6h6'/>",
	arrow: "<path d='M9 5l7 7-7 7'/>",
	back: "<path d='M19 12H5'/><path d='M11 6l-6 6 6 6'/>",
	close: "<path d='M6 6l12 12'/><path d='M18 6 6 18'/>",
	more: "<circle cx='5' cy='12' r='1.4' fill='currentColor' stroke='none'/><circle cx='12' cy='12' r='1.4' fill='currentColor' stroke='none'/><circle cx='19' cy='12' r='1.4' fill='currentColor' stroke='none'/>",
	check: "<path d='M4.5 12.5l5 5L19.5 7'/>",
	plus: "<path d='M12 5v14'/><path d='M5 12h14'/>",
	search: "<path d='M10.5 4a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13Z'/><path d='M15.5 15.5 20 20'/>",
	heart: "<path d='M12 20s-7.5-4.6-9-9.2C2 7.5 4 5 6.8 5c2 0 3.6 1.1 5.2 3 1.6-1.9 3.2-3 5.2-3C20 5 22 7.5 21 10.8 19.5 15.4 12 20 12 20Z'/>",
	'heart-fill': "<path d='M12 20s-7.5-4.6-9-9.2C2 7.5 4 5 6.8 5c2 0 3.6 1.1 5.2 3 1.6-1.9 3.2-3 5.2-3C20 5 22 7.5 21 10.8 19.5 15.4 12 20 12 20Z' fill='currentColor'/>",
	star: "<path d='m12 4 2.4 5 5.6.7-4 3.9.9 5.6-4.9-2.7-4.9 2.7.9-5.6-4-3.9 5.6-.7L12 4Z'/>",
	'star-fill': "<path d='m12 4 2.4 5 5.6.7-4 3.9.9 5.6-4.9-2.7-4.9 2.7.9-5.6-4-3.9 5.6-.7L12 4Z' fill='currentColor'/>",
	comment: "<path d='M4 6h16v10H9l-5 4V6Z'/>",
	bell: "<path d='M6 16v-5a6 6 0 0 1 12 0v5l1.5 2.5H4.5L6 16Z'/><path d='M10 20a2 2 0 0 0 4 0'/>",
	settings: "<circle cx='12' cy='12' r='3'/><path d='M12 3v2.5'/><path d='M12 18.5V21'/><path d='M3 12h2.5'/><path d='M18.5 12H21'/><path d='m5.6 5.6 1.8 1.8'/><path d='m16.6 16.6 1.8 1.8'/><path d='m18.4 5.6-1.8 1.8'/><path d='m7.4 16.6-1.8 1.8'/>",
	user: "<circle cx='12' cy='8' r='4'/><path d='M4.5 20a7.5 7.5 0 0 1 15 0'/>",
	home: "<path d='M4 11 12 4l8 7'/><path d='M6 9.5V20h12V9.5'/><path d='M10 20v-5h4v5'/>",
	community: "<rect x='4' y='4' width='7' height='7'/><rect x='13' y='4' width='7' height='7'/><rect x='4' y='13' width='7' height='7'/><rect x='13' y='13' width='7' height='7'/>",
	craft: "<path d='M19 5 8.5 15.5'/><path d='M19 5c1 1 1 2.5 0 3.5L8.5 19 5 19l0-3.5L15.5 5c1-1 2.5-1 3.5 0Z'/><circle cx='17.2' cy='6.8' r='.9'/>",
	group: "<circle cx='9' cy='9' r='3.2'/><path d='M3.5 19a5.5 5.5 0 0 1 11 0'/><path d='M15.5 6.5a3.2 3.2 0 0 1 0 5.5'/><path d='M17 13.6a5.5 5.5 0 0 1 3.5 5.4'/>",
	eye: "<path d='M2.5 12S6 6.5 12 6.5 21.5 12 21.5 12 18 17.5 12 17.5 2.5 12 2.5 12Z'/><circle cx='12' cy='12' r='2.8'/>",
	'eye-off': "<path d='M4 4l16 16'/><path d='M9.9 5.2A9.8 9.8 0 0 1 12 5c6 0 9.5 7 9.5 7a17.6 17.6 0 0 1-3 3.9'/><path d='M6.1 8A17 17 0 0 0 2.5 12S6 19 12 19c1.5 0 2.9-.4 4.1-1'/>",
	calendar: "<rect x='4' y='6' width='16' height='14'/><path d='M4 10.5h16'/><path d='M8.5 4v4'/><path d='M15.5 4v4'/>",
	location: "<path d='M12 21s-6.5-5.7-6.5-10.5a6.5 6.5 0 0 1 13 0C18.5 15.3 12 21 12 21Z'/><circle cx='12' cy='10.5' r='2.3'/>",
	clock: "<circle cx='12' cy='12' r='8.5'/><path d='M12 7v5l3.5 2'/>",
	edit: "<path d='M4 20h16'/><path d='m6 16 10-10 2.5 2.5L8.5 18.5 5.5 19 6 16Z'/>",
	trash: "<path d='M5 7h14'/><path d='M9.5 7V4.5h5V7'/><path d='M7 7l1 13h8l1-13'/><path d='M10.5 11v5.5'/><path d='M13.5 11v5.5'/>",
	download: "<path d='M12 4v11'/><path d='M7.5 11 12 15.5 16.5 11'/><path d='M5 19h14'/>",
	refresh: "<path d='M19.5 12a7.5 7.5 0 1 1-2.2-5.3'/><path d='M19.5 3.5v3.7h-3.7'/>",
	'chevron-down': "<path d='m6 9.5 6 6 6-6'/>",
	alert: "<circle cx='12' cy='12' r='8.5'/><path d='M12 7.5v5.5'/><circle cx='12' cy='16.2' r='.9' fill='currentColor' stroke='none'/>",
	phone: "<path d='M6.4 3.5h3.1l1.5 4-2.1 1.5a11.2 11.2 0 0 0 6.1 6.1l1.5-2.1 4 1.5v3.1a2 2 0 0 1-2.2 2A16.6 16.6 0 0 1 4.4 5.7a2 2 0 0 1 2-2.2Z'/>",
	picture: "<rect x='3.5' y='5' width='17' height='14'/><circle cx='8.5' cy='9.5' r='1.4'/><path d='m4.5 17 5-5 4 4 3-3 3 3'/>",
	fullscreen: "<path d='M9 4H4v5'/><path d='M15 4h5v5'/><path d='M20 15v5h-5'/><path d='M4 15v5h5'/>",
	lock: "<rect x='5' y='10.5' width='14' height='9.5'/><path d='M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5'/>",
	info: "<circle cx='12' cy='12' r='8.5'/><path d='M12 11v5.5'/><circle cx='12' cy='7.8' r='.9' fill='currentColor' stroke='none'/>",
	video: "<rect x='3' y='6' width='12' height='12' rx='1'/><path d='m15 10 6-3v10l-6-3'/>",
	'chevron-up': "<path d='m6 14.5 6-6 6 6'/>",
	'check-circle': "<circle cx='12' cy='12' r='8.5'/><path d='m8 12 2.8 2.8L16.5 9'/>",
}

/* 玄墨主题下，浅底 tone 自动映射为深底等价值（data-URI 读不到 CSS 变量） */
const DARK_TONES = {
	text: '#C3D2CA',
	title: '#F1F4F1',
	muted: '#A9BBB2',
	faint: '#8FA39A',
	brand: '#8FC7B8',
	care: '#D4736A',
}
const themeStore = useThemeStore()

const iconStyle = computed(() => {
	const palette = themeStore.isDark ? { ...TONES, ...DARK_TONES } : TONES
	const stroke = props.color || palette[props.tone] || palette.text
	// more 等实心点用 currentColor：SVG 根节点 color 与 stroke 同值即可继承
	const body = (ICONS[props.name] || ICONS.arrow)
	const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='${stroke}' color='${stroke}' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'>${body}</svg>`
	return {
		width: props.size + 'rpx',
		height: props.size + 'rpx',
		backgroundImage: `url("data:image/svg+xml,${encodeURIComponent(svg)}")`,
	}
})
</script>

<style scoped>
.zy-icon {
	display: inline-block;
	flex-shrink: 0;
	background-size: contain;
	background-repeat: no-repeat;
	background-position: center;
	vertical-align: middle;
}
</style>
