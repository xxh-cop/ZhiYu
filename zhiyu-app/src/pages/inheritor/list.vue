<template>
	<view class="page zy-page-enter">
		<!-- 顶部标题 -->
		<view class="header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="page-title">非遗传承人</text>
		</view>

		<!-- 搜索框 -->
		<view class="search-bar zy-slide-up zy-stagger" style="--stagger-i: 1">
			<view class="search-input-wrap">
				<wd-icon name="search" size="32rpx" color="#9CA3AF" />
				<input
					class="search-input"
					type="text"
					placeholder="搜索技艺或昵称"
					placeholder-class="search-placeholder"
					:value="keyword"
					@confirm="onSearch"
					@input="onKeywordInput"
					confirm-type="search"
				/>
			</view>
			<text class="search-btn" @click="onSearch">搜索</text>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading && records.length === 0" class="card-list">
			<view v-for="i in 3" :key="'sk-'+i" class="skeleton-card">
				<view class="skeleton-avatar zy-skeleton"></view>
				<view class="skeleton-body">
					<view class="skeleton-name zy-skeleton"></view>
					<view class="skeleton-skill zy-skeleton"></view>
					<view class="skeleton-intro zy-skeleton"></view>
				</view>
			</view>
		</view>

		<!-- 空状态 -->
		<view v-else-if="!loading && records.length === 0 && !errorMsg" class="status-box">
			<text class="empty-text">暂无传承人</text>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg && records.length === 0" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<wd-button type="primary" plain size="small" @click="loadData(1)">重试</wd-button>
		</view>

		<!-- 传承人卡片列表 -->
		<view v-else class="card-list">
			<view
				v-for="(item, idx) in records"
				:key="item.id"
				class="inheritor-card zy-slide-up zy-stagger"
				:style="{ '--stagger-i': idx + 2 }"
				@click="goDetail(item)"
			>
				<!-- 头像 -->
				<view class="card-avatar" :style="avatarStyle(item)">
					<text v-if="!item.avatarUrl" class="avatar-letter">{{ firstLetter(item.nickname) }}</text>
				</view>

				<view class="card-body">
					<view class="card-header-row">
						<text class="card-name">{{ item.nickname || '未知' }}</text>
						<view v-if="item.skill" class="skill-badge">
							<text class="skill-text">{{ item.skill }}</text>
						</view>
					</view>

					<text v-if="item.honor" class="card-honor">{{ item.honor }}</text>

					<text v-if="item.intro" class="card-intro">{{ item.intro }}</text>
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
import { onReachBottom } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

const loading = ref(false)
const errorMsg = ref('')
const records = ref([])
const page = ref(1)
const total = ref(0)
const size = 10
const keyword = ref('')

const hasMore = computed(() => page.value * size < total.value)

onReachBottom(() => {
	if (hasMore.value && !loading.value) {
		page.value++
		loadMore()
	}
})

function onKeywordInput(e) {
	keyword.value = e.detail.value
}

function onSearch() {
	loadData(1)
}

async function loadData(pageNum) {
	loading.value = true
	errorMsg.value = ''
	if (pageNum === 1) {
		records.value = []
	}
	page.value = pageNum

	try {
		const params = { page: pageNum, pageSize: size }
		if (keyword.value) {
			params.keyword = keyword.value
		}
		const data = await http.get('/api/inheritors', { data: params })
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
		const params = { page: page.value, pageSize: size }
		if (keyword.value) {
			params.keyword = keyword.value
		}
		const data = await http.get('/api/inheritors', { data: params })
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

function firstLetter(name) {
	if (!name) return '?'
	return name.charAt(0)
}

function avatarStyle(item) {
	if (item.avatarUrl) {
		// #ifdef H5
		return `background-image: url(${item.avatarUrl}); background-size: cover; background-position: center;`
		// #endif
	}
	return 'background: linear-gradient(135deg, #2D9B8F, #5BCFBF);'
}

function goDetail(item) {
	uni.navigateTo({
		url: `/pages/inheritor/detail?id=${item.id}`,
	})
}

// 初始加载
loadData(1)
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 顶部标题 ── */
.header {
	margin-bottom: 24rpx;
}

.page-title {
	font-size: 44rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
}

/* ── 搜索框 ── */
.search-bar {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin-bottom: 28rpx;
}

.search-input-wrap {
	flex: 1;
	display: flex;
	align-items: center;
	gap: 12rpx;
	padding: 16rpx 24rpx;
	border-radius: 999rpx;
	background-color: #FFFFFF;
	border: 1px solid var(--zy-border, #E5E7EB);
}

.search-input {
	flex: 1;
	font-size: 28rpx;
	color: var(--zy-title, #1F2937);
}

.search-placeholder {
	color: var(--zy-aid, #9CA3AF);
}

.search-btn {
	font-size: 28rpx;
	color: var(--wot-color-theme, #2D9B8F);
	white-space: nowrap;
}

/* ── 骨架屏 ── */
.skeleton-card {
	display: flex;
	gap: 24rpx;
	padding: 28rpx;
	border-radius: 16px;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
}

.skeleton-avatar {
	width: 96rpx;
	height: 96rpx;
	border-radius: 50%;
	flex-shrink: 0;
}

.skeleton-body {
	flex: 1;
	display: flex;
	flex-direction: column;
	gap: 12rpx;
	padding-top: 8rpx;
}

.skeleton-name {
	height: 32rpx;
	width: 40%;
}

.skeleton-skill {
	height: 28rpx;
	width: 30%;
}

.skeleton-intro {
	height: 28rpx;
	width: 80%;
}

/* ── 状态区 ── */
.status-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 120rpx 0;
	gap: 24rpx;
}

.empty-text {
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

.inheritor-card {
	display: flex;
	gap: 24rpx;
	padding: 28rpx;
	border-radius: 16px;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1px solid var(--zy-border, #E5E7EB);
	box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
	transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.inheritor-card:active {
	transform: scale(0.98);
	box-shadow: 0 4rpx 16rpx rgba(45, 155, 143, 0.1);
}

/* ── 头像 ── */
.card-avatar {
	width: 96rpx;
	height: 96rpx;
	border-radius: 50%;
	flex-shrink: 0;
	display: flex;
	align-items: center;
	justify-content: center;
}

.avatar-letter {
	font-size: 40rpx;
	font-weight: 600;
	color: #FFFFFF;
}

/* ── 卡片内容 ── */
.card-body {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}

.card-header-row {
	display: flex;
	align-items: center;
	gap: 16rpx;
	flex-wrap: wrap;
}

.card-name {
	font-size: 32rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.skill-badge {
	padding: 4rpx 16rpx;
	border-radius: 999rpx;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.skill-text {
	font-size: 22rpx;
	color: var(--wot-color-theme, #2D9B8F);
}

.card-honor {
	margin-top: 8rpx;
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.card-intro {
	display: -webkit-box;
	margin-top: 8rpx;
	font-size: 26rpx;
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
