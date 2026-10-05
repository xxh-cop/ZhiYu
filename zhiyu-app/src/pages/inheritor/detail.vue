<template>
	<view class="page zy-page-enter">
		<!-- 骨架屏 -->
		<view v-if="loading && !inheritor" class="skeleton-wrap">
			<view class="skeleton-avatar zy-skeleton"></view>
			<view class="skeleton-name zy-skeleton"></view>
			<view class="skeleton-skill zy-skeleton"></view>
			<view class="skeleton-intro zy-skeleton"></view>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg && !inheritor" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<wd-button type="primary" plain size="small" @click="loadInheritor">重试</wd-button>
		</view>

		<!-- 传承人资料 + 项目列表 -->
		<view v-else-if="inheritor">
			<!-- ═══ 传承人资料区 ═══ -->
			<view class="profile-section" style="--stagger-i: 0">
				<view class="avatar-large" :style="avatarStyle(inheritor)">
					<text v-if="!inheritor.avatarUrl" class="avatar-letter">{{ firstLetter(inheritor.nickname) }}</text>
				</view>
				<text class="profile-name">{{ inheritor.nickname || '未知' }}</text>
				<view class="profile-tags">
					<view v-if="inheritor.skill" class="skill-badge">
						<text class="skill-text">{{ inheritor.skill }}</text>
					</view>
				</view>
				<text v-if="inheritor.honor" class="profile-honor">{{ inheritor.honor }}</text>
				<text v-if="inheritor.school" class="profile-school">{{ inheritor.school }}</text>
				<text v-if="inheritor.intro" class="profile-intro">{{ inheritor.intro }}</text>
			</view>

			<!-- ═══ TA 的手作 ═══ -->
			<view class="projects-section" style="--stagger-i: 1">
				<text class="section-title">TA 的手作</text>

				<!-- 项目加载中 -->
				<view v-if="projectLoading && projects.length === 0" class="card-list">
					<view v-for="i in 2" :key="'psk-'+i" class="skeleton-project-card">
						<view class="skeleton-cover zy-skeleton"></view>
						<view class="skeleton-project-body">
							<view class="skeleton-title zy-skeleton"></view>
							<view class="skeleton-text zy-skeleton"></view>
						</view>
					</view>
				</view>

				<!-- 空状态 -->
				<view v-else-if="!projectLoading && projects.length === 0" class="empty-projects">
					<text class="empty-text">暂无已上架作品</text>
				</view>

				<!-- 项目卡片列表 -->
				<view v-else class="card-list">
					<view
						v-for="(item, idx) in projects"
						:key="item.id"
						class="project-card"
						:style="{ '--stagger-i': idx + 2 }"
						@click="goProjectDetail(item)"
					>
						<!-- 封面图 -->
						<view class="card-cover" :style="coverStyle(item.coverUrl)">
							<view class="level-badge">
								<text class="level-text">{{ levelLabel(item.level) }}</text>
							</view>
						</view>

						<view class="card-body">
							<text class="card-title">{{ item.title }}</text>
							<text v-if="item.intro" class="card-intro">{{ item.intro }}</text>

							<view class="card-tags">
								<view v-if="item.category" class="category-badge">
									<text class="category-text">{{ item.category }}</text>
								</view>
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
						</view>
					</view>

					<!-- 底部状态 -->
					<view v-if="projects.length > 0" class="bottom-status">
						<view v-if="projectLoading" class="loading-dot"></view>
						<text v-if="projectLoading" class="bottom-text">正在加载…</text>
						<text v-else-if="!hasMore" class="bottom-text">没有更多了</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

const inheritorId = ref(null)
const inheritor = ref(null)
const loading = ref(false)
const errorMsg = ref('')

const projects = ref([])
const projectLoading = ref(false)
const page = ref(1)
const total = ref(0)
const size = 10

const hasMore = computed(() => page.value * size < total.value)

onLoad((query) => {
	inheritorId.value = query.id
	loadInheritor()
	loadProjects(1)
})

onReachBottom(() => {
	if (hasMore.value && !projectLoading.value) {
		page.value++
		loadMoreProjects()
	}
})

async function loadInheritor() {
	loading.value = true
	errorMsg.value = ''
	try {
		const data = await http.get(`/api/inheritors/${inheritorId.value}`)
		inheritor.value = data
	} catch (e) {
		errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
	} finally {
		loading.value = false
	}
}

async function loadProjects(pageNum) {
	projectLoading.value = true
	if (pageNum === 1) {
		projects.value = []
	}
	page.value = pageNum

	try {
		const data = await http.get(`/api/inheritors/${inheritorId.value}/projects`, {
			data: { page: pageNum, pageSize: size },
		})
		if (data) {
			total.value = Number(data.total) || 0
			projects.value = data.records || []
		}
	} catch (e) {
		// 项目列表加载失败不阻塞页面
	} finally {
		projectLoading.value = false
	}
}

async function loadMoreProjects() {
	projectLoading.value = true
	try {
		const data = await http.get(`/api/inheritors/${inheritorId.value}/projects`, {
			data: { page: page.value, pageSize: size },
		})
		if (data) {
			total.value = Number(data.total) || 0
			projects.value = projects.value.concat(data.records || [])
		}
	} catch (e) {
	} finally {
		projectLoading.value = false
	}
}

function firstLetter(name) {
	if (!name) return '?'
	return name.charAt(0)
}

function avatarStyle(item) {
	if (item && item.avatarUrl) {
		// #ifdef H5
		return `background-image: url(${item.avatarUrl}); background-size: cover; background-position: center;`
		// #endif
	}
	return 'background: linear-gradient(135deg, #2D9B8F, #5BCFBF);'
}

function coverStyle(coverUrl) {
	// #ifdef H5
	if (coverUrl) {
		return `background-image: url(${coverUrl}); background-size: cover; background-position: center;`
	}
	// #endif
	return 'background: linear-gradient(135deg, #2D9B8F, #5BCFBF);'
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

function goProjectDetail(item) {
	uni.navigateTo({
		url: `/pages/craft/detail?id=${item.id}`,
	})
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 错峰入场 ── */
.profile-section,
.projects-section,
.project-card {
	animation: staggerIn 0.5s cubic-bezier(0.22, 0.61, 0.36, 1) backwards;
	animation-delay: calc(var(--stagger-i, 0) * 100ms);
}

@keyframes staggerIn {
	from { opacity: 0; transform: translateY(24rpx); }
	to { opacity: 1; transform: translateY(0); }
}

/* ── 骨架屏 ── */
.skeleton-wrap {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 80rpx 0;
	gap: 24rpx;
}

.skeleton-avatar {
	width: 144rpx;
	height: 144rpx;
	border-radius: 50%;
}

.skeleton-name {
	height: 40rpx;
	width: 200rpx;
}

.skeleton-skill {
	height: 32rpx;
	width: 120rpx;
}

.skeleton-intro {
	height: 32rpx;
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

.error-text {
	font-size: 28rpx;
	color: var(--zy-danger, #FF6B6B);
}

/* ── 传承人资料区 ── */
.profile-section {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 40rpx 32rpx;
	margin-bottom: 32rpx;
	border-radius: 16px;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1px solid var(--zy-border, #E5E7EB);
	box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.avatar-large {
	width: 144rpx;
	height: 144rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 24rpx;
}

.avatar-letter {
	font-size: 56rpx;
	font-weight: 600;
	color: #FFFFFF;
}

.profile-name {
	font-size: 40rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
}

.profile-tags {
	display: flex;
	gap: 12rpx;
	margin-top: 16rpx;
	flex-wrap: wrap;
	justify-content: center;
}

.skill-badge {
	padding: 6rpx 20rpx;
	border-radius: 999rpx;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.skill-text {
	font-size: 24rpx;
	color: var(--wot-color-theme, #2D9B8F);
}

.profile-honor {
	margin-top: 16rpx;
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

.profile-school {
	margin-top: 8rpx;
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.profile-intro {
	margin-top: 20rpx;
	font-size: 28rpx;
	line-height: 1.8;
	color: var(--zy-content, #4B5563);
	text-align: center;
}

/* ── TA 的手作 ── */
.projects-section {
	margin-bottom: 32rpx;
}

.section-title {
	font-size: 34rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
	margin-bottom: 24rpx;
	display: block;
}

.empty-projects {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 80rpx 0;
}

.empty-text {
	font-size: 28rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 骨架项目卡片 ── */
.skeleton-project-card {
	overflow: hidden;
	border-radius: 16px;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
}

.skeleton-cover {
	height: 280rpx;
}

.skeleton-project-body {
	padding: 28rpx;
}

.skeleton-title {
	height: 32rpx;
	width: 60%;
	margin-bottom: 16rpx;
}

.skeleton-text {
	height: 28rpx;
	width: 90%;
}

/* ── 项目卡片列表 ── */
.card-list {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
}

.project-card {
	overflow: hidden;
	border-radius: 16px;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1px solid var(--zy-border, #E5E7EB);
	box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
	transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.project-card:active {
	transform: scale(0.98);
	box-shadow: 0 4rpx 16rpx rgba(45, 155, 143, 0.1);
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
	border-radius: 999rpx;
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

.card-intro {
	display: -webkit-box;
	margin-top: 12rpx;
	font-size: 26rpx;
	line-height: 1.6;
	color: var(--zy-content, #4B5563);
	overflow: hidden;
	text-overflow: ellipsis;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.card-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 20rpx;
}

.category-badge {
	padding: 6rpx 20rpx;
	border-radius: 999rpx;
	background-color: #F3F4F6;
}

.category-text {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}

.time-badge {
	padding: 6rpx 20rpx;
	border-radius: 999rpx;
	background-color: #F3F4F6;
}

.time-text {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}

.tag-badge {
	padding: 6rpx 20rpx;
	border-radius: 999rpx;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.tag-text {
	font-size: 22rpx;
	color: var(--wot-color-theme, #2D9B8F);
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
