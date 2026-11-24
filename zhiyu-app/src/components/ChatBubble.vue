<template>
	<!--
		ChatBubble —— 对话行（v7 一线 · 2026-11-16）
		默认仍是轻气泡；dark 用于「玄墨倾听室」：去掉气泡与阴影，消息按排版行阅读。
	-->
	<view class="zy-bubble-row" :class="[role === 'user' ? 'is-user' : 'is-ai', dark ? 'is-dark' : '']">
		<view v-if="role === 'ai'" class="zy-bubble-ava" aria-hidden="true">
			<XiaotuanPet :size="dark ? 24 : 30" />
		</view>
		<view class="zy-bubble-col">
			<view class="zy-bubble">
				<view v-if="typing" class="zy-typing" role="status" aria-label="小织正在输入">
					<ZySpinner :size="30" :tone="dark ? 'light' : 'brand'" />
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
import ZySpinner from './ZySpinner.vue'

defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
defineProps({
	role: { type: String, default: 'ai' }, // 'ai' | 'user'
	time: { type: String, default: '' },
	typing: Boolean,
	userInitial: { type: String, default: '同' },
	dark: Boolean,
})
</script>

<style scoped>
.zy-bubble-row { display: flex; align-items: flex-end; gap: 18rpx; margin-bottom: 36rpx; }
.zy-bubble-row.is-user { flex-direction: row-reverse; }
.zy-bubble-ava { width: 60rpx; height: 60rpx; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.zy-bubble-ava.is-letter { background: var(--zy-bg-tint-mint, #eef3fb); color: var(--zy-brand-700, var(--zy-brand-700, #24478f)); font-size:var(--zy-text-caption, 24rpx); font-weight: 600; }
.zy-bubble-col { max-width: 74%; display: flex; flex-direction: column; }
.zy-bubble-row.is-user .zy-bubble-col { align-items: flex-end; }
.zy-bubble { padding: 24rpx 32rpx; font-size: var(--zy-text-body-lg, 30rpx); line-height: 1.7; box-shadow: var(--zy-shadow-card); word-break: break-word; }
.is-ai .zy-bubble { background: var(--zy-card-bg, #ffffff); color: var(--zy-title, #33302a); border-radius: 8rpx 32rpx 32rpx 32rpx; }
.is-user .zy-bubble { background: var(--zy-brand-600, var(--zy-brand-700, #24478f)); color: #FFFFFF; border-radius: 32rpx 8rpx 32rpx 32rpx; }
.zy-bubble-time { margin-top: 10rpx; font-size: var(--zy-text-micro, 22rpx); color: var(--zy-text, #5c5648); }
.zy-typing { display: flex; align-items: center; padding: 8rpx 4rpx; }

/* ── 玄墨倾听室：不是气泡，是一行行被看见的话 ── */
.zy-bubble-row.is-dark { align-items: flex-start; gap: 14rpx; margin-bottom: 44rpx; }
.zy-bubble-row.is-dark.is-user { align-items: flex-end; }
.zy-bubble-row.is-dark .zy-bubble-ava { width: 48rpx; height: 48rpx; border-radius: var(--zy-radius-control, 18rpx); opacity: .78; }
.zy-bubble-row.is-dark .zy-bubble-ava.is-letter { background: transparent; color: var(--zy-d-text-secondary, #A9BBB2); font-weight: 400; font-size: 22rpx; }
.zy-bubble-row.is-dark .zy-bubble-col { max-width: 84%; }
.zy-bubble-row.is-dark .zy-bubble { padding: 0; background: transparent; box-shadow: none; border-radius: var(--zy-radius-control, 18rpx); font-size: 30rpx; line-height: 1.9; }
.is-dark.is-ai .zy-bubble { color: var(--zy-d-text, #F1F4F1); border-left: 1rpx solid rgba(157, 184, 236,.35); padding-left: 22rpx; }
.is-dark.is-user .zy-bubble { color: var(--zy-d-text-secondary, #A9BBB2); text-align: right; }
.zy-bubble-row.is-dark .zy-bubble-time { color: rgba(169,187,178,.55); }
.zy-bubble-row.is-dark .zy-typing { padding-left: 22rpx; }
</style>
