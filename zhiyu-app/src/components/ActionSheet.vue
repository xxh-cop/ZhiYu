<template>
	<view v-if="open" class="sheet-mask" :class="{ closing }" :style="maskStyle" @click="requestClose">
		<view
			ref="panel"
			class="sheet-panel"
			:class="{ closing }"
			:style="panelStyle"
			role="dialog"
			aria-modal="true"
			:aria-label="title"
			tabindex="-1"
			@click.stop
		>
			<view class="sheet-grabber" aria-hidden="true" @touchstart="onDragStart" @touchmove.stop.prevent="onDragMove" @touchend="onDragEnd" @touchcancel="onDragEnd" />
			<view class="sheet-heading" @touchstart="onDragStart" @touchmove.stop.prevent="onDragMove" @touchend="onDragEnd" @touchcancel="onDragEnd">
				<text class="sheet-title">{{ title }}</text>
				<AccessibleButton class="sheet-close" aria-label="关闭面板" @click="requestClose"><ZyIcon name="close" :size="28" tone="muted" /></AccessibleButton>
			</view>
			<text v-if="description" class="sheet-description">{{ description }}</text>
			<slot />
			<view v-if="actions.length" class="sheet-actions">
				<AccessibleButton
					v-for="action in actions"
					:key="action.key"
					class="sheet-action"
					:class="['tone-' + (action.tone || 'default'), { disabled: action.disabled }]"
					:disabled="action.disabled || !!action.busy"
					:loading="!!action.busy"
					@click="$emit('select', action.key)"
				>
					<text class="action-label">{{ action.label }}</text>
					<text v-if="action.description" class="action-description">{{ action.description }}</text>
				</AccessibleButton>
			</view>
			<AccessibleButton v-if="cancelText" class="sheet-cancel" :disabled="busy" @click="requestClose">{{ cancelText }}</AccessibleButton>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import ZyIcon from './ZyIcon.vue'
import AccessibleButton from './AccessibleButton.vue'

/**
 * ActionSheet —— 底部动作面板（计划 §3.2），替代零散的 view 弹层。
 * 支持两种用法：
 *  1) actions 数组：标准动作列表（danger/primary/default 三种语气）；
 *  2) 默认插槽：自定义内容（如分享面板），此时可省略 actions。
 * H5 提供 Escape 关闭、初始聚焦与焦点归还；小程序端降级为纯触摸交互。
 */
const props = defineProps({
	open: { type: Boolean, default: false },
	title: { type: String, required: true },
	description: { type: String, default: '' },
	actions: { type: Array, default: () => [] },
	cancelText: { type: String, default: '' },
	busy: { type: Boolean, default: false },
})
const emit = defineEmits(['select', 'close'])
const panel = ref(null)
const dragY = ref(0)
const dragging = ref(false)
const closing = ref(false)
let startY = 0, prevY = 0, prevT = 0, lastVelocity = 0, closeTimer = 0
const reduceMotion = typeof window !== 'undefined' && window.matchMedia
	? window.matchMedia('(prefers-reduced-motion: reduce)').matches
	: false

const panelStyle = computed(() => ({
	transform: closing.value ? 'translateY(100%)' : dragY.value ? `translateY(${dragY.value}px)` : '',
	transition: dragging.value ? 'none' : ''
}))
const maskStyle = computed(() => ({
	opacity: closing.value ? 0 : String(1 - Math.min(dragY.value / 600, 0.55)),
	transition: dragging.value ? 'none' : ''
}))

function onDragStart(e) {
	if (props.busy || closing.value) return
	const t = e.touches && e.touches[0]
	if (!t) return
	dragging.value = true
	startY = prevY = t.clientY
	prevT = Date.now()
	lastVelocity = 0
}
function onDragMove(e) {
	if (!dragging.value) return
	const t = e.touches && e.touches[0]
	if (!t) return
	const now = Date.now()
	lastVelocity = (t.clientY - prevY) / Math.max(1, now - prevT)
	dragY.value = Math.max(0, t.clientY - startY)
	prevY = t.clientY
	prevT = now
}
function onDragEnd() {
	if (!dragging.value) return
	dragging.value = false
	// 位移超过 110px，或下滑速度超过 0.3px/ms，即关闭
	if (dragY.value > 110 || lastVelocity > 0.3) { closeWithAnimation(); return }
	dragY.value = 0
}
function closeWithAnimation() {
	if (reduceMotion) { dragY.value = 0; emit('close'); return }
	closing.value = true
	clearTimeout(closeTimer)
	closeTimer = setTimeout(() => { closing.value = false; dragY.value = 0; emit('close') }, 260)
}

function requestClose() {
	if (props.busy) return
	closeWithAnimation()
}
watch(() => props.open, (open) => {
	if (!open) {
		clearTimeout(closeTimer)
		closing.value = false
		dragY.value = 0
		dragging.value = false
	}
})
onBeforeUnmount(() => clearTimeout(closeTimer))

// #ifdef H5
let previousFocus, previousOverflow, element
function focusables() {
	// uni-app H5 把 <button> 编译为 tabIndex=-1 的 <uni-button>（原生不可聚焦）；
	// 可聚焦候选 = 显式带 tabindex≥0 的元素（AccessibleButton 根）+ 原生 button。
	return [...(element?.querySelectorAll('button:not([disabled]),[tabindex]') || [])]
		.filter((el) => el.tabIndex >= 0 && !el.disabled && el.getAttribute('aria-disabled') !== 'true' && el.getClientRects().length)
}
function keydown(event) {
	if (event.key === 'Escape') { event.preventDefault(); requestClose(); return }
	if (event.key !== 'Tab') return
	const items = focusables()
	if (!items.length) { event.preventDefault(); return }
	const first = items[0], last = items[items.length - 1]
	if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
	else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
function release() {
	document.removeEventListener('keydown', keydown, true)
	if (previousOverflow !== undefined) document.body.style.overflow = previousOverflow
	previousOverflow = undefined
	element = null
	if (previousFocus?.isConnected) previousFocus.focus()
	previousFocus = null
}
watch(() => props.open, async (open) => {
	if (!open) { release(); return }
	previousFocus = document.activeElement
	previousOverflow = document.body.style.overflow
	await nextTick()
	if (!props.open) return
	element = panel.value?.$el || panel.value
	document.body.style.overflow = 'hidden'
	document.addEventListener('keydown', keydown, true)
	focusables()[0]?.focus()
})
onBeforeUnmount(release)
// #endif
</script>

<style scoped>
.sheet-mask {
	position: fixed;
	inset: 0;
	z-index: 100;
	background: rgba(24, 54, 43, 0.4);
	display: flex;
	align-items: flex-end;
	justify-content: center;
	transition: opacity 220ms ease;
}
.sheet-panel {
	width: 100%;
	max-width: 560px;
	box-sizing: border-box;
	max-height: 85vh;
	overflow-y: auto;
	background: var(--zy-card-bg, #fff);
	border-top: 2rpx solid var(--zy-care, #A64842);
	border-radius: var(--zy-radius-control, 18rpx);
	padding: 24rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
	animation: sheet-rise var(--zy-duration-base, 250ms) var(--zy-ease-out, ease-out);
	transition: transform 320ms cubic-bezier(0.22, 1, 0.36, 1);
	will-change: transform;
}
.sheet-panel.closing { transition: transform 260ms cubic-bezier(0.4, 0, 1, 1); }
@media (prefers-reduced-motion: reduce) {
	.sheet-panel { animation: none; transition: none; }
	.sheet-mask { transition: none; }
}
.sheet-grabber {
	width: 72rpx;
	height: 8rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-border, #dfe7dc);
	margin: 0 auto 20rpx;
	cursor: grab;
	touch-action: none;
}
.sheet-heading { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; cursor: grab; touch-action: none; }
.sheet-title {
	font-size: var(--zy-font-md, 32rpx);
	font-weight: 600;
	color: var(--zy-title, #233d34);
	overflow-wrap: anywhere;
}
.sheet-close {
	flex-shrink: 0;
	width: 56rpx;
	height: 56rpx;
	min-height: 44px;
	padding: 0;
	margin: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	background: transparent;
	border-radius: var(--zy-radius-control, 18rpx);
	color: var(--zy-text-secondary, #4a5f54);
	font-size:var(--zy-text-body, 28rpx);
	line-height: 1;
}
.sheet-close::after { border: 0; }
.sheet-description {
	display: block;
	margin-top: 12rpx;
	font-size: var(--zy-font-sm, 24rpx);
	color: var(--zy-text-secondary, #4a5f54);
	line-height: 1.7;
	overflow-wrap: anywhere;
}
.sheet-actions { display: flex; flex-direction: column; gap: 16rpx; margin-top: 28rpx; }
.sheet-action {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 6rpx;
	width: 100%;
	min-height: 44px;
	margin: 0;
	padding: 24rpx 28rpx;
	border-radius: var(--zy-radius-control, 18rpx);
	background: var(--zy-bg-tint-mint, #eef3fb);
	line-height: 1.5;
	text-align: left;
}
.sheet-action::after { border: 0; }
.sheet-action.tone-default { background: transparent; border: 1rpx solid var(--zy-border, #d3ded8); color: var(--zy-title, #111816); }
.sheet-action.tone-primary { background: var(--zy-brand-600, var(--zy-brand-700, #24478f)); color: #fff; }
.sheet-action.tone-danger { background: transparent; border: 1rpx solid var(--zy-care, var(--zy-care, #a64842)); color: var(--zy-care, var(--zy-care, #a64842)); }
.sheet-action.disabled { opacity: .5; }
.sheet-action.is-disabled { opacity: .55; }
.action-label { font-size: var(--zy-font-base, 28rpx); font-weight: 600; }
.action-description { font-size: var(--zy-font-sm, 24rpx); opacity: .85; }
.sheet-cancel {
	width: 100%;
	min-height: 44px;
	margin: 20rpx 0 0;
	padding: 20rpx;
	border-radius: var(--zy-radius-control, 18rpx);
	background: var(--zy-bg, #ffffff);
	color: var(--zy-text-secondary, #4a5f54);
	font-size: var(--zy-font-base, 28rpx);
	line-height: 1.5;
}
.sheet-cancel::after { border: 0; }
button:focus-visible { outline: 3px solid var(--zy-brand-700, var(--zy-brand-700, #24478f)); outline-offset: 3px; }
@keyframes sheet-rise {
	from { transform: translateY(32rpx); opacity: 0; }
	to { transform: translateY(0); opacity: 1; }
}
</style>
