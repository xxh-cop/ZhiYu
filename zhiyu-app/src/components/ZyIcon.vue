<template>
	<!--
		ZyIcon —— 统一线性图标（2026-11-15 图标收口）。
		替代 emoji / 字符图标：同一套 1.5px 描边、圆角端点、暖墨色。
		实现：data-URI SVG 背景，H5 与小程序同一份渲染，不依赖图标字体码点。
		用法：<ZyIcon name="send" :size="30" color="#FFFFFF" />（装饰性，aria-hidden；
		可点语义由外层 AccessibleButton 的 aria-label 承担）。
	-->
	<view class="zy-icon" :style="iconStyle" aria-hidden="true"></view>
</template>

<script setup>
import { computed } from 'vue'

defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })

const props = defineProps({
	name: { type: String, required: true },
	size: { type: Number, default: 32 }, // rpx，只用 24 / 32 / 40 三档
	color: { type: String, default: '#5C5648' },
})

const PATHS = {
	send: ['M21 3 3 10.5l7 2.5L12.5 20 21 3Z', 'M10 13 21 3'],
	mic: ['M12 3a2.5 2.5 0 0 1 2.5 2.5v6a2.5 2.5 0 0 1-5 0v-6A2.5 2.5 0 0 1 12 3Z', 'M6.5 11a5.5 5.5 0 0 0 11 0', 'M12 16.5V21'],
	play: ['M8 5.5v13l11-6.5-11-6.5Z'],
	sound: ['M4 9.5v5h3.5L12 19V5L7.5 9.5H4Z', 'M15.5 9a4.2 4.2 0 0 1 0 6', 'M18 6.5a8 8 0 0 1 0 11'],
	share: ['M12 15V4', 'M7.5 8.5 12 4l4.5 4.5', 'M5 13v6h14v-6'],
	external: ['M14 4h6v6', 'M20 4 11 13', 'M18 13v6H5V6h6'],
	arrow: ['M9 5l7 7-7 7'],
	search: ['M10.5 4a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13Z', 'M15.5 15.5 20 20'],
}

const iconStyle = computed(() => {
	const paths = PATHS[props.name] || PATHS.arrow
	const body = paths.map(d => `<path d='${d}'/>`).join('')
	const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='${props.color}' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'>${body}</svg>`
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
