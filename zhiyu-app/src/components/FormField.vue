<template>
	<view class="form-field" :class="{ 'has-error': !!error }">
		<label class="field-label" :for="inputId">
			<text>{{ label }}</text>
			<text v-if="required" class="field-required" aria-hidden="true">*</text>
			<text v-if="!required && optionalText" class="field-optional">{{ optionalText }}</text>
		</label>
		<text v-if="hint" :id="hintId" class="field-hint">{{ hint }}</text>
		<!-- 消费方通过作用域插槽拿到 id / aria 属性，绑定到自己的 input / textarea -->
		<slot :id="inputId" :aria-describedby="describedBy" :aria-invalid="!!error" />
		<view class="field-footer">
			<text v-if="error" :id="errorId" class="field-error" role="alert">{{ error }}</text>
			<text v-if="showCounter" class="field-counter" aria-hidden="true">{{ count }} / {{ maxlength }}</text>
		</view>
	</view>
</template>

<script setup>
import { computed, getCurrentInstance } from 'vue'

/**
 * FormField —— 可见标签 + 必填标记 + hint + 错误定位 + 字数统计（计划 §3.2）。
 * 只负责「字段外壳」：标签与说明/错误的关联（id + aria-describedby）由组件生成，
 * 控件本体由消费方经作用域插槽提供，保持对原生 input/textarea 的完全控制。
 */
const props = defineProps({
	label: { type: String, required: true },
	required: { type: Boolean, default: false },
	optionalText: { type: String, default: '' },
	hint: { type: String, default: '' },
	error: { type: String, default: '' },
	maxlength: { type: Number, default: 0 },
	count: { type: Number, default: 0 },
})

const uid = getCurrentInstance().uid
const inputId = `zy-field-${uid}`
const hintId = `${inputId}-hint`
const errorId = `${inputId}-error`

const describedBy = computed(() =>
	[props.hint ? hintId : '', props.error ? errorId : ''].filter(Boolean).join(' ') || undefined
)
const showCounter = computed(() => props.maxlength > 0)
</script>

<style scoped>
.form-field { display: flex; flex-direction: column; gap: 10rpx; }
.field-label {
	display: flex;
	align-items: baseline;
	gap: 8rpx;
	font-size: var(--zy-font-base, 28rpx);
	font-weight: 600;
	color: var(--zy-title, #233d34);
}
.field-required { color: var(--zy-care, #a64842); }
.field-optional { font-size: var(--zy-font-xs, 20rpx); font-weight: 400; color: var(--zy-text-secondary, #4a5f54); }
.field-hint {
	font-size: var(--zy-font-sm, 24rpx);
	color: var(--zy-text-secondary, #4a5f54);
	line-height: 1.7;
}
.field-footer { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; }
.field-error {
	font-size: var(--zy-font-sm, 24rpx);
	color: var(--zy-care, #a64842);
	line-height: 1.6;
	overflow-wrap: anywhere;
}
.field-counter {
	margin-left: auto;
	font-size: var(--zy-font-xs, 20rpx);
	color: var(--zy-text-secondary, #4a5f54);
	font-variant-numeric: tabular-nums;
}
.has-error ::v-deep textarea,
.has-error ::v-deep input { border-color: var(--zy-care, #a64842) !important; }
</style>
