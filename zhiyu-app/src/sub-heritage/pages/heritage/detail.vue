<template>
	<view class="growth-page heritage-detail-page" :class="{ 'motion-reduced': reducedMotion }">
		<view v-if="loading" class="growth-card heritage-status" role="status" aria-live="polite">
			<text class="growth-muted">正在读取纹样…</text>
		</view>
		<view v-else-if="error" class="growth-card heritage-status" role="alert">
			<text class="growth-error">{{ error }}</text>
			<AccessibleButton class="growth-button secondary" @click="load">重试</AccessibleButton>
			<AccessibleButton class="growth-button secondary" @click="back">返回纹样馆</AccessibleButton>
		</view>
		<view v-else-if="notFound" class="growth-card heritage-status">
			<text class="growth-muted">没有找到这个纹样。</text>
			<AccessibleButton class="growth-button secondary" @click="back">返回纹样馆</AccessibleButton>
		</view>
		<template v-else-if="pattern">
			<view class="growth-hero heritage-detail-head">
				<text class="growth-eyebrow">HERITAGE STUDY</text>
				<text class="growth-title">{{ pattern.name }}</text>
				<text class="growth-subtitle">{{ pattern.traditionName }} · {{ pattern.region }} · {{ pattern.familyLabel }} · {{ pattern.level }}</text>
				<text class="heritage-origin">原创教学再设计</text>
			</view>

			<image
				class="heritage-detail-art"
				:src="patternImage(pattern.id)"
				:style="{ backgroundColor: pattern.palette[0] }"
				mode="aspectFit"
				:aria-label="pattern.name + ' 原创教学构图'"
			/>
			<view class="heritage-palette" aria-label="纹样色板">
				<view v-for="(color, index) in pattern.palette" :key="color + index" class="heritage-swatch">
					<view class="heritage-swatch-color" :style="{ backgroundColor: color }"></view>
					<text class="heritage-swatch-label">{{ ['底色', '墨色', '主色', '辅色'][index] }}</text>
				</view>
			</view>

			<view class="growth-card">
				<text class="growth-card-title">学习观察点</text>
				<text class="growth-muted">{{ pattern.learningFocus }}</text>
			</view>

			<view class="growth-card">
				<text class="growth-card-title">原创设计说明</text>
				<text class="growth-muted">{{ pattern.designNote }}</text>
			</view>

			<view class="growth-card">
				<text class="growth-card-title">数字练习与实物工艺</text>
				<text class="growth-muted">{{ tradition.practiceBoundary }}</text>
			</view>

			<view class="growth-card">
				<text class="growth-card-title">材料与安全</text>
				<text class="growth-muted">材料背景：{{ materialText }}。</text>
				<text v-for="(item, index) in tradition.safety" :key="'safety-' + index" class="growth-muted heritage-safety">{{ item }}</text>
			</view>

			<view class="growth-card">
				<text class="growth-card-title">来源背景</text>
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
				<text class="growth-muted heritage-source-note">来源仅作材料与技艺背景参考，不表示官方认可本图案，也不代表本图是传统原作。</text>
			</view>

			<AccessibleButton class="growth-button" @click="goStudio">用这个图案配色</AccessibleButton>
			<AccessibleButton class="growth-button secondary" @click="goStarter">去看看手作</AccessibleButton>
			<AccessibleButton class="growth-button secondary" @click="back">返回纹样馆</AccessibleButton>
		</template>

		<text class="growth-foot">本系统不能替代专业心理咨询</text>

		<Toast />
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import {
	decoratePatterns,
	findHeritagePattern,
	findTradition,
	patternImage,
	sourcesForPattern,
} from '@/utils/heritage-ui'
import { useInteraction } from '@/composables/useInteraction'
import Toast from '@/components/Toast.vue'
import AccessibleButton from '@/components/AccessibleButton.vue'
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

function goStudio() {
	if (!pattern.value) return
	uni.navigateTo({ url: '/sub-heritage/pages/heritage/studio?id=' + encodeURIComponent(pattern.value.id) })
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

<style src="@/styles/growth.css"></style>
<style scoped>
.heritage-detail-page { max-width: 880px; margin: 0 auto; }
.heritage-status { display: flex; flex-direction: column; align-items: flex-start; gap: 12rpx; }
.heritage-detail-head { padding-bottom: 8rpx; }
.heritage-origin { display: inline-block; margin-top: 14rpx; padding: 4rpx 12rpx; border-radius: 8rpx; background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-primary, #217e76); font-size: 22rpx; }
.heritage-detail-art { display: block; width: 100%; height: 620rpx; margin: 20rpx 0 10rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: var(--zy-radius-lg, 32rpx); }
.heritage-palette { display: flex; gap: 22rpx; padding: 12rpx 4rpx 24rpx; flex-wrap: wrap; }
.heritage-swatch { display: flex; align-items: center; gap: 8rpx; }
.heritage-swatch-color { width: 30rpx; height: 30rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 50%; }
.heritage-swatch-label { font-size: 21rpx; color: var(--zy-text, #4a5f54); }
.heritage-safety { display: block; margin-top: 8rpx; }
.heritage-source { display: flex; flex-direction: column; align-items: flex-start; width: 100%; margin: 10rpx 0 0; padding: 16rpx 18rpx; text-align: left; background: var(--zy-bg, #f5f7f2); border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 12rpx; line-height: 1.6; }
.heritage-source-title { font-size: 25rpx; color: var(--zy-primary, #217e76); }
.heritage-source-meta { font-size: 20rpx; color: var(--zy-text, #4a5f54); margin-top: 4rpx; }
.heritage-source-note { display: block; margin-top: 16rpx; }
.heritage-source:active { opacity: .82; }
@media (min-width: 700px) { .heritage-detail-art { height: 480px; } }
</style>
