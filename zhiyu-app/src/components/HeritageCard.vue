<template>
	<AccessibleButton class="heritage-card" :aria-label="cardLabel" @click="onOpen">
		<view
			class="heritage-card-cover"
			:class="{ 'heritage-card-cover--material': !pattern && !$slots.cover }"
		>
			<slot name="cover">
				<template v-if="pattern">
					<!-- 缩略用同源 PNG（与活体渲染同一几何，check:heritage 保证不漂移）；
					     活体 canvas 留给详情/配色台，避免列表端数十个 canvas 回读开销 -->
					<image
						class="heritage-card-art"
						:src="artSrc"
						mode="aspectFill"
						lazy-load
						:alt="pattern.name + ' 原创教学纹样'"
					/>
				</template>
				<!-- 泛型卡材质带：纯 CSS 四族工艺逻辑，颜色只用令牌（与 ProcessStrip 的 motif 同写法） -->
				<view v-else class="heritage-card-material" :class="'material-' + motifFamily" aria-hidden="true">
					<view class="material-a"></view>
					<view class="material-b"></view>
				</view>
			</slot>
			<RegionSeal v-if="pattern" class="heritage-card-seal" :region="pattern.region" size="sm" />
		</view>
		<view class="heritage-card-copy">
			<view class="heritage-card-title-row">
				<text class="heritage-card-title">{{ displayTitle }}</text>
				<text v-if="displayTag" class="heritage-card-tag">{{ displayTag }}</text>
			</view>
			<text v-if="displayMeta" class="heritage-card-meta">{{ displayMeta }}</text>
			<text v-if="displayNote" class="heritage-card-note">{{ displayNote }}</text>
			<slot />
		</view>
		<view v-if="$slots.footer" class="heritage-card-footer">
			<slot name="footer" />
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
	/** 有 pattern 时保持纹样馆既有行为；泛型用法不传 */
	pattern: { type: Object, default: null },
	title: { type: String, default: '' },
	meta: { type: String, default: '' },
	note: { type: String, default: '' },
	tag: { type: String, default: '' },
	/** 泛型卡材质带：weave | stitch | cutout | porcelain（其余回落 weave） */
	family: { type: String, default: 'weave' },
	ariaLabel: { type: String, default: '' },
})
const emit = defineEmits(['open'])
const SLOT_NAMES = ['weave', 'stitch', 'cutout', 'porcelain']

/* H5 用同源几何 SVG（dev 环境分包 PNG 不可达，见 heritage-svg.js 注释）；
   小程序端 image 对 data-uri SVG 支持不稳，用打包进分包的 PNG。 */
// #ifdef H5
const artSrc = computed(() => (props.pattern ? buildPatternSvg(props.pattern) || patternImage(props.pattern.id) : ''))
// #endif
// #ifndef H5
const artSrc = computed(() => (props.pattern ? patternImage(props.pattern.id) : ''))
// #endif

const motifFamily = computed(() => (SLOT_NAMES.includes(props.family) ? props.family : 'weave'))
const displayTitle = computed(() => (props.pattern ? props.pattern.name : props.title))
const displayTag = computed(() => (props.pattern ? '原创教学' : props.tag))
const displayMeta = computed(() => (
	props.pattern
		? [props.pattern.traditionShortName, props.pattern.region].filter(Boolean).join(' · ')
		: props.meta
))
const displayNote = computed(() => {
	if (!props.pattern) return props.note
	return [
		props.pattern.familyLabel,
		props.pattern.level,
		props.pattern.minutes ? `约 ${props.pattern.minutes} 分钟数字练习` : '',
	].filter(Boolean).join(' · ')
})
const cardLabel = computed(() => {
	if (props.ariaLabel) return props.ariaLabel
	if (props.pattern) {
		return '查看纹样 ' + props.pattern.name + '，' + (props.pattern.traditionShortName || '') + '，' + (props.pattern.level || '')
	}
	const parts = [props.title, props.tag, props.meta, props.note].filter(Boolean)
	return parts.length ? '查看 ' + parts.join('，') : undefined
})

function onOpen() {
	if (props.pattern) emit('open', props.pattern.id)
	else emit('open')
}
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
/* 泛型卡无 pattern 且未提供 cover slot 时，封面退为一条材质带 */
.heritage-card-cover--material {
	height: 132rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: var(--zy-bg-tint-warm);
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
.heritage-card-footer {
	display: block;
	padding: 0 22rpx 22rpx;
}

/* ── 材质带四族（与 ProcessStrip 同一工艺逻辑，纯 CSS） ── */
.heritage-card-material {
	position: relative;
	width: 72rpx;
	height: 72rpx;
}
.material-a,
.material-b {
	position: absolute;
}
/* 编织：经纬交叉 */
.material-weave .material-a {
	left: 0;
	right: 0;
	top: 22rpx;
	height: 8rpx;
	background: var(--zy-brand-600);
}
.material-weave .material-b {
	top: 0;
	bottom: 0;
	left: 32rpx;
	width: 8rpx;
	background: var(--zy-gold);
}
/* 针线：针脚虚线 + 落针点 */
.material-stitch .material-a {
	left: 4rpx;
	right: 4rpx;
	top: 34rpx;
	border-top: 4rpx dashed var(--zy-brand-600);
}
.material-stitch .material-b {
	left: 30rpx;
	top: 24rpx;
	width: 10rpx;
	height: 10rpx;
	border-radius: 50%;
	background: var(--zy-gold);
}
/* 剪刻：负空间圆孔 */
.material-cutout .material-a {
	top: 4rpx;
	right: 4rpx;
	bottom: 4rpx;
	left: 4rpx;
	background: var(--zy-care);
	border-radius: var(--zy-radius-xs);
}
.material-cutout .material-b {
	left: 24rpx;
	top: 24rpx;
	width: 24rpx;
	height: 24rpx;
	background: var(--zy-card-bg);
	border-radius: 50%;
}
/* 器面：双环 */
.material-porcelain .material-a {
	top: 4rpx;
	right: 4rpx;
	bottom: 4rpx;
	left: 4rpx;
	border: 5rpx solid var(--zy-brand-600);
	border-radius: 50%;
}
.material-porcelain .material-b {
	left: 22rpx;
	top: 22rpx;
	width: 28rpx;
	height: 28rpx;
	border: 4rpx solid var(--zy-gold);
	border-radius: 50%;
}
@media (min-width: 700px) {
	.heritage-card-cover {
		height: 220px;
	}
	.heritage-card-cover--material {
		height: 100px;
	}
}
</style>
