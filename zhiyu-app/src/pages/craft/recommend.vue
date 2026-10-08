<template>
	<view class="page zy-page-enter">
		<!-- 顶部标题区 -->
		<view class="header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="page-title">为你推荐</text>
			<view v-if="moodTag" class="mood-badge">
				<text class="mood-text">{{ moodTag }}</text>
			</view>
		</view>

		<!-- 推荐理由卡片 -->
		<view v-if="reason" class="reason-card zy-slide-up zy-stagger" style="--stagger-i: 1">
			<view class="reason-quote">"</view>
			<view class="reason-header">
				<view class="reason-icon-wrap">
					<wd-icon name="chat" size="28rpx" color="#2D9B8F" />
				</view>
				<text class="reason-label">小织推荐</text>
			</view>
			<text class="reason-text">{{ reason }}</text>
		</view>

		<!-- 刷新按钮 -->
		<view class="refresh-btn zy-slide-up zy-stagger" style="--stagger-i: 2" @click="loadData">
			<text class="refresh-icon">↻</text>
			<text class="refresh-text">换一批</text>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading" class="card-list">
			<view v-for="i in 3" :key="'sk-'+i" class="skeleton-card">
				<view class="skeleton-cover zy-skeleton"></view>
				<view class="skeleton-body">
					<view class="skeleton-title zy-skeleton"></view>
					<view class="skeleton-text zy-skeleton"></view>
				</view>
			</view>
		</view>

		<!-- 空状态 -->
		<view v-else-if="items.length === 0 && !errorMsg" class="status-box">
			<text class="status-text">暂时没有匹配的手作，换个时间再试试</text>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<wd-button type="error" size="small" plain @click="loadData">重试</wd-button>
		</view>

		<!-- 卡片列表 -->
		<view v-else class="card-list">
			<view
				v-for="(item, idx) in items"
				:key="item.projectId"
				class="craft-card zy-slide-up zy-stagger"
				:style="{ '--stagger-i': idx + 3 }"
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
					<view class="card-meta">
						<view v-if="item.category" class="meta-pill">
							<text class="meta-pill-text">{{ item.category }}</text>
						</view>
						<view class="meta-pill">
							<text class="meta-pill-text">约 {{ item.estMinutes }} 分钟</text>
						</view>
					</view>

					<view v-if="splitTags(item.therapyTags).length" class="card-tags">
						<view
							v-for="tag in splitTags(item.therapyTags)"
							:key="tag"
							class="tag-badge"
						>
							<text class="tag-text">{{ tag }}</text>
						</view>
					</view>

					<text v-if="item.reason" class="card-reason">{{ item.reason }}</text>
				</view>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref } from 'vue'
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
	// #ifdef H5
	if (coverUrl) {
		return `background-image: url(${coverUrl}); background-size: cover; background-position: center;`
	}
	// #endif
	return 'background: linear-gradient(135deg, #2D9B8F, #5BC4A8);'
}

function goSession(item) {
	uni.navigateTo({
		url: `/pages/craft/session?projectId=${item.projectId}&projectTitle=${encodeURIComponent(item.title)}`,
	})
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 80rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 顶部标题区 ── */
.header {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin-bottom: 24rpx;
}

.page-title {
	font-size: 44rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
}

.mood-badge {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.mood-text {
	font-size: 24rpx;
	color: var(--zy-primary, #2D9B8F);
}

/* ── 推荐理由卡片 ── */
.reason-card {
	position: relative;
	padding: 28rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: var(--zy-shadow-sm);
	margin-bottom: 24rpx;
	overflow: hidden;
}

.reason-quote {
	position: absolute;
	top: 4rpx;
	left: 16rpx;
	font-size: 56rpx;
	font-family: var(--zy-serif);
	color: var(--zy-primary, #2D9B8F);
	opacity: 0.15;
	line-height: 1;
}

.reason-header {
	display: flex;
	align-items: center;
	gap: 10rpx;
	margin-bottom: 12rpx;
}

.reason-icon-wrap {
	width: 48rpx;
	height: 48rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	display: flex;
	align-items: center;
	justify-content: center;
}

.reason-label {
	font-size: 26rpx;
	font-weight: 500;
	color: var(--zy-primary, #2D9B8F);
}

.reason-text {
	font-size: 28rpx;
	line-height: 1.8;
	color: var(--zy-content, #4B5563);
}

/* ── 刷新按钮 ── */
.refresh-btn {
	display: flex;
	align-items: center;
	gap: 8rpx;
	padding: 12rpx 24rpx;
	margin-bottom: 24rpx;
}

.refresh-icon {
	font-size: 28rpx;
	color: var(--zy-primary, #2D9B8F);
	transition: transform 0.5s ease;
}

.refresh-btn:active .refresh-icon {
	transform: rotate(360deg);
}

.refresh-text {
	font-size: 26rpx;
	color: var(--zy-primary, #2D9B8F);
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

/* ── 状态区 ── */
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
	gap: 24rpx;
}

.craft-card {
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: var(--zy-shadow-sm);
	overflow: hidden;
	transition: transform var(--zy-duration-base, 200ms) var(--zy-ease-out, ease-out), box-shadow var(--zy-duration-base, 200ms) var(--zy-ease-out, ease-out);
}

.craft-card:active {
	transform: translateY(-2rpx);
	box-shadow: var(--zy-shadow-md);
}

/* ── 封面图 ── */
.card-cover {
	height: 280rpx;
	display: flex;
	align-items: flex-start;
	justify-content: flex-end;
	padding: 16rpx;
}

.level-badge {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: rgba(255, 181, 71, 0.9);
}

.level-text {
	font-size: 22rpx;
	color: #FFFFFF;
	font-weight: 500;
}

/* ── 卡片内容 ── */
.card-body {
	padding: 28rpx;
}

.card-title {
	font-size: 32rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.card-meta {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 16rpx;
}

.meta-pill {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg, #F3F4F6);
}

.meta-pill-text {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 疗愈标签 ── */
.card-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 12rpx;
}

.tag-badge {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.tag-text {
	font-size: 22rpx;
	color: var(--zy-primary, #2D9B8F);
}

/* ── 单项推荐理由 ── */
.card-reason {
	display: block;
	margin-top: 20rpx;
	padding-top: 20rpx;
	border-top: 1rpx solid var(--zy-border, #E5E7EB);
	font-size: 24rpx;
	font-style: italic;
	color: var(--zy-aid, #9CA3AF);
	line-height: 1.6;
}

/* ── 底部免责 ── */
.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
