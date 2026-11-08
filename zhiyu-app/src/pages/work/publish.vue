<template>
	<view class="page">
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
				<text class="step-card-sub">{{form.groupId?'分享给当前小组，图片与视频都受成员权限保护':'为你的作品选一种表达方式'}}</text>

				<!-- 媒体类型 Tab -->
				<view class="media-tabs" role="tablist" aria-label="作品媒体类型">
					<view
						class="media-tab"
						:class="{ 'media-tab--active': form.mediaType === 'image' }"
						role="tab"
						:aria-selected="form.mediaType === 'image'"
						:tabindex="form.mediaType === 'image' ? 0 : -1"
						@click="switchMediaTab('image')"
						@keydown.enter.prevent="switchMediaTab('image')"
						@keydown.space.prevent="switchMediaTab('image')"
					>
						<wd-icon name="image" size="36rpx" :color="form.mediaType === 'image' ? '#217e76' : '#4a5f54'" />
						<text class="media-tab-text" :class="{ 'media-tab-text--active': form.mediaType === 'image' }">图片</text>
					</view>
					<view
						class="media-tab"
						:class="{ 'media-tab--active': form.mediaType === 'video' }"
						role="tab"
						:aria-selected="form.mediaType === 'video'"
						:tabindex="form.mediaType === 'video' ? 0 : -1"
						@click="switchMediaTab('video')"
						@keydown.enter.prevent="switchMediaTab('video')"
						@keydown.space.prevent="switchMediaTab('video')"
					>
						<wd-icon name="video" size="36rpx" :color="form.mediaType === 'video' ? '#217e76' : '#4a5f54'" />
						<text class="media-tab-text" :class="{ 'media-tab-text--active': form.mediaType === 'video' }">视频</text>
					</view>
					<view
						class="media-tab"
						:class="{ 'media-tab--active': form.mediaType === 'text' }"
						role="tab"
						:aria-selected="form.mediaType === 'text'"
						:tabindex="form.mediaType === 'text' ? 0 : -1"
						@click="switchMediaTab('text')"
						@keydown.enter.prevent="switchMediaTab('text')"
						@keydown.space.prevent="switchMediaTab('text')"
					>
						<wd-icon name="edit" size="36rpx" :color="form.mediaType === 'text' ? '#217e76' : '#4a5f54'" />
						<text class="media-tab-text" :class="{ 'media-tab-text--active': form.mediaType === 'text' }">纯文字</text>
					</view>
				</view>

				<!-- Tab 1：图片上传 -->
				<view v-if="form.mediaType === 'image'" class="media-content">
					<view class="image-upload-area" role="button" tabindex="0" aria-label="上传作品图片" @click="chooseImage" @keydown.enter.prevent="chooseImage" @keydown.space.prevent="chooseImage">
						<image v-if="form.coverUrl && !uploading" class="image-preview" :src="imagePreview||fullUrl(form.coverUrl)" mode="aspectFill" alt="已上传封面的预览" />
						<view v-else-if="uploading" class="upload-loading">
							<StateBlock type="loading" title="上传中…" compact />
						</view>
						<view v-else class="image-placeholder">
							<wd-icon name="add" size="48rpx" color="#4a5f54" />
							<text class="image-placeholder-text">点击上传作品图</text>
							<text class="image-placeholder-hint">支持 jpg / jpeg / png / webp，≤5MB</text>
						</view>
					</view>
					<view v-if="form.coverUrl && !uploading" class="re-upload-btn" role="button" tabindex="0" aria-label="重新上传作品图片" @click="chooseImage" @keydown.enter.prevent="chooseImage" @keydown.space.prevent="chooseImage">
						<text class="re-upload-text">重新上传</text>
					</view>
				</view>

				<!-- Tab 2：视频上传 -->
				<view v-if="form.mediaType === 'video'" class="media-content">
					<view class="image-upload-area" role="button" tabindex="0" aria-label="上传作品视频" @click="!form.videoUrl && chooseVideo()" @keydown.enter.prevent="!form.videoUrl && chooseVideo()" @keydown.space.prevent="!form.videoUrl && chooseVideo()">
						<MediaVideo v-if="form.videoUrl && !uploading" :src="form.videoUrl" caption="发布前预览"/>
						<view v-else-if="uploading" class="upload-loading">
							<view class="upload-progress-bar">
								<view class="upload-progress-fill" :style="{ width: uploadProgress + '%' }"></view>
							</view>
							<text class="upload-loading-text">上传中 {{ uploadProgress }}%</text>
						</view>
						<view v-else class="image-placeholder">
							<wd-icon name="video" size="48rpx" color="#4a5f54" />
							<text class="image-placeholder-text">点击上传作品视频</text>
							<text class="image-placeholder-hint">MP4 · ≤50MB · 建议 H.264 + AAC</text>
						</view>
					</view>
					<view v-if="form.videoUrl && !uploading" class="re-upload-btn" role="button" tabindex="0" aria-label="重新上传作品视频" @click="chooseVideo" @keydown.enter.prevent="chooseVideo" @keydown.space.prevent="chooseVideo">
						<text class="re-upload-text">重新上传</text>
					</view>
				</view>

				<view v-if="uploading" class="upload-actions"><text>上传中 {{uploadProgress}}%</text><AccessibleButton @click.stop="cancelUpload">取消上传</AccessibleButton></view><view v-if="uploadError" class="upload-error"><text>{{uploadError}}</text><AccessibleButton @click="retryUpload">重试上传</AccessibleButton></view><text v-if="form.mediaType==='video'" class="video-notice">仅上传原创或已获授权的视频。公开前需审核，请勿包含未经同意的人像、声音与个人信息。</text><!-- Tab 3：纯文字 -->
				<view v-if="form.mediaType === 'text'" class="media-content">
					<view class="text-only-hint">
						<view class="text-only-icon-wrap">
							<wd-icon name="edit" size="40rpx" color="#217e76" />
						</view>
						<text class="text-only-title">只写文字，不配图</text>
						<text class="text-only-sub">用文字记录你的创作故事</text>
					</view>
				</view>
			</view>

			<view class="btn-row">
				<view class="btn-primary" role="button" :tabindex="canStep1 ? 0 : -1" :aria-disabled="!canStep1" :class="{ 'btn-primary--disabled': !canStep1 }" @click="nextStep" @keydown.enter.prevent="nextStep" @keydown.space.prevent="nextStep">
					<text class="btn-primary-text">下一步</text>
				</view>
			</view>
		</view>

		<!-- 步骤 2：填写作品信息 -->
		<view v-if="currentStep === 2" class="step-content zy-slide-up">
			<view class="step-card">
				<text class="step-card-title">作品信息</text>
				<text class="step-card-sub">告诉大家这件作品的故事</text>

				<FormField label="作品标题" required :error="errors.title">
					<template #default="{ id }">
						<wd-input
							:id="id"
							v-model="form.title"
							placeholder="给作品起个名字"
							:maxlength="100"
							no-border
							custom-class="form-input-el"
							@input="errors.title = ''"
						/>
					</template>
				</FormField>

				<FormField
					label="创作故事"
					optional-text="选填"
					:maxlength="500"
					:count="form.message.length"
				>
					<template #default="{ id }">
						<wd-textarea
							:id="id"
							v-model="form.message"
							placeholder="分享创作过程中的感受…"
							:maxlength="500"
							no-border
							custom-class="form-textarea-el"
						/>
					</template>
				</FormField>
				<text v-if="prefilledMessage" class="char-hint">已带入小织回顾，可修改或删除。</text>

				<view class="form-item">
					<text class="form-label">谁可以看到</text>
					<view class="radio-group" role="radiogroup" aria-label="谁可以看到作品"><AccessibleButton v-if="form.groupId" class="radio-item" role="radio" :class="{'radio-active':form.visible===2}" :aria-checked="form.visible===2" @click="form.visible=2">仅当前小组</AccessibleButton>
						<view
							class="radio-item"
							role="radio"
							tabindex="0"
							:aria-checked="form.visible === 1"
							:class="{ 'radio-active': form.visible === 1 }"
							@click="form.visible = 1"
							@keydown.enter.prevent="form.visible = 1"
							@keydown.space.prevent="form.visible = 1"
						>
							<view class="radio-dot" :class="{ 'dot-active': form.visible === 1 }"></view>
							<text class="radio-label">公开</text>
						</view>
						<view
							class="radio-item"
							role="radio"
							tabindex="0"
							:aria-checked="form.visible === 0"
							:class="{ 'radio-active': form.visible === 0 }"
							@click="form.visible = 0"
							@keydown.enter.prevent="form.visible = 0"
							@keydown.space.prevent="form.visible = 0"
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
						role="switch"
						tabindex="0"
						aria-label="匿名发布"
						:aria-checked="form.anonymous"
						:class="{ 'switch-on': form.anonymous }"
						@click="form.anonymous = !form.anonymous"
						@keydown.enter.prevent="form.anonymous = !form.anonymous"
						@keydown.space.prevent="form.anonymous = !form.anonymous"
					>
						<view class="switch-knob"></view>
					</view>
				</view>

				<!-- AI 寄语提示 -->
				<view class="ai-hint">
					<view class="ai-hint-icon-wrap">
						<wd-icon name="sparkles" size="36rpx" color="#217e76" />
					</view>
					<view class="ai-hint-info">
						<text class="ai-hint-title">AI 寄语</text>
						<text class="ai-hint-text">作品发布后，AI 会根据你的创作过程自动生成一句温暖寄语</text>
					</view>
				</view>
			</view>

			<view class="btn-row">
				<view class="btn-secondary" role="button" tabindex="0" @click="prevStep" @keydown.enter.prevent="prevStep" @keydown.space.prevent="prevStep">
					<text class="btn-secondary-text">上一步</text>
				</view>
				<view class="btn-primary" role="button" :tabindex="canSubmit ? 0 : -1" :aria-disabled="!canSubmit" :class="{ 'btn-primary--disabled': !canSubmit }" @click="nextStep" @keydown.enter.prevent="nextStep" @keydown.space.prevent="nextStep">
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
					<view v-if="form.mediaType === 'image' && form.coverUrl" class="preview-cover" :style="{ backgroundImage: 'url(' + (imagePreview||fullUrl(form.coverUrl)) + ')' }">
					</view>
					<!-- 视频预览 -->
					<view v-else-if="form.mediaType === 'video' && form.videoUrl" class="preview-cover preview-cover--video">
						<MediaVideo :src="form.videoUrl" caption="发布前预览"/>
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
				<view class="btn-secondary" role="button" tabindex="0" @click="prevStep" @keydown.enter.prevent="prevStep" @keydown.space.prevent="prevStep">
					<text class="btn-secondary-text">上一步</text>
				</view>
				<view class="btn-primary" role="button" :tabindex="canSubmit && !submitting ? 0 : -1" :aria-disabled="!canSubmit || submitting" :class="{ 'btn-primary--disabled': !canSubmit || submitting }" @click="handleSubmit" @keydown.enter.prevent="handleSubmit" @keydown.space.prevent="handleSubmit">
					<text class="btn-primary-text">{{ submitting ? '发布中…' : '发布作品' }}</text>
				</view>
			</view>
		</view>

		<!-- 发布成功页（先审后发：统一为「已提交」口径，保留成功感、不承诺即公开） -->
		<view v-if="published" class="success-overlay" @click.stop>
			<view class="success-card zy-elastic">
				<XiaotuanPet :size="72" face="happy" aria-hidden="true" />
				<text class="success-title">已提交，审核通过后公开</text>
				<text class="success-hint">小织已经收到你的作品。通过前仅你自己可见，进度随时可在「我的作品」查看。</text>
				<view class="success-btn" role="button" tabindex="0" @click="goMine" @keydown.enter.prevent="goMine" @keydown.space.prevent="goMine">
					<text class="success-btn-text">去我的作品看看</text>
				</view>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
		<Toast />
	</view>
</template>

<script setup>
import MediaVideo from "../../components/MediaVideo.vue"
import StateBlock from '../../components/StateBlock.vue'
import FormField from '../../components/FormField.vue'
import Toast from '../../components/Toast.vue'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import { ref, computed, reactive } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { invalidate, TTL_KEYS } from '../../utils/ttlCache'
import { API_BASE_URL, TOKEN_KEY } from '../../utils/config.js'
import { useToast } from '../../composables/useToast'

const toast = useToast()

const imagePreview=ref('')
const currentStep = ref(1)
const published = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const prefilledMessage = ref(false)

const errors = reactive({
	title: '',
})

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
 if(uploading.value)return false;
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
	form.value.groupId=query.groupId||null
 if(form.value.groupId)form.value.visible=2
 form.value.sessionId = query.sessionId || null
	form.value.projectId = query.projectId || null
	if (query.projectTitle) {
		form.value.title = decodeURIComponent(query.projectTitle)
	}
	if (query.message) {
		try {
			form.value.message = decodeURIComponent(String(query.message)).slice(0, 500)
		} catch (e) {
			form.value.message = String(query.message).slice(0, 500)
		}
		prefilledMessage.value = true
	}
})

function switchMediaTab(type) {
 if(uploading.value)return;
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
				toast.care('图片不能超过 5MB')
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

function uploadImage(filePath){imagePreview.value=filePath;uploadMedia(filePath,'private-image')}

function chooseVideo() {
	if (uploading.value) return
	uni.chooseVideo({
		sourceType: ['album', 'camera'],
		maxDuration: 60,
		success: (res) => {
			const tempPath = res.tempFilePath
			// 前端检查文件大小（50MB）
			if (res.size && res.size > 50 * 1024 * 1024) {
				toast.care('视频不能超过 50MB')
				return
			}
			uploadVideo(tempPath)
		},
		fail: () => {},
	})
}

let activeTask=null,lastUpload=null,uploadTurn=0
const uploadError=ref('')
function cancelUpload(){uploadTurn++;activeTask?.abort();activeTask=null;uploading.value=false;uploadError.value='已取消上传，可重新选择或重试。'}
function retryUpload(){if(lastUpload)uploadMedia(lastUpload.path,lastUpload.kind)}
function uploadVideo(filePath){uploadMedia(filePath,'video')}
function uploadMedia(path,kind){const turn=++uploadTurn;lastUpload={path,kind};uploading.value=true;uploadError.value='';uploadProgress.value=0;activeTask=uni.uploadFile({url:API_BASE_URL+'/api/files/upload/'+kind,filePath:path,name:'file',header:{Authorization:'Bearer '+readToken()},timeout:180000,success(res){if(turn!==uploadTurn)return;try{const d=JSON.parse(res.data);if(res.statusCode>=400||d.code!==0)throw Error(d.message||'上传失败');if(kind==='video')form.value.videoUrl=d.data.url;else form.value.coverUrl=d.data.url;}catch(e){uploadError.value=e.message||'上传失败'}},fail(){if(turn===uploadTurn)uploadError.value='上传失败，请检查网络后重试。'},complete(){if(turn===uploadTurn){activeTask=null;uploading.value=false}}});activeTask.onProgressUpdate(e=>{if(turn===uploadTurn)uploadProgress.value=e.progress})}
onUnload(()=>{uploadTurn++;activeTask?.abort()})
function nextStep() {
	if (currentStep.value === 1) {
		if (!canStep1.value) {
			const hint = form.value.mediaType === 'image' ? '请上传图片' : form.value.mediaType === 'video' ? '请上传视频' : ''
			if (hint) toast.care(hint)
			return
		}
		currentStep.value = 2
	} else if (currentStep.value === 2) {
		if (!canSubmit.value) {
			errors.title = '请填写作品标题'
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
	if (submitting.value) return
	if (!canSubmit.value) {
		errors.title = '请填写作品标题'
		return
	}

	submitting.value = true
	try {
		const body = {
			sessionId: form.value.sessionId,
			title: form.value.title.trim(),
			visible: form.value.visible,
            groupId: form.value.visible===2?form.value.groupId:null,
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
		invalidate(TTL_KEYS.userStats)
		published.value = true
	} catch (e) {
		toast.care((e && e.message) || '发布失败')
	} finally {
		submitting.value = false
	}
}

function goMine() {
	published.value = false
	// 先审后发：新作处于审核中，不会出现在社区；引导去「我的作品」看进度
	uni.navigateTo({ url: '/pages/work/mine' })
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 0 32rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #f5f7f2);
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
	background-color: var(--zy-border, #dfe7dc);
}

.nav-title {
	font-family: var(--zy-serif);
	font-size: 36rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
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
	background-color: var(--zy-border, #dfe7dc);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: all var(--zy-duration-base, 250ms) var(--zy-ease-smooth, ease);
}

.step-dot--active {
	background-color: var(--zy-primary, #217e76);
}

.step-dot--current {
	box-shadow: 0 0 0 6rpx rgba(33, 126, 118, 0.15);
}

.step-dot-text {
	font-size: 26rpx;
	font-weight: 600;
	color: var(--zy-text, #4a5f54);
}
/* AV 巡检修复：未激活步骤圆点是浅灰绿底，白字仅 1.26:1；激活态才用白字 */
.step-dot--active .step-dot-text {
	color: #FFFFFF;
}

.step-line {
	width: 60rpx;
	height: 4rpx;
	background-color: var(--zy-border, #dfe7dc);
	transition: background-color var(--zy-duration-base, 250ms) var(--zy-ease-smooth, ease);
}

.step-line--active {
	background-color: var(--zy-primary, #217e76);
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
	color: var(--zy-title, #233d34);
	margin-bottom: 8rpx;
}

.step-card-sub {
	display: block;
	font-size: 26rpx;
	color: var(--zy-text, #4a5f54);
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
	border: 2rpx solid var(--zy-border, #dfe7dc);
	background-color: var(--zy-card-bg, #FFFFFF);
	transition: all var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.media-tab--active {
	border-color: var(--zy-primary, #217e76);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.media-tab-text {
	font-size: 24rpx;
	color: var(--zy-text, #4a5f54);
}

.media-tab-text--active {
	color: var(--zy-brand-700, #1a645e);
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
	background-color: var(--zy-bg, #f5f7f2);
	border: 2rpx dashed var(--zy-border, #dfe7dc);
	transition: border-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.image-upload-area:active {
	border-color: var(--zy-primary, #217e76);
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
	color: var(--zy-text, #4a5f54);
}

.image-placeholder-hint {
	font-size: 22rpx;
	color: var(--zy-text, #4a5f54);
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
	border: 4rpx solid var(--zy-border, #dfe7dc);
	border-top-color: var(--zy-primary, #217e76);
	animation: spin 0.8s linear infinite;
}

@keyframes spin {
	to { transform: rotate(360deg); }
}

.upload-loading-text {
	font-size: 24rpx;
	color: var(--zy-text, #4a5f54);
}

.upload-progress-bar {
	width: 300rpx;
	height: 8rpx;
	border-radius: 4rpx;
	background-color: var(--zy-border, #dfe7dc);
	overflow: hidden;
}

.upload-progress-fill {
	height: 100%;
	border-radius: 4rpx;
	background: linear-gradient(90deg, var(--zy-primary, #217e76), var(--zy-brand-300, #5bc4a8));
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
	color: var(--zy-brand-700, #217e76);
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
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 8rpx;
}

.text-only-title {
	font-size: 30rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
}

.text-only-sub {
	font-size: 26rpx;
	color: var(--zy-text, #4a5f54);
}

/* ── 表单项 ── */
.form-field {
	margin-bottom: 32rpx;
}

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
	color: var(--zy-title, #233d34);
}

.required {
	color: var(--zy-care, #a64842);
}

.form-hint {
	font-size: 24rpx;
	color: var(--zy-text, #4a5f54);
}

.char-hint {
	font-size: 22rpx;
	color: var(--zy-text, #4a5f54);
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
	border: 2rpx solid var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	transition: all var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.radio-active {
	border-color: var(--zy-primary, #217e76);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.radio-dot {
	width: 28rpx;
	height: 28rpx;
	border-radius: 50%;
	border: 4rpx solid var(--zy-border, #dfe7dc);
	background-color: var(--zy-card-bg, #FFFFFF);
	transition: all var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.dot-active {
	border-color: var(--zy-primary, #217e76);
	background-color: var(--zy-primary, #217e76);
	box-shadow: inset 0 0 0 4rpx var(--zy-card-bg, #FFFFFF);
}

.radio-label {
	font-size: 28rpx;
	color: var(--zy-content, #4a5f54);
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
	background-color: var(--zy-border, #dfe7dc);
	position: relative;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.switch-on {
	background-color: var(--zy-primary, #217e76);
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
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	border: 1rpx solid rgba(33, 126, 118, 0.12);
	margin-top: 8rpx;
}

.ai-hint-icon-wrap {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background-color: rgba(33, 126, 118, 0.1);
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
	color: var(--zy-title, #233d34);
}

.ai-hint-text {
	font-size: 24rpx;
	color: var(--zy-content, #4a5f54);
	line-height: 1.5;
}

/* ── 预览卡片 ── */
.preview-card {
	overflow: hidden;
	border-radius: var(--zy-radius-md, 24rpx);
	background-color: var(--zy-bg, #f5f7f2);
	border: 1rpx solid var(--zy-border, #dfe7dc);
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
	background: linear-gradient(135deg, var(--zy-primary, #217e76), var(--zy-brand-300, #5bc4a8));
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
	color: var(--zy-title, #233d34);
	margin-bottom: 12rpx;
}

.preview-message {
	display: block;
	font-size: 26rpx;
	line-height: 1.6;
	color: var(--zy-content, #4a5f54);
	margin-bottom: 16rpx;
}

.preview-tags {
	display: flex;
	gap: 12rpx;
}

.preview-tag {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.preview-tag-text {
	font-size: 22rpx;
	color: var(--zy-brand-700, #217e76);
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
	background: var(--zy-brand-600, #217e76);
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
	border: 1rpx solid var(--zy-border, #dfe7dc);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.btn-secondary:active {
	background-color: var(--zy-bg, #f5f7f2);
}

.btn-secondary-text {
	font-size: 30rpx;
	color: var(--zy-content, #4a5f54);
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
	color: var(--zy-brand-700, #217e76);
}

.success-hint {
	font-size: 28rpx;
	color: var(--zy-content, #4a5f54);
}

.success-btn {
	margin-top: 16rpx;
	padding: 20rpx 56rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-brand-600, #217e76);
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
	color: var(--zy-text, #4a5f54);
	text-align: center;
}
</style>

<style scoped>.upload-actions,.upload-error{padding:20rpx;font-size:25rpx;line-height:1.8}.upload-error{color:#a54836;background:#fff4ef;border-radius:14rpx}.upload-actions button,.upload-error button{font-size:24rpx;color:var(--zy-brand-700);background:var(--zy-bg-tint-mint);margin-top:12rpx}.video-notice{display:block;font-size:23rpx;color:var(--zy-content);line-height:1.8;margin:20rpx 0}</style>

<style scoped>
.media-tab:focus-visible,
.image-upload-area:focus-visible,
.re-upload-btn:focus-visible,
.radio-item:focus-visible,
.switch:focus-visible {
	outline: 3px solid var(--zy-primary, #217e76);
	outline-offset: 3px;
}

button:focus-visible {
	outline: 3px solid var(--zy-brand-700, #1a645e);
	outline-offset: 3px;
}
</style>
