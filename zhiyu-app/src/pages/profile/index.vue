<template>
	<view class="page">
		<!-- 用户信息卡片 -->
		<view class="profile-card">
			<view class="card-accent"></view>
			<view class="profile-body">
				<view class="avatar">
					<text class="avatar-text">{{ avatarChar }}</text>
				</view>
				<view class="profile-info">
					<text class="profile-name">{{ nickname }}</text>
					<view class="profile-meta">
						<view v-if="roleLabel" class="role-badge">
							<text class="role-text">{{ roleLabel }}</text>
						</view>
						<text v-if="school" class="school-text">{{ school }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 快捷入口列表 -->
		<view class="menu-list">
			<view
				v-for="(item, idx) in menuItems"
				:key="idx"
				class="menu-item"
				:style="{ '--stagger-i': idx }"
				@click="toastComing"
			>
				<view class="menu-icon-wrap">
					<wd-icon :name="item.icon" size="36rpx" color="#2D9B8F" />
				</view>
				<text class="menu-label">{{ item.label }}</text>
				<wd-icon name="arrow-right" size="32rpx" color="#D1D5DB" />
			</view>
		</view>

		<!-- 退出登录按钮 -->
		<wd-button
			class="logout-btn"
			type="error"
			plain
			block
			@click="handleLogout"
		>退出登录</wd-button>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { useUserStore } from '../../stores/user'

const ROLE_LABEL = {
	0: '学生',
	1: '传承人',
	2: '咨询师',
	3: '管理员',
}

const menuItems = [
	{ icon: 'picture', label: '我的作品' },
	{ icon: 'chat', label: '我的对话历史' },
	{ icon: 'group', label: '我的小组' },
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

onShow(() => {
	if (!userStore.isLogin) {
		uni.reLaunch({ url: '/pages/login/index' })
		return
	}
	loadCurrentUser()
})

async function loadCurrentUser() {
	if (loadingMe.value) return
	loadingMe.value = true
	try {
		const data = await request({ url: '/api/auth/me', method: 'GET' })
		userStore.setUserInfo(data)
	} catch (e) {
		// code=1002 已由 utils/request.js 处理
	} finally {
		loadingMe.value = false
	}
}

function toastComing() {
	uni.showToast({ title: '即将上线', icon: 'none' })
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
	border-radius: 32rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: 0 4rpx 20rpx rgba(31, 41, 55, 0.05);
	margin-bottom: 32rpx;
	animation: fadeInUp 0.35s ease;
}

@keyframes fadeInUp {
	from { opacity: 0; transform: translateY(16rpx); }
	to { opacity: 1; transform: translateY(0); }
}

.card-accent {
	height: 8rpx;
	background: linear-gradient(90deg, #2D9B8F 0%, #5BC4A8 100%);
}

.profile-body {
	display: flex;
	align-items: center;
	gap: 24rpx;
	padding: 36rpx 32rpx;
}

.avatar {
	width: 120rpx;
	height: 120rpx;
	border-radius: 50%;
	background: linear-gradient(135deg, #2D9B8F 0%, #5BC4A8 100%);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.avatar-text {
	font-size: 48rpx;
	font-weight: 600;
	color: #FFFFFF;
}

.profile-info {
	flex: 1;
	display: flex;
	flex-direction: column;
	gap: 12rpx;
}

.profile-name {
	font-size: 34rpx;
	font-weight: 600;
	color: var(--zy-title, #1F2937);
}

.profile-meta {
	display: flex;
	align-items: center;
	gap: 16rpx;
}

.role-badge {
	padding: 4rpx 16rpx;
	border-radius: 999rpx;
	background-color: var(--zy-bg-tint-warm, #FFF8EE);
}

.role-text {
	font-size: 22rpx;
	color: #FFB547;
}

.school-text {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 快捷入口列表 ── */
.menu-list {
	overflow: hidden;
	border-radius: 32rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: 0 4rpx 20rpx rgba(31, 41, 55, 0.05);
	margin-bottom: 40rpx;
}

.menu-item {
	display: flex;
	align-items: center;
	gap: 20rpx;
	padding: 28rpx 32rpx;
	transition: background-color 0.15s ease;
	animation: menuEnter 0.35s ease both;
	animation-delay: calc(var(--stagger-i, 0) * 80ms);
}

.menu-item:not(:last-child) {
	border-bottom: 1rpx solid var(--zy-border, #E5E7EB);
}

.menu-item:active {
	background-color: var(--zy-bg, #F8F9FB);
}

@keyframes menuEnter {
	from { opacity: 0; transform: translateY(12rpx); }
	to { opacity: 1; transform: translateY(0); }
}

.menu-icon-wrap {
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
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
	margin-top: 8rpx;
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
