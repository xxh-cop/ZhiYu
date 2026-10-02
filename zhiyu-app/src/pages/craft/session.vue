<template>
	<view class="page">
		<!-- 顶部进度条 -->
		<view class="progress-bar">
			<view class="progress-track">
				<view class="progress-fill" :style="{ width: progressPercent + '%' }"></view>
			</view>
			<text class="progress-text">{{ currentStep }} / {{ totalSteps }}</text>
		</view>

		<!-- 心流提示 -->
		<view v-if="craftStore.inFlow" class="flow-hint">
			<text class="flow-text">专注中…</text>
		</view>

		<!-- 步骤内容区 -->
		<scroll-view class="step-area" scroll-y>
			<view class="step-content">
				<text class="step-title">{{ currentStepData.title }}</text>
				<text class="step-guide">{{ currentStepData.guide }}</text>

				<!-- 音频播放占位 -->
				<view v-if="currentStepData.audioUrl" class="audio-btn" @click="handleAudio">
					<text class="audio-icon">🔊</text>
					<text class="audio-text">播放引导语音</text>
				</view>
			</view>
		</scroll-view>

		<!-- 操作按钮区 -->
		<view class="action-area">
			<view class="step-btns">
				<view
					v-if="!craftStore.inFlow && currentStep > 1"
					class="step-btn btn-prev"
					@click="handlePrev"
				>
					<text class="btn-text">上一步</text>
				</view>
				<view
					v-if="currentStep < totalSteps"
					class="step-btn btn-next"
					@click="handleNext"
				>
					<text class="btn-text">下一步</text>
				</view>
				<view
					v-if="currentStep === totalSteps"
					class="step-btn btn-finish"
					@click="handleFinish"
				>
					<text class="btn-text">完成创作</text>
				</view>
			</view>

			<!-- 底部状态操作 -->
			<view class="bottom-actions">
				<view v-if="craftStore.status === 1" class="action-btn" @click="handlePause">
					<text class="action-text">暂停</text>
				</view>
				<view v-if="craftStore.status === 2" class="action-btn btn-resume" @click="handleResume">
					<text class="action-text">继续</text>
				</view>
				<view class="action-btn btn-abandon" @click="handleAbandon">
					<text class="action-text">放弃</text>
				</view>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>

		<!-- 呼吸动画覆盖层 -->
		<BreathingAnimation
			v-if="showBreathing"
			@finish="onBreathingFinish"
		/>

		<!-- 完成成功卡片 -->
		<view v-if="showSuccess" class="success-overlay" @click.stop>
			<view class="success-card">
				<text class="success-emoji">🎉</text>
				<text class="success-title">创作完成！</text>
				<text class="success-hint">你用双手完成了一件作品，很棒</text>
				<view class="success-btns">
					<view class="success-btn btn-primary" @click="handlePublish">
						<text class="success-btn-text">去发布作品</text>
					</view>
					<view class="success-btn btn-secondary" @click="handleBackHome">
						<text class="success-btn-text">回到首页</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 加载中 -->
		<view v-if="pageLoading" class="loading-overlay">
			<text class="loading-text">正在准备…</text>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useCraftStore } from '../../stores/craft'
import { http } from '../../utils/request'
import BreathingAnimation from '../../components/BreathingAnimation.vue'

const craftStore = useCraftStore()

const pageLoading = ref(true)
const showBreathing = ref(false)
const showSuccess = ref(false)

let sessionId = null
let stepStartTime = Date.now()
let heartbeatTimer = null
let heartbeatInFlight = false
let isExited = false

const currentStep = computed(() => craftStore.currentStep)
const totalSteps = computed(() => craftStore.totalSteps)
const progressPercent = computed(() => {
	if (!totalSteps.value) return 0
	return Math.round((currentStep.value / totalSteps.value) * 100)
})

const currentStepData = computed(() => {
	const steps = craftStore.steps
	if (!steps || steps.length === 0) return { title: '', guide: '', audioUrl: null }
	const step = steps.find((s) => s.stepNo === currentStep.value)
	return step || { title: '', guide: '', audioUrl: null }
})

// TODO(M2): 支持从 URL 带 sessionId 恢复历史会话
onLoad(async (query) => {
	const projectId = query.projectId
	const projectTitle = query.projectTitle ? decodeURIComponent(query.projectTitle) : ''

	if (!projectId) {
		uni.showToast({ title: '缺少项目参数', icon: 'none' })
		setTimeout(() => uni.navigateBack(), 1500)
		return
	}

	try {
		// 1. 创建会话
		const sessionVO = await http.post('/api/craft/session', { projectId: Number(projectId) })
		sessionId = sessionVO.sessionId
		craftStore.setSession(sessionVO)
		if (projectTitle) {
			craftStore.projectTitle = projectTitle
		}

		// 2. 获取步骤
		const steps = await http.get(`/api/craft/projects/${projectId}/steps`)
		craftStore.setSteps(steps)

		// 3. 启动计时 + 心跳
		stepStartTime = Date.now()
		startHeartbeatTimer()
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
		setTimeout(() => uni.navigateBack(), 1500)
		return
	} finally {
		pageLoading.value = false
	}
})

onUnload(() => {
	stopHeartbeatTimer()
	if (sessionId && !isExited && craftStore.status === 1) {
		isExited = true
		sendHeartbeat({
			stepNo: currentStep.value,
			dwellSeconds: getDwellSeconds(),
			isExited: true,
			isBreathingResponded: false,
		})
	}
})

// ── 心跳 ──

function startHeartbeatTimer() {
	stopHeartbeatTimer()
	heartbeatTimer = setInterval(() => {
		if (craftStore.status !== 1) return
		sendHeartbeat({
			stepNo: currentStep.value,
			dwellSeconds: getDwellSeconds(),
			isExited: false,
			isBreathingResponded: false,
		})
	}, 30000)
}

function stopHeartbeatTimer() {
	if (heartbeatTimer) {
		clearInterval(heartbeatTimer)
		heartbeatTimer = null
	}
}

function getDwellSeconds() {
	return Math.floor((Date.now() - stepStartTime) / 1000)
}

async function sendHeartbeat(payload) {
	if (heartbeatInFlight) return
	heartbeatInFlight = true
	try {
		const res = await http.post(`/api/craft/session/${sessionId}/heartbeat`, payload)
		craftStore.setFlowState(res.inFlow || false, res.needBreathing || false)
		if (res.needBreathing && !showBreathing.value) {
			showBreathing.value = true
		}
	} catch (e) {
		console.warn('心跳失败', e)
	} finally {
		heartbeatInFlight = false
	}
}

// ── 步骤操作 ──

function handleNext() {
	if (craftStore.status !== 1) return
	// 上报当前步骤退出 + 新步骤进入
	sendHeartbeat({
		stepNo: currentStep.value + 1,
		dwellSeconds: 0,
		isExited: false,
		isBreathingResponded: false,
	})
	craftStore.stepForward()
	stepStartTime = Date.now()
}

function handlePrev() {
	if (craftStore.status !== 1) return
	if (currentStep.value <= 1) return
	sendHeartbeat({
		stepNo: currentStep.value - 1,
		dwellSeconds: 0,
		isExited: false,
		isBreathingResponded: false,
	})
	craftStore.stepBack()
	stepStartTime = Date.now()
}

// ── 状态机 ──

async function handlePause() {
	try {
		const vo = await http.put(`/api/craft/session/${sessionId}/status`, { action: 'pause' })
		craftStore.setSession(vo)
		stopHeartbeatTimer()
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
	}
}

async function handleResume() {
	try {
		const vo = await http.put(`/api/craft/session/${sessionId}/status`, { action: 'resume' })
		craftStore.setSession(vo)
		stepStartTime = Date.now()
		startHeartbeatTimer()
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
	}
}

async function handleFinish() {
	try {
		stopHeartbeatTimer()
		const vo = await http.put(`/api/craft/session/${sessionId}/status`, { action: 'finish' })
		craftStore.setSession(vo)
		craftStore.finish()
		showSuccess.value = true
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
	}
}

function handleAbandon() {
	uni.showModal({
		title: '确认放弃',
		content: '放弃后无法恢复这次创作进度，确定放弃吗？',
		confirmText: '放弃',
		confirmColor: '#b85450',
		success: async (res) => {
			if (res.confirm) {
				try {
					await http.put(`/api/craft/session/${sessionId}/status`, { action: 'abandon' })
					craftStore.reset()
					uni.navigateBack()
				} catch (e) {
					uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
				}
			}
		},
	})
}

// ── 呼吸动画 ──

function onBreathingFinish() {
	showBreathing.value = false
	craftStore.needBreathing = false
	sendHeartbeat({
		stepNo: currentStep.value,
		dwellSeconds: getDwellSeconds(),
		isExited: false,
		isBreathingResponded: true,
	})
}

// ── 其他 ──

function handleAudio() {
	uni.showToast({ title: '即将上线', icon: 'none' })
}

function handlePublish() {
	uni.showToast({ title: '作品发布即将上线', icon: 'none' })
}

function handleBackHome() {
	craftStore.reset()
	uni.reLaunch({ url: '/pages/home/index' })
}
</script>

<style scoped>
.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	background-color: #f7f4ef;
}

/* 进度条 */
.progress-bar {
	display: flex;
	align-items: center;
	gap: 12px;
	padding: 12px 16px;
	background-color: #fffdfa;
	border-bottom: 1px solid #e5ddd0;
}

.progress-track {
	flex: 1;
	height: 6px;
	border-radius: 3px;
	background-color: #ece3d5;
}

.progress-fill {
	height: 100%;
	border-radius: 3px;
	background-color: #8b6f47;
	transition: width 0.3s ease;
}

.progress-text {
	font-size: 13px;
	color: #8a8378;
	white-space: nowrap;
}

/* 心流提示 */
.flow-hint {
	padding: 8px 16px;
	background-color: rgba(200, 155, 110, 0.12);
	text-align: center;
}

.flow-text {
	font-size: 13px;
	color: #c89b6e;
}

/* 步骤内容 */
.step-area {
	flex: 1;
	overflow: hidden;
}

.step-content {
	padding: 24px 20px;
}

.step-title {
	font-size: 20px;
	font-weight: 600;
	color: #2b2b2b;
}

.step-guide {
	display: block;
	margin-top: 16px;
	font-size: 15px;
	line-height: 1.8;
	color: #5a534a;
}

.audio-btn {
	display: flex;
	align-items: center;
	gap: 8px;
	margin-top: 24px;
	padding: 10px 20px;
	border: 1px solid #c89b6e;
	border-radius: 8px;
	align-self: flex-start;
}

.audio-icon {
	font-size: 18px;
}

.audio-text {
	font-size: 14px;
	color: #c89b6e;
}

/* 操作按钮区 */
.action-area {
	padding: 12px 16px 8px;
	background-color: #fffdfa;
	border-top: 1px solid #e5ddd0;
}

.step-btns {
	display: flex;
	gap: 12px;
	margin-bottom: 12px;
}

.step-btn {
	flex: 1;
	height: 44px;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 8px;
}

.btn-prev {
	background-color: #f0ebe1;
}

.btn-next {
	background-color: #8b6f47;
}

.btn-finish {
	background-color: #c89b6e;
}

.btn-text {
	font-size: 15px;
	color: #fffdfa;
}

.btn-prev .btn-text {
	color: #8a8378;
}

.bottom-actions {
	display: flex;
	gap: 12px;
	justify-content: center;
}

.action-btn {
	padding: 6px 24px;
	border: 1px solid #e5ddd0;
	border-radius: 8px;
	background-color: transparent;
}

.btn-resume {
	border-color: #8b6f47;
}

.btn-abandon {
	border-color: #e0d5c5;
}

.action-text {
	font-size: 13px;
	color: #8a8378;
}

.btn-resume .action-text {
	color: #8b6f47;
}

.btn-abandon .action-text {
	color: #b85450;
}

.footnote {
	display: block;
	padding: 4px 0 8px;
	font-size: 12px;
	color: #bdb2a1;
	text-align: center;
	background-color: #fffdfa;
}

/* 成功卡片 */
.success-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(0, 0, 0, 0.6);
	z-index: 9998;
	display: flex;
	align-items: center;
	justify-content: center;
}

.success-card {
	width: 80%;
	padding: 40px 32px;
	border-radius: 12px;
	background-color: #fffdfa;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 16px;
}

.success-emoji {
	font-size: 48px;
}

.success-title {
	font-size: 20px;
	font-weight: 600;
	color: #2b2b2b;
}

.success-hint {
	font-size: 14px;
	color: #8a8378;
	text-align: center;
}

.success-btns {
	display: flex;
	flex-direction: column;
	gap: 12px;
	margin-top: 8px;
	width: 100%;
}

.success-btn {
	height: 44px;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 8px;
}

.btn-primary {
	background-color: #8b6f47;
}

.btn-secondary {
	background-color: #f0ebe1;
}

.success-btn-text {
	font-size: 15px;
	color: #fffdfa;
}

.btn-secondary .success-btn-text {
	color: #8b6f47;
}

/* 加载中 */
.loading-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(247, 244, 239, 0.9);
	z-index: 9997;
	display: flex;
	align-items: center;
	justify-content: center;
}

.loading-text {
	font-size: 15px;
	color: #8a8378;
}
</style>
