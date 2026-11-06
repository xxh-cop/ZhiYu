<template>
	<view class="state-block" :class="['state-' + type, { compact }]" :role="type === 'error' ? 'alert' : 'status'">
		<template v-if="type === 'loading'">
			<view class="skeleton-stack" aria-hidden="true">
				<view class="zy-skeleton skeleton-bar" />
				<view class="zy-skeleton skeleton-bar short" />
				<view class="zy-skeleton skeleton-bar shorter" />
			</view>
			<text class="state-title loading-title">{{ title || '正在加载…' }}</text>
		</template>
		<template v-else>
			<!-- R2：H5 空态插画（内联 SVG 零请求）；小程序端降级为小织表情（不新增 PNG） -->
			<!-- #ifdef H5 -->
			<EmptyArt v-if="art" :variant="art" class="state-art" />
			<XiaotuanPet v-else class="state-mascot" :size="mascotSize" :face="faceName" aria-hidden="true" />
			<!-- #endif -->
			<!-- #ifndef H5 -->
			<XiaotuanPet class="state-mascot" :size="mascotSize" :face="faceName" aria-hidden="true" />
			<!-- #endif -->
			<text class="state-title">{{ title }}</text>
			<text v-if="description" class="state-description">{{ description }}</text>
			<slot />
			<AccessibleButton
				v-if="actionText"
				class="state-action"
				:class="{ secondary: type === 'empty' }"
				:disabled="actionLoading"
				:loading="actionLoading"
				@click="$emit('action')"
			>{{ actionLoading ? '正在处理…' : actionText }}</AccessibleButton>
		</template>
	</view>
</template>

<script setup>
import { computed } from 'vue'
import XiaotuanPet from './XiaotuanPet.vue'
import EmptyArt from './EmptyArt.vue'
import AccessibleButton from './AccessibleButton.vue'

/**
 * StateBlock —— 空 / 错 / 加载三态合一（计划 §3.4）。
 * loading：骨架屏优先于 spinner；empty/error 用小织线团小兽作视觉锚点，
 * 不再引用旧版 xiaozhi 静态 PNG。
 */
const props = defineProps({
	type: { type: String, default: 'empty', validator: (v) => ['loading', 'empty', 'error'].includes(v) },
	title: { type: String, default: '' },
	description: { type: String, default: '' },
	actionText: { type: String, default: '' },
	actionLoading: { type: Boolean, default: false },
	/** 小织表情（calm/listen/think/happy/care/sleepy），缺省按类型给默认 */
	face: { type: String, default: '' },
	/** 空态插画（R2：works 我的作品 / community 社区 / notice 通知）；仅 H5 渲染，MP 自动降级为表情 */
	art: { type: String, default: '', validator: (v) => ['', 'works', 'community', 'notice'].includes(v) },
	compact: { type: Boolean, default: false },
})
defineEmits(['action'])

const faceName = computed(() => props.face || (props.type === 'error' ? 'care' : 'calm'))
const mascotSize = computed(() => (props.compact ? 72 : 96))
</script>

<style scoped>
.state-block {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 20rpx;
	padding: 64rpx 40rpx;
	text-align: center;
}
.state-block.compact { padding: 36rpx 32rpx; gap: 14rpx; }

.skeleton-stack { width: 100%; display: flex; flex-direction: column; gap: 20rpx; }
.skeleton-bar {
	height: 32rpx;
	border-radius: var(--zy-radius-sm, 12rpx);
	background: linear-gradient(90deg, var(--zy-bg-tint-mint, #e8efe4) 25%, var(--zy-brand-50, #eaf4f1) 50%, var(--zy-bg-tint-mint, #e8efe4) 75%);
	background-size: 200% 100%;
	animation: zy-shimmer 1.6s var(--zy-ease-linear, linear) infinite;
}
.skeleton-bar.short { width: 78%; align-self: center; }
.skeleton-bar.shorter { width: 52%; align-self: center; }
@media (prefers-reduced-motion: reduce) {
	.skeleton-bar { animation: none; }
}

.state-title {
	font-size: var(--zy-font-md, 32rpx);
	font-weight: 600;
	color: var(--zy-title, #233d34);
	line-height: 1.5;
	overflow-wrap: anywhere;
}
.loading-title { font-weight: 400; color: var(--zy-text, #4a5f54); font-size: var(--zy-font-sm, 24rpx); }
.state-description {
	font-size: var(--zy-font-sm, 24rpx);
	color: var(--zy-text-secondary, #4a5f54);
	line-height: 1.7;
	overflow-wrap: anywhere;
}
.state-action {
	min-height: 44px;
	margin: 8rpx 0 0;
	padding: 16rpx 44rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-brand-600, #217e76);
	color: #fff;
	font-size: var(--zy-font-base, 28rpx);
	line-height: 1.5;
}
.state-action.secondary {
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-brand-700, #1a645e);
}
.state-action::after { border: 0; }
.state-action.is-disabled { opacity: .55; }
.state-action:focus-visible { outline: 3px solid var(--zy-brand-700, #1a645e); outline-offset: 3px; }

@keyframes zy-shimmer {
	0% { background-position: 200% 0; }
	100% { background-position: -200% 0; }
}
</style>
