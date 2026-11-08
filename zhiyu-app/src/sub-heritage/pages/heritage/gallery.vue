<template>
	<view class="growth-page heritage-page" :class="[{ 'motion-reduced': reducedMotion }, zyThemeClass]">
		<view class="growth-hero heritage-hero">
			<text class="growth-eyebrow">HERITAGE STUDIES</text>
			<text class="growth-title">非遗纹样馆</text>
			<text class="growth-subtitle">{{ catalogCount }} · 来源与练习边界可查。</text>
		</view>

		<view v-if="loading" class="growth-card heritage-status" role="status" aria-live="polite">
			<text class="growth-muted">正在整理纹样…</text>
		</view>
		<view v-else-if="error" class="growth-card heritage-status" role="alert">
			<text class="growth-error">{{ error }}</text>
			<AccessibleButton class="growth-button secondary" @click="load">重试</AccessibleButton>
		</view>
		<template v-else>
			<view class="growth-card heritage-search-card">
				<label class="heritage-label" for="heritage-search">搜索纹样</label>
				<input
					id="heritage-search"
					:key="searchKey"
					v-model="keyword"
					class="growth-input"
					aria-label="搜索纹样"
					placeholder="输入名称、地域或学习观察点"
					confirm-type="search"
				/>
				<text class="growth-muted">可按名称、地域或学习观察点查找。</text>
			</view>

			<view class="growth-row heritage-filters" role="group" aria-label="按技艺筛选">
				<AccessibleButton
					:class="['growth-choice', { selected: !traditionId }]"
					:aria-pressed="!traditionId"
					@click="traditionId = ''"
				>全部</AccessibleButton>
				<AccessibleButton
					v-for="tradition in traditions"
					:key="tradition.id"
					:class="['growth-choice', { selected: traditionId === tradition.id }]"
					:aria-pressed="traditionId === tradition.id"
					@click="traditionId = tradition.id"
				>{{ tradition.shortName }}</AccessibleButton>
			</view>

			<view class="heritage-count-row">
				<text class="heritage-count">找到 {{ filtered.length }} 份构图</text>
				<text class="growth-muted">点击卡片查看来源与说明</text>
			</view>

			<view v-if="!filtered.length" class="growth-card heritage-status">
				<text class="growth-muted">{{ patterns.length ? '没有找到匹配的纹样。' : '当前没有可展示的纹样。' }}</text>
				<AccessibleButton v-if="patterns.length" class="growth-button secondary" @click="clearFilters">清空筛选</AccessibleButton>
				<AccessibleButton v-else class="growth-button secondary" @click="load">重试</AccessibleButton>
			</view>
			<view v-else class="heritage-grid">
				<AccessibleButton
					v-for="pattern in filtered"
					:key="pattern.id"
					class="heritage-card"
					:aria-label="'查看纹样 ' + pattern.name + '，' + pattern.traditionShortName + '，' + pattern.level"
					@click="openPattern(pattern.id)"
				>
					<image class="heritage-card-art" :src="patternImage(pattern.id)" mode="aspectFill" lazy-load :alt="(pattern.name||'非遗')+'纹样图样'" />
					<view class="heritage-card-copy">
						<view class="heritage-card-title-row">
							<text class="heritage-card-title">{{ pattern.name }}</text>
							<text class="heritage-card-tag">原创教学</text>
						</view>
						<text class="heritage-card-meta">{{ pattern.traditionShortName }} · {{ pattern.region }}</text>
						<text class="heritage-card-note">{{ pattern.familyLabel }} · {{ pattern.level }} · 约 {{ pattern.minutes }} 分钟数字练习</text>
					</view>
				</AccessibleButton>
			</view>
		</template>

		<text class="growth-foot">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import { decoratePatterns, filterHeritagePatterns, patternImage } from '@/utils/heritage-ui'
import { useInteraction } from '@/composables/useInteraction'
import AccessibleButton from '@/components/AccessibleButton.vue'

const { reducedMotion } = useInteraction()
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const searchKey = ref(0)
const traditionId = ref('')
const traditions = ref([])
const patterns = ref([])
const filtered = computed(() => filterHeritagePatterns(patterns.value, {
	keyword: keyword.value,
	traditionId: traditionId.value,
}))
const catalogCount = computed(() => patterns.value.length ? `${patterns.value.length} 份原创教学构图` : '原创教学构图')

function clearFilters() {
	keyword.value = ''
	traditionId.value = ''
	searchKey.value++
}

function openPattern(id) {
	uni.navigateTo({ url: '/sub-heritage/pages/heritage/detail?id=' + encodeURIComponent(id) })
}

async function load() {
	loading.value = true
	error.value = ''
	try {
		const data = await request({ url: '/api/heritage/catalog' })
		traditions.value = data && Array.isArray(data.traditions) ? data.traditions : []
		patterns.value = decoratePatterns(data)
	} catch (event) {
		error.value = (event && event.message) || '纹样暂时没有载入，请重试'
	} finally {
		loading.value = false
	}
}

onLoad(load)
</script>

<style src="@/styles/growth.css"></style>
<style scoped>
.heritage-page { max-width: 1180px; margin: 0 auto; }
.heritage-hero { padding-bottom: 12rpx; }
.heritage-status { display: flex; flex-direction: column; align-items: flex-start; gap: 12rpx; }
.heritage-search-card { padding: 24rpx 28rpx; }
.heritage-label { display: block; margin-bottom: 10rpx; font-size: 26rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.heritage-filters { gap: 12rpx; margin: 22rpx 0 8rpx; }
.heritage-page .heritage-filters button.growth-choice { flex: 0 0 auto; width: auto; min-height: 88rpx; padding: 12rpx 24rpx; margin: 0; font-size: 25rpx; }
.heritage-count-row { display: flex; justify-content: space-between; align-items: baseline; gap: 16rpx; margin: 24rpx 0 16rpx; flex-wrap: wrap; }
.heritage-count { font-size: 26rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.heritage-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18rpx; }
.heritage-card { display: block; width: 100%; margin: 0; padding: 0; overflow: hidden; text-align: left; line-height: 1.5; background: var(--zy-card-bg, #fff); border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: var(--zy-radius-md, 24rpx); }
.heritage-card-art { display: block; width: 100%; height: 260rpx; background: var(--zy-bg-tint-mint, #e8efe4); }
.heritage-card-copy { display: block; padding: 20rpx 22rpx 24rpx; }
.heritage-card-title-row { display: flex; align-items: baseline; justify-content: space-between; gap: 10rpx; }
.heritage-card-title { font-size: 29rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.heritage-card-tag { flex-shrink: 0; font-size: 20rpx; color: var(--zy-brand-700, #217e76); background: var(--zy-bg-tint-mint, #e8efe4); border-radius: 8rpx; padding: 2rpx 8rpx; }
.heritage-card-meta, .heritage-card-note { display: block; font-size: 22rpx; color: var(--zy-text, #4a5f54); margin-top: 8rpx; }
.heritage-card-note { font-size: 20rpx; }
.heritage-card:active { opacity: .86; }
@media (min-width: 700px) { .heritage-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } .heritage-card-art { height: 220px; } }
@media (min-width: 1050px) { .heritage-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
</style>
