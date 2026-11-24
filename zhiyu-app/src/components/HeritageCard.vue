<template>
	<AccessibleButton class="heritage-card" :aria-label="cardLabel" @click="onOpen">
		<view
			class="heritage-card-cover"
			:class="{ 'heritage-card-cover--material': !pattern && !$slots.cover }"
		>
			<slot name="cover">
				<template v-if="pattern">
					<!-- 材质层（AI 微距，标注「材质示意」）；H5 纹样以透明底叠在材质上，
					     小程序 image 对透明 SVG 支持不稳，仍用不透明 PNG 作降级 -->
					<image class="heritage-card-texture" :src="textureSrc" mode="aspectFill" />
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
import { textureFor } from '@/utils/heritage-media'

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

/* H5 用同源几何 SVG（dev 环境分包 PNG 不可达，见 heritage-svg.js 注释），
   并去掉内置底色以便 AI 材质微距透出；小程序端 image 对 data-uri SVG 支持不稳，
   用打包进分包的 PNG（材质层被不透明 PNG 覆盖，属已知降级）。 */
// #ifdef H5
const artSrc = computed(() => (props.pattern ? buildPatternSvg(props.pattern, 240, { background: false }) || patternImage(props.pattern.id) : ''))
// #endif
// #ifndef H5
const artSrc = computed(() => (props.pattern ? patternImage(props.pattern.id) : ''))
// #endif
const textureSrc = computed(() => (props.pattern ? textureFor(props.pattern.family) : ''))

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
	/* v9：白卡 + 圆角 + 投影，纹样图是主角 */
	background: var(--zy-card-bg, #fff);
	border: 0;
	border-radius: var(--zy-radius-card, 28rpx);
	box-shadow: var(--zy-shadow-card);
}
.heritage-card:active { transform: scale(.98); }
.heritage-card-cover {
	position: relative;
	height: 280rpx;
	background: #f6f1e6;
	border-bottom: 0;
}
/* 泛型卡无 pattern 且未提供 cover slot 时，封面退为一条材质带 */
.heritage-card-cover--material {
	height: 3rpx;
	display: block;
	background: var(--zy-brand-700, #183369);
	opacity: 0.55;
}
.heritage-card-material { display: none; }
.heritage-card-texture {
	position: absolute;
	top: 0;
	right: 0;
	bottom: 0;
	left: 0;
	width: 100%;
	height: 100%;
	opacity: 0.38;
}
.heritage-card-art {
	position: relative;
	display: block;
	width: 100%;
	height: 100%;
}
.heritage-card-seal {
	position: absolute;
	z-index: 2;
	top: 12rpx;
	right: 12rpx;
	background: rgba(255, 255, 255, .9);
	border: 0;
	border-radius: 8rpx;
}
.heritage-card-copy {
	display: block;
	padding: 18rpx 22rpx 22rpx;
}
.heritage-card-title-row {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	gap: 10rpx;
}
.heritage-card-title {
	font-family: var(--zy-font-display, "Songti SC", serif);
	font-size: 32rpx;
	font-weight: 700;
	color: var(--zy-title, #141c2e);
}
.heritage-card-tag {
	flex-shrink: 0;
	font-size: var(--zy-text-micro, 22rpx);
	color: var(--zy-brand-700, #183369);
	background: var(--zy-brand-50, #eef3fb);
	border: 0;
	border-radius: 999rpx;
	padding: 2rpx 14rpx;
	font-weight: 600;
}
.heritage-card-meta,
.heritage-card-note {
	display: block;
	font-size: var(--zy-text-caption, 24rpx);
	color: var(--zy-muted, #6e7b75);
	margin-top: 8rpx;
}
.heritage-card-note {
	font-size: var(--zy-text-micro, 22rpx);
}
.heritage-card-footer {
	display: block;
	padding: 0 0 22rpx;
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
