<template>
	<view class="page">
		<!-- 404 状态 -->
		<view v-if="notFound" class="not-found">
			<text class="not-found-text">作品不存在或已下架</text>
			<view class="back-btn" @click="goBack">
				<text class="back-text">返回</text>
			</view>
		</view>

		<!-- 正常内容 -->
		<template v-else>
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

					<!-- 点赞按钮 -->
					<view class="like-area">
						<view class="like-btn" @click="handleLike">
							<text class="like-icon" :class="{ 'liked': liked }">{{ liked ? '♥' : '♡' }}</text>
							<text class="like-count">{{ likeCount }}</text>
						</view>
						<!-- TODO(T1.9d): 增加收藏按钮（type=0） -->
					</view>
				</view>

				<!-- 评论列表 -->
				<view class="comment-section">
					<text class="section-title">留暖评论 {{ commentCount }} 条</text>

					<view v-if="comments.length === 0 && !commentLoading" class="comment-empty">
						<text class="empty-text">还没有评论，留下第一句温暖吧</text>
					</view>

					<view
						v-for="comment in comments"
						:key="comment.id"
						class="comment-item"
					>
						<text class="comment-author">{{ comment.authorName }}</text>
						<text class="comment-content">{{ comment.content }}</text>
						<text class="comment-time">{{ formatTime(comment.createTime) }}</text>
					</view>

					<view v-if="commentLoading" class="comment-more">
						<text class="more-text">正在加载评论...</text>
					</view>
					<view v-else-if="!hasMoreComments" class="comment-more">
						<text class="more-text">没有更多评论了</text>
					</view>
				</view>
			</scroll-view>

			<!-- 底部输入区 -->
			<view class="input-area">
				<input
					class="comment-input"
					type="text"
					v-model="commentText"
					placeholder="留下一句温暖..."
					placeholder-class="input-placeholder"
					:disabled="sending"
					maxlength="255"
				/>
				<view
					class="send-btn"
					:class="{ 'btn-disabled': !commentText.trim() || sending }"
					@click="handleSendComment"
				>
					<text class="send-text">{{ sending ? '发送中...' : '发送' }}</text>
				</view>
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

function goBack() {
	uni.navigateBack()
}
</script>

<style scoped>
.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	background-color: #f7f4ef;
}

/* 404 */
.not-found {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 32rpx;
}

.not-found-text {
	font-size: 32rpx;
	color: #8a8378;
}

.back-btn {
	padding: 16rpx 48rpx;
	border-radius: 8px;
	background-color: #8b6f47;
}

.back-text {
	font-size: 28rpx;
	color: #fffdfa;
}

/* 内容区 */
.content-area {
	flex: 1;
	overflow: hidden;
}

.work-info {
	padding: 24rpx 24rpx 0;
}

.work-cover {
	height: 300rpx;
	border-radius: 8px;
}

.work-title {
	display: block;
	margin-top: 20rpx;
	font-size: 38rpx;
	font-weight: 600;
	color: #2b2b2b;
}

.work-meta {
	display: flex;
	align-items: center;
	gap: 20rpx;
	margin-top: 12rpx;
}

.work-author {
	font-size: 26rpx;
	color: #8b6f47;
}

.work-time {
	font-size: 24rpx;
	color: #bdb2a1;
}

.work-message {
	display: block;
	margin-top: 16rpx;
	font-size: 28rpx;
	line-height: 1.6;
	color: #5a534a;
}

/* 点赞 */
.like-area {
	display: flex;
	align-items: center;
	margin-top: 24rpx;
	padding-bottom: 24rpx;
	border-bottom: 2rpx solid #e5ddd0;
}

.like-btn {
	display: flex;
	align-items: center;
	gap: 8rpx;
	padding: 8rpx 20rpx;
}

.like-icon {
	font-size: 36rpx;
	color: #8a8378;
}

.like-icon.liked {
	color: #b85450;
}

.like-count {
	font-size: 28rpx;
	color: #8a8378;
}

/* 评论 */
.comment-section {
	padding: 24rpx;
}

.section-title {
	font-size: 30rpx;
	font-weight: 600;
	color: #3d3a34;
}

.comment-empty {
	padding: 40rpx 0;
	text-align: center;
}

.empty-text {
	font-size: 26rpx;
	color: #bdb2a1;
}

.comment-item {
	padding: 20rpx 0;
	border-bottom: 2rpx solid #f0ebe1;
}

.comment-author {
	font-size: 26rpx;
	font-weight: 600;
	color: #8b6f47;
}

.comment-content {
	display: block;
	margin-top: 8rpx;
	font-size: 28rpx;
	line-height: 1.6;
	color: #2b2b2b;
}

.comment-time {
	display: block;
	margin-top: 8rpx;
	font-size: 22rpx;
	color: #bdb2a1;
}

.comment-more {
	padding: 16rpx 0;
	text-align: center;
}

.more-text {
	font-size: 24rpx;
	color: #bdb2a1;
}

/* 输入区 */
.input-area {
	display: flex;
	align-items: center;
	padding: 16rpx 24rpx;
	gap: 16rpx;
	background-color: #fffdfa;
	border-top: 2rpx solid #e5ddd0;
}

.comment-input {
	flex: 1;
	height: 72rpx;
	padding: 0 20rpx;
	border-radius: 8px;
	border: 2rpx solid #e5ddd0;
	background-color: #f7f4ef;
	font-size: 28rpx;
	color: #2b2b2b;
}

.input-placeholder {
	color: #a49c90;
}

.send-btn {
	padding: 12rpx 32rpx;
	border-radius: 8px;
	background-color: #8b6f47;
}

.btn-disabled {
	opacity: 0.4;
}

.send-text {
	font-size: 28rpx;
	color: #fffdfa;
}

.footnote {
	display: block;
	padding: 8rpx 0 12rpx;
	font-size: 22rpx;
	color: #bdb2a1;
	text-align: center;
	background-color: #fffdfa;
}
</style>
