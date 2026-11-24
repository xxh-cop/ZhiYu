<template>
	<view :class="zyThemeClass" class="page">
		<view v-if="loading && !inheritor" class="skeleton-wrap">
			<view class="skeleton-banner zy-skeleton"></view>
			<view class="skeleton-avatar zy-skeleton"></view>
			<view class="skeleton-name zy-skeleton"></view>
			<view class="skeleton-intro zy-skeleton"></view>
		</view>

		<view v-else-if="errorMsg && !inheritor" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<AccessibleButton variant="secondary" size="sm" @click="loadInheritor">重试</AccessibleButton>
		</view>

		<view v-else-if="inheritor">
			<!-- ① 工艺大图横幅 -->
			<view class="banner">
				<image class="banner-img" :src="bannerSrc" mode="aspectFill" aria-hidden="true" />
				<view class="banner-shade" aria-hidden="true"></view>
				<view class="banner-tag">
					<text class="banner-tag-text">示范者档案</text>
				</view>
				<view v-if="inheritor.skill" class="banner-seal"><ZySeal :text="inheritor.skill.slice(0, 2)" size="lg" tilt /></view>
			</view>

			<!-- ② 名片：头像压在横幅边缘 -->
			<view class="profile-card">
				<view class="avatar-ring">
					<view class="avatar" :style="avatarStyle(inheritor)">
						<text v-if="!inheritor.avatarUrl" class="avatar-letter">{{ firstLetter(inheritor.nickname) }}</text>
					</view>
				</view>
				<text class="name">{{ inheritor.nickname || '示范者' }}</text>
				<view class="tags">
					<text v-if="inheritor.skill" class="tag tag-skill">{{ inheritor.skill }}</text>
					<text v-if="inheritor.honor" class="tag tag-honor">{{ inheritor.honor }}</text>
					<RegionSeal v-if="inheritor.region" :region="inheritor.region" size="md" />
				</view>
				<text v-if="inheritor.school" class="school">{{ inheritor.school }}</text>

				<view v-if="stats.works || stats.fans || stats.likes" class="stats">
					<view class="stat"><text class="stat-num">{{ stats.works }}</text><text class="stat-label">作品</text></view>
					<view class="stat-divider" aria-hidden="true"></view>
					<view class="stat"><text class="stat-num">{{ stats.fans }}</text><text class="stat-label">关注</text></view>
					<view class="stat-divider" aria-hidden="true"></view>
					<view class="stat"><text class="stat-num">{{ stats.likes }}</text><text class="stat-label">获赞</text></view>
				</view>
			</view>

			<view class="body">
				<!-- ③ 手艺人的话 -->
				<view v-if="inheritor.intro" class="quote-card">
					<text class="quote-mark" aria-hidden="true">“</text>
					<text class="quote-text">{{ inheritor.intro }}</text>
					<view class="quote-foot">
						<view class="quote-line" aria-hidden="true"></view>
						<text class="quote-by">{{ inheritor.nickname }}</text>
					</view>
				</view>

				<!-- ④ 示范项目 -->
				<view class="sec-head">
					<view class="sec-mark" aria-hidden="true"></view>
					<text class="sec-title">跟着 TA 做一件</text>
					<text v-if="projects.length" class="sec-note">{{ total || projects.length }} 个项目</text>
				</view>

				<view v-if="projectLoading && projects.length === 0" class="project-grid">
					<view v-for="i in 2" :key="'psk-'+i" class="project-card">
						<view class="skeleton-cover zy-skeleton"></view>
						<view class="skeleton-line zy-skeleton"></view>
					</view>
				</view>

				<view v-else-if="projectError && projects.length === 0" class="empty-card" role="alert">
					<text class="empty-text">{{ projectError }}</text>
					<AccessibleButton variant="secondary" size="sm" @click="retryProjects">重试</AccessibleButton>
				</view>

				<view v-else-if="!projectLoading && !projectError && projects.length === 0" class="empty-card">
					<text class="empty-text">TA 的示范项目正在整理中</text>
				</view>

				<view v-else>
					<view class="project-grid">
						<view
							v-for="item in projects"
							:key="item.id"
							class="project-card"
							role="button"
							tabindex="0"
							:aria-label="'查看手作 ' + item.title"
							@click="goProjectDetail(item)"
							@keydown.enter.prevent="goProjectDetail(item)"
						>
							<view class="project-cover">
								<image class="project-img" :src="coverFor(item)" mode="aspectFill" lazy-load />
								<text v-if="levelLabel(item.level)" class="level-pill">{{ levelLabel(item.level) }}</text>
							</view>
							<view class="project-body">
								<text class="project-title">{{ item.title }}</text>
								<text v-if="item.intro" class="project-intro">{{ item.intro }}</text>
								<view class="project-meta">
									<ZyIcon name="clock" :size="22" tone="muted" />
									<text>约 {{ item.estMinutes || '—' }} 分钟</text>
								</view>
							</view>
						</view>
					</view>

					<view v-if="projectError && projects.length > 0" class="empty-card" role="alert">
						<text class="empty-text">{{ projectError }}</text>
						<AccessibleButton variant="secondary" size="sm" @click="retryProjects">重试</AccessibleButton>
					</view>

					<view v-if="projects.length > 0" class="bottom-status">
						<ZySpinner v-if="projectLoading" :size="28" />
						<text v-if="projectLoading" class="bottom-text">正在加载…</text>
						<template v-else-if="!hasMore">
							<view class="bottom-divider zy-band-meander" aria-hidden="true"></view>
							<text class="bottom-text">已看完全部项目</text>
							<view class="bottom-divider zy-band-meander" aria-hidden="true"></view>
						</template>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ZySpinner from '../../components/ZySpinner.vue'
import RegionSeal from '../../components/RegionSeal.vue'
import ZySeal from '../../components/ZySeal.vue'
import ZyIcon from '../../components/ZyIcon.vue'
import { coverFor } from '../../utils/covers'

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
/* 横幅按技艺取项目自带的工艺实拍图 */
const BANNER_BY_SKILL = { 赣绣: '/static/heritage/texture/stitch.jpg', 刺绣: '/static/heritage/texture/stitch.jpg', 苏绣: '/static/heritage/texture/stitch.jpg', 剪纸: '/static/heritage/real/papercut.jpg', 扎染: '/static/covers/zharan.jpg', 陶艺: '/static/blueprint/hero-glaze.jpg', 青花: '/static/heritage/real/porcelain.jpg', 竹编: '/static/heritage/real/bamboo.jpg', 编织: '/static/heritage/texture/weave.jpg' }
const bannerSrc = computed(() => BANNER_BY_SKILL[inheritor.value?.skill] || '/static/heritage/texture/cold-linen.jpg')

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
			stats.value.works = Number(data.workCount) || 0
			stats.value.fans = Number(data.fanCount) || 0
			stats.value.likes = Number(data.likeCount) || 0
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
	return ''
}

function coverStyle(coverUrl) {
	// #ifdef H5
	if (coverUrl) {
		return `background-image: url(${coverUrl}); background-size: cover; background-position: center;`
	}
	// #endif
	return 'background: linear-gradient(135deg, #24478f, var(--zy-brand-300, #8ea9dc));'
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

<style scoped>
/* v9 传承人档案：工艺横幅 + 压边名片 + 引言卡 + 实拍项目卡 */
.page { min-height: 100vh; padding: 0 0 80rpx; box-sizing: border-box; background: var(--zy-bg); }
.status-box { display: flex; flex-direction: column; align-items: center; gap: 24rpx; padding: 200rpx 40rpx; }
.error-text { font-size: 28rpx; color: var(--zy-care); text-align: center; }

.skeleton-wrap { display: flex; flex-direction: column; align-items: center; gap: 20rpx; }
.skeleton-banner { width: 100%; height: 380rpx; border-radius: 0 0 48rpx 48rpx; }
.skeleton-avatar { width: 160rpx; height: 160rpx; border-radius: 50%; margin-top: -80rpx; }
.skeleton-name { width: 240rpx; height: 44rpx; }
.skeleton-intro { width: 80%; height: 120rpx; }

/* ① 横幅 */
.banner { position: relative; height: 400rpx; overflow: hidden; background: var(--zy-brand-900); border-radius: 0 0 56rpx 56rpx; }
.banner-img { position: absolute; inset: 0; width: 100%; height: 100%; }
.banner-shade { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(13, 31, 69, .15) 0%, rgba(13, 31, 69, .55) 100%); }
.banner-tag { position: absolute; left: 32rpx; top: 32rpx; padding: 8rpx 22rpx; border-radius: 999rpx; background: rgba(255, 255, 255, .22); border: 2rpx solid rgba(255, 255, 255, .35); }
.banner-tag-text { font-size: 22rpx; letter-spacing: 4rpx; color: #fff; }
.banner-seal { position: absolute; right: 36rpx; top: 28rpx; }

/* ② 名片 */
.profile-card { position: relative; z-index: 2; margin: -110rpx 28rpx 0; padding: 0 28rpx 30rpx; background: var(--zy-card-bg); border-radius: var(--zy-radius-feature, 36rpx); box-shadow: var(--zy-shadow-pop); display: flex; flex-direction: column; align-items: center; text-align: center; }
.avatar-ring { margin-top: -84rpx; padding: 8rpx; border-radius: 50%; background: var(--zy-card-bg); box-shadow: 0 10rpx 28rpx rgba(20, 36, 72, .18); }
.avatar { width: 168rpx; height: 168rpx; border-radius: 50%; background: var(--zy-brand-600); display: flex; align-items: center; justify-content: center; overflow: hidden; }
.avatar-letter { font-family: var(--zy-font-display); font-size: 76rpx; font-weight: 700; color: #fff; }
.name { margin-top: 18rpx; font-family: var(--zy-font-display); font-size: 52rpx; font-weight: 700; letter-spacing: 6rpx; color: var(--zy-title); }
.tags { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 12rpx; margin-top: 14rpx; }
.tag { padding: 6rpx 20rpx; border-radius: 999rpx; font-size: 24rpx; font-weight: 600; }
.tag-skill { background: var(--zy-brand-50); color: var(--zy-brand-700); }
.tag-honor { background: var(--zy-gamboge-50, #fbf1de); color: #8a5a12; }
.school { margin-top: 10rpx; font-size: 24rpx; color: var(--zy-muted); }
.stats { width: 100%; display: flex; align-items: center; margin-top: 26rpx; padding-top: 24rpx; border-top: 2rpx solid var(--zy-line-soft); }
.stat { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4rpx; }
.stat-num { font-family: var(--zy-font-display); font-size: 44rpx; font-weight: 700; color: var(--zy-brand-600); }
.stat-label { font-size: 22rpx; color: var(--zy-muted); }
.stat-divider { width: 2rpx; height: 56rpx; background: var(--zy-line-soft); }

.body { padding: 28rpx 28rpx 0; }

/* ③ 引言卡：暖纸色 */
.quote-card { position: relative; padding: 40rpx 36rpx 30rpx; border-radius: var(--zy-radius-card, 28rpx); background: #fbf3e6; box-shadow: var(--zy-shadow-card); overflow: hidden; }
.page.zy-theme-dark .quote-card { background: #2c2520; }
.quote-mark { position: absolute; left: 20rpx; top: -8rpx; font-family: var(--zy-font-display); font-size: 140rpx; line-height: 1; color: rgba(192, 57, 43, .18); }
.quote-text { position: relative; display: block; font-family: var(--zy-font-display); font-size: 32rpx; line-height: 1.9; color: var(--zy-title); }
.quote-foot { display: flex; align-items: center; justify-content: flex-end; gap: 14rpx; margin-top: 18rpx; }
.quote-line { width: 48rpx; height: 3rpx; border-radius: 2rpx; background: var(--zy-cinnabar); }
.quote-by { font-size: 24rpx; color: var(--zy-cinnabar); font-weight: 600; letter-spacing: 2rpx; }

.sec-head { display: flex; align-items: center; gap: 14rpx; margin: 40rpx 0 20rpx; }
.sec-mark { width: 8rpx; height: 34rpx; border-radius: 4rpx; background: var(--zy-cinnabar); }
.sec-title { font-family: var(--zy-font-display); font-size: 36rpx; font-weight: 700; color: var(--zy-title); }
.sec-note { margin-left: auto; font-size: 22rpx; color: var(--zy-muted); }

/* ④ 项目卡 */
.project-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; }
.project-card { background: var(--zy-card-bg); border-radius: var(--zy-radius-card, 28rpx); overflow: hidden; box-shadow: var(--zy-shadow-card); }
.project-card:active { transform: scale(.98); }
.project-cover { position: relative; height: 260rpx; background: var(--zy-bg-sunken); }
.project-img { width: 100%; height: 100%; display: block; }
.level-pill { position: absolute; left: 16rpx; top: 16rpx; padding: 4rpx 16rpx; border-radius: 999rpx; background: rgba(255, 255, 255, .92); color: var(--zy-brand-700); font-size: 22rpx; font-weight: 700; }
.project-body { padding: 18rpx 20rpx 22rpx; }
.project-title { display: block; font-size: 28rpx; font-weight: 700; color: var(--zy-title); }
.project-intro { display: -webkit-box; margin-top: 6rpx; font-size: 22rpx; line-height: 1.6; color: var(--zy-text); overflow: hidden; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.project-meta { display: flex; align-items: center; gap: 6rpx; margin-top: 12rpx; font-size: 22rpx; color: var(--zy-muted); }
.skeleton-cover { height: 260rpx; }
.skeleton-line { height: 28rpx; width: 70%; margin: 18rpx 20rpx 22rpx; }
.empty-card { display: flex; flex-direction: column; align-items: center; gap: 16rpx; padding: 40rpx; border-radius: var(--zy-radius-card, 28rpx); background: var(--zy-card-bg); box-shadow: var(--zy-shadow-card); }
.empty-text { font-size: 26rpx; color: var(--zy-muted); }

.bottom-status { display: flex; align-items: center; justify-content: center; gap: 16rpx; padding: 36rpx 0 8rpx; }
.bottom-divider { width: 84rpx; }
.bottom-text { font-size: 24rpx; color: var(--zy-muted); }
</style>
