<template>
	<view class="ai-companion-root">
		<!-- 恢复按钮（小织隐藏时显示） -->
		<view v-if="isHidden" class="restore-dot" @click="onRestore">
			<XiaotuanPet :size="28" quiet aria-hidden="true" />
		</view>

		<!-- 隐藏区提示条（拖动时显示） -->
		<view
			v-if="isDragging && !isHidden"
			class="hide-zone-hint"
			:class="{ active: pos.bottom < 80 }"
		>
			<text class="hide-zone-text">{{ pos.bottom < 80 ? '松手即可收起' : '拖到此处收起' }}</text>
		</view>

		<view
			v-if="visible"
			class="ai-companion"
			:class="{ hidden: isHidden }"
			:style="{ right: pos.right + 'rpx', bottom: pos.bottom + 'rpx' }"
			@touchstart="onTouchStart"
			@touchmove.stop.prevent="onTouchMove"
			@touchend="onTouchEnd"
		>
		<!-- 点击气泡 -->
		<view
			v-if="showBubble && !isHidden"
			class="bubble"
			:class="'bubble-' + bubbleSide"
		>
			<text class="bubble-text">{{ bubbleContent }}</text>
		</view>

		<!-- 呼吸模式气泡 -->
		<view v-if="mode === 'breathing' && !showBubble" class="bubble bubble-left">
			<text class="bubble-text">{{ breathingText }}</text>
		</view>

		<!-- 织结小精灵，跟随 aiStore.petState；呼吸模式仅保留外层节奏 -->
		<!-- #ifdef H5 -->
		<view
			class="xiaozhi-wrapper"
			:class="{ breathing: mode === 'breathing' }"
			@mousedown="onMouseDown"
			@click="onCompanionClick"
		>
			<XiaotuanPet :size="70" :face="mode === 'breathing' ? 'rest' : ''" :quiet="mode === 'breathing'" class="xiaotuan-render" />
		</view>
		<!-- #endif -->

		<!-- #ifndef H5 -->
		<view
			class="xiaozhi-wrapper"
			:class="{ breathing: mode === 'breathing' }"
			@click="onCompanionClick"
		>
			<XiaotuanPet :size="70" :face="mode === 'breathing' ? 'rest' : ''" :quiet="mode === 'breathing'" class="xiaotuan-render" />
		</view>
		<!-- #endif -->
		</view>
	</view>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useAiCompanionStore } from '../stores/aiCompanion'
import XiaotuanPet from './XiaotuanPet.vue'

const props = defineProps({
	proactive: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['open-panel'])

const aiStore = useAiCompanionStore()
const pos = computed(() => aiStore.position)
const mode = computed(() => aiStore.mode)
const visible = computed(() => aiStore.visible)
const isHidden = computed(() => aiStore.isHidden)
const isDragging = computed(() => aiStore.isDragging)
const showBubble = computed(() => aiStore.showBubble)
const bubbleContent = computed(() => aiStore.bubbleText)
const bubbleSide = computed(() => aiStore.bubbleSide)
const breathingText = ref('跟着我，慢慢吸…')

// 全站共用织结小精灵；状态图片加载和淡入由渲染组件处理。

// 拖动状态
let dragging = false
let suppressClick = false  // 拖动结束后短暂抑制 click
let startX = 0
let startY = 0
let startRight = 0
let startBottom = 0
let screenWidth = 0

function onTouchStart(e) {
	const touch = e.touches[0]
	startX = touch.clientX
	startY = touch.clientY
	startRight = pos.value.right
	startBottom = pos.value.bottom
	dragging = false
	aiStore.setDragging(true)
}

function onTouchMove(e) {
	const touch = e.touches[0]
	const dx = startX - touch.clientX
	const dy = startY - touch.clientY
	if (Math.abs(dx) > 5 || Math.abs(dy) > 5) dragging = true
	if (!dragging) return

	if (!screenWidth) {
		screenWidth = uni.getSystemInfoSync().windowWidth
	}
	const scale = 750 / (screenWidth || 375)
	let newRight = startRight + dx * scale
	let newBottom = startBottom + dy * scale

	newRight = Math.max(0, Math.min(750 - 140, newRight))
	newBottom = Math.max(0, Math.min(1400, newBottom))

	aiStore.setPosition(newRight, newBottom)
}

function onTouchEnd() {
	aiStore.setDragging(false)
	// 拖到底部 → 隐藏
	if (pos.value.bottom < 80) {
		aiStore.hide()
	}
	// 如果发生了拖动，抑制紧随的 click 事件
	if (dragging) {
		suppressClick = true
		setTimeout(() => { suppressClick = false }, 100)
	}
	dragging = false
}

// 点击（未拖动）→ 打开聊天面板（点击视觉反馈由 XiaotuanPet 内置 squish 承担）
function onCompanionClick() {
	if (dragging || suppressClick) return

	// 只设 visible=false 隐藏小织，不动 isHidden（避免触发恢复按钮）
	setTimeout(() => {
		aiStore.hideBubble()
		aiStore.visible = false
		emit('open-panel')
	}, 150)
}

// 恢复按钮
function onRestore() {
	aiStore.unhide()
}

// H5 端鼠标拖拽
// #ifdef H5
function onMouseDown(e) {
	onTouchStart({ touches: [{ clientX: e.clientX, clientY: e.clientY }] })
	document.addEventListener('mousemove', handleMouseMove)
	document.addEventListener('mouseup', handleMouseUp)
}

function handleMouseMove(e) {
	onTouchMove({ touches: [{ clientX: e.clientX, clientY: e.clientY }] })
}

function handleMouseUp() {
	onTouchEnd()
	document.removeEventListener('mousemove', handleMouseMove)
	document.removeEventListener('mouseup', handleMouseUp)
}
// #endif

onMounted(() => {
	aiStore.loadPosition()

	if (props.proactive) {
		aiStore.enableProactive()
	}

	// #ifdef H5
	// 暴露 store 到 window，方便调试
	window.__aiStore = aiStore
	// #endif
})

onUnmounted(() => {
	if (phaseTimer) clearInterval(phaseTimer)

	if (props.proactive) {
		aiStore.disableProactive()
	}

	// #ifdef H5
	delete window.__aiStore
	// #endif
})

let phaseTimer = null
watch(mode, (newMode) => {
	if (newMode === 'breathing') {
		breathingText.value = '跟着我，慢慢吸…'
		phaseTimer = setInterval(() => {
			breathingText.value = breathingText.value.includes('吸') ? '慢慢呼…' : '跟着我，慢慢吸…'
		}, 5000)
	} else {
		if (phaseTimer) {
			clearInterval(phaseTimer)
			phaseTimer = null
		}
	}
})

// 面板打开时 visible=false → 暂停计时；关闭后恢复
watch(
	() => aiStore.visible,
	(v) => {
		if (!props.proactive) return
		if (!v) {
			aiStore.pauseIdle()
		} else {
			aiStore.resetIdle()
		}
	}
)
</script>

<style scoped>
/* 唯一根节点容器：不设 position/transform/overflow/filter，
   确保子元素的 position: fixed 仍相对视口定位。
   子元素全部 fixed 脱离文档流，此容器不占空间。 */
.ai-companion-root {
	width: 0;
	height: 0;
}

.ai-companion {
	position: fixed;
	z-index: 9999;
	pointer-events: none;
	transition: opacity var(--zy-duration-base), transform var(--zy-duration-base);
}

.ai-companion.hidden {
	opacity: 0;
	transform: scale(0.5);
	pointer-events: none;
}

.xiaozhi-wrapper {
	pointer-events: auto;
	width: 140rpx;
	height: 140rpx;
	position: relative;
	display: flex;
	align-items: center;
	justify-content: center;
	filter: drop-shadow(0 4rpx 8rpx rgba(33, 126, 118, 0.15));
}

.xiaotuan-render {
	user-select: none;
}

/* ── 问题 2 修法：气泡文字横排 ── */
.bubble {
	position: absolute;
	bottom: 100%;
	min-width: 200rpx;
	max-width: 400rpx;
	width: max-content;
	padding: 14rpx 24rpx;
	background: var(--zy-card-bg, #ffffff);
	border-radius: var(--zy-radius-md, 24rpx);
	box-shadow: var(--zy-shadow-md, 0 4rpx 16rpx rgba(33, 126, 118, 0.08));
	font-size: 26rpx;
	line-height: 1.5;
	color: var(--zy-title, #233d34);
	white-space: normal;
	word-break: keep-all;
	letter-spacing: 0;
	writing-mode: horizontal-tb;
	pointer-events: none;
	animation: bubbleIn var(--zy-duration-base, 250ms) var(--zy-ease-out, ease-out);
}

.bubble-text {
	font-size: 26rpx;
	color: var(--zy-title, #233d34);
	line-height: 1.5;
}

/* 气泡在小织左边（尖角指向右侧） */
.bubble-left {
	right: 100%;
	margin-right: 16rpx;
}

/* 气泡在小织右边（尖角指向左侧） */
.bubble-right {
	left: 100%;
	margin-left: 16rpx;
}

@keyframes bubbleIn {
	from {
		opacity: 0;
		transform: scale(0.9);
	}
	to {
		opacity: 1;
		transform: scale(1);
	}
}

/* ── 问题 3：拖动隐藏区提示条 ── */
.hide-zone-hint {
	position: fixed;
	left: 50%;
	bottom: 40rpx;
	transform: translateX(-50%);
	padding: 16rpx 40rpx;
	background: rgba(33, 126, 118, 0.15);
	border: 2rpx dashed rgba(33, 126, 118, 0.4);
	border-radius: var(--zy-radius-full, 9999rpx);
	pointer-events: none;
	z-index: 9998;
	animation: hideZoneIn var(--zy-duration-base, 250ms) var(--zy-ease-out, ease-out);
}

.hide-zone-text {
	font-size: 26rpx;
	color: var(--zy-brand-700, #217e76);
	white-space: nowrap;
}

.hide-zone-hint.active {
	background: var(--zy-primary, #217e76);
	border-color: transparent;
	transform: translateX(-50%) scale(1.05);
}

.hide-zone-hint.active .hide-zone-text {
	color: #ffffff;
}

@keyframes hideZoneIn {
	from {
		opacity: 0;
		transform: translateX(-50%) translateY(20rpx);
	}
	to {
		opacity: 1;
		transform: translateX(-50%) translateY(0);
	}
}

/* ── 恢复按钮 ── */
.restore-dot {
	position: fixed;
	right: 24rpx;
	bottom: 200rpx;
	width: 72rpx;
	height: 72rpx;
	border-radius: 50%;
	background: rgba(33, 126, 118, 0.15);
	display: flex;
	align-items: center;
	justify-content: center;
	z-index: 9999;
	box-shadow: var(--zy-shadow-sm, 0 2rpx 8rpx rgba(33, 126, 118, 0.06));
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
	pointer-events: auto;
}

.restore-dot:active {
	transform: scale(0.9);
}

.restore-emoji {
	font-size: 32rpx;
}

/* ── 呼吸动画 ── */
.xiaozhi-wrapper.breathing {
	animation: breathe 10s var(--zy-ease-in-out, ease-in-out) infinite;
}

@keyframes breathe {
	0% {
		transform: scale(1);
	}
	40% {
		transform: scale(1.06);
	}
	100% {
		transform: scale(1);
	}
}
</style>
