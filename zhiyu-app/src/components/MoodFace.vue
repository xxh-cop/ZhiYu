<template>
	<!--
		MoodFace —— 心情打卡脸谱（暖纸青玉 v6 · 规格 §2-C6）。
		沿用首页现有 CSS 脸谱绘制，选中态改为青玉实线圈 + 薄荷 tint 底（200ms 过渡）。
		kind: 0 开心 / 1 平静 / 2 一般 / 3 有点累 / 4 不太好。
	-->
	<AccessibleButton
		class="zy-mood"
		:aria-pressed="selected"
		:aria-label="label"
		@click="$emit('select')"
	>
		<view class="zy-mood-ring">
			<view class="zy-mood-face" :class="'zy-mood-face--' + kind">
				<view class="zy-mood-eyes"></view>
				<view class="zy-mood-mouth"></view>
			</view>
		</view>
		<text class="zy-mood-label">{{ label }}</text>
	</AccessibleButton>
</template>

<script setup>
import AccessibleButton from './AccessibleButton.vue'

defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
defineProps({
	kind: { type: Number, default: 0 },
	label: { type: String, required: true },
	selected: Boolean,
})
defineEmits(['select'])
</script>

<style scoped>
.zy-mood { background: transparent; padding: 0 4rpx; margin: 0; line-height: 1.8; border-radius: 16rpx; min-height: 88rpx; display: flex; flex-direction: column; align-items: center; justify-content: flex-start; gap: 0; }
.zy-mood-ring { width: 92rpx; height: 92rpx; border-radius: 50%; border: 2rpx dashed transparent; display: flex; align-items: center; justify-content: center; margin: 0 auto 12rpx; transition: border-color 200ms ease-out, background-color 200ms ease-out; }
.zy-mood.is-selected .zy-mood-ring { border: 2rpx solid var(--zy-brand-600, #217e76); background: var(--zy-bg-tint-mint, #e4efe7); }
.zy-mood.is-selected .zy-mood-label { color: var(--zy-brand-700, #1a645e); font-weight: 500; }
.zy-mood-label { font-size: var(--zy-text-micro, 22rpx); color: var(--zy-text, #5c5648); }

/* ── 脸谱（与首页 P0 版一致的五官绘制） ── */
.zy-mood-face { position: relative; width: 68rpx; height: 68rpx; border-radius: 50%; background: #f7e9bf; }
.zy-mood-face--1 { background: var(--zy-bg-tint-mint, #e4efe7); }
.zy-mood-face--2 { background: var(--zy-bg-tint-warm, #f3e9cf); }
.zy-mood-face--3 { background: #e7e4ef; }
.zy-mood-face--4 { background: #e3e9ee; }
.zy-mood-eyes::before, .zy-mood-eyes::after { content: ''; position: absolute; width: 5rpx; height: 6rpx; border-radius: 50%; background: var(--zy-text, #5c5648); top: 26rpx; }
.zy-mood-eyes::before { left: 21rpx; }
.zy-mood-eyes::after { right: 21rpx; }
.zy-mood-mouth { position: absolute; left: 25rpx; top: 36rpx; width: 18rpx; height: 10rpx; border: 2rpx solid var(--zy-text, #5c5648); border-top: 0; border-radius: 0 0 20rpx 20rpx; }
.zy-mood-face--2 .zy-mood-mouth { height: 2rpx; border: 0; background: var(--zy-text, #5c5648); top: 41rpx; }
.zy-mood-face--3 .zy-mood-mouth { height: 9rpx; width: 8rpx; left: 30rpx; border: 2rpx solid var(--zy-text, #5c5648); border-radius: 50%; }
.zy-mood-face--4 .zy-mood-mouth { transform: rotate(180deg); top: 39rpx; }
</style>
