<template>
	<view class="page">
		<view class="hero">
			<text class="brand">织愈</text>
			<text class="slogan">用一针一线，缝补青春的焦虑</text>
		</view>

		<view class="form">
			<input
				class="input"
				type="text"
				v-model="form.username"
				placeholder="登录名（学号或昵称）"
				placeholder-class="input-placeholder"
				:disabled="loading"
			/>
			<input
				class="input"
				type="text"
				password
				v-model="form.password"
				placeholder="密码"
				placeholder-class="input-placeholder"
				:disabled="loading"
				@confirm="handleLogin"
			/>
			<button class="submit" :loading="loading" :disabled="loading" @click="handleLogin">
				登 录
			</button>
			<text v-if="errMsg" class="error">{{ errMsg }}</text>
		</view>

		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()

const form = reactive({
	username: '',
	password: '',
})
const loading = ref(false)
const errMsg = ref('')

// 冷启动 / 刷新时本地若已有 token，直接进首页，保持登录态
onShow(() => {
	if (userStore.isLogin) {
		uni.reLaunch({ url: '/pages/home/index' })
	}
})

async function handleLogin() {
	if (loading.value) return
	errMsg.value = ''

	if (!form.username || !form.password) {
		errMsg.value = '请填写登录名和密码'
		return
	}

	loading.value = true
	try {
		const data = await request({
			url: '/api/auth/login',
			method: 'POST',
			auth: false,
			data: { username: form.username, password: form.password },
		})
		userStore.setToken(data.token)
		userStore.setUserInfo(data)
		uni.reLaunch({ url: '/pages/home/index' })
	} catch (e) {
		errMsg.value = (e && e.message) || '登录失败，请稍后重试'
	} finally {
		loading.value = false
	}
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	display: flex;
	flex-direction: column;
	justify-content: center;
	align-items: center;
	padding: 0 64rpx;
	box-sizing: border-box;
	background-color: #f7f4ef;
}

.hero {
	display: flex;
	flex-direction: column;
	align-items: center;
	margin-bottom: 80rpx;
}

.brand {
	font-size: 72rpx;
	font-weight: 600;
	letter-spacing: 12rpx;
	color: #8b6f47;
}

.slogan {
	margin-top: 24rpx;
	font-size: 28rpx;
	color: #9c8f7d;
}

.form {
	width: 100%;
	display: flex;
	flex-direction: column;
}

.input {
	width: 100%;
	height: 96rpx;
	padding: 0 32rpx;
	margin-bottom: 28rpx;
	box-sizing: border-box;
	background-color: #fffdfa;
	border: 2rpx solid #e6ddcf;
	border-radius: 20rpx;
	font-size: 30rpx;
	color: #3d3a34;
}

.input-placeholder {
	color: #bdb2a1;
}

.submit {
	width: 100%;
	height: 96rpx;
	line-height: 96rpx;
	margin-top: 12rpx;
	border-radius: 20rpx;
	background-color: #8b6f47;
	color: #fffdfa;
	font-size: 32rpx;
	letter-spacing: 8rpx;
}

.submit[disabled] {
	opacity: 0.6;
}

.error {
	margin-top: 24rpx;
	font-size: 26rpx;
	color: #c1584a;
	text-align: center;
}

.footnote {
	margin-top: 72rpx;
	font-size: 24rpx;
	color: #bdb2a1;
}
</style>
