<template>
	<view class="page zy-page-enter">
		<!-- 用户信息卡片 -->
		<view class="profile-card zy-card">
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

		<!-- 快捷入口列表 -->
		<view class="menu-list zy-card">
			<view class="menu-item zy-clickable" @click="toastComing">
				<text class="menu-icon">🎨</text>
				<text class="menu-label">我的作品</text>
				<text class="menu-arrow">›</text>
			</view>
			<view class="menu-divider"></view>
			<view class="menu-item zy-clickable" @click="toastComing">
				<text class="menu-icon">💬</text>
				<text class="menu-label">我的对话历史</text>
				<text class="menu-arrow">›</text>
			</view>
			<view class="menu-divider"></view>
			<view class="menu-item zy-clickable" @click="toastComing">
				<text class="menu-icon">👥</text>
				<text class="menu-label">我的小组</text>
				<text class="menu-arrow">›</text>
			</view>
		</view>

		<!-- 退出登录按钮 -->
		<view class="logout-btn zy-btn-outline" @click="handleLogout">
			<text class="logout-text">退出登录</text>
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

const ROLE_LABEL = {
	0: '学生',
	1: '传承人',
	2: '咨询师',
	3: '管理员',
}

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
}

/* 用户信息卡片 */
.profile-card {
	display: flex;
	align-items: center;
	gap: 24rpx;
	padding: 36rpx 32rpx;
	margin-bottom: 32rpx;
}

.avatar {
	width: 100rpx;
	height: 100rpx;
	border-radius: 50%;
	background-color: rgba(200, 155, 110, 0.15);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.avatar-text {
	font-size: 44rpx;
	font-weight: 600;
	color: #c89b6e;
}

.profile-info {
	flex: 1;
	display: flex;
	flex-direction: column;
	gap: 12rpx;
}

.profile-name {
	font-size: 38rpx;
	font-weight: 600;
	color: #3d3a34;
}

.profile-meta {
	display: flex;
	align-items: center;
	gap: 16rpx;
}

.role-badge {
	padding: 4rpx 16rpx;
	border-radius: 999rpx;
	background-color: rgba(200, 155, 110, 0.12);
}

.role-text {
	font-size: 22rpx;
	color: #c89b6e;
}

.school-text {
	font-size: 24rpx;
	color: #9c8f7d;
}

/* 快捷入口列表 */
.menu-list {
	overflow: hidden;
}

.menu-item {
	display: flex;
	align-items: center;
	gap: 20rpx;
	padding: 28rpx 32rpx;
}

.menu-icon {
	font-size: 36rpx;
}

.menu-label {
	flex: 1;
	font-size: 30rpx;
	color: #3d3a34;
}

.menu-arrow {
	font-size: 36rpx;
	color: #cdbfa9;
}

.menu-divider {
	height: 1px;
	background-color: rgba(139, 111, 71, 0.06);
	margin: 0 32rpx;
}

/* 退出登录 */
.logout-btn {
	height: 88rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-top: 40rpx;
}

.logout-text {
	font-size: 30rpx;
	color: #b85450;
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 24rpx;
	color: #bdb2a1;
	text-align: center;
}
</style>
