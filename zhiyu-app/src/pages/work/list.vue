<template>
	<view class="page zy-page-enter">
		<!-- 顶部标题 -->
		<view class="header">
			<text class="page-title">作品社区</text>
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
		<view v-else-if="!loading && records.length === 0 && !errorMsg" class="status-box">
			<text class="empty-emoji">🌱</text>
			<text class="status-text">还没有人分享作品，来成为第一个吧</text>
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
				class="work-card"
				:style="{ '--stagger-i': idx }"
				@click="goDetail(item)"
			>
				<!-- 封面图 -->
				<view class="card-cover" :style="coverStyle(item.coverUrl)"></view>

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
				<text v-else-if="!hasMore" class="bottom-text">没有更多了</text>
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

const loading = ref(false)
const errorMsg = ref('')
const records = ref([])
const page = ref(1)
const total = ref(0)
const size = 10

const hasMore = computed(() => page.value * size < total.value)

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
		const data = await http.get('/api/works', { data: { page: pageNum, size } })
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
		const data = await http.get('/api/works', { data: { page: page.value, size } })
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
	if (coverUrl) {
		return `background-image: url(${coverUrl}); background-size: cover; background-position: center;`
	}
	return 'background: linear-gradient(135deg, #2D9B8F 0%, #5BC4A8 100%);'
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

.header {
	margin-bottom: 32rpx;
}

.page-title {
	font-family: var(--zy-serif);
	font-size: 44rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

/* ── 骨架屏 ── */
.skeleton-card {
	overflow: hidden;
	border-radius: 32rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
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

/* ── 状态 ── */
.status-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 120rpx 0;
	gap: 24rpx;
}

.empty-emoji {
	font-size: 80rpx;
}

.status-text {
	font-size: 28rpx;
	color: var(--zy-aid, #9CA3AF);
}

.error-text {
	font-size: 28rpx;
	color: var(--zy-danger, #FF6B6B);
}

/* ── 卡片列表 ── */
.card-list {
	display: flex;
	flex-direction: column;
	gap: 28rpx;
}

.work-card {
	overflow: hidden;
	border-radius: 32rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: 0 4rpx 20rpx rgba(31, 41, 55, 0.05);
	transition: transform 0.15s ease, box-shadow 0.15s ease;
	animation: cardEnter 0.35s ease both;
	animation-delay: calc(var(--stagger-i, 0) * 60ms);
}

.work-card:active {
	transform: scale(0.98);
	box-shadow: 0 2rpx 8rpx rgba(31, 41, 55, 0.08);
}

@keyframes cardEnter {
	from { opacity: 0; transform: translateY(20rpx); }
	to { opacity: 1; transform: translateY(0); }
}

.card-cover {
	height: 240rpx;
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
	gap: 12rpx;
	padding: 32rpx 0;
}

.loading-dot {
	width: 32rpx;
	height: 32rpx;
	border-radius: 50%;
	border: 3rpx solid var(--zy-border, #E5E7EB);
	border-top-color: var(--wot-color-theme, #2D9B8F);
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
