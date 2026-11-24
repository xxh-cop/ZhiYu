<template>
	<!--
		ZySeal —— 朱砂印章（v9 青花瓷）。
		1 字：方印；2 字：竖排；4 字：田字格。用于页头、徽章、作品落款。
		tone: cinnabar（默认，朱文白字）| blue（青花）| outline（白文，描边不填色，用于未达成）
	-->
	<view class="zy-seal" :class="['zy-seal--' + size, 'zy-seal--' + tone, 'zy-seal--n' + chars.length, { 'is-tilt': tilt }]" aria-hidden="true">
		<text v-for="(c, i) in chars" :key="i" class="zy-seal-char">{{ c }}</text>
	</view>
</template>

<script setup>
import { computed } from 'vue'
defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
const props = defineProps({
	text: { type: String, default: '织' },
	size: { type: String, default: 'md' }, // sm | md | lg | xl
	tone: { type: String, default: 'cinnabar' },
	tilt: Boolean,
})
const chars = computed(() => Array.from(props.text || '织').slice(0, 4))
</script>

<style scoped>
.zy-seal {
	flex-shrink: 0;
	display: flex;
	flex-direction: column;
	flex-wrap: wrap;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
	border-radius: 10rpx;
	font-family: var(--zy-font-display, "STKaiti", "KaiTi", "Songti SC", serif);
	font-weight: 700;
	line-height: 1;
	background: var(--zy-cinnabar, #c0392b);
	color: #fff;
	/* 印泥边框：内圈细白线 */
	box-shadow: inset 0 0 0 3rpx var(--zy-cinnabar, #c0392b), inset 0 0 0 5rpx rgba(255, 255, 255, 0.7);
}
.zy-seal.is-tilt { transform: rotate(-6deg); }
.zy-seal--blue { background: var(--zy-brand-600, #24478f); box-shadow: inset 0 0 0 3rpx var(--zy-brand-600, #24478f), inset 0 0 0 5rpx rgba(255, 255, 255, 0.7); }
.zy-seal--outline { background: transparent; color: var(--zy-faint, #8a93a6); box-shadow: inset 0 0 0 3rpx var(--zy-border, #d3dbe7); }
.zy-seal--n4 { flex-direction: row-reverse; }

.zy-seal--sm { width: 44rpx; height: 44rpx; font-size: 26rpx; }
.zy-seal--md { width: 68rpx; height: 68rpx; font-size: 40rpx; }
.zy-seal--lg { width: 96rpx; height: 96rpx; font-size: 56rpx; }
.zy-seal--xl { width: 128rpx; height: 128rpx; font-size: 72rpx; }
.zy-seal--n2.zy-seal--sm { font-size: 18rpx; }
.zy-seal--n2.zy-seal--md { font-size: 26rpx; }
.zy-seal--n2.zy-seal--lg { font-size: 38rpx; }
.zy-seal--n2.zy-seal--xl { font-size: 50rpx; }
.zy-seal--n4.zy-seal--md { font-size: 24rpx; }
.zy-seal--n4.zy-seal--lg { font-size: 36rpx; }
.zy-seal--n4.zy-seal--xl { font-size: 48rpx; }
.zy-seal--n4 .zy-seal-char { width: 50%; text-align: center; }
.zy-seal--n2 .zy-seal-char { margin: 1rpx 0; }
</style>
