<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
		<!-- 展签页头 -->
		<view class="page-head zy-page-enter">
			<text class="page-eyebrow">MOOD NOTES · 此刻</text>
			<view class="page-head-row">
				<view class="page-head-copy">
					<text class="page-title">写下此刻</text>
					<text class="page-subtitle">不必解释，也不必写好。只留一句话给自己。</text>
				</view>
				<AccessibleButton class="create-btn" aria-label="新建心情便签" @click="goCreate">写一张</AccessibleButton>
			</view>
		</view>

		<!-- 加载状态 -->
		<StateBlock v-if="loading && records.length === 0" type="loading" shape="list" title="正在读取心情便签…" />

		<!-- 空状态 -->
		<StateBlock
			v-else-if="!loading && records.length === 0 && !errorMsg"
			type="empty"
			art="notes"
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
			<text class="list-label">最近记录</text>
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
					<!-- 左侧心情字印：冷瓷展签式单字，替代彩色脸谱 -->
					<view class="mood-circle" aria-hidden="true">
						<text class="mood-mark">{{ moodMark(item.mood) }}</text>
					</view>
					<!-- 中间内容 -->
					<view class="card-center">
						<text class="note-excerpt">{{ item.excerpt || '（无内容）' }}</text>
						<view class="note-meta">
							<text class="note-date">{{ formatDate(item.noteDate) }}</text>
							<text v-if="item.mood" class="mood-tag-text">{{ moodLabel(item.mood) }}</text>
						</view>
					</view>
					<!-- 右侧箭头 -->
						<ZyIcon name="arrow" :size="24" tone="muted" />
				</view>
			</view>

			<!-- 底部状态 -->
			<view v-if="records.length > 0" class="bottom-status">
				<ZySpinner v-if="loading" :size="28" />
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
import ZySpinner from '../../components/ZySpinner.vue'
import ActionSheet from '../../components/ActionSheet.vue'
import Toast from '../../components/Toast.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ZyIcon from '../../components/ZyIcon.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

/* 后端 mood 存英文枚举 key，展示一律映射为中文（MOOD_MAP 已兼容中文 key，二者并存时以中文为准） */
const MOOD_LABELS = {
	'happy': '开心',
	'calm': '平静',
	'neutral': '一般',
	'tired': '有点累',
	'down': '不太好',
}

const MOOD_MARK = {
	'happy': '喜', '开心': '喜',
	'calm': '静', '平静': '静',
	'neutral': '平', '一般': '平',
	'tired': '倦', '有点累': '倦',
	'down': '低', '不太好': '低'
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

function moodLabel(mood) {
	if (!mood) return ''
	return MOOD_LABELS[mood] || mood
}
function moodMark(mood) {
	if (!mood) return '记'
	return MOOD_MARK[mood] || '记'
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
	padding: 36rpx 32rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #f1f4f1);
}

/* ── 展签页头 ── */
.page-head {
	padding-bottom: 32rpx;
	margin-bottom: 8rpx;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
}
.page-eyebrow {
	display: block;
	font-size: var(--zy-text-micro, 22rpx);
	letter-spacing: 2px;
	color: var(--zy-muted, #6e7b75);
	margin-bottom: 20rpx;
}
.page-head-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 24rpx; }
.page-head-copy { min-width: 0; }
.page-title {
	display: block;
	font-family: var(--zy-font-display, serif);
	font-size: 64rpx;
	font-weight: 600;
	line-height: 1.2;
	color: var(--zy-title, #111816);
}
.list-label {
	display: block;
	font-size: var(--zy-text-micro, 22rpx);
	letter-spacing: 2px;
	color: var(--zy-muted);
	margin-bottom: 4rpx;
}
.page-subtitle {
	display: block;
	margin-top: 10rpx;
	font-size: var(--zy-text-caption, 24rpx);
	color: var(--zy-muted, #6e7b75);
}
.create-btn {
	flex-shrink: 0;
	min-height: 72rpx;
	margin: 0;
	padding: 0 24rpx;
	border: 1rpx solid var(--zy-brand-700, #0a403b);
	border-radius: 0;
	background: transparent;
	color: var(--zy-brand-700, #0a403b);
	font-size: var(--zy-text-caption, 24rpx);
	line-height: 72rpx;
	letter-spacing: 1rpx;
}
.create-btn::after { border: 0; }

/* ── 焦点 ── */
.note-card:focus-visible,
.create-btn:focus-visible {
	outline: 3px solid var(--zy-brand-700, #0a403b);
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
	font-size:var(--zy-text-display, 56rpx);
}

.empty-title {
	font-size:var(--zy-text-body-lg, 30rpx);
	font-weight: 500;
	color: var(--zy-title, #233d34);
}

.empty-sub {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-text, #5c5648);
}

.empty-btn {
	margin-top: 16rpx;
	padding: 16rpx 40rpx;
	border-radius: 0;
	background: var(--zy-brand-600, #0e5b53);
	box-shadow: none;
}

.empty-btn-text {
	font-size:var(--zy-text-body, 28rpx);
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
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-care, var(--zy-care, #a64842));
}

/* ── 便签列表：时间轴式行 ── */
.card-list {
	display: flex;
	flex-direction: column;
	gap: 0;
	border-top: 1rpx solid var(--zy-border, #d3ded8);
}

.note-card {
	padding: 28rpx 0;
	border-radius: 0;
	background-color: transparent;
	border: 0;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
	box-shadow: none;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.note-card:active {
	background-color: var(--zy-bg-sunken, #e9efeb);
	box-shadow: none;
}

.card-main {
	display: flex;
	align-items: flex-start;
	gap: 20rpx;
}

/* ── 心情方形底 ── */
.mood-circle {
	width: 72rpx;
	height: 72rpx;
	border-radius: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
	border: 1rpx solid var(--zy-border, #d3ded8);
	background: transparent;
	overflow: hidden;
}
.mood-mark {
	font-family: var(--zy-font-display, "Songti SC", serif);
	font-size: 34rpx;
	font-weight: 500;
	color: var(--zy-brand-700, #0a403b);
	line-height: 1;
}

/* ── 中间内容 ── */
.card-center {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 10rpx;
}

.note-excerpt {
	display: -webkit-box;
	font-family: var(--zy-font-display, serif);
	font-size: 34rpx;
	line-height: 1.6;
	color: var(--zy-title, #111816);
	overflow: hidden;
	text-overflow: ellipsis;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.note-meta {
	display: flex;
	align-items: center;
	gap: 14rpx;
	font-size: var(--zy-text-micro, 22rpx);
	color: var(--zy-muted, #6e7b75);
}

.note-date { color: var(--zy-muted, #6e7b75); font-variant-numeric: tabular-nums; }

/* ── 心情标签：不再用胶囊 ── */
.mood-tag-text {
	padding-left: 14rpx;
	border-left: 1rpx solid var(--zy-border, #d3ded8);
	color: var(--zy-accent, #8e4a3c);
	font-size: var(--zy-text-micro, 22rpx);
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
	border-top-color: var(--zy-primary, var(--zy-brand-600, #217e76)));
	animation: spin 0.8s linear infinite;
}

@keyframes spin {
	to { transform: rotate(360deg); }
}

.bottom-text {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-text, #5c5648);
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-text, #5c5648);
	text-align: center;
}
</style>
