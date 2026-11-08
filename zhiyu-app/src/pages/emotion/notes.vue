<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
		<!-- 悬浮新建按钮 -->
		<view class="fab" role="button" tabindex="0" aria-label="新建心情便签" @click="goCreate" @keydown.enter.prevent="goCreate" @keydown.space.prevent="goCreate">
			<wd-icon name="add" size="44rpx" color="#FFFFFF" />
		</view>

		<!-- 加载状态 -->
		<StateBlock v-if="loading && records.length === 0" type="loading" title="正在读取心情便签…" />

		<!-- 空状态 -->
		<StateBlock
			v-else-if="!loading && records.length === 0 && !errorMsg"
			type="empty"
			title="还没有心情便签"
			description="写下此刻的心情，就从第一句开始"
			action-text="记录第一条"
			@action="goCreate"
		/>

		<!-- 错误状态 -->
		<StateBlock
			v-else-if="errorMsg && records.length === 0"
			type="error"
			title="便签暂时没有加载出来"
			:description="errorMsg"
			action-text="重试"
			@action="loadData(1)"
		/>

		<!-- 便签卡片列表 -->
		<view v-else class="card-list">
			<view
				v-for="(item, idx) in records"
				:key="item.id"
				class="note-card zy-slide-up zy-stagger"
				:style="{ '--stagger-i': idx }"
				role="button"
				tabindex="0"
				@click="goEdit(item)"
				@keydown.enter.prevent="goEdit(item)"
				@keydown.space.prevent="goEdit(item)"
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
						<text class="mood-tag-text">{{ moodLabel(item.mood) }}</text>
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

		<ActionSheet
			:open="!!pendingDelete"
			title="移除便签"
			description="确定要移除这条心情便签吗？"
			:actions="[{ key: 'confirm', label: '确认移除', tone: 'danger', busy: deleting }]"
			cancel-text="再想想"
			:busy="deleting"
			@select="confirmDelete"
			@close="pendingDelete = null"
		/>
		<Toast />
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import StateBlock from '../../components/StateBlock.vue'
import ActionSheet from '../../components/ActionSheet.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

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

/* 后端 mood 存英文枚举 key，展示一律映射为中文（MOOD_MAP 已兼容中文 key，二者并存时以中文为准） */
const MOOD_LABELS = {
	'happy': '开心',
	'calm': '平静',
	'neutral': '一般',
	'tired': '有点累',
	'down': '不太好',
}

const MOOD_GRADIENTS = {
	'开心': 'linear-gradient(135deg, var(--zy-accent, #8a6d1f), #FFD08A)',
	'平静': 'linear-gradient(135deg, #217e76, var(--zy-brand-300, #5bc4a8))',
	'一般': 'linear-gradient(135deg, #6B7280, var(--zy-text, #4a5f54))',
	'有点累': 'linear-gradient(135deg, var(--zy-brand-300, #5bc4a8), #8adcc6)',
	'不太好': 'linear-gradient(135deg, var(--zy-care, #a64842), #d4736a)',
	'happy': 'linear-gradient(135deg, var(--zy-accent, #8a6d1f), #FFD08A)',
	'calm': 'linear-gradient(135deg, #217e76, var(--zy-brand-300, #5bc4a8))',
	'neutral': 'linear-gradient(135deg, #6B7280, var(--zy-text, #4a5f54))',
	'tired': 'linear-gradient(135deg, var(--zy-brand-300, #5bc4a8), #8adcc6)',
	'down': 'linear-gradient(135deg, var(--zy-care, #a64842), #d4736a)',
}

const loading = ref(false)
const errorMsg = ref('')
const records = ref([])
const page = ref(1)
const total = ref(0)
const pageSize = 10
const pendingDelete = ref(null)
const deleting = ref(false)

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

function moodLabel(mood) {
	if (!mood) return ''
	return MOOD_LABELS[mood] || mood
}

function moodGradient(mood) {
	return MOOD_GRADIENTS[mood] || 'linear-gradient(135deg, #217e76, var(--zy-brand-300, #5bc4a8))'
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
				askDelete(item)
			}
		},
	})
}

function askDelete(item) {
	pendingDelete.value = item
}

async function confirmDelete() {
	const item = pendingDelete.value
	if (!item || deleting.value) return
	deleting.value = true
	try {
		await http.del('/api/emotion/notes/' + item.id)
		toast.success('已移除')
		records.value = records.value.filter((r) => r.id !== item.id)
		total.value = Math.max(0, total.value - 1)
		pendingDelete.value = null
	} catch (e) {
		toast.care((e && e.message) || '操作失败')
	} finally {
		deleting.value = false
	}
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #ffffff);
}

/* ── 悬浮按钮 ── */
.fab {
	position: fixed;
	right: 40rpx;
	bottom: 160rpx;
	width: 100rpx;
	height: 100rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #217e76), var(--zy-primary-light, #5bc4a8));
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

.fab:focus-visible,
.note-card:focus-visible {
	outline: 3px solid var(--zy-brand-700, #1a645e);
	outline-offset: 3px;
}

/* ── 骨架屏 ── */
.skeleton-card {
	overflow: hidden;
	border-radius: var(--zy-radius-md, 24rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
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
	background-color: var(--zy-bg-tint-mint, #e8efe4);
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
	color: var(--zy-title, #233d34);
}

.empty-sub {
	font-size: 26rpx;
	color: var(--zy-text, #4a5f54);
}

.empty-btn {
	margin-top: 16rpx;
	padding: 16rpx 48rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-brand-600, #217e76);
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
	color: var(--zy-care, #a64842);
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
	border: 1rpx solid var(--zy-border, #dfe7dc);
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
	color: var(--zy-content, #4a5f54);
	overflow: hidden;
	text-overflow: ellipsis;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.note-date {
	font-size: 22rpx;
	color: var(--zy-text, #4a5f54);
}

/* ── 底部心情标签 ── */
.mood-tag-row {
	margin-top: 16rpx;
	padding-top: 16rpx;
	margin-bottom: 8rpx;
	border-top: 1rpx solid var(--zy-border, #dfe7dc);
}

.mood-tag {
	display: inline-flex;
	padding: 4rpx 16rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.mood-tag-text {
	font-size: 22rpx;
	color: var(--zy-brand-700, #217e76);
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
	color: var(--zy-text, #4a5f54);
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 22rpx;
	color: var(--zy-text, #4a5f54);
	text-align: center;
}
</style>
