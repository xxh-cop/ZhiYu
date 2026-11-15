<template>
	<view :class="zyThemeClass" class="page" @touchstart="idleArm" @click="idleArm">
		<view class="chat-header"><XiaotuanPet :size="42" aria-label="小织"/><view class="chat-header-copy"><text class="header-name zy-t-serif">小织 · 倾听陪伴</text><text class="header-state">{{ streaming ? '正在回应你…' : '在听' }}</text></view><AccessibleButton v-if="emotionStore.sessionId" variant="ghost" size="sm" class="header-action" :disabled="streaming" @click="endConversation">结束倾听</AccessibleButton><AccessibleButton variant="danger" size="sm" class="header-action" @click="goCrisisPage">寻求支持</AccessibleButton></view>
		<!-- 对话区 -->
		<scroll-view
			class="chat-area"
			scroll-y
			:scroll-top="scrollTop"
			:scroll-with-animation="true"
		>
			<view class="chat-list">
				<!-- 空状态欢迎区 -->
				<view v-if="messages.length === 0" class="welcome">
					<view class="welcome-avatar">
						<XiaotuanPet :size="110" aria-label="小织陪伴你"/>
					</view>
					<text class="welcome-title zy-t-serif">今天想从哪儿说起？</text>
					<text class="welcome-sub">不用想好句子，说半句也行。</text>
					<text class="welcome-hint">说说今天发生了什么？</text>
					<view v-if="memoryPreview" class="memory-hint" role="button" tabindex="0" @click="goMemory" @keydown.enter.prevent="goMemory" @keydown.space.prevent="goMemory">
						<text class="memory-hint-text">{{ memoryPreview }}</text>
						<text class="memory-hint-link">改</text>
					</view>
					<view class="welcome-chips">
						<AccessibleButton v-for="c in OPENERS" :key="c" class="opener-chip" @click="fillInput(c)">{{ c }}</AccessibleButton>
					</view>
				</view>

				<view
					v-for="(msg, idx) in messages"
					:key="idx"
					class="msg-row"
				>
					<ChatBubble
						v-if="msg.role === 'assistant'"
						role="ai"
						:time="msg.done ? formatTime(msg) : ''"
						:typing="!msg.done && !msg.text"
					>{{ msg.text }}</ChatBubble>
					<ChatBubble
						v-else
						role="user"
						:time="formatTime(msg)"
						:user-initial="userInitial"
					>{{ msg.text }}</ChatBubble>

					<!-- AI 消息附加：provider 标注 / 朗读 / 推荐入口（逻辑判定原样保留） -->
					<template v-if="msg.role === 'assistant'">
						<!-- 2026-11-08 装配层批：一次性归属标注——仅本条确为本地模型产出时出现，后续不重复 -->
						<text v-if="msg.done && msg.provider === 'ollama'" class="msg-provider">本条由校内本地模型生成</text>
						<view v-if="msg.done && speechAvailable && msg.text" class="msg-actions">
							<button
								class="speak-btn"
								:class="{ speaking: speakingIndex === idx, 'is-pressed': speakingIndex === idx, 'is-disabled': speechBusy === idx }"
								:aria-pressed="speakingIndex === idx ? 'true' : 'false'"
								:disabled="speechBusy === idx"
								:aria-label="speakingIndex === idx ? '停止朗读这条回复' : '朗读这条回复'"
								@click="toggleSpeak(msg, idx)"
							>{{ speechBusy === idx ? '准备语音…' : speakingIndex === idx ? '停止' : '听' }}</button>
						</view>
						<!-- 最后一条 assistant 消息完成后，显示推荐入口 -->
						<AccessibleButton
							v-if="msg.done && idx === messages.length - 1 && showRecommendBtn"
							class="recommend-entry"
							aria-label="查看为你推荐的手作"
							@click="handleRecommend"
						>
							<text class="recommend-text">试试一件手作，让手帮心忙起来 →</text>
						</AccessibleButton>
					</template>
				</view>

			<!-- 危机关怀卡片 -->
			<view v-if="showCrisisCard" class="crisis-card">
				<view class="crisis-card-header">
					<view class="crisis-avatar"><XiaotuanPet :size="30" face="care" /></view>
					<text class="crisis-card-title zy-t-serif">小织想对你说</text>
				</view>
				<text class="crisis-card-text">我先陪你一会儿。如果撑不住，这些电话现在就能打。</text>
				<view class="crisis-card-btns">
					<AccessibleButton variant="care" size="md" aria-label="查看心理援助资源" @click="goCrisisPage">
						<wd-icon name="phone" size="28rpx" color="#FFFFFF" />
						<text>查看心理援助资源</text>
					</AccessibleButton>
					<AccessibleButton variant="secondary" size="md" aria-label="继续聊聊" @click="dismissCrisis">继续聊聊</AccessibleButton>
				</view>
			</view>

			<!-- 隐私同意引导（Q2）：撤回同意后对话不可用，引导主动查看并重新同意（不自动弹） -->
			<StateBlock
				v-if="isConsentError"
				type="error"
				face="care"
				title="需要你重新同意隐私政策"
				description="你之前撤回了隐私政策同意。对话内容属于敏感信息，只有在你重新同意后，小织才能继续陪你聊。是否同意，始终由你决定。"
				action-text="去了解并重新同意"
				@action="goPrivacySettings"
			/>
			<!-- 错误重试 -->
			<view v-else-if="errorMsg" class="error-row">
				<text class="error-text">{{ errorMsg }}</text>
				<wd-button type="error" size="small" plain @click="handleRetry">重试</wd-button>
			</view>
		</view>
	</scroll-view>

		<!-- 2026-11-08 装配层批：本地兜底持续轻注（云端正常时不出现；危机出口常驻） -->
		<view v-if="localMode" class="local-note">
			<text class="local-note__text">当前为本地陪伴模式，回复会朴素一些。紧急情况请随时点</text>
			<text class="local-note__link" @click="goCrisisPage">「寻求支持」</text>
		</view>

		<!-- 输入区（固定底部） -->
		<ChatInputBar
			v-model="inputText"
			:disabled="streaming || historyLoading || historyError || isConsentError"
			:voice-state="voiceStateView"
			@send="handleSend"
			@voice="handleVoice"
		/>

		<!-- 底部免责声明（固定在输入区下方） -->
		<view class="footer-disclaimer">
			<text class="footer-disclaimer-text">本系统不能替代专业心理咨询</text>
		</view>
		<!-- R3 时机②：长停无操作的一次性陪伴（9s 自动退场，可点 ✕ / 右滑关闭；点按即收起） -->
		<CompanionNudge
			:open="idleNudgeOpen"
			text="不用急着说完"
			face="care"
			bottom-offset="calc(220rpx + env(safe-area-inset-bottom))"
			@action="idleNudgeOpen = false"
			@close="idleNudgeOpen = false"
		/>
		<Toast />
	</view>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import { storeToRefs } from 'pinia'
import {request} from '../../utils/request'
import { onLoad, onUnload, onShow } from '@dcloudio/uni-app'
import { useEmotionStore } from '../../stores/emotion'
import { useUserStore } from '../../stores/user'
import {useVoiceInput} from '../../composables/useVoiceInput'
import {playSpeech, stopSpeech} from '../../utils/speech-playback'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import StateBlock from '../../components/StateBlock.vue'
import CompanionNudge from '../../components/CompanionNudge.vue'
import ChatBubble from '../../components/ChatBubble.vue'
import ChatInputBar from '../../components/ChatInputBar.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'
import { useIdleNudge } from '../../composables/useIdleNudge'

const toast = useToast()

// 「小织记得的」（2026-11-15）：空状态时给一条可修改的记忆提示；未开启记忆时不显示。
const memoryPreview = ref('')
async function loadMemoryHint() {
	try {
		const m = await request({ url: '/api/companion/memory' })
		if (!m.enabled) { memoryPreview.value = ''; return }
		const parts = []
		const calm = (m.derived || []).find(d => d.key === 'calm_strategy')
		if (calm) parts.push(calm.value)
		const minutes = (m.items || []).find(i => i.key === 'preferred_minutes')
		if (minutes) parts.push(`一次 ${minutes.value} 分钟`)
		if ((m.avoidTopics || []).length) parts.push(`不聊${m.avoidTopics.join('、')}`)
		memoryPreview.value = parts.length ? `小织记得：${parts.slice(0, 2).join(' · ')}` : ''
	} catch (e) { memoryPreview.value = '' }
}
function goMemory() { uni.navigateTo({ url: '/pages/settings/memory' }) }
onShow(loadMemoryHint)

const emotionStore = useEmotionStore()
const userStore = useUserStore()
const { messages, streaming, errorMsg, errorCode, scrollSignal, crisisTriggered, localMode } = storeToRefs(emotionStore)

// Q2：撤回隐私同意后，情绪接口返回 1003「请先阅读并同意隐私政策」。
// 此时展示「去重新同意」引导态（不自动弹同意、不阻断用户选择），替代裸错误 + 无效重试。
const isConsentError = computed(() => errorCode.value === 1003 && /隐私政策/.test(errorMsg.value || ''))
function goPrivacySettings() {
	uni.navigateTo({ url: '/pages/privacy/settings' })
}

// R3 时机②：页内长停无操作 120s，小织出现一次（仅「正在做事」页启用；
// 任何交互重置计时；quotaKey page-idle 每天 ≤2 次）
const idleNudgeOpen = ref(false)
const { arm: idleArm } = useIdleNudge({
	quotaKey: 'page-idle',
	onTrigger: () => { idleNudgeOpen.value = true },
})

const inputText = ref('')
const speechAvailable = ref(false)
const speakingIndex = ref(-1)
const speechBusy = ref(-1)
const {state:voiceState,toggle:toggleVoice}=useVoiceInput(text=>{inputText.value=text})
const showRecommendBtn = ref(false)
const showCrisisCard = ref(false)
const scrollTop = ref(0)
const historyId = ref('')
const historyLoading = ref(false)
const historyError = ref(false)
async function loadHistory() {
 historyLoading.value = true; historyError.value = false; emotionStore.errorMsg = ''
 try {
  const detail = await request({url:'/api/emotion/sessions/'+historyId.value})
  emotionStore.sessionId = detail.session.sessionId
  emotionStore.messages = detail.messages.map(m=>({role:m.role,text:m.content,done:true}))
 } catch(e) { historyError.value = true; emotionStore.errorMsg = e.message || '历史对话读取失败，请重试'; emotionStore.errorCode = (e && typeof e.code === 'number' && e.code) || 0 }
 finally { historyLoading.value = false }
}

onLoad(async (query) => {
 loadSpeechStatus()
 if(query?.sessionId){emotionStore.reset();historyId.value=query.sessionId;await loadHistory();return;}
	// 面板关闭后跳来时 preserveMessages=true，保留消息上下文
	if (emotionStore.preserveMessages) {
		emotionStore.preserveMessages = false // 消费掉
	} else {
		// 正常进入（如首页点「情绪倾听」），重置会话
		emotionStore.reset()
	}
})

onUnload(() => {
	stopSpeech()
	// 页面卸载时 abort SSE
	emotionStore.abortSSE()
})

// watch scrollSignal 触发滚动到底部
watch(scrollSignal, () => {
	scrollToBottom()
})
watch(() => messages.value.length, () => {
	scrollToBottom()
})

function scrollToBottom() {
	nextTick(() => {
		scrollTop.value = 99999
	})
}

function handleSend() {
	if (streaming.value || historyLoading.value || historyError.value) return
	const content = inputText.value.trim()
	if (!content) return

	showRecommendBtn.value = false
	inputText.value = ''

	// 调用 store 的 sendMessage（SSE 逻辑已抽取到 store）
	emotionStore.sendMessage(content)
}

function handleRetry() {
	if (historyError.value) { loadHistory(); return }
	emotionStore.retryLast()
}

function handleRecommend() {
	const sid = emotionStore.sessionId || ''
	uni.navigateTo({
		url: `/pages/craft/recommend${sid ? '?sessionId=' + sid : ''}`,
	})
}

function handleVoice() {
	stopSpeech()
	speakingIndex.value=-1
	if(!streaming.value)toggleVoice()
}

async function loadSpeechStatus(){
	if(!userStore.isLogin)return
	try{
		const status=await request({url:'/api/speech/status'})
		speechAvailable.value=!!status?.available
	}catch(e){
		speechAvailable.value=false
	}
}

async function toggleSpeak(msg,idx){
	if(!speechAvailable.value||!msg?.text)return
	if(speakingIndex.value===idx){
		stopSpeech()
		speakingIndex.value=-1
		return
	}
	if(speechBusy.value!==-1)return
	stopSpeech()
	speakingIndex.value=-1
	speechBusy.value=idx
	try{
		const data=await request({url:'/api/speech/synthesize',method:'POST',data:{text:msg.text.slice(0,2000)},timeout:30000})
		if(!data?.audioBase64)throw new Error('语音暂时不可用')
		speakingIndex.value=idx
		playSpeech(data.audioBase64,'audio/mpeg',{
			onEnd:()=>{if(speakingIndex.value===idx)speakingIndex.value=-1},
			onError:()=>{
				if(speakingIndex.value===idx)speakingIndex.value=-1
				toast.care('语音播放失败，可以继续看文字')
			}
		})
	}catch(e){
		speakingIndex.value=-1
		toast.care(e.message||'语音暂时不可用')
	}finally{
		speechBusy.value=-1
	}
}

function goCrisisPage() {
	uni.navigateTo({ url: '/pages/crisis/index' })
}

function dismissCrisis() {
	emotionStore.dismissCrisis()
}

// ── 纯展示辅助函数（不涉及 SSE / store 逻辑） ──
const userInitial = computed(() => {
	const n = (userStore.userInfo && userStore.userInfo.nickname) || '同'
	return n ? n.charAt(0) : '同'
})

function formatTime(msg) {
	const d = new Date()
	const h = String(d.getHours()).padStart(2, '0')
	const m = String(d.getMinutes()).padStart(2, '0')
	return `${h}:${m}`
}

// SSE done 后显示推荐按钮（watch 最后一条消息的 done 状态）
watch(() => {
	const last = messages.value[messages.value.length - 1]
	return last && last.role === 'assistant' && last.done
}, (val) => {
	if (val) showRecommendBtn.value = true
})

// 危机卡片：crisisTriggered 延迟 0.5s 显示
watch(crisisTriggered, (val) => {
	if (val) {
		setTimeout(() => {
			showCrisisCard.value = true
			nextTick(() => scrollToBottom())
		}, 500)
	} else {
		showCrisisCard.value = false
	}
})
async function endConversation(){if(streaming.value||!emotionStore.sessionId)return;try{await request({url:'/api/emotion/sessions/'+emotionStore.sessionId+'/state',method:'PUT',data:{action:'END'}});uni.redirectTo({url:'/pages/emotion/history'})}catch(e){emotionStore.errorMsg=e.message}}

/* ── 暖纸青玉 P4：纯展示新增（无逻辑改动） ── */
// 空状态开场白（静态文案，点击仅回填输入框，不接接口）
const OPENERS = ['最近静不下心来', '今天有一件开心的小事', '我也不知道从哪说起']
function fillInput(text) { inputText.value = text }
// 语音状态映射：useVoiceInput 的 idle/starting/recording/uploading → ChatInputBar 的 ''/recording/processing
const voiceStateView = computed(() => {
	const s = voiceState.value
	if (s === 'recording') return 'recording'
	if (s === 'starting' || s === 'uploading') return 'processing'
	return ''
})
</script>

<style scoped>
.page {
	display: flex;
	flex-direction: column;
	height: calc(100vh - var(--window-top, 0px));
	overflow: hidden;
	background-color: var(--zy-bg, #ffffff);
}

/* ── 页头（去底部分隔线） ── */
.chat-header { display: flex; align-items: center; gap: 20rpx; padding: 24rpx 28rpx; flex-shrink: 0; }
.chat-header-copy { flex: 1; min-width: 0; }
.header-name { display: block; font-size: var(--zy-text-body-lg); font-weight: 600; letter-spacing: 1rpx; color: var(--zy-title); white-space: nowrap; }
.header-state { display: block; font-size: var(--zy-text-micro); color: var(--zy-text); margin-top: 4rpx; }
.header-action { flex-shrink: 0; margin: 0; }

/* ── 对话区（flex: 1 占满剩余空间） ── */
.chat-area {
	min-height: 0;
	height: 0;
	flex: 1;
	overflow: hidden;
}
.chat-list { padding: 16rpx 32rpx 32rpx; }

/* ── 空状态欢迎区 ── */
.welcome { display: flex; flex-direction: column; align-items: center; padding: 96rpx 0 64rpx; }
.welcome-avatar { margin-bottom: 32rpx; }
.welcome-title { font-size: var(--zy-text-title-lg); font-weight: 600; color: var(--zy-title); letter-spacing: 1rpx; }
.welcome-sub { margin-top: 12rpx; font-size: var(--zy-text-body); color: var(--zy-brand-700); }
.welcome-hint { margin-top: 28rpx; font-size: var(--zy-text-caption); color: var(--zy-text); }
.memory-hint { display: flex; align-items: center; gap: 12rpx; margin-top: 24rpx; padding: 14rpx 20rpx; max-width: 100%; background: var(--zy-card-bg, #fffdf7); border: 1rpx solid var(--zy-line-soft, #e9e2d5); border-radius: 999rpx; }
.memory-hint-text { font-size: var(--zy-text-micro, 22rpx); color: var(--zy-text, #5c5648); }
.memory-hint-link { font-size: var(--zy-text-micro, 22rpx); color: var(--zy-brand-700, #1a645e); padding: 0 6rpx; }
.welcome-chips { display: flex; flex-direction: column; gap: 16rpx; margin-top: 32rpx; align-items: center; }
.opener-chip { background: transparent; border: 2rpx dashed var(--zy-stitch); color: var(--zy-text); border-radius: 999rpx; padding: 16rpx 32rpx; font-size: var(--zy-text-caption); margin: 0; line-height: 1.6; }

/* ── 消息行（气泡视觉在 ChatBubble 组件） ── */
.msg-row { display: flex; flex-direction: column; }
.msg-provider { display: block; margin: -24rpx 0 24rpx 78rpx; font-size: var(--zy-text-micro); color: var(--zy-text); opacity: .75; }
.msg-actions { display: flex; gap: 16rpx; margin: -20rpx 0 24rpx 78rpx; }
.speak-btn { min-height: 56rpx; margin: 0; padding: 0 24rpx; border-radius: 999rpx; background: var(--zy-bg-tint-mint); color: var(--zy-brand-700); font-size: var(--zy-text-micro); line-height: 1.6; }
.speak-btn::after { border: 0; }
.speak-btn.is-pressed { background: var(--zy-brand-600); color: #FFFFFF; }
.speak-btn.is-disabled { opacity: .6; }

/* ── 推荐入口 ── */
.recommend-entry { align-self: flex-start; margin: -12rpx 0 24rpx 78rpx; padding: 16rpx 28rpx; border-radius: 999rpx; background: var(--zy-bg-tint-mint); box-shadow: var(--zy-shadow-card); line-height: 1.6; }
.recommend-text { font-size: var(--zy-text-caption); color: var(--zy-brand-700); }

/* ── 错误重试 ── */
.error-row { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 12px; }
.error-text { font-size: var(--zy-text-caption); color: var(--zy-danger, var(--zy-care, #a64842)); }

/* ── 本地兜底持续轻注 ── */
.local-note { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; padding: 12rpx 24rpx; background: var(--zy-bg-tint-mint); border-top: 2rpx dashed var(--zy-stitch); }
.local-note__text { font-size: var(--zy-text-micro); color: var(--zy-text); }
.local-note__link { font-size: var(--zy-text-micro); color: var(--zy-brand-700); font-weight: 600; }

/* ── 底部免责声明（缝线上缘 + micro 档） ── */
.footer-disclaimer { flex-shrink: 0; margin: 0 32rpx; padding: 12rpx 0; padding-bottom: calc(12rpx + constant(safe-area-inset-bottom)); padding-bottom: calc(12rpx + env(safe-area-inset-bottom)); border-top: 2rpx dashed var(--zy-stitch); text-align: center; }
.footer-disclaimer-text { font-size: var(--zy-text-micro); color: var(--zy-text); }

/* ── 危机关怀卡片 ── */
.crisis-card {
	margin: 16rpx 0 24rpx;
	padding: 32rpx 28rpx;
	border-radius: var(--zy-radius-card);
	background: #F7ECEA;
	box-shadow: var(--zy-shadow-card);
	animation: crisisIn 0.4s cubic-bezier(0.22, 0.61, 0.36, 1);
}
@keyframes crisisIn {
	from { opacity: 0; transform: translateY(16rpx) scale(0.97); }
	to { opacity: 1; transform: translateY(0) scale(1); }
}
.crisis-card-header { display: flex; align-items: center; gap: 16rpx; margin-bottom: 20rpx; }
.crisis-avatar { flex-shrink: 0; }
.crisis-card-title { font-size: var(--zy-text-body-lg); font-weight: 600; color: var(--zy-care); }
.crisis-card-text { display: block; font-size: var(--zy-text-body); line-height: 1.7; color: var(--zy-title); margin-bottom: 28rpx; }
.crisis-card-btns { display: flex; flex-direction: column; gap: 16rpx; }

.motion-reduced .crisis-card { animation: none; }
</style>
