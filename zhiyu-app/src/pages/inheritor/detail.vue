<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
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
			<!-- ═══ 渐变头部区 ═══ -->
			<view class="hero-section zy-slide-up">
				<view class="hero-bg"></view>
				<view class="hero-content">
					<view class="hero-avatar-wrap">
						<view class="hero-avatar" :style="avatarStyle(inheritor)">
							<text v-if="!inheritor.avatarUrl" class="hero-avatar-letter">{{ firstLetter(inheritor.nickname) }}</text>
						</view>
					</view>
					<text class="hero-name">{{ inheritor.nickname || '未知' }}</text>
					<view class="hero-tags">
						<view v-if="inheritor.skill" class="skill-badge">
							<text class="skill-text">{{ inheritor.skill }}</text>
						</view>
						<view v-if="inheritor.honor" class="honor-badge">
							<wd-icon name="star-filled" size="24rpx" color="var(--zy-accent, #8a6d1f)" />
							<text class="honor-text">{{ inheritor.honor }}</text>
						</view>
						<!-- 接口暂未下发 region；有值时作为地域印记展示，无值不占位 -->
						<RegionSeal v-if="inheritor.region" :region="inheritor.region" size="md" />
					</view>
					<text v-if="inheritor.school" class="hero-school">{{ inheritor.school }}</text>
				</view>
			</view>

			<!-- ═══ 数据行 ═══ -->
			<view class="stats-row zy-slide-up zy-stagger" style="--stagger-i: 1">
				<view class="stat-item">
					<text class="stat-num">{{ stats.works }}</text>
					<text class="stat-label">作品</text>
				</view>
				<view class="stat-divider"></view>
				<view class="stat-item">
					<text class="stat-num">{{ stats.fans }}</text>
					<text class="stat-label">粉丝</text>
				</view>
				<view class="stat-divider"></view>
				<view class="stat-item">
					<text class="stat-num">{{ stats.likes }}</text>
					<text class="stat-label">获赞</text>
				</view>
			</view>

			<!-- ═══ 简介区 ═══ -->
			<view v-if="inheritor.intro" class="intro-card zy-slide-up zy-stagger" style="--stagger-i: 2">
				<view class="intro-quote">"</view>
				<text class="intro-text">{{ inheritor.intro }}</text>
			</view>

			<!-- ═══ TA 的手作 ═══ -->
			<view class="projects-section zy-slide-up zy-stagger" style="--stagger-i: 3">
				<text class="section-title">TA 的手作</text>

				<!-- 项目加载中 -->
				<view v-if="projectLoading && projects.length === 0" class="project-grid">
					<view v-for="i in 2" :key="'psk-'+i" class="skeleton-project-card">
						<view class="skeleton-cover zy-skeleton"></view>
						<view class="skeleton-project-body">
							<view class="skeleton-title zy-skeleton"></view>
							<view class="skeleton-text zy-skeleton"></view>
						</view>
					</view>
				</view>

				<!-- 首次加载失败 -->
				<view v-else-if="projectError && projects.length === 0" class="empty-projects project-error" role="alert">
					<text class="empty-text">{{ projectError }}</text>
					<AccessibleButton class="project-retry" @click="retryProjects">重试</AccessibleButton>
				</view>

				<!-- 空状态 -->
				<view v-else-if="!projectLoading && !projectError && projects.length === 0" class="empty-projects">
					<text class="empty-text">暂无已上架作品</text>
				</view>

				<!-- 项目卡片网格 -->
				<view v-else class="project-grid">
					<view
						v-for="(item, idx) in projects"
						:key="item.id"
						class="project-card zy-slide-up zy-stagger"
						:style="{ '--stagger-i': idx + 4 }"
						role="button"
						tabindex="0"
						@click="goProjectDetail(item)"
						@keydown.enter.prevent="goProjectDetail(item)"
						@keydown.space.prevent="goProjectDetail(item)"
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
							</view>
						</view>
					</view>

					<!-- 分页失败：保留已加载项目 -->
					<view v-if="projectError && projects.length > 0" class="project-error-row" role="alert">
						<text class="empty-text">{{ projectError }}</text>
						<AccessibleButton class="project-retry" @click="retryProjects">重试</AccessibleButton>
					</view>

					<!-- 底部状态 -->
					<view v-if="projects.length > 0" class="bottom-status">
						<view v-if="projectLoading" class="loading-dot"></view>
						<text v-if="projectLoading" class="bottom-text">正在加载…</text>
						<template v-else-if="!hasMore">
							<view class="bottom-divider"></view>
							<text class="bottom-text">没有更多了</text>
							<view class="bottom-divider"></view>
						</template>
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
import AccessibleButton from '../../components/AccessibleButton.vue'
import RegionSeal from '../../components/RegionSeal.vue'

const inheritorId = ref(null)
const inheritor = ref(null)
const loading = ref(false)
const errorMsg = ref('')

const projects = ref([])
const projectLoading = ref(false)
const projectError = ref('')
const page = ref(1)
const total = ref(0)
const size = 10

// 统计数据（来自后端 InheritorPublicDetailVO）
const stats = ref({
	works: 0,
	fans: 0,
	likes: 0,
})

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
		// 从后端返回的统计字段填充
		if (data) {
			stats.value.works = data.workCount || 0
			stats.value.fans = data.fanCount || 0
			stats.value.likes = data.likeCount || 0
		}
	} catch (e) {
		errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
	} finally {
		loading.value = false
	}
}

async function loadProjects(pageNum) {
	projectLoading.value = true
	projectError.value = ''
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
		projectError.value = '手作项目加载失败，请重试'
	} finally {
		projectLoading.value = false
	}
}

async function loadMoreProjects() {
	projectLoading.value = true
	projectError.value = ''
	try {
		const data = await http.get(`/api/inheritors/${inheritorId.value}/projects`, {
			data: { page: page.value, pageSize: size },
		})
		if (data) {
			total.value = Number(data.total) || 0
			projects.value = projects.value.concat(data.records || [])
		}
	} catch (e) {
		projectError.value = '更多手作加载失败，请重试'
	} finally {
		projectLoading.value = false
	}
}

function retryProjects() {
	if (projects.value.length > 0) loadMoreProjects()
	else loadProjects(1)
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
	return 'background: linear-gradient(135deg, #217e76, var(--zy-brand-300, #5bc4a8));'
}

function coverStyle(coverUrl) {
	// #ifdef H5
	if (coverUrl) {
		return `background-image: url(${coverUrl}); background-size: cover; background-position: center;`
	}
	// #endif
	return 'background: linear-gradient(135deg, #217e76, var(--zy-brand-300, #5bc4a8));'
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
	padding: 0 0 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #ffffff);
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
	width: 160rpx;
	height: 160rpx;
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
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-danger, var(--zy-care, #a64842));
}

/* ── 渐变头部区 ── */
.hero-section {
	position: relative;
	padding: 60rpx 32rpx 40rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
	overflow: hidden;
}

.hero-bg {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background: linear-gradient(180deg, var(--zy-bg-tint-mint, #e8efe4) 0%, var(--zy-bg, #ffffff) 100%);
}

.hero-content {
	position: relative;
	z-index: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 16rpx;
}

.hero-avatar-wrap {
	width: 212rpx;
	height: 212rpx;
	border-radius: 50%;
	padding: 6rpx;
	background: linear-gradient(135deg, var(--zy-primary, var(--zy-brand-600, #217e76)), var(--zy-primary-light, var(--zy-brand-300, #5bc4a8)));
	margin-bottom: 8rpx;
}

.hero-avatar {
	width: 100%;
	height: 100%;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	border: 6rpx solid var(--zy-card-bg, #FFFFFF);
	box-sizing: border-box;
	box-shadow: var(--zy-shadow-md);
}

.hero-avatar-letter {
	font-size:var(--zy-text-display, 56rpx);
	font-weight: 600;
	color: #FFFFFF;
}

.hero-name {
	font-size:var(--zy-text-title-lg, 40rpx);
	font-weight: 600;
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
}

.hero-tags {
	display: flex;
	gap: 12rpx;
	flex-wrap: wrap;
	justify-content: center;
}

.skill-badge {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.skill-text {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
}

.honor-badge {
	display: flex;
	align-items: center;
	gap: 6rpx;
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-warm, #f7efd9);
}

.honor-text {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-accent, var(--zy-accent, #7a5c12));
}

.hero-school {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-aid, #4a5f54);
}

/* ── 数据行 ── */
.stats-row {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 0;
	margin: 0 24rpx 24rpx;
	padding: 28rpx 0;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	box-shadow: var(--zy-shadow-sm);
}

.stat-item {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 4rpx;
}

.stat-num {
	font-size:var(--zy-text-title-lg, 40rpx);
	font-weight: 700;
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
	line-height: 1;
}

.stat-label {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-aid, #4a5f54);
}

.stat-divider {
	width: 1rpx;
	height: 48rpx;
	background-color: var(--zy-border, #dfe7dc);
}

/* ── 简介区 ── */
.intro-card {
	position: relative;
	margin: 0 24rpx 24rpx;
	padding: 32rpx 28rpx 28rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	box-shadow: var(--zy-shadow-sm);
	border: 1rpx solid var(--zy-border, #dfe7dc);
}

.intro-quote {
	position: absolute;
	top: 8rpx;
	left: 20rpx;
	font-size:var(--zy-text-display, 56rpx);
	font-family: var(--zy-serif);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
	opacity: 0.2;
	line-height: 1;
}

.intro-text {
	font-size:var(--zy-text-body, 28rpx);
	line-height: 1.8;
	color: var(--zy-content, #4a5f54);
}

/* ── TA 的手作 ── */
.projects-section {
	margin: 0 24rpx;
}

.section-title {
	font-size:var(--zy-text-title, 32rpx);
	font-weight: 500;
	color: var(--zy-title, #233d34);
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
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-aid, #4a5f54);
}

/* ── 骨架项目卡片 ── */
.skeleton-project-card {
	overflow: hidden;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
}

.skeleton-cover {
	height: 200rpx;
}

.skeleton-project-body {
	padding: 24rpx;
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

/* ── 项目卡片网格（2 列） ── */
.project-grid {
	display: flex;
	flex-wrap: wrap;
	gap: 20rpx;
}

.project-card {
	width: calc((100% - 20rpx) / 2);
	overflow: hidden;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1px solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
	transition: transform var(--zy-duration-base, 200ms) var(--zy-ease-out, ease-out), box-shadow var(--zy-duration-base, 200ms) var(--zy-ease-out, ease-out);
}

.project-card:active {
	transform: scale(0.98);
	box-shadow: var(--zy-shadow-md);
}

/* ── 封面图 ── */
.card-cover {
	height: 200rpx;
	display: flex;
	align-items: flex-start;
	justify-content: flex-end;
	padding: 16rpx;
}

.level-badge {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-gold, #d9a441);
}

.level-text {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-brand-900, var(--zy-brand-900, #0f3d38));
	font-weight: 600;
}

/* ── 卡片内容 ── */
.card-body {
	padding: 24rpx;
}

.card-title {
	font-size:var(--zy-text-body, 28rpx);
	font-weight: 500;
	color: var(--zy-title, #233d34);
	display: -webkit-box;
	overflow: hidden;
	text-overflow: ellipsis;
	-webkit-line-clamp: 1;
	-webkit-box-orient: vertical;
}

.card-intro {
	display: -webkit-box;
	margin-top: 8rpx;
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 1.5;
	color: var(--zy-content, #4a5f54);
	overflow: hidden;
	text-overflow: ellipsis;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.card-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 8rpx;
	margin-top: 16rpx;
}

.category-badge {
	padding: 4rpx 14rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg, #ffffff);
}

.category-text {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-aid, #4a5f54);
}

.time-badge {
	padding: 4rpx 14rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg, #ffffff);
}

.time-text {
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
	width: 100%;
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
	margin: 24rpx;
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-aid, #4a5f54);
	text-align: center;
}
</style>

<style scoped>
.project-error { flex-direction: column; gap: 16rpx; }
.project-error-row {
	grid-column: 1 / -1;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;
	margin-top: 20rpx;
	padding: 24rpx;
	border: 1rpx dashed var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-md, 24rpx);
	background: var(--zy-card-bg, #fff);
}
.project-retry {
	min-height: 88rpx;
	padding: 0 28rpx;
	border: 1rpx solid var(--zy-primary, var(--zy-brand-600, #217e76));
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 2.8;
}
.project-retry::after { border: 0; }
.project-retry:focus-visible { outline: 3px solid var(--zy-primary, var(--zy-brand-600, #217e76)); outline-offset: 3px; }
</style>
