<template>
	<view class="heritage-page" :class="[{ 'motion-reduced': reducedMotion }, zyThemeClass]">
		<SectionHead eyebrow="HERITAGE STUDIES" title="非遗纹样馆" tone="heritage" />
		<text class="heritage-subtitle zy-t-body">{{ catalogCount }} · 来源与练习边界可查。</text>

		<view v-if="loading" class="zy-card heritage-status" role="status" aria-live="polite">
			<text class="heritage-muted zy-t-body">正在整理纹样…</text>
		</view>
		<view v-else-if="error" class="zy-card heritage-status" role="alert">
			<text class="heritage-error zy-t-body">{{ error }}</text>
			<AccessibleButton variant="secondary" @click="load">重试</AccessibleButton>
		</view>
		<template v-else>
			<view class="zy-card heritage-search-card">
				<label class="heritage-label zy-t-caption" for="heritage-search">搜索纹样</label>
				<input
					id="heritage-search"
					:key="searchKey"
					v-model="keyword"
					class="heritage-input zy-t-body"
					aria-label="搜索纹样"
					placeholder="输入名称、地域或学习观察点"
					confirm-type="search"
				/>
				<text class="heritage-muted zy-t-caption">可按名称、地域或学习观察点查找。</text>
			</view>

			<view class="heritage-filters" role="group" aria-label="按技艺筛选">
				<AccessibleButton
					:class="['heritage-choice', { 'is-selected': !traditionId }]"
					:aria-pressed="!traditionId"
					variant="ghost"
					size="sm"
					@click="traditionId = ''"
				>全部</AccessibleButton>
				<AccessibleButton
					v-for="tradition in traditions"
					:key="tradition.id"
					:class="['heritage-choice', { 'is-selected': traditionId === tradition.id }]"
					:aria-pressed="traditionId === tradition.id"
					variant="ghost"
					size="sm"
					@click="traditionId = tradition.id"
				>{{ tradition.shortName }}</AccessibleButton>
			</view>

			<view class="heritage-count-row">
				<text class="heritage-count zy-t-body">找到 {{ filtered.length }} 份构图</text>
				<text class="heritage-muted zy-t-caption">点击卡片查看来源与说明</text>
			</view>

			<view v-if="!filtered.length" class="zy-card heritage-status">
				<text class="heritage-muted zy-t-body">{{ patterns.length ? '没有找到匹配的纹样。' : '当前没有可展示的纹样。' }}</text>
				<AccessibleButton v-if="patterns.length" variant="secondary" @click="clearFilters">清空筛选</AccessibleButton>
				<AccessibleButton v-else variant="secondary" @click="load">重试</AccessibleButton>
			</view>
			<view v-else class="heritage-grid">
				<HeritageCard v-for="pattern in filtered" :key="pattern.id" :pattern="pattern" @open="openPattern" />
			</view>
		</template>

		<text class="heritage-foot zy-t-caption">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import { decoratePatterns, filterHeritagePatterns } from '@/utils/heritage-ui'
import { useInteraction } from '@/composables/useInteraction'
import AccessibleButton from '@/components/AccessibleButton.vue'
import SectionHead from '@/components/SectionHead.vue'
import HeritageCard from '@/components/HeritageCard.vue'

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

<style scoped>
.heritage-page {
	max-width: 1180px;
	margin: 0 auto;
	padding: var(--zy-space-lg) var(--zy-space-md) var(--zy-space-3xl);
}
.heritage-subtitle {
	display: block;
	margin: 8rpx 0 var(--zy-space-md);
	color: var(--zy-text);
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
.heritage-search-card {
	padding: 24rpx 28rpx;
}
.heritage-label {
	display: block;
	margin-bottom: 10rpx;
	font-weight: 600;
	color: var(--zy-title);
}
.heritage-input {
	display: block;
	width: 100%;
	height: 88rpx;
	padding: 0 20rpx;
	margin-bottom: 10rpx;
	color: var(--zy-title);
	background: var(--zy-bg-sunken);
	border: 2rpx solid var(--zy-border);
	border-radius: var(--zy-radius-control);
}
.heritage-filters {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin: 22rpx 0 8rpx;
}
.heritage-choice {
	flex: 0 0 auto;
	width: auto;
	min-height: 72rpx;
	padding: 10rpx 24rpx;
	margin: 0;
	border: 2rpx solid var(--zy-border);
	border-radius: var(--zy-radius-full);
	background: var(--zy-card-bg);
	color: var(--zy-text);
}
.heritage-choice.is-selected {
	background: var(--zy-brand-600);
	border-color: var(--zy-brand-600);
	color: #ffffff;
}
.heritage-count-row {
	display: flex;
	justify-content: space-between;
	align-items: baseline;
	gap: 16rpx;
	margin: 24rpx 0 16rpx;
	flex-wrap: wrap;
}
.heritage-count {
	font-weight: 600;
	color: var(--zy-title);
}
.heritage-grid {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 18rpx;
}
.heritage-foot {
	display: block;
	margin-top: var(--zy-space-3xl);
	text-align: center;
	color: var(--zy-text);
}
@media (min-width: 700px) {
	.heritage-grid {
		grid-template-columns: repeat(3, minmax(0, 1fr));
	}
}
@media (min-width: 1050px) {
	.heritage-grid {
		grid-template-columns: repeat(4, minmax(0, 1fr));
	}
}
</style>
