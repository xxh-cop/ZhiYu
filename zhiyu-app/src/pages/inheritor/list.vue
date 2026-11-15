<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
		<!-- 顶部标题 -->
		<view class="header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="page-title">非遗传承人</text>
		</view>

		<!-- 手艺人的故事 -->
		<view v-if="storyLoading || storyList.length || storyError" class="story-section zy-slide-up zy-stagger" style="--stagger-i: 1">
			<view class="story-head">
				<view>
					<text class="story-eyebrow">手艺人的故事</text>
					<text class="story-note">合成教学叙事 · 非真实访谈</text>
				</view>
			</view>
			<view v-if="storyLoading" class="story-skeleton zy-skeleton" aria-hidden="true"></view>
			<view v-else-if="storyError" class="story-error" role="alert">{{ storyError }}</view>
			<scroll-view v-else scroll-x class="story-scroll" :show-scrollbar="false">
				<view v-for="(story, idx) in storyList" :key="story.id" class="story-cell">
					<HeritageCard
						class="story-card"
						family="stitch"
						:title="story.title"
						:meta="story.traditionName + ' · ' + story.readingMinutes + ' 分钟'"
						:tag="'0' + (idx + 1)"
						:aria-label="'读故事 ' + story.title"
						@open="goStory(story)"
					>
						<text class="story-synopsis">{{ story.synopsis }}</text>
						<template #footer>
							<text class="story-link">读这个故事 →</text>
						</template>
					</HeritageCard>
				</view>
			</scroll-view>
			<text class="story-boundary">故事用于理解材料与工艺，不构成传承人履历、资质或口述史。</text>
		</view>

		<!-- 搜索框 -->
		<view class="search-bar zy-slide-up zy-stagger" style="--stagger-i: 2">
			<view class="search-input-wrap" :class="{ 'is-focused': searchFocused }">
				<wd-icon name="search" size="32rpx" color="var(--zy-text, #4a5f54)" />
				<input
					class="search-input"
					type="text"
					placeholder="搜索技艺或昵称"
					placeholder-class="search-placeholder"
					:value="keyword"
					@confirm="onSearch"
					@input="onKeywordInput"
					@focus="searchFocused = true"
					@blur="searchFocused = false"
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
			<view class="empty-icon-wrap">
				<ZyIcon name="search" :size="56" color="#A79B87" class="empty-emoji" />
			</view>
			<text class="empty-text">暂无传承人</text>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg && records.length === 0" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<wd-button type="primary" plain size="small" @click="loadData(1)">重试</wd-button>
		</view>

		<!-- 传承人卡片列表 -->
		<view v-else class="card-list">
			<HeritageCard
				v-for="(item, idx) in records"
				:key="item.id"
				class="inheritor-card zy-slide-up zy-stagger"
				:style="{ '--stagger-i': idx + 2 }"
				:family="inheritorFamily(item)"
				:title="item.nickname || '未知'"
				:tag="item.skill || ''"
				:meta="item.honor || ''"
				:note="item.intro || ''"
				:aria-label="inheritorLabel(item)"
				@open="goDetail(item)"
			>
				<template #cover>
					<view class="inheritor-cover">
						<view class="avatar-ring">
							<view class="card-avatar" :style="avatarStyle(item)">
								<text v-if="!item.avatarUrl" class="avatar-letter">{{ firstLetter(item.nickname) }}</text>
							</view>
						</view>
					</view>
				</template>
				<template #footer>
					<view class="card-footer">
						<view class="footer-left">
							<RegionSeal v-if="item.region" :region="item.region" size="sm" />
							<view v-if="item.verified" class="verified-badge">
								<wd-icon name="check-circle-filled" size="24rpx" color="#217e76" />
								<text class="verified-text">已认证</text>
							</view>
						</view>
						<text class="project-count-text">{{ item.projectCount || 0 }} 个手作项目</text>
					</view>
				</template>
			</HeritageCard>

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
import { onReachBottom } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import HeritageCard from '../../components/HeritageCard.vue'
import RegionSeal from '../../components/RegionSeal.vue'
import ZyIcon from '../../components/ZyIcon.vue'

const loading = ref(false)
const errorMsg = ref('')
const records = ref([])
const page = ref(1)
const total = ref(0)
const size = 10
const keyword = ref('')
const searchFocused = ref(false)
const storyList = ref([])
const storyLoading = ref(false)
const storyError = ref('')

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

/* 传承人接口暂未下发 family / region；工艺族按技艺关键词降级为材质带，region 有值才渲染印章 */
function inheritorFamily(item) {
	const text = `${(item && item.skill) || ''}`
	if (/纸|剪/.test(text)) return 'cutout'
	if (/竹|编|藤/.test(text)) return 'weave'
	if (/布|绣|织|线/.test(text)) return 'stitch'
	if (/陶|瓷|釉/.test(text)) return 'porcelain'
	return 'weave'
}

function inheritorLabel(item) {
	const parts = ['查看传承人', (item && item.nickname) || '未知']
	if (item && item.skill) parts.push(item.skill)
	if (item && item.honor) parts.push(item.honor)
	parts.push(`${(item && item.projectCount) || 0} 个手作项目`)
	return parts.join('，')
}

function avatarStyle(item) {
	if (item.avatarUrl) {
		// #ifdef H5
		return `background-image: url(${item.avatarUrl}); background-size: cover; background-position: center;`
		// #endif
	}
	return 'background: linear-gradient(135deg, #217e76, var(--zy-brand-300, #5bc4a8));'
}

function goDetail(item) {
	uni.navigateTo({
		url: `/pages/inheritor/detail?id=${item.id}`,
	})
}

async function loadStories() {
	storyLoading.value = true
	storyError.value = ''
	try {
		const data = await http.get('/api/inheritors/stories')
		storyList.value = data || []
	} catch (e) {
		storyList.value = []
		storyError.value = (e && e.message) || '故事暂时无法读取'
	} finally {
		storyLoading.value = false
	}
}

function goStory(story) {
	uni.navigateTo({
		url: `/pages/inheritor/story?id=${encodeURIComponent(story.id)}`,
	})
}

// 初始加载
loadData(1)
loadStories()
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #ffffff);
}

/* ── 顶部标题 ── */
.header {
	margin-bottom: 24rpx;
}

.page-title {
	font-size:var(--zy-text-title-lg, 40rpx);
	font-weight: 500;
	color: var(--zy-title, #233d34);
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
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1px solid var(--zy-border, #dfe7dc);
	transition: border-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.search-input-wrap.is-focused {
	border-color: var(--zy-primary, var(--zy-brand-600, #217e76));
}

.search-input {
	flex: 1;
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-title, #233d34);
}

.search-placeholder {
	color: var(--zy-aid, #4a5f54);
}

.search-btn {
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
	white-space: nowrap;
}

/* ── 骨架屏 ── */
.skeleton-card {
	display: flex;
	gap: 24rpx;
	padding: 28rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
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

.empty-icon-wrap {
	width: 120rpx;
	height: 120rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	display: flex;
	align-items: center;
	justify-content: center;
}

.empty-emoji {
	font-size:var(--zy-text-display, 56rpx);
}

.empty-text {
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-aid, #4a5f54);
}

.error-text {
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-danger, var(--zy-care, #a64842));
}

/* ── 卡片列表 ── */
.card-list {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
}

.inheritor-card {
	overflow: hidden;
}

.inheritor-card:active {
	transform: scale(0.98);
}

/* cover slot：保留原头像的渐变环与首字兜底 */
.inheritor-cover {
	height: 100%;
	display: flex;
	align-items: center;
	justify-content: center;
	background: linear-gradient(145deg, var(--zy-card-bg, #fff), var(--zy-bg-tint-mint, #e8efe4));
}

/* ── 头像 ── */
.avatar-ring {
	width: 132rpx;
	height: 132rpx;
	border-radius: 50%;
	padding: 4rpx;
	background: linear-gradient(135deg, var(--zy-primary, var(--zy-brand-600, #217e76)), var(--zy-primary-light, var(--zy-brand-300, #5bc4a8)));
	flex-shrink: 0;
}

.card-avatar {
	width: 100%;
	height: 100%;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	border: 3rpx solid var(--zy-card-bg, #FFFFFF);
	box-sizing: border-box;
}

.avatar-letter {
	font-size:var(--zy-text-title-lg, 40rpx);
	font-weight: 600;
	color: #FFFFFF;
}

/* ── 卡片底部 ── */
.card-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx;
}

.footer-left {
	display: flex;
	align-items: center;
	gap: 12rpx;
	min-width: 0;
}

.verified-badge {
	display: flex;
	align-items: center;
	gap: 6rpx;
}

.verified-text {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
}

.project-count-text {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-aid, #4a5f54);
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
	border-top-color: var(--zy-primary, var(--zy-brand-600, #217e76));
	animation: spin 0.8s linear infinite;
}

@keyframes spin {
	to { transform: rotate(360deg); }
}

.bottom-text {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-aid, #4a5f54);
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-aid, #4a5f54);
	text-align: center;
}
</style>

<style scoped>
.story-section { margin-bottom: 26rpx; }
.story-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 12rpx; margin-bottom: 14rpx; }
.story-eyebrow { display: block; font-family: var(--zy-serif); font-size:var(--zy-text-body-lg, 30rpx); font-weight: 600; color: var(--zy-title, #233d34); }
.story-note { display: block; margin-top: 6rpx; font-size:var(--zy-text-micro, 22rpx); letter-spacing: 1rpx; color: var(--zy-text, #4a5f54); }
.story-skeleton { height: 260rpx; border-radius: 20rpx; }
.story-error { padding: 22rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 16rpx; background: var(--zy-card-bg, #fff); font-size:var(--zy-text-micro, 22rpx); line-height: 1.7; color: var(--zy-content, #4a5f54); }
.story-scroll { width: 100%; white-space: nowrap; }
.story-cell { display: inline-block; width: 320rpx; margin-right: 16rpx; vertical-align: top; white-space: normal; }
.story-cell:last-child { margin-right: 0; }
.story-card { height: 100%; }
.story-synopsis { display: -webkit-box; overflow: hidden; margin-top: 12rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.7; color: var(--zy-content, #4a5f54); -webkit-line-clamp: 3; -webkit-box-orient: vertical; }
.story-link { display: block; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-brand-700, var(--zy-brand-600, #217e76)); }
.story-boundary { display: block; margin-top: 12rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.7; color: var(--zy-text, #4a5f54); }
@media (prefers-reduced-motion: reduce) {
  .story-card { transition: none; }
}
</style>
