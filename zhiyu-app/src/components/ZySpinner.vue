<template>
	<!--
		ZySpinner —— 「线轴收针」loading 母题（P2 2026-11-18，方案 E 项）。
		线轴（上下法兰 + 轴身 + 三道绕线）为静态 glyph；外圈一段朱砂线以
		stroke-dashoffset 离散 12 帧绕轴一周（SMIL，data-URI SVG，H5/小程序同构）。
		reduced-motion（系统或手动开关，复用 useInteraction）时输出静态帧。
		tone: brand = 窑青轴 + 朱砂线（浅底）；light = 瓷白轴 + 浅朱砂线（深底/主按钮）。
	-->
	<view class="zy-spinner" :style="spinStyle" aria-hidden="true"></view>
</template>

<script setup>
import { computed } from 'vue'
import { useInteraction } from '../composables/useInteraction'
import { useThemeStore } from '../stores/theme'

defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
const props = defineProps({
	size: { type: Number, default: 28 }, // rpx
	tone: { type: String, default: 'brand', validator: (v) => ['brand', 'light'].includes(v) },
})

const { reducedMotion } = useInteraction()
const themeStore = useThemeStore()

const PALETTES = {
	brand: { axis: '#24478f', thread: '#A64842' },
	light: { axis: '#F1F4F1', thread: '#E9C9C5' },
}

/* 12 帧离散收针：周长 2π×10.75≈67.5，每帧推进 5.625 */
const FRAMES = '0;-5.625;-11.25;-16.875;-22.5;-28.125;-33.75;-39.375;-45;-50.625;-56.25;-61.875'

const spinStyle = computed(() => {
	// 玄墨主题下 brand 档自动升为浅底配色，避免深底上 invisible
	const effective = props.tone === 'brand' && themeStore.isDark ? 'light' : props.tone
	const { axis, thread } = PALETTES[effective] || PALETTES.brand
	const anim = reducedMotion.value
		? ''
		: `<animate attributeName='stroke-dashoffset' values='${FRAMES}' dur='1.2s' calcMode='discrete' repeatCount='indefinite'/>`
	const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke-linecap='round' stroke-linejoin='round'>`
		+ `<path d='M5 4h14M5 20h14' stroke='${axis}' stroke-width='2'/>`
		+ `<path d='M8 4v16M16 4v16' stroke='${axis}' stroke-width='1.5'/>`
		+ `<path d='M8 8h8M8 12h8M8 16h8' stroke='${thread}' stroke-width='1.5' opacity='.8'/>`
		+ `<circle cx='12' cy='12' r='10.75' stroke='${thread}' stroke-width='1.5' stroke-dasharray='17 50.5'>${anim}</circle>`
		+ `</svg>`
	return {
		width: props.size + 'rpx',
		height: props.size + 'rpx',
		backgroundImage: `url("data:image/svg+xml,${encodeURIComponent(svg)}")`,
	}
})
</script>

<style scoped>
.zy-spinner {
	display: inline-block;
	flex-shrink: 0;
	background-size: contain;
	background-repeat: no-repeat;
	background-position: center;
	vertical-align: middle;
}
</style>
