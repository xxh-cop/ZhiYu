<template>
	<view class="page zy-page-enter">
		<!-- 页面标题 -->
		<view class="page-header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="page-title">个人设置</text>
			<text class="page-subtitle">管理你的资料与账号安全</text>
		</view>
		<view v-if="loadError" class="settings-error" role="alert">
			<text>{{ loadError }}</text>
			<button @click="loadUserInfo">重试</button>
		</view>

		<!-- 头像区域 -->
		<view class="avatar-section zy-slide-up zy-stagger" style="--stagger-i: 1">
			<view class="avatar-ring" role="button" tabindex="0" aria-label="更换头像" @click="chooseAvatar" @keydown.enter.prevent="chooseAvatar" @keydown.space.prevent="chooseAvatar">
				<image
					v-if="form.avatarUrl"
					class="avatar-img"
					:src="avatarFullUrl"
					mode="aspectFill"
				/>
				<view v-else class="avatar-placeholder">
					<text class="avatar-placeholder-text">{{ avatarChar }}</text>
				</view>
			</view>
			<text class="avatar-hint">点击更换头像</text>
		</view>

		<!-- 卡片 1：基本资料 -->
		<view class="section-card zy-slide-up zy-stagger" style="--stagger-i: 2">
			<view class="card-head">
				<view class="card-icon-wrap">
					<wd-icon name="user" size="32rpx" color="#FFFFFF" />
				</view>
				<text class="section-heading">基本资料</text>
			</view>

			<view class="form-group">
				<text class="form-label">昵称</text>
				<input
					class="form-input"
					v-model="form.nickname"
					aria-label="昵称"
					placeholder="请输入昵称"
					:maxlength="50"
				/>
			</view>

			<view class="form-group">
				<text class="form-label">用户名</text>
				<input
					class="form-input form-input--disabled"
					:value="form.username"
					aria-label="用户名"
					disabled
				/>
			</view>

			<view class="form-group">
				<text class="form-label">角色</text>
				<view class="form-readonly">
					<text class="form-readonly-text">{{ roleLabel }}</text>
				</view>
			</view>

			<view class="form-group">
				<text class="form-label">学校</text>
				<input
					class="form-input"
					v-model="form.school"
					aria-label="学校"
					placeholder="请输入学校名称"
					:maxlength="100"
				/>
			</view>

			<view class="form-group">
				<text class="form-label">个人简介</text>
				<textarea
					class="form-textarea"
					v-model="form.intro"
					aria-label="个人简介"
					placeholder="介绍一下自己吧"
					:maxlength="500"
					:auto-height="true"
					:style="{ minHeight: '120rpx' }"
				/>
			</view>

			<view
				class="btn-primary"
				role="button"
				:tabindex="savingProfile ? -1 : 0"
				:aria-disabled="savingProfile"
				:aria-busy="savingProfile"
				:class="{ 'btn-primary--disabled': savingProfile }"
				@click="handleSaveProfile"
				@keydown.enter.prevent="handleSaveProfile"
				@keydown.space.prevent="handleSaveProfile"
			>
				<text class="btn-primary-text">{{ savingProfile ? '保存中...' : '保存资料' }}</text>
			</view>
		</view>

		<!-- 卡片 2：修改密码 -->
		<view class="section-card zy-slide-up zy-stagger" style="--stagger-i: 3">
			<view class="card-head">
				<view class="card-icon-wrap card-icon-security">
					<wd-icon name="lock" size="32rpx" color="#FFFFFF" />
				</view>
				<text class="section-heading">修改密码</text>
			</view>

			<view class="form-group">
				<text class="form-label">旧密码</text>
				<input
					class="form-input"
					v-model="pwdForm.oldPassword"
					aria-label="旧密码"
					placeholder="请输入旧密码"
					password
					:maxlength="32"
				/>
			</view>

			<view class="form-group">
				<text class="form-label">新密码</text>
				<input
					class="form-input"
					v-model="pwdForm.newPassword"
					aria-label="新密码"
					placeholder="6-32 位新密码"
					password
					:maxlength="32"
				/>
			</view>

			<view class="form-group">
				<text class="form-label">确认新密码</text>
				<input
					class="form-input"
					v-model="pwdForm.confirmPassword"
					aria-label="确认新密码"
					placeholder="请再次输入新密码"
					password
					:maxlength="32"
				/>
			</view>

			<view
				class="btn-primary btn-primary--accent"
				role="button"
				:tabindex="savingPassword ? -1 : 0"
				:aria-disabled="savingPassword"
				:aria-busy="savingPassword"
				:class="{ 'btn-primary--disabled': savingPassword }"
				@click="handleChangePassword"
				@keydown.enter.prevent="handleChangePassword"
				@keydown.space.prevent="handleChangePassword"
			>
				<text class="btn-primary-text">{{ savingPassword ? '提交中...' : '修改密码' }}</text>
			</view>
		</view>

		<!-- 小织记忆入口 -->
		<view
			class="section-card memory-entry zy-slide-up zy-stagger"
			style="--stagger-i: 4"
			role="button"
			tabindex="0"
			aria-label="打开小织记忆设置"
			@click="goMemory"
			@keydown.enter.prevent="goMemory"
			@keydown.space.prevent="goMemory"
		>
			<view class="card-head">
				<view class="card-icon-wrap card-icon-memory">
					<wd-icon name="heart" size="32rpx" color="#FFFFFF" />
				</view>
				<text class="section-heading">小织记忆</text>
				<text class="memory-entry-arrow" aria-hidden="true">›</text>
			</view>
			<text class="memory-entry-text">默认关闭 · 只保存练习偏好 · 180 天后失效 · 可随时删除</text>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { useUserStore } from '../../stores/user'
import { API_BASE_URL, TOKEN_KEY } from '../../utils/config.js'

const ROLE_LABEL = {
	0: '学生',
	1: '传承人',
	2: '咨询师',
	3: '管理员',
}

const userStore = useUserStore()

const form = ref({
	nickname: '',
	username: '',
	role: null,
	school: '',
	intro: '',
	avatarUrl: '',
})

const pwdForm = ref({
	oldPassword: '',
	newPassword: '',
	confirmPassword: '',
})

const savingProfile = ref(false)
const savingPassword = ref(false)
const loadingMe = ref(false)
const loadError = ref('')

function goMemory() {
	uni.navigateTo({ url: '/pages/settings/memory' })
}

const roleLabel = computed(() => {
	const role = form.value.role
	if (role === null || role === undefined) return '未设置'
	return ROLE_LABEL[role] || '未知'
})

const avatarChar = computed(() => {
	const name = form.value.nickname || userStore.userInfo?.nickname || '织'
	return name ? name.charAt(0) : '织'
})

const avatarFullUrl = computed(() => {
	const url = form.value.avatarUrl
	if (!url) return ''
	return url.startsWith('http') ? url : `${API_BASE_URL}${url}`
})

onShow(() => {
	if (!userStore.isLogin) {
		uni.reLaunch({ url: '/pages/login/index' })
		return
	}
	loadUserInfo()
})

async function loadUserInfo() {
	if (loadingMe.value) return
	loadingMe.value = true
	try {
		const data = await request({ url: '/api/auth/me', method: 'GET' })
		form.value = {
			nickname: data.nickname || '',
			username: data.username || '',
			role: data.role != null ? Number(data.role) : null,
			school: data.school || '',
			intro: data.intro || '',
			avatarUrl: data.avatarUrl || '',
		}
		loadError.value = ''
	} catch (e) {
		loadError.value = '个人资料加载失败，请重试'
	} finally {
		loadingMe.value = false
	}
}

// ── 头像上传 ──
function chooseAvatar() {
	uni.chooseImage({
		count: 1,
		sizeType: ['compressed'],
		sourceType: ['album', 'camera'],
		success: (res) => {
			const tempFilePath = res.tempFilePaths[0]
			uploadAvatar(tempFilePath)
		},
	})
}

function uploadAvatar(filePath) {
	uni.showLoading({ title: '上传中...', mask: true })
	const token = uni.getStorageSync(TOKEN_KEY) || ''
	uni.uploadFile({
		url: `${API_BASE_URL}/api/files/upload/image`,
		filePath: filePath,
		name: 'file',
		header: {
			Authorization: token ? `Bearer ${token}` : '',
		},
		success: (uploadRes) => {
			try {
				const data = JSON.parse(uploadRes.data)
				if (data.code === 0 && data.data && data.data.url) {
					form.value.avatarUrl = data.data.url
					uni.showToast({ title: '头像已更换', icon: 'success' })
				} else {
					uni.showToast({ title: data.message || '上传失败', icon: 'none' })
				}
			} catch (e) {
				uni.showToast({ title: '上传失败', icon: 'none' })
			}
		},
		fail: () => {
			uni.showToast({ title: '网络异常，上传失败', icon: 'none' })
		},
		complete: () => {
			uni.hideLoading()
		},
	})
}

// ── 保存资料 ──
async function handleSaveProfile() {
	if (savingProfile.value) return
	const nickname = (form.value.nickname || '').trim()
	if (!nickname) {
		uni.showToast({ title: '昵称不能为空', icon: 'none' })
		return
	}
	savingProfile.value = true
	try {
		const payload = {
			nickname: nickname,
			avatarUrl: form.value.avatarUrl || null,
			school: (form.value.school || '').trim() || null,
			intro: (form.value.intro || '').trim() || null,
		}
		const data = await request({
			url: '/api/user/profile',
			method: 'PUT',
			data: payload,
		})
		// 更新 userStore，让「我的」页面同步
		userStore.setUserInfo(data)
		uni.showToast({ title: '资料已保存', icon: 'success' })
	} catch (e) {
		uni.showToast({ title: e.message || '保存失败', icon: 'none' })
	} finally {
		savingProfile.value = false
	}
}

// ── 修改密码 ──
async function handleChangePassword() {
	if (savingPassword.value) return
	const { oldPassword, newPassword, confirmPassword } = pwdForm.value

	if (!oldPassword) {
		uni.showToast({ title: '请输入旧密码', icon: 'none' })
		return
	}
	if (!newPassword) {
		uni.showToast({ title: '请输入新密码', icon: 'none' })
		return
	}
	if (newPassword.length < 6 || newPassword.length > 32) {
		uni.showToast({ title: '新密码长度需 6-32 位', icon: 'none' })
		return
	}
	if (oldPassword === newPassword) {
		uni.showToast({ title: '新旧密码不能相同', icon: 'none' })
		return
	}
	if (newPassword !== confirmPassword) {
		uni.showToast({ title: '两次密码输入不一致', icon: 'none' })
		return
	}

	savingPassword.value = true
	try {
		await request({
			url: '/api/user/password',
			method: 'PUT',
			data: { oldPassword, newPassword },
		})
		uni.showToast({ title: '密码已修改，下次登录请使用新密码', icon: 'none', duration: 2500 })
		pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
	} catch (e) {
		if (e.code === 2001) {
			uni.showToast({ title: '旧密码错误', icon: 'none' })
		} else {
			uni.showToast({ title: e.message || '修改失败', icon: 'none' })
		}
	} finally {
		savingPassword.value = false
	}
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 32rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #f5f7f2);
}

/* ── 页面标题 ── */
.page-header {
	margin-bottom: 32rpx;
}

.page-title {
	font-size: 44rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
	display: block;
}

.page-subtitle {
	font-size: 26rpx;
	color: var(--zy-aid, #718478);
	display: block;
	margin-top: 8rpx;
}

/* ── 头像区域 ── */
.avatar-section {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;
	margin-bottom: 32rpx;
}

.avatar-ring {
	width: 140rpx;
	height: 140rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	padding: 6rpx;
	background: linear-gradient(135deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
	flex-shrink: 0;
}

.avatar-img {
	width: 100%;
	height: 100%;
	border-radius: var(--zy-radius-full, 9999rpx);
	border: 4rpx solid var(--zy-card-bg, #FFFFFF);
	box-sizing: border-box;
}

.avatar-placeholder {
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

.avatar-placeholder-text {
	font-size: 52rpx;
	font-weight: 600;
	color: #FFFFFF;
}

.avatar-hint {
	font-size: 24rpx;
	color: var(--zy-aid, #718478);
}

/* ── 卡片 ── */
.section-card {
	background-color: var(--zy-card-bg, #FFFFFF);
	border-radius: var(--zy-radius-lg, 32rpx);
	padding: 32rpx 28rpx;
	margin-bottom: 20rpx;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
}

.card-head {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin-bottom: 24rpx;
}

.card-icon-wrap {
	width: 56rpx;
	height: 56rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.card-icon-security {
	background: linear-gradient(135deg, var(--zy-accent, #bb914d), #FFCB6E);
}

.section-heading {
	font-size: 32rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
}

/* ── 表单 ── */
.form-group {
	margin-bottom: 24rpx;
}

.form-label {
	font-size: 26rpx;
	color: var(--zy-aid, #718478);
	display: block;
	margin-bottom: 10rpx;
}

.form-input {
	width: 100%;
	height: 80rpx;
	padding: 0 24rpx;
	font-size: 28rpx;
	color: var(--zy-title, #233d34);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-md, 16rpx);
	background-color: var(--zy-bg, #f5f7f2);
	box-sizing: border-box;
	transition: border-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.form-input:focus {
	border-color: var(--zy-primary, #2f7566);
}

.form-input--disabled {
	opacity: 0.6;
}

.form-textarea {
	width: 100%;
	padding: 20rpx 24rpx;
	font-size: 28rpx;
	color: var(--zy-title, #233d34);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-md, 16rpx);
	background-color: var(--zy-bg, #f5f7f2);
	box-sizing: border-box;
	transition: border-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.form-textarea:focus {
	border-color: var(--zy-primary, #2f7566);
}

.form-readonly {
	height: 80rpx;
	display: flex;
	align-items: center;
}

.form-readonly-text {
	font-size: 28rpx;
	color: var(--zy-title, #233d34);
}

/* ── 按钮 ── */
.btn-primary {
	height: 88rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
	display: flex;
	align-items: center;
	justify-content: center;
	margin-top: 8rpx;
	transition: opacity var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.btn-primary:active {
	opacity: 0.85;
}

.btn-primary--accent {
	background: linear-gradient(135deg, var(--zy-accent, #bb914d), #FFCB6E);
}

.btn-primary--disabled {
	opacity: 0.5;
	pointer-events: none;
}

.btn-primary-text {
	font-size: 30rpx;
	font-weight: 500;
	color: #FFFFFF;
}

/* ── 底部 ── */
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
.btn-primary:focus-visible {
	outline: 3px solid var(--zy-primary, #2f7566);
	outline-offset: 3px;
}
.settings-error {
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
.settings-error button {
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
.settings-error button::after { border: 0; }
.card-icon-memory {
	background: linear-gradient(135deg, var(--zy-accent, #bb914d), var(--zy-primary, #2f7566));
}
.memory-entry {
	cursor: pointer;
}
.memory-entry .card-head {
	margin-bottom: 10rpx;
}
.memory-entry-arrow {
	margin-left: auto;
	color: var(--zy-aid, #718478);
	font-size: 40rpx;
	line-height: 1;
}
.memory-entry-text {
	display: block;
	font-size: 23rpx;
	line-height: 1.7;
	color: var(--zy-content, #586e62);
}
</style>
