<template>
	<view class="page">
		<!-- 顶部导航 -->
		<view class="nav-bar">
			<view class="nav-back" @click="goBack">
				<wd-icon name="arrow-left" size="40rpx" color="#1F2937" />
			</view>
			<text class="nav-title">发布作品</text>
		</view>

		<!-- 步骤指示器 -->
		<view class="step-indicator">
			<view
				v-for="i in 3"
				:key="i"
				class="step-dot-wrap"
			>
				<view
					class="step-dot"
					:class="{ 'step-dot--active': currentStep >= i, 'step-dot--current': currentStep === i }"
				>
					<text class="step-dot-text">{{ currentStep > i ? '✓' : i }}</text>
				</view>
				<view v-if="i < 3" class="step-line" :class="{ 'step-line--active': currentStep > i }"></view>
			</view>
		</view>

		<!-- 步骤 1：选择作品媒体 -->
		<view v-if="currentStep === 1" class="step-content zy-slide-up">
			<view class="step-card">
				<text class="step-card-title">选择作品</text>
				<text class="step-card-sub">为你的作品选一种表达方式</text>

				<!-- 媒体类型 Tab -->
				<view class="media-tabs">
					<view
						class="media-tab"
						:class="{ 'media-tab--active': form.mediaType === 'image' }"
						@click="switchMediaTab('image')"
					>
						<wd-icon name="image" size="36rpx" :color="form.mediaType === 'image' ? '#2D9B8F' : '#9CA3AF'" />
						<text class="media-tab-text" :class="{ 'media-tab-text--active': form.mediaType === 'image' }">图片</text>
					</view>
					<view
						class="media-tab"
						:class="{ 'media-tab--active': form.mediaType === 'video' }"
						@click="switchMediaTab('video')"
					>
						<wd-icon name="video" size="36rpx" :color="form.mediaType === 'video' ? '#2D9B8F' : '#9CA3AF'" />
						<text class="media-tab-text" :class="{ 'media-tab-text--active': form.mediaType === 'video' }">视频</text>
					</view>
					<view
						class="media-tab"
						:class="{ 'media-tab--active': form.mediaType === 'text' }"
						@click="switchMediaTab('text')"
					>
						<wd-icon name="edit" size="36rpx" :color="form.mediaType === 'text' ? '#2D9B8F' : '#9CA3AF'" />
						<text class="media-tab-text" :class="{ 'media-tab-text--active': form.mediaType === 'text' }">纯文字</text>
					</view>
				</view>

				<!-- Tab 1：图片上传 -->
				<view v-if="form.mediaType === 'image'" class="media-content">
					<view class="image-upload-area" @click="chooseImage">
						<image v-if="form.coverUrl && !uploading" class="image-preview" :src="fullUrl(form.coverUrl)" mode="aspectFill" />
						<view v-else-if="uploading" class="upload-loading">
							<view class="loading-spinner"></view>
							<text class="upload-loading-text">上传中…</text>
						</view>
						<view v-else class="image-placeholder">
							<wd-icon name="add" size="48rpx" color="#9CA3AF" />
							<text class="image-placeholder-text">点击上传作品图</text>
							<text class="image-placeholder-hint">支持 jpg / jpeg / png / webp，≤5MB</text>
						</view>
					</view>
					<view v-if="form.coverUrl && !uploading" class="re-upload-btn" @click="chooseImage">
						<text class="re-upload-text">重新上传</text>
					</view>
				</view>

				<!-- Tab 2：视频上传 -->
				<view v-if="form.mediaType === 'video'" class="media-content">
					<view class="image-upload-area" @click="chooseVideo">
						<video
							v-if="form.videoUrl && !uploading"
							class="image-preview"
							:src="fullUrl(form.videoUrl)"
							controls
							object-fit="cover"
						/>
						<view v-else-if="uploading" class="upload-loading">
							<view class="upload-progress-bar">
								<view class="upload-progress-fill" :style="{ width: uploadProgress + '%' }"></view>
							</view>
							<text class="upload-loading-text">上传中 {{ uploadProgress }}%</text>
						</view>
						<view v-else class="image-placeholder">
							<wd-icon name="video" size="48rpx" color="#9CA3AF" />
							<text class="image-placeholder-text">点击上传作品视频</text>
							<text class="image-placeholder-hint">支持 mp4 / mov，≤50MB</text>
						</view>
					</view>
					<view v-if="form.videoUrl && !uploading" class="re-upload-btn" @click="chooseVideo">
						<text class="re-upload-text">重新上传</text>
					</view>
				</view>

				<!-- Tab 3：纯文字 -->
				<view v-if="form.mediaType === 'text'" class="media-content">
					<view class="text-only-hint">
						<view class="text-only-icon-wrap">
							<wd-icon name="edit" size="40rpx" color="#2D9B8F" />
						</view>
						<text class="text-only-title">只写文字，不配图</text>
						<text class="text-only-sub">用文字记录你的创作故事</text>
					</view>
				</view>
			</view>

			<view class="btn-row">
				<view class="btn-primary" :class="{ 'btn-primary--disabled': !canStep1 }" @click="nextStep">
					<text class="btn-primary-text">下一步</text>
				</view>
			</view>
		</view>

		<!-- 步骤 2：填写作品信息 -->
		<view v-if="currentStep === 2" class="step-content zy-slide-up">
			<view class="step-card">
				<text class="step-card-title">作品信息</text>
				<text class="step-card-sub">告诉大家这件作品的故事</text>

				<view class="form-item">
					<text class="form-label">作品标题 <text class="required">*</text></text>
					<wd-input
						v-model="form.title"
						placeholder="给作品起个名字"
						:maxlength="100"
						no-border
						custom-class="form-input-el"
					/>
				</view>

				<view class="form-item">
					<text class="form-label">创作故事</text>
					<wd-textarea
						v-model="form.message"
						placeholder="分享创作过程中的感受…"
						:maxlength="500"
						no-border
						custom-class="form-textarea-el"
					/>
					<text class="char-hint">{{ form.message.length }} / 500</text>
				</view>

				<view class="form-item">
					<text class="form-label">谁可以看到</text>
					<view class="radio-group">
						<view
							class="radio-item"
							:class="{ 'radio-active': form.visible === 1 }"
							@click="form.visible = 1"
						>
							<view class="radio-dot" :class="{ 'dot-active': form.visible === 1 }"></view>
							<text class="radio-label">公开</text>
						</view>
						<view
							class="radio-item"
							:class="{ 'radio-active': form.visible === 2 }"
							@click="form.visible = 2"
						>
							<view class="radio-dot" :class="{ 'dot-active': form.visible === 2 }"></view>
							<text class="radio-label">仅小组</text>
						</view>
						<view
							class="radio-item"
							:class="{ 'radio-active': form.visible === 0 }"
							@click="form.visible = 0"
						>
							<view class="radio-dot" :class="{ 'dot-active': form.visible === 0 }"></view>
							<text class="radio-label">私密</text>
						</view>
					</view>
				</view>

				<view class="form-item form-row">
					<view class="form-row-info">
						<text class="form-label">匿名发布</text>
						<text class="form-hint">隐藏昵称，显示为「匿名同学」</text>
					</view>
					<view
						class="switch"
						:class="{ 'switch-on': form.anonymous }"
						@click="form.anonymous = !form.anonymous"
					>
						<view class="switch-knob"></view>
					</view>
				</view>

				<!-- AI 寄语提示 -->
				<view class="ai-hint">
					<view class="ai-hint-icon-wrap">
						<wd-icon name="sparkles" size="36rpx" color="#2D9B8F" />
					</view>
					<view class="ai-hint-info">
						<text class="ai-hint-title">AI 寄语</text>
						<text class="ai-hint-text">作品发布后，AI 会根据你的创作过程自动生成一句温暖寄语</text>
					</view>
				</view>
			</view>

			<view class="btn-row">
				<view class="btn-secondary" @click="prevStep">
					<text class="btn-secondary-text">上一步</text>
				</view>
				<view class="btn-primary" :class="{ 'btn-primary--disabled': !canSubmit }" @click="nextStep">
					<text class="btn-primary-text">下一步</text>
				</view>
			</view>
		</view>

		<!-- 步骤 3：预览 + 发布 -->
		<view v-if="currentStep === 3" class="step-content zy-slide-up">
			<view class="step-card">
				<text class="step-card-title">预览作品</text>
				<text class="step-card-sub">确认无误后即可发布</text>

				<!-- 预览卡片 -->
				<view class="preview-card">
					<!-- 图片预览 -->
					<view v-if="form.mediaType === 'image' && form.coverUrl" class="preview-cover" :style="{ backgroundImage: 'url(' + fullUrl(form.coverUrl) + ')' }">
					</view>
					<!-- 视频预览 -->
					<view v-else-if="form.mediaType === 'video' && form.videoUrl" class="preview-cover preview-cover--video">
						<video class="preview-video" :src="fullUrl(form.videoUrl)" controls object-fit="cover" />
					</view>
					<!-- 纯文字/无封面 -->
					<view v-else class="preview-cover preview-cover--placeholder">
						<wd-icon name="edit" size="40rpx" color="#FFFFFF" />
						<text class="preview-cover-text">纯文字作品</text>
					</view>
					<view class="preview-body">
						<text class="preview-title">{{ form.title || '未填写标题' }}</text>
						<text v-if="form.message" class="preview-message">{{ form.message }}</text>
						<view class="preview-tags">
							<view class="preview-tag">
								<text class="preview-tag-text">{{ mediaTypeLabel }}</text>
							</view>
							<view class="preview-tag">
								<text class="preview-tag-text">{{ visibleLabel }}</text>
							</view>
							<view v-if="form.anonymous" class="preview-tag">
								<text class="preview-tag-text">匿名</text>
							</view>
						</view>
					</view>
				</view>
			</view>

			<view class="btn-row">
				<view class="btn-secondary" @click="prevStep">
					<text class="btn-secondary-text">上一步</text>
				</view>
				<view class="btn-primary" :class="{ 'btn-primary--disabled': !canSubmit || submitting }" @click="handleSubmit">
					<text class="btn-primary-text">{{ submitting ? '发布中…' : '发布作品' }}</text>
				</view>
			</view>
		</view>

		<!-- 发布成功页 -->
		<view v-if="published" class="success-overlay" @click.stop>
			<view class="success-card zy-elastic">
				<image class="success-xiaozhi" src="/static/xiaozhi-happy.png" mode="aspectFit" />
				<text class="success-title">发布成功！</text>
				<text class="success-hint">小织为你感到骄傲</text>
				<view class="success-btn" @click="goWorkList">
					<text class="success-btn-text">查看作品</text>
				</view>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { API_BASE_URL, TOKEN_KEY } from '../../utils/config.js'

const currentStep = ref(1)
const published = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)

const form = ref({
	coverUrl: '',
	videoUrl: '',
	mediaType: 'image',
	title: '',
	message: '',
	visible: 1,
	anonymous: true,
	sessionId: null,
	projectId: null,
})

const canStep1 = computed(() => {
	if (form.value.mediaType === 'image') return !!form.value.coverUrl
	if (form.value.mediaType === 'video') return !!form.value.videoUrl
	return true // text 类型不需要文件
})

const canSubmit = computed(() => form.value.title.trim().length > 0)

const visibleLabel = computed(() => {
	if (form.value.visible === 1) return '公开'
	if (form.value.visible === 2) return '仅小组'
	return '私密'
})

const mediaTypeLabel = computed(() => {
	if (form.value.mediaType === 'image') return '图片'
	if (form.value.mediaType === 'video') return '视频'
	return '纯文字'
})

onLoad((query) => {
	form.value.sessionId = query.sessionId || null
	form.value.projectId = query.projectId || null
	if (query.projectTitle) {
		form.value.title = decodeURIComponent(query.projectTitle)
	}
})

function switchMediaTab(type) {
	if (form.value.mediaType === type) return
	form.value.mediaType = type
	// 切换 tab 时清空之前选的媒体
	form.value.coverUrl = ''
	form.value.videoUrl = ''
}

function fullUrl(url) {
	if (!url) return ''
	if (url.startsWith('http')) return url
	return API_BASE_URL + url
}

function readToken() {
	try {
		return uni.getStorageSync(TOKEN_KEY) || ''
	} catch (e) {
		return ''
	}
}

function chooseImage() {
	if (uploading.value) return
	uni.chooseImage({
		count: 1,
		sizeType: ['compressed'],
		sourceType: ['album', 'camera'],
		success: (res) => {
			const tempPath = res.tempFilePaths[0]
			// 前端检查文件大小（5MB）
			// #ifdef H5
			const file = res.tempFiles && res.tempFiles[0]
			if (file && file.size > 5 * 1024 * 1024) {
				uni.showToast({ title: '图片不能超过 5MB', icon: 'none' })
				return
			}
			uploadImage(tempPath)
			// #endif
			// #ifndef H5
			uploadImage(tempPath)
			// #endif
		},
		fail: () => {},
	})
}

function uploadImage(filePath) {
	uploading.value = true
	uploadProgress.value = 0

	const token = readToken()
	const uploadTask = uni.uploadFile({
		url: `${API_BASE_URL}/api/files/upload/image`,
		filePath: filePath,
		name: 'file',
		header: {
			Authorization: `Bearer ${token}`,
		},
		success: (res) => {
			try {
				const data = JSON.parse(res.data)
				if (data.code === 0 && data.data && data.data.url) {
					form.value.coverUrl = data.data.url
				} else {
					uni.showToast({ title: data.message || '上传失败', icon: 'none' })
				}
			} catch (e) {
				uni.showToast({ title: '上传失败', icon: 'none' })
			}
		},
		fail: () => {
			uni.showToast({ title: '上传失败', icon: 'none' })
		},
		complete: () => {
			uploading.value = false
		},
	})

	uploadTask.onProgressUpdate((res) => {
		uploadProgress.value = res.progress
	})
}

function chooseVideo() {
	if (uploading.value) return
	uni.chooseVideo({
		sourceType: ['album', 'camera'],
		maxDuration: 60,
		success: (res) => {
			const tempPath = res.tempFilePath
			// 前端检查文件大小（50MB）
			if (res.size && res.size > 50 * 1024 * 1024) {
				uni.showToast({ title: '视频不能超过 50MB', icon: 'none' })
				return
			}
			uploadVideo(tempPath)
		},
		fail: () => {},
	})
}

function uploadVideo(filePath) {
	uploading.value = true
	uploadProgress.value = 0

	const token = readToken()
	const uploadTask = uni.uploadFile({
		url: `${API_BASE_URL}/api/files/upload/video`,
		filePath: filePath,
		name: 'file',
		header: {
			Authorization: `Bearer ${token}`,
		},
		success: (res) => {
			try {
				const data = JSON.parse(res.data)
				if (data.code === 0 && data.data && data.data.url) {
					form.value.videoUrl = data.data.url
				} else {
					uni.showToast({ title: data.message || '上传失败', icon: 'none' })
				}
			} catch (e) {
				uni.showToast({ title: '上传失败', icon: 'none' })
			}
		},
		fail: () => {
			uni.showToast({ title: '上传失败', icon: 'none' })
		},
		complete: () => {
			uploading.value = false
		},
	})

	uploadTask.onProgressUpdate((res) => {
		uploadProgress.value = res.progress
	})
}

function nextStep() {
	if (currentStep.value === 1) {
		if (!canStep1.value) {
			const hint = form.value.mediaType === 'image' ? '请上传图片' : form.value.mediaType === 'video' ? '请上传视频' : ''
			if (hint) uni.showToast({ title: hint, icon: 'none' })
			return
		}
		currentStep.value = 2
	} else if (currentStep.value === 2) {
		if (!canSubmit.value) {
			uni.showToast({ title: '请填写作品标题', icon: 'none' })
			return
		}
		currentStep.value = 3
	}
}

function prevStep() {
	if (currentStep.value > 1) {
		currentStep.value--
	}
}

function goBack() {
	uni.navigateBack()
}

async function handleSubmit() {
	if (!canSubmit.value || submitting.value) return

	submitting.value = true
	try {
		const body = {
			sessionId: form.value.sessionId,
			title: form.value.title.trim(),
			visible: form.value.visible,
			anonymous: form.value.anonymous,
			mediaType: form.value.mediaType,
		}
		if (form.value.message.trim()) {
			body.message = form.value.message.trim()
		}
		if (form.value.projectId) {
			body.projectId = form.value.projectId
		}
		// 根据媒体类型设置 URL
		if (form.value.mediaType === 'image') {
			body.coverUrl = form.value.coverUrl
		} else if (form.value.mediaType === 'video') {
			body.videoUrl = form.value.videoUrl
		}

		await http.post('/api/works', body)
		published.value = true
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '发布失败', icon: 'none' })
	} finally {
		submitting.value = false
	}
}

function goWorkList() {
	published.value = false
	uni.switchTab({ url: '/pages/work/list' })
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 0 32rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 导航栏 ── */
.nav-bar {
	display: flex;
	align-items: center;
	gap: 16rpx;
	padding: 20rpx 0;
}

.nav-back {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 64rpx;
	height: 64rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.nav-back:active {
	background-color: var(--zy-border, #E5E7EB);
}

.nav-title {
	font-family: var(--zy-serif);
	font-size: 36rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

/* ── 步骤指示器 ── */
.step-indicator {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 16rpx 0 32rpx;
	gap: 0;
}

.step-dot-wrap {
	display: flex;
	align-items: center;
}

.step-dot {
	width: 56rpx;
	height: 56rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-border, #E5E7EB);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: all var(--zy-duration-base, 250ms) var(--zy-ease-smooth, ease);
}

.step-dot--active {
	background-color: var(--zy-primary, #2D9B8F);
}

.step-dot--current {
	box-shadow: 0 0 0 6rpx rgba(45, 155, 143, 0.15);
}

.step-dot-text {
	font-size: 26rpx;
	font-weight: 600;
	color: #FFFFFF;
}

.step-line {
	width: 60rpx;
	height: 4rpx;
	background-color: var(--zy-border, #E5E7EB);
	transition: background-color var(--zy-duration-base, 250ms) var(--zy-ease-smooth, ease);
}

.step-line--active {
	background-color: var(--zy-primary, #2D9B8F);
}

/* ── 步骤内容 ── */
.step-content {
	padding-bottom: 32rpx;
}

.step-card {
	background-color: var(--zy-card-bg, #FFFFFF);
	border-radius: var(--zy-radius-lg, 32rpx);
	box-shadow: var(--zy-shadow-sm);
	padding: 36rpx 32rpx;
	margin-bottom: 24rpx;
}

.step-card-title {
	display: block;
	font-family: var(--zy-serif);
	font-size: 36rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	margin-bottom: 8rpx;
}

.step-card-sub {
	display: block;
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
	margin-bottom: 32rpx;
}

/* ── 媒体类型 Tab ── */
.media-tabs {
	display: flex;
	gap: 12rpx;
	margin-bottom: 32rpx;
}

.media-tab {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 8rpx;
	padding: 20rpx 0;
	border-radius: var(--zy-radius-md, 24rpx);
	border: 2rpx solid var(--zy-border, #E5E7EB);
	background-color: var(--zy-card-bg, #FFFFFF);
	transition: all var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.media-tab--active {
	border-color: var(--zy-primary, #2D9B8F);
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.media-tab-text {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.media-tab-text--active {
	color: var(--zy-primary, #2D9B8F);
	font-weight: 500;
}

/* ── 上传区域 ── */
.image-upload-area {
	width: 100%;
	height: 360rpx;
	border-radius: var(--zy-radius-md, 24rpx);
	overflow: hidden;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: var(--zy-bg, #F8F9FB);
	border: 2rpx dashed var(--zy-border, #E5E7EB);
	transition: border-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.image-upload-area:active {
	border-color: var(--zy-primary, #2D9B8F);
}

.image-preview {
	width: 100%;
	height: 100%;
}

.image-placeholder {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;
}

.image-placeholder-text {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

.image-placeholder-hint {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	opacity: 0.7;
}

/* ── 上传中状态 ── */
.upload-loading {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 16rpx;
}

.loading-spinner {
	width: 48rpx;
	height: 48rpx;
	border-radius: 50%;
	border: 4rpx solid var(--zy-border, #E5E7EB);
	border-top-color: var(--zy-primary, #2D9B8F);
	animation: spin 0.8s linear infinite;
}

@keyframes spin {
	to { transform: rotate(360deg); }
}

.upload-loading-text {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.upload-progress-bar {
	width: 300rpx;
	height: 8rpx;
	border-radius: 4rpx;
	background-color: var(--zy-border, #E5E7EB);
	overflow: hidden;
}

.upload-progress-fill {
	height: 100%;
	border-radius: 4rpx;
	background: linear-gradient(90deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
	transition: width 0.2s ease;
}

/* ── 重新上传按钮 ── */
.re-upload-btn {
	display: flex;
	justify-content: center;
	margin-top: 16rpx;
}

.re-upload-text {
	font-size: 26rpx;
	color: var(--zy-primary, #2D9B8F);
}

/* ── 纯文字提示 ── */
.text-only-hint {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;
	padding: 60rpx 0;
}

.text-only-icon-wrap {
	width: 96rpx;
	height: 96rpx;
	border-radius: 50%;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 8rpx;
}

.text-only-title {
	font-size: 30rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.text-only-sub {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 表单项 ── */
.form-item {
	display: flex;
	flex-direction: column;
	gap: 12rpx;
	margin-bottom: 32rpx;
}

.form-item:last-of-type {
	margin-bottom: 0;
}

.form-label {
	font-size: 28rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.required {
	color: var(--zy-danger, #FF6B6B);
}

.form-hint {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.char-hint {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: right;
}

/* ── radio ── */
.radio-group {
	display: flex;
	gap: 16rpx;
	flex-wrap: wrap;
}

.radio-item {
	display: flex;
	align-items: center;
	gap: 10rpx;
	padding: 16rpx 28rpx;
	border: 2rpx solid var(--zy-border, #E5E7EB);
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	transition: all var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.radio-active {
	border-color: var(--zy-primary, #2D9B8F);
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.radio-dot {
	width: 28rpx;
	height: 28rpx;
	border-radius: 50%;
	border: 4rpx solid var(--zy-border, #E5E7EB);
	background-color: var(--zy-card-bg, #FFFFFF);
	transition: all var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.dot-active {
	border-color: var(--zy-primary, #2D9B8F);
	background-color: var(--zy-primary, #2D9B8F);
	box-shadow: inset 0 0 0 4rpx var(--zy-card-bg, #FFFFFF);
}

.radio-label {
	font-size: 28rpx;
	color: var(--zy-content, #4B5563);
}

/* ── switch ── */
.form-row {
	flex-direction: row;
	align-items: center;
	justify-content: space-between;
}

.form-row-info {
	display: flex;
	flex-direction: column;
	gap: 4rpx;
}

.switch {
	width: 88rpx;
	height: 48rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-border, #E5E7EB);
	position: relative;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.switch-on {
	background-color: var(--zy-primary, #2D9B8F);
}

.switch-knob {
	position: absolute;
	top: 4rpx;
	left: 4rpx;
	width: 40rpx;
	height: 40rpx;
	border-radius: 50%;
	background-color: #FFFFFF;
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.switch-on .switch-knob {
	transform: translateX(40rpx);
}

/* ── AI 寄语提示 ── */
.ai-hint {
	display: flex;
	align-items: flex-start;
	gap: 20rpx;
	padding: 24rpx;
	border-radius: var(--zy-radius-md, 24rpx);
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	border: 1rpx solid rgba(45, 155, 143, 0.12);
	margin-top: 8rpx;
}

.ai-hint-icon-wrap {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background-color: rgba(45, 155, 143, 0.1);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.ai-hint-info {
	flex: 1;
	display: flex;
	flex-direction: column;
	gap: 6rpx;
}

.ai-hint-title {
	font-size: 28rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.ai-hint-text {
	font-size: 24rpx;
	color: var(--zy-content, #4B5563);
	line-height: 1.5;
}

/* ── 预览卡片 ── */
.preview-card {
	overflow: hidden;
	border-radius: var(--zy-radius-md, 24rpx);
	background-color: var(--zy-bg, #F8F9FB);
	border: 1rpx solid var(--zy-border, #E5E7EB);
}

.preview-cover {
	height: 320rpx;
	background-size: cover;
	background-position: center;
	display: flex;
	align-items: center;
	justify-content: center;
}

.preview-cover--video {
	padding: 0;
	overflow: hidden;
}

.preview-video {
	width: 100%;
	height: 100%;
}

.preview-cover--placeholder {
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
}

.preview-cover-text {
	font-size: 24rpx;
	color: rgba(255, 255, 255, 0.8);
	margin-top: 8rpx;
}

.preview-body {
	padding: 24rpx;
}

.preview-title {
	display: block;
	font-family: var(--zy-serif);
	font-size: 34rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	margin-bottom: 12rpx;
}

.preview-message {
	display: block;
	font-size: 26rpx;
	line-height: 1.6;
	color: var(--zy-content, #4B5563);
	margin-bottom: 16rpx;
}

.preview-tags {
	display: flex;
	gap: 12rpx;
}

.preview-tag {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.preview-tag-text {
	font-size: 22rpx;
	color: var(--zy-primary, #2D9B8F);
}

/* ── 按钮行 ── */
.btn-row {
	display: flex;
	gap: 20rpx;
}

.btn-primary {
	flex: 1;
	height: 88rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.btn-primary:active {
	transform: scale(0.97);
}

.btn-primary--disabled {
	opacity: 0.4;
}

.btn-primary-text {
	font-size: 32rpx;
	font-weight: 500;
	color: #FFFFFF;
	letter-spacing: 2rpx;
}

.btn-secondary {
	flex: 1;
	height: 88rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.btn-secondary:active {
	background-color: var(--zy-bg, #F8F9FB);
}

.btn-secondary-text {
	font-size: 30rpx;
	color: var(--zy-content, #4B5563);
}

/* ── 发布成功 ── */
.success-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(31, 41, 55, 0.5);
	z-index: 9998;
	display: flex;
	align-items: center;
	justify-content: center;
}

.success-card {
	width: 80%;
	padding: 48rpx 40rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 16rpx;
}

.success-xiaozhi {
	width: 120rpx;
	height: 160rpx;
}

.success-title {
	font-family: var(--zy-serif);
	font-size: 36rpx;
	font-weight: 600;
	color: var(--zy-primary, #2D9B8F);
}

.success-hint {
	font-size: 28rpx;
	color: var(--zy-content, #4B5563);
}

.success-btn {
	margin-top: 16rpx;
	padding: 20rpx 56rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.success-btn:active {
	transform: scale(0.97);
}

.success-btn-text {
	font-size: 30rpx;
	font-weight: 500;
	color: #FFFFFF;
}

/* ── 底部免责 ── */
.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
