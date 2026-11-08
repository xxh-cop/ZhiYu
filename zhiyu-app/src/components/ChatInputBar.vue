<template>
	<!--
		ChatInputBar —— 对话输入栏（暖纸青玉 v6 · 规格 §2-C8）。
		暖白胶囊容器：输入框 + 语音钮（wd-icon，替换 🎤 emoji）+ 青玉圆形发送钮。
		免责声明由页面持有，不在本组件内。
	-->
	<view class="zy-inputbar">
		<view class="zy-inputbar-pill">
			<input
				class="zy-inputbar-input"
				:value="modelValue"
				:placeholder="placeholder"
				:disabled="disabled"
				confirm-type="send"
				:aria-label="placeholder"
				@input="onInput"
				@confirm="$emit('send')"
			/>
			<AccessibleButton
				class="zy-inputbar-voice"
				:aria-label="voiceState === 'recording' ? '停止录音' : '语音输入'"
				:aria-pressed="voiceState === 'recording'"
				:disabled="disabled || voiceState === 'processing'"
				@click="$emit('voice')"
			>
				<text v-if="voiceState === 'recording'" class="zy-inputbar-voice-text">停止</text>
				<text v-else-if="voiceState === 'processing'" class="zy-inputbar-voice-text">…</text>
				<wd-icon v-else name="service" size="34rpx" color="#5C5648" />
			</AccessibleButton>
			<AccessibleButton
				variant="primary"
				size="icon"
				class="zy-inputbar-send"
				aria-label="发送"
				:disabled="disabled || !canSend"
				@click="$emit('send')"
			>↑</AccessibleButton>
		</view>
	</view>
</template>

<script setup>
import { computed } from 'vue'
import AccessibleButton from './AccessibleButton.vue'

defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
const props = defineProps({
	modelValue: { type: String, default: '' },
	placeholder: { type: String, default: '说说你现在的感受…' },
	disabled: Boolean,
	voiceState: { type: String, default: '' }, // '' | 'recording' | 'processing'
})
const emit = defineEmits(['update:modelValue', 'send', 'voice'])
const canSend = computed(() => !!props.modelValue.trim())
function onInput(e) { emit('update:modelValue', e.detail.value) }
</script>

<style scoped>
.zy-inputbar { padding: 20rpx 28rpx 16rpx; }
.zy-inputbar-pill {
	display: flex;
	align-items: center;
	gap: 16rpx;
	background: var(--zy-card-bg, #fffdf7);
	border-radius: var(--zy-radius-full, 9999rpx);
	padding: 12rpx 16rpx 12rpx 32rpx;
	box-shadow: var(--zy-shadow-card);
}
.zy-inputbar-input { flex: 1; min-height: 64rpx; font-size: var(--zy-text-body, 28rpx); color: var(--zy-title, #33302a); background: transparent; }
.zy-inputbar-voice { width: 64rpx; height: 64rpx; min-width: 64rpx; min-height: 64rpx; border-radius: 50%; background: transparent; padding: 0; margin: 0; }
.zy-inputbar-voice-text { font-size: var(--zy-text-micro, 22rpx); color: var(--zy-text, #5c5648); }
.zy-inputbar-send { font-size: 30rpx; color: #FFFDF7; }
</style>
