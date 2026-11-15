<template>
	<!--
		StatStrip —— 旅程数据行（暖纸青玉 v6 · 规格 §2-C7）。
		去卡片容器：数字 + 标签均布，项间竖缝线，上下横缝线由页面按需加 .zy-stitch-h。
		数字为大字（40rpx/600），brand-600 按大字级对比度（≥3:1）使用。
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
.zy-stat-num { display: block; font-size: var(--zy-text-title-lg, 40rpx); font-weight: 600; color: var(--zy-brand-600, var(--zy-brand-600, #217e76)); font-variant-numeric: tabular-nums; }
.zy-stat-label { display: block; font-size: var(--zy-text-micro, 22rpx); color: var(--zy-text, #5c5648); margin-top: 6rpx; }
.zy-stat-divider { height: 60rpx; flex-shrink: 0; }
</style>
