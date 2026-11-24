<template>
	<!--
		ZySegmented —— 「一线」互斥单选（P1 2026-11-18）。
		发丝线外框 + 竖向分隔；选中 = 标题色 + 底部 3rpx 朱砂线（与 start 页既有语言一致）。
		role=radiogroup，选项 role=radio + aria-checked，键盘 Enter/Space 由 AccessibleButton 承担。
	-->
	<view class="zy-segmented" role="radiogroup" :aria-label="ariaLabel">
		<AccessibleButton
			v-for="opt in options"
			:key="opt.value"
			class="zy-segmented-item"
			:class="{ active: opt.value === modelValue }"
			role="radio"
			:aria-checked="opt.value === modelValue"
			:disabled="disabled"
			@click="$emit('update:modelValue', opt.value)"
		>
			{{ opt.label }}
		</AccessibleButton>
	</view>
</template>

<script setup>
import AccessibleButton from './AccessibleButton.vue'
defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
defineProps({
	options: { type: Array, default: () => [] }, // [{ value, label }]
	modelValue: { type: [String, Number], default: '' },
	disabled: { type: Boolean, default: false },
	ariaLabel: { type: String, default: '单选' },
})
defineEmits(['update:modelValue'])
</script>

<style scoped>
/* v9：浅底轨道 + 选中项浮起白块（青花蓝字） */
.zy-segmented {
	display: flex;
	gap: 8rpx;
	padding: 8rpx;
	border-radius: 28rpx;
	background: var(--zy-bg-sunken, #eef2f7);
}
.zy-segmented-item {
	flex: 1;
	min-width: 0;
	min-height: 76rpx;
	margin: 0;
	padding: 0 8rpx;
	background: transparent;
	border: 0;
	border-radius: 20rpx;
	color: var(--zy-text, #384256);
	font-size: var(--zy-text-body, 28rpx);
	line-height: 1.5;
}
.zy-segmented-item::after { border: 0; }
.zy-segmented-item.active {
	background: var(--zy-card-bg, #fff);
	color: var(--zy-brand-600, #24478f);
	font-weight: 700;
	box-shadow: 0 4rpx 14rpx rgba(20, 36, 72, 0.12);
}
</style>
