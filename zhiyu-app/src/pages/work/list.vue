<template>
	<view class="page zy-page-enter">
		<!-- 顶部标题 -->
		<view class="header">
			<text class="page-title">作品社区</text>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading && records.length === 0" class="card-list">
			<view v-for="i in 3" :key="'sk-'+i" class="skeleton-card zy-card">
				<view class="zy-skeleton skeleton-cover"></view>
				<view class="skeleton-body">
					<view class="zy-skeleton skeleton-title"></view>
					<view class="zy-skeleton skeleton-text"></view>
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
			<view class="retry-btn zy-btn-primary" @click="loadData(1)">
				<text class="retry-text">重试</text>
			</view>
		</view>

		<!-- 作品卡片列表 -->
		<view v-else class="card-list">
			<view
				v-for="item in records"
				:key="item.id"
				class="work-card zy-card zy-clickable"
				@click="goDetail(item)"
			>
				<!-- 封面图 -->
				<view class="card-cover" :style="coverStyle(item.coverUrl)"></view>

				<view class="card-body">
					<text class="card-title">{{ item.title }}</text>
					<text class="card-author">{{ item.authorName }}</text>
					<text v-if="item.message" class="card-message">{{ item.message }}</text>

					<view class="card-meta">
						<text class="meta-item">❤️ {{ item.likeCount }}</text>
						<text class="meta-item">💬 {{ item.commentCount }}</text>
						<text class="meta-time">{{ formatTime(item.createTime) }}</text>
					</view>
				</view>
			</view>

			<!-- 底部状态 -->
			<view v-if="records.length > 0" class="bottom-status">
				<text v-if="loading" class="bottom-text">正在加载...</text>
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
	return 'background-color: #ece3d5;'
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
}

.header {
	margin-bottom: 24rpx;
}

.page-title {
	font-size: 44rpx;
	font-weight: 600;
	color: #3d3a34;
}

/* 骨架屏 */
.skeleton-card {
	overflow: hidden;
}
.skeleton-cover {
	height: 200rpx;
	border-radius: 0;
}
.skeleton-body {
	padding: 24rpx;
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

.status-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 80rpx 0;
	gap: 24rpx;
}

.empty-emoji {
	font-size: 64rpx;
}

.status-text {
	font-size: 28rpx;
	color: #9c8f7d;
}

.error-text {
	font-size: 28rpx;
	color: #b85450;
}

.retry-btn {
	padding: 12rpx 40rpx;
}

.retry-text {
	font-size: 26rpx;
	color: #fffdfa;
}

.card-list {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
}

.work-card {
	overflow: hidden;
}

.card-cover {
	height: 200rpx;
}

.card-body {
	padding: 24rpx;
}

.card-title {
	font-size: 32rpx;
	font-weight: 500;
	color: #3d3a34;
}

.card-author {
	display: block;
	margin-top: 8rpx;
	font-size: 26rpx;
	color: #9c8f7d;
}

.card-message {
	display: block;
	margin-top: 12rpx;
	font-size: 28rpx;
	line-height: 1.6;
	color: #5a534a;
	overflow: hidden;
	text-overflow: ellipsis;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.card-meta {
	display: flex;
	align-items: center;
	gap: 20rpx;
	margin-top: 16rpx;
}

.meta-item {
	font-size: 24rpx;
	color: #8a8378;
}

.meta-time {
	font-size: 24rpx;
	color: #bdb2a1;
	margin-left: auto;
}

.bottom-status {
	padding: 24rpx 0;
	text-align: center;
}

.bottom-text {
	font-size: 24rpx;
	color: #bdb2a1;
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 24rpx;
	color: #bdb2a1;
	text-align: center;
}
</style>
