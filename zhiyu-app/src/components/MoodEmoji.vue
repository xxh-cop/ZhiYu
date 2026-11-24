<template>
	<!--
		MoodEmoji —— 温暖手绘心情脸（v9 2026-11-23）。
		kind: 0 开心 / 1 平静 / 2 一般 / 3 有点累 / 4 不太好。
		用 SVG data-URI 背景绘制：H5 与小程序都能显示，不依赖系统 emoji 字体。
	-->
	<view class="mood-emoji" :style="style" aria-hidden="true"></view>
</template>

<script setup>
import { computed } from 'vue'
defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
const props = defineProps({
	kind: { type: Number, default: 0 },
	size: { type: Number, default: 88 }, // rpx
})

const INK = '#5A3E2B'
const BLUSH = "<circle cx='15' cy='39' r='4.5' fill='#FF8F8F' opacity='.45'/><circle cx='49' cy='39' r='4.5' fill='#FF8F8F' opacity='.45'/>"
const FACES = [
	// 0 开心：眯眼笑 + 张嘴大笑
	`<circle cx='32' cy='32' r='29' fill='#FFD66B'/><circle cx='32' cy='32' r='29' fill='none' stroke='#F2B53A' stroke-width='2'/>${BLUSH}<path d='M18 28q5-6 10 0M36 28q5-6 10 0' fill='none' stroke='${INK}' stroke-width='3' stroke-linecap='round'/><path d='M21 37q11 15 22 0z' fill='#C9533F'/><path d='M26 44q6 3 12 0' fill='none' stroke='#FF9C8A' stroke-width='2.4' stroke-linecap='round'/>`,
	// 1 平静：闭眼弯弯 + 浅笑
	`<circle cx='32' cy='32' r='29' fill='#BFE6D2'/><circle cx='32' cy='32' r='29' fill='none' stroke='#8FCBAE' stroke-width='2'/>${BLUSH}<path d='M18 29q5 4 10 0M36 29q5 4 10 0' fill='none' stroke='${INK}' stroke-width='3' stroke-linecap='round'/><path d='M25 40q7 6 14 0' fill='none' stroke='${INK}' stroke-width='3' stroke-linecap='round'/>`,
	// 2 一般：圆眼 + 平嘴
	`<circle cx='32' cy='32' r='29' fill='#F7E4BC'/><circle cx='32' cy='32' r='29' fill='none' stroke='#E5C98E' stroke-width='2'/>${BLUSH}<circle cx='23' cy='28' r='3.4' fill='${INK}'/><circle cx='41' cy='28' r='3.4' fill='${INK}'/><path d='M25 42h14' stroke='${INK}' stroke-width='3' stroke-linecap='round'/>`,
	// 3 有点累：半闭眼 + 小波浪嘴 + 一缕汗
	`<circle cx='32' cy='32' r='29' fill='#DCD5F2'/><circle cx='32' cy='32' r='29' fill='none' stroke='#B9AEE0' stroke-width='2'/>${BLUSH}<path d='M18 29h10M36 29h10' stroke='${INK}' stroke-width='3' stroke-linecap='round'/><path d='M19 25q4-2 8 0M37 25q4-2 8 0' fill='none' stroke='${INK}' stroke-width='2' stroke-linecap='round' opacity='.6'/><path d='M24 43q4-3 8 0q4 3 8 0' fill='none' stroke='${INK}' stroke-width='3' stroke-linecap='round'/><path d='M49 15q3 5 0 8q-3-3 0-8z' fill='#8EC3F0'/>`,
	// 4 不太好：下垂眉 + 小眼 + 撇嘴 + 一滴泪
	`<circle cx='32' cy='32' r='29' fill='#CBD8EA'/><circle cx='32' cy='32' r='29' fill='none' stroke='#9FB4D3' stroke-width='2'/>${BLUSH}<path d='M17 25l9-4M47 25l-9-4' stroke='${INK}' stroke-width='2.6' stroke-linecap='round'/><circle cx='23' cy='30' r='3.2' fill='${INK}'/><circle cx='41' cy='30' r='3.2' fill='${INK}'/><path d='M24 46q8-7 16 0' fill='none' stroke='${INK}' stroke-width='3' stroke-linecap='round'/><path d='M43 35q3 5 0 7.5q-3-2.5 0-7.5z' fill='#7DB4EC'/>`,
]

const style = computed(() => {
	const body = FACES[props.kind] || FACES[2]
	const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'>${body}</svg>`
	return {
		width: props.size + 'rpx',
		height: props.size + 'rpx',
		backgroundImage: `url("data:image/svg+xml,${encodeURIComponent(svg)}")`,
	}
})
</script>

<style scoped>
.mood-emoji { flex-shrink: 0; background-size: contain; background-repeat: no-repeat; background-position: center; }
</style>
