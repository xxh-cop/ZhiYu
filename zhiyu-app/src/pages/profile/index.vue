<template>
	<view class="page">
		<!-- 用户信息卡片 -->
		<view class="profile-card zy-slide-up">
			<view class="card-accent"></view>
			<view class="profile-body">
				<view class="avatar-ring" @click.stop="goSettings">
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
					<wd-icon name="edit" size="32rpx" color="#2D9B8F" />
				</view>
				<text class="stat-num">{{ stats.days }}</text>
				<text class="stat-label">创作天数</text>
			</view>
			<view class="stat-card zy-slide-up zy-stagger" style="--stagger-i: 1">
				<view class="stat-icon-wrap">
					<wd-icon name="picture" size="32rpx" color="#FFB547" />
				</view>
				<text class="stat-num">{{ stats.works }}</text>
				<text class="stat-label">作品数</text>
			</view>
			<view class="stat-card zy-slide-up zy-stagger" style="--stagger-i: 2">
				<view class="stat-icon-wrap">
					<wd-icon name="chat" size="32rpx" color="#5BC4A8" />
				</view>
				<text class="stat-num">{{ stats.notes }}</text>
				<text class="stat-label">便签数</text>
			</view>
		</view>

		<!-- 快捷入口列表 -->
		<view class="menu-list">
			<view
				v-for="(item, idx) in menuItems"
				:key="idx"
				class="menu-item zy-slide-up zy-stagger"
				:style="{ '--stagger-i': idx }"
				@click="handleMenu(item)"
			>
				<view class="menu-icon-wrap" :style="{ background: item.iconBg }">
					<wd-icon :name="item.icon" size="36rpx" color="#FFFFFF" />
				</view>
				<text class="menu-label">{{ item.label }}</text>
				<wd-icon name="arrow-right" size="32rpx" color="#D1D5DB" />
			</view>
		</view>

		<!-- 退出登录按钮 -->
		<view class="logout-btn zy-slide-up" @click="handleLogout">
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
	{ icon: 'picture', label: '我的作品', action: 'coming', iconBg: 'linear-gradient(135deg, #2D9B8F, #5BC4A8)' },
	{ icon: 'chat', label: '我的对话历史', action: 'coming', iconBg: 'linear-gradient(135deg, #5BC4A8, #8DD9C4)' },
	{ icon: 'edit', label: '我的便签', action: 'notes', iconBg: 'linear-gradient(135deg, #FFB547, #FFCB6E)' },
	{ icon: 'group', label: '我的小组', action: 'coming', iconBg: 'linear-gradient(135deg, #FF6B6B, #FF9B9B)' },
	{ icon: 'chat1', label: '非遗问答', action: 'rag', iconBg: 'linear-gradient(135deg, #2D9B8F, #5BC4A8)' },
	{ icon: 'setting', label: '个人设置', action: 'settings', iconBg: 'linear-gradient(135deg, #2D9B8F, #5BC4A8)' },
	{ icon: 'setting1', label: '隐私设置', action: 'privacy', iconBg: 'linear-gradient(135deg, #6B7280, #9CA3AF)' },
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
	days: 0,
	works: 0,
	notes: 0,
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
	} catch (e) {
	} finally {
		loadingMe.value = false
	}
}

async function loadStats() {
	try {
		const data = await request({ url: '/api/user/stats', method: 'GET' })
		if (data) {
			stats.value.days = data.daysSinceRegister || 0
			stats.value.works = data.workCount || 0
			stats.value.notes = data.noteCount || 0
		}
	} catch (e) {
		// 降级为 0
		stats.value.days = stats.value.days || 0
		stats.value.works = 0
		stats.value.notes = 0
	}
}

function handleMenu(item) {
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
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 用户信息卡片 ── */
.profile-card {
	overflow: hidden;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: var(--zy-shadow-sm);
	margin-bottom: 24rpx;
}

.card-accent {
	height: 8rpx;
	background: linear-gradient(90deg, var(--zy-primary, #2D9B8F) 0%, var(--zy-primary-light, #5BC4A8) 100%);
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
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
	flex-shrink: 0;
}

.avatar {
	width: 100%;
	height: 100%;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F) 0%, var(--zy-primary-light, #5BC4A8) 100%);
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
	color: var(--zy-title, #1F2937);
}

.profile-signature {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
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
	background-color: var(--zy-bg-tint-warm, #FFF8EE);
}

.role-text {
	font-size: 22rpx;
	color: var(--zy-accent, #FFB547);
}

.school-text {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
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
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	margin-bottom: 4rpx;
}

.stat-num {
	font-size: 44rpx;
	font-weight: 700;
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
	line-height: 1;
}

.stat-label {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 快捷入口列表 ── */
.menu-list {
	overflow: hidden;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
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
	border-bottom: 1rpx solid var(--zy-border, #E5E7EB);
}

.menu-item:active {
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
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
	color: var(--zy-title, #1F2937);
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
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
