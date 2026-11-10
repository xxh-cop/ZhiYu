<template>
	<view class="process-strip" role="list" aria-label="工序步骤">
		<view
			v-for="(step, index) in steps"
			:key="index"
			class="process-step"
			:class="{ 'is-current': index === current, 'is-done': current > index }"
			role="listitem"
			:aria-current="index === current ? 'step' : undefined"
		>
			<view class="process-motif" :class="'motif-' + family" aria-hidden="true">
				<view class="motif-a"></view>
				<view class="motif-b"></view>
			</view>
			<text class="process-index zy-t-serif">{{ index + 1 }}</text>
			<text class="process-text">{{ step }}</text>
		</view>
	</view>
</template>

<script setup>
defineProps({
	steps: { type: Array, default: () => [] },
	current: { type: Number, default: -1 },
	family: { type: String, default: 'weave' },
})
</script>

<style scoped>
.process-strip {
	display: flex;
	gap: var(--zy-space-sm);
}
.process-step {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 8rpx;
	padding: var(--zy-space-sm);
	border: 2rpx solid var(--zy-card-line);
	border-radius: var(--zy-radius-md);
	background: var(--zy-card-bg);
}
.process-step.is-current {
	background: var(--zy-bg-tint-warm);
	border-color: var(--zy-gold);
}
.process-step.is-done {
	background: var(--zy-bg-tint-mint);
}
.process-index {
	font-size: var(--zy-text-title);
	font-weight: 600;
	color: var(--zy-accent);
	line-height: 1;
}
.process-text {
	font-size: var(--zy-text-caption);
	color: var(--zy-text);
	line-height: 1.6;
}

/* 材质微示意：纯 CSS，四族各一种工艺逻辑 */
.process-motif {
	position: relative;
	width: 56rpx;
	height: 56rpx;
}
.motif-a,
.motif-b {
	position: absolute;
}
/* 编织：经纬交替 */
.motif-weave .motif-a {
	left: 0;
	right: 0;
	top: 16rpx;
	height: 8rpx;
	background: var(--zy-brand-600);
}
.motif-weave .motif-b {
	top: 0;
	bottom: 0;
	left: 24rpx;
	width: 8rpx;
	background: var(--zy-gold);
}
/* 针线：走向虚线 */
.motif-stitch .motif-a {
	left: 4rpx;
	right: 4rpx;
	top: 26rpx;
	border-top: 4rpx dashed var(--zy-brand-600);
}
.motif-stitch .motif-b {
	left: 24rpx;
	top: 18rpx;
	width: 8rpx;
	height: 8rpx;
	border-radius: 50%;
	background: var(--zy-gold);
}
/* 剪刻：负空间 */
.motif-cutout .motif-a {
	top: 4rpx;
	right: 4rpx;
	bottom: 4rpx;
	left: 4rpx;
	background: var(--zy-care);
	border-radius: var(--zy-radius-xs);
}
.motif-cutout .motif-b {
	left: 18rpx;
	top: 18rpx;
	width: 20rpx;
	height: 20rpx;
	background: var(--zy-card-bg);
	border-radius: 50%;
}
/* 器面：青花分层环 */
.motif-porcelain .motif-a {
	top: 4rpx;
	right: 4rpx;
	bottom: 4rpx;
	left: 4rpx;
	border: 4rpx solid var(--zy-brand-600);
	border-radius: 50%;
}
.motif-porcelain .motif-b {
	left: 18rpx;
	top: 18rpx;
	width: 20rpx;
	height: 20rpx;
	border: 3rpx solid var(--zy-gold);
	border-radius: 50%;
}
</style>
