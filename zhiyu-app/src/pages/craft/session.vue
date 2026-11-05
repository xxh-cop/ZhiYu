<template>
	<view class="page" @touchstart="idleArm" @click="idleArm">
		<!-- 顶部情境条：项目名 / 进度点 / 更多（心流态自动收起更多，减少打扰） -->
		<view class="topbar">
			<view class="topbar-info">
				<text class="topbar-eyebrow">正在创作</text>
				<text class="topbar-title">{{ displayTitle }}</text>
			</view>
			<view class="topbar-right">
				<view
					v-if="totalSteps"
					class="step-dots"
					role="progressbar"
					:aria-valuenow="currentStep"
					:aria-valuemin="1"
					:aria-valuemax="totalSteps"
					:aria-label="`进度：第 ${currentStep} 步，共 ${totalSteps} 步`"
				>
					<view
						v-for="n in totalSteps"
						:key="n"
						class="step-dot"
						:class="{ done: n < currentStep, current: n === currentStep }"
					></view>
				</view>
				<text v-if="totalSteps" class="step-count">{{ currentStep }} / {{ totalSteps }}</text>
				<AccessibleButton
					v-if="!craftStore.inFlow"
					class="more-btn"
					:disabled="statusBusy || progressSaving || pageLoading || !!loadError"
					aria-label="更多操作：暂停、放弃或和小织聊聊"
					@click="showMore"
				>⋯</AccessibleButton>
			</view>
		</view>

		<!-- 状态条：只读回看（BR）/ 同步失败 / 已暂停 / 心流（同一位置轮替，不叠加） -->
		<view v-if="craftStore.readOnly" class="banner banner-readonly" role="status">
			<text class="banner-text readonly-text">{{ craftStore.notice }}</text>
			<!-- 导出真实入口（文案与后端 notice 模板逐字一致）：仅只读+窗口内+有会话指针时渲染；
			     窗口关闭/开关关闭/归档态（canExport=false）不渲染，由 notice 文本说明，不做禁用态占位 -->
			<view v-if="craftStore.canExport && exportSessionId" class="export-row">
				<AccessibleButton class="export-btn" :loading="exportBusy" :disabled="exportBusy" @click="exportSessionData">导出我的创作数据</AccessibleButton>
				<text v-if="craftStore.exportUntil" class="banner-sub">可导出至 {{ formatDeadline(craftStore.exportUntil) }}（截止后转为归档，仅保留回看）</text>
			</view>
			<text v-else-if="craftStore.canExport && craftStore.exportUntil" class="banner-sub">可导出至 {{ formatDeadline(craftStore.exportUntil) }}（截止后转为归档，仅保留回看）</text>
		</view>
		<view v-else-if="craftStore.notice" class="banner banner-readonly" role="status">
			<text class="banner-text readonly-text">{{ craftStore.notice }}</text>
		</view>
		<view v-else-if="syncError" class="banner banner-warn" role="alert">
			<text class="banner-text">{{ syncError }}</text>
			<AccessibleButton class="banner-btn" :disabled="progressSaving" @click="retryProgress">重试保存</AccessibleButton>
		</view>
		<view v-else-if="craftStore.status === 2" class="banner banner-pause">
			<text class="banner-text">已暂停，进度已保留。准备好后再继续。</text>
		</view>
		<view v-else-if="craftStore.inFlow" class="banner banner-flow" aria-live="polite">
			<text class="banner-text">专注中，保持自己的节奏就好</text>
		</view>

		<!-- 步骤内容区 -->
		<scroll-view v-if="!loadError" class="step-area" scroll-y>
			<MediaVideo v-if="teachingVideo" :src="teachingVideo" caption="边看边做 · 可暂停后操作" />
			<view class="step-content zy-slide-up" :key="currentStep">
				<!-- 小织陪伴：心流态隐藏，不打扰专注 -->
				<view v-if="!craftStore.inFlow" class="pet-row">
					<XiaotuanPet :size="88" @open="openChat" />
					<view class="pet-bubble"><text class="pet-bubble-text">{{ petLine }}</text></view>
				</view>
				<view class="step-card">
					<text v-if="totalSteps" class="step-kicker">{{ stepKicker }}</text>
					<text class="step-title">{{ currentStepData.title }}</text>
					<StarterDiagram v-if="starterStep" :kind="starterStep.diagram" />
					<text v-if="starterStep" class="diagram-tip">原创纹样示意 · 操作以文字为准</text>
					<text class="step-guide">{{ currentStepData.guide }}</text>
				</view>

				<!-- 引导语音 -->
				<AccessibleButton
					v-if="currentStepData.audioUrl"
					class="audio-btn"
					:aria-pressed="guidePlaying"
					@click="handleAudio"
				>
					<text class="audio-icon">🔊</text>
					<text class="audio-text">{{ guidePlaying ? '停止引导语音' : '播放引导语音' }}</text>
				</AccessibleButton>
			</view>
		</scroll-view>

		<!-- 加载失败 / 暂无步骤：整块状态页 -->
		<view v-else class="state-block">
			<XiaotuanPet :size="88" face="care" />
			<text class="state-title">这次没能准备好</text>
			<text class="state-desc">{{ loadError }}</text>
			<AccessibleButton class="state-primary" aria-label="重新读取进度" @click="initialize">重新读取进度</AccessibleButton>
			<AccessibleButton class="state-link" @click="goBack">返回上一页</AccessibleButton>
		</view>

		<!-- 底部操作区：单一主按钮 -->
		<view v-if="!loadError" class="action-area">
			<view class="action-sub">
				<AccessibleButton
					v-if="!craftStore.inFlow && currentStep > 1"
					class="text-button"
					:disabled="(craftStore.status !== 1 && !craftStore.readOnly) || statusBusy || progressSaving"
					@click="handlePrev"
				>上一步</AccessibleButton>
				<text v-else class="session-tip">跟随自己的节奏</text>
				<text class="safe-tip">{{ craftStore.readOnly ? '回看模式：创作记录已保留' : '进度自动保留，随时可以离开' }}</text>
			</view>
			<!-- BR：只读回看推进控件不可用——主按钮替换为纯浏览「翻看下一步」，不出现完成/推进入口 -->
			<AccessibleButton
				v-if="craftStore.readOnly"
				class="primary-btn"
				:disabled="currentStep >= totalSteps"
				aria-label="翻看下一步"
				@click="handleNext"
			>翻看下一步</AccessibleButton>
			<AccessibleButton
				v-else
				class="primary-btn"
				:disabled="pageLoading || statusBusy || progressSaving || !!loadError || !totalSteps"
				:aria-label="primaryLabel"
				@click="handlePrimary"
			>{{ primaryLabel }}</AccessibleButton>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>

		<!-- 呼吸引导（底部抽屉） -->
		<BreathingAnimation
			v-if="showBreathing"
			@finish="onBreathingFinish"
		/>

		<!-- 完成庆祝层 -->
		<view v-if="showSuccess" class="success-overlay" @click.stop>
			<view class="success-card" role="dialog" aria-modal="true" aria-label="创作完成">
				<XiaotuanPet :size="110" face="happy" />
				<text class="success-title">创作完成！</text>
				<text class="success-hint">你用双手完成了一件作品，很棒</text>
				<text class="success-time">{{ elapsedText }}</text>
				<view class="success-btns">
					<AccessibleButton class="primary-btn" aria-label="去记录此刻" @click="handlePublish">去记录此刻</AccessibleButton>
					<AccessibleButton class="state-link" @click="handleBackHome">回到首页</AccessibleButton>
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

		<!-- 更多操作（暂停/放弃/和小织聊聊） -->
		<ActionSheet
			:open="moreSheetOpen"
			title="更多操作"
			:actions="moreActions"
			cancel-text="继续创作"
			@select="onMoreSelect"
			@close="moreSheetOpen = false"
		/>

		<!-- 退出确认（保留进度并退出） -->
		<ActionSheet
			:open="exitSheetOpen"
			title="先暂停一下？"
			description="离开会自动保留进度，随时可以从这里继续。"
			:actions="[{ key: 'exit', label: '保留进度并退出', tone: 'primary' }]"
			cancel-text="继续创作"
			@select="confirmExit"
			@close="exitSheetOpen = false"
		/>

		<!-- 放弃确认（危险动作） -->
		<ActionSheet
			:open="abandonSheetOpen"
			title="确认放弃"
			description="放弃后无法恢复这次创作进度，确定放弃吗？"
			:actions="[{ key: 'confirm', label: '确认放弃', tone: 'danger', busy: abandonBusy }]"
			cancel-text="再想想"
			:busy="abandonBusy"
			@select="confirmAbandon"
			@close="abandonSheetOpen = false"
		/>

		<!-- R3 时机②：长停无操作的一次性陪伴（9s 自动退场，可点 ✕ / 右滑关闭；点按即收起） -->
		<CompanionNudge
			:open="idleNudgeOpen"
			text="不着急，慢慢来"
			face="care"
			bottom-offset="calc(240rpx + env(safe-area-inset-bottom))"
			@action="idleNudgeOpen = false"
			@close="idleNudgeOpen = false"
		/>

		<Toast />
	</view>
</template>

<script setup>
import MediaVideo from "../../components/MediaVideo.vue"
import { ref, computed, watch } from 'vue'
import { createMusicAudio } from '../../utils/music-audio'
import { API_BASE_URL, TOKEN_KEY } from '../../utils/config'
import { onLoad, onUnload, onShow, onHide, onBackPress } from '@dcloudio/uni-app'
import { useCraftStore } from '../../stores/craft'
import { http } from '../../utils/request'
import BreathingAnimation from '../../components/BreathingAnimation.vue'
import ChatPanel from '../../components/ChatPanel.vue'
import StarterDiagram from '../../components/StarterDiagram.vue'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ActionSheet from '../../components/ActionSheet.vue'
import CompanionNudge from '../../components/CompanionNudge.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'
import { useIdleNudge } from '../../composables/useIdleNudge'
import { useCompanionNudge } from '../../composables/useCompanionNudge'
import { invalidate, TTL_KEYS } from '../../utils/ttlCache'
import { bindSessionEvents, unbindSessionEvents, finishSessionEvents, trackSessionEvent, flushSessionEvents } from '../../utils/session-events'

const toast = useToast()

const craftStore = useCraftStore()

// R3 时机②：页内长停无操作 120s，小织出现一次（仅「正在做事」页启用；
// 任何交互重置计时；quotaKey page-idle 每天 ≤2 次）
const idleNudgeOpen = ref(false)
const { arm: idleArm } = useIdleNudge({
	quotaKey: 'page-idle',
	onTrigger: () => { idleNudgeOpen.value = true },
})
// R3 时机③：创作完成庆祝浮层关闭后，回首页出现一次（由首页消费）
const nudgeQuota = useCompanionNudge()
const teachingVideo=ref('')
async function loadTeaching(){const id=route.projectId||craftStore.projectId;if(!id)return;try{const p=await http.get('/api/craft/projects/'+id);teachingVideo.value=p.videoUrl||''}catch{teachingVideo.value=''}}

const pageLoading = ref(true)
const showBreathing = ref(false)
const showSuccess = ref(false)
const statusBusy = ref(false)
const showChatPanel = ref(false)
const moreSheetOpen = ref(false)
const exitSheetOpen = ref(false)
const abandonSheetOpen = ref(false)
const abandonBusy = ref(false)

function onPanelClose() {
	showChatPanel.value = false
}
function openChat() {
	showChatPanel.value = true
}

let sessionId = null
let stepStartTime = Date.now()
let totalDwellSeconds = 0
let heartbeatTimer = null
let heartbeatChain = Promise.resolve(true)
let disposed = false
let hidden = false
let route = {}
let retryPayload = null
const loadError=ref(''),syncError=ref(''),progressSaving=ref(false),starter=ref(null),resumed=ref(false)
const starterStep=computed(()=>starter.value?.steps?.[currentStep.value-1])
let isExited = false

const currentStep = computed(() => craftStore.currentStep)
const totalSteps = computed(() => craftStore.totalSteps)

const displayTitle = computed(() => craftStore.projectTitle || route.projectTitle || '手作创作')

const primaryLabel = computed(() => {
	if (craftStore.status === 2) return '继续创作'
	if (totalSteps.value && currentStep.value === totalSteps.value) return '完成创作'
	return '下一步'
})

const stepKicker = computed(() => {
	if (!totalSteps.value) return ''
	if (currentStep.value >= totalSteps.value) return '最后一步，完成就做好啦'
	return `第 ${currentStep.value} 步 · 做完这一步还剩 ${totalSteps.value - currentStep.value} 步`
})

const petLine = computed(() => {
	if (craftStore.status === 2) return '欢迎回来，从这里继续就好'
	if (resumed.value) return '接着上次的进度，慢慢来'
	if (currentStep.value === 1) return '备好材料，我们一步一步来'
	if (totalSteps.value > 1 && currentStep.value === totalSteps.value) return '最后一步啦，完成后记得夸夸自己'
	return '按自己的节奏来，不着急'
})

const elapsedText = computed(() => {
	const minutes = Math.floor(totalDwellSeconds / 60)
	return minutes > 0 ? `这段专注时光约 ${minutes} 分钟` : '哪怕只有一会儿，也值得被记住'
})

const currentStepData = computed(() => {
	const steps = craftStore.steps
	if (!steps || steps.length === 0) return { title: '', guide: '', audioUrl: null }
	const step = steps.find((s) => s.stepNo === currentStep.value)
	const raw = step || { title: '', guide: '', audioUrl: null }
	// BQ（2026-11-05）：audioUrl 归一化——空白字符串视同无音频。
	// 播放控件仅对非空白地址渲染（v-if 走这里），null 时不出现按钮、不触发任何播放/错误路径。
	const audioUrl = typeof raw.audioUrl === 'string' && raw.audioUrl.trim() ? raw.audioUrl.trim() : null
	return { ...raw, audioUrl }
})

onLoad(query=>{route=query||{};initialize().then(loadTeaching)})
async function initialize(){
 if(disposed)return
 pageLoading.value=true;loadError.value='';stopHeartbeatTimer();craftStore.reset();totalDwellSeconds=0;resumed.value=false;exportSessionId=null
 try{
  let projectId=route.projectId
  if(!projectId)throw new Error('缺少项目参数')
  // BR（2026-11-05 分层下架）：listSteps 为结构化响应 {steps, projectStatus, readOnly, notice, ...}；
  // 仅「不存在/待审」1004。先拆包装写入 store（含 readOnly/notice 等），再按只读与否分流。
  const payload=await http.get('/api/craft/projects/'+projectId+'/steps')
  craftStore.setStepsPayload(payload)
  const steps=(payload&&payload.steps)||[]
  if(!steps.length)throw new Error('项目暂无可用步骤，请返回重新选择')
  // BR 只读回看（合规下架窗口内/已归档/窗口过期）：不建不续会话、不起心跳，
  // 步骤可本地翻看，推进控件禁用，页面显示后端 notice 原文。
  // BS：只读不绑定事件上报（无会话），step_enter/step_leave 天然零事件，不污染心流数据。
  if(craftStore.readOnly){
   if(disposed)return
   craftStore.totalSteps=steps.length
   craftStore.currentStep=1
   // 导出端点按会话寻址：用 listSteps 下发的最近会话指针（无会话则可导出标记也无意义，按钮不渲染）
   exportSessionId=payload.sessionId?String(payload.sessionId):null
   stepStartTime=Date.now()
   return
  }
  const active=await http.get('/api/craft/session/active?projectId='+projectId)
  let existing=route.sessionId?active.find(x=>String(x.sessionId)===String(route.sessionId)):active[0]
  // BR：运营下架后 active 列表（JOIN 上架项目）不再返回进行中会话；
  // 用 listSteps 下发的最近会话指针恢复（getProgress 只校验归属，不校验项目状态）。
  if(!existing&&payload.sessionId&&[1,2].includes(Number(payload.sessionStatus))){
   existing={sessionId:String(payload.sessionId)}
  }
  let vo
  if(route.sessionId&&!existing)throw new Error('该进度已结束或项目暂不可用，请返回目录')
  if(existing){resumed.value=true;vo=await http.get('/api/craft/session/'+existing.sessionId)}
  else{
   try{vo=await http.post('/api/craft/session',{projectId})}
   catch(e){const latest=await http.get('/api/craft/session/active?projectId='+projectId);if(!latest.length)throw e;vo=await http.get('/api/craft/session/'+latest[0].sessionId)}
  }
  if(![1,2].includes(vo.status))throw new Error('该创作已结束，请返回目录')
  if(disposed)return
  sessionId=vo.sessionId;craftStore.setSession(vo)
  // BS：绑定事件上报 + 埋首步进入 + 注册可见性监听（仅进行中会话走到这里）
  bindSessionEvents(sessionId)
  trackSessionEvent('step_enter',{stepNo:vo.currentStep||1})
  registerVisibilityEvents()
  stepStartTime=Date.now();if(vo.status===1&&!hidden)startHeartbeatTimer()
  http.get('/api/craft/starter').then(all=>{if(!disposed)starter.value=all.find(x=>String(x.id)===String(projectId))||null}).catch(()=>{})
 }catch(e){if(!disposed)loadError.value=(e&&e.code===1004)?'这个手作已调整或暂未上架，去看看别的吧':e.message||'加载失败，请重试'}finally{if(!disposed)pageLoading.value=false}
}
onHide(()=>{hidden=true;stopHeartbeatTimer();if(sessionId&&craftStore.status===1&&!progressSaving.value)sendHeartbeat({stepNo:currentStep.value,dwellSeconds:getDwellSeconds(),isExited:true,isBreathingResponded:false})})
onShow(()=>{hidden=false;if(sessionId&&craftStore.status===1&&!pageLoading.value){stepStartTime=Date.now();startHeartbeatTimer()}})
onUnload(()=>{stopHeartbeatTimer();if(sessionId&&!isExited&&craftStore.status===1&&!progressSaving.value){isExited=true;sendHeartbeat({stepNo:currentStep.value,dwellSeconds:getDwellSeconds(),isExited:true,isBreathingResponded:false})}unregisterVisibilityEvents();unbindSessionEvents();disposed=true})

// ── 退出确认（H5 / App 生效；微信小程序平台不支持拦截页面返回，见交付说明）──
onBackPress(() => {
	if (disposed || isExited) return false
	if (showSuccess.value || showBreathing.value || showChatPanel.value) return false
	if (!sessionId || craftStore.status !== 1) return false
	exitSheetOpen.value = true
	return true
})

// 确认「保留进度并退出」：尽力暂停；失败也不阻塞退出（onUnload 的退出心跳同样会保留进度）
function confirmExit() {
	exitSheetOpen.value = false
	http.put(`/api/craft/session/${sessionId}/status`, { action: 'pause' })
		.then((vo) => craftStore.setSession(vo))
		.catch(() => {})
	uni.navigateBack()
}

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

// BR：后端 LocalDateTime（ISO）→ 界面可读截止时间
function formatDeadline(v) {
	return String(v || '').replace('T', ' ').slice(0, 16)
}

// ── 导出我的创作数据（合规下架窗口内真实入口）──
// 只读回看下可用；导出请求不经过会话事件上报器（只读会话不绑定上报器，天然隔离）。
let exportSessionId = null
const exportBusy = ref(false)
function readToken() {
	try { return uni.getStorageSync(TOKEN_KEY) || '' } catch (e) { return '' }
}
async function exportSessionData() {
	if (exportBusy.value || !exportSessionId) return
	exportBusy.value = true
	const url = API_BASE_URL + '/api/craft/session/' + exportSessionId + '/export'
	const token = readToken()
	try {
		// #ifdef H5
		// 附件下载需带 JWT（不能裸 a.download）。跨源 fetch 读不到 Content-Disposition（CORS 受限头），
		// 故不靠响应头区分：业务拒绝（3001 等）body 为 Result JSON（code!==0），导出载荷无 code 字段。
		const resp = await fetch(url, { headers: { Authorization: 'Bearer ' + token } })
		if (!resp.ok) {
			const errBody = await resp.json().catch(() => null)
			throw { code: errBody && errBody.code, message: (errBody && errBody.message) || '导出未完成，请稍后重试' }
		}
		const text = await resp.text()
		let parsed = null
		try { parsed = JSON.parse(text) } catch (e) { /* 非 JSON 按附件数据落盘 */ }
		if (parsed && typeof parsed.code === 'number' && parsed.code !== 0) {
			throw { code: parsed.code, message: parsed.message || '导出未完成，请稍后重试' }
		}
		const cd = resp.headers.get('content-disposition') || ''
		const fname = (cd.match(/filename\*?=(?:UTF-8''|"?)([^";]+)"?/i) || [])[1]
			|| `zhiyu-craft-session-${exportSessionId}-p${route.projectId || craftStore.projectId || ''}.json`
		const blob = new Blob([text], { type: 'application/json;charset=UTF-8' })
		const a = document.createElement('a')
		a.href = URL.createObjectURL(blob)
		a.download = decodeURIComponent(fname)
		document.body.appendChild(a)
		a.click()
		a.remove()
		setTimeout(() => URL.revokeObjectURL(a.href), 4000)
		toast.success('已导出')
		return
		// #endif
		// #ifdef MP-WEIXIN
		await new Promise((resolve) => {
			uni.downloadFile({
				url,
				header: { Authorization: 'Bearer ' + token },
				success(r) {
					if (r.statusCode !== 200) { toast.care('导出未完成（HTTP ' + r.statusCode + '）'); resolve(); return }
					const fsm = uni.getFileSystemManager()
					fsm.readFile({
						filePath: r.tempFilePath,
						encoding: 'utf8',
						success(fr) {
							let biz = null
							try { const j = JSON.parse(fr.data); if (j && typeof j.code === 'number' && j.code !== 0) biz = j } catch (e) { /* 导出 JSON 无 code 字段，解析失败即视为数据 */ }
							if (biz) { toast.care(biz.message || '导出未完成'); resolve(); return }
							uni.saveFile({
								tempFilePath: r.tempFilePath,
								success() { toast.success('已导出，文件已保存') },
								fail(e2) { toast.care('已下载但保存失败：' + ((e2 && e2.errMsg) || '未知原因')) },
								complete() { resolve() },
							})
						},
						fail() { toast.care('导出文件读取失败'); resolve() },
					})
				},
				fail(e) { toast.care('导出失败：' + ((e && e.errMsg) || '请检查网络')); resolve() },
			})
		})
		// #endif
	} catch (e) {
		toast.care((e && e.message) || '导出未完成，请稍后重试')
	} finally {
		exportBusy.value = false
	}
}

// ── BS 可见性事件（只报生命周期，不报输入/触摸）──
let visibilityBound = false
function onDocVisibility() {
	// #ifdef H5
	const visible = !document.hidden
	trackSessionEvent('visibility_change', { context: { visible } })
	if (!visible) flushSessionEvents() // 切后台尽力 flush
	// #endif
}
// #ifdef MP-WEIXIN
function onMpHide() { trackSessionEvent('app_lifecycle', { context: { foreground: false } }); flushSessionEvents() }
function onMpShow() { trackSessionEvent('app_lifecycle', { context: { foreground: true } }) }
// #endif
function registerVisibilityEvents() {
	if (visibilityBound) return
	visibilityBound = true
	// #ifdef H5
	document.addEventListener('visibilitychange', onDocVisibility)
	// #endif
	// #ifdef MP-WEIXIN
	uni.onAppHide(onMpHide)
	uni.onAppShow(onMpShow)
	// #endif
}
function unregisterVisibilityEvents() {
	if (!visibilityBound) return
	visibilityBound = false
	// #ifdef H5
	document.removeEventListener('visibilitychange', onDocVisibility)
	// #endif
	// #ifdef MP-WEIXIN
	uni.offAppHide(onMpHide)
	uni.offAppShow(onMpShow)
	// #endif
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
async function retryProgress(){if(!retryPayload||progressSaving.value)return;progressSaving.value=true;const payload=retryPayload;try{if(await sendHeartbeat(payload)){craftStore.currentStep=payload.stepNo;stepStartTime=Date.now();invalidate(TTL_KEYS.craftSessionActive)}}finally{progressSaving.value=false}}

// ── 步骤操作 ──

function handlePrimary() {
	if (craftStore.readOnly || statusBusy.value || progressSaving.value || loadError.value) return
	if (craftStore.status === 2) return handleResume()
	if (currentStep.value === totalSteps.value) return handleFinish()
	handleNext()
}

// BR：只读回看无会话，暂停/放弃无意义，只留小织陪伴入口
const moreActions = computed(() => craftStore.readOnly
	? [{ key: 'chat', label: '和小织聊聊', tone: 'default' }]
	: [
		{ key: 'pause', label: craftStore.status === 2 ? '继续创作' : '暂停创作', tone: 'primary' },
		{ key: 'abandon', label: '放弃本次创作', tone: 'danger' },
		{ key: 'chat', label: '和小织聊聊', tone: 'default' },
	])

function showMore() {
	moreSheetOpen.value = true
}

function onMoreSelect(key) {
	moreSheetOpen.value = false
	if (key === 'chat') { showChatPanel.value = true; return }
	if (key === 'abandon') { handleAbandon(); return }
	if (craftStore.status === 2) handleResume()
	else handlePause()
}

async function moveStep(delta){
 const next=currentStep.value+delta;if(next<1||next>totalSteps.value)return
 // BR 只读回看：步骤纯本地浏览，不发心跳（后端闸口也会拒，3001）
 if(craftStore.readOnly){craftStore.currentStep=next;return}
 if(craftStore.status!==1||progressSaving.value||statusBusy.value)return
 progressSaving.value=true
 try{if(await sendHeartbeat({stepNo:next,dwellSeconds:0,isExited:false,isBreathingResponded:false})){if(!disposed){const dwell=getDwellSeconds();totalDwellSeconds+=dwell;trackSessionEvent('step_leave',{stepNo:currentStep.value,context:{dwellSeconds:dwell}});craftStore.currentStep=next;trackSessionEvent('step_enter',{stepNo:next});stepStartTime=Date.now();invalidate(TTL_KEYS.craftSessionActive)}}}finally{if(!disposed)progressSaving.value=false}
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
		totalDwellSeconds += getDwellSeconds()
		stopHeartbeatTimer()
	} catch (e) {
		toast.care((e && e.message) || '操作失败')
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
		toast.care((e && e.message) || '操作失败')
	}
	statusBusy.value = false
}

async function handleFinish() {
	if (statusBusy.value || craftStore.status !== 1) return
	statusBusy.value = true
	await heartbeatChain
	try {
		const vo = await http.put(`/api/craft/session/${sessionId}/status`, { action: 'finish' })
		totalDwellSeconds += getDwellSeconds()
		stopHeartbeatTimer()
		craftStore.setSession(vo)
		craftStore.finish()
		// BS：完成即离开当前步骤；确保事件队列 flush 完再进入成功页
		trackSessionEvent('step_leave', { stepNo: currentStep.value, context: { dwellSeconds: getDwellSeconds() } })
		await finishSessionEvents()
		showSuccess.value = true
	} catch (e) {
		toast.care((e && e.message) || '操作失败')
	}
	statusBusy.value = false
}

function handleAbandon() {
	if (abandonBusy.value) return
	abandonSheetOpen.value = true
}

async function confirmAbandon() {
	if (abandonBusy.value) return
	abandonBusy.value = true
	try {
		await http.put(`/api/craft/session/${sessionId}/status`, { action: 'abandon' })
		invalidate(TTL_KEYS.craftSessionActive)
		// BS：放弃即离开；flush 完事件队列再返回
		trackSessionEvent('step_leave', { stepNo: currentStep.value, context: { dwellSeconds: getDwellSeconds() } })
		await finishSessionEvents()
		abandonSheetOpen.value = false
		craftStore.reset()
		uni.navigateBack()
	} catch (e) {
		toast.care((e && e.message) || '操作失败')
	} finally {
		abandonBusy.value = false
	}
}

// ── 呼吸引导 ──

function onBreathingFinish() {
	showBreathing.value = false
	craftStore.needBreathing = false
	trackSessionEvent('breathing_responded', { stepNo: currentStep.value })
	sendHeartbeat({
		stepNo: currentStep.value,
		dwellSeconds: getDwellSeconds(),
		isExited: false,
		isBreathingResponded: true,
	})
}

// ── 其他 ──

const guidePlaying = ref(false)
let guideAudio = null
let guideRetried = false
function stopGuideAudio() {
  if (guideAudio) { guideAudio.destroy(); guideAudio = null }
  guidePlaying.value = false
}
function handleAudio() {
  if (guideAudio) { stopGuideAudio(); return }
  playGuide(currentStepData.value.audioUrl)
}
/**
 * 播放引导语音。AX（2026-11-05）：签名播放地址 1h 过期——首次播放失败时
 * 静默重拉一次步骤数据换新签名重试（地址未变则不重试），再失败才提示。
 * 重试计数在播放成功时复位。
 */
function playGuide(source) {
  if (!source) return
  // Accept configured HTTP(S) resources and server-local upload paths only.
  if (!/^https?:\/\//i.test(source) && !/^\/(?!\/)/.test(source)) {
    toast.care('音频地址无效，请联系内容管理员'); return
  }
  const player = createMusicAudio()
  guideAudio = player
  player.onPlay(() => { if (guideAudio === player) { guidePlaying.value = true; guideRetried = false; trackSessionEvent('audio_play', { stepNo: currentStep.value }) } })
  player.onEnded(() => { if (guideAudio === player) stopGuideAudio() })
  player.onError(async () => {
    if (guideAudio !== player) return
    stopGuideAudio()
    if (!guideRetried) {
      guideRetried = true
      const fresh = await reloadStepsAudioUrl()
      if (fresh && fresh !== source && guideRetried) { playGuide(fresh); return }
    }
    toast.care('音频暂时无法播放，可继续阅读文字引导')
  })
  player.src = /^https?:\/\//i.test(source) ? source : API_BASE_URL + source
  player.play()
}
async function reloadStepsAudioUrl() {
  try {
    const projectId = route.projectId || craftStore.projectId
    if (!projectId) return null
    // BR：结构化响应，同步包装字段（窗口状态可能变化）
    const payload = await http.get('/api/craft/projects/' + projectId + '/steps')
    craftStore.setStepsPayload(payload)
    const cur = currentStepData.value
    return (cur && cur.audioUrl) || null
  } catch (e) {
    return null
  }
}
watch(currentStep, stopGuideAudio)
watch(() => craftStore.status, status => { if (status !== 1) stopGuideAudio() })
onHide(stopGuideAudio)
onUnload(stopGuideAudio)

function goBack() {
	uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/home/index' }) })
}

function handlePublish() {
	nudgeQuota.scheduleNudge('craft-finish', '完成啦，喝口水歇歇')
	uni.navigateTo({
		url: `/pages/craft/reflection?sessionId=${sessionId}&projectId=${craftStore.projectId || ''}&projectTitle=${encodeURIComponent(craftStore.projectTitle || '')}`,
	})
}

function handleBackHome() {
	nudgeQuota.scheduleNudge('craft-finish', '完成啦，喝口水歇歇')
	craftStore.reset()
	uni.switchTab({ url: '/pages/home/index' })
}
</script>

<style scoped>
/* ── 页面骨架 ── */
.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	min-height: 0;
	background-color: var(--zy-bg, #f5f7f2);
}
/* #ifdef H5 */
.page { height: calc(100dvh - var(--window-top, 44px) - var(--window-bottom, 0px)); }
/* #endif */

/* ── 顶部情境条 ── */
.topbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 24rpx;
	padding: 20rpx 32rpx;
	background-color: var(--zy-bg-warm, #ffffff);
	border-bottom: 1rpx solid var(--zy-border, #dfe7dc);
}

.topbar-info {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 2rpx;
}

.topbar-eyebrow {
	font-size: 20rpx;
	letter-spacing: 4rpx;
	color: var(--zy-content, #4a5f54);
}

.topbar-title {
	font-family: var(--zy-serif);
	font-size: 30rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.topbar-right {
	flex-shrink: 0;
	display: flex;
	align-items: center;
	gap: 16rpx;
}

.step-dots {
	display: flex;
	align-items: center;
	gap: 10rpx;
}

.step-dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	background-color: var(--zy-border, #dfe7dc);
	transition: all var(--zy-duration-base, 250ms) var(--zy-ease-in-out, ease);
}

.step-dot.done {
	background-color: var(--zy-brand-300, #5bc4a8);
}

.step-dot.current {
	width: 28rpx;
	border-radius: 8rpx;
	background-color: var(--zy-primary, #217e76);
}

.step-count {
	font-size: 24rpx;
	color: var(--zy-content, #4a5f54);
	white-space: nowrap;
}

.more-btn {
	width: 88rpx;
	height: 88rpx;
	min-width: 44px;
	min-height: 44px;
	padding: 0;
	margin: 0;
	border-radius: 50%;
	background-color: var(--zy-bg, #f5f7f2);
	color: var(--zy-title, #233d34);
	font-size: 36rpx;
	line-height: 1;
	display: flex;
	align-items: center;
	justify-content: center;
}

.more-btn::after { border: 0; }
.more-btn[disabled] { opacity: 0.5; }

/* ── 状态条 ── */
.banner {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	padding: 18rpx 32rpx;
}

.banner-text {
	flex: 1;
	min-width: 0;
	font-size: 25rpx;
	line-height: 1.6;
}

.banner-warn {
	background-color: var(--zy-bg, #f5f7f2);
	border-left: 6rpx solid var(--zy-gold, #d9a441);
}

/* BR 只读回看条：暖底中性提示，notice 为后端原文（markdown 源文本，保留换行） */
.banner-readonly {
	flex-direction: column;
	align-items: flex-start;
	background-color: var(--zy-bg-tint-warm, #f7efd9);
	border-left: 6rpx solid var(--zy-gold, #d9a441);
}

.banner-readonly .readonly-text {
	white-space: pre-line;
	color: var(--zy-warning-text, #8a6d1f);
}

.banner-sub {
	font-size: 22rpx;
	line-height: 1.7;
	color: var(--zy-warning-text, #8a6d1f);
	opacity: 0.9;
	margin-top: 8rpx;
}

/* 导出真实入口：主操作热区 ≥88rpx（≈44px） */
.export-row {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 10rpx;
	margin-top: 16rpx;
}

.export-btn {
	margin: 0;
	min-height: 88rpx;
	padding: 20rpx 36rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-brand-600, #217e76);
	color: #fff;
	font-size: 27rpx;
	font-weight: 600;
}

.banner-warn .banner-text { color: var(--zy-warning-text, #8a6d1f); }

.banner-btn {
	flex-shrink: 0;
	margin: 0;
	padding: 8rpx 28rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: transparent;
	border: 2rpx solid var(--zy-warning-text, #8a6d1f);
	color: var(--zy-warning-text, #8a6d1f);
	font-size: 24rpx;
	line-height: 1.5;
}

.banner-btn::after { border: 0; }

.banner-pause {
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.banner-pause .banner-text { color: var(--zy-brand-700, #1a645e); }

.banner-flow {
	justify-content: center;
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	animation: flowFadeIn 0.3s ease;
}

.banner-flow .banner-text {
	flex: none;
	color: var(--zy-brand-700, #1a645e);
	letter-spacing: 4rpx;
}

@keyframes flowFadeIn {
	from { opacity: 0; transform: translateY(-8rpx); }
	to { opacity: 1; transform: translateY(0); }
}

/* ── 步骤内容 ── */
.step-area {
	flex: 1;
	min-height: 0;
	height: 0;
	overflow: hidden;
}

.step-content {
	padding: 32rpx;
	animation: stepFadeIn 0.35s ease both;
}

@keyframes stepFadeIn {
	from { opacity: 0; transform: translateY(12rpx); }
	to { opacity: 1; transform: translateY(0); }
}

/* 小织陪伴行 */
.pet-row {
	display: flex;
	align-items: center;
	gap: 20rpx;
	margin-bottom: 24rpx;
}

.pet-bubble {
	flex: 1;
	min-width: 0;
	background-color: var(--zy-card-bg, #ffffff);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-md, 24rpx);
	border-top-left-radius: 8rpx;
	padding: 16rpx 24rpx;
	box-shadow: var(--zy-shadow-xs);
}

.pet-bubble-text {
	font-size: 26rpx;
	line-height: 1.6;
	color: var(--zy-content, #4a5f54);
}

.step-card {
	background-color: var(--zy-card-bg, #ffffff);
	border-radius: var(--zy-radius-card, 32rpx);
	padding: 40rpx 32rpx;
	box-shadow: var(--zy-shadow-sm);
}

.step-kicker {
	display: block;
	font-size: 22rpx;
	letter-spacing: 2rpx;
	color: var(--zy-primary, #217e76);
	margin-bottom: 12rpx;
}

.step-title {
	display: block;
	font-family: var(--zy-serif);
	font-size: 36rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
	line-height: 1.4;
}

.diagram-tip {
	display: block;
	margin-top: 12rpx;
	font-size: 22rpx;
	color: var(--zy-content, #4a5f54);
}

.step-guide {
	display: block;
	margin-top: 24rpx;
	font-size: 28rpx;
	line-height: 1.8;
	color: var(--zy-content, #4a5f54);
}

/* ── 引导语音 ── */
.audio-btn {
	display: inline-flex;
	align-items: center;
	gap: 12rpx;
	margin: 32rpx 0 0;
	padding: 16rpx 32rpx;
	border: 2rpx solid var(--zy-primary, #217e76);
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	line-height: 1.5;
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.audio-btn::after { border: 0; }

.audio-btn:active {
	transform: scale(0.96);
}

.audio-icon {
	font-size: 32rpx;
}

.audio-text {
	font-size: 28rpx;
	color: var(--zy-brand-700, #1a645e);
}

/* ── 失败 / 空状态 ── */
.state-block {
	flex: 1;
	min-height: 0;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 20rpx;
	padding: 48rpx;
}

.state-title {
	font-family: var(--zy-serif);
	font-size: 34rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
}

.state-desc {
	font-size: 26rpx;
	line-height: 1.7;
	color: var(--zy-content, #4a5f54);
	text-align: center;
}

.state-primary {
	margin-top: 12rpx;
	min-width: 320rpx;
	padding: 0 48rpx;
	border-radius: var(--zy-radius-control, 24rpx);
	background-color: var(--zy-primary, #217e76);
	color: #ffffff;
	font-size: 28rpx;
}

.state-link {
	margin: 0;
	padding: 12rpx 24rpx;
	background: transparent;
	color: var(--zy-primary, #217e76);
	font-size: 26rpx;
	line-height: 1.5;
}

.state-link::after { border: 0; }

/* ── 底部操作区 ── */
.action-area {
	padding: 24rpx 32rpx 16rpx;
	background-color: var(--zy-bg-warm, #ffffff);
	border-top: 1rpx solid var(--zy-border, #dfe7dc);
}

.action-sub {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	margin-bottom: 20rpx;
}

.text-button {
	padding: 0 12rpx;
	margin: 0;
	background: transparent;
	color: var(--zy-primary, #217e76);
	font-size: 26rpx;
	line-height: 1.5;
}

.text-button::after { border: 0; }

.session-tip {
	font-size: 24rpx;
	color: var(--zy-content, #4a5f54);
}

.safe-tip {
	font-size: 22rpx;
	color: var(--zy-content, #4a5f54);
	text-align: right;
}

.primary-btn {
	width: 100%;
	min-height: 96rpx;
	border-radius: var(--zy-radius-control, 24rpx);
	background-color: var(--zy-primary, #217e76);
	color: #ffffff;
	font-size: 30rpx;
	font-weight: 500;
}

.primary-btn:active {
	background-color: var(--zy-brand-700, #1a645e);
}

/* ── 免责声明 ── */
.footnote {
	display: block;
	padding: 8rpx 0 calc(16rpx + env(safe-area-inset-bottom));
	font-size: 22rpx;
	color: var(--zy-content, #4a5f54);
	text-align: center;
	background-color: var(--zy-bg-warm, #ffffff);
}

/* ── 完成庆祝层 ── */
.success-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(20, 32, 28, 0.5);
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
	max-width: 640rpx;
	padding: 56rpx 48rpx;
	border-radius: var(--zy-radius-dialog, 48rpx);
	background-color: var(--zy-bg-warm, #ffffff);
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 20rpx;
	animation: cardIn 0.35s var(--zy-ease-elastic, cubic-bezier(0.34, 1.56, 0.64, 1));
}

@keyframes cardIn {
	from { opacity: 0; transform: scale(0.85); }
	to { opacity: 1; transform: scale(1); }
}

.success-title {
	font-family: var(--zy-serif);
	font-size: 36rpx;
	font-weight: 500;
	color: var(--zy-primary, #217e76);
}

.success-hint {
	font-size: 28rpx;
	color: var(--zy-content, #4a5f54);
	text-align: center;
	line-height: 1.6;
}

.success-time {
	font-size: 24rpx;
	color: var(--zy-accent, #8a6d1f);
	text-align: center;
}

.success-btns {
	display: flex;
	flex-direction: column;
	gap: 8rpx;
	margin-top: 16rpx;
	width: 100%;
	align-items: center;
}

/* ── 加载中 ── */
.loading-overlay {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(245, 247, 242, 0.95);
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
	border-top-color: var(--zy-primary, #217e76);
	animation: spin 0.8s linear infinite;
}

@keyframes spin {
	to { transform: rotate(360deg); }
}

.loading-text {
	font-size: 28rpx;
	color: var(--zy-content, #4a5f54);
}
button:focus-visible{outline:3px solid var(--zy-brand-700,#1a645e);outline-offset:3px}
</style>
