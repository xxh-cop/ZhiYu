<template>
	<view class="toast-stack" aria-live="polite">
		<view
			v-for="item in state.items"
			:key="item.id"
			class="toast-item"
			:class="'tone-' + item.tone"
			role="status"
		>
			<ZyIcon v-if="item.tone === 'success'" name="check" :size="28" color="#FFFFFF" />
			<ZyIcon v-else-if="item.tone === 'care'" name="alert" :size="28" color="#FFFFFF" />
			<text>{{ item.message }}</text>
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
	top: calc(24rpx + env(safe-area-inset-top));
	left: 0;
	right: 0;
	z-index: 200;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;
	pointer-events: none;
}
.toast-item {
	display: flex;
	align-items: center;
	gap: 12rpx;
	max-width: 80vw;
	padding: 16rpx 32rpx;
	border-radius: 0;
	background: rgba(17, 24, 22, 0.94);
	color: #fff;
	font-size: var(--zy-font-sm, 24rpx);
	line-height: 1.6;
	overflow-wrap: anywhere;
	animation: toast-in var(--zy-duration-base, 250ms) var(--zy-ease-out, ease-out);
}
.toast-item.tone-success { background: var(--zy-success, #2e7d5b); }
.toast-item.tone-care { background: var(--zy-care, var(--zy-care, #a64842)); }
@media (prefers-reduced-motion: reduce) {
	.toast-item { animation: none; }
}
@keyframes toast-in {
	from { transform: translateY(-16rpx); opacity: 0; }
	to { transform: translateY(0); opacity: 1; }
}
</style>
