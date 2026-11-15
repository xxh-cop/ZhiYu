<template>
	<view class="breathing-mask" @click.stop>
		<view class="breathing-sheet" role="dialog" aria-modal="true" aria-label="呼吸引导">
			<view class="sheet-head">
				<XiaotuanPet :size="88" face="rest" quiet />
				<view class="sheet-copy">
					<text class="sheet-title">先停一下，跟着小织呼吸</text>
					<text class="sheet-sub">不用做得完美，跟着圆圈慢慢来</text>
				</view>
			</view>
			<view class="breathing-stage">
				<view class="breathing-circle" :class="{ 'circle-inhale': phase === 'inhale', 'circle-exhale': phase === 'exhale' }"></view>
				<text class="breathing-hint" role="status">{{ phase === 'inhale' ? '吸气…' : '呼气…' }}</text>
				<text class="breathing-count">{{ currentCycle + 1 }} / 2</text>
			</view>
			<AccessibleButton class="skip-btn" @click="handleSkip">我好了，继续创作</AccessibleButton>
		</view>
	</view>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import XiaotuanPet from './XiaotuanPet.vue'
import AccessibleButton from './AccessibleButton.vue'

const emit = defineEmits(['finish'])

const phase = ref('inhale') // 'inhale' | 'exhale'
const currentCycle = ref(0)
let timer = null
let finished = false

function runCycle() {
	// 吸气 4s（AGENTS §7 固化节奏，勿改）
	phase.value = 'inhale'
	timer = setTimeout(() => {
		// 呼气 6s
		phase.value = 'exhale'
		timer = setTimeout(() => {
			currentCycle.value++
			if (currentCycle.value >= 2) {
				doFinish()
			} else {
				runCycle()
			}
		}, 6000)
	}, 4000)
}

function doFinish() {
	if (finished) return
	finished = true
	if (timer) {
		clearTimeout(timer)
		timer = null
	}
	emit('finish')
}

function handleSkip() {
	doFinish()
}

onMounted(() => {
	runCycle()
})

onUnmounted(() => {
	if (timer) {
		clearTimeout(timer)
		timer = null
	}
})
</script>

<style scoped>
.breathing-mask {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(20, 32, 28, 0.45);
	z-index: 9999;
	display: flex;
	flex-direction: column;
	justify-content: flex-end;
	animation: brFade 0.25s ease;
}

.breathing-sheet {
	background-color: var(--zy-card-bg, #ffffff);
	border-radius: var(--zy-radius-dialog, 48rpx) var(--zy-radius-dialog, 48rpx) 0 0;
	padding: 40rpx 40rpx calc(48rpx + env(safe-area-inset-bottom));
	box-shadow: var(--zy-shadow-xl);
	display: flex;
	flex-direction: column;
	animation: brSlideUp 0.35s var(--zy-ease-smooth, cubic-bezier(0.22, 1, 0.36, 1));
}

.sheet-head {
	display: flex;
	align-items: center;
	gap: 24rpx;
}

.sheet-copy {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.sheet-title {
	font-family: var(--zy-serif);
	font-size: 32rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
}

.sheet-sub {
	font-size: 24rpx;
	line-height: 1.6;
	color: var(--zy-content, #4a5f54);
}

.breathing-stage {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 24rpx;
	padding: 48rpx 0 32rpx;
}

.breathing-circle {
	width: 200rpx;
	height: 200rpx;
	border-radius: 50%;
	background-color: var(--zy-brand-100, #c9e4dd);
	border: 4rpx solid var(--zy-brand-300, #5bc4a8);
	box-shadow: var(--zy-shadow-md);
	transform: scale(0.8);
	opacity: 0.6;
	transition: transform 4s ease-in-out, opacity 4s ease-in-out;
}

.circle-inhale {
	transform: scale(1.15);
	opacity: 1;
	transition: transform 4s ease-in-out, opacity 4s ease-in-out;
}

.circle-exhale {
	transform: scale(0.8);
	opacity: 0.6;
	transition: transform 6s ease-in-out, opacity 6s ease-in-out;
}

.breathing-hint {
	font-size: 32rpx;
	font-weight: 500;
	letter-spacing: 4rpx;
	color: var(--zy-brand-700, #217e76);
}

.breathing-count {
	font-size: 24rpx;
	color: var(--zy-content, #4a5f54);
}

.skip-btn {
	align-self: center;
	margin: 0;
	padding: 16rpx 48rpx;
	border: 2rpx solid var(--zy-primary, #217e76);
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: transparent;
	color: var(--zy-brand-700, #217e76);
	font-size: 26rpx;
	line-height: 1.5;
}

.skip-btn::after { border: 0; }

@keyframes brFade {
	from { opacity: 0; }
	to { opacity: 1; }
}

@keyframes brSlideUp {
	from { opacity: 0.6; transform: translateY(80rpx); }
	to { opacity: 1; transform: translateY(0); }
}
</style>
