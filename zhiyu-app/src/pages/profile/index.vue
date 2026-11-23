<template>
	<view :class="zyThemeClass" class="page">
		<!-- ① 我是谁：展签式身份区 -->
		<view class="page-head zy-slide-up">
			<text class="page-eyebrow">PERSONAL ARCHIVE · 个人档案</text>
			<view class="identity-row">
				<AccessibleButton class="monogram" aria-label="修改个人资料" @click="goSettings">
					<image v-if="avatarFullUrl && !avatarError" class="avatar-img" :src="avatarFullUrl" mode="aspectFill" alt="我的头像" @error="avatarError = true" />
					<view v-else class="avatar"><text class="avatar-text">{{ avatarChar }}</text></view>
				</AccessibleButton>
				<view class="identity-copy">
					<text class="profile-name">{{ nickname }}</text>
					<text class="profile-signature">{{ signature }}</text>
					<view class="profile-meta">
						<text v-if="roleLabel" class="role-text">{{ roleLabel }}</text>
						<text v-if="school" class="school-text">{{ school }}</text>
					</view>
				</view>
			</view>
		</view>

		<StateBlock
			v-if="profileError"
			type="error"
			compact
			title="信息暂时没有更新"
			:description="profileError"
			action-text="重新加载"
			@action="retryProfile"
		/>

		<!-- ② 我做过什么：旅程三指标（与首页「连续记录」同源：/api/user/stats） -->
		<text class="section-eyebrow zy-slide-up zy-stagger" style="--stagger-i: 0">JOURNEY · 旅程</text>
		<view class="stats-row" role="group" aria-label="我的创作旅程">
			<view class="stat-card zy-slide-up zy-stagger" style="--stagger-i: 1">
				<text class="stat-num">{{ stats.daysHere }}</text>
				<text class="stat-label">来到这里 / 天</text>
			</view>
			<view class="stat-card zy-slide-up zy-stagger" style="--stagger-i: 2">
				<text class="stat-num">{{ stats.totalWorks }}</text>
				<text class="stat-label">作品 / 件</text>
			</view>
			<view class="stat-card zy-slide-up zy-stagger" style="--stagger-i: 3">
				<text class="stat-num">{{ stats.totalMinutes }}</text>
				<text class="stat-label">专注 / 分钟</text>
			</view>
		</view>

		<!-- 徽章横滑：由统计数据推导的成就，未达成的展示进度 -->
		<view class="badge-section zy-slide-up zy-stagger" style="--stagger-i: 3">
			<view class="badge-heading">
				<text class="section-title">一路留下的印记</text>
				<text class="section-note">由你的真实记录生成，不需要额外打卡。</text>
			</view>
			<view class="badge-scroll">
				<view class="badge-track">
					<view v-for="badge in badges" :key="badge.key" class="badge-card" :class="{ locked: !badge.earned }">
						<view class="badge-icon" aria-hidden="true"><text>{{ badge.mark }}</text></view>
						<text class="badge-name">{{ badge.name }}</text>
						<text class="badge-desc">{{ badge.earned ? badge.desc : badge.progress }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 菜单分区 -->
		<view v-for="section in menuSections" :key="section.title" class="menu-section">
			<text class="menu-section-title">{{ section.title }}</text>
			<view class="menu-list">
				<AccessibleButton
					v-for="item in section.items"
					:key="item.action"
					class="menu-item"
					:aria-label="item.label"
					@click="handleMenu(item)"
				>
					<view class="menu-icon-wrap" :class="'tone-' + item.tone" aria-hidden="true">
						<ZyIcon :name="item.icon" :size="30" tone="brand" />
					</view>
					<text class="menu-label">{{ item.label }}</text>
					<ZyIcon name="arrow" :size="30" tone="muted" />
				</AccessibleButton>
			</view>
		</view>

		<!-- ③ 我的边界：隐私与安全 -->
		<view class="boundary-card">
			<view class="boundary-copy">
				<text class="section-title">你的记录，由你做主</text>
				<text class="boundary-desc">在「账号与隐私」中可以撤回授权、导出个人记录，或注销账号。操作即时生效，导出副本归你保存。</text>
			</view>
			<AccessibleButton class="boundary-btn" @click="goPrivacy">管理授权与账号</AccessibleButton>
		</view>

		<AccessibleButton class="logout-btn" aria-label="退出登录" @click="showLogout = true">退出登录</AccessibleButton>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>

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
.page {
	min-height: 100vh;
	padding: var(--zy-space-lg) var(--zy-space-md) 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #ffffff);
}
/* #ifdef MP */
/* 小程序 custom tabBar 不占页面视口，页面自留底部空间（含安全区） */
.page { padding-bottom: calc(112rpx + env(safe-area-inset-bottom)); }
/* #endif */

/* ── 展签式身份区 ── */
.page-head {
	padding: 8rpx 4rpx 36rpx;
	margin-bottom: 8rpx;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
}
.page-eyebrow,
.section-eyebrow {
	display: block;
	font-size: var(--zy-text-micro, 22rpx);
	letter-spacing: 2px;
	color: var(--zy-muted, #6e7b75);
	margin-bottom: 22rpx;
}
.identity-row { display: flex; align-items: flex-start; gap: 28rpx; }
.monogram {
	width: 132rpx;
	height: 160rpx;
	min-height: 160rpx;
	flex-shrink: 0;
	padding: 0;
	margin: 0;
	border-radius: 0;
	background: var(--zy-bg-sunken, #e9efeb);
	overflow: hidden;
}
.monogram::after { border: 0; }
.avatar, .avatar-img {
	width: 100%;
	height: 100%;
	border-radius: 0;
	border: 0;
	box-sizing: border-box;
	display: block;
}
.avatar {
	background: var(--zy-bg-sunken, #e9efeb);
	display: flex;
	align-items: center;
	justify-content: center;
}
.avatar-text {
	font-family: var(--zy-font-display, serif);
	font-size: 68rpx;
	font-weight: 500;
	color: var(--zy-brand-700, #0a403b);
}
.identity-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 10rpx; }
.profile-name {
	font-family: var(--zy-font-display, serif);
	font-size: 64rpx;
	font-weight: 600;
	line-height: 1.25;
	letter-spacing: 1rpx;
	color: var(--zy-title, #111816);
	overflow-wrap: anywhere;
}
.profile-signature { font-size: var(--zy-text-body, 28rpx); color: var(--zy-text, #42524c); overflow-wrap: anywhere; }
.profile-meta { display: flex; align-items: center; gap: 18rpx; flex-wrap: wrap; margin-top: 6rpx; }
.role-text {
	font-size: var(--zy-text-micro, 22rpx);
	letter-spacing: 1px;
	color: var(--zy-accent, #8e4a3c);
}
.school-text { font-size: var(--zy-text-caption, 24rpx); color: var(--zy-muted, #6e7b75); overflow-wrap: anywhere; }

/* ── ② 旅程三指标：一根线分三栏，不做卡片 ── */
.stats-row {
	display: flex;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
	margin-bottom: 40rpx;
}
.stat-card {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 6rpx;
	padding: 24rpx 20rpx 24rpx 0;
	background: transparent;
	border: 0;
}
.stat-card + .stat-card { padding-left: 24rpx; border-left: 1rpx solid var(--zy-border, #d3ded8); }
.stat-num {
	font-size: 56rpx;
	font-weight: 500;
	color: var(--zy-title, #111816);
	font-family: var(--zy-font-display, serif);
	line-height: 1;
	font-variant-numeric: tabular-nums;
}
.stat-label { font-size: var(--zy-text-micro, 22rpx); color: var(--zy-muted, #6e7b75); line-height: 1.5; letter-spacing: 1rpx; }

/* ── 徽章横滑：展签小卡 ── */
.badge-section { margin-bottom: 44rpx; }
.badge-heading { margin-bottom: 20rpx; }
.section-title {
	display: block;
	font-family: var(--zy-font-display, serif);
	font-size: 40rpx;
	font-weight: 500;
	line-height: 1.4;
	color: var(--zy-title, #111816);
}
.section-note { display: block; margin-top: 6rpx; font-size: var(--zy-text-caption, 24rpx); color: var(--zy-muted, #6e7b75); line-height: 1.7; }
.badge-scroll { width: 100%; }
.badge-track { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0; border-top: 1rpx solid var(--zy-border, #d3ded8); border-left: 1rpx solid var(--zy-border, #d3ded8); }
.badge-card {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 8rpx;
	width: auto;
	min-width: 0;
	padding: 20rpx 18rpx 22rpx;
	border-radius: 0;
	border: 0;
	border-right: 1rpx solid var(--zy-border, #d3ded8);
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
	background: transparent;
	box-sizing: border-box;
	white-space: normal;
	text-align: left;
}
.badge-card.locked { background: transparent; }
.badge-icon {
	width: 56rpx;
	height: 56rpx;
	border-radius: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	font-family: var(--zy-font-display, serif);
	font-size: 40rpx;
	font-weight: 500;
	color: var(--zy-brand-700, #0a403b);
	background: transparent;
}
.badge-card.locked .badge-icon { color: var(--zy-muted, #6e7b75); opacity: .55; }
.badge-name { font-size: var(--zy-text-caption, 24rpx); font-weight: 500; color: var(--zy-title, #111816); }
.badge-card.locked .badge-name { color: var(--zy-text, #42524c); }
.badge-desc { font-size: var(--zy-text-micro, 22rpx); color: var(--zy-muted, #6e7b75); line-height: 1.55; }
.badge-card.locked .badge-desc { color: var(--zy-muted, #6e7b75); font-variant-numeric: tabular-nums; }

/* ── 菜单分区 ── */
.menu-section { margin-bottom: 36rpx; }
.menu-section-title {
	display: block;
	margin: 0 0 12rpx;
	font-size: var(--zy-text-micro, 22rpx);
	font-weight: 400;
	letter-spacing: 2px;
	color: var(--zy-muted, #6e7b75);
}
.menu-list {
	overflow: hidden;
	border-radius: 0;
	background: transparent;
	border-top: 1rpx solid var(--zy-border, #d3ded8);
}
.menu-item {
	display: flex;
	align-items: center;
	gap: 20rpx;
	width: 100%;
	padding: 24rpx 4rpx;
	margin: 0;
	background: transparent;
	border-radius: 0;
	line-height: 1.5;
	text-align: left;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}
.menu-item::after { border: 0; }
.menu-item:not(:last-child) { border-bottom: 1rpx solid var(--zy-border, #d3ded8); }
.menu-item:active { background-color: var(--zy-bg-sunken, #e9efeb); }
.menu-icon-wrap {
	width: 44rpx;
	height: 44rpx;
	border-radius: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
	background: transparent;
	border: 1rpx solid var(--zy-border, #d3ded8);
}
.menu-icon-wrap.tone-brand,
.menu-icon-wrap.tone-brand-light,
.menu-icon-wrap.tone-gold,
.menu-icon-wrap.tone-care,
.menu-icon-wrap.tone-neutral { background: transparent; }
.menu-label { flex: 1; min-width: 0; font-size: var(--zy-text-body-lg, 30rpx); color: var(--zy-title, #111816); overflow-wrap: anywhere; }

/* ── ③ 我的边界 ── */
.boundary-card {
	display: flex;
	flex-direction: column;
	gap: 18rpx;
	padding: 24rpx 0 24rpx 24rpx;
	margin: 0 0 36rpx;
	border-left: 4rpx solid var(--zy-care, #a64842);
	background: transparent;
}
.boundary-copy { min-width: 0; }
.boundary-desc { display: block; margin-top: 8rpx; font-size: var(--zy-text-caption, 24rpx); color: var(--zy-muted, #6e7b75); line-height: 1.8; }
.boundary-btn {
	min-height: 44px;
	margin: 0;
	padding: 12rpx 24rpx;
	border-radius: 0;
	background: transparent;
	border: 1rpx solid var(--zy-brand-700, #0a403b);
	color: var(--zy-brand-700, #0a403b);
	font-size: var(--zy-text-caption, 24rpx);
	line-height: 1.5;
	align-self: flex-start;
}
.boundary-btn::after { border: 0; }

/* ── 退出登录 ── */
.logout-btn {
	width: 100%;
	height: 80rpx;
	margin: 0;
	border-radius: 0;
	border: 0;
	background: transparent;
	color: var(--zy-muted, #6e7b75);
	font-size: var(--zy-text-caption, 24rpx);
	line-height: 80rpx;
	letter-spacing: 1px;
}
.logout-btn::after { border: 0; }
.logout-btn:active { color: var(--zy-care, #a64842); }

.footnote { display: block; margin-top: 32rpx; font-size: var(--zy-text-micro, 22rpx); color: var(--zy-muted, #6e7b75); text-align: center; }


button:focus-visible { outline: 3px solid var(--zy-brand-700, var(--zy-brand-700, #1a645e)); outline-offset: 3px; }
</style>
