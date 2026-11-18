<template>
	<!--
		SectionHead —— 区块标题（暖纸青玉 v6 · 规格 §2-C5）。
		eyebrow 为英文大写小标：plain 语境用正文色，heritage 语境用深金。
		右侧操作位走默认 slot（建议放 AccessibleButton variant="ghost"）。
	-->
	<view class="zy-section-head">
		<view class="zy-section-main">
			<text v-if="eyebrow" class="zy-section-eyebrow" :class="{ 'is-heritage': tone === 'heritage' }">{{ eyebrow }}</text>
			<text class="zy-section-title">{{ title }}</text>
		</view>
		<slot />
	</view>
</template>

<script setup>
defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
defineProps({
	eyebrow: { type: String, default: '' },
	title: { type: String, required: true },
	tone: { type: String, default: 'plain' }, // 'plain' | 'heritage'
})
</script>

<style scoped>
.zy-section-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16rpx; margin-bottom: 24rpx; }
.zy-section-main { min-width: 0; }
.zy-section-eyebrow { display: block; font-size: var(--zy-text-micro, 22rpx); letter-spacing: 2rpx; color: var(--zy-muted, #6e7b75); margin-bottom: 8rpx; }
.zy-section-eyebrow.is-heritage { color: var(--zy-muted, #6e7b75); }
.zy-section-title { display: block; font-family: var(--zy-font-display, serif); font-size: var(--zy-text-display, 56rpx); font-weight: 500; line-height: 1.3; letter-spacing: 0.5rpx; color: var(--zy-title, #111816); }
</style>
