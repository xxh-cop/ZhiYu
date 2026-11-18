<template>
	<view class="sensory">
		<text class="kicker">GROUNDING · 五感</text>
		<text class="heading">感官落地练习</text>
		<text class="intro">睁开眼睛，慢慢注意身边。某个感官不方便时，可以跳过。</text>
		<view class="stage">
			<text class="number">{{steps[index].count}}</text>
			<text class="prompt">{{steps[index].text}}</text>
		</view>
		<view class="actions">
			<AccessibleButton :disabled="index===0" @click="index--">上一步</AccessibleButton>
			<AccessibleButton @click="index=index===4?0:index+1">{{index===4?'再走一遍':'下一步'}}</AccessibleButton>
		</view>
		<text class="intro ending">感到不适时可随时结束，回到自然呼吸。</text>
	</view>
</template>
<script setup>
import { ref } from 'vue'
import AccessibleButton from './AccessibleButton.vue'
const index = ref(0)
const steps = [
	{ count: '5', text: '找到五样你看得见的东西。留意它们的形状和颜色。' },
	{ count: '4', text: '注意四种触感。例如衣服、椅子、地面与手心。' },
	{ count: '3', text: '听见三种声音。远处与近处的声音都可以。' },
	{ count: '2', text: '留意两种熟悉的气味；没有闻到也没关系，无需寻找或接触材料。' },
	{ count: '1', text: '注意口中自然的味道，或者喝一小口水。不需要品尝创作材料。' }
]
</script>
<style scoped>
.sensory {
	padding: 28rpx 0;
	margin-top: 24rpx;
	background: transparent;
	border: 0;
	border-top: 1rpx solid var(--zy-border, #d3ded8);
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
	border-radius: 0;
	color: var(--zy-text, #42524c);
}
.kicker {
	display: block;
	font-size: var(--zy-text-micro, 22rpx);
	letter-spacing: 2px;
	color: var(--zy-muted, #6e7b75);
	margin-bottom: 12rpx;
}
.heading {
	display: block;
	font-family: var(--zy-font-display, "Songti SC", serif);
	font-size: var(--zy-text-title, 36rpx);
	font-weight: 500;
	line-height: 1.4;
	color: var(--zy-title, #111816);
}
.intro {
	display: block;
	font-size: var(--zy-text-caption, 24rpx);
	line-height: 1.8;
	color: var(--zy-muted, #6e7b75);
	margin-top: 14rpx;
}
.stage {
	display: flex;
	align-items: flex-start;
	gap: 26rpx;
	margin: 28rpx 0 8rpx;
	padding: 22rpx 0 22rpx 22rpx;
	border-left: 2rpx solid var(--zy-brand-600, #0e5b53);
}
.number {
	flex-shrink: 0;
	font-family: var(--zy-font-display, "Songti SC", serif);
	font-size: 96rpx;
	font-weight: 500;
	line-height: 1;
	color: var(--zy-brand-700, #0a403b);
	font-variant-numeric: tabular-nums;
}
.prompt {
	flex: 1;
	min-width: 0;
	font-size: var(--zy-text-body-lg, 30rpx);
	line-height: 1.75;
	color: var(--zy-title, #111816);
	padding-top: 6rpx;
}
.actions {
	display: flex;
	gap: 0;
	margin: 26rpx 0 22rpx;
}
.actions button {
	flex: 1;
	min-height: 80rpx;
	border-radius: 0;
	border: 1rpx solid var(--zy-border, #d3ded8);
	background: transparent;
	color: var(--zy-brand-700, #0a403b);
	font-size: var(--zy-text-caption, 24rpx);
	margin: 0 -1rpx 0 0;
}
.actions button:last-child {
	background: var(--zy-brand-600, #0e5b53);
	border-color: var(--zy-brand-600, #0e5b53);
	color: #fff;
}
.actions button.is-disabled {
	opacity: .35;
}
.ending {
	margin-top: 0;
}
</style>
