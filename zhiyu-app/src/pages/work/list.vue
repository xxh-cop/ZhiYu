<template>
	<view class="page zy-page-enter">
		<!-- 顶部品牌区 -->
		<view class="brand-header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="brand-title">作品社区</text>
			<text class="brand-subtitle">每个人都是创作者</text>
		</view>

		<!-- 筛选 tab -->
		<view class="filter-tabs zy-slide-up zy-stagger" style="--stagger-i: 1" role="tablist" aria-label="作品排序">
			<view
				v-for="tab in tabs"
				:key="tab.value"
				class="filter-tab"
				:class="{ 'tab-active': activeTab === tab.value }"
				role="tab"
				:aria-selected="activeTab === tab.value"
				:tabindex="activeTab === tab.value ? 0 : -1"
				@click="switchTab(tab.value)"
				@keydown.enter.prevent="switchTab(tab.value)"
				@keydown.space.prevent="switchTab(tab.value)"
			>
				<text class="tab-text" :class="{ 'tab-text-active': activeTab === tab.value }">{{ tab.label }}</text>
			</view>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading && records.length === 0" class="card-list">
			<view v-for="i in 3" :key="'sk-'+i" class="skeleton-card">
				<view class="skeleton-cover zy-skeleton"></view>
				<view class="skeleton-body">
					<view class="skeleton-title zy-skeleton"></view>
					<view class="skeleton-text zy-skeleton"></view>
				</view>
			</view>
		</view>

		<!-- 空状态 -->
		<StateBlock
			v-else-if="!loading && records.length === 0 && !errorMsg"
			type="empty"
			art="community"
			title="还没有作品"
			description="发布你的第一件吧"
			action-text="去发布"
			@action="goPublish"
		/>

		<!-- 错误状态 -->
		<StateBlock
			v-else-if="errorMsg && records.length === 0"
			type="error"
			title="作品没有加载成功"
			:description="errorMsg"
			action-text="重试"
			:action-loading="loading"
			@action="loadData(1)"
		/>

		<!-- 作品卡片列表 -->
		<view v-else class="card-list">
			<view
				v-for="(item, idx) in records"
				:key="item.id"
				class="work-card zy-slide-up zy-stagger"
				:style="{ '--stagger-i': idx }"
				role="button"
				tabindex="0"
				:aria-label="'查看作品 ' + item.title"
				@click="goDetail(item)"
				@keydown.enter.prevent="goDetail(item)"
				@keydown.space.prevent="goDetail(item)"
			>
			<!-- 封面区域：根据 mediaType 渲染 -->
				<view v-if="!item.mediaType || item.mediaType === 'image'" class="card-cover" :style="coverStyle(item.coverUrl)">
					<view class="cover-placeholder"></view>
				</view>
				<view v-else-if="item.mediaType === 'video'" class="card-cover card-cover--video">
					<video v-if="item.videoUrl" class="card-cover-video" :src="fullUrl(item.videoUrl)" object-fit="cover" :controls="false" :autoplay="false" />
					<view class="card-cover-play">
						<view class="play-icon">
							<text class="play-icon-text">▶</text>
						</view>
					</view>
				</view>
				<view v-else-if="item.mediaType === 'text'" class="card-cover card-cover--text">
					<text class="card-cover-text-label">纯文字作品</text>
				</view>

				<view class="card-body">
					<text class="card-title">{{ item.title }}</text>
					<text class="card-author">{{ item.authorName }}</text>
					<text v-if="item.message" class="card-message">{{ item.message }}</text>

					<view class="card-meta">
						<view class="meta-left">
							<view class="meta-item">
								<wd-icon name="heart" size="28rpx" :color="item.liked ? '#a64842' : '#4a5f54'" />
								<text class="meta-num" :class="{ 'meta-liked': item.liked }">{{ item.likeCount }}</text>
							</view>
							<view class="meta-item">
								<wd-icon name="chat" size="28rpx" color="#4a5f54" />
								<text class="meta-num">{{ item.commentCount }}</text>
							</view>
						</view>
						<text class="meta-time">{{ formatTime(item.createTime) }}</text>
					</view>
				</view>
			</view>

			<!-- 分页失败：保留已加载作品 -->
			<view v-if="errorMsg && records.length > 0" class="bottom-error" role="alert">
				<text>{{ errorMsg }}</text>
				<AccessibleButton @click="loadMore">重试</AccessibleButton>
			</view>

			<!-- 底部状态 -->
			<view v-if="records.length > 0" class="bottom-status">
				<view v-if="loading" class="loading-dot"></view>
				<text v-if="loading" class="bottom-text">正在加载…</text>
				<template v-else-if="!hasMore">
					<view class="bottom-divider"></view>
					<text class="bottom-text">没有更多了</text>
					<view class="bottom-divider"></view>
				</template>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { API_BASE_URL } from '../../utils/config.js'
import StateBlock from '../../components/StateBlock.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'

const tabs = [
	{ label: '全部', value: 'all' },
	{ label: '最新', value: 'latest' },
	{ label: '热门', value: 'hot' },
]
const activeTab = ref('all')

const loading = ref(false)
const errorMsg = ref('')
const records = ref([])
const page = ref(1)
const total = ref(0)
const size = 10

const hasMore = computed(() => page.value * size < total.value)

function switchTab(val) {
	if (activeTab.value === val) return
	activeTab.value = val
	loadData(1)
}

onPullDownRefresh(() => {
	loadData(1).finally(() => {
		uni.stopPullDownRefresh()
	})
})

onReachBottom(() => {
	if (hasMore.value && !loading.value) {
		page.value++
		loadMore()
	}
})

async function loadData(pageNum) {
	loading.value = true
	errorMsg.value = ''
	if (pageNum === 1) {
		records.value = []
	}
	page.value = pageNum

	try {
		const params = { page: pageNum, size }
		if (activeTab.value === 'latest') params.sort = 'latest'
		if (activeTab.value === 'hot') params.sort = 'hot'
		const data = await http.get('/api/works', { data: params })
		if (data) {
			total.value = Number(data.total) || 0
			records.value = data.records || []
		}
	} catch (e) {
		errorMsg.value = '作品加载失败，请稍后重试'
	} finally {
		loading.value = false
	}
}

async function loadMore() {
	loading.value = true
	try {
		const params = { page: page.value, size }
		if (activeTab.value === 'latest') params.sort = 'latest'
		if (activeTab.value === 'hot') params.sort = 'hot'
		const data = await http.get('/api/works', { data: params })
		if (data) {
			total.value = Number(data.total) || 0
			records.value = records.value.concat(data.records || [])
		}
	} catch (e) {
		errorMsg.value = '更多作品加载失败，请重试'
	} finally {
		loading.value = false
	}
}

function coverStyle(coverUrl) {
	// #ifdef H5
	if (coverUrl) {
		const url = fullUrl(coverUrl)
		return `background-image: url(${url}); background-size: cover; background-position: center;`
	}
	// #endif
	// #ifndef H5
	if (coverUrl) {
		const url = fullUrl(coverUrl)
		return `background-image: url(${url}); background-size: cover; background-position: center;`
	}
	// #endif
	return 'background: linear-gradient(135deg, #217e76 0%, #5bc4a8 100%);'
}

function fullUrl(url) {
	if (!url) return ''
	if (url.startsWith('http')) return url
	return API_BASE_URL + url
}

function formatTime(time) {
	if (!time) return ''
	const parts = time.split('T')
	if (parts.length < 2) return time
	const dateParts = parts[0].split('-')
	if (dateParts.length < 3) return time
	return `${parseInt(dateParts[1])}月${parseInt(dateParts[2])}日`
}

function goDetail(item) {
	uni.navigateTo({
		url: `/pages/work/detail?id=${item.id}`,
	})
}

function goPublish() {
	uni.navigateTo({ url: '/pages/work/publish' })
}

// 初始加载
loadData(1)
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #f5f7f2);
}

/* ── 顶部品牌区 ── */
.brand-header {
	margin-bottom: 24rpx;
}

.brand-title {
	font-family: var(--zy-serif);
	font-size: 44rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
	display: block;
}

.brand-subtitle {
	font-size: 26rpx;
	color: var(--zy-aid, #4a5f54);
	margin-top: 4rpx;
	display: block;
}

/* ── 筛选 tab ── */
.filter-tabs {
	display: flex;
	gap: 12rpx;
	margin-bottom: 24rpx;
}

.filter-tab {
	padding: 10rpx 28rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	transition: all var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.tab-active {
	background-color: var(--zy-primary, #217e76);
	border-color: var(--zy-primary, #217e76);
}

.tab-text {
	font-size: 26rpx;
	color: var(--zy-aid, #4a5f54);
}

.tab-text-active {
	color: #FFFFFF;
	font-weight: 500;
}

/* ── 骨架屏 ── */
.skeleton-card {
	overflow: hidden;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
}
.skeleton-cover {
	height: 240rpx;
	border-radius: 0;
}
.skeleton-body {
	padding: 28rpx;
}
.skeleton-title {
	height: 36rpx;
	width: 60%;
	margin-bottom: 16rpx;
}
.skeleton-text {
	height: 28rpx;
	width: 90%;
}

/* ── 卡片列表 ── */
.card-list {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
}

.work-card {
	overflow: hidden;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out), box-shadow var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.work-card:active {
	transform: translateY(-2rpx);
	box-shadow: var(--zy-shadow-md);
}

.card-cover {
	height: 280rpx;
	position: relative;
}

.card-cover--video {
	overflow: hidden;
	position: relative;
}

.card-cover-video {
	width: 100%;
	height: 100%;
}

.card-cover-play {
	position: absolute;
	inset: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: rgba(0, 0, 0, 0.2);
}

.play-icon {
	width: 72rpx;
	height: 72rpx;
	border-radius: 50%;
	background-color: rgba(255, 255, 255, 0.85);
	display: flex;
	align-items: center;
	justify-content: center;
}

.play-icon-text {
	font-size: 28rpx;
	color: #1A645E;
	margin-left: 4rpx;
}

.card-cover--text {
	display: flex;
	align-items: center;
	justify-content: center;
	background: linear-gradient(135deg, rgba(33, 126, 118, 0.1) 0%, rgba(91, 196, 168, 0.05) 100%);
}

.card-cover-text-label {
	font-size: 26rpx;
	color: var(--zy-brand-700, #217e76);
}

.cover-placeholder {
	position: absolute;
	inset: 0;
	background: linear-gradient(135deg, rgba(33, 126, 118, 0.1) 0%, rgba(91, 196, 168, 0.05) 100%);
}

.card-body {
	padding: 28rpx;
}

.card-title {
	font-size: 30rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
	line-height: 1.4;
}

.card-author {
	display: block;
	margin-top: 8rpx;
	font-size: 26rpx;
	color: var(--zy-aid, #4a5f54);
}

.card-message {
	display: -webkit-box;
	margin-top: 12rpx;
	font-size: 26rpx;
	line-height: 1.6;
	color: var(--zy-content, #4a5f54);
	overflow: hidden;
	text-overflow: ellipsis;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.card-meta {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 20rpx;
}

.meta-left {
	display: flex;
	align-items: center;
	gap: 28rpx;
}

.meta-item {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.meta-num {
	font-size: 24rpx;
	color: var(--zy-aid, #4a5f54);
}

.meta-liked {
	color: var(--zy-care, #a64842);
}

.meta-time {
	font-size: 24rpx;
	color: var(--zy-aid, #4a5f54);
}

/* ── 底部 ── */
.bottom-status {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 16rpx;
	padding: 32rpx 0;
}

.bottom-divider {
	width: 64rpx;
	height: 1rpx;
	background-color: var(--zy-border, #dfe7dc);
}

.loading-dot {
	width: 32rpx;
	height: 32rpx;
	border-radius: 50%;
	border: 3rpx solid var(--zy-border, #dfe7dc);
	border-top-color: var(--zy-primary, #217e76);
	animation: spin 0.8s linear infinite;
}

@keyframes spin {
	to { transform: rotate(360deg); }
}

.bottom-text {
	font-size: 24rpx;
	color: var(--zy-aid, #4a5f54);
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #4a5f54);
	text-align: center;
}
</style>

<style scoped>
.filter-tab:focus-visible,
.work-card:focus-visible {
	outline: 3px solid var(--zy-primary, #217e76);
	outline-offset: 3px;
}
.bottom-error {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;
	margin-top: 20rpx;
	padding: 24rpx;
	border: 1rpx dashed var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-md, 24rpx);
	background: var(--zy-card-bg, #fff);
	color: var(--zy-content, #4a5f54);
	font-size: 24rpx;
	line-height: 1.7;
	text-align: center;
}
.bottom-error button {
	min-height: 72rpx;
	padding: 0 26rpx;
	border: 1rpx solid var(--zy-primary, #217e76);
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-brand-700, #217e76);
	font-size: 24rpx;
	line-height: 2.6;
}
.bottom-error button::after { border: 0; }
</style>
