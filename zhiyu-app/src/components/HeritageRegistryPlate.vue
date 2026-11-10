<template>
	<view
		v-if="tradition && (tradition.registryCode || tradition.listedYear)"
		class="registry-plate"
		role="group"
		:aria-label="'名录工艺 ' + (tradition.name || '')"
	>
		<text class="registry-code zy-t-serif">{{ tradition.registryCode || '名录工艺' }}</text>
		<view class="registry-meta">
			<text class="registry-name">{{ tradition.name }}</text>
			<text v-if="tradition.listedYear" class="registry-year">{{ tradition.listedYear }} 年列入名录</text>
		</view>
	</view>
</template>

<script setup>
defineProps({
	tradition: { type: Object, default: null },
})
</script>

<style scoped>
.registry-plate {
	display: flex;
	align-items: center;
	gap: var(--zy-space-md);
	padding: var(--zy-space-sm) 0 var(--zy-space-sm) var(--zy-space-md);
	border-left: 2rpx dashed var(--zy-stitch);
}
.registry-code {
	flex-shrink: 0;
	font-size: var(--zy-text-title-lg);
	font-weight: 600;
	color: var(--zy-accent);
	letter-spacing: 1rpx;
}
.registry-meta {
	display: flex;
	flex-direction: column;
	gap: 4rpx;
	min-width: 0;
}
.registry-name {
	font-size: var(--zy-text-body);
	font-weight: 600;
	color: var(--zy-title);
}
.registry-year {
	font-size: var(--zy-text-caption);
	color: var(--zy-text);
}
</style>
