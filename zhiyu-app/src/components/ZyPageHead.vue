<template>
	<!--
		ZyPageHead —— 统一页头（v9 青花瓷）：朱砂印章 + 宋体标题 + 一句副题 + 右侧动作。
		取代各页「英文眉题 + 大标题 + 副题」三段式，首屏更紧凑；右上角一抹海水纹作为青花底饰。
	-->
	<view class="zy-head">
		<view class="zy-head-wave zy-wave-bg" aria-hidden="true"></view>
		<view class="zy-head-row">
			<ZySeal v-if="seal" :text="seal" size="md" />
			<view class="zy-head-copy">
				<text class="zy-head-title">{{ title }}</text>
				<text v-if="subtitle" class="zy-head-sub">{{ subtitle }}</text>
			</view>
			<view class="zy-head-action"><slot name="action" /></view>
		</view>
		<slot />
	</view>
</template>

<script setup>
import ZySeal from './ZySeal.vue'
defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
defineProps({
	seal: { type: String, default: '' },
	title: { type: String, default: '' },
	subtitle: { type: String, default: '' },
})
</script>

<style scoped>
.zy-head { position: relative; padding: 12rpx 0 28rpx; margin-bottom: 8rpx; text-align: left; width: 100%; box-sizing: border-box; }
.zy-head-wave { position: absolute; left: -40rpx; right: -40rpx; bottom: 0; height: 170rpx; pointer-events: none; }
.zy-head-row { position: relative; display: flex; align-items: center; gap: 22rpx; }
.zy-head-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.zy-head-title { font-family: var(--zy-font-display, serif); font-size: 52rpx; font-weight: 700; line-height: 1.25; letter-spacing: 4rpx; color: var(--zy-title); }
.zy-head-sub { margin-top: 6rpx; font-size: var(--zy-text-caption, 24rpx); line-height: 1.6; color: var(--zy-muted); }
.zy-head-action { flex-shrink: 0; display: flex; align-items: center; gap: 12rpx; }
</style>
