<template>
	<!--
		ChatBubble —— 对话气泡（暖纸青玉 v6 · 规格 §2-C3）
		折角圆角指示说话人（AI 左上小角 / 用户右上小角），无描边、暖墨轻影；
		流式中由 typing 显示三点呼吸（reduced-motion 下由全局 .motion-reduced 关停动画）。
		WXSS 合规：仅类选择器；三点延迟用内联 style，不用 :nth-child。
	-->
	<view class="zy-bubble-row" :class="role === 'user' ? 'is-user' : 'is-ai'">
		<view v-if="role === 'ai'" class="zy-bubble-ava" aria-hidden="true">
			<XiaotuanPet :size="30" />
		</view>
		<view class="zy-bubble-col">
			<view class="zy-bubble">
				<view v-if="typing" class="zy-typing-dots" aria-label="正在输入">
					<view class="zy-typing-dot"></view>
					<view class="zy-typing-dot" style="animation-delay: 180ms"></view>
					<view class="zy-typing-dot" style="animation-delay: 360ms"></view>
				</view>
				<text v-else class="zy-bubble-text"><slot /></text>
			</view>
			<text v-if="time" class="zy-bubble-time">{{ time }}</text>
		</view>
		<view v-if="role === 'user'" class="zy-bubble-ava is-letter" aria-hidden="true">{{ userInitial }}</view>
	</view>
</template>

<script setup>
import XiaotuanPet from './XiaotuanPet.vue'

defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
defineProps({
	role: { type: String, default: 'ai' }, // 'ai' | 'user'
	time: { type: String, default: '' },
	typing: Boolean,
	userInitial: { type: String, default: '同' },
})
</script>

<style scoped>
.zy-bubble-row { display: flex; align-items: flex-end; gap: 18rpx; margin-bottom: 36rpx; }
.zy-bubble-row.is-user { flex-direction: row-reverse; }
.zy-bubble-ava { width: 60rpx; height: 60rpx; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.zy-bubble-ava.is-letter { background: var(--zy-bg-tint-mint, #e4efe7); color: var(--zy-brand-700, #1a645e); font-size: 26rpx; font-weight: 600; }
.zy-bubble-col { max-width: 74%; display: flex; flex-direction: column; }
.zy-bubble-row.is-user .zy-bubble-col { align-items: flex-end; }
.zy-bubble { padding: 24rpx 32rpx; font-size: var(--zy-text-body-lg, 30rpx); line-height: 1.7; box-shadow: var(--zy-shadow-card); word-break: break-word; }
.is-ai .zy-bubble { background: var(--zy-card-bg, #fffdf7); color: var(--zy-title, #33302a); border-radius: 8rpx 32rpx 32rpx 32rpx; }
.is-user .zy-bubble { background: var(--zy-brand-600, #1A645E); color: #FFFDF7; border-radius: 32rpx 8rpx 32rpx 32rpx; }
.zy-bubble-time { margin-top: 10rpx; font-size: var(--zy-text-micro, 22rpx); color: var(--zy-text, #5c5648); }
.zy-typing-dots { display: flex; gap: 8rpx; align-items: center; padding: 8rpx 4rpx; }
.zy-typing-dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--zy-brand-300, #5bc4a8); animation: zyTypingDots 1400ms var(--zy-ease-in-out, ease-in-out) infinite; }
@keyframes zyTypingDots {
	0%, 100% { opacity: .35; transform: scale(.85); }
	50% { opacity: 1; transform: scale(1); }
}
</style>
