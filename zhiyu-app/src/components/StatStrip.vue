<template>
	<!--
		StatStrip —— 旅程数据行（v7 一线 · P2 数字收口 2026-11-18）。
		去卡片容器：数字 + 标签均布，项间竖缝线，上下横缝线由页面按需加 .zy-stitch-h。
		数字为宋体展签大字（40rpx/500）+ tabular-nums；标签 22rpx 无衬线。
	-->
	<view class="zy-stat-strip" role="group" :aria-label="ariaLabel">
		<template v-for="(item, i) in items" :key="item.label">
			<StitchDivider v-if="i > 0" vertical class="zy-stat-divider" />
			<view class="zy-stat">
				<text class="zy-stat-num">{{ item.value }}</text>
				<text class="zy-stat-label">{{ item.label }}</text>
			</view>
		</template>
	</view>
</template>

<script setup>
import StitchDivider from './StitchDivider.vue'

defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
defineProps({
	items: { type: Array, default: () => [] }, // [{ value, label }]
	ariaLabel: { type: String, default: '数据统计' },
})
</script>

<style scoped>
.zy-stat-strip { display: flex; align-items: center; justify-content: space-around; padding: 28rpx 0; }
.zy-stat { text-align: center; flex: 1; min-width: 0; }
.zy-stat-num { display: block; font-family: var(--zy-font-display, serif); font-size: var(--zy-text-title-lg, 40rpx); font-weight: 500; color: var(--zy-brand-600, #0E5B53); font-variant-numeric: tabular-nums; }
.zy-stat-label { display: block; font-size: var(--zy-text-micro, 22rpx); color: var(--zy-muted, #6E7B75); margin-top: 6rpx; }
.zy-stat-divider { height: 60rpx; flex-shrink: 0; }
</style>
