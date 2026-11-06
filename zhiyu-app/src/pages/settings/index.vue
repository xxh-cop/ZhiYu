<template>
	<view class="page zy-page-enter">
		<!-- 页面标题 -->
		<view class="page-header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="page-title">个人设置</text>
			<text class="page-subtitle">管理你的资料与账号安全</text>
		</view>
		<view v-if="loadError" class="settings-error" role="alert">
			<text>{{ loadError }}</text>
			<AccessibleButton @click="loadUserInfo">重试</AccessibleButton>
		</view>

		<!-- 头像区域 -->
		<view class="avatar-section zy-slide-up zy-stagger" style="--stagger-i: 1">
			<view class="avatar-ring" role="button" tabindex="0" aria-label="更换头像" @click="chooseAvatar" @keydown.enter.prevent="chooseAvatar" @keydown.space.prevent="chooseAvatar">
				<image
					v-if="form.avatarUrl"
					class="avatar-img"
					:src="avatarFullUrl"
					mode="aspectFill"
					alt="当前头像"
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

			<FormField label="昵称" required :error="profileErrors.nickname" :maxlength="50" :count="form.nickname.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<input
						:id="id"
						class="form-input"
						v-model="form.nickname"
						:aria-describedby="ariaDescribedby"
						:aria-invalid="ariaInvalid"
						placeholder="请输入昵称"
						:maxlength="50"
						@input="profileErrors.nickname = ''"
					/>
				</template>
			</FormField>

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

			<FormField label="学校" :error="profileErrors.school" :maxlength="100" :count="form.school.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<input
						:id="id"
						class="form-input"
						v-model="form.school"
						:aria-describedby="ariaDescribedby"
						:aria-invalid="ariaInvalid"
						placeholder="请输入学校名称"
						:maxlength="100"
						@input="profileErrors.school = ''"
					/>
				</template>
			</FormField>

			<FormField label="个人简介" optional-text="选填" hint="会展示在你的作品与主页上" :error="profileErrors.intro" :maxlength="500" :count="form.intro.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<textarea
						:id="id"
						class="form-textarea"
						v-model="form.intro"
						:aria-describedby="ariaDescribedby"
						:aria-invalid="ariaInvalid"
						placeholder="介绍一下自己吧"
						:maxlength="500"
						:auto-height="true"
						:style="{ minHeight: '120rpx' }"
						@input="profileErrors.intro = ''"
					/>
				</template>
			</FormField>

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

			<FormField label="旧密码" required :error="pwdErrors.old" :maxlength="32" :count="pwdForm.oldPassword.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<input
						:id="id"
						class="form-input"
						v-model="pwdForm.oldPassword"
						:aria-describedby="ariaDescribedby"
						:aria-invalid="ariaInvalid"
						placeholder="请输入旧密码"
						password
						:maxlength="32"
						@input="pwdErrors.old = ''"
					/>
				</template>
			</FormField>

			<FormField label="新密码" required hint="6-32 位，建议包含字母与数字" :error="pwdErrors.new" :maxlength="32" :count="pwdForm.newPassword.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<input
						:id="id"
						class="form-input"
						v-model="pwdForm.newPassword"
						:aria-describedby="ariaDescribedby"
						:aria-invalid="ariaInvalid"
						placeholder="6-32 位新密码"
						password
						:maxlength="32"
						@input="pwdErrors.new = ''"
					/>
				</template>
			</FormField>

			<FormField label="确认新密码" required :error="pwdErrors.confirm" :maxlength="32" :count="pwdForm.confirmPassword.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<input
						:id="id"
						class="form-input"
						v-model="pwdForm.confirmPassword"
						:aria-describedby="ariaDescribedby"
						:aria-invalid="ariaInvalid"
						placeholder="请再次输入新密码"
						password
						:maxlength="32"
						@input="pwdErrors.confirm = ''"
					/>
				</template>
			</FormField>

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

		<!-- AM1 情绪关怀提醒开关（AR 批接入）：关闭后首页不再展示基线提醒 -->
		<view class="section-card zy-slide-up zy-stagger" style="--stagger-i: 5">
			<view class="card-head">
				<view class="card-icon-wrap card-icon-memory">
					<wd-icon name="notification" size="32rpx" color="#FFFFFF" />
				</view>
				<text class="section-heading">情绪关怀提醒</text>
			</view>
			<text class="memory-entry-text">当最近的低落时刻比平时多时，在首页温和提醒你一次。这只是提醒，不是任何判断。</text>
			<AccessibleButton
				class="nudge-toggle"
				:aria-pressed="nudgeEnabled"
				:disabled="nudgeBusy"
				:aria-label="nudgeEnabled ? '关闭情绪关怀提醒' : '开启情绪关怀提醒'"
				@click="toggleNudge"
			>{{ nudgeBusy ? '正在保存…' : nudgeEnabled ? '已开启 · 点按关闭' : '已关闭 · 点按开启' }}</AccessibleButton>
		</view>

		<!-- 卡点引导开关（2026-11-05 卡点引导）：创作中同一步骤反复停留时的一句轻提示。
		     与情绪关怀提醒同属「可拒绝的关心」，放在相邻位置便于发现；关闭后任何路径不再出现。 -->
		<view class="section-card zy-slide-up zy-stagger" style="--stagger-i: 6">
			<view class="card-head">
				<view class="card-icon-wrap card-icon-memory">
					<wd-icon name="sparkles" size="32rpx" color="#FFFFFF" />
				</view>
				<text class="section-heading">陪伴小提示</text>
			</view>
			<text class="memory-entry-text">创作中如果在同一步骤停留较久，小织会轻轻给一句可选的小建议。它从不打断你，也随时可以收起。这只是陪伴，不是任何判断。</text>
			<AccessibleButton
				class="nudge-toggle"
				:aria-pressed="stuckGuideEnabled"
				:disabled="stuckGuideBusy"
				:aria-label="stuckGuideEnabled ? '关闭陪伴小提示' : '开启陪伴小提示'"
				@click="toggleStuckGuide"
			>{{ stuckGuideBusy ? '正在保存…' : stuckGuideEnabled ? '已开启 · 点按关闭' : '已关闭 · 点按开启' }}</AccessibleButton>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
		<Toast />
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { invalidate, TTL_KEYS } from '../../utils/ttlCache'
import { useUserStore } from '../../stores/user'
import { API_BASE_URL, TOKEN_KEY } from '../../utils/config.js'
import Toast from '../../components/Toast.vue'
import FormField from '../../components/FormField.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

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
const profileErrors = ref({ nickname: '', school: '', intro: '' })
const pwdErrors = ref({ old: '', new: '', confirm: '' })

function goMemory() {
	uni.navigateTo({ url: '/pages/settings/memory' })
}

/* ── AM1 情绪关怀提醒开关（AR 批接入）── */
const nudgeEnabled = ref(true)
const nudgeBusy = ref(false)
async function loadNudgeSetting() {
	try {
		const data = await request({ url: '/api/emotion/baseline-nudge', method: 'GET' })
		nudgeEnabled.value = !data || data.enabled !== false
	} catch (e) {
		/* 读取失败保持默认开（后端默认开启），不打扰 */
	}
}
async function toggleNudge() {
	if (nudgeBusy.value) return
	const next = !nudgeEnabled.value
	nudgeBusy.value = true
	try {
		await request({ url: '/api/emotion/baseline-nudge/setting', method: 'PUT', data: { enabled: next } })
		nudgeEnabled.value = next
		toast.success(next ? '已开启情绪关怀提醒' : '已关闭，首页不会再展示提醒')
	} catch (e) {
		toast.care((e && e.message) || '保存失败，请重试')
	} finally {
		nudgeBusy.value = false
	}
}

/* ── 卡点引导开关（用户级，持久；与提示条上的「不再提示」写同一开关）── */
const stuckGuideEnabled = ref(true)
const stuckGuideBusy = ref(false)
async function loadStuckGuideSetting() {
	try {
		const data = await request({ url: '/api/craft/stuck-guide/setting', method: 'GET' })
		stuckGuideEnabled.value = !data || data.enabled !== false
	} catch (e) {
		/* 读取失败保持默认开（后端默认开启），不打扰 */
	}
}
async function toggleStuckGuide() {
	if (stuckGuideBusy.value) return
	const next = !stuckGuideEnabled.value
	stuckGuideBusy.value = true
	try {
		await request({ url: '/api/craft/stuck-guide/setting', method: 'PUT', data: { enabled: next } })
		stuckGuideEnabled.value = next
		toast.success(next ? '已开启陪伴小提示' : '已关闭，创作中不会再出现这类提示')
	} catch (e) {
		toast.care((e && e.message) || '保存失败，请重试')
	} finally {
		stuckGuideBusy.value = false
	}
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
	loadNudgeSetting()
	loadStuckGuideSetting()
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
					toast.success('头像已更换')
				} else {
					toast.care(data.message || '上传失败')
				}
			} catch (e) {
				toast.care('上传失败')
			}
		},
		fail: () => {
			toast.care('网络异常，上传失败')
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
		profileErrors.value.nickname = '昵称不能为空'
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
		invalidate(TTL_KEYS.authMe)
		toast.success('资料已保存')
	} catch (e) {
		toast.care(e.message || '保存失败')
	} finally {
		savingProfile.value = false
	}
}

// ── 修改密码 ──
async function handleChangePassword() {
	if (savingPassword.value) return
	const { oldPassword, newPassword, confirmPassword } = pwdForm.value

	if (!oldPassword) {
		pwdErrors.value.old = '请输入旧密码'
		return
	}
	if (!newPassword) {
		pwdErrors.value.new = '请输入新密码'
		return
	}
	if (newPassword.length < 6 || newPassword.length > 32) {
		pwdErrors.value.new = '新密码长度需 6-32 位'
		return
	}
	if (oldPassword === newPassword) {
		pwdErrors.value.new = '新旧密码不能相同'
		return
	}
	if (newPassword !== confirmPassword) {
		pwdErrors.value.confirm = '两次密码输入不一致'
		return
	}

	savingPassword.value = true
	try {
		await request({
			url: '/api/user/password',
			method: 'PUT',
			data: { oldPassword, newPassword },
		})
		toast.show('密码已修改，下次登录请使用新密码', { duration: 2500 })
		pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
	} catch (e) {
		if (e.code === 2001) {
			toast.care('旧密码错误')
		} else {
			toast.care(e.message || '修改失败')
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
	color: var(--zy-text, #4a5f54);
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
	background: linear-gradient(135deg, var(--zy-primary, #217e76), var(--zy-brand-300, #5bc4a8));
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
	background: linear-gradient(135deg, var(--zy-brand-700, #1a645e) 0%, var(--zy-primary, #217e76) 100%);
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
	color: var(--zy-text, #4a5f54);
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
	background: linear-gradient(135deg, var(--zy-primary, #217e76), var(--zy-brand-300, #5bc4a8));
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.card-icon-security {
	background: linear-gradient(135deg, var(--zy-accent, #8a6d1f), var(--zy-gold, #d9a441));
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

.form-field {
	margin-bottom: 24rpx;
}

.form-label {
	font-size: 26rpx;
	color: var(--zy-text, #4a5f54);
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
	border-color: var(--zy-primary, #217e76);
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
	border-color: var(--zy-primary, #217e76);
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
	background: var(--zy-brand-600, #217e76);
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
	background: var(--zy-accent, #8a6d1f);
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
	color: var(--zy-text, #4a5f54);
	text-align: center;
}
</style>

<style scoped>
.avatar-ring:focus-visible,
.btn-primary:focus-visible {
	outline: 3px solid var(--zy-primary, #217e76);
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
	color: var(--zy-content, #4a5f54);
	font-size: 23rpx;
	line-height: 1.7;
}
.settings-error button {
	flex-shrink: 0;
	min-height: 64rpx;
	padding: 0 22rpx;
	border: 1rpx solid var(--zy-primary, #217e76);
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-primary, #217e76);
	font-size: 23rpx;
	line-height: 2.5;
}
.settings-error button::after { border: 0; }
.card-icon-memory {
	background: linear-gradient(135deg, var(--zy-accent, #8a6d1f), var(--zy-primary, #217e76));
}
.memory-entry {
	cursor: pointer;
}
.memory-entry .card-head {
	margin-bottom: 10rpx;
}
.memory-entry-arrow {
	margin-left: auto;
	color: var(--zy-text, #4a5f54);
	font-size: 40rpx;
	line-height: 1;
}
.memory-entry-text {
	display: block;
	font-size: 23rpx;
	line-height: 1.7;
	color: var(--zy-content, #4a5f54);
}
/* AM1 情绪关怀提醒开关 */
.nudge-toggle {
	margin-top: 18rpx;
	padding: 12rpx 26rpx;
	font-size: 24rpx;
	color: var(--zy-brand-700, #1a645e);
	background: var(--zy-bg-tint-mint, #e8efe4);
	border-radius: 999rpx;
}
.nudge-toggle[aria-pressed="false"] {
	color: var(--zy-text, #4a5f54);
	background: var(--zy-bg, #f5f7f2);
}
</style>
