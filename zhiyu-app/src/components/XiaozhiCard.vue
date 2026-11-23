<template>
	<AccessibleButton class="xiaozhi-card" aria-label="和小织聊聊" @click="$emit('open')">
		<XiaotuanPet :size="72"/>
		<view class="xiaozhi-copy">
			<text class="xiaozhi-label">小织 · 你的创作伙伴</text>
			<text class="xiaozhi-title">想说了就聊两句。</text>
			<text class="xiaozhi-hint">或者，从一件小手作开始</text>
		</view>
		<ZyIcon name="arrow" :size="28" tone="brand" class="xiaozhi-arrow" />
	</AccessibleButton>
</template>

<script setup>
import XiaotuanPet from './XiaotuanPet.vue'
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
	background: var(--zy-card-bg, #fff);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-card, 32rpx);
	text-align: left;
	line-height: 1.6;
}
.xiaozhi-copy { flex: 1; min-width: 0; }
.xiaozhi-label { display: block; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-text, #4a5f54); }
.xiaozhi-title { display: block; font-size:var(--zy-text-body-lg, 30rpx); font-weight: 600; color: var(--zy-title, #233d34); margin-top: 4rpx; }
.xiaozhi-hint { display: block; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-text, #4a5f54); margin-top: 4rpx; }
.xiaozhi-arrow { font-size:var(--zy-text-body-lg, 30rpx); color: var(--zy-brand-700, var(--zy-brand-600, #217e76)); }
.xiaozhi-card::after { border: 0; }
.xiaozhi-card:active { opacity: .84; }
</style>
