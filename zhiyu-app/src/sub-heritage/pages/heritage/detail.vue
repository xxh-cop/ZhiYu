<template>
	<view class="growth-page heritage-detail-page" :class="[{ 'motion-reduced': reducedMotion }, zyThemeClass]">
		<view v-if="loading" class="zy-card heritage-status" role="status" aria-live="polite">
			<text class="heritage-muted zy-t-body">正在读取纹样…</text>
		</view>
		<view v-else-if="error" class="zy-card heritage-status" role="alert">
			<text class="heritage-error zy-t-body">{{ error }}</text>
			<AccessibleButton variant="secondary" @click="load">重试</AccessibleButton>
			<AccessibleButton variant="secondary" @click="back">返回纹样馆</AccessibleButton>
		</view>
		<view v-else-if="notFound" class="zy-card heritage-status">
			<text class="heritage-muted zy-t-body">没有找到这个纹样。</text>
			<AccessibleButton variant="secondary" @click="back">返回纹样馆</AccessibleButton>
		</view>
		<template v-else-if="pattern">
			<SectionHead eyebrow="HERITAGE STUDY" :title="pattern.name" tone="heritage" />
			<view class="heritage-detail-meta">
				<text class="heritage-muted zy-t-body">{{ pattern.traditionName }} · {{ pattern.region }} · {{ pattern.familyLabel }} · {{ pattern.level }}</text>
				<RegionSeal :region="pattern.region" size="md" />
			</view>
			<text class="heritage-origin">原创教学再设计</text>

			<view class="heritage-detail-art">
				<PatternLive
					:pattern="pattern"
					canvas-id="heritage-detail"
					:animate="true"
					:reveal-ratio="revealRatio"
					:label="pattern.name + ' 原创教学构图'"
				/>
			</view>
			<view class="heritage-process-bar">
				<AccessibleButton variant="ghost" size="sm" :aria-pressed="playing" @click="togglePlay">
					{{ playing ? '停下观察' : '看工序' }}
				</AccessibleButton>
				<text class="heritage-muted zy-t-caption">{{ processHint }}</text>
			</view>
			<ProcessStrip :steps="processSteps" :current="processCurrent" :family="pattern.family" />
			<HeritageRegistryPlate :tradition="tradition" />
			<view class="heritage-palette" aria-label="纹样色板">
				<view v-for="(color, index) in pattern.palette" :key="color + index" class="heritage-swatch">
					<view class="heritage-swatch-color" :style="{ backgroundColor: color }"></view>
					<text class="heritage-swatch-label">{{ ['底色', '墨色', '主色', '辅色'][index] }}</text>
				</view>
			</view>

			<view class="zy-card">
				<text class="heritage-card-title zy-t-title">学习观察点</text>
				<text class="heritage-muted zy-t-body">{{ pattern.learningFocus }}</text>
			</view>

			<view class="heritage-quote zy-stitch-v">
				<text class="heritage-quote-mark zy-t-serif" aria-hidden="true">「</text>
				<text class="heritage-quote-text zy-t-serif">{{ pattern.designNote }}</text>
			</view>

			<view class="zy-card">
				<text class="heritage-card-title zy-t-title">数字练习与实物工艺</text>
				<text class="heritage-muted zy-t-body">{{ tradition.practiceBoundary }}</text>
			</view>

			<view class="zy-card">
				<text class="heritage-card-title zy-t-title">材料与安全</text>
				<text class="heritage-muted zy-t-body">材料背景：{{ materialText }}。</text>
				<text v-for="(item, index) in tradition.safety" :key="'safety-' + index" class="heritage-muted zy-t-body heritage-safety">{{ item }}</text>
			</view>

			<view class="zy-card">
				<text class="heritage-card-title zy-t-title">来源背景</text>
				<AccessibleButton
					v-for="source in sources"
					:key="source.id"
					class="heritage-source"
					:aria-label="'打开来源 ' + source.title"
					@click="openSource(source)"
				>
					<text class="heritage-source-title">{{ source.title }} ↗</text>
					<text class="heritage-source-meta">{{ source.organization }} · 访问于 {{ source.accessedAt }}</text>
				</AccessibleButton>
				<text class="heritage-muted zy-t-caption heritage-source-note">来源仅作材料与技艺背景参考，不表示官方认可本图案，也不代表本图是传统原作。</text>
			</view>

			<view class="heritage-follow">
				<AccessibleButton variant="primary" :loading="starting" @click="startFollowAlong">用这个纹样跟做</AccessibleButton>
				<text class="heritage-muted zy-t-caption heritage-follow-note">会建立一次练习会话，完成后可在成长旅程查看文化卡</text>
			</view>
			<AccessibleButton variant="secondary" @click="goStarter">去看看手作</AccessibleButton>
			<AccessibleButton variant="secondary" @click="back">返回纹样馆</AccessibleButton>
		</template>

		<text class="heritage-foot zy-t-caption">本系统不能替代专业心理咨询</text>

		<Toast />
	</view>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import {
	decoratePatterns,
	findHeritagePattern,
	findTradition,
	sourcesForPattern,
} from '@/utils/heritage-ui'
import { useInteraction } from '@/composables/useInteraction'
import { createPracticeSession } from '@/utils/practice-session'
import Toast from '@/components/Toast.vue'
import AccessibleButton from '@/components/AccessibleButton.vue'
import SectionHead from '@/components/SectionHead.vue'
import PatternLive from '@/components/PatternLive.vue'
import ProcessStrip from '@/components/ProcessStrip.vue'
import HeritageRegistryPlate from '@/components/HeritageRegistryPlate.vue'
import RegionSeal from '@/components/RegionSeal.vue'
import { useToast } from '@/composables/useToast'

const toast = useToast()

const { reducedMotion } = useInteraction()
const loading = ref(false)
const error = ref('')
const notFound = ref(false)
const patternId = ref('')
const catalog = ref(null)
const pattern = ref(null)
const tradition = ref(null)
const sources = ref([])
const materialText = computed(() => {
	const materials = tradition.value && Array.isArray(tradition.value.materials) ? tradition.value.materials : []
	return materials.length ? materials.join('、') : '未提供材料背景'
})

/* 工序播放器：按 tradition.process 逐步提高 revealRatio，让纹样分层绘出；
   停下后保留当前工序与分层画面，便于对照观察 */
const playing = ref(false)
const revealed = ref(false)
const stepIndex = ref(0)
let playTimer = null
const processSteps = computed(() => {
	const raw = tradition.value && tradition.value.process
	if (!Array.isArray(raw)) return []
	return raw.map(item => (typeof item === 'string' ? item : (item && (item.text || item.name)) || ''))
})
const revealRatio = computed(() => (revealed.value ? (stepIndex.value + 1) / Math.max(1, processSteps.value.length) : 1))
const processCurrent = computed(() => (revealed.value ? stepIndex.value : -1))
const processHint = computed(() => {
	if (playing.value) return '正在按工序逐层绘出，可停下细看'
	if (revealed.value) return '已停在当前工序，再点一次重新播放'
	return '点「看工序」逐层观察结构'
})

function stopPlay() {
	if (playTimer) {
		clearInterval(playTimer)
		playTimer = null
	}
}

function togglePlay() {
	if (playing.value) {
		playing.value = false
		stopPlay()
		return
	}
	if (!processSteps.value.length) return
	revealed.value = true
	playing.value = true
	stepIndex.value = 0
	stopPlay()
	playTimer = setInterval(() => {
		if (stepIndex.value < processSteps.value.length - 1) stepIndex.value++
		else {
			playing.value = false
			stopPlay()
		}
	}, 1200)
}

onBeforeUnmount(stopPlay)

async function load() {
	loading.value = true
	error.value = ''
	notFound.value = false
	try {
		const data = await request({ url: '/api/heritage/catalog' })
		catalog.value = data
		const patterns = decoratePatterns(data)
		const found = findHeritagePattern(patterns, patternId.value)
		if (!found) {
			pattern.value = null
			notFound.value = true
			return
		}
		pattern.value = found
		tradition.value = findTradition(data && data.traditions, found.traditionId)
		sources.value = sourcesForPattern(data, found)
		if (!tradition.value) notFound.value = true
	} catch (event) {
		error.value = (event && event.message) || '纹样暂时没有载入，请重试'
	} finally {
		loading.value = false
	}
}

function openSource(source) {
	const url = source && source.url
	if (!url || !/^https:\/\//.test(url)) return
	// #ifdef H5
	window.open(url, '_blank', 'noopener,noreferrer')
	// #endif
	// #ifndef H5
	uni.setClipboardData({
		data: url,
		success: () => toast.show('来源链接已复制，请在浏览器打开'),
	})
	// #endif
}

/* 跟做闭环：先建立一次 HERITAGE 练习会话，再带着 sessionId 进入配色台；
   loading 期间按钮不可再次触发，失败只提示不跳转，用户可原地重试 */
const starting = ref(false)

async function startFollowAlong() {
	if (starting.value || !pattern.value) return
	starting.value = true
	try {
		const session = await createPracticeSession({
			kind: 'HERITAGE',
			minutesBudget: pattern.value.minutes || 10,
			heritagePatternId: pattern.value.id,
		})
		const sessionId = session && (session.practiceSessionId || session.id)
		if (!sessionId) throw new Error('练习会话没有返回标识')
		uni.navigateTo({
			url: '/sub-heritage/pages/heritage/studio?id=' + encodeURIComponent(pattern.value.id)
				+ '&sessionId=' + encodeURIComponent(sessionId),
		})
	} catch (event) {
		toast.show('练习暂时没有准备好，请重试')
	} finally {
		starting.value = false
	}
}

function goStarter() {
	uni.navigateTo({ url: '/pages/starter/index' })
}

function back() {
	const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
	if (pages.length > 1) uni.navigateBack({ delta: 1 })
	else uni.redirectTo({ url: '/sub-heritage/pages/heritage/gallery' })
}

onLoad((query) => {
	patternId.value = query && query.id ? String(query.id) : ''
	load()
})
</script>

<style scoped>
.heritage-detail-page {
	max-width: 880px;
	margin: 0 auto;
	padding: var(--zy-space-lg) var(--zy-space-md) var(--zy-space-3xl);
}
.heritage-status {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 12rpx;
}
.heritage-muted {
	color: var(--zy-text);
}
.heritage-error {
	color: var(--zy-care);
}
.heritage-detail-meta {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	margin: 8rpx 0 4rpx;
	flex-wrap: wrap;
}
.heritage-origin {
	display: inline-block;
	margin-top: 14rpx;
	padding: 4rpx 12rpx;
	border-radius: var(--zy-radius-chip);
	background: var(--zy-bg-tint-mint);
	color: var(--zy-brand-700);
	font-size: var(--zy-text-caption);
}
.heritage-detail-art {
	display: block;
	width: 100%;
	height: 620rpx;
	margin: 20rpx 0 10rpx;
	overflow: hidden;
	border: 2rpx solid var(--zy-card-line);
	border-radius: var(--zy-radius-feature);
	box-shadow: var(--zy-shadow-card);
}
.heritage-process-bar {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin: 8rpx 0 16rpx;
	flex-wrap: wrap;
}
.heritage-palette {
	display: flex;
	gap: 22rpx;
	padding: 12rpx 4rpx 24rpx;
	flex-wrap: wrap;
}
.heritage-swatch {
	display: flex;
	align-items: center;
	gap: 8rpx;
}
.heritage-swatch-color {
	width: 30rpx;
	height: 30rpx;
	border: 1rpx solid var(--zy-border);
	border-radius: 50%;
}
.heritage-swatch-label {
	font-size: var(--zy-text-micro);
	color: var(--zy-text);
}
.heritage-card-title {
	display: block;
	margin-bottom: 10rpx;
	color: var(--zy-title);
}
.heritage-quote {
	display: flex;
	gap: 12rpx;
	margin: 0 0 var(--zy-space-md);
	padding: var(--zy-space-sm) 0 var(--zy-space-sm) var(--zy-space-md);
}
.heritage-quote-mark {
	flex-shrink: 0;
	font-size: var(--zy-text-title-lg);
	color: var(--zy-gold);
	line-height: 1.4;
}
.heritage-quote-text {
	font-size: var(--zy-text-body-lg);
	color: var(--zy-title);
	line-height: 1.8;
}
.heritage-safety {
	display: block;
	margin-top: 8rpx;
}
.heritage-source {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	width: 100%;
	margin: 10rpx 0 0;
	padding: 16rpx 18rpx;
	text-align: left;
	background: var(--zy-bg-sunken);
	border: 2rpx solid var(--zy-card-line);
	border-radius: var(--zy-radius-control);
	line-height: 1.6;
}
.heritage-source-title {
	font-size: var(--zy-text-body);
	color: var(--zy-brand-700);
}
.heritage-source-meta {
	font-size: var(--zy-text-micro);
	color: var(--zy-text);
	margin-top: 4rpx;
}
.heritage-source-note {
	display: block;
	margin-top: 16rpx;
}
.heritage-follow {
	display: flex;
	flex-direction: column;
	align-items: stretch;
	gap: 10rpx;
	margin-bottom: var(--zy-space-sm);
}
.heritage-follow-note {
	display: block;
	line-height: 1.6;
}
.heritage-foot {
	display: block;
	margin-top: var(--zy-space-3xl);
	text-align: center;
	color: var(--zy-text);
}
@media (min-width: 700px) {
	.heritage-detail-art {
		height: 480px;
	}
}
</style>
