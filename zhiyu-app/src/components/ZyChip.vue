<template>
	<!--
		ZyChip —— 「一线」筛选片（P1 2026-11-18）。
		与 programs/work-list 既有 underline-tab 语言一致：透明底 + 24rpx 字，
		选中 = 窑青字 + 窑青 2rpx 底线；热区经 ::after 扩到 88rpx。
	-->
	<AccessibleButton class="zy-chip" :class="{ active }" :aria-pressed="active" :disabled="disabled" @click="$emit('click')">
		<slot />
	</AccessibleButton>
</template>

<script setup>
import AccessibleButton from './AccessibleButton.vue'
defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
defineProps({
	active: { type: Boolean, default: false },
	disabled: { type: Boolean, default: false },
})
defineEmits(['click'])
</script>

<style scoped>
.zy-chip {
	position: relative;
	display: inline-flex;
	align-items: center;
	min-height: 64rpx;
	margin: 0;
	padding: 6rpx 4rpx 10rpx;
	background: transparent;
	border: 0;
	border-bottom: 2rpx solid transparent;
	border-radius: 0;
	color: var(--zy-text, #42524C);
	font-size: var(--zy-text-caption, 24rpx);
	line-height: 1.5;
	white-space: nowrap;
}
.zy-chip::after {
	content: '';
	position: absolute;
	left: 0;
	right: 0;
	top: 50%;
	transform: translateY(-50%);
	height: 88rpx;
	border: 0;
}
.zy-chip.active {
	border-bottom-color: var(--zy-brand-700, #0A403B);
	color: var(--zy-brand-700, #0A403B);
	font-weight: 500;
}
</style>
