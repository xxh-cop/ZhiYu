<template>
	<!--
		StuckGuideBar —— 卡点引导轻提示条（2026-11-05 卡点引导，设计稿 §2）。
		形态红线：内嵌在页面正常流里（非 overlay、非弹窗、不遮罩），不盖住步骤操作区；
		不自动播报（可选陪伴，aria-live 会打断读屏流），出现时不抢焦点；
		文案由后端一次性下发（AI 或静态模板），前端不写引导语本身；
		框架文案只做中性标注，不判定用户状态（禁「你卡住了」类表述）。
	-->
	<view v-if="text" class="stuck-guide" role="note" aria-label="陪伴小提示">
		<view class="stuck-guide-main">
			<text class="stuck-guide-kicker">小织陪伴提示</text>
			<text class="stuck-guide-text">{{ text }}</text>
		</view>
		<view class="stuck-guide-actions">
			<AccessibleButton
				class="stuck-guide-btn stuck-guide-btn--dismiss"
				aria-label="收起这条提示，本次创作不再出现"
				:disabled="busy"
				@click="$emit('dismiss')"
			>收起</AccessibleButton>
			<AccessibleButton
				class="stuck-guide-btn stuck-guide-btn--off"
				:aria-busy="busy"
				:disabled="busy"
				aria-label="不再提示：永久关闭这类提示，可在个人设置中重新开启"
				@click="$emit('disable')"
			>{{ busy ? '正在保存…' : '不再提示' }}</AccessibleButton>
		</view>
	</view>
</template>

<script setup>
import AccessibleButton from './AccessibleButton.vue'

defineProps({
	/** 后端下发的引导文案；空白串由父层拦截，本组件拿到的一定是非空文案 */
	text: { type: String, default: '' },
	/** 「不再提示」写入中的忙态（防重复点击） */
	busy: { type: Boolean, default: false },
})
defineEmits(['dismiss', 'disable'])
</script>

<style scoped>
.stuck-guide {
	display: flex;
	align-items: center;
	justify-content: space-between;
	flex-wrap: wrap;
	gap: 12rpx 20rpx;
	padding: 18rpx 32rpx;
	background: var(--zy-bg-tint-mint, #eef3fb);
	border-left: 6rpx solid var(--zy-brand-300, var(--zy-brand-300, #8ea9dc));
	animation: stuck-guide-in var(--zy-duration-base, 250ms) var(--zy-ease-out, ease-out);
}
.stuck-guide-main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 4rpx;
}
.stuck-guide-kicker {
	font-size:var(--zy-text-micro, 22rpx);
	letter-spacing: 2rpx;
	color: var(--zy-brand-700, var(--zy-brand-700, #24478f));
}
.stuck-guide-text {
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 1.6;
	color: var(--zy-title, #233d34);
}
.stuck-guide-actions {
	flex-shrink: 0;
	display: flex;
	align-items: center;
	gap: 12rpx;
}
.stuck-guide-btn {
	margin: 0;
	min-height: 64rpx;
	padding: 8rpx 24rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 1.5;
}
.stuck-guide-btn::after { border: 0; }
.stuck-guide-btn--dismiss {
	background: var(--zy-brand-600, var(--zy-brand-600, #24478f));
	color: #fff;
}
.stuck-guide-btn--off {
	background: transparent;
	color: var(--zy-content, #4a5f54);
}
.stuck-guide-btn.is-disabled { opacity: 0.5; }
button:focus-visible { outline: 3px solid var(--zy-brand-700, var(--zy-brand-700, #24478f)); outline-offset: 3px; }
@media (prefers-reduced-motion: reduce) {
	.stuck-guide { animation: none; }
}
@keyframes stuck-guide-in {
	from { opacity: 0; transform: translateY(-8rpx); }
	to { opacity: 1; transform: translateY(0); }
}
</style>
