<template>
	<view class="state-block" :class="['state-' + type, { compact }]" :role="type === 'error' ? 'alert' : 'status'">
		<template v-if="type === 'loading'">
			<!-- 骨架形状按页面内容预置：bars 默认 / list 列表 / card 卡片 / detail 详情 / chat 对话 -->
			<view v-if="shape === 'list'" class="skeleton-list" aria-hidden="true">
				<view v-for="i in 3" :key="i" class="skeleton-row">
					<view class="zy-skeleton skeleton-thumb" />
					<view class="skeleton-row-copy">
						<view class="zy-skeleton skeleton-bar" />
						<view class="zy-skeleton skeleton-bar short" />
					</view>
				</view>
			</view>
			<view v-else-if="shape === 'card'" class="skeleton-stack" aria-hidden="true">
				<view class="zy-skeleton skeleton-cover" />
				<view class="zy-skeleton skeleton-bar" />
				<view class="zy-skeleton skeleton-bar short" />
			</view>
			<view v-else-if="shape === 'detail'" class="skeleton-stack" aria-hidden="true">
				<view class="zy-skeleton skeleton-hero" />
				<view class="zy-skeleton skeleton-bar" />
				<view class="zy-skeleton skeleton-bar short" />
				<view class="zy-skeleton skeleton-bar shorter" />
			</view>
			<view v-else-if="shape === 'chat'" class="skeleton-chat" aria-hidden="true">
				<view class="zy-skeleton skeleton-bubble left" />
				<view class="zy-skeleton skeleton-bubble right" />
				<view class="zy-skeleton skeleton-bubble left narrow" />
			</view>
			<view v-else class="skeleton-stack" aria-hidden="true">
				<view class="zy-skeleton skeleton-bar" />
				<view class="zy-skeleton skeleton-bar short" />
				<view class="zy-skeleton skeleton-bar shorter" />
			</view>
			<text class="state-title loading-title">{{ title || '正在加载…' }}</text>
		</template>
		<template v-else>
			<!-- H5 场景插画复用新小织素材；小程序显示相同角色的独立状态图 -->
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
				:variant="type === 'empty' ? 'secondary' : 'primary'"
				size="md"
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
 * loading：骨架屏优先于 spinner；empty/error 使用织结小精灵。
 */
const props = defineProps({
	type: { type: String, default: 'empty', validator: (v) => ['loading', 'empty', 'error'].includes(v) },
	title: { type: String, default: '' },
	description: { type: String, default: '' },
	actionText: { type: String, default: '' },
	actionLoading: { type: Boolean, default: false },
	/** 小织表情（calm/listen/think/happy/care/sleepy），缺省按类型给默认 */
	face: { type: String, default: '' },
	/** 空态插画（R2：works/community/notice；P2 补 notes/groups/programs）；仅 H5 渲染，MP 自动降级为表情 */
	art: { type: String, default: '', validator: (v) => ['', 'works', 'community', 'notice', 'notes', 'groups', 'programs'].includes(v) },
	compact: { type: Boolean, default: false },
	/** 骨架形状：bars 默认三条 / list 列表行 / card 封面卡 / detail 详情 / chat 对话气泡 */
	shape: { type: String, default: 'bars', validator: (v) => ['bars', 'list', 'card', 'detail', 'chat'].includes(v) },
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
	border-radius: 0;
	background: linear-gradient(90deg, var(--zy-bg-sunken, #eef1ee) 25%, var(--zy-border, #d3ded8) 50%, var(--zy-bg-sunken, #eef1ee) 75%);
	background-size: 200% 100%;
	animation: zy-shimmer 1.6s var(--zy-ease-linear, linear) infinite;
}
.skeleton-bar.short { width: 78%; }
.skeleton-bar.shorter { width: 52%; }
.skeleton-list { width: 100%; display: flex; flex-direction: column; gap: 28rpx; }
.skeleton-row { display: flex; gap: 20rpx; align-items: center; }
.skeleton-thumb { width: 96rpx; height: 96rpx; flex-shrink: 0; }
.skeleton-row-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 14rpx; }
.skeleton-row-copy .skeleton-bar { height: 26rpx; }
.skeleton-row-copy .skeleton-bar.short { width: 56%; align-self: flex-start; }
.skeleton-cover { width: 100%; height: 300rpx; }
.skeleton-hero { width: 100%; height: 360rpx; }
.skeleton-chat { width: 100%; display: flex; flex-direction: column; gap: 24rpx; }
.skeleton-bubble { height: 72rpx; width: 70%; }
.skeleton-bubble.right { width: 55%; align-self: flex-end; }
.skeleton-bubble.narrow { width: 64%; }
@media (prefers-reduced-motion: reduce) {
	.skeleton-bar, .skeleton-thumb, .skeleton-cover, .skeleton-hero, .skeleton-bubble { animation: none; }
}

.state-title {
	font-family: var(--zy-font-display, serif);
	font-size: var(--zy-font-md, 32rpx);
	font-weight: 500;
	color: var(--zy-title, #111816);
	line-height: 1.5;
	overflow-wrap: anywhere;
}
.loading-title { font-weight: 400; color: var(--zy-muted, #6e7b75); font-size: var(--zy-font-sm, 24rpx); }
.state-description {
	font-size: var(--zy-font-sm, 24rpx);
	color: var(--zy-muted, #6e7b75);
	line-height: 1.7;
	overflow-wrap: anywhere;
}
.state-action { margin: 8rpx 0 0; }

@keyframes zy-shimmer {
	0% { background-position: 200% 0; }
	100% { background-position: -200% 0; }
}
</style>
