<template>
	<view class="toast-stack" aria-live="polite">
		<view
			v-for="item in state.items"
			:key="item.id"
			class="toast-item"
			:class="'tone-' + item.tone"
			role="status"
		>
			<!-- v9.1：白色浮起胶囊 + 彩色圆形图标（松绿=完成，朱砂=请留意，青花=一般提示） -->
			<view class="toast-icon" aria-hidden="true">
				<ZyIcon v-if="item.tone === 'success'" name="check" :size="28" color="#FFFFFF" />
				<ZyIcon v-else-if="item.tone === 'care'" name="alert" :size="28" color="#FFFFFF" />
				<ZyIcon v-else name="info" :size="28" color="#FFFFFF" />
			</view>
			<text class="toast-text">{{ item.message }}</text>
		</view>
	</view>
</template>

<script setup>
/**
 * Toast —— useToast 队列的渲染层。每页挂载一次（建议放在页面根节点末尾）。
 * 顶部居中轻提示，不打断操作；动效遵循 prefers-reduced-motion。
 * 挂载计数用于 notify() 判断宿主页面是否有统一渲染层（见 composables/useToast.js）。
 */
import { onMounted, onBeforeUnmount } from 'vue'
import ZyIcon from './ZyIcon.vue'
import { toastState as state, _trackToastMount } from '../composables/useToast'

onMounted(() => _trackToastMount(1))
onBeforeUnmount(() => _trackToastMount(-1))
</script>

<style scoped>
.toast-stack {
	position: fixed;
	top: calc(28rpx + env(safe-area-inset-top));
	left: 0;
	right: 0;
	z-index: 200;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 14rpx;
	pointer-events: none;
}
.toast-item {
	display: flex;
	align-items: center;
	gap: 16rpx;
	max-width: 86vw;
	padding: 14rpx 32rpx 14rpx 14rpx;
	border-radius: 999rpx;
	background: var(--zy-card-bg, #fff);
	color: var(--zy-title, #141c2e);
	font-size: 27rpx;
	font-weight: 600;
	line-height: 1.5;
	overflow-wrap: anywhere;
	box-shadow: 0 12rpx 36rpx rgba(13, 31, 69, .16);
	animation: toast-in 320ms cubic-bezier(.2, 1.3, .4, 1);
}
.toast-icon { width: 52rpx; height: 52rpx; flex-shrink: 0; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: var(--zy-brand-600, #24478f); }
.tone-success .toast-icon { background: var(--zy-pine, #2f7d68); }
.tone-care .toast-icon { background: var(--zy-cinnabar, #c0392b); }
.toast-text { flex: 1; min-width: 0; }
@media (prefers-reduced-motion: reduce) {
	.toast-item { animation: none; }
}
@keyframes toast-in {
	from { transform: translateY(-24rpx) scale(.96); opacity: 0; }
	to { transform: translateY(0) scale(1); opacity: 1; }
}
</style>
