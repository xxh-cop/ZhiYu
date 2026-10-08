<template>
	<view class="page zy-page-enter">
		<!-- 悬浮新建按钮 -->
		<view class="fab" @click="goCreate">
			<wd-icon name="add" size="44rpx" color="#FFFFFF" />
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading && records.length === 0" class="card-list">
			<view v-for="i in 3" :key="'sk-'+i" class="skeleton-card">
				<view class="skeleton-body">
					<view class="skeleton-line zy-skeleton" style="width: 30%"></view>
					<view class="skeleton-line zy-skeleton" style="width: 90%"></view>
					<view class="skeleton-line zy-skeleton" style="width: 60%"></view>
				</view>
			</view>
		</view>

		<!-- 空状态 -->
		<view v-else-if="!loading && records.length === 0 && !errorMsg" class="empty-box zy-slide-up">
			<view class="empty-icon-wrap">
				<text class="empty-emoji">📝</text>
			</view>
			<text class="empty-title">还没有心情便签</text>
			<text class="empty-sub">记录第一条心情</text>
			<view class="empty-btn" @click="goCreate">
				<text class="empty-btn-text">记录第一条</text>
			</view>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg && records.length === 0" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<wd-button type="primary" plain size="small" @click="loadData(1)">重试</wd-button>
		</view>

		<!-- 便签卡片列表 -->
		<view v-else class="card-list">
			<view
				v-for="(item, idx) in records"
				:key="item.id"
				class="note-card zy-slide-up zy-stagger"
				:style="{ '--stagger-i': idx }"
				@click="goEdit(item)"
				@longpress="onLongPress(item)"
			>
				<view class="card-main">
					<!-- 左侧心情 emoji 圆形底 -->
					<view class="mood-circle" :style="moodGradient(item.mood)">
						<text class="mood-emoji">{{ moodEmoji(item.mood) }}</text>
					</view>
					<!-- 中间内容 -->
					<view class="card-center">
						<text class="note-excerpt">{{ item.excerpt || '（无内容）' }}</text>
						<text class="note-date">{{ formatDate(item.noteDate) }}</text>
					</view>
					<!-- 右侧箭头 -->
					<wd-icon name="arrow-right" size="28rpx" color="#D1D5DB" />
				</view>
				<!-- 底部心情标签 -->
				<view v-if="item.mood" class="mood-tag-row">
					<view class="mood-tag">
						<text class="mood-tag-text">{{ item.mood }}</text>
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
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

const MOOD_MAP = {
	'开心': '😊',
	'平静': '😌',
	'一般': '😐',
	'有点累': '😮💨',
	'不太好': '😕',
	'happy': '😊',
	'calm': '😌',
	'neutral': '😐',
	'tired': '😮💨',
	'down': '😕',
}

const MOOD_GRADIENTS = {
	'开心': 'linear-gradient(135deg, #FFB547, #FFD08A)',
	'平静': 'linear-gradient(135deg, #2D9B8F, #5BC4A8)',
	'一般': 'linear-gradient(135deg, #6B7280, #9CA3AF)',
	'有点累': 'linear-gradient(135deg, #5BC4A8, #8DD9C4)',
	'不太好': 'linear-gradient(135deg, #FF6B6B, #FF9B9B)',
	'happy': 'linear-gradient(135deg, #FFB547, #FFD08A)',
	'calm': 'linear-gradient(135deg, #2D9B8F, #5BC4A8)',
	'neutral': 'linear-gradient(135deg, #6B7280, #9CA3AF)',
	'tired': 'linear-gradient(135deg, #5BC4A8, #8DD9C4)',
	'down': 'linear-gradient(135deg, #FF6B6B, #FF9B9B)',
}

const loading = ref(false)
const errorMsg = ref('')
const records = ref([])
const page = ref(1)
const total = ref(0)
const pageSize = 10

const hasMore = computed(() => page.value * pageSize < total.value)

onShow(() => {
	loadData(1)
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
		const data = await http.get('/api/emotion/notes', { data: { page: pageNum, pageSize } })
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
		const data = await http.get('/api/emotion/notes', { data: { page: page.value, pageSize } })
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

function moodEmoji(mood) {
	if (!mood) return '📝'
	return MOOD_MAP[mood] || '📝'
}

function moodGradient(mood) {
	return MOOD_GRADIENTS[mood] || 'linear-gradient(135deg, #2D9B8F, #5BC4A8)'
}

function formatDate(dateStr) {
	if (!dateStr) return ''
	const parts = dateStr.split('-')
	if (parts.length < 3) return dateStr
	return parts[1] + '-' + parts[2]
}

function goCreate() {
	uni.navigateTo({ url: '/pages/emotion/note-edit' })
}

function goEdit(item) {
	uni.navigateTo({ url: '/pages/emotion/note-edit?id=' + item.id })
}

function onLongPress(item) {
	uni.showActionSheet({
		itemList: ['编辑', '移除'],
		success: (res) => {
			if (res.tapIndex === 0) {
				goEdit(item)
			} else if (res.tapIndex === 1) {
				confirmDelete(item)
			}
		},
	})
}

function confirmDelete(item) {
	uni.showModal({
		title: '移除便签',
		content: '确定要移除这条心情便签吗？',
		confirmText: '移除',
		confirmColor: '#b85450',
		success: (res) => {
			if (res.confirm) {
				doDelete(item)
			}
		},
	})
}

async function doDelete(item) {
	try {
		await http.del('/api/emotion/notes/' + item.id)
		uni.showToast({ title: '已移除', icon: 'success' })
		records.value = records.value.filter((r) => r.id !== item.id)
		total.value = Math.max(0, total.value - 1)
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
	}
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 悬浮按钮 ── */
.fab {
	position: fixed;
	right: 40rpx;
	bottom: 160rpx;
	width: 100rpx;
	height: 100rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: var(--zy-shadow-lg);
	z-index: 100;
	transition: transform var(--zy-duration-base, 200ms) var(--zy-ease-out, ease-out);
}

.fab:active {
	transform: scale(0.92);
}

/* ── 骨架屏 ── */
.skeleton-card {
	overflow: hidden;
	border-radius: var(--zy-radius-md, 24rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	margin-bottom: 24rpx;
}

.skeleton-body {
	padding: 28rpx;
}

.skeleton-line {
	height: 28rpx;
	margin-bottom: 16rpx;
	border-radius: var(--zy-radius-xs, 6rpx);
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

.empty-sub {
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
	gap: 16rpx;
}

.error-text {
	font-size: 28rpx;
	color: var(--zy-danger, #FF6B6B);
}

/* ── 卡片列表 ── */
.card-list {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}

.note-card {
	padding: 24rpx 28rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: var(--zy-shadow-sm);
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out), box-shadow var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.note-card:active {
	transform: translateY(-2rpx);
	box-shadow: var(--zy-shadow-md);
}

.card-main {
	display: flex;
	align-items: center;
	gap: 20rpx;
}

/* ── 心情圆形底 ── */
.mood-circle {
	width: 72rpx;
	height: 72rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.mood-emoji {
	font-size: 36rpx;
	line-height: 1;
}

/* ── 中间内容 ── */
.card-center {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 6rpx;
}

.note-excerpt {
	display: -webkit-box;
	font-size: 28rpx;
	line-height: 1.6;
	color: var(--zy-content, #4B5563);
	overflow: hidden;
	text-overflow: ellipsis;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.note-date {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 底部心情标签 ── */
.mood-tag-row {
	margin-top: 16rpx;
	padding-top: 16rpx;
	border-top: 1rpx solid var(--zy-border, #E5E7EB);
}

.mood-tag {
	display: inline-flex;
	padding: 4rpx 16rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.mood-tag-text {
	font-size: 22rpx;
	color: var(--zy-primary, #2D9B8F);
	font-weight: 500;
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
