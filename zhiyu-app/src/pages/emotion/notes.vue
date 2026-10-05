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
		<view v-else-if="!loading && records.length === 0 && !errorMsg" class="status-box">
			<text class="empty-emoji">📝</text>
			<text class="status-text">还没有心情便签</text>
			<text class="status-sub">点击右下角 + 记录一条</text>
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
				class="note-card"
				:style="{ '--stagger-i': idx }"
				@click="goEdit(item)"
				@longpress="onLongPress(item)"
			>
				<view class="card-top">
					<view class="mood-badge">
						<text class="mood-emoji">{{ moodEmoji(item.mood) }}</text>
						<text class="mood-label">{{ item.mood || '记录' }}</text>
					</view>
					<text class="note-date">{{ formatDate(item.noteDate) }}</text>
				</view>
				<text class="note-excerpt">{{ item.excerpt || '（无内容）' }}</text>
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
		// 从本地列表中移除
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
	border-radius: 50%;
	background: linear-gradient(135deg, #2D9B8F, #5BC4A8);
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: 0 8rpx 28rpx rgba(45, 155, 143, 0.3);
	z-index: 100;
	transition: transform 0.2s ease;
}

.fab:active {
	transform: scale(0.92);
}

/* ── 骨架屏 ── */
.skeleton-card {
	overflow: hidden;
	border-radius: 24rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	margin-bottom: 24rpx;
}

.skeleton-body {
	padding: 28rpx;
}

.skeleton-line {
	height: 28rpx;
	margin-bottom: 16rpx;
	border-radius: 8rpx;
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

.empty-emoji {
	font-size: 80rpx;
}

.status-text {
	font-size: 30rpx;
	color: var(--zy-aid, #9CA3AF);
}

.status-sub {
	font-size: 26rpx;
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

.note-card {
	padding: 28rpx 32rpx;
	border-radius: 24rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: 0 4rpx 20rpx rgba(31, 41, 55, 0.05);
	transition: transform 0.15s ease, box-shadow 0.15s ease;
	animation: cardEnter 0.35s ease both;
	animation-delay: calc(var(--stagger-i, 0) * 60ms);
}

.note-card:active {
	transform: scale(0.98);
	box-shadow: 0 2rpx 8rpx rgba(31, 41, 55, 0.08);
}

@keyframes cardEnter {
	from { opacity: 0; transform: translateY(20rpx); }
	to { opacity: 1; transform: translateY(0); }
}

.card-top {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 16rpx;
}

.mood-badge {
	display: flex;
	align-items: center;
	gap: 10rpx;
	padding: 6rpx 20rpx;
	border-radius: 999rpx;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.mood-emoji {
	font-size: 32rpx;
	line-height: 1;
}

.mood-label {
	font-size: 24rpx;
	color: #2D9B8F;
	font-weight: 500;
}

.note-date {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
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
