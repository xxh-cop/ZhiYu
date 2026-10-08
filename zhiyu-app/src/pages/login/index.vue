<template>
	<view class="page">
		<!-- 顶部品牌区 -->
		<view class="hero zy-slide-up">
			<view class="hero-logo-wrap">
				<image class="hero-logo" src="/static/logo.png" mode="aspectFit" />
			</view>
			<text class="brand">织愈</text>
			<text class="slogan">用一针一线，缝补青春的焦虑</text>
		</view>

		<!-- 登录表单卡 -->
		<view class="form-card zy-slide-up" style="--stagger-i: 1">
			<view class="form">
				<view class="input-wrap">
					<wd-input
						v-model="form.username"
						placeholder="登录名（学号或昵称）"
						:disabled="loading"
						no-border
						custom-class="form-input-el"
					/>
				</view>
				<view class="input-wrap">
					<wd-input
						v-model="form.password"
						placeholder="密码"
						show-password
						:disabled="loading"
						no-border
						custom-class="form-input-el"
						@confirm="handleLogin"
					/>
				</view>
				<view class="login-btn" :class="{ 'login-btn--loading': loading }" @click="handleLogin">
					<text class="login-btn-text">{{ loading ? '登录中…' : '登 录' }}</text>
				</view>
				<text v-if="errMsg" class="error">{{ errMsg }}</text>
			</view>
		</view>

		<!-- 底部免责 + 隐私政策 -->
		<view class="footer zy-slide-up" style="--stagger-i: 2">
			<view class="register-link-row">
				<text class="footer-text">还没有账号？</text>
				<text class="register-link" @click="goRegister">立即注册</text>
			</view>
			<text class="footnote">本系统不能替代专业心理咨询</text>
			<text class="privacy-link" @click="goPrivacy">查看隐私政策</text>
		</view>
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

function goPrivacy() {
	uni.navigateTo({ url: '/pages/privacy/index' })
}

function goRegister() {
	uni.navigateTo({ url: '/pages/register/index' })
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
	background: linear-gradient(180deg, var(--zy-bg-tint-mint, #F0F9F7) 0%, var(--zy-bg, #F8F9FB) 50%);
}

/* ── 品牌区 ── */
.hero {
	display: flex;
	flex-direction: column;
	align-items: center;
	margin-bottom: 56rpx;
}

.hero-logo-wrap {
	width: 160rpx;
	height: 160rpx;
	border-radius: var(--zy-radius-full);
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 28rpx;
	box-shadow: var(--zy-shadow-md);
}

.hero-logo {
	width: 100rpx;
	height: 100rpx;
}

.brand {
	font-family: var(--zy-serif);
	font-size: 56rpx;
	font-weight: 600;
	letter-spacing: 12rpx;
	color: var(--zy-title, #1F2937);
}

.slogan {
	margin-top: 16rpx;
	font-size: 26rpx;
	color: var(--zy-content, #4B5563);
	letter-spacing: 2rpx;
}

/* ── 表单卡 ── */
.form-card {
	width: 100%;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: var(--zy-shadow-md);
	padding: 48rpx 40rpx;
	box-sizing: border-box;
}

.form {
	display: flex;
	flex-direction: column;
	gap: 28rpx;
}

.input-wrap {
	background-color: var(--zy-bg, #F8F9FB);
	border-radius: var(--zy-radius-md, 24rpx);
	padding: 0 8rpx;
	overflow: hidden;
}

.login-btn {
	height: 88rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
	margin-top: 8rpx;
}

.login-btn:active {
	transform: scale(0.97);
}

.login-btn--loading {
	opacity: 0.7;
}

.login-btn-text {
	font-size: 32rpx;
	font-weight: 500;
	color: #FFFFFF;
	letter-spacing: 4rpx;
}

.error {
	margin-top: 8rpx;
	font-size: 26rpx;
	color: var(--zy-danger, #FF6B6B);
	text-align: center;
}

/* ── 底部 ── */
.footer {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;
	margin-top: 56rpx;
}

.register-link-row {
	display: flex;
	align-items: center;
	gap: 8rpx;
	margin-bottom: 4rpx;
}

.footer-text {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

.register-link {
	font-size: 26rpx;
	color: var(--zy-primary, #2D9B8F);
	font-weight: 500;
}

.footnote {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}

.privacy-link {
	font-size: 22rpx;
	color: var(--zy-primary, #2D9B8F);
	text-decoration: underline;
}
</style>
