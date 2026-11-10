<template>
	<AccessibleButton
		class="heritage-card"
		:aria-label="'查看纹样 ' + pattern.name + '，' + (pattern.traditionShortName || '') + '，' + (pattern.level || '')"
		@click="$emit('open', pattern.id)"
	>
		<view class="heritage-card-cover">
			<!-- 缩略用同源 PNG（与活体渲染同一几何，check:heritage 保证不漂移）；
			     活体 canvas 留给详情/配色台，避免列表端数十个 canvas 回读开销 -->
			<image
				class="heritage-card-art"
				:src="artSrc"
				mode="aspectFill"
				lazy-load
				:alt="pattern.name + ' 原创教学纹样'"
			/>
			<RegionSeal class="heritage-card-seal" :region="pattern.region" size="sm" />
		</view>
		<view class="heritage-card-copy">
			<view class="heritage-card-title-row">
				<text class="heritage-card-title">{{ pattern.name }}</text>
				<text class="heritage-card-tag">原创教学</text>
			</view>
			<text class="heritage-card-meta">{{ pattern.traditionShortName }} · {{ pattern.region }}</text>
			<text class="heritage-card-note">{{ pattern.familyLabel }} · {{ pattern.level }} · 约 {{ pattern.minutes }} 分钟数字练习</text>
		</view>
	</AccessibleButton>
</template>

<script setup>
import { computed } from 'vue'
import AccessibleButton from '@/components/AccessibleButton.vue'
import RegionSeal from '@/components/RegionSeal.vue'
import { patternImage } from '@/utils/heritage-ui'
import { buildPatternSvg } from '@/utils/heritage-svg'

const props = defineProps({
	pattern: { type: Object, required: true },
})
defineEmits(['open'])

/* H5 用同源几何 SVG（dev 环境分包 PNG 不可达，见 heritage-svg.js 注释）；
   小程序端 image 对 data-uri SVG 支持不稳，用打包进分包的 PNG。 */
// #ifdef H5
const artSrc = computed(() => buildPatternSvg(props.pattern) || patternImage(props.pattern.id))
// #endif
// #ifndef H5
const artSrc = computed(() => patternImage(props.pattern.id))
// #endif
</script>

<style scoped>
.heritage-card {
	display: block;
	width: 100%;
	margin: 0;
	padding: 0;
	overflow: hidden;
	text-align: left;
	line-height: 1.5;
	background: var(--zy-card-bg);
	border: 2rpx solid var(--zy-card-line);
	border-radius: var(--zy-radius-card);
	box-shadow: var(--zy-shadow-card);
}
.heritage-card-cover {
	position: relative;
	height: 260rpx;
	background: var(--zy-bg-tint-mint);
	border-bottom: 2rpx dashed var(--zy-stitch);
}
.heritage-card-art {
	display: block;
	width: 100%;
	height: 100%;
}
.heritage-card-seal {
	position: absolute;
	top: 12rpx;
	right: 12rpx;
	background: var(--zy-card-bg);
}
.heritage-card-copy {
	display: block;
	padding: 20rpx 22rpx 24rpx;
}
.heritage-card-title-row {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	gap: 10rpx;
}
.heritage-card-title {
	font-size: var(--zy-text-title);
	font-weight: 600;
	color: var(--zy-title);
}
.heritage-card-tag {
	flex-shrink: 0;
	font-size: var(--zy-text-micro);
	color: var(--zy-brand-700);
	background: var(--zy-bg-tint-mint);
	border-radius: var(--zy-radius-chip);
	padding: 2rpx 8rpx;
}
.heritage-card-meta,
.heritage-card-note {
	display: block;
	font-size: var(--zy-text-caption);
	color: var(--zy-text);
	margin-top: 8rpx;
}
.heritage-card-note {
	font-size: var(--zy-text-micro);
}
@media (min-width: 700px) {
	.heritage-card-cover {
		height: 220px;
	}
}
</style>
