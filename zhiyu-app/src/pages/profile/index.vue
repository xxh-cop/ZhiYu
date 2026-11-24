<template>
	<view :class="zyThemeClass" class="page">
		<!-- ① 青花名帖 -->
		<view class="name-card">
			<view class="name-wave zy-wave-bg--light" aria-hidden="true"></view>
			<view class="name-top">
				<AccessibleButton class="monogram" aria-label="修改个人资料" @click="goSettings">
					<image v-if="avatarFullUrl && !avatarError" class="avatar-img" :src="avatarFullUrl" mode="aspectFill" alt="我的头像" @error="avatarError = true" />
					<text v-else class="avatar-text">{{ avatarChar }}</text>
				</AccessibleButton>
				<view class="identity-copy">
					<text class="profile-name">{{ nickname }}</text>
					<text class="profile-signature">{{ signature }}</text>
					<view class="profile-meta">
						<text v-if="roleLabel" class="meta-pill">{{ roleLabel }}</text>
						<text v-if="school" class="meta-pill">{{ school }}</text>
					</view>
				</view>
				<AccessibleButton class="gear" aria-label="个人设置" @click="goSettings"><ZyIcon name="settings" :size="36" color="#FFFFFF" /></AccessibleButton>
			</view>
		</view>

		<!-- ② 旅程三指标（与首页同源：/api/user/stats） -->
		<view class="stats-card" role="group" aria-label="我的创作旅程">
			<view class="stat">
				<text class="stat-num">{{ stats.daysHere }}</text>
				<text class="stat-label">来到这里 · 天</text>
			</view>
			<view class="stat-divider" aria-hidden="true"></view>
			<view class="stat">
				<text class="stat-num">{{ stats.totalWorks }}</text>
				<text class="stat-label">作品 · 件</text>
			</view>
			<view class="stat-divider" aria-hidden="true"></view>
			<view class="stat">
				<text class="stat-num">{{ stats.totalMinutes }}</text>
				<text class="stat-label">专注 · 分钟</text>
			</view>
		</view>

		<StateBlock v-if="profileError" type="error" compact title="信息暂时没有更新" :description="profileError" action-text="重新加载" @action="retryProfile" />

		<!-- ③ 印记：印章徽章 -->
		<view class="sec-head">
			<view class="sec-mark" aria-hidden="true"></view>
			<text class="sec-title">一路留下的印记</text>
			<text class="sec-note">{{ earnedCount }} / {{ badges.length }}</text>
		</view>
		<view class="badge-grid">
			<view v-for="badge in badges" :key="badge.key" class="badge" :class="{ locked: !badge.earned }" :aria-label="badge.name + '，' + (badge.earned ? badge.desc : badge.progress)">
				<ZySeal :text="badge.mark" size="lg" :tone="badge.earned ? 'cinnabar' : 'outline'" :tilt="badge.earned" />
				<text class="badge-name">{{ badge.name }}</text>
				<text class="badge-desc">{{ badge.earned ? badge.desc : badge.progress }}</text>
			</view>
		</view>

		<!-- ④ 我的记录：宫格 -->
		<view class="sec-head">
			<view class="sec-mark" aria-hidden="true"></view>
			<text class="sec-title">{{ menuSections[0].title }}</text>
		</view>
		<view class="tile-grid">
			<AccessibleButton v-for="item in menuSections[0].items" :key="item.action" class="tile" :aria-label="item.label" @click="handleMenu(item)">
				<view class="tile-icon" :class="'tone-' + item.tone" aria-hidden="true"><ZyIcon :name="item.icon" :size="40" :tone="iconTone(item.tone)" /></view>
				<text class="tile-label">{{ item.label }}</text>
			</AccessibleButton>
		</view>

		<!-- ⑤ 其余分区：列表 -->
		<view v-for="section in menuSections.slice(1)" :key="section.title" class="menu-section">
			<view class="sec-head">
				<view class="sec-mark" aria-hidden="true"></view>
				<text class="sec-title">{{ section.title }}</text>
			</view>
			<view class="menu-list">
				<AccessibleButton v-for="item in section.items" :key="item.action" class="menu-row" :aria-label="item.label" @click="handleMenu(item)">
					<view class="row-icon" :class="'tone-' + item.tone" aria-hidden="true"><ZyIcon :name="item.icon" :size="32" :tone="iconTone(item.tone)" /></view>
					<text class="row-label">{{ item.label }}</text>
					<ZyIcon name="arrow" :size="28" tone="faint" />
				</AccessibleButton>
			</view>
		</view>

		<!-- ⑥ 我的边界 -->
		<view class="boundary-card">
			<view class="boundary-band zy-band-meander" aria-hidden="true"></view>
			<text class="boundary-title">你的记录，由你做主</text>
			<text class="boundary-desc">在「账号与隐私」中可以撤回授权、导出个人记录，或注销账号。操作即时生效，导出副本归你保存。</text>
			<AccessibleButton variant="secondary" size="md" class="boundary-action" @click="goPrivacy">管理授权与账号</AccessibleButton>
		</view>

		<AccessibleButton class="logout-btn" aria-label="退出登录" @click="showLogout = true">退出登录</AccessibleButton>


		<ActionSheet
			:open="showLogout"
			title="退出登录？"
			description="退出后需要重新登录才能查看个人记录；本地未同步的内容不会丢失。"
			:actions="[{ key: 'confirm', label: '确认退出', tone: 'danger' }]"
			cancel-text="继续留下"
			@select="handleLogout"
			@close="showLogout = false"
		/>
		<Toast />
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { useUserStore } from '../../stores/user'
import { API_BASE_URL } from '../../utils/config.js'
import StateBlock from '../../components/StateBlock.vue'
import ActionSheet from '../../components/ActionSheet.vue'
import Toast from '../../components/Toast.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ZyIcon from '../../components/ZyIcon.vue'
import ZySeal from '../../components/ZySeal.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()
const ROLE_LABEL = { 0: '学生', 1: '传承人', 2: '咨询师', 3: '管理员' }

/* 全部原有入口保留，仅按「记录 / 参与 / 支持 / 设置」重新分区 */
const menuSections = [
	{
		title: '我的记录',
		items: [
			{ icon: 'picture', label: '我的作品', action: 'works', tone: 'brand' },
			{ icon: 'star', label: '我的收藏', action: 'favorites', tone: 'gold' },
			{ icon: 'comment', label: '倾听记录', action: 'history', tone: 'brand-light' },
			{ icon: 'edit', label: '我的便签', action: 'notes', tone: 'gold' },
			{ icon: 'clock', label: '成长旅程', action: 'journey', tone: 'brand' },
			{ icon: 'eye', label: '自我了解', action: 'assessment', tone: 'brand-light' },
		],
	},
	{
		title: '我的参与',
		items: [
			{ icon: 'group', label: '活动与预约', action: 'programs', tone: 'brand' },
			{ icon: 'comment', label: '非遗问答', action: 'rag', tone: 'brand-light' },
			{ icon: 'user', label: '校园身份', action: 'campus', tone: 'brand' },
		],
	},
	{
		title: '支持与设置',
		items: [
			{ icon: 'phone', label: '心理援助', action: 'crisis', tone: 'care' },
			{ icon: 'settings', label: '个人设置', action: 'settings', tone: 'brand' },
			{ icon: 'eye-off', label: '隐私设置', action: 'privacy', tone: 'neutral' },
		],
	},
]

const userStore = useUserStore()
const loadingMe = ref(false)
const showLogout = ref(false)

const nickname = computed(() => (userStore.userInfo && userStore.userInfo.nickname) || '同学')
const school = computed(() => (userStore.userInfo && userStore.userInfo.school) || '')
const roleLabel = computed(() => {
	const role = userStore.userInfo ? userStore.userInfo.role : null
	return role === null || role === undefined ? '' : ROLE_LABEL[role] || ''
})
const avatarChar = computed(() => (nickname.value && nickname.value.charAt(0)) || '织')
const avatarError = ref(false)
const avatarFullUrl = computed(() => {
	const url = userStore.userInfo ? userStore.userInfo.avatarUrl : ''
	return url ? (url.startsWith('http') ? url : `${API_BASE_URL}${url}`) : ''
})

const signature = computed(() => {
	const hour = new Date().getHours()
	if (hour < 6) return '夜深了，先把自己安顿好'
	if (hour < 12) return '今天也要好好爱自己'
	if (hour < 18) return '给自己留一点安静的时间'
	return '把今天的疲惫放一放'
})

/* 旅程三指标：全部真实累计口径。
   2026-11-05 修正：原「连续创作天数」读的后端 streakDays 字段并不存在（恒 0），
   且连续口径本身是断签惩罚机制，与「按自己的节奏」基调冲突——
   改为后端真实累计字段 daysSinceRegister（来到这里的天数，无断签压力）。
   徽章同步删除 streak-3/streak-7 连续类，只保留累计类。 */
const stats = ref({ daysHere: '—', totalWorks: '—', totalMinutes: '—' })
const statsRaw = ref({ daysHere: 0, totalWorks: 0, totalMinutes: 0 })
const statsReady = ref(false)
const profileError = ref('')

const badges = computed(() => {
	const { totalWorks, totalMinutes } = statsRaw.value
	const ready = statsReady.value
	const progress = (current, target) => (ready ? `进行中 ${Math.min(current, target)} / ${target}` : '等待数据载入')
	return [
		{ key: 'first-work', mark: '初', name: '初作', desc: '发布了第一份作品', earned: ready && totalWorks >= 1, progress: progress(totalWorks, 1) },
		{ key: 'focus-60', mark: '光', name: '拾光', desc: '专注累计满 60 分钟', earned: ready && totalMinutes >= 60, progress: progress(totalMinutes, 60) },
		{ key: 'focus-300', mark: '闲', name: '半日闲', desc: '专注累计满 300 分钟', earned: ready && totalMinutes >= 300, progress: progress(totalMinutes, 300) },
		{ key: 'works-10', mark: '拾', name: '十作', desc: '发布了 10 份作品', earned: ready && totalWorks >= 10, progress: progress(totalWorks, 10) },
	]
})

const earnedCount = computed(() => badges.value.filter((b) => b.earned).length)
const ICON_TONE = { brand: 'brand', 'brand-light': 'pine', gold: 'gold', care: 'cinnabar', neutral: 'muted' }
function iconTone(tone) { return ICON_TONE[tone] || 'brand' }

onShow(() => {
	if (!userStore.isLogin) {
		uni.reLaunch({ url: '/pages/login/index' })
		return
	}
	loadCurrentUser()
	loadStats()
})

async function loadCurrentUser() {
	if (loadingMe.value) return
	loadingMe.value = true
	try {
		const data = await request({ url: '/api/auth/me', method: 'GET' })
		userStore.setUserInfo(data)
		profileError.value = ''
	} catch (e) {
		profileError.value = '个人资料暂时没有更新，请重试'
	} finally {
		loadingMe.value = false
	}
}

async function loadStats() {
	try {
		// /api/user/stats：daysSinceRegister/workCount/noteCount（全真累计）；
		// 专注分钟后端 stats 无字段，取 /api/growth/journey 的 flowSeconds（journey 页同源真值），失败则 0
		const [data, journey] = await Promise.all([
			request({ url: '/api/user/stats', method: 'GET' }),
			request({ url: '/api/growth/journey', method: 'GET' }).catch(() => null),
		])
		statsRaw.value = {
			daysHere: Number(data.daysSinceRegister) || 0,
			totalWorks: Number(data.workCount) || 0,
			totalMinutes: journey && Number.isFinite(Number(journey.flowSeconds)) ? Math.floor(Number(journey.flowSeconds) / 60) : 0,
		}
		stats.value = { ...statsRaw.value }
		statsReady.value = true
		profileError.value = ''
	} catch (e) {
		stats.value = { daysHere: '—', totalWorks: '—', totalMinutes: '—' }
		statsReady.value = false
		profileError.value = '统计暂时无法读取，请重试'
	}
}

function retryProfile() {
	profileError.value = ''
	loadCurrentUser()
	loadStats()
}

function goSettings() { uni.navigateTo({ url: '/pages/settings/index' }) }
function goPrivacy() { uni.navigateTo({ url: '/pages/privacy/settings' }) }

function handleMenu(item) {
	if (item.action === 'works') { uni.navigateTo({ url: '/pages/work/mine' }); return }
	if (item.action === 'favorites') { uni.navigateTo({ url: '/pages/work/favorites' }); return }
	if (item.action === 'campus') { uni.navigateTo({ url: '/pages/campus/index' }); return }
	if (item.action === 'history') { uni.navigateTo({ url: '/pages/emotion/history' }); return }
	if (item.action === 'programs') { uni.navigateTo({ url: '/sub-programs/pages/programs/index' }); return }
	if (['journey', 'assessment'].includes(item.action)) { uni.navigateTo({ url: '/pages/' + item.action + '/index' }); return }
	if (item.action === 'crisis') { uni.navigateTo({ url: '/pages/crisis/index' }); return }
	if (item.action === 'privacy') { uni.navigateTo({ url: '/pages/privacy/settings' }); return }
	if (item.action === 'settings') { uni.navigateTo({ url: '/pages/settings/index' }); return }
	if (item.action === 'notes') { uni.navigateTo({ url: '/pages/emotion/notes' }); return }
	if (item.action === 'rag') { uni.navigateTo({ url: '/pages/ai/rag-ask' }); return }
	toast.show('即将上线')
}

function handleLogout() {
	showLogout.value = false
	userStore.logout()
}
</script>

<style scoped>
.page { min-height: 100vh; padding: 0 28rpx 160rpx; box-sizing: border-box; background-color: var(--zy-bg); }
/* #ifdef MP */
.page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
/* 自定义导航：让出微信胶囊按钮区域 */
.name-card { padding-top: calc(var(--status-bar-height, 20px) + 120rpx); }
/* #endif */

/* 青花名帖 */
.name-card { position: relative; margin: 0 -28rpx; padding: calc(var(--status-bar-height, 0px) + 56rpx) 36rpx 110rpx; background: var(--zy-brand-600); border-radius: 0 0 56rpx 56rpx; overflow: hidden; }
.name-wave { position: absolute; inset: 0; }
.name-top { position: relative; display: flex; align-items: center; gap: 26rpx; }
.monogram { width: 136rpx; height: 136rpx; min-width: 136rpx; padding: 0; margin: 0; border-radius: 50%; background: #fff; border: 6rpx solid rgba(255, 255, 255, .45); overflow: hidden; display: flex; align-items: center; justify-content: center; box-shadow: 0 10rpx 30rpx rgba(13, 31, 69, .35); }
.avatar-img { width: 100%; height: 100%; }
.avatar-text { font-family: var(--zy-font-display); font-size: 64rpx; font-weight: 700; color: var(--zy-brand-600); }
.identity-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
.profile-name { font-family: var(--zy-font-display); font-size: 48rpx; font-weight: 700; color: #fff; letter-spacing: 2rpx; }
.profile-signature { font-size: 24rpx; color: rgba(255, 255, 255, .82); }
.profile-meta { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 6rpx; }
.meta-pill { padding: 4rpx 18rpx; border-radius: 999rpx; background: rgba(255, 255, 255, .18); color: #fff; font-size: 22rpx; }
.gear { position: absolute; top: -12rpx; right: -8rpx; width: 72rpx; height: 72rpx; min-width: 72rpx; padding: 0; margin: 0; border-radius: 50%; background: rgba(255, 255, 255, .16); border: 0; display: flex; align-items: center; justify-content: center; }

/* 统计 */
.stats-card { position: relative; z-index: 2; display: flex; align-items: center; margin: -72rpx 8rpx 0; padding: 30rpx 10rpx; background: var(--zy-card-bg); border-radius: var(--zy-radius-card); box-shadow: var(--zy-shadow-pop); }
.stat { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6rpx; }
.stat-num { font-family: var(--zy-font-display); font-size: 52rpx; font-weight: 700; color: var(--zy-brand-600); line-height: 1.1; }
.stat-label { font-size: 22rpx; color: var(--zy-muted); }
.stat-divider { width: 2rpx; height: 64rpx; background: var(--zy-line-soft); }

.sec-head { display: flex; align-items: center; gap: 14rpx; margin: 44rpx 0 20rpx; }
.sec-mark { width: 8rpx; height: 34rpx; border-radius: 4rpx; background: var(--zy-cinnabar); }
.sec-title { font-family: var(--zy-font-display); font-size: 36rpx; font-weight: 700; letter-spacing: 2rpx; color: var(--zy-title); }
.sec-note { margin-left: auto; font-size: 24rpx; color: var(--zy-cinnabar); font-weight: 600; }

/* 印章徽章 */
.badge-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12rpx; padding: 26rpx 12rpx; background: var(--zy-card-bg); border-radius: var(--zy-radius-card); box-shadow: var(--zy-shadow-card); }
.badge { display: flex; flex-direction: column; align-items: center; gap: 10rpx; text-align: center; }
.badge-name { font-family: var(--zy-font-display); font-size: 26rpx; font-weight: 700; color: var(--zy-title); }
.badge-desc { font-size: 20rpx; line-height: 1.4; color: var(--zy-muted); }
.badge.locked .badge-name { color: var(--zy-muted); }

/* 宫格 */
.tile-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16rpx; }
.tile { margin: 0; padding: 28rpx 8rpx 22rpx; display: flex; flex-direction: column; align-items: center; gap: 14rpx; background: var(--zy-card-bg); border: 0; border-radius: var(--zy-radius-card); box-shadow: var(--zy-shadow-card); }
.tile:active { transform: scale(.97); background: var(--zy-brand-50); }
.tile-icon, .row-icon { display: flex; align-items: center; justify-content: center; border-radius: 50%; }
.tile-icon { width: 84rpx; height: 84rpx; }
.tile-label { font-size: 26rpx; font-weight: 600; color: var(--zy-title); }
.tone-brand { background: var(--zy-brand-50); }
.tone-brand-light { background: var(--zy-pine-50, #e6f3ee); }
.tone-gold { background: var(--zy-gamboge-50, #fbf1de); }
.tone-care { background: var(--zy-cinnabar-50, #fbecea); }
.tone-neutral { background: var(--zy-bg-sunken); }

/* 列表 */
.menu-list { background: var(--zy-card-bg); border-radius: var(--zy-radius-card); box-shadow: var(--zy-shadow-card); overflow: hidden; }
.menu-row { display: flex; align-items: center; gap: 22rpx; width: 100%; margin: 0; padding: 24rpx 26rpx; background: transparent; border: 0; border-bottom: 2rpx solid var(--zy-line-soft); border-radius: 0; text-align: left; }
.menu-row:last-child { border-bottom: 0; }
.menu-row:active { background: var(--zy-brand-50); }
.row-icon { width: 64rpx; height: 64rpx; flex-shrink: 0; }
.row-label { flex: 1; font-size: 30rpx; color: var(--zy-title); }

/* 边界 */
.boundary-card { margin-top: 44rpx; padding: 0 28rpx 28rpx; background: var(--zy-card-bg); border-radius: var(--zy-radius-card); box-shadow: var(--zy-shadow-card); overflow: hidden; display: flex; flex-direction: column; gap: 12rpx; }
.boundary-band { margin: 0 -28rpx 12rpx; }
.boundary-title { font-family: var(--zy-font-display); font-size: 32rpx; font-weight: 700; color: var(--zy-title); }
.boundary-desc { font-size: 24rpx; line-height: 1.7; color: var(--zy-text); }
.boundary-action { margin: 8rpx 0 0; }

.logout-btn { width: 100%; margin: 32rpx 0 0; min-height: 88rpx; font-size: 28rpx; }
.footnote { display: block; margin-top: 32rpx; font-size: 22rpx; color: var(--zy-muted); text-align: center; }
</style>
