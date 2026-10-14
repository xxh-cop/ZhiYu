<template>
	<view class="page">
		<!-- 顶部进度条 -->
		<view class="progress-bar">
			<view class="progress-track">
				<view class="progress-fill" :style="{ width: progressPercent + '%' }"></view>
			</view>
			<text class="progress-text">{{ currentStep }} / {{ totalSteps }}</text>
		</view>

		<view v-if="loadError" class="sync-error"><text>{{loadError}}</text><button @click="initialize">重新读取进度</button></view><view v-if="syncError" class="sync-error"><text>{{syncError}}</text><button :disabled="progressSaving" @click="retryProgress">重试保存</button></view>
		<view v-if="craftStore.status === 2" class="pause-notice">已暂停，进度已保留。准备好后再继续。</view>
		<!-- 心流提示 -->
		<view v-if="craftStore.inFlow" class="flow-hint">
			<text class="flow-text">专注中…</text>
		</view>

		<!-- 步骤内容区 -->
		<scroll-view class="step-area" scroll-y><MediaVideo v-if="teachingVideo" :src="teachingVideo" caption="边看边做 · 可暂停后操作"/>
			<view class="step-content zy-slide-up" :key="currentStep">
				<view class="step-card">
					<text class="step-title">{{ currentStepData.title }}</text>
					<StarterDiagram v-if="starterStep" :kind="starterStep.diagram"/><text v-if="starterStep" class="session-tip">原创纹样示意 · 操作以文字为准</text><text class="step-guide">{{ currentStepData.guide }}</text>
				</view>

				<!-- 音频播放按钮 -->
				<view v-if="currentStepData.audioUrl" class="audio-btn" @click="handleAudio">
					<text class="audio-icon">🔊</text>
					<text class="audio-text">播放引导语音</text>
				</view>
			</view>
		</scroll-view>

		<view class="action-area"><view class="session-tools"><button v-if="!craftStore.inFlow && currentStep > 1" class="text-button" :disabled="craftStore.status !== 1 || statusBusy || progressSaving" @click="handlePrev">上一步</button><text v-else class="session-tip">跟随自己的节奏</text><button class="text-button" :disabled="statusBusy || progressSaving || pageLoading || !!loadError" @click="showMore">更多操作</button></view><wd-button block type="primary" :disabled="pageLoading || statusBusy || progressSaving || !!loadError || !totalSteps" @click="handlePrimary">{{ craftStore.status === 2 ? '继续创作' : currentStep === totalSteps ? '完成创作' : '下一步' }}</wd-button></view>

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
					<wd-button type="primary" block @click="handlePublish">去记录此刻</wd-button>
					<wd-button plain block @click="handleBackHome">回到首页</wd-button>
				</view>
			</view>
		</view>

		<!-- 加载中 -->
		<view v-if="pageLoading" class="loading-overlay">
			<view class="loading-dot"></view>
			<text class="loading-text">正在准备…</text>
		</view>


		<!-- 悬浮聊天面板 -->
		<ChatPanel v-if="showChatPanel" @close="onPanelClose" />
	</view>
</template>

<script setup>
import MediaVideo from "../../components/MediaVideo.vue"
import { ref, computed, onUnmounted } from 'vue'
import { onLoad, onUnload, onShow, onHide } from '@dcloudio/uni-app'
import { useCraftStore } from '../../stores/craft'
import { http } from '../../utils/request'
import BreathingAnimation from '../../components/BreathingAnimation.vue'
import ChatPanel from '../../components/ChatPanel.vue'
import StarterDiagram from '../../components/StarterDiagram.vue'

const craftStore = useCraftStore()
const teachingVideo=ref('')
async function loadTeaching(){const id=route.projectId||craftStore.projectId;if(!id)return;try{const p=await http.get('/api/craft/projects/'+id);teachingVideo.value=p.videoUrl||''}catch{teachingVideo.value=''}}

const pageLoading = ref(true)
const showBreathing = ref(false)
const showSuccess = ref(false)
const statusBusy = ref(false)
const showChatPanel = ref(false)

function onPanelClose() {
	showChatPanel.value = false
}

let sessionId = null
let stepStartTime = Date.now()
let heartbeatTimer = null
let heartbeatChain = Promise.resolve(true)
let disposed = false
let hidden = false
let route = {}
let retryPayload = null
const loadError=ref(''),syncError=ref(''),progressSaving=ref(false),starter=ref(null)
const starterStep=computed(()=>starter.value?.steps?.[currentStep.value-1])
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

onLoad(query=>{route=query;initialize().then(loadTeaching)})
async function initialize(){
 if(disposed)return
 pageLoading.value=true;loadError.value='';stopHeartbeatTimer();craftStore.reset()
 try{
  let projectId=route.projectId
  if(!projectId)throw new Error('缺少项目参数')
  // Read steps before creating a session; retry cannot strand a hidden unfinished session.
  const steps=await http.get('/api/craft/projects/'+projectId+'/steps')
  if(!steps.length)throw new Error('项目暂无可用步骤，请返回重新选择')
  const active=await http.get('/api/craft/session/active?projectId='+projectId)
  const existing=route.sessionId?active.find(x=>String(x.sessionId)===String(route.sessionId)):active[0]
  let vo
  if(route.sessionId&&!existing)throw new Error('该进度已结束或项目暂不可用，请返回目录')
  if(existing)vo=await http.get('/api/craft/session/'+existing.sessionId)
  else{
   try{vo=await http.post('/api/craft/session',{projectId})}
   catch(e){const latest=await http.get('/api/craft/session/active?projectId='+projectId);if(!latest.length)throw e;vo=await http.get('/api/craft/session/'+latest[0].sessionId)}
  }
  if(![1,2].includes(vo.status))throw new Error('该创作已结束，请返回目录')
  if(disposed)return
  sessionId=vo.sessionId;craftStore.setSession(vo);craftStore.setSteps(steps)
  stepStartTime=Date.now();if(vo.status===1&&!hidden)startHeartbeatTimer()
  http.get('/api/craft/starter').then(all=>{if(!disposed)starter.value=all.find(x=>String(x.id)===String(projectId))||null}).catch(()=>{})
 }catch(e){if(!disposed)loadError.value=e.message||'加载失败，请重试'}finally{if(!disposed)pageLoading.value=false}
}
onHide(()=>{hidden=true;stopHeartbeatTimer();if(sessionId&&craftStore.status===1&&!progressSaving.value)sendHeartbeat({stepNo:currentStep.value,dwellSeconds:getDwellSeconds(),isExited:true,isBreathingResponded:false})})
onShow(()=>{hidden=false;if(sessionId&&craftStore.status===1&&!pageLoading.value){stepStartTime=Date.now();startHeartbeatTimer()}})
onUnload(()=>{stopHeartbeatTimer();if(sessionId&&!isExited&&craftStore.status===1&&!progressSaving.value){isExited=true;sendHeartbeat({stepNo:currentStep.value,dwellSeconds:getDwellSeconds(),isExited:true,isBreathingResponded:false})}disposed=true})

// ── 心跳 ──

function startHeartbeatTimer() {
	stopHeartbeatTimer()
	heartbeatTimer = setInterval(() => {
		if (craftStore.status !== 1 || progressSaving.value || syncError.value || hidden) return
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

function sendHeartbeat(payload){
 const target=sessionId
 const task=heartbeatChain.then(async()=>{
  try{
   const res=await http.post('/api/craft/session/'+target+'/heartbeat',payload)
   if(!disposed&&!hidden&&craftStore.status===1){craftStore.setFlowState(res.inFlow||false,res.needBreathing||false);if(res.needBreathing)showBreathing.value=true}
   if(!disposed){syncError.value='';retryPayload=null}return true
  }catch{
   if(!disposed){syncError.value='进度暂未保存，请保持当前步骤并重试。';retryPayload=payload}return false
  }
 })
 heartbeatChain=task;return task
}
async function retryProgress(){if(!retryPayload||progressSaving.value)return;progressSaving.value=true;const payload=retryPayload;try{if(await sendHeartbeat(payload)){craftStore.currentStep=payload.stepNo;stepStartTime=Date.now()}}finally{progressSaving.value=false}}

// ── 步骤操作 ──

function handlePrimary() {
	if (statusBusy.value || progressSaving.value || loadError.value) return
	if (craftStore.status === 2) return handleResume()
	if (currentStep.value === totalSteps.value) return handleFinish()
	handleNext()
}

function showMore() {
	uni.showActionSheet({ itemList: [craftStore.status === 2 ? '继续创作' : '暂停创作', '放弃本次创作', '和小织聊聊'], success: ({ tapIndex }) => {
		if (tapIndex === 2) showChatPanel.value = true
		else if (tapIndex === 1) handleAbandon()
		else if (craftStore.status === 2) handleResume()
		else handlePause()
	} })
}

async function moveStep(delta){
 if(craftStore.status!==1||progressSaving.value||statusBusy.value)return
 const next=currentStep.value+delta;if(next<1||next>totalSteps.value)return
 progressSaving.value=true
 try{if(await sendHeartbeat({stepNo:next,dwellSeconds:0,isExited:false,isBreathingResponded:false})){if(!disposed){craftStore.currentStep=next;stepStartTime=Date.now()}}}finally{if(!disposed)progressSaving.value=false}
}
function handleNext(){return moveStep(1)}
function handlePrev(){return moveStep(-1)}

// ── 状态机 ──

async function handlePause() {
	if (statusBusy.value || craftStore.status !== 1) return
	statusBusy.value = true
	await heartbeatChain
	try {
		const vo = await http.put(`/api/craft/session/${sessionId}/status`, { action: 'pause' })
		craftStore.setSession(vo)
		stopHeartbeatTimer()
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
	}
	statusBusy.value = false
}

async function handleResume() {
	if (statusBusy.value || craftStore.status !== 2) return
	statusBusy.value = true
	await heartbeatChain
	try {
		const vo = await http.put(`/api/craft/session/${sessionId}/status`, { action: 'resume' })
		craftStore.setSession(vo)
		stepStartTime = Date.now()
		startHeartbeatTimer()
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
	}
	statusBusy.value = false
}

async function handleFinish() {
	if (statusBusy.value || craftStore.status !== 1) return
	statusBusy.value = true
	await heartbeatChain
	try {
		const vo = await http.put(`/api/craft/session/${sessionId}/status`, { action: 'finish' })
		stopHeartbeatTimer()
		craftStore.setSession(vo)
		craftStore.finish()
		showSuccess.value = true
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
	}
	statusBusy.value = false
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
	uni.navigateTo({
		url: `/pages/craft/reflection?sessionId=${sessionId}&projectId=${craftStore.projectId || ''}&projectTitle=${encodeURIComponent(craftStore.projectTitle || '')}`,
	})
}

function handleBackHome() {
	craftStore.reset()
	uni.switchTab({ url: '/pages/home/index' })
}
</script>

<style scoped>
.pause-notice{padding:18rpx 32rpx;font-size:25rpx;line-height:1.6;background:#eef7f3;color:#267c6e}
.sync-error{margin:20rpx;padding:22rpx;border-radius:16rpx;background:#fff3df;color:#865b26;font-size:26rpx}.sync-error button{font-size:26rpx;margin-top:12rpx}

.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	background-color: var(--zy-bg, #f5f7f2);
}

/* ── 进度条 ── */
.progress-bar {
	display: flex;
	align-items: center;
	gap: 24rpx;
	padding: 20rpx 32rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border-bottom: 1rpx solid var(--zy-border, #dfe7dc);
}

.progress-track {
	flex: 1;
	height: 8rpx;
	border-radius: 4rpx;
	background-color: var(--wot-color-border-light, #F3F4F6);
}

.progress-fill {
	height: 100%;
	border-radius: 4rpx;
	background: linear-gradient(90deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
	transition: width 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.progress-text {
	font-family: var(--zy-serif);
	font-size: 28rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
	white-space: nowrap;
}

/* ── 心流提示 ── */
.flow-hint {
	padding: 16rpx 32rpx;
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	text-align: center;
	animation: flowFadeIn 0.3s ease;
	box-shadow: 0 0 24rpx rgba(45, 155, 143, 0.15);
}

.flow-text {
	font-size: 26rpx;
	color: var(--zy-primary, #2f7566);
	letter-spacing: 4rpx;
}

@keyframes flowFadeIn {
	from { opacity: 0; transform: translateY(-8rpx); }
	to { opacity: 1; transform: translateY(0); }
}

/* ── 步骤内容 ── */
.step-area {
	flex: 1;
	overflow: hidden;
}

.step-content {
	padding: 40rpx 32rpx;
	animation: stepFadeIn 0.35s ease both;
}

@keyframes stepFadeIn {
	from { opacity: 0; transform: translateY(12rpx); }
	to { opacity: 1; transform: translateY(0); }
}

.step-card {
	background-color: var(--zy-card-bg, #FFFFFF);
	border-radius: var(--zy-radius-lg, 32rpx);
	padding: 40rpx 32rpx;
	box-shadow: var(--zy-shadow-sm);
}

.step-title {
	display: block;
	font-family: var(--zy-serif);
	font-size: 36rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
	line-height: 1.4;
}

.step-guide {
	display: block;
	margin-top: 24rpx;
	font-size: 28rpx;
	line-height: 1.8;
	color: var(--zy-content, #586e62);
}

/* ── 音频按钮 ── */
.audio-btn {
	display: inline-flex;
	align-items: center;
	gap: 12rpx;
	margin-top: 32rpx;
	padding: 16rpx 32rpx;
	border: 2rpx solid var(--zy-primary, #2f7566);
	border-radius: 40rpx;
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.audio-btn:active {
	transform: scale(0.96);
}

.audio-icon {
	font-size: 32rpx;
}

.audio-text {
	font-size: 28rpx;
	color: var(--zy-primary, #2f7566);
}

/* ── 操作按钮区 ── */
.action-area {
	padding: 24rpx 32rpx 16rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border-top: 1rpx solid var(--zy-border, #dfe7dc);
}

.step-btns {
	display: flex;
	gap: 24rpx;
	margin-bottom: 20rpx;
}

.step-btn {
	flex: 1;
}

.bottom-actions {
	display: flex;
	gap: 24rpx;
	justify-content: center;
}

.action-btn {
	min-width: 160rpx;
}

/* ── 免责 ── */
.footnote {
	display: block;
	padding: 8rpx 0 16rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #718478);
	text-align: center;
	background-color: var(--zy-bg-warm, #FFFFFF);
}

/* ── 成功卡片 ── */
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
	animation: fadeIn 0.25s ease;
}

@keyframes fadeIn {
	from { opacity: 0; }
	to { opacity: 1; }
}

.success-card {
	width: 80%;
	padding: 64rpx 48rpx;
	border-radius: 32rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 24rpx;
	animation: cardIn 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes cardIn {
	from { opacity: 0; transform: scale(0.85); }
	to { opacity: 1; transform: scale(1); }
}

.success-emoji {
	font-size: 96rpx;
}

.success-title {
	font-family: var(--zy-serif);
	font-size: 36rpx;
	font-weight: 500;
	color: var(--zy-primary, #2f7566);
}

.success-hint {
	font-size: 28rpx;
	color: var(--zy-content, #586e62);
	text-align: center;
	line-height: 1.6;
}

.success-btns {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
	margin-top: 16rpx;
	width: 100%;
}

/* ── 加载中 ── */
.loading-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(248, 249, 251, 0.95);
	z-index: 9997;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 24rpx;
}

.loading-dot {
	width: 48rpx;
	height: 48rpx;
	border-radius: 50%;
	border: 4rpx solid var(--zy-border, #dfe7dc);
	border-top-color: var(--zy-primary, #2f7566);
	animation: spin 0.8s linear infinite;
}

@keyframes spin {
	to { transform: rotate(360deg); }
}

.loading-text {
	font-size: 28rpx;
	color: var(--zy-aid, #718478);
}

.session-tools { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }.text-button { padding: 0 12rpx; margin: 0; background: transparent; color: var(--zy-primary); font-size: 26rpx; }.text-button::after { border: 0; }.session-tip { font-size: 24rpx; color: var(--zy-content); }.step-area { min-height: 0; height: 0; }.footnote { padding-bottom: calc(16rpx + env(safe-area-inset-bottom)); }.page { height: 100vh; min-height: 0; }
/* #ifdef H5 */
.page { height: calc(100dvh - var(--window-top, 44px) - var(--window-bottom, 0px)); }
/* #endif */
</style>
