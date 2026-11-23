<template>
	<!--
		ZyTextarea —— 「一线」多行输入本体（P1 2026-11-18）。
		与 ZyInput 同一底线语言；最小高 176rpx，可自动增高；字数统计交给 FormField。
	-->
	<view class="zy-textarea" :class="{ 'is-focused': focused, 'is-error': error, 'is-disabled': disabled }">
		<textarea
			class="zy-textarea-el"
			:id="id"
			:value="modelValue"
			:placeholder="placeholder"
			:placeholder-style="placeholderStyle"
			:maxlength="maxlength"
			:disabled="disabled"
			:auto-height="autoHeight"
			:show-confirm-bar="showConfirmBar"
			:cursor-spacing="24"
			:aria-describedby="ariaDescribedby"
			:aria-invalid="error || ariaInvalid"
			:aria-label="ariaLabel"
			@input="onInput"
			@focus="focused = true; $emit('focus', $event)"
			@blur="focused = false; $emit('blur', $event)"
		/>
	</view>
</template>

<script setup>
import { ref } from 'vue'

defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
const props = defineProps({
	modelValue: { type: String, default: '' },
	placeholder: { type: String, default: '' },
	maxlength: { type: Number, default: 500 },
	disabled: { type: Boolean, default: false },
	error: { type: Boolean, default: false },
	autoHeight: { type: Boolean, default: true },
	showConfirmBar: { type: Boolean, default: true },
	id: { type: String, default: undefined },
	ariaDescribedby: { type: String, default: undefined },
	ariaInvalid: { type: Boolean, default: false },
	ariaLabel: { type: String, default: undefined },
})
const emit = defineEmits(['update:modelValue', 'focus', 'blur'])
const focused = ref(false)
const placeholderStyle = 'color:#8B9994;font-size:28rpx'
function onInput(e) { emit('update:modelValue', e.detail.value) }
</script>

<style scoped>
.zy-textarea {
	position: relative;
	border-bottom: 2rpx solid var(--zy-faint, #8B9994);
	background: transparent;
}
.zy-textarea-el {
	width: 100%;
	min-height: 176rpx;
	padding: 16rpx 0;
	margin: 0;
	background: transparent;
	border: 0;
	box-sizing: border-box;
	font-size: var(--zy-text-body-lg, 30rpx);
	line-height: 1.7;
	color: var(--zy-title, #111816);
}
.zy-textarea.is-focused { border-bottom-color: var(--zy-brand-600, #0E5B53); }
.zy-textarea.is-focused::after {
	content: '';
	position: absolute;
	left: 0;
	right: 0;
	bottom: 2rpx;
	height: 2rpx;
	background: var(--zy-brand-300, #8FC7B8);
	opacity: .55;
	pointer-events: none;
}
.zy-textarea.is-error { border-bottom-color: var(--zy-care, #A64842); }
.zy-textarea.is-disabled { opacity: .55; }
</style>
