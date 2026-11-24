<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
		<!-- 页面标题 -->
		<view class="page-header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<ZyPageHead seal="设" title="把自己，安顿好" subtitle="管理你的资料与账号安全" />
		</view>
		<view v-if="loadError" class="settings-error" role="alert">
			<text>{{ loadError }}</text>
			<AccessibleButton @click="loadUserInfo">重试</AccessibleButton>
		</view>

		<!-- 头像区域 -->
		<view class="avatar-section zy-slide-up zy-stagger" style="--stagger-i: 1">
			<view class="avatar-ring" role="button" tabindex="0" aria-label="更换头像" @click="chooseAvatar" @keydown.enter.prevent="chooseAvatar" @keydown.space.prevent="chooseAvatar">
				<image
					v-if="form.avatarUrl && !avatarError"
					class="avatar-img"
					:src="avatarFullUrl"
					mode="aspectFill"
					alt="当前头像"
					@error="avatarError = true"
				/>
				<view v-else class="avatar-placeholder">
					<text class="avatar-placeholder-text">{{ avatarChar }}</text>
				</view>
			</view>
			<view class="avatar-copy">
				<text class="avatar-title">头像</text>
				<text class="avatar-hint">点击更换头像</text>
			</view>
		</view>

		<!-- 卡片 1：基本资料 -->
		<view class="section-card zy-slide-up zy-stagger" style="--stagger-i: 2">
			<view class="card-head">
				<view class="card-icon-wrap">
					<ZyIcon name="user" :size="32" color="#FFFFFF" />
				</view>
				<text class="section-heading">基本资料</text>
			</view>

			<FormField label="昵称" required :error="profileErrors.nickname" :maxlength="50" :count="form.nickname.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<ZyInput :id="id" v-model="form.nickname" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" :error="!!profileErrors.nickname" placeholder="请输入昵称" :maxlength="50" @update:model-value="profileErrors.nickname = ''" />
				</template>
			</FormField>

			<view class="form-group">
				<text class="form-label">用户名</text>
				<ZyInput :model-value="form.username" aria-label="用户名" disabled />
			</view>

			<view class="form-group">
				<text class="form-label">角色</text>
				<view class="form-readonly">
					<text class="form-readonly-text">{{ roleLabel }}</text>
				</view>
			</view>

			<FormField label="学校" :error="profileErrors.school" :maxlength="100" :count="form.school.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<ZyInput :id="id" v-model="form.school" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" :error="!!profileErrors.school" placeholder="请输入学校名称" :maxlength="100" @update:model-value="profileErrors.school = ''" />
				</template>
			</FormField>

			<FormField label="个人简介" optional-text="选填" hint="会展示在你的作品与主页上" :error="profileErrors.intro" :maxlength="500" :count="form.intro.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<ZyTextarea :id="id" v-model="form.intro" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" :error="!!profileErrors.intro" placeholder="介绍一下自己吧" :maxlength="500" auto-height @update:model-value="profileErrors.intro = ''" />
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
					<ZyIcon name="lock" :size="32" color="#FFFFFF" />
				</view>
				<text class="section-heading">修改密码</text>
			</view>

			<FormField label="旧密码" required :error="pwdErrors.old" :maxlength="32" :count="pwdForm.oldPassword.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<ZyInput :id="id" v-model="pwdForm.oldPassword" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" :error="!!pwdErrors.old" placeholder="请输入旧密码" password :maxlength="32" @update:model-value="pwdErrors.old = ''" />
				</template>
			</FormField>

			<FormField label="新密码" required hint="6-32 位，建议包含字母与数字" :error="pwdErrors.new" :maxlength="32" :count="pwdForm.newPassword.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<ZyInput :id="id" v-model="pwdForm.newPassword" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" :error="!!pwdErrors.new" placeholder="6-32 位新密码" password :maxlength="32" @update:model-value="pwdErrors.new = ''" />
				</template>
			</FormField>

			<FormField label="确认新密码" required :error="pwdErrors.confirm" :maxlength="32" :count="pwdForm.confirmPassword.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<ZyInput :id="id" v-model="pwdForm.confirmPassword" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" :error="!!pwdErrors.confirm" placeholder="请再次输入新密码" password :maxlength="32" @update:model-value="pwdErrors.confirm = ''" />
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
			aria-label="打开小织记得的设置"
			@click="goMemory"
			@keydown.enter.prevent="goMemory"
			@keydown.space.prevent="goMemory"
		>
			<view class="card-head">
				<view class="card-icon-wrap card-icon-memory">
					<ZyIcon name="heart" :size="32" color="#FFFFFF" />
				</view>
				<text class="section-heading">小织记得的</text>
				<text class="memory-entry-arrow" aria-hidden="true">›</text>
			</view>
			<text class="memory-entry-text">默认关闭 · 只保存练习偏好 · 180 天后失效 · 可随时删除</text>
		</view>

		<!-- AM1 情绪关怀提醒开关（AR 批接入）：关闭后首页不再展示基线提醒 -->
		<view class="section-card zy-slide-up zy-stagger" style="--stagger-i: 5">
			<view class="card-head">
				<view class="card-icon-wrap card-icon-memory">
					<ZyIcon name="bell" :size="32" color="#FFFFFF" />
				</view>
				<text class="section-heading">情绪关怀提醒</text>
			</view>
			<text class="memory-entry-text">当最近的低落时刻比平时多时，在首页温和提醒你一次。这只是提醒，不是任何判断。</text>
			<AccessibleButton
				class="nudge-toggle"
				:class="{ 'is-off': !nudgeEnabled }"
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
					<ZyIcon name="info" :size="32" color="#FFFFFF" />
				</view>
				<text class="section-heading">陪伴小提示</text>
			</view>
			<text class="memory-entry-text">创作中如果在同一步骤停留较久，小织会轻轻给一句可选的小建议。它从不打断你，也随时可以收起。这只是陪伴，不是任何判断。</text>
			<AccessibleButton
				class="nudge-toggle"
				:class="{ 'is-off': !stuckGuideEnabled }"
				:aria-pressed="stuckGuideEnabled"
				:disabled="stuckGuideBusy"
				:aria-label="stuckGuideEnabled ? '关闭陪伴小提示' : '开启陪伴小提示'"
				@click="toggleStuckGuide"
			>{{ stuckGuideBusy ? '正在保存…' : stuckGuideEnabled ? '已开启 · 点按关闭' : '已关闭 · 点按开启' }}</AccessibleButton>
		</view>

		<!-- 页面配色（2026-11-08 主题切换）：云白默认 / 暖纸可选，选择持久化，全站即时生效 -->
		<view class="section-card zy-slide-up zy-stagger" style="--stagger-i: 7">
			<view class="card-head">
				<view class="card-icon-wrap card-icon-memory">
					<ZyIcon name="picture" :size="32" color="#FFFFFF" />
				</view>
				<text class="section-heading">页面配色</text>
			</view>
			<text class="memory-entry-text">选择你看着舒服的底色，立即生效，下次打开会记住。</text>
			<view class="theme-options" role="radiogroup" aria-label="页面配色">
				<AccessibleButton
					v-for="opt in THEME_OPTIONS"
					:key="opt.value"
					class="theme-option"
					:class="{ 'is-selected': themeStore.theme === opt.value }"
					role="radio"
					:aria-checked="themeStore.theme === opt.value"
					:aria-label="'切换到' + opt.name"
					@click="themeStore.setTheme(opt.value)"
				>
					<view class="theme-swatch" :class="'theme-swatch-' + opt.value" aria-hidden="true"></view>
					<text class="theme-option-name">{{ opt.name }}</text>
					<text class="theme-option-desc">{{ opt.desc }}</text>
				</AccessibleButton>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<Toast />
	</view>
</template>

<script setup>
import ZyPageHead from '../../components/ZyPageHead.vue'
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { invalidate, TTL_KEYS } from '../../utils/ttlCache'
import { useUserStore } from '../../stores/user'
import { useThemeStore } from '../../stores/theme'
import { API_BASE_URL, TOKEN_KEY } from '../../utils/config.js'
import Toast from '../../components/Toast.vue'
import FormField from '../../components/FormField.vue'
import ZyInput from '../../components/ZyInput.vue'
import ZyTextarea from '../../components/ZyTextarea.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ZyIcon from '../../components/ZyIcon.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

const ROLE_LABEL = {
	0: '学生',
	1: '传承人',
	2: '咨询师',
	3: '管理员',
}

const userStore = useUserStore()
const themeStore = useThemeStore()

const THEME_OPTIONS = [
	{ value: 'white', name: '青花瓷', desc: '白瓷底 · 青花蓝（默认）' },
	{ value: 'jade', name: '青瓷', desc: '淡青底 · 原版青绿' },
	{ value: 'warm', name: '暖纸', desc: '宣纸米色 · 青花蓝' },
	{ value: 'dark', name: '玄墨', desc: '藏青深底，夜间更安静' },
	{ value: 'system', name: '跟随系统', desc: '随手机深浅色自动切换' },
]

const form = ref({
	nickname: '',
	username: '',
	role: null,
	school: '',
	intro: '',
	avatarUrl: '',
})
const avatarError = ref(false)

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
const focusedField = ref('')
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
	background-color: var(--zy-bg, #ffffff);
}

/* ── 页面标题 ── */
.page-header {
	margin-bottom: 32rpx;
}

.page-title {
	font-size:var(--zy-text-title-lg, 40rpx);
	font-weight: 500;
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
	display: block;
}

.page-subtitle {
	font-size:var(--zy-text-caption, 24rpx);
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
	background: linear-gradient(135deg, var(--zy-primary, var(--zy-brand-600, #24478f)), var(--zy-brand-300, var(--zy-brand-300, #8ea9dc)));
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
	background: linear-gradient(135deg, var(--zy-brand-700, var(--zy-brand-700, #24478f)) 0%, var(--zy-primary, var(--zy-brand-600, #24478f)) 100%);
	display: flex;
	align-items: center;
	justify-content: center;
	border: 4rpx solid var(--zy-card-bg, #FFFFFF);
	box-sizing: border-box;
}

.avatar-placeholder-text {
	font-size:var(--zy-text-display, 56rpx);
	font-weight: 600;
	color: #FFFFFF;
}

.avatar-hint {
	font-size:var(--zy-text-caption, 24rpx);
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
	background: linear-gradient(135deg, var(--zy-primary, var(--zy-brand-600, #24478f)), var(--zy-brand-300, var(--zy-brand-300, #8ea9dc)));
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.card-icon-security {
	background: linear-gradient(135deg, var(--zy-accent, var(--zy-accent, #7a5c12)), var(--zy-gold, #d9a441));
}

.section-heading {
	font-size:var(--zy-text-title, 32rpx);
	font-weight: 600;
	color: var(--zy-title, #233d34);
}

/* ── 表单 ── */
.form-group {
	margin-bottom: 24rpx;
}

.form-label {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-text, #4a5f54);
	display: block;
	margin-bottom: 10rpx;
}

.form-readonly {
	height: 80rpx;
	display: flex;
	align-items: center;
}

.form-readonly-text {
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-title, #233d34);
}

/* ── 按钮 ── */
.btn-primary {
	height: 88rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-brand-600, var(--zy-brand-600, #24478f));
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
	background: var(--zy-accent, var(--zy-accent, #7a5c12));
}

.btn-primary--disabled {
	opacity: 0.5;
	pointer-events: none;
}

.btn-primary-text {
	font-size:var(--zy-text-body-lg, 30rpx);
	font-weight: 500;
	color: #FFFFFF;
}

/* ── 底部 ── */
.footnote {
	display: block;
	margin-top: 48rpx;
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-text, #4a5f54);
	text-align: center;
}
</style>

<style scoped>
.avatar-ring:focus-visible,
.btn-primary:focus-visible {
	outline: 3px solid var(--zy-primary, var(--zy-brand-600, #24478f));
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
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 1.7;
}
.settings-error button {
	flex-shrink: 0;
	min-height: 64rpx;
	padding: 0 22rpx;
	border: 1rpx solid var(--zy-primary, var(--zy-brand-600, #24478f));
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-bg-tint-mint, #eef3fb);
	color: var(--zy-brand-700, var(--zy-brand-600, #24478f));
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 2.5;
}
.settings-error button::after { border: 0; }
.card-icon-memory {
	background: linear-gradient(135deg, var(--zy-accent, var(--zy-accent, #7a5c12)), var(--zy-primary, var(--zy-brand-600, #24478f)));
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
	font-size:var(--zy-text-title-lg, 40rpx);
	line-height: 1;
}
.memory-entry-text {
	display: block;
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 1.7;
	color: var(--zy-content, #4a5f54);
}
/* AM1 情绪关怀提醒开关 */
.nudge-toggle {
	margin-top: 18rpx;
	padding: 12rpx 26rpx;
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-brand-700, var(--zy-brand-700, #24478f));
	background: var(--zy-bg-tint-mint, #eef3fb);
	border-radius: 999rpx;
}
.nudge-toggle.is-off {
	color: var(--zy-text, #4a5f54);
	background: var(--zy-bg, #ffffff);
}

/* ── 页面配色选项 ── */
.theme-options { display: flex; flex-wrap: wrap; gap: 20rpx; margin-top: 20rpx; }
.theme-option { flex: 1 1 calc(50% - 10rpx); min-width: calc(50% - 10rpx); display: flex; flex-direction: column; align-items: flex-start; gap: 8rpx; padding: 20rpx; border-radius: var(--zy-radius-card); background: var(--zy-card-bg); border: 2rpx solid var(--zy-border); margin: 0; line-height: 1.6; text-align: left; }
.theme-option.is-selected { border-color: var(--zy-brand-600); }
.theme-swatch { width: 100%; height: 72rpx; border-radius: var(--zy-radius-control, 18rpx); border: 2rpx solid var(--zy-border); position: relative; }
/* 色板为主题的展示样本，刻意硬编码展示色，不随当前主题变化 */
/* v9：底色 + 一枚主色胶囊，一眼看出「底是什么颜色、按钮是什么颜色」 */
.theme-swatch::after { content: ''; position: absolute; left: 16rpx; bottom: 16rpx; width: 88rpx; height: 24rpx; border-radius: 999rpx; }
.theme-swatch-white { background: #F6F8FB; }
.theme-swatch-white::after { background: #24478F; }
.theme-swatch-jade { background: #F1F4F1; }
.theme-swatch-jade::after { background: #136A5F; }
.theme-swatch-warm { background: #F6F1E6; }
.theme-swatch-warm::after { background: #24478F; }
.theme-swatch-dark { background: #121A2B; }
.theme-swatch-dark::after { background: #9DB8EC; }
.theme-swatch-system { background: linear-gradient(90deg, #F6F8FB 50%, #121A2B 50%); }
.theme-swatch-system::after { background: #24478F; }
.theme-option-name { font-size: var(--zy-text-body); font-weight: 600; color: var(--zy-title); }
.theme-option-desc { font-size: var(--zy-text-micro, 22rpx); color: var(--zy-muted); line-height: 1.6; }
.theme-option.is-selected .theme-option-name { color: var(--zy-brand-700); }
/* ── v9 冷瓷展签覆盖（不改表单结构，只收口视觉） ── */
.page { padding: 36rpx 32rpx 120rpx; background-color: var(--zy-bg, #f1f4f1); }
.page-header { padding-bottom: 32rpx; margin-bottom: 8rpx; border-bottom: 1rpx solid var(--zy-border, #d3ded8); }
.page-eyebrow { display: block; font-size: var(--zy-text-micro, 22rpx); letter-spacing: 2px; color: var(--zy-muted, #6e7b75); margin-bottom: 18rpx; }
.page-title { display: block; font-family: var(--zy-font-display, serif); font-size: 64rpx; font-weight: 500; line-height: 1.2; color: var(--zy-title, #111816); }
.page-subtitle { display: block; margin-top: 10rpx; font-size: var(--zy-text-caption, 24rpx); color: var(--zy-muted, #6e7b75); line-height: 1.7; }
.avatar-section { display: flex; flex-direction: row; align-items: center; justify-content: flex-start; gap: 24rpx; padding: 32rpx 0; border-bottom: 1rpx solid var(--zy-border, #d3ded8); }
.avatar-copy { display: flex; flex-direction: column; gap: 8rpx; min-width: 0; }
.avatar-title { font-family: var(--zy-font-display, serif); font-size: 32rpx; font-weight: 500; color: var(--zy-title, #111816); }
.avatar-ring { width: 104rpx; height: 104rpx; border-radius: var(--zy-radius-control, 18rpx); padding: 0; margin: 0; background: var(--zy-bg-sunken, #e9efeb); overflow: hidden; flex-shrink: 0; }
.avatar-ring::after { border: 0; }
.avatar-img, .avatar-placeholder { width: 100%; height: 100%; border-radius: var(--zy-radius-control, 18rpx); border: 0; }
.avatar-placeholder { display: flex; align-items: center; justify-content: center; background: var(--zy-bg-sunken, #e9efeb); }
.avatar-placeholder-text { font-family: var(--zy-font-display, serif); font-size: 48rpx; color: var(--zy-brand-700, #183369); }
.avatar-hint { font-size: var(--zy-text-caption, 24rpx); color: var(--zy-muted, #6e7b75); }
.section-card { margin-top: 0; padding: 36rpx 0; border: 0; border-bottom: 1rpx solid var(--zy-border, #d3ded8); border-radius: var(--zy-radius-control, 18rpx); background: transparent; box-shadow: none; }
.card-head { display: flex; align-items: center; gap: 14rpx; margin-bottom: 20rpx; }
.card-icon-wrap, .card-icon-security, .card-icon-memory { width: 40rpx; height: 40rpx; border-radius: var(--zy-radius-control, 18rpx); background: transparent !important; border: 1rpx solid var(--zy-border, #d3ded8); }
.section-heading { font-family: var(--zy-font-display, serif); font-size: 36rpx; font-weight: 500; color: var(--zy-title, #111816); }
.form-label, .form-readonly-text { color: var(--zy-text, #42524c); }
.btn-primary, .btn-primary--accent { width: 100%; height: 88rpx; border-radius: var(--zy-radius-control, 18rpx); background: var(--zy-brand-600, #24478f); margin-top: 28rpx; }
.btn-primary-text { font-size: var(--zy-text-body, 28rpx); letter-spacing: 1rpx; }
.nudge-toggle { border-radius: var(--zy-radius-control, 18rpx); border: 1rpx solid var(--zy-border, #d3ded8); background: transparent; }
.nudge-toggle.is-off { border-color: var(--zy-border, #d3ded8); color: var(--zy-muted, #6e7b75); }
.theme-option { border-radius: var(--zy-radius-control, 18rpx); border-width: 1rpx; padding: 16rpx; }
.theme-swatch { border-radius: var(--zy-radius-control, 18rpx); border-width: 1rpx; }
.theme-option-name { font-weight: 500; }
.footnote { color: var(--zy-muted, #6e7b75); }
</style>
