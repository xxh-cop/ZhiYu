<template>
	<view class="page">
		<!-- 404 状态 -->
		<view v-if="notFound" class="not-found">
			<text class="not-found-emoji">🍂</text>
			<text class="not-found-text">作品不存在或已下架</text>
			<wd-button type="primary" plain @click="goBack">返回</wd-button>
		</view>

		<!-- 正常内容 -->
		<template v-else>
			<!-- 顶部导航 -->
			<view class="nav-bar">
				<view class="nav-back" @click="goBack">
					<wd-icon name="arrow-left" size="40rpx" color="#1F2937" />
				</view>
				<text class="nav-title">作品详情</text>
			</view>

			<!-- 作品信息区 -->
			<scroll-view class="content-area" scroll-y>
				<view class="work-info" v-if="work">
					<view class="work-cover" :style="coverStyle(work.coverUrl)"></view>
					<text class="work-title">{{ work.title }}</text>
					<view class="work-meta">
						<text class="work-author">{{ work.authorName }}</text>
						<text class="work-time">{{ formatTime(work.createTime) }}</text>
					</view>
					<text v-if="work.message" class="work-message">{{ work.message }}</text>

					<!-- 点赞 + 收藏按钮 -->
					<view class="action-area">
						<view class="action-btn" @click="handleLike">
							<wd-icon
								:name="liked ? 'heart' : 'heart'"
								size="44rpx"
								:color="liked ? '#FF6B6B' : '#9CA3AF'"
							/>
							<text class="action-count" :class="{ 'count-liked': liked }">{{ likeCount }}</text>
						</view>
						<view class="action-btn" @click="handleFavorite">
							<wd-icon
								:name="favorited ? 'star' : 'star'"
								size="44rpx"
								:color="favorited ? '#FFB547' : '#9CA3AF'"
							/>
							<text class="action-label" :class="{ 'label-favorited': favorited }">收藏</text>
						</view>
					</view>
				</view>

				<!-- 评论列表 -->
				<view class="comment-section">
					<text class="section-title">留暖评论 {{ commentCount }} 条</text>

					<view v-if="comments.length === 0 && !commentLoading" class="comment-empty">
						<text class="empty-emoji">💬</text>
						<text class="empty-text">还没有人留言，说句温暖的话吧</text>
					</view>

					<view
						v-for="comment in comments"
						:key="comment.id"
						class="comment-item"
					>
						<view class="comment-avatar">
							<text class="avatar-text">{{ (comment.authorName || '匿')[0] }}</text>
						</view>
						<view class="comment-main">
							<view class="comment-header">
								<text class="comment-author">{{ comment.authorName }}</text>
								<text class="comment-time">{{ formatTime(comment.createTime) }}</text>
							</view>
							<text class="comment-content">{{ comment.content }}</text>
						</view>
					</view>

					<view v-if="commentLoading" class="comment-more">
						<view class="loading-dot"></view>
						<text class="more-text">正在加载评论…</text>
					</view>
					<view v-else-if="!hasMoreComments && comments.length > 0" class="comment-more">
						<text class="more-text">没有更多评论了</text>
					</view>
				</view>
			</scroll-view>

			<!-- 底部输入区 -->
			<view class="input-area">
				<view class="input-wrap">
					<wd-input
						v-model="commentText"
						placeholder="留下一句温暖..."
						:disabled="sending"
						no-border
						custom-class="comment-input-el"
					/>
				</view>
				<wd-button
					type="primary"
					round
					:disabled="!commentText.trim() || sending"
					@click="handleSendComment"
				>{{ sending ? '发送中' : '发送' }}</wd-button>
			</view>

			<!-- 底部免责声明 -->
			<text class="footnote">本系统不能替代专业心理咨询</text>
		</template>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

const work = ref(null)
const notFound = ref(false)
const liked = ref(false)
const likeCount = ref(0)
const favorited = ref(false)
const commentCount = ref(0)

const comments = ref([])
const commentText = ref('')
const commentLoading = ref(false)
const sending = ref(false)
const commentPage = ref(1)
const commentTotal = ref(0)
const commentSize = 10

let workId = null

const hasMoreComments = computed(() => commentPage.value * commentSize < commentTotal.value)

onLoad((query) => {
	workId = query.id
	if (!workId) {
		notFound.value = true
		return
	}
	loadWork()
	loadComments(1)
})

onReachBottom(() => {
	if (hasMoreComments.value && !commentLoading.value) {
		commentPage.value++
		loadCommentsMore()
	}
})

async function loadWork() {
	try {
		const data = await http.get(`/api/works/${workId}`)
		work.value = data
		likeCount.value = data.likeCount || 0
		commentCount.value = data.commentCount || 0
	} catch (e) {
		if (e && e.code === 1004) {
			notFound.value = true
		} else {
			uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
		}
	}
}

async function loadComments(pageNum) {
	commentLoading.value = true
	commentPage.value = pageNum
	try {
		const data = await http.get(`/api/works/${workId}/comments`, {
			data: { page: pageNum, size: commentSize },
		})
		if (data) {
			commentTotal.value = Number(data.total) || 0
			comments.value = data.records || []
		}
	} catch (e) {
		// 评论加载失败静默
	} finally {
		commentLoading.value = false
	}
}

async function loadCommentsMore() {
	commentLoading.value = true
	try {
		const data = await http.get(`/api/works/${workId}/comments`, {
			data: { page: commentPage.value, size: commentSize },
		})
		if (data) {
			commentTotal.value = Number(data.total) || 0
			comments.value = comments.value.concat(data.records || [])
		}
	} catch (e) {
		// 静默
	} finally {
		commentLoading.value = false
	}
}

async function handleLike() {
	try {
		const res = await http.post(`/api/works/${workId}/like`, { type: 1 })
		liked.value = res.active
		if (res.active) {
			likeCount.value++
		} else {
			likeCount.value = Math.max(0, likeCount.value - 1)
		}
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
	}
}

async function handleFavorite() {
	try {
		const res = await http.post(`/api/works/${workId}/like`, { type: 0 })
		favorited.value = res.active
		uni.showToast({
			title: res.active ? '已收藏' : '已取消收藏',
			icon: 'none',
		})
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
	}
}

async function handleSendComment() {
	const content = commentText.value.trim()
	if (!content || sending.value) return

	sending.value = true
	try {
		const data = await http.post(`/api/works/${workId}/comments`, { content })
		comments.value.push(data)
		commentCount.value++
		commentText.value = ''
		uni.showToast({ title: '评论成功', icon: 'success' })
	} catch (e) {
		// 内容安全过滤返回 1001 + careMessage
		uni.showToast({
			title: (e && e.message) || '评论失败',
			icon: 'none',
			duration: 3000,
		})
	} finally {
		sending.value = false
	}
}

// 封面图样式：小程序 background-image 不支持本地路径，用渐变占位
function coverStyle(coverUrl) {
	// #ifdef H5
	if (coverUrl) {
		return `background-image: url(${coverUrl}); background-size: cover; background-position: center;`
	}
	// #endif
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

function goBack() {
	uni.navigateBack()
}
</script>

<style scoped>
.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 404 ── */
.not-found {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 32rpx;
}

.not-found-emoji {
	font-size: 96rpx;
}

.not-found-text {
	font-size: 32rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 导航栏 ── */
.nav-bar {
	display: flex;
	align-items: center;
	gap: 16rpx;
	padding: 20rpx 32rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border-bottom: 1rpx solid var(--zy-border, #E5E7EB);
	flex-shrink: 0;
}

.nav-back {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	transition: background-color 0.15s ease;
}

.nav-back:active {
	background-color: var(--zy-border, #E5E7EB);
}

.nav-title {
	font-family: var(--zy-serif);
	font-size: 34rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

/* ── 内容区 ── */
.content-area {
	flex: 1;
	overflow: hidden;
}

.work-info {
	padding: 32rpx 32rpx 0;
	animation: fadeInUp 0.3s ease;
}

@keyframes fadeInUp {
	from { opacity: 0; transform: translateY(16rpx); }
	to { opacity: 1; transform: translateY(0); }
}

.work-cover {
	height: 360rpx;
	border-radius: 24rpx;
}

.work-title {
	display: block;
	margin-top: 24rpx;
	font-family: var(--zy-serif);
	font-size: 38rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	line-height: 1.4;
}

.work-meta {
	display: flex;
	align-items: center;
	gap: 20rpx;
	margin-top: 12rpx;
}

.work-author {
	font-size: 26rpx;
	color: var(--zy-content, #4B5563);
}

.work-time {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.work-message {
	display: block;
	margin-top: 20rpx;
	font-size: 28rpx;
	line-height: 1.7;
	color: var(--zy-content, #4B5563);
}

/* ── 点赞 + 收藏 ── */
.action-area {
	display: flex;
	align-items: center;
	gap: 56rpx;
	margin-top: 32rpx;
	padding-bottom: 28rpx;
	border-bottom: 1rpx solid var(--zy-border, #E5E7EB);
}

.action-btn {
	display: flex;
	align-items: center;
	gap: 10rpx;
	padding: 8rpx 20rpx;
	transition: transform 0.15s ease;
}

.action-btn:active {
	transform: scale(0.92);
}

.action-count {
	font-size: 28rpx;
	color: var(--zy-aid, #9CA3AF);
}

.count-liked {
	color: #FF6B6B;
	font-weight: 500;
}

.action-label {
	font-size: 28rpx;
	color: var(--zy-aid, #9CA3AF);
}

.label-favorited {
	color: #FFB547;
	font-weight: 500;
}

/* ── 评论 ── */
.comment-section {
	padding: 32rpx;
}

.section-title {
	font-size: 30rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.comment-empty {
	padding: 60rpx 0;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 16rpx;
}

.empty-emoji {
	font-size: 56rpx;
}

.empty-text {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

.comment-item {
	display: flex;
	gap: 20rpx;
	padding: 24rpx 0;
	border-bottom: 1rpx solid var(--zy-border, #E5E7EB);
}

.comment-avatar {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.avatar-text {
	font-size: 26rpx;
	font-weight: 500;
	color: var(--wot-color-theme, #2D9B8F);
}

.comment-main {
	flex: 1;
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.comment-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.comment-author {
	font-size: 26rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.comment-time {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}

.comment-content {
	font-size: 28rpx;
	line-height: 1.6;
	color: var(--zy-content, #4B5563);
}

.comment-more {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12rpx;
	padding: 24rpx 0;
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

.more-text {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 输入区 ── */
.input-area {
	display: flex;
	align-items: center;
	padding: 16rpx 24rpx;
	gap: 16rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border-top: 1rpx solid var(--zy-border, #E5E7EB);
	flex-shrink: 0;
}

.input-wrap {
	flex: 1;
	background-color: var(--zy-bg, #F8F9FB);
	border-radius: 32rpx;
	padding: 0 8rpx;
	overflow: hidden;
}

.footnote {
	display: block;
	padding: 8rpx 0 16rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
	background-color: var(--zy-bg-warm, #FFFFFF);
	flex-shrink: 0;
}
</style>
