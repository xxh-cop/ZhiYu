<template>
	<!--
		ZyInput —— 「一线」输入框本体（P1 2026-11-18）。
		底线语言：默认 2rpx #8B9994（对白底 ≥3:1），聚焦窑青双线（下实上虚），错误朱砂。
		高 88rpx / 值 30rpx；密码显隐用 eye 图形钮（88rpx 热区）。
		与 FormField 外壳配套：id / aria 由外壳作用域插槽传入。
	-->
	<view class="zy-input" :class="{ 'is-focused': focused, 'is-error': error, 'is-disabled': disabled }">
		<input
			class="zy-input-el"
			:id="id"
			:value="modelValue"
			:type="inputType"
			:password="password && !revealed"
			:placeholder="placeholder"
			:placeholder-style="placeholderStyle"
			:maxlength="maxlength"
			:disabled="disabled"
			:confirm-type="confirmType"
			:cursor-spacing="24"
			:aria-describedby="ariaDescribedby"
			:aria-invalid="error || ariaInvalid"
			:aria-label="ariaLabel"
			@input="onInput"
			@focus="focused = true; $emit('focus', $event)"
			@blur="focused = false; $emit('blur', $event)"
			@confirm="$emit('confirm', $event)"
		/>
		<AccessibleButton
			v-if="password"
			class="zy-input-eye"
			:aria-label="revealed ? '隐藏密码' : '显示密码'"
			:aria-pressed="revealed"
			:disabled="disabled"
			@click="revealed = !revealed"
		>
			<ZyIcon :name="revealed ? 'eye-off' : 'eye'" :size="32" tone="muted" />
		</AccessibleButton>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import AccessibleButton from './AccessibleButton.vue'
import ZyIcon from './ZyIcon.vue'

defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
const props = defineProps({
	modelValue: { type: [String, Number], default: '' },
	type: { type: String, default: 'text' },
	password: { type: Boolean, default: false },
	placeholder: { type: String, default: '' },
	maxlength: { type: Number, default: 140 },
	disabled: { type: Boolean, default: false },
	error: { type: Boolean, default: false },
	confirmType: { type: String, default: 'done' },
	id: { type: String, default: undefined },
	ariaDescribedby: { type: String, default: undefined },
	ariaInvalid: { type: Boolean, default: false },
	ariaLabel: { type: String, default: undefined },
})
const emit = defineEmits(['update:modelValue', 'focus', 'blur', 'confirm'])
const focused = ref(false)
const revealed = ref(false)
const inputType = computed(() => (props.password ? 'text' : props.type))
const placeholderStyle = 'color:#8B9994;font-size:28rpx'
function onInput(e) { emit('update:modelValue', e.detail.value) }
</script>

<style scoped>
.zy-input {
	position: relative;
	display: flex;
	align-items: center;
	min-height: 92rpx;
	/* v9：白底描边方框 + 聚焦光环，一眼看出「这里能填」 */
	padding: 0 24rpx;
	border: 2rpx solid var(--zy-border, #d3dbe7);
	border-radius: var(--zy-radius-control, 20rpx);
	background: var(--zy-card-bg, #fff);
	box-sizing: border-box;
	transition: border-color 150ms ease, box-shadow 150ms ease;
}
.zy-input-el {
	flex: 1;
	min-width: 0;
	min-height: 86rpx;
	padding: 0;
	margin: 0;
	background: transparent;
	border: 0;
	font-size: var(--zy-text-body-lg, 30rpx);
	line-height: 1.5;
	color: var(--zy-title, #111816);
}
/* 聚焦：窑青实线 + 上方浅青虚线（双线如一针穿过） */
.zy-input.is-focused { border-color: var(--zy-brand-600, #24478f); box-shadow: 0 0 0 6rpx var(--zy-brand-100, #dde6f6); }
.zy-input.is-error { border-color: var(--zy-care, #b03d33); box-shadow: 0 0 0 6rpx var(--zy-bg-tint-care, #fbeae7); }
.zy-input.is-disabled { background: var(--zy-bg-sunken, #eef2f7); }
.zy-input-eye {
	flex-shrink: 0;
	width: 72rpx;
	min-height: 88rpx;
	margin: 0;
	padding: 0;
	background: transparent;
	border: 0;
	display: flex;
	align-items: center;
	justify-content: center;
}
.zy-input-eye::after { border: 0; }
</style>
