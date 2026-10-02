<template>
	<view class="page">
		<view class="greeting">
			<text class="hello">你好，{{ nickname }}</text>
			<text class="welcome">{{ welcome }}</text>
			<text v-if="roleLabel" class="role">{{ roleLabel }}</text>
		</view>

		<view class="card">
			<text class="card-title">今日推荐手作</text>
			<text class="card-hint">根据你此刻的心情，为你挑一件合适的手作</text>
			<view class="placeholder">推荐内容将在后续版本接入</view>
		</view>

		<view class="cta">
			<text class="cta-text">开始疗愈之旅</text>
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
	// 路由守卫：未登录直接回登录页（只做跳转，不做安全决策）
	if (!userStore.isLogin) {
		uni.reLaunch({ url: '/pages/login/index' })
		return
	}
	// 每次进入都向后端确认一次身份，顺便刷新昵称等展示信息
	loadCurrentUser()
})

async function loadCurrentUser() {
	if (loadingMe.value) return
	loadingMe.value = true
	try {
		const data = await request({ url: '/api/auth/me', method: 'GET' })
		userStore.setUserInfo(data)
	} catch (e) {
		// code=1002 已由 utils/request.js 清凭证并跳登录页；
		// 其余错误保留当前页面与已缓存的昵称，不白屏
	} finally {
		loadingMe.value = false
	}
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

.card {
	padding: 40rpx 32rpx;
	border-radius: 24rpx;
	background-color: #fffdfa;
	border: 2rpx solid #e6ddcf;
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

.placeholder {
	margin-top: 32rpx;
	padding: 48rpx 0;
	border-radius: 16rpx;
	background-color: #f7f4ef;
	font-size: 26rpx;
	color: #bdb2a1;
	text-align: center;
}

.cta {
	margin-top: 40rpx;
	padding: 32rpx 0;
	border-radius: 24rpx;
	background-color: #8b6f47;
	text-align: center;
}

.cta-text {
	font-size: 32rpx;
	color: #fffdfa;
	letter-spacing: 6rpx;
}

.logout {
	margin-top: 56rpx;
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
