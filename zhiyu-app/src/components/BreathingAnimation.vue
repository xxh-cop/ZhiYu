<template>
	<view class="breathing-overlay" @click.stop>
		<view class="breathing-content">
			<text class="breathing-hint">{{ phase === 'inhale' ? '吸气…' : '呼气…' }}</text>
			<view class="breathing-circle" :class="{ 'circle-inhale': phase === 'inhale', 'circle-exhale': phase === 'exhale' }">
			</view>
			<text class="breathing-count">{{ currentCycle + 1 }} / 2</text>
		</view>
		<view class="skip-btn" @click="handleSkip">
			<text class="skip-text">跳过</text>
		</view>
	</view>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const emit = defineEmits(['finish'])

const phase = ref('inhale') // 'inhale' | 'exhale'
const currentCycle = ref(0)
let timer = null
let finished = false

function runCycle() {
	// 吸气 4s
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
.breathing-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(45, 155, 143, 0.85);
	z-index: 9999;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
}

.breathing-content {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 24px;
}

.breathing-hint {
	font-size: 18px;
	color: #FFFFFF;
	opacity: 0.95;
}

.breathing-circle {
	width: 120px;
	height: 120px;
	border-radius: 50%;
	background-color: rgba(255, 255, 255, 0.15);
	border: 2px solid rgba(255, 255, 255, 0.4);
	box-shadow: 0 0 60px rgba(255, 255, 255, 0.08);
	transform: scale(0.8);
	opacity: 0.5;
	transition: transform 4s ease-in-out, opacity 4s ease-in-out;
}

.circle-inhale {
	transform: scale(1.2);
	opacity: 1;
	transition: transform 4s ease-in-out, opacity 4s ease-in-out;
}

.circle-exhale {
	transform: scale(0.8);
	opacity: 0.5;
	transition: transform 6s ease-in-out, opacity 6s ease-in-out;
}

.breathing-count {
	font-size: 14px;
	color: rgba(255, 255, 255, 0.7);
}

.skip-btn {
	position: absolute;
	bottom: 60px;
	padding: 10px 32px;
	border: 1px solid rgba(255, 255, 255, 0.5);
	border-radius: 20px;
	background-color: transparent;
}

.skip-text {
	font-size: 14px;
	color: rgba(255, 255, 255, 0.85);
}
</style>
