<template>
	<view class="page">
		<!-- 用户信息卡片 -->
		<view class="profile-card zy-slide-up">
			<view class="card-accent"></view>
			<view class="profile-body">
				<view class="avatar-ring" role="button" tabindex="0" aria-label="修改个人资料" @click.stop="goSettings" @keydown.enter.prevent="goSettings" @keydown.space.prevent="goSettings">
					<image
						v-if="avatarFullUrl"
						class="avatar-img"
						:src="avatarFullUrl"
						mode="aspectFill"
					/>
					<view v-else class="avatar">
						<text class="avatar-text">{{ avatarChar }}</text>
					</view>
				</view>
				<view class="profile-info">
					<text class="profile-name">{{ nickname }}</text>
					<text class="profile-signature">{{ signature }}</text>
					<view class="profile-meta">
						<view v-if="roleLabel" class="role-badge">
							<text class="role-text">{{ roleLabel }}</text>
						</view>
						<text v-if="school" class="school-text">{{ school }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 数据可视化行 -->
		<view class="stats-row">
			<view class="stat-card zy-slide-up zy-stagger" style="--stagger-i: 0">
				<view class="stat-icon-wrap">
					<wd-icon name="edit" size="32rpx" color="#2f7566" />
				</view>
				<text class="stat-num">{{ stats.days }}</text>
				<text class="stat-label">创作天数</text>
			</view>
			<view class="stat-card zy-slide-up zy-stagger" style="--stagger-i: 1">
				<view class="stat-icon-wrap">
					<wd-icon name="picture" size="32rpx" color="#bb914d" />
				</view>
				<text class="stat-num">{{ stats.works }}</text>
				<text class="stat-label">作品数</text>
			</view>
			<view class="stat-card zy-slide-up zy-stagger" style="--stagger-i: 2">
				<view class="stat-icon-wrap">
					<wd-icon name="chat" size="32rpx" color="#82ad9b" />
				</view>
				<text class="stat-num">{{ stats.notes }}</text>
				<text class="stat-label">便签数</text>
			</view>
		</view>

		<view v-if="profileError" class="profile-error" role="alert">
			<text>{{ profileError }}</text>
			<button @click="retryProfile">重新加载</button>
		</view>

		<!-- 快捷入口列表 -->
		<view class="menu-list">
			<view
				v-for="(item, idx) in menuItems"
				:key="idx"
				class="menu-item zy-slide-up zy-stagger"
				:style="{ '--stagger-i': idx }"
				role="button"
				tabindex="0"
				:aria-label="item.label"
				@click="handleMenu(item)"
				@keydown.enter.prevent="handleMenu(item)"
				@keydown.space.prevent="handleMenu(item)"
			>
				<view class="menu-icon-wrap" :style="{ background: item.iconBg }">
					<wd-icon :name="item.icon" size="36rpx" color="#FFFFFF" />
				</view>
				<text class="menu-label">{{ item.label }}</text>
				<wd-icon name="arrow-right" size="32rpx" color="#D1D5DB" />
			</view>
		</view>

		<!-- 退出登录按钮 -->
		<view class="logout-btn zy-slide-up" role="button" tabindex="0" aria-label="退出登录" @click="handleLogout" @keydown.enter.prevent="handleLogout" @keydown.space.prevent="handleLogout">
			<text class="logout-btn-text">退出登录</text>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { useUserStore } from '../../stores/user'
import { API_BASE_URL } from '../../utils/config.js'

const ROLE_LABEL = {
	0: '学生',
	1: '传承人',
	2: '咨询师',
	3: '管理员',
}

const menuItems = [
 { icon: 'star', label: '我的收藏', action: 'favorites', iconBg: '#bb914d' },
 { icon: 'picture', label: '我的作品', action: 'works', iconBg: '#2f7566' },
 { icon: 'user', label: '校园身份', action: 'campus', iconBg: '#2f7566' },
 { icon: 'chat', label: '倾听记录', action: 'history', iconBg: '#718478' },
	{ icon: 'picture', label: '成长旅程', action: 'journey', iconBg: 'linear-gradient(135deg, #2f7566, #82ad9b)' },
	{ icon: 'chat', label: '自我了解', action: 'assessment', iconBg: 'linear-gradient(135deg, #82ad9b, #8DD9C4)' },
	{ icon: 'edit', label: '我的便签', action: 'notes', iconBg: 'linear-gradient(135deg, #bb914d, #FFCB6E)' },
	{ icon: 'group', label: '活动与预约', action: 'programs', iconBg: 'linear-gradient(135deg, #FF6B6B, #FF9B9B)' },
	{ icon: 'chat1', label: '非遗问答', action: 'rag', iconBg: 'linear-gradient(135deg, #2f7566, #82ad9b)' },
	{ icon: 'setting', label: '个人设置', action: 'settings', iconBg: 'linear-gradient(135deg, #2f7566, #82ad9b)' },
	{ icon: 'setting1', label: '隐私设置', action: 'privacy', iconBg: 'linear-gradient(135deg, #6B7280, #718478)' },
	{ icon: 'phone', label: '心理援助', action: 'crisis', iconBg: 'linear-gradient(135deg, #FF6B6B, #FF8B8B)' },
]

const userStore = useUserStore()
const loadingMe = ref(false)

const nickname = computed(
	() => (userStore.userInfo && userStore.userInfo.nickname) || '同学'
)

const school = computed(
	() => (userStore.userInfo && userStore.userInfo.school) || ''
)

const roleLabel = computed(() => {
	const role = userStore.userInfo ? userStore.userInfo.role : null
	if (role === null || role === undefined) return ''
	return ROLE_LABEL[role] || ''
})

const avatarChar = computed(() => {
	const name = nickname.value
	return name ? name.charAt(0) : '织'
})

const avatarFullUrl = computed(() => {
	const url = userStore.userInfo ? userStore.userInfo.avatarUrl : ''
	if (!url) return ''
	return url.startsWith('http') ? url : `${API_BASE_URL}${url}`
})

function goSettings() {
	uni.navigateTo({ url: '/pages/settings/index' })
}

// 动态签名（根据时段变化）
const signature = computed(() => {
	const hour = new Date().getHours()
	if (hour < 6) return '夜深了，先把自己安顿好'
	if (hour < 12) return '今天也要好好爱自己'
	if (hour < 18) return '给自己留一点安静的时间'
	return '把今天的疲惫放一放'
})

// 统计数据
const stats = ref({
	days: '—',
	works: '—',
	notes: '—',
})
const profileError = ref('')

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
		const [data, journey] = await Promise.all([request({ url: '/api/user/stats', method: 'GET' }), request({ url: '/api/growth/journey' })])
		if (data) {
			stats.value.days = journey.days
			stats.value.works = data.workCount || 0
			stats.value.notes = data.noteCount || 0
			profileError.value = ''
		}
	} catch (e) {
		stats.value = { days: '—', works: '—', notes: '—' }
		profileError.value = '统计暂时无法读取，请重试'
	}
}

function retryProfile() {
	profileError.value = ''
	loadCurrentUser()
	loadStats()
}

function handleMenu(item) {
 if(item.action==='works'){uni.navigateTo({url:'/pages/work/mine'});return}
 if(item.action==='favorites'){uni.navigateTo({url:'/pages/work/favorites'});return}
 if(item.action==='campus'){uni.navigateTo({url:'/pages/campus/index'});return}
 if(item.action==='history'){uni.navigateTo({url:'/pages/emotion/history'});return}
 if (['journey','assessment','programs'].includes(item.action)) { uni.navigateTo({ url: '/pages/'+item.action+'/index' }); return }
	if (item.action === 'crisis') {
		uni.navigateTo({ url: '/pages/crisis/index' })
	} else if (item.action === 'privacy') {
		uni.navigateTo({ url: '/pages/privacy/settings' })
	} else if (item.action === 'settings') {
		uni.navigateTo({ url: '/pages/settings/index' })
	} else if (item.action === 'notes') {
		uni.navigateTo({ url: '/pages/emotion/notes' })
	} else if (item.action === 'rag') {
		uni.navigateTo({ url: '/pages/ai/rag-ask' })
	} else {
		uni.showToast({ title: '即将上线', icon: 'none' })
	}
}

function handleLogout() {
	uni.showModal({
		title: '确认退出',
		content: '确定要退出登录吗？',
		confirmText: '退出',
		confirmColor: '#b85450',
		success: (res) => {
			if (res.confirm) {
				userStore.logout()
			}
		},
	})
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #f5f7f2);
}

/* ── 用户信息卡片 ── */
.profile-card {
	overflow: hidden;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
	margin-bottom: 24rpx;
}

.card-accent {
	height: 8rpx;
	background: linear-gradient(90deg, var(--zy-primary, #2f7566) 0%, var(--zy-primary-light, #82ad9b) 100%);
}

.profile-body {
	display: flex;
	align-items: center;
	gap: 28rpx;
	padding: 36rpx 32rpx;
}

.avatar-ring {
	width: 172rpx;
	height: 172rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	padding: 6rpx;
	background: linear-gradient(135deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
	flex-shrink: 0;
}

.avatar {
	width: 100%;
	height: 100%;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2f7566) 0%, var(--zy-primary-light, #82ad9b) 100%);
	display: flex;
	align-items: center;
	justify-content: center;
	border: 4rpx solid var(--zy-card-bg, #FFFFFF);
	box-sizing: border-box;
}

.avatar-img {
	width: 100%;
	height: 100%;
	border-radius: var(--zy-radius-full, 9999rpx);
	border: 4rpx solid var(--zy-card-bg, #FFFFFF);
	box-sizing: border-box;
}

.avatar-text {
	font-size: 56rpx;
	font-weight: 600;
	color: #FFFFFF;
}

.profile-info {
	flex: 1;
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.profile-name {
	font-size: 36rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
}

.profile-signature {
	font-size: 24rpx;
	color: var(--zy-aid, #718478);
}

.profile-meta {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin-top: 4rpx;
}

.role-badge {
	padding: 4rpx 16rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-warm, #f5edde);
}

.role-text {
	font-size: 22rpx;
	color: var(--zy-accent, #bb914d);
}

.school-text {
	font-size: 26rpx;
	color: var(--zy-aid, #718478);
}

/* ── 数据可视化行 ── */
.stats-row {
	display: flex;
	gap: 16rpx;
	margin-bottom: 24rpx;
}

.stat-card {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 8rpx;
	padding: 28rpx 12rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	box-shadow: var(--zy-shadow-sm);
}

.stat-icon-wrap {
	width: 56rpx;
	height: 56rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	margin-bottom: 4rpx;
}

.stat-num {
	font-size: 44rpx;
	font-weight: 700;
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
	line-height: 1;
}

.stat-label {
	font-size: 22rpx;
	color: var(--zy-aid, #718478);
}

/* ── 快捷入口列表 ── */
.menu-list {
	overflow: hidden;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
	margin-bottom: 32rpx;
}

.menu-item {
	display: flex;
	align-items: center;
	gap: 20rpx;
	padding: 28rpx 32rpx;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.menu-item:not(:last-child) {
	border-bottom: 1rpx solid var(--zy-border, #dfe7dc);
}

.menu-item:active {
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.menu-icon-wrap {
	width: 64rpx;
	height: 64rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.menu-label {
	flex: 1;
	font-size: 30rpx;
	color: var(--zy-title, #233d34);
}

/* ── 退出登录 ── */
.logout-btn {
	height: 88rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	border: 1rpx solid rgba(255, 107, 107, 0.3);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.logout-btn:active {
	background-color: rgba(255, 107, 107, 0.05);
}

.logout-btn-text {
	font-size: 30rpx;
	color: var(--zy-danger, #FF6B6B);
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #718478);
	text-align: center;
}
</style>

<style scoped>
.avatar-ring:focus-visible,
.menu-item:focus-visible,
.logout-btn:focus-visible {
	outline: 3px solid var(--zy-primary, #2f7566);
	outline-offset: 3px;
}
.profile-info { min-width: 0; }
.profile-name, .profile-signature { overflow-wrap: anywhere; }
.profile-error {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	margin-bottom: 20rpx;
	padding: 20rpx 24rpx;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-md, 24rpx);
	background: var(--zy-card-bg, #fff);
	color: var(--zy-content, #586e62);
	font-size: 23rpx;
	line-height: 1.7;
}
.profile-error button {
	flex-shrink: 0;
	min-height: 64rpx;
	padding: 0 22rpx;
	border: 1rpx solid var(--zy-primary, #2f7566);
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-primary, #2f7566);
	font-size: 23rpx;
	line-height: 2.5;
}
.profile-error button::after { border: 0; }
</style>
