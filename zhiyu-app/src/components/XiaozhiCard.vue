<template>
	<AccessibleButton class="xiaozhi-card" aria-label="和小织聊聊" @click="$emit('open')">
		<view class="xiaozhi-ava" aria-hidden="true"><image class="xiaozhi-img" src="/static/mascot/xiaozhi-welcome.png" mode="aspectFit" /></view>
		<view class="xiaozhi-copy">
			<text class="xiaozhi-label">小织 · 你的创作伙伴</text>
			<text class="xiaozhi-title">想说了就聊两句。</text>
			<text class="xiaozhi-hint">或者，从一件小手作开始</text>
		</view>
		<ZyIcon name="arrow" :size="28" tone="brand" class="xiaozhi-arrow" />
	</AccessibleButton>
</template>

<script setup>
import AccessibleButton from './AccessibleButton.vue'
import ZyIcon from './ZyIcon.vue'

defineEmits(['open'])
</script>

<style scoped>
.xiaozhi-card {
	display: flex;
	align-items: center;
	gap: 20rpx;
	width: 100%;
	margin-top: 24rpx;
	padding: 22rpx 26rpx;
	/* v9：青花浅底 + 实心小织头像，作为「陪伴」入口一眼可辨 */
	background: var(--zy-brand-50, #eef3fb);
	border: 0;
	border-radius: var(--zy-radius-card, 28rpx);
	box-shadow: var(--zy-shadow-card);
	text-align: left;
	line-height: 1.6;
}
.xiaozhi-ava { width: 112rpx; height: 112rpx; flex-shrink: 0; border-radius: 50%; background: var(--zy-card-bg, #fff); display: flex; align-items: center; justify-content: center; overflow: hidden; }
.xiaozhi-img { width: 104rpx; height: 104rpx; }
.xiaozhi-copy { flex: 1; min-width: 0; }
.xiaozhi-label { display: block; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-text, #4a5f54); }
.xiaozhi-title { display: block; font-size:var(--zy-text-body-lg, 30rpx); font-weight: 600; color: var(--zy-title, #233d34); margin-top: 4rpx; }
.xiaozhi-hint { display: block; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-text, #4a5f54); margin-top: 4rpx; }
.xiaozhi-arrow { font-size:var(--zy-text-body-lg, 30rpx); color: var(--zy-brand-700, var(--zy-brand-600, #24478f)); }
.xiaozhi-card::after { border: 0; }
.xiaozhi-card:active { opacity: .84; }
</style>
