<template>
	<view class="page">
		<view class="greeting">
			<text class="hello">你好，{{ nickname }}</text>
			<text class="welcome">{{ welcome }}</text>
			<text v-if="roleLabel" class="role">{{ roleLabel }}</text>
		</view>

		<!-- 功能入口网格 -->
		<view class="grid">
			<view class="grid-item" @click="goEmotion">
				<view class="grid-icon">
					<text class="icon-emoji">💬</text>
				</view>
				<text class="grid-label">情绪倾听</text>
			</view>

			<view class="grid-item" @click="goRecommend">
				<view class="grid-icon">
					<text class="icon-emoji">🧶</text>
				</view>
				<text class="grid-label">疗愈推荐</text>
			</view>

			<view class="grid-item" @click="toastComing">
				<view class="grid-icon">
					<text class="icon-emoji">✂️</text>
				</view>
				<text class="grid-label">创作引导</text>
			</view>

			<view class="grid-item" @click="goWorkList">
				<view class="grid-icon">
					<text class="icon-emoji">❤️</text>
				</view>
				<text class="grid-label">作品社区</text>
			</view>

			<view class="grid-item" @click="goGroupList">
				<view class="grid-icon">
					<text class="icon-emoji">👥</text>
				</view>
				<text class="grid-label">互助小组</text>
			</view>
		</view>

		<!-- 我的 -->
		<view class="card profile-card" @click="toastComing">
			<text class="card-title">我的</text>
			<text class="card-hint">个人信息与设置</text>
		</view>

		<button class="logout" @click="handleLogout">退出登录</button>

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

const welcome = computed(() => {
	const hour = new Date().getHours()
	if (hour < 6) return '夜深了，先把自己安顿好'
	if (hour < 12) return '早上好，今天也慢慢来'
	if (hour < 18) return '下午好，给自己留一点安静的时间'
	return '晚上好，把今天的疲惫放一放'
})

const roleLabel = computed(() => {
	const role = userStore.userInfo ? userStore.userInfo.role : null
	if (role === null || role === undefined) return ''
	return ROLE_LABEL[role] || ''
})

onShow(() => {
	// 路由守卫：未登录直接回登录页
	if (!userStore.isLogin) {
		uni.reLaunch({ url: '/pages/login/index' })
		return
	}
	// 每次进入都向后端确认一次身份
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

function goEmotion() {
	uni.navigateTo({ url: '/pages/emotion/chat' })
}

function goRecommend() {
	uni.navigateTo({ url: '/pages/craft/recommend' })
}

function goWorkList() {
	uni.navigateTo({ url: '/pages/work/list' })
}

function goGroupList() {
	uni.navigateTo({ url: '/pages/group/list' })
}

function toastComing() {
	uni.showToast({ title: '即将上线', icon: 'none' })
}

function handleLogout() {
	userStore.logout()
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 48rpx 40rpx 80rpx;
	box-sizing: border-box;
	background-color: #f7f4ef;
}

.greeting {
	display: flex;
	flex-direction: column;
	margin-bottom: 48rpx;
}

.hello {
	font-size: 48rpx;
	font-weight: 600;
	color: #3d3a34;
}

.welcome {
	margin-top: 16rpx;
	font-size: 28rpx;
	color: #9c8f7d;
}

.role {
	margin-top: 16rpx;
	align-self: flex-start;
	padding: 6rpx 20rpx;
	border-radius: 999rpx;
	background-color: #ece3d5;
	font-size: 24rpx;
	color: #8b6f47;
}

/* 功能入口网格 */
.grid {
	display: flex;
	flex-wrap: wrap;
	justify-content: space-between;
	gap: 24rpx;
	margin-bottom: 48rpx;
}

.grid-item {
	width: 31%;
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 32rpx 0;
	border-radius: 24rpx;
	background-color: #fffdfa;
	border: 2rpx solid #e6ddcf;
	box-shadow: 0 2px 8px rgba(139, 111, 71, 0.08);
}

.grid-icon {
	width: 80rpx;
	height: 80rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 16rpx;
}

.icon-emoji {
	font-size: 48rpx;
}

.grid-label {
	font-size: 26rpx;
	color: #8b6f47;
}

/* 我的卡片 */
.card {
	padding: 40rpx 32rpx;
	border-radius: 24rpx;
	background-color: #fffdfa;
	border: 2rpx solid #e6ddcf;
}

.profile-card {
	margin-bottom: 40rpx;
}

.card-title {
	font-size: 34rpx;
	font-weight: 600;
	color: #8b6f47;
}

.card-hint {
	display: block;
	margin-top: 16rpx;
	font-size: 26rpx;
	color: #9c8f7d;
}

.logout {
	margin-top: 24rpx;
	background-color: transparent;
	border: 2rpx solid #d9cdb9;
	border-radius: 20rpx;
	color: #8b6f47;
	font-size: 28rpx;
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 24rpx;
	color: #bdb2a1;
	text-align: center;
}
</style>
