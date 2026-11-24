<template>
	<!--
		ChatInputBar —— 对话输入栏（v7 一线 · 2026-11-16）。
		dark=true 用于玄墨倾听室：去掉白色胶囊，改成一条输入线；免责声明由页面持有。
	-->
	<view class="zy-inputbar" :class="{ 'is-dark': dark }">
		<view class="zy-inputbar-pill">
			<input
				class="zy-inputbar-input"
				:value="modelValue"
				:placeholder="placeholder"
				:placeholder-style="dark ? 'color: rgba(169,187,178,.55)' : ''"
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
				<ZyIcon v-else name="mic" :size="34" :color="dark ? '#A7B1C6' : '#384256'" />
			</AccessibleButton>
			<AccessibleButton
				variant="primary"
				size="icon"
				class="zy-inputbar-send"
				aria-label="发送"
				:disabled="disabled || !canSend"
				@click="$emit('send')"
			><ZyIcon name="send" :size="30" color="#FFFFFF" /></AccessibleButton>
		</view>
	</view>
</template>

<script setup>
import { computed } from 'vue'
import AccessibleButton from './AccessibleButton.vue'
import ZyIcon from './ZyIcon.vue'

defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
const props = defineProps({
	modelValue: { type: String, default: '' },
	placeholder: { type: String, default: '说说你现在的感受…' },
	disabled: Boolean,
	voiceState: { type: String, default: '' }, // '' | 'recording' | 'processing'
	dark: Boolean,
})
const emit = defineEmits(['update:modelValue', 'send', 'voice'])
const canSend = computed(() => !!props.modelValue.trim())
function onInput(e) { emit('update:modelValue', e.detail.value) }
</script>

<style scoped>
.zy-inputbar { padding: 20rpx 32rpx 16rpx; }
.zy-inputbar-pill {
	display: flex;
	align-items: center;
	gap: 16rpx;
	/* v8：白色输入框 + 描边 + 圆角，一眼看出「在这里打字」 */
	background: var(--zy-card-bg, #fffdf8);
	border: 2rpx solid var(--zy-border, #d9cdb5);
	border-radius: 999rpx;
	padding: 8rpx 8rpx 8rpx 28rpx;
	box-shadow: var(--zy-shadow-card);
}
.zy-inputbar-send.zy-inputbar-send { border-radius: 999rpx; }
.zy-inputbar-input { flex: 1; min-height: 64rpx; font-size: var(--zy-text-body, 28rpx); color: var(--zy-title, #111816); background: transparent; }
.zy-inputbar-voice { width: 64rpx; height: 64rpx; min-width: 64rpx; min-height: 64rpx; border-radius: 50%; background: transparent; padding: 0; margin: 0; }
.zy-inputbar-voice-text { font-size: var(--zy-text-micro, 22rpx); color: var(--zy-muted, #6e7b75); }
.zy-inputbar-send { font-size:var(--zy-text-body-lg, 30rpx); color: #FFFFFF; }

/* 玄墨：同样是胶囊输入框，颜色随主题令牌（卡片底比页面底亮一档） */
.is-dark .zy-inputbar-input { color: var(--zy-title, #F1F4F1); }
.is-dark .zy-inputbar-voice-text { color: var(--zy-muted, #A9BBB2); }
</style>
