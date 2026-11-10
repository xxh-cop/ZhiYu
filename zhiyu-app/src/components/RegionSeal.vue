<template>
	<view
		v-if="region"
		class="region-seal"
		:class="'region-seal--' + size"
		role="img"
		:aria-label="'地域印记 ' + region"
	>
		<text class="region-seal-text zy-t-serif">{{ region }}</text>
	</view>
</template>

<script setup>
defineProps({
	region: { type: String, default: '' },
	size: { type: String, default: 'sm' },
})
</script>

<style scoped>
.region-seal {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	padding: 6rpx 10rpx;
	border: 2rpx solid var(--zy-gold);
	border-radius: var(--zy-radius-xs);
	background: transparent;
	transform: rotate(-2deg);
}
.region-seal-text {
	color: var(--zy-accent);
	font-weight: 600;
	letter-spacing: 2rpx;
	line-height: 1.2;
}
.region-seal--sm .region-seal-text {
	font-size: var(--zy-text-micro);
}
.region-seal--md .region-seal-text {
	font-size: var(--zy-text-caption);
}
</style>
