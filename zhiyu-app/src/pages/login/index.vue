<template>
	<view class="page">
		<!-- 顶部品牌区 -->
		<view class="hero">
			<text class="brand">织愈</text>
			<text class="slogan">用一针一线，缝补青春的焦虑</text>
		</view>

		<!-- 登录表单卡 -->
		<view class="form-card">
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
				<wd-button
					type="primary"
					block
					size="large"
					:loading="loading"
					:disabled="loading"
					@click="handleLogin"
				>登 录</wd-button>
				<text v-if="errMsg" class="error">{{ errMsg }}</text>
			</view>
		</view>

		<!-- 底部免责声明 -->
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
	background: linear-gradient(180deg, #F0F9F7 0%, #F8F9FB 50%);
}

/* ── 品牌区 ── */
.hero {
	display: flex;
	flex-direction: column;
	align-items: center;
	margin-bottom: 64rpx;
	animation: heroFadeIn 0.5s ease;
}

@keyframes heroFadeIn {
	from { opacity: 0; transform: translateY(-12rpx); }
	to { opacity: 1; transform: translateY(0); }
}

.brand {
	font-family: var(--zy-serif);
	font-size: 64rpx;
	font-weight: 600;
	letter-spacing: 12rpx;
	color: var(--zy-title, #1F2937);
}

.slogan {
	margin-top: 20rpx;
	font-size: 26rpx;
	color: var(--zy-content, #4B5563);
}

/* ── 表单卡 ── */
.form-card {
	width: 100%;
	border-radius: 48rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: 0 8rpx 40rpx rgba(31, 41, 55, 0.06);
	padding: 48rpx 40rpx;
	box-sizing: border-box;
	animation: cardSlideUp 0.4s ease 0.1s both;
}

@keyframes cardSlideUp {
	from { opacity: 0; transform: translateY(24rpx); }
	to { opacity: 1; transform: translateY(0); }
}

.form {
	display: flex;
	flex-direction: column;
	gap: 28rpx;
}

.input-wrap {
	background-color: var(--zy-bg, #F8F9FB);
	border-radius: 24rpx;
	padding: 0 8rpx;
	overflow: hidden;
}

.error {
	margin-top: 8rpx;
	font-size: 26rpx;
	color: var(--zy-danger, #FF6B6B);
	text-align: center;
}

/* ── 底部 ── */
.footnote {
	margin-top: 64rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}
</style>
