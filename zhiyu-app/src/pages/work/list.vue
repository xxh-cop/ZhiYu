<template>
	<view class="page zy-page-enter">
		<!-- 顶部品牌区 -->
		<view class="brand-header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="brand-title">作品社区</text>
			<text class="brand-subtitle">每个人都是创作者</text>
		</view>

		<!-- 筛选 tab -->
		<view class="filter-tabs zy-slide-up zy-stagger" style="--stagger-i: 1">
			<view
				v-for="tab in tabs"
				:key="tab.value"
				class="filter-tab"
				:class="{ 'tab-active': activeTab === tab.value }"
				@click="switchTab(tab.value)"
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
		<view v-else-if="!loading && records.length === 0 && !errorMsg" class="empty-box zy-slide-up">
			<view class="empty-icon-wrap">
				<text class="empty-emoji">🌱</text>
			</view>
			<text class="empty-title">还没有作品</text>
			<text class="empty-subtitle">发布你的第一件吧</text>
			<view class="empty-btn" @click="goPublish">
				<text class="empty-btn-text">去发布</text>
			</view>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg && records.length === 0" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<wd-button type="primary" plain size="small" @click="loadData(1)">重试</wd-button>
		</view>

		<!-- 作品卡片列表 -->
		<view v-else class="card-list">
			<view
				v-for="(item, idx) in records"
				:key="item.id"
				class="work-card zy-slide-up zy-stagger"
				:style="{ '--stagger-i': idx }"
				@click="goDetail(item)"
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
								<wd-icon name="heart" size="28rpx" :color="item.liked ? '#FF6B6B' : '#9CA3AF'" />
								<text class="meta-num" :class="{ 'meta-liked': item.liked }">{{ item.likeCount }}</text>
							</view>
							<view class="meta-item">
								<wd-icon name="chat" size="28rpx" color="#9CA3AF" />
								<text class="meta-num">{{ item.commentCount }}</text>
							</view>
						</view>
						<text class="meta-time">{{ formatTime(item.createTime) }}</text>
					</view>
				</view>
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
		errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
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
		errorMsg.value = (e && e.message) || '加载失败'
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
	return 'background: linear-gradient(135deg, #2D9B8F 0%, #5BC4A8 100%);'
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
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 顶部品牌区 ── */
.brand-header {
	margin-bottom: 24rpx;
}

.brand-title {
	font-family: var(--zy-serif);
	font-size: 44rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	display: block;
}

.brand-subtitle {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
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
	border: 1rpx solid var(--zy-border, #E5E7EB);
	transition: all var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.tab-active {
	background-color: var(--zy-primary, #2D9B8F);
	border-color: var(--zy-primary, #2D9B8F);
}

.tab-text {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
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
	border: 1rpx solid var(--zy-border, #E5E7EB);
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

/* ── 空状态 ── */
.empty-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 100rpx 0;
	gap: 16rpx;
}

.empty-icon-wrap {
	width: 120rpx;
	height: 120rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 16rpx;
}

.empty-emoji {
	font-size: 60rpx;
}

.empty-title {
	font-size: 30rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.empty-subtitle {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

.empty-btn {
	margin-top: 16rpx;
	padding: 16rpx 48rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
	box-shadow: var(--zy-shadow-md);
}

.empty-btn-text {
	font-size: 28rpx;
	color: #FFFFFF;
	font-weight: 500;
}

/* ── 状态 ── */
.status-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 120rpx 0;
	gap: 24rpx;
}

.error-text {
	font-size: 28rpx;
	color: var(--zy-danger, #FF6B6B);
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
	border: 1rpx solid var(--zy-border, #E5E7EB);
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
	color: #2D9B8F;
	margin-left: 4rpx;
}

.card-cover--text {
	display: flex;
	align-items: center;
	justify-content: center;
	background: linear-gradient(135deg, rgba(45, 155, 143, 0.1) 0%, rgba(91, 196, 168, 0.05) 100%);
}

.card-cover-text-label {
	font-size: 26rpx;
	color: var(--zy-primary, #2D9B8F);
}

.cover-placeholder {
	position: absolute;
	inset: 0;
	background: linear-gradient(135deg, rgba(45, 155, 143, 0.1) 0%, rgba(91, 196, 168, 0.05) 100%);
}

.card-body {
	padding: 28rpx;
}

.card-title {
	font-size: 30rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	line-height: 1.4;
}

.card-author {
	display: block;
	margin-top: 8rpx;
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

.card-message {
	display: -webkit-box;
	margin-top: 12rpx;
	font-size: 26rpx;
	line-height: 1.6;
	color: var(--zy-content, #4B5563);
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
	color: var(--zy-aid, #9CA3AF);
}

.meta-liked {
	color: #FF6B6B;
}

.meta-time {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
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
	background-color: var(--zy-border, #E5E7EB);
}

.loading-dot {
	width: 32rpx;
	height: 32rpx;
	border-radius: 50%;
	border: 3rpx solid var(--zy-border, #E5E7EB);
	border-top-color: var(--zy-primary, #2D9B8F);
	animation: spin 0.8s linear infinite;
}

@keyframes spin {
	to { transform: rotate(360deg); }
}

.bottom-text {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
