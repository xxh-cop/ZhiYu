<template>
	<view :class="zyThemeClass" class="page">
		<!-- ① 我是谁：身份区 -->
		<view class="profile-card zy-slide-up">
			<view class="card-accent" aria-hidden="true" />
			<view class="profile-body">
				<AccessibleButton class="avatar-ring" aria-label="修改个人资料" @click="goSettings">
					<image v-if="avatarFullUrl" class="avatar-img" :src="avatarFullUrl" mode="aspectFill" alt="我的头像" />
					<view v-else class="avatar"><text class="avatar-text">{{ avatarChar }}</text></view>
				</AccessibleButton>
				<view class="profile-info">
					<text class="profile-name">{{ nickname }}</text>
					<text class="profile-signature">{{ signature }}</text>
					<view class="profile-meta">
						<view v-if="roleLabel" class="role-badge"><text class="role-text">{{ roleLabel }}</text></view>
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
		<view class="stats-row" role="group" aria-label="我的创作旅程">
			<view class="stat-card zy-slide-up zy-stagger" style="--stagger-i: 0">
				<text class="stat-num">{{ stats.daysHere }}</text>
				<text class="stat-label">来到这里（天）</text>
			</view>
			<view class="stat-card zy-slide-up zy-stagger" style="--stagger-i: 1">
				<text class="stat-num">{{ stats.totalWorks }}</text>
				<text class="stat-label">作品数</text>
			</view>
			<view class="stat-card zy-slide-up zy-stagger" style="--stagger-i: 2">
				<text class="stat-num">{{ stats.totalMinutes }}</text>
				<text class="stat-label">专注时长（分钟）</text>
			</view>
		</view>

		<!-- 徽章横滑：由统计数据推导的成就，未达成的展示进度 -->
		<view class="badge-section zy-slide-up zy-stagger" style="--stagger-i: 3">
			<view class="badge-heading">
				<text class="section-title">一路留下的印记</text>
				<text class="section-note">由你的真实记录生成，不需要额外打卡。</text>
			</view>
			<scroll-view scroll-x class="badge-scroll" :show-scrollbar="false">
				<view class="badge-track">
					<view v-for="badge in badges" :key="badge.key" class="badge-card" :class="{ locked: !badge.earned }">
						<view class="badge-icon" aria-hidden="true"><text>{{ badge.mark }}</text></view>
						<text class="badge-name">{{ badge.name }}</text>
						<text class="badge-desc">{{ badge.earned ? badge.desc : badge.progress }}</text>
					</view>
				</view>
			</scroll-view>
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
						<wd-icon :name="item.icon" size="36rpx" color="#FFFFFF" />
					</view>
					<text class="menu-label">{{ item.label }}</text>
					<wd-icon name="arrow-right" size="32rpx" color="#8fa89c" />
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
			{ icon: 'chat', label: '倾听记录', action: 'history', tone: 'brand-light' },
			{ icon: 'edit', label: '我的便签', action: 'notes', tone: 'gold' },
			{ icon: 'picture', label: '成长旅程', action: 'journey', tone: 'brand' },
			{ icon: 'chat', label: '自我了解', action: 'assessment', tone: 'brand-light' },
		],
	},
	{
		title: '我的参与',
		items: [
			{ icon: 'group', label: '活动与预约', action: 'programs', tone: 'brand' },
			{ icon: 'chat1', label: '非遗问答', action: 'rag', tone: 'brand-light' },
			{ icon: 'user', label: '校园身份', action: 'campus', tone: 'brand' },
		],
	},
	{
		title: '支持与设置',
		items: [
			{ icon: 'phone', label: '心理援助', action: 'crisis', tone: 'care' },
			{ icon: 'setting', label: '个人设置', action: 'settings', tone: 'brand' },
			{ icon: 'setting1', label: '隐私设置', action: 'privacy', tone: 'neutral' },
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
	if (['journey', 'assessment', 'programs'].includes(item.action)) { uni.navigateTo({ url: '/pages/' + item.action + '/index' }); return }
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
	padding: 32rpx 24rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #ffffff);
}

/* ── ① 身份区 ── */
.profile-card {
	overflow: hidden;
	border-radius: var(--zy-radius-card, 32rpx);
	background-color: var(--zy-card-bg, #fff);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
	margin-bottom: 24rpx;
}
.card-accent {
	height: 8rpx;
	background: linear-gradient(90deg, var(--zy-brand-600, #217e76) 0%, var(--zy-brand-300, #5bc4a8) 100%);
}
.profile-body { display: flex; align-items: center; gap: 28rpx; padding: 36rpx 32rpx; }
.avatar-ring {
	width: 172rpx;
	height: 172rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	padding: 6rpx;
	margin: 0;
	background: linear-gradient(135deg, var(--zy-brand-600, #217e76), var(--zy-brand-300, #5bc4a8));
	flex-shrink: 0;
	line-height: 1;
}
.avatar-ring::after { border: 0; }
.avatar, .avatar-img {
	width: 100%;
	height: 100%;
	border-radius: var(--zy-radius-full, 9999rpx);
	border: 4rpx solid var(--zy-card-bg, #fff);
	box-sizing: border-box;
}
.avatar {
	background: linear-gradient(135deg, var(--zy-brand-600, #217e76) 0%, var(--zy-brand-300, #5bc4a8) 100%);
	display: flex;
	align-items: center;
	justify-content: center;
}
.avatar-text { font-size: 56rpx; font-weight: 600; color: #fff; }
.profile-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8rpx; }
.profile-name {
	font-size: 36rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
	overflow-wrap: anywhere;
}
.profile-signature { font-size: 24rpx; color: var(--zy-text, #4a5f54); overflow-wrap: anywhere; }
.profile-meta { display: flex; align-items: center; gap: 16rpx; margin-top: 4rpx; flex-wrap: wrap; }
.role-badge { padding: 4rpx 16rpx; border-radius: var(--zy-radius-full, 9999rpx); background-color: var(--zy-bg-tint-warm, #f7efd9); }
.role-text { font-size: 22rpx; color: var(--zy-accent, #8a6d1f); }
.school-text { font-size: 26rpx; color: var(--zy-text, #4a5f54); overflow-wrap: anywhere; }

/* ── ② 旅程三指标 ── */
.stats-row { display: flex; gap: 16rpx; margin-bottom: 24rpx; }
.stat-card {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 10rpx;
	padding: 28rpx 12rpx;
	border-radius: var(--zy-radius-card, 32rpx);
	background-color: var(--zy-card-bg, #fff);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
}
.stat-num {
	font-size: 44rpx;
	font-weight: 700;
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
	line-height: 1;
	font-variant-numeric: tabular-nums;
}
.stat-label { font-size: 22rpx; color: var(--zy-text, #4a5f54); text-align: center; line-height: 1.5; }

/* ── 徽章横滑 ── */
.badge-section {
	margin-bottom: 32rpx;
	padding: 28rpx;
	border-radius: var(--zy-radius-card, 32rpx);
	background-color: var(--zy-card-bg, #fff);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
}
.badge-heading { margin-bottom: 20rpx; }
.section-title { display: block; font-size: 30rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.section-note { display: block; margin-top: 8rpx; font-size: 22rpx; color: var(--zy-text, #4a5f54); line-height: 1.6; }
.badge-scroll { white-space: nowrap; }
.badge-track { display: inline-flex; gap: 16rpx; padding: 4rpx; }
.badge-card {
	display: inline-flex;
	flex-direction: column;
	align-items: center;
	gap: 8rpx;
	width: 180rpx;
	padding: 24rpx 12rpx;
	border-radius: var(--zy-radius-md, 24rpx);
	background: var(--zy-bg-tint-warm, #f7efd9);
	box-sizing: border-box;
	white-space: normal;
	text-align: center;
}
.badge-card.locked { background: var(--zy-bg, #ffffff); }
.badge-icon {
	width: 72rpx;
	height: 72rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 36rpx;
	font-weight: 700;
	color: var(--zy-gold, #d9a441);
	background: var(--zy-card-bg, #fff);
	box-shadow: var(--zy-shadow-xs);
}
.badge-card.locked .badge-icon { color: var(--zy-text-secondary, #4a5f54); opacity: .55; }
.badge-name { font-size: 24rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.badge-card.locked .badge-name { color: var(--zy-text, #4a5f54); }
.badge-desc { font-size: 20rpx; color: var(--zy-accent, #8a6d1f); line-height: 1.5; }
.badge-card.locked .badge-desc { color: var(--zy-text, #4a5f54); font-variant-numeric: tabular-nums; }

/* ── 菜单分区 ── */
.menu-section { margin-bottom: 24rpx; }
.menu-section-title {
	display: block;
	margin: 0 8rpx 14rpx;
	font-size: 24rpx;
	font-weight: 600;
	color: var(--zy-text, #4a5f54);
}
.menu-list {
	overflow: hidden;
	border-radius: var(--zy-radius-card, 32rpx);
	background-color: var(--zy-card-bg, #fff);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
}
.menu-item {
	display: flex;
	align-items: center;
	gap: 20rpx;
	width: 100%;
	padding: 24rpx 32rpx;
	margin: 0;
	background: transparent;
	border-radius: 0;
	line-height: 1.5;
	text-align: left;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}
.menu-item::after { border: 0; }
.menu-item:not(:last-child) { border-bottom: 1rpx solid var(--zy-border, #dfe7dc); }
.menu-item:active { background-color: var(--zy-bg-tint-mint, #e8efe4); }
.menu-icon-wrap {
	width: 64rpx;
	height: 64rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}
.menu-icon-wrap.tone-brand { background: var(--zy-brand-600, #217e76); }
.menu-icon-wrap.tone-brand-light { background: var(--zy-brand-500, #2d9b8f); }
.menu-icon-wrap.tone-gold { background: var(--zy-accent, #8a6d1f); }
.menu-icon-wrap.tone-care { background: var(--zy-care, #a64842); }
.menu-icon-wrap.tone-neutral { background: var(--zy-text, #4a5f54); }
.menu-label { flex: 1; min-width: 0; font-size: 30rpx; color: var(--zy-title, #233d34); overflow-wrap: anywhere; }

/* ── ③ 我的边界 ── */
.boundary-card {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
	padding: 28rpx;
	margin-bottom: 32rpx;
	border-radius: var(--zy-radius-card, 32rpx);
	background: linear-gradient(135deg, var(--zy-bg-tint-mint, #e8efe4), var(--zy-brand-50, #eaf4f1));
	border: 1rpx solid var(--zy-border, #dfe7dc);
}
.boundary-copy { min-width: 0; }
.boundary-desc { display: block; margin-top: 10rpx; font-size: 24rpx; color: var(--zy-text, #4a5f54); line-height: 1.8; }
.boundary-btn {
	min-height: 44px;
	margin: 0;
	padding: 16rpx 32rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-brand-600, #217e76);
	color: #fff;
	font-size: 26rpx;
	line-height: 1.5;
}
.boundary-btn::after { border: 0; }

/* ── 退出登录 ── */
.logout-btn {
	width: 100%;
	height: 88rpx;
	margin: 0;
	border-radius: var(--zy-radius-full, 9999rpx);
	border: 1rpx solid rgba(166, 72, 66, 0.35);
	background: transparent;
	color: var(--zy-care, #a64842);
	font-size: 30rpx;
	line-height: 88rpx;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}
.logout-btn::after { border: 0; }
.logout-btn:active { background-color: rgba(166, 72, 66, 0.06); }

.footnote { display: block; margin-top: 48rpx; font-size: 22rpx; color: var(--zy-text, #4a5f54); text-align: center; }

button:focus-visible { outline: 3px solid var(--zy-brand-700, #1a645e); outline-offset: 3px; }
</style>
