<template>
	<view class="page">
		<!-- 顶部品牌区 -->
		<view class="hero zy-slide-up">
			<view class="hero-logo-wrap">
				<image class="hero-logo" src="/static/logo.png" mode="aspectFit" />
			</view>
			<text class="brand">织愈</text>
			<text class="slogan">开始你的疗愈之旅</text>
		</view>

		<!-- 注册表单卡 -->
		<view class="form-card zy-slide-up" style="--stagger-i: 1">
			<view class="form">
				<!-- 用户名 -->
				<view class="field-group">
					<view class="input-wrap" :class="{ 'input-error': errors.username }">
						<wd-input
							v-model="form.username"
							placeholder="用户名（3-50 字符）"
							:disabled="loading"
							no-border
							custom-class="form-input-el"
							@blur="validateField('username')"
						/>
					</view>
					<text v-if="errors.username" class="field-error">{{ errors.username }}</text>
				</view>

				<!-- 昵称 -->
				<view class="field-group">
					<view class="input-wrap" :class="{ 'input-error': errors.nickname }">
						<wd-input
							v-model="form.nickname"
							placeholder="昵称（用于展示）"
							:disabled="loading"
							no-border
							custom-class="form-input-el"
							@blur="validateField('nickname')"
						/>
					</view>
					<text v-if="errors.nickname" class="field-error">{{ errors.nickname }}</text>
				</view>

				<!-- 学校 -->
				<view class="input-wrap">
					<wd-input
						v-model="form.school"
						placeholder="学校（可选，如：江西师范大学）"
						:disabled="loading"
						no-border
						custom-class="form-input-el"
					/>
				</view>

				<!-- 密码 -->
				<view class="field-group">
					<view class="input-wrap" :class="{ 'input-error': errors.password }">
						<wd-input
							v-model="form.password"
							placeholder="密码（6-32 字符）"
							show-password
							:disabled="loading"
							no-border
							custom-class="form-input-el"
							@blur="validateField('password')"
						/>
					</view>
					<text v-if="errors.password" class="field-error">{{ errors.password }}</text>
				</view>

				<!-- 确认密码 -->
				<view class="field-group">
					<view class="input-wrap" :class="{ 'input-error': errors.confirmPassword }">
						<wd-input
							v-model="form.confirmPassword"
							placeholder="确认密码"
							show-password
							:disabled="loading"
							no-border
							custom-class="form-input-el"
							@blur="validateField('confirmPassword')"
						/>
					</view>
					<text v-if="errors.confirmPassword" class="field-error">{{ errors.confirmPassword }}</text>
				</view>

				<!-- 注册按钮 -->
				<view class="register-btn" :class="{ 'register-btn--loading': loading }" @click="handleRegister">
					<text class="register-btn-text">{{ loading ? '注册中…' : '注 册' }}</text>
				</view>

				<!-- 通用错误提示 -->
				<text v-if="errMsg" class="error">{{ errMsg }}</text>
			</view>
		</view>

		<!-- 底部链接 + 免责声明 -->
		<view class="footer zy-slide-up" style="--stagger-i: 2">
			<view class="login-link-row">
				<text class="footer-text">已有账号？</text>
				<text class="login-link" @click="goLogin">去登录</text>
			</view>
			<text class="footnote">本系统不能替代专业心理咨询</text>
		</view>
	</view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { request } from '../../utils/request'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()

const form = reactive({
	username: '',
	nickname: '',
	school: '',
	password: '',
	confirmPassword: '',
})

const errors = reactive({
	username: '',
	nickname: '',
	password: '',
	confirmPassword: '',
})

const loading = ref(false)
const errMsg = ref('')

// ── 字段校验 ──

function validateField(field) {
	switch (field) {
		case 'username':
			if (!form.username) {
				errors.username = '用户名不能为空'
			} else if (form.username.length < 3 || form.username.length > 50) {
				errors.username = '用户名长度需在 3-50 之间'
			} else {
				errors.username = ''
			}
			break
		case 'nickname':
			if (!form.nickname) {
				errors.nickname = '昵称不能为空'
			} else if (form.nickname.length > 50) {
				errors.nickname = '昵称长度不能超过 50'
			} else {
				errors.nickname = ''
			}
			break
		case 'password':
			if (!form.password) {
				errors.password = '密码不能为空'
			} else if (form.password.length < 6 || form.password.length > 32) {
				errors.password = '密码长度需在 6-32 之间'
			} else {
				errors.password = ''
			}
			// 密码改变时也校验确认密码
			if (form.confirmPassword) {
				validateField('confirmPassword')
			}
			break
		case 'confirmPassword':
			if (!form.confirmPassword) {
				errors.confirmPassword = '请确认密码'
			} else if (form.confirmPassword !== form.password) {
				errors.confirmPassword = '两次输入的密码不一致'
			} else {
				errors.confirmPassword = ''
			}
			break
	}
}

function validateAll() {
	validateField('username')
	validateField('nickname')
	validateField('password')
	validateField('confirmPassword')
	// 学校是可选的，只校验长度
	if (form.school && form.school.length > 100) {
		errMsg.value = '学校名称过长'
		return false
	}
	return !errors.username && !errors.nickname && !errors.password && !errors.confirmPassword
}

// ── 注册 ──

async function handleRegister() {
	if (loading.value) return
	errMsg.value = ''

	if (!validateAll()) {
		return
	}

	loading.value = true
	try {
		// 1. 注册
		await request({
			url: '/api/auth/register',
			method: 'POST',
			auth: false,
			data: {
				username: form.username,
				nickname: form.nickname,
				school: form.school || undefined,
				password: form.password,
			},
		})

		// 2. 自动登录
		const data = await request({
			url: '/api/auth/login',
			method: 'POST',
			auth: false,
			data: {
				username: form.username,
				password: form.password,
			},
		})

		// 3. 存 token + 用户信息
		userStore.setToken(data.token)
		userStore.setUserInfo(data)

		// 4. 成功提示
		uni.showToast({ title: '注册成功', icon: 'success' })

		// 5. 跳首页（privacy_agreed=0 会自动弹隐私弹窗）
		setTimeout(() => {
			uni.reLaunch({ url: '/pages/home/index' })
		}, 800)
	} catch (e) {
		const code = e && e.code
		const message = (e && e.message) || '注册失败，请稍后重试'

		if (code === 2002) {
			// 用户名已存在
			errors.username = '用户名已被占用'
		} else if (code === 1001) {
			// 参数校验错误，显示具体信息
			errMsg.value = message
		} else {
			errMsg.value = message
		}
	} finally {
		loading.value = false
	}
}

function goLogin() {
	uni.navigateBack()
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
	margin-bottom: 48rpx;
}

.hero-logo-wrap {
	width: 160rpx;
	height: 160rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
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
	gap: 24rpx;
}

/* ── 字段组 ── */
.field-group {
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.input-wrap {
	background-color: var(--zy-bg, #F8F9FB);
	border-radius: var(--zy-radius-md, 24rpx);
	padding: 0 8rpx;
	overflow: hidden;
	transition: box-shadow var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.input-error {
	box-shadow: 0 0 0 2rpx var(--zy-danger, #FF6B6B);
}

.field-error {
	font-size: 24rpx;
	color: var(--zy-danger, #FF6B6B);
	padding-left: 8rpx;
}

/* ── 注册按钮 ── */
.register-btn {
	height: 88rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
	margin-top: 8rpx;
}

.register-btn:active {
	transform: scale(0.97);
}

.register-btn--loading {
	opacity: 0.7;
}

.register-btn-text {
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
	gap: 16rpx;
	margin-top: 48rpx;
}

.login-link-row {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.footer-text {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

.login-link {
	font-size: 26rpx;
	color: var(--zy-primary, #2D9B8F);
	font-weight: 500;
}

.footnote {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}
</style>
