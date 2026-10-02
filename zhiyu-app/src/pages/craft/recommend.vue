<template>
	<view class="page">
		<!-- 顶部标题区 -->
		<view class="header">
			<text class="page-title">为你推荐</text>
			<view v-if="moodTag" class="mood-badge">
				<text class="mood-text">{{ moodTag }}</text>
			</view>
		</view>

		<!-- 整体推荐理由 -->
		<view v-if="reason" class="reason-card">
			<text class="reason-text">{{ reason }}</text>
		</view>

		<!-- 刷新按钮 -->
		<view class="refresh-btn" @click="loadData">
			<text class="refresh-icon">↻</text>
			<text class="refresh-text">换一批</text>
		</view>

		<!-- 加载中 -->
		<view v-if="loading" class="status-box">
			<text class="status-text">正在为你挑选…</text>
		</view>

		<!-- 空状态 -->
		<view v-else-if="!loading && items.length === 0 && !errorMsg" class="status-box">
			<text class="status-text">暂时没有匹配的手作，换个时间试试</text>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<view class="retry-btn" @click="loadData">
				<text class="retry-text">重试</text>
			</view>
		</view>

		<!-- 卡片列表 -->
		<view v-else class="card-list">
			<view
				v-for="item in items"
				:key="item.projectId"
				class="craft-card"
				@click="goSession(item)"
			>
				<!-- 封面图 -->
				<view class="card-cover" :style="coverStyle(item.coverUrl)">
					<view class="level-badge">
						<text class="level-text">{{ levelLabel(item.level) }}</text>
					</view>
				</view>

				<view class="card-body">
					<text class="card-title">{{ item.title }}</text>
					<text class="card-intro">{{ item.intro }}</text>
					<text class="card-culture">{{ item.cultureBg }}</text>

					<view class="card-tags">
						<view class="time-badge">
							<text class="time-text">约 {{ item.estMinutes }} 分钟</text>
						</view>
						<view
							v-for="tag in splitTags(item.therapyTags)"
							:key="tag"
							class="tag-badge"
						>
							<text class="tag-text">{{ tag }}</text>
						</view>
					</view>

					<text class="card-reason">{{ item.reason }}</text>
				</view>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

const loading = ref(false)
const errorMsg = ref('')
const items = ref([])
const reason = ref('')
const moodTag = ref('')
const sessionId = ref(null)

const MOOD_LABELS = {
	anxious: '焦虑',
	stressed: '压力',
	depressed: '低落',
	normal: '平静',
}

onLoad((query) => {
	sessionId.value = query.sessionId || null
	loadData()
})

async function loadData() {
	loading.value = true
	errorMsg.value = ''
	items.value = []
	reason.value = ''
	moodTag.value = ''

	try {
		const params = {}
		if (sessionId.value) {
			params.sessionId = sessionId.value
		}
		const data = await http.get('/api/craft/recommend', { data: params })
		if (data) {
			reason.value = data.reason || ''
			moodTag.value = MOOD_LABELS[data.mood] || data.mood || ''
			items.value = data.items || []
		}
	} catch (e) {
		errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
	} finally {
		loading.value = false
	}
}

function splitTags(tags) {
	if (!tags) return []
	return tags.split(',').map((t) => t.trim()).filter(Boolean)
}

function levelLabel(level) {
	if (level === 1) return '入门'
	if (level === 2) return '进阶'
	if (level === 3) return '高阶'
	return ''
}

function coverStyle(coverUrl) {
	if (coverUrl) {
		return `background-image: url(${coverUrl}); background-size: cover; background-position: center;`
	}
	return 'background-color: #ece3d5;'
}

function goSession(item) {
	uni.navigateTo({
		url: `/pages/craft/session?projectId=${item.projectId}&projectTitle=${encodeURIComponent(item.title)}`,
	})
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 80rpx;
	box-sizing: border-box;
	background-color: #f7f4ef;
}

.header {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin-bottom: 24rpx;
}

.page-title {
	font-size: 44rpx;
	font-weight: 600;
	color: #3d3a34;
}

.mood-badge {
	padding: 6rpx 20rpx;
	border-radius: 999rpx;
	background-color: #ece3d5;
}

.mood-text {
	font-size: 24rpx;
	color: #8b6f47;
}

.reason-card {
	padding: 24rpx;
	border-radius: 12px;
	background-color: #fffdfa;
	border: 2rpx solid #e6ddcf;
	box-shadow: 0 2px 8px rgba(139, 111, 71, 0.08);
	margin-bottom: 24rpx;
}

.reason-text {
	font-size: 28rpx;
	line-height: 1.6;
	color: #5a534a;
}

.refresh-btn {
	display: flex;
	align-items: center;
	gap: 8rpx;
	padding: 12rpx 24rpx;
	align-self: flex-end;
	margin-bottom: 24rpx;
}

.refresh-icon {
	font-size: 28rpx;
	color: #8b6f47;
}

.refresh-text {
	font-size: 26rpx;
	color: #8b6f47;
}

.status-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 80rpx 0;
	gap: 24rpx;
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
	border-radius: 8px;
	background-color: #8b6f47;
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

.craft-card {
	border-radius: 12px;
	background-color: #fffdfa;
	border: 2rpx solid #e6ddcf;
	box-shadow: 0 2px 8px rgba(139, 111, 71, 0.08);
	overflow: hidden;
}

.card-cover {
	height: 200rpx;
	display: flex;
	align-items: flex-start;
	justify-content: flex-end;
	padding: 16rpx;
}

.level-badge {
	padding: 4rpx 16rpx;
	border-radius: 999rpx;
	background-color: rgba(139, 111, 71, 0.85);
}

.level-text {
	font-size: 22rpx;
	color: #fffdfa;
}

.card-body {
	padding: 24rpx;
}

.card-title {
	font-size: 34rpx;
	font-weight: 600;
	color: #3d3a34;
}

.card-intro {
	display: block;
	margin-top: 12rpx;
	font-size: 28rpx;
	line-height: 1.6;
	color: #5a534a;
}

.card-culture {
	display: block;
	margin-top: 8rpx;
	font-size: 26rpx;
	color: #9c8f7d;
}

.card-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 16rpx;
}

.time-badge {
	padding: 4rpx 16rpx;
	border-radius: 999rpx;
	background-color: #f0ebe1;
}

.time-text {
	font-size: 22rpx;
	color: #8a8378;
}

.tag-badge {
	padding: 4rpx 16rpx;
	border-radius: 999rpx;
	background-color: rgba(200, 155, 110, 0.15);
}

.tag-text {
	font-size: 22rpx;
	color: #c89b6e;
}

.card-reason {
	display: block;
	margin-top: 16rpx;
	font-size: 26rpx;
	font-style: italic;
	color: #8a8378;
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 24rpx;
	color: #bdb2a1;
	text-align: center;
}
</style>
