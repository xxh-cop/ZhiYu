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
.zy-segmented {
	display: flex;
	border: 1rpx solid var(--zy-border, #D3DED8);
	background: transparent;
}
.zy-segmented-item {
	flex: 1;
	min-width: 0;
	min-height: 80rpx;
	margin: 0;
	padding: 0 8rpx;
	background: transparent;
	border: 0;
	border-right: 1rpx solid var(--zy-border, #D3DED8);
	border-radius: 0;
	color: var(--zy-text, #42524C);
	font-size: var(--zy-text-caption, 24rpx);
	line-height: 1.5;
}
.zy-segmented-item:last-child { border-right: 0; }
.zy-segmented-item::after { border: 0; }
.zy-segmented-item.active {
	color: var(--zy-title, #111816);
	font-weight: 500;
	box-shadow: inset 0 -3rpx 0 var(--zy-care, #A64842);
}
</style>
