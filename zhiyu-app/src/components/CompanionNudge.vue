<template>
	<view
		v-if="open"
		class="nudge"
		:class="{ leaving }"
		:style="{ bottom: bottomOffset }"
		@touchstart="onTouchStart"
		@touchmove="onTouchMove"
		@touchend="onTouchEnd"
	>
		<XiaotuanPet :size="44" :face="face" aria-hidden="true" />
		<AccessibleButton class="nudge-main" :aria-label="text + '，点按打开倾听'" @click="$emit('action')">
			<text class="nudge-text">{{ text }}</text>
			<text class="nudge-action">{{ actionText }}</text>
		</AccessibleButton>
		<AccessibleButton class="nudge-close" aria-label="关闭陪伴提示" @click="dismiss"><ZyIcon name="close" :size="24" tone="muted" /></AccessibleButton>
	</view>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import XiaotuanPet from './XiaotuanPet.vue'
import AccessibleButton from './AccessibleButton.vue'
import ZyIcon from './ZyIcon.vue'

/**
 * CompanionNudge —— 小织「按需出现」轻提示（F2）。
 *
 * 与常驻悬浮件 AiCompanion 的区别：不常驻、不拖拽、不抢焦点。
 * - 自动退场：duration 毫秒后淡出（默认 9000，在 8–10s 区间内）；
 * - 手动退场：点 ✕ 或向右划过（>40px 位移）；
 * - 低打扰：纯视觉呈现，不用 aria-live 播报（它是可选陪伴而非状态变更，
 *   播报会打断读屏用户当前的阅读流）；出现时不调用任何 focus()，焦点原地不动；
 *   关闭按钮在 Tab 序中自然可达。
 * - 防遮挡：bottomOffset 由使用页按自己的底部操作栏计算传入。
 */
const props = defineProps({
	open: { type: Boolean, default: false },
	/** 一句话，≤20 字，禁止诊断性措辞 */
	text: { type: String, required: true },
	actionText: { type: String, default: '' },
	face: { type: String, default: 'care' },
	duration: { type: Number, default: 9000 },
	bottomOffset: { type: String, default: 'calc(140rpx + env(safe-area-inset-bottom))' },
})
const emit = defineEmits(['action', 'close'])

const leaving = ref(false)
let timer = null
let touchX = 0
let swiped = false

function dismiss() {
	if (leaving.value) return
	leaving.value = true
	clearTimeout(timer)
	// 与 CSS 退场动画时长一致后再通知父层移除
	setTimeout(() => emit('close'), 220)
}

function onTouchStart(e) {
	touchX = e.touches[0].clientX
	swiped = false
}
function onTouchMove(e) {
	if (e.touches[0].clientX - touchX > 40) swiped = true
}
function onTouchEnd() {
	if (swiped) dismiss()
}

watch(
	() => props.open,
	(open) => {
		clearTimeout(timer)
		leaving.value = false
		if (open) timer = setTimeout(dismiss, props.duration)
	}
)
onBeforeUnmount(() => clearTimeout(timer))
</script>

<style scoped>
.nudge {
	position: fixed;
	right: 24rpx;
	z-index: 50;
	display: flex;
	align-items: center;
	gap: 12rpx;
	max-width: 560rpx;
	padding: 16rpx 16rpx 16rpx 20rpx;
	box-sizing: border-box;
	background: var(--zy-card-bg, #fff);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-card, 32rpx);
	box-shadow: var(--zy-shadow-lg, 0 8rpx 24rpx rgba(33, 126, 118, 0.1));
	animation: nudge-in var(--zy-duration-base, 250ms) var(--zy-ease-out, ease-out);
}
.nudge.leaving {
	opacity: 0;
	transform: translateY(16rpx);
	transition: opacity 200ms var(--zy-ease-in, ease-in), transform 200ms var(--zy-ease-in, ease-in);
}
.nudge-main {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 2rpx;
	margin: 0;
	padding: 0;
	background: transparent;
	line-height: 1.5;
	text-align: left;
}
.nudge-main::after { border: 0; }
.nudge-text {
	font-size:var(--zy-text-caption, 24rpx);
	font-weight: 600;
	color: var(--zy-title, #233d34);
	white-space: nowrap;
}
.nudge-action {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-brand-600, var(--zy-brand-700, #1a645e));
}
.nudge-close {
	flex-shrink: 0;
	width: 88rpx;
	height: 88rpx;
	min-width: 44px;
	min-height: 44px;
	margin: -16rpx -16rpx -16rpx 0;
	padding: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	background: transparent;
	color: var(--zy-text, #4a5f54);
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 1;
}
.nudge-close::after { border: 0; }
button:focus-visible { outline: 3px solid var(--zy-brand-700, var(--zy-brand-700, #1a645e)); outline-offset: 3px; }
@media (prefers-reduced-motion: reduce) {
	.nudge { animation: none; }
	.nudge.leaving { transition: none; }
}
@keyframes nudge-in {
	from { opacity: 0; transform: translateY(24rpx); }
	to { opacity: 1; transform: translateY(0); }
}
</style>
